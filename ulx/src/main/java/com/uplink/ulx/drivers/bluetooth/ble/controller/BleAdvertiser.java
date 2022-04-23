package com.uplink.ulx.drivers.bluetooth.ble.controller;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.Build;
import android.os.ParcelUuid;

import com.uplink.ulx.TransportType;
import com.uplink.ulx.UlxError;
import com.uplink.ulx.UlxErrorCode;
import com.uplink.ulx.drivers.bluetooth.ble.gattServer.GattServer;
import com.uplink.ulx.drivers.bluetooth.ble.model.BleChannel;
import com.uplink.ulx.drivers.bluetooth.ble.model.BleDevice;
import com.uplink.ulx.drivers.bluetooth.ble.model.BleTransport;
import com.uplink.ulx.drivers.bluetooth.ble.model.passive.BleDomesticService;
import com.uplink.ulx.drivers.bluetooth.ble.model.passive.BlePassiveConnector;
import com.uplink.ulx.drivers.bluetooth.ble.model.passive.BlePassiveInputStream;
import com.uplink.ulx.drivers.bluetooth.ble.model.passive.BlePassiveOutputStream;
import com.uplink.ulx.drivers.bluetooth.commons.BluetoothStateListener;
import com.uplink.ulx.drivers.commons.controller.AdvertiserCommons;
import com.uplink.ulx.drivers.commons.model.ConnectorCommons;
import com.uplink.ulx.drivers.controller.Advertiser;
import com.uplink.ulx.drivers.model.Channel;
import com.uplink.ulx.drivers.model.Connector;
import com.uplink.ulx.drivers.model.Device;
import com.uplink.ulx.drivers.model.InputStream;
import com.uplink.ulx.drivers.model.OutputStream;
import com.uplink.ulx.drivers.model.Transport;
import com.uplink.ulx.threading.Dispatch;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import timber.log.Timber;

