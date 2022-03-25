package com.uplink.ulx.drivers.bluetooth.ble.model.domestic;

import android.bluetooth.BluetoothDevice;

import com.uplink.ulx.TransportType;
import com.uplink.ulx.drivers.bluetooth.ble.gattServer.GattServer;
import com.uplink.ulx.drivers.commons.model.ConnectorCommons;

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
        super.onConnected();
    }

    @Override
    public void requestAdapterToDisconnect() {
        throw new RuntimeException("Can't request a domestic connector to " +
                "disconnect because the protocol to request activity from the " +
                "central is not implemented yet.");
    }
}
