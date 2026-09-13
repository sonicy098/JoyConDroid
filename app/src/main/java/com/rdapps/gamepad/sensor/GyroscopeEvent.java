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
        return e;
    }

}
