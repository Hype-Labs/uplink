package com.uplink.ulx.drivers.bluetooth.ble.controller;

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
import android.util.Log;

import com.uplink.ulx.TransportType;
import com.uplink.ulx.UlxError;
import com.uplink.ulx.UlxErrorCode;
import com.uplink.ulx.bridge.Registry;
import com.uplink.ulx.drivers.bluetooth.ble.gattServer.GattServer;
import com.uplink.ulx.drivers.bluetooth.ble.model.BleChannel;
import com.uplink.ulx.drivers.bluetooth.ble.model.BleDevice;
import com.uplink.ulx.drivers.bluetooth.ble.model.BleTransport;
import com.uplink.ulx.drivers.bluetooth.ble.model.domestic.BleDomesticConnector;
import com.uplink.ulx.drivers.bluetooth.ble.model.domestic.BleDomesticInputStream;
import com.uplink.ulx.drivers.bluetooth.ble.model.domestic.BleDomesticOutputStream;
import com.uplink.ulx.drivers.bluetooth.ble.model.domestic.BleDomesticService;
import com.uplink.ulx.drivers.bluetooth.commons.BluetoothStateListener;
import com.uplink.ulx.drivers.commons.controller.AdvertiserCommons;
import com.uplink.ulx.drivers.controller.Advertiser;
import com.uplink.ulx.drivers.model.Channel;
import com.uplink.ulx.drivers.model.Connector;
import com.uplink.ulx.drivers.model.Device;
import com.uplink.ulx.drivers.model.InputStream;
import com.uplink.ulx.drivers.model.OutputStream;
import com.uplink.ulx.drivers.model.Stream;
import com.uplink.ulx.drivers.model.Transport;
import com.uplink.ulx.threading.Dispatch;

