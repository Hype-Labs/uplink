package com.uplink.ulx.drivers.bluetooth.ble.controller;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.ParcelUuid;

import com.uplink.ulx.TransportType;
import com.uplink.ulx.UlxError;
import com.uplink.ulx.UlxErrorCode;
import com.uplink.ulx.drivers.bluetooth.ble.gattClient.GattClient;
import com.uplink.ulx.drivers.bluetooth.ble.model.BleChannel;
import com.uplink.ulx.drivers.bluetooth.ble.model.BleDevice;
import com.uplink.ulx.drivers.bluetooth.ble.model.BleTransport;
import com.uplink.ulx.drivers.bluetooth.ble.model.active.BleActiveConnector;
import com.uplink.ulx.drivers.bluetooth.ble.model.active.BleActiveInputStream;
import com.uplink.ulx.drivers.bluetooth.ble.model.active.BleActiveOutputStream;
import com.uplink.ulx.drivers.bluetooth.ble.model.passive.BleDomesticService;
import com.uplink.ulx.drivers.bluetooth.commons.BluetoothPermissionChecker;
import com.uplink.ulx.drivers.bluetooth.commons.BluetoothStateListener;
import com.uplink.ulx.drivers.commons.controller.BrowserCommons;
import com.uplink.ulx.drivers.controller.Browser;
import com.uplink.ulx.drivers.model.Channel;
import com.uplink.ulx.drivers.model.Connector;
import com.uplink.ulx.drivers.model.Transport;
import com.uplink.ulx.threading.Dispatch;
import com.uplink.ulx.utils.SerialOperationsManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import timber.log.Timber;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class BleBrowser extends BrowserCommons implements
        Connector.StateDelegate,
        BluetoothStateListener.Observer
{
    private final BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private ScanSettings.Builder bluetoothLeBrowsingSettings;
    private final BleDomesticService domesticService;

    private List<ScanFilter> scanFilters;
    private final ScanCallback scanCallback;

    /**
     * A map of identifiers (String) to BluetoothDevice, which corresponds to the registry of
     * devices that have been found by the implementation. This will prevent devices from being
     * found multiples times.
     */
    private final ConcurrentMap<String, BluetoothDevice> knownDevices;

    private final SerialOperationsManager operationsManager;

    // Access to this field is confined to the main thread
    private boolean isStartScanRequested;
    // Access to this field is confined to the main thread
    private int connectionsInProgress;

    /**
     * The BLEScannerCallback implements the Bluetooth LE scan callbacks that
     * are used by the system to report scan results. It
     */
    private class BLEScannerCallback extends ScanCallback {

        @Override
        public void onScanResult(int callbackType, final ScanResult scanResult) {
            if (knownDevices.containsKey(scanResult.getDevice().getAddress())) {
                //Log.i(getClass().getCanonicalName(), String.format("ULX BLE browser ignoring device %s because it's already known", scanResult.getDevice().getAddress()));
                return;
            }

            // The host may find itself in a scan, and that must be ignored
            if (isHostDevice(scanResult)) {
                Timber.i("ULX ignoring host device");
                return;
            }

            handleDeviceFound(scanResult.getDevice(), scanResult.getScanRecord());
        }

        @Override
        public void onBatchScanResults(List<ScanResult> scanResultList) {
            for (final ScanResult scanResult : scanResultList) {
                onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, scanResult);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {

            // More than one scan request may have overlapped, since it's
            // unlikely that the app is scanning with the same settings.
            if (errorCode == ScanCallback.SCAN_FAILED_ALREADY_STARTED) {
                return;
            }

            // This is not an ideal error; we should be looking at the errorCode
            // and trying to figure the appropriate message. This should be
            // changed.
            UlxError error = new UlxError(
                    UlxErrorCode.UNKNOWN,
                    "Could not scan for other devices on the network.",
                    "The scanner failed due to an unknown error.",
                    "Try resetting the Bluetooth adapter and giving all necessary permissions."
            );

            // Propagate to the delegate
            onFailedStart(error);
        }

        /**
         * Checks if a given {@link ScanResult} corresponds to the host device.
         * This may happen if the host device sees its own advertisement, in
         * which case it will find itself. Such matches are filtered out of the
         * batch of results.
         * @param scanResult The ScanResult to check.
         * @return Whether the ScanResult corresponds to the host device.
         */
        @SuppressLint({"HardwareIds", "MissingPermission"})
        private boolean isHostDevice(ScanResult scanResult) {
            return getBluetoothAdapter().getAddress().equalsIgnoreCase(scanResult.getDevice().getAddress());
        }
    }

    /**
     * Initializes the instance with the given parameters. It also subscribes the browser as a
     * BluetoothStateListener observer, so that it can track adapter state changes. The given
     * BluetoothManager is expected to be shared with the advertiser, since it's the abstract entity
     * that will be used to managed the adapter. The BleDomesticService, on the other hand, is used
     * to configure the services, and works as a specification of the type of services that the
     * browser should be looking for.
     *
     * @param identifier        A string identifier for the instance.
     * @param bluetoothManager  The BluetoothManager instance.
     * @param domesticService   The service configuration specification.
     * @param operationsManager operations manager to serialize BLE operations
     * @param context           The Android environment Context.
     */
    public static BleBrowser newInstance(
            String identifier,
            BluetoothManager bluetoothManager,
            BleDomesticService domesticService,
            SerialOperationsManager operationsManager,
            Context context
    ) {
        final BleBrowser browser = new BleBrowser(
                identifier,
                bluetoothManager,
                domesticService,
                operationsManager,
                context
        );
        browser.initialize(browser.getClass().getSimpleName());
        BluetoothStateListener.addObserver(browser);
        return browser;
    }

    private BleBrowser(
            String identifier,
            BluetoothManager bluetoothManager,
            BleDomesticService domesticService,
            SerialOperationsManager operationsManager, Context context
    ) {
        super(identifier, TransportType.BLUETOOTH_LOW_ENERGY, context);

        this.bluetoothManager = bluetoothManager;
        this.domesticService = domesticService;
        this.operationsManager = operationsManager;

        this.scanCallback = new BLEScannerCallback();

        this.knownDevices = new ConcurrentHashMap<>();
    }

    @SuppressLint("MissingPermission")
    @MainThread
    private void updateScannerStatus() {
        boolean shouldStartScanning = isStartScanRequested && connectionsInProgress == 0;
        if (shouldStartScanning) {
            startScanning();
        } else {
            stopScanning();
        }
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
     * Helper method that retrieves the BluetoothGattService core service from
     * the BleDomesticService instance given at construction time.
     * @return The core service.
     */
    private BluetoothGattService getCoreService() {
        return getDomesticService().getCoreService();
    }

    /**
     * Getter for the Bluetooth Low Energy scanner, used by the browser to look
     * for other devices on the network. This instance will be used to manage
     * BLE scanning operations. This is queried from the BluetoothAdapter
     * instance, which already holds a strong reference to the scanner.
     */
    @Nullable
    private BluetoothLeScanner getBluetoothLeScanner() {
        return getBluetoothAdapter().getBluetoothLeScanner();
    }

    /**
     * This is a factory method for the Bluetooth LE scan filters (ScanFilter),
     * which will return a list of filters with the ID of the BLE domestic
     * service. This will make sure that the scanner will only get BLE scan
     * results for services that are of interest to the SDK.
     * @return A list of ScanFilter with a single entry for the core service.
     */
    private synchronized List<ScanFilter> getScanFilters() {
        if (this.scanFilters == null) {
            this.scanFilters = new ArrayList<>();

            // Create a ScanFilter with the core service UUID, which will make
            // the scanner return only the services that interest us
            UUID coreServiceUuid = getCoreService().getUuid();
            ParcelUuid uuid = new ParcelUuid(coreServiceUuid);
            ScanFilter filter = new ScanFilter.Builder().setServiceUuid(uuid).build();

            this.scanFilters.add(filter);
        }
        return this.scanFilters;
    }

    /**
     * This is a factory method that creates the ScanSettings for the Bluetooth
     * LE scanner. This method does not allow for any form of configurability,
     * since all types of settings are hardcoded into it; however, this should
     * change in the future, and new implementations must enable more dynamic
     * configuration schemes.
     * @return The Bluetooth LE ScanSettings instance to configure the scanner.
     */
    private ScanSettings getScanSettings() {

        if (this.bluetoothLeBrowsingSettings == null) {
            this.bluetoothLeBrowsingSettings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED);

            // What changed in marshmallow to enforce this? Docs needed.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                this.bluetoothLeBrowsingSettings.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES);
                this.bluetoothLeBrowsingSettings.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE);
            }
        }

        return this.bluetoothLeBrowsingSettings.build();
    }

    /**
     * Returns an instance of BLEScannerCallback, which will be used to respond
     * to Bluetooth LE scan results. This class will handle notifications of
     * devices being found on the network.
     * @return The BLEScannerCallback instance to handle scan results.
     */
    private ScanCallback getScanCallback() {
        return this.scanCallback;
    }

    /**
     * Helper method that returns the core service UUID.
     * @return The core service UUID.
     */
    private UUID getCoreServiceUuid() {
        return getCoreService().getUuid();
    }

    /**
     * Helper method that retrieves the core service UUID encoded as a string.
     * @return The core service UUID string.
     */
    private String getCoreServiceUuidString() {
        return getCoreServiceUuid().toString();
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void requestAdapterToStartBrowsing() {
        Timber.i("ULX BLE browser is requesting the adapter to start");

        // Are the necessary permissions in place?
        if (!BluetoothPermissionChecker.isLocationPermissionGranted(getContext())) {
            handleStartPermissionDenied();
            return;
        }

        // Is Bluetooth enabled?
        if (!getBluetoothAdapter().isEnabled()) {
            handleStartDisabledBluetooth();
            return;
        }

        Dispatch.post(() -> {
            isStartScanRequested = true;
            updateScannerStatus();
        });
    }


    @MainThread
    private void startScanning() {
        Timber.i("ULX BLE scanner starting");

        final BluetoothLeScanner bluetoothLeScanner = getBluetoothLeScanner();
        if (bluetoothLeScanner != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    bluetoothLeScanner.startScan(
                            getScanFilters(),
                            getScanSettings(),
                            getScanCallback()
                    );
                } else {
                    bluetoothLeScanner.startScan(
                            getScanCallback()
                    );
                }

                // Notify the delegate
                onStart();
            } catch (Exception e) {
                handleStartAdapterFailed();
            }
        } else {
            handleStartAdapterFailed();
        }
    }

    /**
     * Handles a failure in requesting the adapter to start scanning.
     * This will create an error and notify the delegate of a failure.
     */
    @SuppressLint("MissingPermission")
    private void handleStartAdapterFailed() {
        onFailedStart(new UlxError(
                UlxErrorCode.UNKNOWN,
                "Failed to start BLE scanning",
                String.format(
                        Locale.US,
                        "BT scanner unavailable. BT adapter state: %d",
                        getBluetoothAdapter().getState()
                ),
                "Try restarting bluetooth"
        ));
    }

    /**
     * Handles a failure in obtaining the necessary permissions when trying to
     * start the service. This will create an error and notify the delegate of
     * a failure.
     */
    private void handleStartPermissionDenied() {

        UlxError error = new UlxError(
                UlxErrorCode.ADAPTER_UNAUTHORIZED,
                "Could not start the Bluetooth service.",
                "The app does not have the necessary permissions or the Bluetooth adapter is off.",
                "Please give the necessary permissions and turn the adapter on."
        );

        onFailedStart(error);
    }

    /**
     * Handles the event when the Bluetooth adapter is disabled when attempting
     * to start the BLE service. The implementation will create an error and
     * notify the delegate.
     */
    private void handleStartDisabledBluetooth() {

        UlxError error = new UlxError(
                UlxErrorCode.ADAPTER_DISABLED,
                "Could not start the Bluetooth service.",
                "The Bluetooth adapter is not available.",
                "Try turning Bluetooth on.");

        onFailedStart(error);

        // Restart the adapter
        getBluetoothAdapter().enable();
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void requestAdapterToStopBrowsing() {
        Timber.i("ULX BLE browser requesting the adapter to stop browsing");

        Dispatch.post(() -> {
            isStartScanRequested = false;
            updateScannerStatus();
        });

        // Notify the delegate; we're stopping gracefully
        onStop(null);
    }

    @SuppressLint("MissingPermission")
    @MainThread
    private void stopScanning() {
        Timber.i("ULX BLE scanner stopping");
        final BluetoothLeScanner bluetoothLeScanner = getBluetoothLeScanner();
        if (bluetoothLeScanner != null) {
            bluetoothLeScanner.stopScan(getScanCallback());
        } else {
            Timber.e(
                    "Failed to stop scanning. Unable to access scanner. BT state: %d",
                    getBluetoothAdapter().getState()
            );
        }
    }

    /**
     * Handles the event of a device being found on the network, by making
     * several checks and deciding whether to initiate a connection. If the
     * connection comes through, the host device will initiate a connection.
     * @param bluetoothDevice The device to check.
     * @param scanRecord The ScanRecord corresponding to the given device.
     */
    private void handleDeviceFound(BluetoothDevice bluetoothDevice, ScanRecord scanRecord) {
        //Log.i(BleBrowser.this.getClass().getCanonicalName(), String.format("ULX BLE browser found native device %s", bluetoothDevice.getAddress()));

        // Do not proceed if the address was already registered
        if (this.knownDevices.putIfAbsent(bluetoothDevice.getAddress(), bluetoothDevice) != null) {
            //Log.i(getClass().getCanonicalName(), String.format("ULX BLE browser ignoring device %s because it's already known", bluetoothDevice.getAddress()));
            return;
        }

        Timber.d("Device %s added to registry", bluetoothDevice.getAddress());

        // Is the record found by the scanner publishing the expected service?
        if (!isCoreServiceScanRecord(scanRecord)) {
            Timber.i(
                    "ULX BLE browser ignoring device %s because it doesn't publish a core service",
                    bluetoothDevice.getAddress()
            );
            return;
        }


        // Connect
        startConnection(bluetoothDevice);
    }

    /**
     * Checks whether the given ScanRecord corresponds to a device publishing
     * a service that is recognizable by the implementation. This is done by
     * checking all service UUIDs published the device, and whether any of
     * those matches the expected value. This shouldn't be needed, however,
     * given that the scan filters are in place, but it works as an additional
     * validation.
     * @param record The ScanRecord to check.
     * @return Whether the ScanRecord corresponds to a valid service UUID.
     */
    @SuppressWarnings("MissingPermission")
    private boolean isCoreServiceScanRecord(ScanRecord record) {
        List<ParcelUuid> serviceUuidList = record.getServiceUuids();

        // No services at all?
        if (serviceUuidList == null) {
            return false;
        }

        for (ParcelUuid parcelUuid : serviceUuidList) {
            String uuid = parcelUuid.getUuid().toString();
            String coreServiceUuid = getCoreServiceUuidString();

            // Found one?
            if (uuid.equalsIgnoreCase(coreServiceUuid)) {
                return true;
            }

        }

        return false;
    }

    /**
     * Instantiates the Connector abstraction and initiates the connection. The
     * connector will be assigned a unique identifier (generated randomly)
     * that is used internally by the implementation to keep track of known
     * connectors, and the browser (this browser) will assume the connector's
     * delegates as needed. This is the first step in abstracting the native
     * model, since the Device abstraction will only appear later, after the
     * streams are functional.
     * @param bluetoothDevice The native device to connect.
     */
    @SuppressWarnings("MissingPermission")
    private void startConnection(BluetoothDevice bluetoothDevice) {
        Timber.i(
                "ULX BLE scanner is initiating connection process to %s",
                bluetoothDevice.getAddress()
        );

        // Instantiate the GATT client
        GattClient gattClient = GattClient.newInstance(
                bluetoothDevice,
                getBluetoothManager(),
                getDomesticService(),
                operationsManager,
                getContext()
        );

        // Create the connector
        Connector connector = BleActiveConnector.newInstance(
                UUID.randomUUID().toString(),
                gattClient
        );

        // Take ownership of the streams
        connector.setStateDelegate(this);
        connector.addInvalidationCallback(this);

        Timber.i(
                "ULX BLE scanner created connector %s for native device %s",
                connector.getIdentifier(),
                bluetoothDevice.getAddress()
        );

        Dispatch.post(() -> {
            connectionsInProgress++;
            if (connectionsInProgress == 1) { // We went from 0 to 1 - maybe time to stop scanning
                updateScannerStatus();
            }
        });
        connector.connect();
    }

    @Override
    public void destroy() {
        // TODO
    }

    @Override
    public void onInvalidation(Connector connector, UlxError error) {
        connector.removeInvalidationCallback(this);
    }

    @Override
    public void onAdapterEnabled(BluetoothStateListener bluetoothStateListener) {
        if (getState() != Browser.State.RUNNING) {
            onReady();
        }
    }

    @Override
    public void onAdapterDisabled(BluetoothStateListener bluetoothStateListener) {
        Timber.i("ULX BLE adapter disabled");

        if (getState() == Browser.State.RUNNING || getState() == Browser.State.STARTING) {

            UlxError error = new UlxError(
                    UlxErrorCode.ADAPTER_DISABLED,
                    "Could not start or maintain the Bluetooth scanner.",
                    "The adapter was turned off.",
                    "Turn the Bluetooth adapter on."
            );

            onStop(error);
        }

        // The adapter being turned off, means that all connections where lost
        notifyAllDevicesAsDisconnected();

        // Enable
        Timber.i("ULX is enabling the BLE adapter");
        getBluetoothAdapter().enable();
    }

    private void notifyAllDevicesAsDisconnected() {
        // TODO
    }

    @Override
    public void onConnected(@NonNull Connector connector) {
        Timber.i(
                "ULX BLE browser connector %s connected",
                connector.getIdentifier()
        );

        Dispatch.post(() -> {
            connectionsInProgress--;
            if (connectionsInProgress == 0) { // We went from 1 to 0 - maybe time to start scanning
                updateScannerStatus();
            }

            final BleDevice device = createDevice((BleActiveConnector)connector);

            // Propagate to the delegate
            super.onDeviceFound(this, device);
        });
    }

    /**
     * This method is a Device factory that creates a BleDevice from a
     * BleActiveConnector. The connector is expected to already have connected
     * to the remote peer, since that's a precondition to create the streams.
     * @param connector The connector to the remote device.
     * @return An umbrella BleDevice instance corresponds to the given connector.
     */
    @MainThread
    private BleDevice createDevice(BleActiveConnector connector) {

        // Common stuff being initialized here...
        String identifier = connector.getIdentifier();

        // GATT stuff
        GattClient gattClient = connector.getGattClient();
        BluetoothGattService foreignService = gattClient.getServiceMatching(getCoreService());

        BluetoothGattCharacteristic reliableInputCharacteristic = getDomesticService().getReliableInputCharacteristic();
        BluetoothGattCharacteristic reliableOutputCharacteristic = getDomesticService().getReliableOutputCharacteristic();

        Objects.requireNonNull(reliableInputCharacteristic);
        Objects.requireNonNull(reliableOutputCharacteristic);

        BluetoothGattCharacteristic physicalReliableOutputCharacteristic = foreignService.getCharacteristic(reliableOutputCharacteristic.getUuid());
        BluetoothGattCharacteristic physicalReliableInputCharacteristic = foreignService.getCharacteristic(reliableInputCharacteristic.getUuid());

        // Create the input stream
        BleActiveInputStream inputStream = BleActiveInputStream.newInstance(
                identifier,
                gattClient,
                physicalReliableOutputCharacteristic
        );

        // Create the output stream
        BleActiveOutputStream outputStream = BleActiveOutputStream.newInstance(
                identifier,
                gattClient,
                physicalReliableInputCharacteristic
        );

        // The streams assume the corresponding GATT delegates
        gattClient.setInputStreamDelegate(inputStream);
        gattClient.setOutputStreamDelegate(outputStream);

        // Create the reliable Channel
        Channel reliableChannel = new BleChannel(
                identifier,
                inputStream,
                outputStream
        );

        // Create the transport
        Transport transport = new BleTransport(
                identifier,
                reliableChannel
        );

        // Create the umbrella Device instance
        return new BleDevice(
                identifier,
                connector,
                transport
        );
    }

    @Override
    public void onDisconnection(@NonNull Connector connector, UlxError error) {
        Timber.e(
                "ULX BLE browser disconnection for connector %s",
                connector.getIdentifier()
        );

        if (connector instanceof BleActiveConnector) {
            final String address = ((BleActiveConnector) connector).getGattClient().getBluetoothDevice().getAddress();

            // Remove the device from the list, so that we can connect to it again in the future
            this.knownDevices.remove(address);
        }
    }

    @Override
    public void onConnectionFailure(@NonNull Connector connector, UlxError error) {
        Timber.e(
                "ULX BLE browser connection failure for connector %s with state %s",
                connector.getIdentifier(),
                connector.getState().toString()
        );

        BleActiveConnector bleActiveConnector = (BleActiveConnector)connector;
        BluetoothDevice bluetoothDevice = bleActiveConnector.getGattClient().getBluetoothDevice();

        // Remove from the registry; when the device is seen again, the
        // connection should be retried
        this.knownDevices.remove(bluetoothDevice.getAddress());
        Timber.d("Device %s removed from registry", bluetoothDevice.getAddress());

        // Since we're having failed connections, we should ask the adapter to
        // restart.
        boolean restarted = false;
        Delegate delegate = getDelegate();
        if (delegate != null) {
            restarted = delegate.onAdapterRestartRequest(this);
        }

        final boolean adapterRestarted = restarted;
        Dispatch.post(() -> {
            connectionsInProgress--;
            if (connectionsInProgress == 0 && !adapterRestarted) { // We went from 1 to 0 - maybe time to start scanning
                updateScannerStatus();
            }
        });
    }
}
