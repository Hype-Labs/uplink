package com.uplink.ulx.drivers.bluetooth.commons;

import androidx.annotation.NonNull;

/**
 * This class represents the possible connection states
 */
public enum BluetoothConnectionState {
    /**
     * The peripheral is disconnected
     */
    DISCONNECTED(0),

    /**
     * The peripheral is connecting
     */
    CONNECTING(1),

    /**
     * The peripheral is connected
     */
    CONNECTED(2),

    /**
     * The peripheral is disconnecting
     */
    DISCONNECTING(3);

    BluetoothConnectionState(final int value) {
        this.value = value;
    }

    public final int value;

    @NonNull
    public static BluetoothConnectionState fromValue(final int value) {
        for (BluetoothConnectionState type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        return DISCONNECTED;
    }
}
