package com.uplink.ulx.drivers.bluetooth.ble.model.foreign;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.uplink.ulx.TransportType;
import com.uplink.ulx.UlxError;
import com.uplink.ulx.drivers.bluetooth.ble.gattClient.GattClient;
import com.uplink.ulx.drivers.bluetooth.ble.model.domestic.BleDomesticService;
import com.uplink.ulx.drivers.commons.model.ConnectorCommons;
import com.uplink.ulx.drivers.model.Stream;

import java.lang.ref.WeakReference;
import java.util.Objects;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class BleForeignConnector extends ConnectorCommons implements GattClient.ConnectorDelegate {

    private GattClient gattClient;

    /**
     * Constructor. Initializes with given parameters.
     * @param identifier A random unique identifier, mostly for bridging.
     * @param gattClient The GATT client for the connector.
     */
    public BleForeignConnector(
            String identifier,
            GattClient gattClient
    ) {
        super(identifier, TransportType.BLUETOOTH_LOW_ENERGY);

        this.gattClient = Objects.requireNonNull(gattClient);

        // Assume the delegate
        this.gattClient.setConnectorDelegate(this);
    }

    /**
     * The GATT client that is used to on the initiator device to manage
     * the connection.
     * @return The host's GattClient instance.
     */
    public final GattClient getGattClient() {
        return this.gattClient;
    }

    @SuppressLint("MissingPermission")
    @Override
    public void requestAdapterToConnect() {
        getGattClient().connect();
    }

    @Override
    public void requestAdapterToDisconnect() {
        // TODO
    }

    @Override
    public void onInvalidation(Stream stream, UlxError error) {
        // TODO
    }

    @Override
    public void onConnected(GattClient gattClient) {
        Log.i(getClass().getCanonicalName(), "ULX foreign connector connected");
        super.onConnected(this);
    }

    @Override
    public void onConnectionFailure(GattClient gattClient, UlxError error) {
        // TODO
    }

    @Override
    public void onDisconnected(GattClient gattClient, UlxError error) {
        // TODO
    }
}
