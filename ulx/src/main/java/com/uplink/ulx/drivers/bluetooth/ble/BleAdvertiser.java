package com.uplink.ulx.drivers.bluetooth.ble;

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
import android.os.Handler;
import android.os.ParcelUuid;

import com.uplink.ulx.TransportType;
import com.uplink.ulx.UlxError;
import com.uplink.ulx.UlxErrorCode;
import com.uplink.ulx.drivers.controller.Advertiser;
import com.uplink.ulx.drivers.controller.Browser;
import com.uplink.ulx.drivers.model.Connector;
import com.uplink.ulx.drivers.bluetooth.ble.gattServer.GattServer;
import com.uplink.ulx.drivers.bluetooth.commons.BluetoothStateListener;
import com.uplink.ulx.drivers.commons.controller.AdvertiserCommons;
import com.uplink.ulx.threading.ExecutorPool;

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
        BluetoothStateListener.Observer {

    private final BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeAdvertiser bluetoothLeAdvertiser;

    private final BleDomesticService domesticService;
    private AdvertiseSettings advertiseSettings;
    private AdvertiseCallback advertiseCallback;
    private GattServer gattServer;

    private Handler mainHandler;

    /**
     * The BleAdvertiserCallback is used to respond to the result of requesting
     * the adapter to start advertising, and handles the situations of success
     * and failure.
     */
    private class BleAdvertiseCallback extends AdvertiseCallback {

        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            ExecutorPool.getExecutor(getTransportType()).execute(
                    () -> onStart(BleAdvertiser.this)
            );
        }

        @Override
        public void onStartFailure(int errorCode) {

            final UlxError error = makeError(errorCode);

            ExecutorPool.getExecutor(getTransportType()).execute(
                    () -> onFailedStart(BleAdvertiser.this, error)
            );
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

    @Override
    public void requestAdapterToStart() {

        // Is BLE supported by this device?
        if (getBluetoothLeAdvertiser() == null) {
            handleStartUnsupportedTechnology();
            return;
        }

        // Start the service
        getGattServer().addService();
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
    }

    @Override
    public void onDeviceConnected(GattServer gattServer, BluetoothDevice device) {
    }

    @Override
    public void onServiceAdded(GattServer gattServer, BluetoothGattService service) {

        String addedServiceUuid = service.getUuid().toString();
        String modelServiceUuid = BleDomesticService.getGattServiceIdentifier();

        // Make sure that the service being added is the one that we're
        // expecting. If so, start advertising.
        if (addedServiceUuid.equalsIgnoreCase(modelServiceUuid)) {
            startAdvertising();
        }
    }

    @Override
    public void onInvalidation(Connector connector, UlxError error) {
    }

    /**
     * Requests the added service to start advertising. This will finally
     * publish the device on the network, and thus complete the start request
     * cycle. As soon as there's a response to this event, the advertiser
     * should be up.
     */
    private void startAdvertising() {

        // This is currently being dispatched in the same thread that the app
        // uses for UI work. It's possible that this is a system requirement,
        // but it should be confirmed, since we should be avoiding dispatching
        // work in this thread altogether.
        getMainHandler().post(
                () -> getBluetoothLeAdvertiser().startAdvertising(
                    getAdvertiseSettings(),
                    getAdvertiseData(),
                    getAdvertiseCallback()
                )
        );
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
                .build()
                ;
    }

    /**
     * Instantiates a Handler for the main Loop of the current process so that
     * the implementation can dispatch work. Work dispatched here will be
     * sharing with other application components, so it should be avoided.
     * @return The Handler for the main Loop.
     */
    private Handler getMainHandler() {
        if (this.mainHandler == null) {
            this.mainHandler = new Handler(getContext().getMainLooper());
        }
        return this.mainHandler;
    }

    @Override
    public void onServiceFailedToAdd(GattServer gattServer, UlxError error) {
        onFailedStart(this, error);
    }
}
