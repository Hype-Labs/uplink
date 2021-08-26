package com.uplink.ulx.drivers.bluetooth.ble.model.foreign;

import android.bluetooth.BluetoothGattCharacteristic;

import com.uplink.ulx.TransportType;
import com.uplink.ulx.drivers.bluetooth.ble.gattClient.GattClient;
import com.uplink.ulx.drivers.commons.model.InputStreamCommons;
import com.uplink.ulx.drivers.model.InputStream;

import java.util.Objects;

public class BleForeignInputStream extends InputStreamCommons {

    private final BluetoothGattCharacteristic inputCharacteristic;
    private final GattClient gattClient;

    /**
     * Constructor. Initializes with the given arguments. By default, this also
     * initializes the stream to trigger hasDataAvailable delegate notifications
     * as soon as data arrives.
     * @param identifier An identifier used for JNI bridging and debugging.
     * @param gattClient
     * @param inputCharacteristic
     * @param invalidationDelegate The stream's InvalidationDelegate.
     */
    public BleForeignInputStream(
            String identifier,
            GattClient gattClient,
            BluetoothGattCharacteristic inputCharacteristic,
            InvalidationDelegate invalidationDelegate
    ) {
        super(identifier, TransportType.BLUETOOTH_LOW_ENERGY, true, invalidationDelegate);

        Objects.requireNonNull(gattClient);
        Objects.requireNonNull(inputCharacteristic);

        this.gattClient = gattClient;
        this.inputCharacteristic = inputCharacteristic;
    }

    private GattClient getGattClient() {
        return this.gattClient;
    }

    private BluetoothGattCharacteristic getInputCharacteristic() {
        return this.inputCharacteristic;
    }

    @Override
    public void requestAdapterToOpen() {
        getGattClient().subscribeCharacteristic(getInputCharacteristic());
    }

    @Override
    public void requestAdapterToClose() {
        // TODO
    }
}