import java.util.List;
import java.util.UUID;

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
        Connector.StateDelegate,
        Stream.InvalidationDelegate
{
    private final BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeAdvertiser bluetoothLeAdvertiser;

    private final BleDomesticService domesticService;
    private AdvertiseSettings advertiseSettings;
    private AdvertiseCallback advertiseCallback;
    private GattServer gattServer;

    private Registry<BluetoothDevice> registry;

    /**
     * The BleAdvertiserCallback is used to respond to the result of requesting
     * the adapter to start advertising, and handles the situations of success
     * and failure.
     */
    private class BleAdvertiseCallback extends AdvertiseCallback {

        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.i(getClass().getCanonicalName(), "ULX advertiser started");

            // This is propagated to the AdvertiserCommons
            onStart(BleAdvertiser.this);
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.i(getClass().getCanonicalName(), "ULX advertiser failed to start");

            final UlxError error = makeError(errorCode);

            // This is propagated to the AdvertiserCommons
            onFailedStart(BleAdvertiser.this, error);
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
     * Constructor. Initializes with the given arguments.
     * @param identifier A string identifier for the instance.
     * @param bluetoothManager The BluetoothManager instance.
     * @param domesticService The service configuration specification.
     * @param context The Android environment Context.
     */
    public BleAdvertiser(
            String identifier,
            BluetoothManager bluetoothManager,
            BleDomesticService domesticService,
            Context context
    ) {
        super(identifier, TransportType.BLUETOOTH_LOW_ENERGY, context);

        this.bluetoothManager = bluetoothManager;
        this.domesticService = domesticService;

        this.registry = null;

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
     * Returns the device registry that is used by the implementation to keep
     * track of native-to-abstract device mappings. If the registry has not
     * been created yet, it will at this point.
     * @return The {@code Device}-to-{@code BluetoothDevice} registry.
     */
    private Registry<BluetoothDevice> getRegistry() {
        if (this.registry == null) {
            this.registry = new Registry<>();
        }
        return this.registry;
    }

    /**
     * Returns the {@link Device} associated with the given {@link
     * BluetoothDevice}, if one exists. Otherwise returns {@code null}. If the
     * device does not exists or more than one is registered, this method will
     * raise a {@link RuntimeException}, although this is a behaviour that is
     * expected to change in the future.
     * @param bluetoothDevice The {@link BluetoothDevice} in the association.
     * @return The {@link Device} in the association or {@code null}.
     */
    private Device getDevice(BluetoothDevice bluetoothDevice) {

        List<Device> deviceList = getRegistry().getDevicesFromGenericIdentifier(bluetoothDevice.getAddress());

        // If the address is not registered, we skipped something
        if (deviceList == null || deviceList.isEmpty()) {
            throw new RuntimeException("A BluetoothDevice notification was " +
                    "issued for an open stream, but the Device identifier is " +
                    "not associated with it; this must mean that the registration " +
                    "was not performed or that the registry got corrupted.");
        }

        if (deviceList.size() > 1) {
            throw new RuntimeException("An unexpected amount of devices was " +
                    "found in association with a single BluetoothDevice address. " +
                    "This means that the registry got corrupted, since only a " +
                    "single entry is expected.");
        }

        // There should be only one
        return deviceList.get(0);
    }

    @Override
    public void requestAdapterToStart() {

        Dispatch.post(() -> {

            // Is BLE supported by this device?
            if (getBluetoothLeAdvertiser() == null) {
                handleStartUnsupportedTechnology();
                return;
            }

            // Start the service
            getGattServer().addService();
        });
    }

    /**
     * This method handles the situation of a failed start due to the lack of
     * support for BLE on the host device. It generates an error message and
     * notifies the delegate.
     */
    private void handleStartUnsupportedTechnology() {

        UlxError error = new UlxError(
                UlxErrorCode.ADAPTER_NOT_SUPPORTED,
                "Could not start Bluetooth Low Energy.",
                "The Bluetooth Low Energy technology is not supported by this device.",
                "Try a system update or contacting the manufacturer."
        );

        onFailedStart(this, error);
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
        getBluetoothLeAdvertiser().stopAdvertising(getAdvertiseCallback());

        // Notify the delegate (the stoppage is synchronous)
        onStop(this, null);
    }

    @Override
    public void onAdapterEnabled(BluetoothStateListener bluetoothStateListener) {
        if (getState() != Advertiser.State.RUNNING) {
            onReady(this);
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

            onStop(this, error);
        }
    }

    @Override
    public void destroy() {
        // TODO
    }

    @Override
    public void onDeviceConnected(GattServer gattServer, BluetoothDevice bluetoothDevice) {
        Log.i(getClass().getCanonicalName(), String.format("ULX bluetooth device connected %s", bluetoothDevice.getAddress()));

        Connector connector = new BleDomesticConnector(
                UUID.randomUUID().toString(),
                gattServer,
                bluetoothDevice,
                getDomesticService()
        );

        // This connector is now active
        addActiveConnector(connector);

        connector.setStateDelegate(this);
        connector.setInvalidationDelegate(this);

        // Register the connector's ID (not the actual connector) in association
        // with the given BluetoothDevice. This will enable retrieving the
        // Connector and the Device instances for BluetoothDevice notifications
        register(connector, bluetoothDevice);

        // Proceed with the discover process
        connector.connect();
    }

    /**
     * Registers the {@code Connector}'s identifier (not the actual Connector)
     * in association with the address for the given {@code BluetoothDevice}.
     * This will result in the BluetoothDevice being associated with its address
     * and the address and connector identifier with each other. What remains
     * to be registered is the association of the identifier with the {@code
     * Device}, which will come later once the device is discovered. This
     * association will be needed then.
     * @param connector The {@code Connector} whose identifier is to be
     *                  associated.
     * @param bluetoothDevice The {@code BluetoothDevice} whose address is to
     *                        be associated.
     */
    private void register(Connector connector, BluetoothDevice bluetoothDevice) {

        String address = bluetoothDevice.getAddress();
        String identifier = connector.getIdentifier();

        getRegistry().setGeneric(address, bluetoothDevice);
        getRegistry().associate(address, identifier);
    }

    @Override
    public void onOutputStreamSubscribed(GattServer gattServer, BluetoothDevice bluetoothDevice) {

        Device device = getDevice(bluetoothDevice);

        // Notify the stream that it has been subscribed
        BleDomesticInputStream inputStream = (BleDomesticInputStream)device.getTransport().getReliableChannel().getInputStream();
        BleDomesticOutputStream outputStream = (BleDomesticOutputStream)device.getTransport().getReliableChannel().getOutputStream();

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

        // Notify the output stream that the indication was given
        BleDomesticOutputStream outputStream = (BleDomesticOutputStream)device.getTransport().getReliableChannel().getOutputStream();
        outputStream.notifySuccessfulIndication();
    }

    @Override
    public void onNotificationNotSent(GattServer gattServer, BluetoothDevice bluetoothDevice, UlxError error) {

        Device device = getDevice(bluetoothDevice);

        // Notify the output stream that the indication was NOT given
        BleDomesticOutputStream outputStream = (BleDomesticOutputStream)device.getTransport().getReliableChannel().getOutputStream();
        outputStream.notifyFailedIndication(error);
    }

    @Override
    public void onCharacteristicWriteRequest(GattServer gattServer, BluetoothDevice bluetoothDevice, byte[] data) {
        Device device = getDevice(bluetoothDevice);

        // Notify the input stream of incoming data
        BleDomesticInputStream inputStream = (BleDomesticInputStream)device.getTransport().getReliableChannel().getInputStream();
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
        removeActiveConnector(connector);
    }

    @Override
    public void onInvalidation(Stream stream, UlxError error) {
    }

    /**
     * Requests the added service to start advertising. This will finally
     * publish the device on the network, and thus complete the start request
     * cycle. As soon as there's a response to this event, the advertiser
     * should be up.
     */
    private void startAdvertising() {
        Log.i(getClass().getCanonicalName(), "ULX advertiser is starting");

        Dispatch.post(() -> {
            getBluetoothLeAdvertiser().startAdvertising(
                    getAdvertiseSettings(),
                    getAdvertiseData(),
                    getAdvertiseCallback()
            );
        });
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
        onFailedStart(this, error);
    }

    @Override
    public void onConnected(Connector connector) {

        BleDomesticConnector domesticConnector = (BleDomesticConnector)connector;

        InputStream inputStream = new BleDomesticInputStream(
                connector.getIdentifier(),
                this
        );

        OutputStream outputStream = new BleDomesticOutputStream(
                connector.getIdentifier(),
                getGattServer(),
                domesticConnector.getBluetoothDevice(),
                getDomesticService().getReliableOutputCharacteristic(),
                this
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

        // Register the device (the BluetoothDevice, identifier, and address
        // should already be there; this should complete the association)
        getRegistry().setDevice(device.getIdentifier(), device);

        super.onDeviceFound(this, device);
    }

    @Override
    public void onDisconnection(Connector connector, UlxError error) {
        Log.e(getClass().getCanonicalName(), "ULX BLE advertiser disconnection");

        // The connector is no longer active
        removeActiveConnector(connector);
    }

    @Override
    public void onConnectionFailure(Connector connector, UlxError error) {
        Log.e(getClass().getCanonicalName(), "ULX BLE advertiser connection failure");
        Log.e(getClass().getCanonicalName(), String.format("ULX connector state is %s", connector.getState().toString()));

        // Remove from the registry; when the device is seen again, the
        // connection should be retried
        removeActiveConnector(connector);   // Shouldn't matter

        // Since we're having failed connections, we should ask the adapter to
        // restart.
        Advertiser.Delegate delegate = getDelegate();
        if (delegate != null) {
            delegate.onAdapterRestartRequest(this);
        }
    }
}
