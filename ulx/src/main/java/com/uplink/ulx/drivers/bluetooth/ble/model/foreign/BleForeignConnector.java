package com.uplink.ulx.drivers.bluetooth.ble.model.foreign;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.os.Build;
import android.util.Log;

import com.uplink.ulx.TransportType;
import com.uplink.ulx.UlxError;
import com.uplink.ulx.drivers.bluetooth.ble.gattClient.GattClient;
import com.uplink.ulx.drivers.commons.model.ConnectorCommons;
import com.uplink.ulx.drivers.model.Stream;

import java.util.Objects;

/**
 * A {@link BleForeignConnector} is a {@link com.uplink.ulx.drivers.model.Connector}
 * that implements the BLE transport for peripheral devices. This constitutes
 * a connector that initiates the connection from the client side of the BLE
 * connection (actually, the only one that can initiate it). This type of
 * connectors are created when scanning for devices on the network. They will
 * rely on the GATT client interface ({@link GattClient}) to interact with the
 * adapter and manage the connections with the central.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class BleForeignConnector extends ConnectorCommons implements GattClient.ConnectorDelegate {
    // TODO refactor Foreign and Domestic for Active/Passive

    private final GattClient gattClient;

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
        Log.e(getClass().getCanonicalName(), "ULX connector is being requested to disconnect, but that's not supported yet");
    }

    @Override
    public void onConnected(GattClient gattClient) {
        super.onConnected(this);
    }

    @Override
    public void onConnectionFailure(GattClient gattClient, UlxError error) {
        super.onConnectionFailure(this, error);
    }

    @Override
    public void onDisconnection(GattClient gattClient, UlxError error) {
        super.onDisconnection(this, error);
    }
}
