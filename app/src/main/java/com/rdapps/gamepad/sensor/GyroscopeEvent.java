package com.rdapps.gamepad.sensor;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import lombok.Data;

@Data
public class GyroscopeEvent {
    public int accuracy;
    public Sensor sensor;
    public long timestamp;
    public float[] values;

    public static GyroscopeEvent createFromSensorEvent(SensorEvent event, int multiplier) {
        GyroscopeEvent e = new GyroscopeEvent();
        e.accuracy = event.accuracy;
        e.sensor = event.sensor;
        e.timestamp = event.timestamp;
        e.values = new float[]{
                event.values[0] * multiplier,
                event.values[1] * multiplier,
                event.values[2] * multiplier
        };
                // Rekam pergerakan gyro, tapi filter angka kecil (noise) agar Logcat tidak lag
        if (Math.abs(e.values[0]) > 2.0f || Math.abs(e.values[1]) > 2.0f || Math.abs(e.values[2]) > 2.0f) {
            android.util.Log.d("SENSOR_REKAM_GYRO", "Pitch(X): " + e.values[0] + " | Yaw(Y): " + e.values[1] + " | Roll(Z): " + e.values[2]);
        }

        return e;
    }

}
