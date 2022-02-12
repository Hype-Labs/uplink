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
import android.util.Log;

import com.uplink.ulx.TransportType;
import com.uplink.ulx.UlxError;
import com.uplink.ulx.UlxErrorCode;
import com.uplink.ulx.drivers.bluetooth.ble.gattClient.GattClient;
import com.uplink.ulx.drivers.bluetooth.ble.model.BleChannel;
import com.uplink.ulx.drivers.bluetooth.ble.model.BleDevice;
import com.uplink.ulx.drivers.bluetooth.ble.model.BleTransport;
import com.uplink.ulx.drivers.bluetooth.ble.model.domestic.BleDomesticService;
import com.uplink.ulx.drivers.bluetooth.ble.model.foreign.BleForeignConnector;
import com.uplink.ulx.drivers.bluetooth.ble.model.foreign.BleForeignInputStream;
import com.uplink.ulx.drivers.bluetooth.ble.model.foreign.BleForeignOutputStream;
import com.uplink.ulx.drivers.bluetooth.commons.BluetoothPermissionChecker;
import com.uplink.ulx.drivers.bluetooth.commons.BluetoothStateListener;
import com.uplink.ulx.drivers.commons.controller.BrowserCommons;
import com.uplink.ulx.drivers.controller.Browser;
import com.uplink.ulx.drivers.model.Channel;
import com.uplink.ulx.drivers.model.Connector;
import com.uplink.ulx.drivers.model.Transport;
import com.uplink.ulx.threading.Dispatch;
import com.uplink.ulx.utils.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class BleBrowser extends BrowserCommons implements
        Connector.StateDelegate,
        BluetoothStateListener.Observer
{
    private final BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private ScanSettings.Builder bluetoothLeBrowsingSettings;
    private final BleDomesticService domesticService;

    private ArrayList<ScanFilter> scanFilters;
    private ScanCallback scanCallback;

    private Map<String, BluetoothDevice> knownDevices;
    private Map<String, Connector> knownConnectors;
    private List<Connector> connectorQueue;
    private Connector currentConnector;

    /**
     * The BLEScannerCallback implements the Bluetooth LE scan callbacks that
     * are used by the system to report scan results. It
     */
    private class BLEScannerCallback extends ScanCallback {

        @Override
        public void onScanResult(int callbackType, final ScanResult scanResult) {

            // The host may find itself in a scan, and that must be ignored
            if (isHostDevice(scanResult)) {
                Log.i(BleBrowser.this.getClass().getCanonicalName(), "ULX ignoring host device");
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
            onFailedStart(BleBrowser.this, error);
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
     * Constructor. Initializes the instance with the given parameters. It also
     * subscribes the browser as a BluetoothStateListener observer, so that it
     * can track adapter state changes. The given BluetoothManager is expected
     * to be shared with the advertiser, since it's the abstract entity that
     * will be used to managed the adapter. The BleDomesticService, on the
     * other hand, is used to configure the services, and works as a
     * specification of the type of services that the browser should be looking
     * for.
     * @param identifier A string identifier for the instance.
     * @param bluetoothManager The BluetoothManager instance.
     * @param domesticService The service configuration specification.
     * @param context The Android environment Context.
     */
    public BleBrowser(
            String identifier,
            BluetoothManager bluetoothManager,
            BleDomesticService domesticService,
            Context context)
    {
        super(identifier, TransportType.BLUETOOTH_LOW_ENERGY, context);

        this.bluetoothManager = bluetoothManager;
        this.domesticService = domesticService;

        this.currentConnector = null;

        // TODO Leaking "this" in the constructor
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
    private List<ScanFilter> getScanFilters() {
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
        if (this.scanCallback == null) {
            this.scanCallback = new BLEScannerCallback();
        }
        return this.scanCallback;
    }

    /**
     * Returns a map of identifiers (String) to BluetoothDevice, which
     * corresponds to the registry of devices that have been found by the
     * implementation. This will prevent devices from being found multiples
     * times.
     * @return An hash map of known devices.
     */
    private Map<String, BluetoothDevice> getKnownDevices() {
        if (this.knownDevices == null) {
            this.knownDevices = new HashMap<>();
        }
        return this.knownDevices;
    }

    /**
     * Accessor for the connector queue, which holds connections that are
     * pending. These connections will be dispatched in sequence, in the same
     * order in which the devices are seen on the network.
     * @return A list of pending connections.
     */
    private List<Connector> getConnectorQueue() {
        if (this.connectorQueue == null) {
            this.connectorQueue = new LinkedList<>();
        }
        return this.connectorQueue;
    }

    /**
     * This method returns a map of identifiers-to-connectors, mapping all
     * Connector that have been found (and not lost) on the network.
     * @return A map of identifiers to their respective connectors.
     */
    private Map<String, Connector> getKnownConnectors() {
        if (this.knownConnectors == null) {
            this.knownConnectors = new HashMap<>();
        }
        return this.knownConnectors;
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

    /**
     * The current connector is a {@link Connector} that is currently attempting
     * to connect. This getter returns such a connector. When not {@code null},
     * the connector is busy and thus no new connections should overlap.
     * @return The active {@link Connector}.
     */
    private Connector getCurrentConnector() {
        return this.currentConnector;
    }

    /**
     * Sets the active {@link Connector}. If a previous {@link Connector} was
     * defined as active, it will be overridden.
     * @param connector The {@link Connector} to set.
     */
    private void setCurrentConnector(Connector connector) {
        this.currentConnector = connector;
    }

    @SuppressLint("MissingPermission")
    @Override
    public void requestAdapterToStart() {
        Log.i(getClass().getCanonicalName(), "ULX BLE browser is requesting the adapter to start");

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

        // Start scanning
        startScanning();

        // Notify the delegate
        onStart(this);
    }

    private void startScanning() {
        Log.i(getClass().getCanonicalName(), "ULX BLE scanner starting");

        Dispatch.post(() -> {

            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                getBluetoothLeScanner().startScan(
                        getScanFilters(),
                        getScanSettings(),
                        getScanCallback()
                );
            } else {
                getBluetoothLeScanner().startScan(
                        getScanCallback()
                );
            }
        });
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

        onFailedStart(this, error);
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

        onFailedStart(this, error);

        // Restart the adapter
        getBluetoothAdapter().enable();
    }

    @SuppressLint("MissingPermission")
    @Override
    public void requestAdapterToStop() {
        Log.i(getClass().getCanonicalName(), "ULX BLE browser requesting the adapter to stop");

        // Is the adapter enabled?
        if (getBluetoothAdapter().isEnabled()) {
            return;
        }

        // Stop the scanner
        stopScanning();

        // Notify the delegate; we're stopping gracefully
        onStop(this, null);
    }

    private void stopScanning() {
        getBluetoothLeScanner().stopScan(getScanCallback());
    }

    /**
     * Handles the event of a device being found on the network, by making
     * several checks and deciding whether to initiate a connection. If the
     * connection comes through, the host device will initiate a connection.
     * @param bluetoothDevice The device to check.
     * @param scanRecord The ScanRecord corresponding to the given device.
     */
    private synchronized void handleDeviceFound(BluetoothDevice bluetoothDevice, ScanRecord scanRecord) {
        //Log.i(BleBrowser.this.getClass().getCanonicalName(), String.format("ULX BLE browser found native device %s", bluetoothDevice.getAddress()));

        // Was the bluetoothDevice already seen?
        if (getKnownDevices().get(bluetoothDevice.getAddress()) != null) {
            //Log.i(getClass().getCanonicalName(), String.format("ULX BLE browser ignoring device %s because it's already known", bluetoothDevice.getAddress()));
            return;
        }

        // Is the record found by the scanner publishing the expected service?
        if (!isCoreServiceScanRecord(scanRecord)) {
            //Log.i(getClass().getCanonicalName(), String.format("ULX BLE browser ignoring device %s because it doesn't publish a core service", bluetoothDevice.getAddress()));
            return;
        }

        // Is the host supposed to initiate, or is the foreign bluetoothDevice?
        if (!isConnectableScanRecord(scanRecord)) {
            //Log.i(getClass().getCanonicalName(), String.format("ULX BLE browser ignoring device %s because the remote is the initiator", bluetoothDevice.getAddress()));
            return;
        }

        // Connect
        queueConnection(bluetoothDevice);
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
     * This method decides whether the given ScanRecord displays a control value
     * that is lexicographically lower than the host service, meaning that it
     * should be the initiator of a connection. If that is not verified, than
     * the host device should initiate the connection instead. This is done
     * this way in order to prevent the connection from occurring in both
     * directions, eliminating noise and unnecessary redundancy.
     * @param scanRecord The ScanRecord to check for lexicographic control order.
     * @return Whether the host should initiate the connection.
     */
    private boolean isConnectableScanRecord(ScanRecord scanRecord) {

        String gattServiceIdentifier = BleDomesticService.getGattServiceIdentifier16bits().toLowerCase();
        ParcelUuid uuid = ParcelUuid.fromString(gattServiceIdentifier);

        byte[] serviceData = scanRecord.getServiceData(uuid);
        byte[] foreignControlValue = Arrays.copyOfRange(
                serviceData, 0, BleDomesticService.CONTROL_VALUE_SIZE
        );

        return compareControlValue(getDomesticService().getControlValue(), foreignControlValue);
    }

    /**
     * Compares a domestic (host) control characteristic value and a foreign
     * one, returning true if the domestic control value (left-hand side)
     * compares less than the other. This can be used to assert whether a
     * device should be the initiator for a given connection.
     * @param domesticControlValue The domestic control value.
     * @param foreignControlValue The foreign control value.
     * @return Whether the domestic control value is lexicographically lower.
     */
    private boolean compareControlValue(byte [] domesticControlValue, byte [] foreignControlValue) {

        int comparison = StringUtils.compare(
                domesticControlValue,
                foreignControlValue,
                BleDomesticService.CONTROL_VALUE_SIZE
        );

        return comparison < 0;
    }

    /**
     * Instantiates the Connector abstraction and adds the connector to a queue.
     * The connector will be requested to connect when others already on the
     * queue finish connecting (either successfully or with an error). The
     * connector will also be assigned a unique identifier (generated randomly)
     * that is used internally by the implementation to keep track of known
     * connectors, and the browser (this browser) will assume the connector's
     * delegates as needed. This is the first step in abstracting the native
     * model, since the Device abstraction will only appear later, after the
     * streams are functional.
     * @param bluetoothDevice The native device to connect.
     */
    @SuppressWarnings("MissingPermission")
    private void queueConnection(BluetoothDevice bluetoothDevice) {
        Log.i(getClass().getCanonicalName(), String.format("ULX BLE scanner is queueing connection %s", bluetoothDevice.getAddress()));

        // Don't proceed if the device is already known; this would mean that
        // some connection attempt is already in progress.
        if (getKnownDevices().get(bluetoothDevice.getAddress()) != null) {
            Log.i(getClass().getCanonicalName(), "ULX ignoring a queue request because the device is already known");
            return;
        }

        // Add to the registry
        getKnownDevices().put(bluetoothDevice.getAddress(), bluetoothDevice);

        // Instantiate the GATT client
        GattClient gattClient = new GattClient(
                bluetoothDevice,
                getBluetoothManager(),
                getDomesticService(),
                getContext()
        );

        // Create the connector
        Connector connector = new BleForeignConnector(
                UUID.randomUUID().toString(),
                gattClient
        );

        // Take ownership of the streams
        connector.setStateDelegate(this);
        connector.addInvalidationCallback(this);

        // Keep the connector in the registry
        getKnownConnectors().put(connector.getIdentifier(), connector);

        // Queue the connector
        getConnectorQueue().add(connector);
        Log.i(getClass().getCanonicalName(), String.format("ULX BLE scanner queued connector %s for native device %s", connector.getIdentifier(), bluetoothDevice.getAddress()));

        // Attempt to connect
        attemptConnection();
    }

    /**
     * Attempts to remove a Connector from the queue, if one exists. This method
     * will return the first still valid and non-connected Connector that it
     * finds in queue, skipping all others. If none is found, it returns null.
     * @return The next valid Connector in the queue or null, if none exists.
     */
    private Connector dequeueConnector() {

        List<Connector> connectorQueue = getConnectorQueue();

        if (connectorQueue.size() == 0) {
            return null;
        }

        // Remove the oldest
        Connector connector = connectorQueue.remove(0);

        // The connector might have connected or invalidated meanwhile
        if (connector == null || connector.getState() != Connector.State.DISCONNECTED) {

            // Try again, until the queue is known to be empty or one connection
            // request succeeds
            return dequeueConnector();
        }

        return connector;
    }

    /**
     * Removes a {@link Connector} from the queue and asks it to connect. The
     * {@link Connector} will initiate the connection with the foreign device
     * by following the normal connection lifecycle. If there are no connectors
     * pending on the queue, this method does nothing.
     */
    private void attemptConnection() {
        Log.i(getClass().getCanonicalName(), "ULX is removing the next connector from the queue");

        // Don't proceed if busy
        if (getCurrentConnector() != null) {
            Log.i(getClass().getCanonicalName(), "ULX connector queue not proceeding because it's busy");
            return;
        }

        Connector connector = dequeueConnector();

        // Don't proceed if there's no connector
        if (connector == null) {
            Log.i(getClass().getCanonicalName(), "ULX attempted to dequeue a connector, but the queue was empty");

            // Resume scanning, no connection is in progress
            startScanning();
            return;
        }

        // Set as busy
        setCurrentConnector(connector);

        // Stop scanning while connecting
        stopScanning();

        // Request the connector at the top of the queue to connect
        Log.i(getClass().getCanonicalName(), String.format("ULX connector queue proceeding with connector %s", connector.getIdentifier()));
        connector.connect();
    }

    @Override
    public void destroy() {
    }

    @Override
    public void onInvalidation(Connector connector, UlxError error) {
        removeActiveConnector(connector);
    }

    @Override
    public void onAdapterEnabled(BluetoothStateListener bluetoothStateListener) {
        if (getState() != Browser.State.RUNNING) {
            onReady(this);
        }
    }

    @Override
    public void onAdapterDisabled(BluetoothStateListener bluetoothStateListener) {
        Log.i(getClass().getCanonicalName(), "ULX BLE adapter disabled");

        if (getState() == Browser.State.RUNNING || getState() == Browser.State.STARTING) {

            UlxError error = new UlxError(
                    UlxErrorCode.ADAPTER_DISABLED,
                    "Could not start or maintain the Bluetooth scanner.",
                    "The adapter was turned off.",
                    "Turn the Bluetooth adapter on."
            );

            onStop(this, error);
        }

        // The adapter being turned off, means that all connections where lost
        notifyAllDevicesAsDisconnected();

        // Enable
        Log.i(getClass().getCanonicalName(), "ULX is enabling the BLE adapter");
        getBluetoothAdapter().enable();
    }

    private void notifyAllDevicesAsDisconnected() {
        // TODO
    }

    @Override
    public void onConnected(Connector connector) {
        Log.i(getClass().getCanonicalName(), String.format("ULX BLE browser connector %s connected", connector.getIdentifier()));

        Dispatch.post(() -> {

            // This connector is now active
            addActiveConnector(connector);

            // Dequeue another connector, in case any is waiting
            setCurrentConnector(null);
            attemptConnection();

            BleDevice device = createDevice((BleForeignConnector)connector);

            // Propagate to the delegate
            super.onDeviceFound(this, device);
        });
    }

    /**
     * This method is a Device factory that creates a BleDevice from a
     * BleForeignConnector. The connector is expected to already have connected
     * to the remote peer, since that's a precondition to create the streams.
     * @param connector The connector to the remote device.
     * @return An umbrella BleDevice instance corresponds to the given connector.
     */
    private BleDevice createDevice(BleForeignConnector connector) {

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
        BleForeignInputStream inputStream = new BleForeignInputStream(
                identifier,
                gattClient,
                physicalReliableOutputCharacteristic,
                this
        );

        // Create the output stream
        BleForeignOutputStream outputStream = new BleForeignOutputStream(
                identifier,
                gattClient,
                physicalReliableInputCharacteristic,
                this
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
    public void onDisconnection(Connector connector, UlxError error) {
        Log.e(getClass().getCanonicalName(), String.format("ULX BLE browser disconnection for connector %s", connector.getIdentifier()));

        // The connector is no longer active
        removeActiveConnector(connector);
    }

    @Override
    public void onConnectionFailure(Connector connector, UlxError error) {
        Log.e(getClass().getCanonicalName(), String.format("ULX BLE browser connection failure for connector %s with state %s", connector.getIdentifier(), connector.getState().toString()));

        BleForeignConnector bleForeignConnector = (BleForeignConnector)connector;
        BluetoothDevice bluetoothDevice = bleForeignConnector.getGattClient().getBluetoothDevice();

        // Remove from the registry; when the device is seen again, the
        // connection should be retried
        getKnownDevices().remove(bluetoothDevice.getAddress());
        getKnownConnectors().remove(connector.getIdentifier());
        removeActiveConnector(connector);   // Shouldn't matter

        // Free the queue
        setCurrentConnector(null);

        // Since we're having failed connections, we should ask the adapter to
        // restart.
        Delegate delegate = getDelegate();
        if (delegate != null) {
            delegate.onAdapterRestartRequest(this);
        }
    }
}