/**
 * The BleAdvertiser is the implementation of the Advertiser interface that
 * specifically manages BLE advertisements. This consists of publishing the
 * device on the network, making it visible to other scanners over the air,
 * so that the devices can connect. This advertiser uses the native system
 * BLE framework to manage the Bluetooth adapter and publish a BLE service,
 * as defined by BleDomesticService.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class BleAdvertiser extends AdvertiserCommons implements
        GattServer.Delegate,
        BluetoothStateListener.Observer,
        Connector.StateDelegate
{
    private final BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeAdvertiser bluetoothLeAdvertiser;

    private final BleDomesticService domesticService;
    private AdvertiseSettings advertiseSettings;
    private AdvertiseCallback advertiseCallback;
    private GattServer gattServer;

    /**
     * The connector registry that keeps mappings of {@link BluetoothDevice}s to {@link Connector}s
     * in connecting state. If the connection succeeds, the mapping is removed and replaced by one
     * in {@link #deviceRegistry}
     */
    @GuardedBy("connectorRegistry")
    private final Map<BluetoothDevice, BlePassiveConnector> connectorRegistry;
    /**
     * The device registry that is used to keep mappings of {@link BluetoothDevice}s to {@link
     * Device}s
     */
    @GuardedBy("connectorRegistry")
    private final Map<BluetoothDevice, Device> deviceRegistry;

    /**
     * The BleAdvertiserCallback is used to respond to the result of requesting
     * the adapter to start advertising, and handles the situations of success
     * and failure.
     */
    private class BleAdvertiseCallback extends AdvertiseCallback {

        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Timber.i("ULX advertiser started");

            // This is propagated to the AdvertiserCommons
            onStart();
        }

        @Override
        public void onStartFailure(int errorCode) {
            Timber.i("ULX advertiser failed to start");

            final UlxError error = makeError(errorCode);

            // This is propagated to the AdvertiserCommons
            onFailedStart(error);
        }

        /**
         * This method is an error factory that creates errors in response to
         * failed start requests. It goes through AdvertiseCallback error codes
         * and creates an UlxError instance with an error code (UlxErrorCode)
         * and appropriate message.
         * @param errorCode The AdvertiseCallback error code.
         * @return The corresponding UlxError.
         */
        private UlxError makeError(int errorCode) {

            // Not supported
            if (errorCode == AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED) {
                return new UlxError(
                        UlxErrorCode.ADAPTER_NOT_SUPPORTED,
                        "Could not advertise the device using Bluetooth Low Energy.",
                        "This operation is not supported by the device.",
                        "Try updating the system or contacting the manufacturer."
                );
            }

            // Too many advertisers
            if (errorCode == AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS) {
                return new UlxError(
                        UlxErrorCode.ADAPTER_BUSY,
                        "Could not advertiser the device using Bluetooth Low Energy.",
                        "The adapter is busy and is incapable of processing the request.",
                        "Try again later."
                );
            }

            // Already start; by the app? Are we overlapping?
            if (errorCode == AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED) {
                return new UlxError(
                        UlxErrorCode.ADAPTER_BUSY,
                        "Could not advertiser the device using Bluetooth Low Energy.",
                        "The adapter is busy and is incapable of processing the request.",
                        "Try again later."
                );
            }

            // If the advertisement data is too large, then there's a
            // programming error. This is an exception.
            if (errorCode == AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE) {
                throw new RuntimeException("Could not advertise using Bluetooth " +
                        "Low Energy. The advertisement data is too large."
                );
            }

            // Internal error
            if (errorCode == AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR) {
                return new UlxError(
                        UlxErrorCode.UNKNOWN,
                        "Could not advertise the device using Bluetooth Low Energy.",
                        "There was an unknown system error.",
                        "Try restarting the adapter."
                );
            }

            return new UlxError(
                    UlxErrorCode.UNKNOWN,
                    "Could not advertiser the device using Bluetooth Low Energy.",
                    "There was an unknown error.",
                    "Try restarting the adapter."
            );
        }
    }

    /**
     * Factory method. Initializes with the given arguments.
     * @param identifier A string identifier for the instance.
     * @param bluetoothManager The BluetoothManager instance.
     * @param domesticService The service configuration specification.
     * @param context The Android environment Context.
     */

    public static BleAdvertiser newInstance(
            String identifier,
            BluetoothManager bluetoothManager,
            BleDomesticService domesticService,
            Context context
    ) {
        final BleAdvertiser instance = new BleAdvertiser(
                identifier,
                bluetoothManager,
                domesticService,
                context
        );
        instance.initialize();
        return instance;
    }

    private BleAdvertiser(
            String identifier,
            BluetoothManager bluetoothManager,
            BleDomesticService domesticService,
            Context context
    ) {
        super(identifier, TransportType.BLUETOOTH_LOW_ENERGY, context);

        this.bluetoothManager = bluetoothManager;
        this.domesticService = domesticService;

        // BluetoothDevice overrides equals() and hashcode(), so it's safe to use it in HashMap
        this.deviceRegistry = new HashMap<>();
        this.connectorRegistry = new HashMap<>();
    }

    @Override
    protected void initialize() {
        super.initialize();
        BluetoothStateListener.addObserver(this);
    }

    /**
     * Getter for the BluetoothManager instance, which is passed down from the
     * Driver. This class is used to obtain the BluetoothAdapter and conduct
     * overall management of Bluetooth.
     * @return The BluetoothManager instance.
     */
    private BluetoothManager getBluetoothManager() {
        return this.bluetoothManager;
    }

    /**
     * Getter for the BluetoothAdapter instance, as is made accessible by the
     * BluetoothManager passed down by the Driver. This class represents the
     * host's physical Bluetooth adapter, and is an abstraction that is used
     * by the implementation to interact with the adapter.
     * @return The BluetoothAdapter instance.
     */
    private BluetoothAdapter getBluetoothAdapter() {
        if (this.bluetoothAdapter == null) {
            this.bluetoothAdapter = getBluetoothManager().getAdapter();
        }
        return this.bluetoothAdapter;
    }

    /**
     * This is a getter for the BluetoothLeAdvertiser, which the implementation
     * uses to start and stop the advertiser and its functions.
     * @return The BluetoothLeAdvertiser.
     */
    @SuppressWarnings("MissingPermission")
    @Nullable
    private BluetoothLeAdvertiser getBluetoothLeAdvertiser() {
        if (this.bluetoothLeAdvertiser == null) {
            this.bluetoothLeAdvertiser = getBluetoothAdapter().getBluetoothLeAdvertiser();
        }
        return this.bluetoothLeAdvertiser;
    }

    /**
     * Getter for the BleDomesticService as given at construction time. This
     * service factory is passed down from the Driver, and holds all the
     * necessary information to build the service that will be used by the
     * advertiser and the scanner.
     * @return The BleDomesticService factory.
     */
    private BleDomesticService getDomesticService() {
        return this.domesticService;
    }

    /**
     * This is a getter for the GattServer, an instance that is used to managed
     * the GATT server, with respect to the likes of publishing services,
     * finding devices on the network, establishing connections, and so on. The
     * advertiser will use this to interact with GATT.
     * @return The GattServer instance.
     * @see GattServer
     */
    private GattServer getGattServer() {
        if (this.gattServer == null) {
            this.gattServer = new GattServer(getDomesticService(), getBluetoothManager(), getContext());
            this.gattServer.setDelegate(this);
        }
        return this.gattServer;
    }

    /**
     * Returns the {@link Device} associated with the given {@link BluetoothDevice}, if one exists.
     * Otherwise returns {@code null}.
     *
     * @param bluetoothDevice The {@link BluetoothDevice} in the association.
     * @return The {@link Device} in the association or {@code null}.
     */
    @Nullable
    private Device getDevice(BluetoothDevice bluetoothDevice) {
        return deviceRegistry.get(bluetoothDevice);
    }

    @SuppressLint("MissingPermission")
    @Override
    public void requestAdapterToStart() {

        Dispatch.post(() -> {

            // Is BLE advertiser available?
            final BluetoothLeAdvertiser advertiser = getBluetoothAdapter().getBluetoothLeAdvertiser();
            if (advertiser == null) {
                Timber.w(
                        "Unable to get BLE advertiser. Adapter state: %d",
                        getBluetoothAdapter().getState()
                );
                handleStartAdvertiserUnavailable();
                return;
            }

            // Start the service
            getGattServer().addService();
        });
    }

    /**
     * This method handles the situation of a failed start due to advertiser unavailability. It
     * generates an error message and notifies the delegate.
     */
    private void handleStartAdvertiserUnavailable() {

        UlxError error = new UlxError(
                UlxErrorCode.ADAPTER_DISABLED,
                "Could not start Bluetooth Low Energy advertiser.",
                "Either BT adapter is off or BLE is not supported.",
                "Try turning BT adapter on."
        );

        onFailedStart(error);
    }

    /**
     * This is the factory method for the AdvertiseCallback (more specifically,
     * BleAdvertiseCallback) instance that will be used to respond to advertise
     * responses.
     * @return The AdvertiseCallback to respond to advertise start events.
     */
    private AdvertiseCallback getAdvertiseCallback() {
        if (this.advertiseCallback == null) {
            this.advertiseCallback = new BleAdvertiseCallback();
        }
        return this.advertiseCallback;
    }

    @Override
    public void requestAdapterToStop() {

        // Request the stoppage
        final BluetoothLeAdvertiser advertiser = getBluetoothLeAdvertiser();
        final UlxError error;
        if (advertiser != null) {
            advertiser.stopAdvertising(getAdvertiseCallback());
            error = null;
        } else {
            error = new UlxError(
                    UlxErrorCode.ADAPTER_DISABLED,
                    "Could not stop advertising",
                    "Bluetooth is turned off",
                    "No need to do anything. Advertising is off"
            );
        }

        // Notify the delegate (the stoppage is synchronous)
        onStop(error);
    }

    @Override
    public void onAdapterEnabled(BluetoothStateListener bluetoothStateListener) {
        if (getState() != Advertiser.State.RUNNING) {
            onReady();
        }
    }

    @Override
    public void onAdapterDisabled(BluetoothStateListener bluetoothStateListener) {

        if (getState() == Advertiser.State.RUNNING || getState() == Advertiser.State.STARTING) {

            UlxError error = new UlxError(
                    UlxErrorCode.ADAPTER_DISABLED,
                    "Could not start or maintain the Bluetooth advertiser.",
                    "The adapter was turned off.",
                    "Turn the Bluetooth adapter on."
            );

            onStop(error);
        }
    }

    @Override
    public void destroy() {
        // TODO
    }

    @Override
    public void onDeviceConnected(GattServer gattServer, BluetoothDevice bluetoothDevice) {
        Timber.i("ULX bluetooth device connected %s", bluetoothDevice.getAddress());
        BlePassiveConnector connector;

        synchronized (connectorRegistry) {
            // Remove the old connector if the connection from this device is
            // already being established (should not normally happen)
            final Connector existingConnector = connectorRegistry.remove(bluetoothDevice);
            if (existingConnector != null) {
                Timber.w("An incoming connection occurred while another one from the same device is being processed. Something went wrong.");
                detachFromConnector(existingConnector);
            } else {
                // Check if there is a connected device. This can happen only if its connector
                // has been removed from connectorRegistry
                final Device existingDevice = getDevice(bluetoothDevice);
                if (existingDevice != null) {
                    Timber.w("ULX device connection received for a known device. Closing old connector(s)");

                    forgetBtDevice(bluetoothDevice);
                }
            }

            connector = BlePassiveConnector.newInstance(
                    UUID.randomUUID().toString(),
                    gattServer,
                    bluetoothDevice,
                    getDomesticService()
            );

            // This connector is now active
            connectorRegistry.put(bluetoothDevice, connector);
        }

        connector.setStateDelegate(this);
        connector.addInvalidationCallback(this);

        // Proceed with the discover process
        connector.connect();
    }

    @Override
    public void onDeviceDisconnected(GattServer gattServer, BluetoothDevice bluetoothDevice, UlxError error) {
        Timber.e(
                "ULX peripheral device %s was disconnected",
                bluetoothDevice.getAddress()
        );

        final Device device;

        synchronized (connectorRegistry) {
            // Connector is no longer in 'connecting' state - unregister it
            final BlePassiveConnector connector;
            if ((connector = connectorRegistry.remove(bluetoothDevice)) != null) {
                detachFromConnector(connector);
            }

            device = getDevice(bluetoothDevice);
        }

        if (device == null) {
            Timber.w("ULX Device not found. Ignoring disconnection event");
            return;
        }

        final ConnectorCommons connector = (ConnectorCommons) device.getConnector();
        connector.onDisconnection(error);
    }

    @Override
    public void onDeviceInvalidation(GattServer gattServer, BluetoothDevice bluetoothDevice, UlxError error) {
        Timber.e(
                "ULX peripheral device %s was invalidated",
                bluetoothDevice.getAddress()
        );

        synchronized (connectorRegistry) {
            // Unregister connector
            final BlePassiveConnector connector;
            if ((connector = connectorRegistry.remove(bluetoothDevice)) != null) {
                detachFromConnector(connector);
            } else {
                // A connector could be registered, or a Device, but not both per one BluetoothDevice
                forgetBtDevice(bluetoothDevice);
            }
        }
    }

    /**
     * Unregisters device's connector and clears its callbacks
     * @param bluetoothDevice device which needs to be forgotten
     */
    private void forgetBtDevice(BluetoothDevice bluetoothDevice) {
        Connector connector;
        synchronized (connectorRegistry) {
            final Device device = getDevice(bluetoothDevice);

            if (device == null) {
                Timber.e("ULX device not found on the registry; not proceeding");
                return;
            }

            connector = device.getConnector();

            // Unregister the association between the connector and native device
            deviceRegistry.remove(bluetoothDevice);
        }

        detachFromConnector(connector);
    }

    /**
     * Removes connector's state delegate and invalidation callback
     * @param connector the connector to detach from
     */
    private void detachFromConnector(@NonNull Connector connector) {
        // Clear the delegates
        connector.setStateDelegate(null);
        connector.removeInvalidationCallback(this);
    }

    @Override
    public void onOutputStreamSubscribed(GattServer gattServer, BluetoothDevice bluetoothDevice) {

        Device device = getDevice(bluetoothDevice);

        if (device == null) {
            Timber.e(
                    "Output stream has been subscribed for an unknown native device %s",
                    bluetoothDevice.getAddress()
            );
            return;
        }
        // Notify the stream that it has been subscribed
        BlePassiveInputStream inputStream = (BlePassiveInputStream)device.getTransport().getReliableChannel().getInputStream();
        BlePassiveOutputStream outputStream = (BlePassiveOutputStream)device.getTransport().getReliableChannel().getOutputStream();

        // In fact, the InputStream is already open, since nothing needs to
        // happen for that. These two events are being triggered at the same
        // time, but the InputStream could have already triggered the event
        // before. This may change in the future.
        inputStream.notifyAsOpen();
        outputStream.notifyAsOpen();
    }

    @Override
    public void onNotificationSent(GattServer gattServer, BluetoothDevice bluetoothDevice) {

        Device device = getDevice(bluetoothDevice);

        if (device == null) {
            Timber.e(
                    "Notification sent callback received for an unknown device: %s",
                    bluetoothDevice.getAddress()
            );
            return;
        }
        // Notify the output stream that the indication was given
        BlePassiveOutputStream outputStream = (BlePassiveOutputStream)device.getTransport().getReliableChannel().getOutputStream();
        outputStream.notifySuccessfulIndication();
    }

    @Override
    public void onNotificationNotSent(GattServer gattServer, BluetoothDevice bluetoothDevice, UlxError error) {

        Device device = getDevice(bluetoothDevice);

        if (device == null) {
            Timber.e(
                    "Notification not sent callback received for an unknown device: %s",
                    bluetoothDevice.getAddress()
            );
            return;
        }


        // Notify the output stream that the indication was NOT given
        BlePassiveOutputStream outputStream = (BlePassiveOutputStream)device.getTransport().getReliableChannel().getOutputStream();
        outputStream.notifyFailedIndication(error);
    }

    @Override
    public void onCharacteristicWriteRequest(GattServer gattServer, BluetoothDevice bluetoothDevice, byte[] data) {
        Device device = getDevice(bluetoothDevice);

        if (device == null) {
            Timber.w(
                    "ULX received characteristic write request from an unknown device: %s. Ignoring.",
                    bluetoothDevice.getAddress()
            );
            return;
        }

        // Notify the input stream of incoming data
        BlePassiveInputStream inputStream = (BlePassiveInputStream)device.getTransport().getReliableChannel().getInputStream();
        inputStream.notifyDataAvailable(data);
    }

    @Override
    public void onServiceAdded(GattServer gattServer, BluetoothGattService service) {

        String addedServiceUuid = service.getUuid().toString();
        String modelServiceUuid = BleDomesticService.getGattServiceIdentifier();

        // Make sure that the service being added is the one that we're
        // expecting. If so, start advertising.
        if (addedServiceUuid.equalsIgnoreCase(modelServiceUuid)) {
            startAdvertising();
        } else {

            // I'm not sure when this happens, so we need to throw an exception.
            // If this does happen in any event, we might need to give some form
            // of indication as to what the result of the start request is, since
            // there must not be any loose ends.
            throw new RuntimeException("The BLE Advertiser got a successful" +
                    "service addition notification for a service that it does " +
                    "not recognize. This scenario is not expected.");
        }
    }

    @Override
    public void onInvalidation(Connector connector, UlxError error) {
        removeIfRegistered(connector);
        detachFromConnector(connector);
    }

    /**
     * Requests the added service to start advertising. This will finally
     * publish the device on the network, and thus complete the start request
     * cycle. As soon as there's a response to this event, the advertiser
     * should be up.
     */
    private void startAdvertising() {
        Timber.i("ULX advertiser is starting");

        final BluetoothLeAdvertiser advertiser = getBluetoothLeAdvertiser();
        if (advertiser != null) {
            Dispatch.post(() -> advertiser.startAdvertising(
                    getAdvertiseSettings(),
                    getAdvertiseData(),
                    getAdvertiseCallback()
            ));
        } else {
            onFailedStart(
                    new UlxError(
                            UlxErrorCode.ADAPTER_DISABLED,
                            "Could not start advertising",
                            "Bluetooth is turned off",
                            "Turn Bluetooth adapter on"
                    )
            );
        }
    }

    /**
     * This method builds the advertise settings for the BLE advertiser. The
     * settings are not yet configurable, since they are pretty much hard coded
     * in this method, but this is expected to change in the future.
     * @return The AdvertiseSettings for the BLE advertiser.
     */
    private AdvertiseSettings getAdvertiseSettings() {
        if (this.advertiseSettings == null) {
            this.advertiseSettings = new AdvertiseSettings.Builder()
                    // Transmission frequency
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                    // Advertising time limit. May not exceed 180000
                    // milliseconds. A value of 0 will disable the time limit.
                    .setTimeout(0)
                    .setConnectable(true)
                    .build()
            ;
        }
        return this.advertiseSettings;
    }

    /**
     * This method compiles the advertisement data that will be broadcast with
     * the service. This will include the service UUID, so that the browser can
     * immediately recognize a device that is trying to join the network.
     * @return The advertisement data.
     */
    private AdvertiseData getAdvertiseData() {

        String coreServiceUuid = getDomesticService().getCoreService().getUuid().toString();
        String gattServiceUuid = BleDomesticService.getGattServiceIdentifier16bits();

        ParcelUuid parcelUuidService = ParcelUuid.fromString(coreServiceUuid);
        ParcelUuid parcelUuid16BitsService = ParcelUuid.fromString(gattServiceUuid);

        byte[] controlValue = getDomesticService().getControlValue();

        // Advertise data with the service UUID so that the browser is capable
        // of recognizing the service.
        return new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(parcelUuidService)
                .addServiceData(parcelUuid16BitsService, controlValue)
                .build();
    }

    @Override
    public void onServiceAdditionFailed(GattServer gattServer, UlxError error) {
        onFailedStart(error);
    }

    @Override
    public void onConnected(@NonNull Connector connector) {

        BlePassiveConnector domesticConnector = (BlePassiveConnector)connector;

        InputStream inputStream = BlePassiveInputStream.newInstance(connector.getIdentifier());

        final BluetoothDevice bluetoothDevice = domesticConnector.getBluetoothDevice();

        OutputStream outputStream = BlePassiveOutputStream.newInstance(
                connector.getIdentifier(),
                getGattServer(),
                bluetoothDevice,
                getDomesticService().getReliableOutputCharacteristic()
        );

        Channel reliableChannel = new BleChannel(
                connector.getIdentifier(),
                inputStream,
                outputStream
        );

        Transport transport = new BleTransport(
                connector.getIdentifier(),
                reliableChannel
        );

        Device device = new BleDevice(
                connector.getIdentifier(),
                connector,
                transport
        );

        synchronized (connectorRegistry) {
            if (removeIfRegistered(connector)) {
                // Register the device
                deviceRegistry.put(bluetoothDevice, device);

                onDeviceFound(device);
            } // Else the connector has been replaced by another incoming connection. Ignore
        }
    }

    @Override
    public void onDisconnection(@NonNull Connector connector, UlxError error) {
        Timber.e("ULX BLE advertiser disconnection");

        // The connector is no longer active.
        // Normally connectorRegistry won't contain the connector at this point,
        // but we're still removing it - just in case
        removeIfRegistered(connector);
    }

    @Override
    public void onConnectionFailure(@NonNull Connector connector, UlxError error) {
        Timber.e("ULX BLE advertiser connection failure");
        Timber.e("ULX connector state is %s", connector.getState().toString());

        // Remove from the registry; when the device is seen again, the
        // connection should be retried
        removeIfRegistered(connector);

        // Since we're having failed connections, we should ask the adapter to
        // restart.
        Advertiser.Delegate delegate = getDelegate();
        if (delegate != null) {
            delegate.onAdapterRestartRequest(this);
        }
    }

    /**
     * Removes given connector from {@link #connectorRegistry} if one is present
     * @param connector connector to remove
     * @return whether the given connector was found and removed
     */
    private boolean removeIfRegistered(@NonNull Connector connector) {
        if (connector instanceof BlePassiveConnector) {

            final BluetoothDevice bluetoothDevice =
                    ((BlePassiveConnector) connector).getBluetoothDevice();

            synchronized (connectorRegistry) {
                final BlePassiveConnector existingConnector = connectorRegistry.get(bluetoothDevice);
                if (existingConnector != null && existingConnector.equals(connector)) {
                    connectorRegistry.remove(bluetoothDevice);
                    return true;
                }
            }
        }

        return false;
    }
}
