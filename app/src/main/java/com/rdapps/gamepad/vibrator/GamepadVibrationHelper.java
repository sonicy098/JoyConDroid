package com.rdapps.gamepad.vibrator;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.InputDevice;
import androidx.preference.PreferenceManager;

public class GamepadVibrationHelper {

    private static Vibrator motor1 = null;
    private static Vibrator motor2 = null;
    private static Thread testThread = null;
    private static volatile boolean isTesting = false;

    public static void triggerStereoRumble(Context context, int leftAmplitude, int rightAmplitude) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        
        // Baca pengaturan (kamu bisa menambahkan toggle ini di file preferences XML JoyConDroid nantinya)
        boolean routeToGamepad = sharedPrefs.getBoolean("route_vibration_to_gamepad", false);

        // Opsi kustomisasi baru
        boolean isStereo = sharedPrefs.getBoolean("vibration_is_stereo", true);
        int leftMultRaw = sharedPrefs.getInt(isStereo ? "vibration_multiplier_left" : "vibration_multiplier", 10);
        int rightMultRaw = sharedPrefs.getInt(isStereo ? "vibration_multiplier_right" : "vibration_multiplier", 10);
        
        float leftMultiplier = (float) leftMultRaw;
		float rightMultiplier = (float) rightMultRaw;
        boolean leftEnabled = sharedPrefs.getBoolean("vibrator_left_enabled", true);
        boolean rightEnabled = sharedPrefs.getBoolean("vibrator_right_enabled", true);
        boolean swapMotors = sharedPrefs.getBoolean("vibrator_swap_motors", false);

