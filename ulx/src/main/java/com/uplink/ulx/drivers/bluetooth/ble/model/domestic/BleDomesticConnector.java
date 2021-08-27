package com.uplink.ulx.drivers.bluetooth.ble.model.domestic;

import android.bluetooth.BluetoothDevice;

import com.uplink.ulx.TransportType;
import com.uplink.ulx.UlxError;
import com.uplink.ulx.drivers.bluetooth.ble.gattServer.GattServer;
import com.uplink.ulx.drivers.commons.model.ConnectorCommons;
import com.uplink.ulx.drivers.model.Connector;
import com.uplink.ulx.drivers.model.Stream;

import java.util.Objects;

public class BleDomesticConnector extends ConnectorCommons {

    private final BluetoothDevice device;
    private final BleDomesticService domesticService;
    private final GattServer gattServer;

    public BleDomesticConnector(
            String identifier,
            GattServer gattServer,
            BluetoothDevice device,
            BleDomesticService domesticService
    ) {
        super(identifier, TransportType.BLUETOOTH_LOW_ENERGY);

        Objects.requireNonNull(gattServer);
        Objects.requireNonNull(device);
        Objects.requireNonNull(domesticService);

        this.gattServer = gattServer;
        this.device = device;
        this.domesticService = domesticService;

    }

    private BluetoothDevice getDevice() {
        return this.device;
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

    @Override
    public void onInvalidation(Stream stream, UlxError error) {
        if (getState() != Connector.State.DISCONNECTED) {
            super.onDisconnection(this, error);

            Connector.InvalidationDelegate invalidationDelegate = getInvalidationDelegate();
            if (invalidationDelegate != null) {
                invalidationDelegate.onInvalidation(this, error);
            }
        }
    }
}
