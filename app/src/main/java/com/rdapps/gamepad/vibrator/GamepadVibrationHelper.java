package com.rdapps.gamepad.vibrator;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.InputDevice;
import androidx.preference.PreferenceManager;

public class GamepadVibrationHelper {

    public static void triggerRumble(Context context, int baseAmplitude) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        
        // Baca pengaturan dari Preferences
        boolean routeToGamepad = sharedPrefs.getBoolean("route_vibration_to_gamepad", false);
        int multiplierRaw = sharedPrefs.getInt("vibration_multiplier", 10);
        float multiplier = multiplierRaw / 10.0f;

        if (routeToGamepad) {
            // Hitung amplitudo akhir, pastikan tidak melebihi batas 255
            int finalAmplitude = Math.min(255, Math.max(1, (int) (baseAmplitude * multiplier)));
            // Buat efek getaran dengan amplitudo baru, durasi sama seperti bawaan JoyConDroid (80ms)
            VibrationEffect effect = VibrationEffect.createOneShot(80, finalAmplitude);
            vibrateExternalGamepad(effect);
        } else {
            // Jika toggle mati, getarkan HP seperti biasa menggunakan VibrationPattern bawaan
            VibrationEffect defaultEffect = VibrationPattern.rumble(baseAmplitude);
            vibratePhone(context, defaultEffect);
        }
    }

    private static void vibrateExternalGamepad(VibrationEffect effect) {
        int[] deviceIds = InputDevice.getDeviceIds();
        
        for (int id : deviceIds) {
            InputDevice device = InputDevice.getDevice(id);
            if (device != null && (
                (device.getSources() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD || 
                (device.getSources() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            )) {
                Vibrator vibrator = device.getVibrator();
                if (vibrator != null && vibrator.hasVibrator()) {
                    vibrator.vibrate(effect);
                    return; // Berhenti setelah stik pertama digetarkan
                }
            }
        }
    }

    private static void vibratePhone(Context context, VibrationEffect effect) {
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(effect);
        }
    }
}
