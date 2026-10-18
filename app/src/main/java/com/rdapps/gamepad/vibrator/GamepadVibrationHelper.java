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
    private static boolean isInitialized = false;

    public static void triggerStereoRumble(Context context, int leftAmplitude, int rightAmplitude) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        
        // Baca pengaturan (kamu bisa menambahkan toggle ini di file preferences XML JoyConDroid nantinya)
        boolean routeToGamepad = sharedPrefs.getBoolean("route_vibration_to_gamepad", false);

        // Opsi kustomisasi baru
        boolean isStereo = sharedPrefs.getBoolean("vibration_is_stereo", true);
        int leftMultRaw = sharedPrefs.getInt(isStereo ? "vibration_multiplier_left" : "vibration_multiplier", 10);
        int rightMultRaw = sharedPrefs.getInt(isStereo ? "vibration_multiplier_right" : "vibration_multiplier", 10);
        
        float leftMultiplier = leftMultRaw / 10.0f;
        float rightMultiplier = rightMultRaw / 10.0f;
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
            vibrateStereoGamepad(finalLeft, finalRight, 80, swapMotors);
        } else {
            // Logika bawaan JoyConDroid: Jika rute gamepad dimatikan dari awal, getarkan HP
            int maxAmp = Math.max(leftAmplitude, rightAmplitude);
            VibrationEffect defaultEffect = VibrationEffect.createOneShot(80, maxAmp);
            vibratePhone(context, defaultEffect);
        }
    }

    private static void initMotors() {
        if (isInitialized) return;

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
                        // Daftarkan motor tanpa asumsi posisi
                        motor1 = vManager.getVibrator(motorIds[0]);
                        motor2 = vManager.getVibrator(motorIds[1]);
                        isInitialized = true;
                        return;
                    } else if (motorIds.length == 1) {
                        motor1 = vManager.getVibrator(motorIds[0]);
                        isInitialized = true;
                        return;
                    }
                }
            }
        }
        isInitialized = true; 
    }

    private static void vibrateStereoGamepad(int leftAmp, int rightAmp, int durationMs, boolean swapMotors) {
        initMotors();

        // Tentukan mana fisik motor yang akan dianggap sebagai "Kiri" dan "Kanan"
        Vibrator physicalLeftMotor = swapMotors ? motor2 : motor1;
        Vibrator physicalRightMotor = swapMotors ? motor1 : motor2;

        // Tembak getaran ke fisik motor Kiri jika nilainya di atas 0
        if (physicalLeftMotor != null && physicalLeftMotor.hasVibrator() && leftAmp > 0) {
            physicalLeftMotor.vibrate(VibrationEffect.createOneShot(durationMs, leftAmp));
        }

        // Tembak getaran ke fisik motor Kanan jika nilainya di atas 0
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
