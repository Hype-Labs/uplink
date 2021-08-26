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

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class BleForeignConnector extends ConnectorCommons implements GattClient.ConnectorDelegate {

    private GattClient gattClient;
    private final BleDomesticService domesticService;
    private final BluetoothManager bluetoothManager;
    private final BluetoothDevice bluetoothDevice;
    private final WeakReference<Context> context;

    /**
     * Constructor. Initializes with given parameters.
     * @param identifier A random unique identifier, mostly for bridging.
     * @param context The Android environment Context for permissions.
     * @param bluetoothDevice The remote Bluetooth that the Connector will refer.
     * @param bluetoothManager The Bluetooth manager.
     * @param domesticService The ULX service description.
     */
    public BleForeignConnector(
            String identifier,
            Context context,
            BluetoothDevice bluetoothDevice,
            BluetoothManager bluetoothManager,
            BleDomesticService domesticService
    ) {
        super(identifier, TransportType.BLUETOOTH_LOW_ENERGY);

        this.context = new WeakReference<>(context);
        this.bluetoothDevice = bluetoothDevice;
        this.bluetoothManager = bluetoothManager;
        this.domesticService = domesticService;
    }

    /**
     * Returns a strong reference to the Android environment Context.
     * @return The Android environment Context.
     */
    private Context getContext() {
        return this.context.get();
    }

    /**
     * Return the system framework BluetoothDevice instance associated with
     * this connector. This represents the remote device that this connector
     * can connect.
     * @return The remote Bluetooth device system abstraction  for the connector.
     */
    private BluetoothDevice getBluetoothDevice() {
        return this.bluetoothDevice;
    }

    /**
     * Returns the system framework BluetoothManager that is being used to
     * manage the connection. This will be used to instantiate the GATT client.
     * @return The BluetoothManager.
     */
    private BluetoothManager getBluetoothManager() {
        return this.bluetoothManager;
    }

    /**
     * Getter for the BleDomesticService instance, containing the Bluetooth LE
     * service specification.
     * @return The BleDomesticService instance.
     */
    private BleDomesticService getDomesticService() {
        return this.domesticService;
    }

    /**
     * The GATT client that is used to on the initiator device to manage
     * the connection.
     * @return The host's GattClient instance.
     */
    public GattClient getGattClient() {
        if (this.gattClient == null) {
            this.gattClient = new GattClient(
                    getBluetoothDevice(),
                    getBluetoothManager(),
                    getDomesticService(),
                    getContext()
            );

            // Assume the delegate
            this.gattClient.setConnectorDelegate(this);
        }
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
