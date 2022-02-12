package com.uplink.ulx.drivers.bluetooth.ble.model.domestic;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.util.Log;

import com.uplink.ulx.TransportType;
import com.uplink.ulx.UlxError;
import com.uplink.ulx.drivers.bluetooth.ble.gattServer.GattServer;
import com.uplink.ulx.drivers.commons.model.ConnectorCommons;
import com.uplink.ulx.drivers.model.Connector;
import com.uplink.ulx.drivers.model.Stream;

import java.util.Objects;

/**
 *
 */
public class BleDomesticConnector extends ConnectorCommons {

    private final BluetoothDevice bluetoothDevice;
    private final BleDomesticService domesticService;
    private final GattServer gattServer;

    public BleDomesticConnector(
            String identifier,
            GattServer gattServer,
            BluetoothDevice bluetoothDevice,
            BleDomesticService domesticService
    ) {
        super(identifier, TransportType.BLUETOOTH_LOW_ENERGY);

        Objects.requireNonNull(gattServer);
        Objects.requireNonNull(bluetoothDevice);
        Objects.requireNonNull(domesticService);

        this.gattServer = gattServer;
        this.bluetoothDevice = bluetoothDevice;
        this.domesticService = domesticService;
    }

    public BluetoothDevice getBluetoothDevice() {
        return this.bluetoothDevice;
    }

    private BleDomesticService getDomesticService() {
        return this.domesticService;
    }

    private GattServer getGattServer() {
        return this.gattServer;
    }

    @Override
    public void requestAdapterToConnect() {
        super.onConnected(this);
    }

    @Override
    public void requestAdapterToDisconnect() {
        throw new RuntimeException("Can't request a domestic connector to " +
                "disconnect because the protocol to request activity from the " +
                "central is not implemented yet.");
    }

    @SuppressLint("NewApi") // forEach() is actually supported with desugaring library
    @Override
    public void onInvalidation(Stream stream, UlxError error) {
        Log.e(getClass().getCanonicalName(), "ULX BLE domestic connector invalidated");

        if (getState() != Connector.State.DISCONNECTED) {
            super.onDisconnection(this, error);

            getInvalidationCallbacks().forEach(callback -> callback.onInvalidation(this, error));
        }
    }
}
