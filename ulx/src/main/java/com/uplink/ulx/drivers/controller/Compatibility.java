package com.uplink.ulx.drivers.controller;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Build;

import com.uplink.ulx.TransportType;

/**
 * This is a helper class that helps determining whether certain transports are
 * supported by the host system. It currently supports BLE only, and checks
 * support in a basic manner, but this will be further elaborated in the
 * future.
 */
public class Compatibility {

    private final Context context;

    /**
     * Constructor. Initializes with given context.
     * @param context The Android Context to use.
     */
    public Compatibility(Context context) {
        this.context = context;
    }

    /**
     * Getter for the Android context used to very supported for some
     * technologies.
     * @return The Android Context to use.
     */
    private Context getContext() {
        return this.context;
    }

    /**
     * Checks whether a given transport type is supported by the host system.
     * The given transport type is checked integrally, that is, compared for
     * equality. Mixed transport types will always yield false.
     * @param transportType The transport type to check.
     * @return Whether the given transport is supported by the host system.
     */
    public boolean isCompatible(int transportType) {
        if (transportType == TransportType.BLUETOOTH_LOW_ENERGY) {
            return isBLESupported();
        }
        return false;
    }

    /**
     * Checks whether BLE is supported by the host system.
     * @return Whether BLE is supported by the host system.
     */
    private boolean isBLESupported() {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) && isBluetoothSupported();
    }

    /**
     * Checks whether Bluetooth is supported by the host device by trying to
     * instantiate a BluetoothManager service.
     * @return Whether Bluetooth is supporterd by the host system.
     */
    @SuppressLint("ObsoleteSdkInt")
    private boolean isBluetoothSupported() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            BluetoothManager blm = (BluetoothManager) getContext().getSystemService(Context.BLUETOOTH_SERVICE);
            if (blm.getAdapter() != null) {
                return true;
            }
        }
        return false;
    }
}