        if (routeToGamepad) {
            // Jika rute ke gamepad aktif, tapi KEDUA motor sengaja dimatikan pengguna,
            // buang paketnya dan JANGAN fallback ke HP.
            if (!leftEnabled && !rightEnabled) {
                return;
            }

            // Logika MONO: Gabungkan sinyal dengan mengambil nilai getaran terkuat
            if (!isStereo) {
                int maxAmp = Math.max(leftAmplitude, rightAmplitude);
                leftAmplitude = maxAmp;
                rightAmplitude = maxAmp;
            }

            // Terapkan multiplier
            int finalLeft = (int) (leftAmplitude * leftMultiplier);
            int finalRight = (int) (rightAmplitude * rightMultiplier);

            // Logika sakelar individu: Jika dimatikan, set amplitudo rute tersebut menjadi 0
            if (!leftEnabled) finalLeft = 0;
            if (!rightEnabled) finalRight = 0;

            // Batasi nilai aman (0 - 255)
            finalLeft = Math.min(255, Math.max(0, finalLeft));
            finalRight = Math.min(255, Math.max(0, finalRight));

            // Eksekusi getaran (dengan parameter swap)
            vibrateStereoGamepad(finalLeft, finalRight, 80, context);
		} else {
            // Logika bawaan JoyConDroid: Jika rute gamepad dimatikan dari awal, getarkan HP
            int maxAmp = Math.max(leftAmplitude, rightAmplitude);
            
            // CEGAH CRASH: Hanya buat VibrationEffect jika amplitudo di atas 0
            if (maxAmp > 0) {
                VibrationEffect defaultEffect = VibrationEffect.createOneShot(80, maxAmp);
                vibratePhone(context, defaultEffect);
            } else {
                // Jika 0, batalkan getaran HP
                Vibrator phoneVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                if (phoneVibrator != null) phoneVibrator.cancel();
            }
        }

    }
    
    public static void stopAllVibration(Context context) {
        // 1. Matikan loop paket
        isTesting = false;
        if (testThread != null) {
            testThread.interrupt();
            testThread = null;
        }

        // 2. Batalkan getaran pada motor yang di-binding dari dropdown
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        String leftBinding = sharedPrefs.getString("vibrator_binding_left", "default");
        String rightBinding = sharedPrefs.getString("vibrator_binding_right", "default");
        
        Vibrator physicalLeft = getVibratorFromBinding(leftBinding);
        Vibrator physicalRight = getVibratorFromBinding(rightBinding);

        if (physicalLeft != null) physicalLeft.cancel();
        if (physicalRight != null) physicalRight.cancel();

        // 3. Batalkan motor default
        if (motor1 != null) motor1.cancel();
        if (motor2 != null) motor2.cancel();
        
        // 4. Batalkan getaran HP
        Vibrator phoneVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (phoneVibrator != null) phoneVibrator.cancel();
    }

    // Memicu pengiriman paket getaran berulang (loop) seperti sistem HD Rumble Switch
    public static void testContinuousVibration(Context context, int leftAmplitude, int rightAmplitude) {
        stopAllVibration(context);
        isTesting = true;

        testThread = new Thread(() -> {
            while (isTesting) {
                // Tembakkan paket getaran singkat
                vibrateStereoGamepad(leftAmplitude, rightAmplitude, 80, context);
                try {
                    // Jeda sebentar sebelum mengirim paket berikutnya agar terasa bersambung
                    Thread.sleep(80); 
                } catch (InterruptedException e) {
                    break; // Keluar dari loop jika dihentikan
                }
            }
        });
        testThread.start();
    }

    private static void initMotors() {
        // Jika motor1 sudah ada dan masih terkoneksi, tidak perlu memindai ulang
        if (motor1 != null && motor1.hasVibrator()) {
            return;
        }

        // Reset variabel jika perangkat terputus
        motor1 = null;
        motor2 = null;

        int[] deviceIds = InputDevice.getDeviceIds();
        for (int id : deviceIds) {
            InputDevice device = InputDevice.getDevice(id);
            if (device != null && (
                (device.getSources() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD || 
                (device.getSources() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            )) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    VibratorManager vManager = device.getVibratorManager();
                    int[] motorIds = vManager.getVibratorIds();
                    
                    if (motorIds.length >= 2) {
                        motor1 = vManager.getVibrator(motorIds[0]);
                        motor2 = vManager.getVibrator(motorIds[1]);
                        return;
                    } else if (motorIds.length == 1) {
                        motor1 = vManager.getVibrator(motorIds[0]);
                        return;
                    }
                }
            }
        }
    }

    // Method untuk mengekstrak Vibrator fisik dari string binding (contoh: "34_1")
    private static Vibrator getVibratorFromBinding(String binding) {
        if (binding == null || binding.equals("default")) return null;
        try {
            String[] parts = binding.split("_");
            int deviceId = Integer.parseInt(parts[0]);
            int motorId = Integer.parseInt(parts[1]);

            InputDevice device = InputDevice.getDevice(deviceId);
            if (device != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return device.getVibratorManager().getVibrator(motorId);
            }
        } catch (Exception e) {
            // Abaikan error parsing dan kembalikan null
        }
        return null;
    }

    private static void vibrateStereoGamepad(int leftAmp, int rightAmp, int durationMs, Context context) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        String leftBinding = sharedPrefs.getString("vibrator_binding_left", "default");
        String rightBinding = sharedPrefs.getString("vibrator_binding_right", "default");

        Vibrator physicalLeftMotor = getVibratorFromBinding(leftBinding);
        Vibrator physicalRightMotor = getVibratorFromBinding(rightBinding);

        // Jika opsi disetel ke "Otomatis (Default)", gunakan initMotors() standar kita
        if (physicalLeftMotor == null || physicalRightMotor == null) {
            initMotors();
            if (physicalLeftMotor == null) physicalLeftMotor = motor1;
            if (physicalRightMotor == null) physicalRightMotor = motor2;
        }

        if (physicalLeftMotor != null && physicalLeftMotor.hasVibrator() && leftAmp > 0) {
            physicalLeftMotor.vibrate(VibrationEffect.createOneShot(durationMs, leftAmp));
        }

        if (physicalRightMotor != null && physicalRightMotor.hasVibrator() && rightAmp > 0) {
            physicalRightMotor.vibrate(VibrationEffect.createOneShot(durationMs, rightAmp));
        }
    }

    private static void vibratePhone(Context context, VibrationEffect effect) {
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(effect);
        }
    }
}
