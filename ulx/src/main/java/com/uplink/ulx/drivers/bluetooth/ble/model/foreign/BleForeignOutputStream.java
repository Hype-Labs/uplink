package com.uplink.ulx.drivers.bluetooth.ble.model.foreign;

import android.bluetooth.BluetoothGattCharacteristic;

import com.uplink.ulx.TransportType;
import com.uplink.ulx.drivers.bluetooth.ble.gattClient.GattClient;
import com.uplink.ulx.drivers.commons.model.OutputStreamCommons;
import com.uplink.ulx.drivers.model.OutputStream;

import java.util.Objects;

public class BleForeignOutputStream extends OutputStreamCommons implements GattClient.OutputStreamDelegate {

    private final BluetoothGattCharacteristic outputCharacteristic;
    private final GattClient gattClient;

    /**
     * Constructor. Initializes with given arguments.
     * @param identifier An identifier used for JNI bridging and debugging.
     * @param gattClient
     * @param outputCharacteristic
     * @param delegate The OutputStream's delegate.
     * @param invalidationDelegate The stream's InvalidationDelegate.
     */
    public BleForeignOutputStream(
            String identifier,
            GattClient gattClient,
            BluetoothGattCharacteristic outputCharacteristic,
            InvalidationDelegate invalidationDelegate
    ) {
        super(identifier, TransportType.BLUETOOTH_LOW_ENERGY, true, invalidationDelegate);

        Objects.requireNonNull(gattClient);
        Objects.requireNonNull(outputCharacteristic);

        this.gattClient = gattClient;
        this.outputCharacteristic = outputCharacteristic;
    }

    private GattClient getGattClient() {
        return this.gattClient;
    }

    private BluetoothGattCharacteristic getOutputCharacteristic() {
        return this.outputCharacteristic;
    }

    @Override
    public void requestAdapterToOpen() {
        super.onOpen(this);

        // Ready to write
        Delegate delegate = getDelegate();
        if (delegate != null) {
            delegate.hasSpaceAvailable(this);
        }
    }

    @Override
    public void requestAdapterToClose() {
        // TODO
    }
}
