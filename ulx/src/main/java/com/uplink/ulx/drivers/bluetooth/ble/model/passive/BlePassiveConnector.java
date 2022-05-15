package com.uplink.ulx.drivers.bluetooth.ble.model.passive;

import android.bluetooth.BluetoothDevice;

import com.uplink.ulx.TransportType;
import com.uplink.ulx.drivers.bluetooth.ble.gattServer.GattServer;
import com.uplink.ulx.drivers.commons.model.ConnectorCommons;

import java.util.Objects;

import androidx.annotation.NonNull;

/**
 *
 */
public class BlePassiveConnector extends ConnectorCommons {

    private final BluetoothDevice bluetoothDevice;
    private final BleDomesticService domesticService;
    private final GattServer gattServer;

    public static BlePassiveConnector newInstance(
            @NonNull String identifier,
            GattServer gattServer,
            BluetoothDevice bluetoothDevice,
            BleDomesticService domesticService
    ) {
        final BlePassiveConnector instance = new BlePassiveConnector(
                identifier,
                gattServer,
                bluetoothDevice,
                domesticService
        );
        instance.initialize(instance.getClass().getSimpleName());
        return instance;
    }

    private BlePassiveConnector(
            @NonNull String identifier,
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
        throw new RuntimeException("Can't request a passive connector to " +
                "disconnect because the protocol to request activity from the " +
                "central is not implemented yet.");
    }
}
