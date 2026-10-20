package com.rdapps.gamepad.vibrator;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.os.CombinedVibration;
import android.view.InputDevice;
import androidx.preference.PreferenceManager;

public class GamepadVibrationHelper {

    private static Vibrator motor1 = null;
    private static Vibrator motor2 = null;
    private static Thread testThread = null;
    private static long lastVibrationTime = 0;
    // Batas aman: abaikan paket baru jika jaraknya kurang dari 40ms (~25 kali/detik)
    private static final int THROTTLE_INTERVAL_MS = 40;
    private static int defaultDeviceId = -1;
    private static int defaultLeftMotorId = -1;
    private static int defaultRightMotorId = -1;
    private static volatile boolean isTesting = false;

    public static void triggerStereoRumble(Context context, int leftAmplitude, int rightAmplitude) {
    	long currentTime = System.currentTimeMillis();
        boolean isStopCommand = (leftAmplitude == 0 && rightAmplitude == 0);

        // FITUR FILTER: Buang paket jika terlalu rapat, TAPI biarkan perintah "berhenti" lolos
        if (!isStopCommand && (currentTime - lastVibrationTime < THROTTLE_INTERVAL_MS)) {
            return; 
        }
        lastVibrationTime = currentTime;

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

        // 2. Batalkan getaran pada motor yang tersimpan
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        String leftBinding = sharedPrefs.getString("vibrator_binding_left", "");
        String rightBinding = sharedPrefs.getString("vibrator_binding_right", "");
        
        Vibrator physicalLeft = getVibratorFromBinding(leftBinding);
        Vibrator physicalRight = getVibratorFromBinding(rightBinding);

        if (physicalLeft != null) physicalLeft.cancel();
        if (physicalRight != null) physicalRight.cancel();
        
        // 3. Batalkan getaran HP
        Vibrator phoneVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (phoneVibrator != null) phoneVibrator.cancel();
    }

    public static void testContinuousVibration(Context context, int leftAmplitude, int rightAmplitude) {
        stopAllVibration(context);
        isTesting = true;

        testThread = new Thread(() -> {
            while (isTesting) {
                vibrateStereoGamepad(leftAmplitude, rightAmplitude, 80, context);
                try {
                    Thread.sleep(80); 
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        testThread.start();
    }

    // =========================================================
    // FITUR CERDAS: AUTO-BIND MOTOR SETELAH UNPLUG-REPLUG (MENGINGAT PILIHAN)
    // =========================================================
    public static void checkAndAutoBind(Context context) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        String leftBinding = sharedPrefs.getString("vibrator_binding_left", "");
        String rightBinding = sharedPrefs.getString("vibrator_binding_right", "");

        // Jika settingan manual pengguna MASIH TERSAMBUNG, biarkan! Jangan diganggu.
        if (isBindingValid(leftBinding) && isBindingValid(rightBinding)) {
            return; 
        }

        // --- FITUR BARU: Ekstrak preferensi ID motor lama sebelum ditimpa ---
        int preferredLeftMotor = 0; // Default pabrik
        int preferredRightMotor = 1; // Default pabrik

        try {
            if (!leftBinding.isEmpty() && leftBinding.contains("_")) {
                preferredLeftMotor = Integer.parseInt(leftBinding.split("_")[1]);
            }
        } catch (Exception ignored) {}

        try {
            if (!rightBinding.isEmpty() && rightBinding.contains("_")) {
                preferredRightMotor = Integer.parseInt(rightBinding.split("_")[1]);
            }
        } catch (Exception ignored) {}
        // --------------------------------------------------------------------

        // Jika sudah dicabut/stale, cari Gamepad baru dan pasang binding otomatis
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
                    if (motorIds.length > 0) {
                        
                        // Validasi apakah motor id favorit pengguna ADA di gamepad ini
                        boolean hasPrefLeft = false, hasPrefRight = false;
                        for (int mId : motorIds) {
                            if (mId == preferredLeftMotor) hasPrefLeft = true;
                            if (mId == preferredRightMotor) hasPrefRight = true;
                        }

                        // Gunakan motor pilihan jika ada, jika tidak kembali ke default 0 dan 1
                        int finalLeftMotor = hasPrefLeft ? preferredLeftMotor : motorIds[0];
                        int finalRightMotor = hasPrefRight ? preferredRightMotor : (motorIds.length > 1 ? motorIds[1] : motorIds[0]);

                        // Gabungkan Device ID baru dengan Motor ID yang sudah diingat
                        String newLeft = id + "_" + finalLeftMotor;
                        String newRight = id + "_" + finalRightMotor;
                        
                        // Timpa memori penyimpanan agar UI langsung terupdate
                        sharedPrefs.edit()
                            .putString("vibrator_binding_left", newLeft)
                            .putString("vibrator_binding_right", newRight)
                            .apply();
                        return; // Auto-Bind sukses, keluar
                    }
                }
            }
        }
    }

