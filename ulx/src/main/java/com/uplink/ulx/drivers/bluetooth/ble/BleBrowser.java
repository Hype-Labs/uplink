package com.uplink.ulx.drivers.bluetooth.ble;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
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
import com.uplink.ulx.drivers.controller.Browser;
import com.uplink.ulx.drivers.model.Connector;
import com.uplink.ulx.drivers.bluetooth.commons.BluetoothPermissionChecker;
import com.uplink.ulx.drivers.bluetooth.commons.BluetoothStateListener;
import com.uplink.ulx.drivers.commons.controller.BrowserCommons;
import com.uplink.ulx.threading.ExecutorPool;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class BleBrowser extends BrowserCommons implements BluetoothStateListener.Observer {

    private final BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private ScanSettings.Builder bluetoothLeBrowsingSettings;
    private final BleDomesticService domesticService;

    private ArrayList<ScanFilter> scanFilters;
    private ScanCallback scanCallback;

    /**
     * The BLEScannerCallback implements the Bluetooth LE scan callbacks that
     * are used by the system to report scan results. It
     */
    private class BLEScannerCallback extends ScanCallback {

        @Override
        public void onScanResult(int callbackType, final ScanResult scanResult) {

            // The host may find itself in a scan, and that must be ignored
            if (isHostDevice(scanResult)) {
                return;
            }

            // Dispatch in the appropriate pool
            ExecutorPool.getExecutor(getTransportType()).execute(
                    () -> handleDeviceFound(scanResult.getDevice(), scanResult.getScanRecord())
            );
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
         * Checks if a given ScanResult corresponds to the host device. This
         * may happen if the host device sees its own advertisement, in which
         * case it will find itself. Such matches are filtered out of the batch
         * of results.
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

        // Leaking "this" in the constructor
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

            Log.i(getClass().getCanonicalName(), String.format("ULX Bluetooth LE scanner is using %s as a service UUID filter", uuid.toString()));

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

    @SuppressLint("MissingPermission")
    @Override
    public void requestAdapterToStart() {

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

        // Notify the delegate
        onStart(this);
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
    }

    @SuppressLint("MissingPermission")
    @Override
    public void requestAdapterToStop() {

        // Is the adapter enabled?
        if (getBluetoothAdapter().isEnabled()) {
            return;
        }

        // Stop the scanner
        getBluetoothLeScanner().stopScan(getScanCallback());

        // Notify the delegate; we're stopping gracefully
        onStop(this, null);
    }

    /**
     * Handles the event of a device being found on the network, by making
     * several checks and deciding whether to initiate a connection. If the
     * connection comes through, the host device will initiate a connection.
     * @param device The device to check.
     * @param scanRecord The ScanRecord corresponding to the given device.
     */
    private synchronized void handleDeviceFound(BluetoothDevice device, ScanRecord scanRecord) {
        Log.i(getClass().getCanonicalName(), String.format("ULX Bluetooth LE scanner found a device [%s]", device.toString()));
    }

    @Override
    public void destroy() {
    }

    @Override
    public void onInvalidation(Connector connector, UlxError error) {
    }

    @Override
    public void onAdapterEnabled(BluetoothStateListener bluetoothStateListener) {
        if (getState() != Browser.State.RUNNING) {
            onReady(this);
        }
    }

    @Override
    public void onAdapterDisabled(BluetoothStateListener bluetoothStateListener) {

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
    }

    private void notifyAllDevicesAsDisconnected() {
    }
}
