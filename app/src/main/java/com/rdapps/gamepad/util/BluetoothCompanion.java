package com.rdapps.gamepad.util;

import static com.rdapps.gamepad.log.JoyConLog.log;

import java.util.Arrays;
import lombok.RequiredArgsConstructor;

public class BluetoothCompanion {
    private static final String TAG = BluetoothCompanion.class.getName();
    private static volatile boolean sLoaded = false;

    /** Values returned by {@link #getBluetoothLinkMode()}, matching the
     * JoyConDroidCompanion shim's /dev/btlinkmode encoding. */
    @RequiredArgsConstructor
    public enum LinkMode {
        UNKNOWN(-1),
        ACTIVE(0),
        HOLD(1),
        SNIFF(2),
        PARK(3);

        private final int code;

        static LinkMode fromCode(int code) {
            return Arrays.stream(values())
                    .filter(mode -> mode.code == code)
                    .findAny()
                    .orElse(UNKNOWN);
        }
    }

    private static native String getBluetoothAddressNative();

    private static native int getBluetoothLinkModeNative();

    public static String getBluetoothAddress() {
        try {
            if (!sLoaded) {
                System.loadLibrary("joycondroid_jni");
                sLoaded = true;
            }
            return getBluetoothAddressNative();
        } catch (Throwable t) {
            log(TAG, "getBluetoothAddress failed", t);
            return null;
        }
    }

    /**
     * Returns the current Bluetooth link power mode (Active/Hold/Sniff/Park)
     * as observed by JoyConDroidCompanion's Mode-Change hook, or
     * {@link LinkMode#UNKNOWN} if the module isn't installed or found
     * nothing to hook on this device.
     */
    public static LinkMode getBluetoothLinkMode() {
        try {
            if (!sLoaded) {
                System.loadLibrary("joycondroid_jni");
                sLoaded = true;
            }
            return LinkMode.fromCode(getBluetoothLinkModeNative());
        } catch (Throwable t) {
            log(TAG, "getBluetoothLinkMode failed", t);
            return LinkMode.UNKNOWN;
        }
    }
}