    private static boolean isBindingValid(String binding) {
        if (binding == null || binding.isEmpty() || binding.equals("default")) return false;
        try {
            String[] parts = binding.split("_");
            int deviceId = Integer.parseInt(parts[0]);
            InputDevice device = InputDevice.getDevice(deviceId);
            return device != null; // Selama devicenya belum dicabut, anggap valid
        } catch (Exception e) {
            return false;
        }
    }

    private static Vibrator getVibratorFromBinding(String binding) {
        if (binding == null || binding.isEmpty()) return null;
        try {
            String[] parts = binding.split("_");
            int deviceId = Integer.parseInt(parts[0]);
            int motorId = Integer.parseInt(parts[1]);

            InputDevice device = InputDevice.getDevice(deviceId);
            if (device != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return device.getVibratorManager().getVibrator(motorId);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static void vibrateStereoGamepad(int leftAmp, int rightAmp, int durationMs, Context context) {
        // PENTING: Periksa sebelum bergetar. Jika stik baru dicolok, otomatis pasang binding!
        checkAndAutoBind(context);

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        String leftBinding = sharedPrefs.getString("vibrator_binding_left", "");
        String rightBinding = sharedPrefs.getString("vibrator_binding_right", "");

        int leftDeviceId = -1, leftMotorId = -1;
        int rightDeviceId = -1, rightMotorId = -1;

        try {
            String[] parts = leftBinding.split("_");
            leftDeviceId = Integer.parseInt(parts[0]);
            leftMotorId = Integer.parseInt(parts[1]);
        } catch (Exception ignored) {}

        try {
            String[] parts = rightBinding.split("_");
            rightDeviceId = Integer.parseInt(parts[0]);
            rightMotorId = Integer.parseInt(parts[1]);
        } catch (Exception ignored) {}

        // =========================================================
        // JALUR MODERN API (CombinedVibration Anti Kebocoran)
        // =========================================================
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && leftDeviceId != -1 && leftDeviceId == rightDeviceId) {
            InputDevice device = InputDevice.getDevice(leftDeviceId);
            if (device != null) {
                VibratorManager vManager = device.getVibratorManager();
                CombinedVibration.ParallelCombination combo = CombinedVibration.startParallel();
                boolean hasSignal = false;
                
                if (leftAmp > 0 && leftMotorId != -1) {
                    combo.addVibrator(leftMotorId, VibrationEffect.createOneShot(durationMs, leftAmp));
                    hasSignal = true;
                }
                if (rightAmp > 0 && rightMotorId != -1) {
                    combo.addVibrator(rightMotorId, VibrationEffect.createOneShot(durationMs, rightAmp));
                    hasSignal = true;
                }
                
                if (hasSignal) {
                    vManager.vibrate(combo.combine());
                    // Paksa matikan motor spesifik jika amplitudonya 0 agar tidak nyangkut dari sebelumnya
                    if (leftAmp == 0 && leftMotorId != -1) vManager.getVibrator(leftMotorId).cancel();
                    if (rightAmp == 0 && rightMotorId != -1) vManager.getVibrator(rightMotorId).cancel();
                } else {
                    vManager.cancel();
                }
                return; 
            }
        }

        // =========================================================
        // JALUR FALLBACK (Beda Device)
        // =========================================================
        Vibrator physicalLeftMotor = getVibratorFromBinding(leftBinding);
        Vibrator physicalRightMotor = getVibratorFromBinding(rightBinding);

        if (physicalLeftMotor != null && physicalLeftMotor.hasVibrator()) {
            if (leftAmp > 0) physicalLeftMotor.vibrate(VibrationEffect.createOneShot(durationMs, leftAmp));
            else physicalLeftMotor.cancel();
        }

        if (physicalRightMotor != null && physicalRightMotor.hasVibrator()) {
            if (rightAmp > 0) physicalRightMotor.vibrate(VibrationEffect.createOneShot(durationMs, rightAmp));
            else physicalRightMotor.cancel();
        }
    }

    private static void vibratePhone(Context context, VibrationEffect effect) {
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(effect);
        }
    }
}
