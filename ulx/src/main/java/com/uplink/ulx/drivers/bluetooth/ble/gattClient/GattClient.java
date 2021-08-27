package com.uplink.ulx.drivers.bluetooth.ble.gattClient;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.uplink.ulx.UlxError;
import com.uplink.ulx.UlxErrorCode;
import com.uplink.ulx.drivers.bluetooth.ble.model.domestic.BleDomesticService;
import com.uplink.ulx.drivers.bluetooth.ble.model.foreign.BleForeignService;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The GattClient implements most of the logic that interacts with the system
 * framework for managing the adapter from the client perspective. This includes
 * connecting to a remote GATT server, as well as subscribing and validating
 * its characteristics for I/O purposes. It communicates up the hierarchy
 * through a series of delegates.
 */
public class GattClient extends BluetoothGattCallback {

    /**
     * This delegate listen to connection events, related with connection
     * processes that are managed by the GattClient.
     */
    public interface ConnectorDelegate {

        /**
         * This callback gives indication that the GATT client connected to the
         * remote device. After the connection has been established, I/O
         * operations may still not be performed, since the streams must be
         * subscribed, but from this moment on, the devices are connected.
         * @param gattClient The GATT client that just connected.
         */
        void onConnected(GattClient gattClient);

        /**
         * The given GATT client could not be connected. The reason for the
         * failure should be described by the given UlxError instance. The
         * implementation will not attempt to reconnect.
         * @param gattClient The GATT client that failed connecting.
         * @param error An error, indicating a probable cause for the failure.
         */
        void onConnectionFailure(GattClient gattClient, UlxError error);

        /**
         * The connection established by the given GATT client was lost and
         * could not be recovered. Any attempt to perform I/O operations on
         * this client will fail.
         * @param gattClient The GATT client that failed connecting.
         * @param error An error, indicating a probable cause for the failure.
         */
        void onDisconnected(GattClient gattClient, UlxError error);
    }

    /**
     *
     */
    public interface InputStreamDelegate {
        /*
        void serverAskForOpen(GattClient gattClient);
        void serverAskForClose(GattClient gattClient);
        void hasDataAvailable(GattClient gattClient, byte [] buffer);
        void opened(GattClient gattClient);
        void readingFailed(GattClient gattClient, BluetoothGattCharacteristic characteristic);
         */
    }

    /**
     *
     */
    public interface OutputStreamDelegate {
        /*
        void serverAskForOpen(GattClient gattClient);
        void serverAskForClose(GattClient gattClient);
        void hasSpaceAvailable(GattClient gattClient);
        void failedWrite(GattClient gattClient, BluetoothGattCharacteristic characteristic);
         */
    }

    private WeakReference<GattClient.ConnectorDelegate> connectorDelegate;

    private final WeakReference<Context> context;

    private final BluetoothDevice bluetoothDevice;
    private final BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;

    private final BleDomesticService domesticService;

    /**
     * Constructor. Initializes with the given parameters.
     * @param bluetoothDevice
     * @param bluetoothManager
     * @param domesticService
     * @param context
     */
    public GattClient(
            BluetoothDevice bluetoothDevice,
            BluetoothManager bluetoothManager,
            BleDomesticService domesticService,
            Context context
    ) {
        Objects.requireNonNull(bluetoothDevice);
        Objects.requireNonNull(bluetoothManager);
        Objects.requireNonNull(domesticService);
        Objects.requireNonNull(context);

        this.bluetoothDevice = bluetoothDevice;
        this.bluetoothManager = bluetoothManager;
        this.bluetoothAdapter = null;
        this.bluetoothGatt = null;

        this.domesticService = domesticService;

        this.context = new WeakReference<>(context);
    }

    /**
     * Sets the ConnectorDelegate that is to listen to connection related
     * events. If a delegate was previously set, any new calls to this method
     * will override the previous setting.
     * @param connectorDelegate The ConnectorDelegate to set.
     */
    public final void setConnectorDelegate(ConnectorDelegate connectorDelegate) {
        this.connectorDelegate = new WeakReference<>(connectorDelegate);
    }

    /**
     * The current ConnectorDelegate, which will be listening to connection
     * events. If no delegate was previously set, this method returns null.
     * @return The current ConnectorDelegate, or null, if one was not set.
     */
    public ConnectorDelegate getConnectorDelegate() {
        return this.connectorDelegate != null ? this.connectorDelegate.get() : null;
    }

    /**
     * Returns the BluetoothDevice associated with this GattClient. This
     * corresponds to the device abstraction that is represented by this class
     * instance.
     * @return The BluetoothDevice instance.
     */
    private BluetoothDevice getBluetoothDevice() {
        return this.bluetoothDevice;
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
     * Returns the BLE domestic service descriptor as given at construction
     * time. This will be used to validate the foreign service against its
     * domestic counterpart.
     * @return The BLE domestic service.
     */
    private BleDomesticService getDomesticService() {
        return this.domesticService;
    }

    /**
     * Returns the Android environment Context given at construction time.
     * @return The Android environment Context.
     */
    private Context getContext() {
        return this.context.get();
    }

    /**
     * Connects to the GATT server on this device and returns the BluetoothGatt
     * handler for the Bluetooth GATT profile.
     * @return The Bluetooth GATT profile abstraction.
     */
    public BluetoothGatt getBluetoothGatt() {
        if (this.bluetoothGatt == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                this.bluetoothGatt = getBluetoothDevice().connectGatt(
                        getContext(),
                        false,
                        this,
                        BluetoothDevice.TRANSPORT_LE
                );
            } else {
                this.bluetoothGatt = getBluetoothDevice().connectGatt(
                        getContext(),
                        false,
                        this
                );
            }
        }
        return this.bluetoothGatt;
    }

    /**
     * Requests the BluetoothGatt to connect, which corresponds to a connection
     * to the device that it represents.
     */
    public void connect() {

        BluetoothGatt bluetoothGatt = getBluetoothGatt();

        if (!bluetoothGatt.connect()) {

            // We should try to figure out a better error message
            UlxError error = new UlxError(
                    UlxErrorCode.UNKNOWN,
                    "Could not connect to the device.",
                    "The connection could not be initiated.",
                    "Please try connecting again."
            );

            handleConnectionFailure(error);
        }
    }

    @Override
    public void onConnectionStateChange(final BluetoothGatt bluetoothGatt, final int status, final int newState) {
        super.onConnectionStateChange(bluetoothGatt, status, newState);

        ConnectorDelegate connectorDelegate = getConnectorDelegate();

        // Don't proceed without a delegate; although this means that the
        // state change will simply be ignored.
        if (connectorDelegate == null) {
            Log.i(getClass().getCanonicalName(), "ULX is ignoring a connection state change because the GATT client callback delegate was not set");
            return;
        }

        // Is the client connected?
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            Log.i(getClass().getCanonicalName(), String.format("ULX GATT client connected [%s]", bluetoothGatt.getDevice().getAddress()));

            // Proceed with the service discovery
            discoverServices();
        } else {
            Log.i(getClass().getCanonicalName(), String.format("ULX GATT client connection failed [%s]", bluetoothGatt.getDevice().getAddress()));

            UlxError error = new UlxError(
                    UlxErrorCode.UNKNOWN,
                    "Could not connect or hold the connection to the remote device.",
                    "The connection could not be established or was lost.",
                    "Please try reconnecting or restarting the Bluetooth adapter."
            );

            handleConnectionFailure(error);
        }
    }

    /**
     * This method requests the GATT client to discover the remote services
     * offered by the GATT server. This method is asynchronous, meaning that
     * the discovery result will be notified through a delegate call (either
     * onServicesDiscovered() or onFailedServiceDiscovery(), depending on the
     * result). If the service discovery process cannot be initiated, this
     * method triggers an immediate notification to the delegate, with
     * onFailedServiceDiscovery(). Otherwise, the services will be validated
     * and the connection lifecycle will continue.
     */
    public void discoverServices() {

        BluetoothGatt bluetoothGatt = getBluetoothGatt();

        if (!bluetoothGatt.discoverServices()) {

            // It's not that the service discovery failed, but rather that it
            // couldn't be initiated
            UlxError error = new UlxError(
                    UlxErrorCode.UNKNOWN,
                    "The connection to the remote device failed.",
                    "Failed to discover the Bluetooth LE services offered by the remote device.",
                    "Try connecting again, or restarting the Bluetooth adapter."
            );

            // Handle the failure
            handleConnectionFailure(error);
        }
    }

    /**
     * Propagates the given UlxError as an onFailedConnection() notification
     * on the ConnectionDelegate.
     * @param error The error to propagate.
     */
    private void handleConnectionFailure(UlxError error) {
        Log.e(getClass().getCanonicalName(), "ULX connection failure");

        ConnectorDelegate connectorDelegate = getConnectorDelegate();
        if (connectorDelegate != null) {
            connectorDelegate.onConnectionFailure(this, error);
        }
    }

    @Override
    public void onServicesDiscovered(final BluetoothGatt gatt, int status) {

        // Services discovered
        if (status == BluetoothGatt.GATT_SUCCESS) {
            handleServicesDiscovered(gatt, gatt.getServices());
        }

        // The service wasn't discovered, something went wrong
        else {

            // We should be checking the status for this
            UlxError error = new UlxError(
                    UlxErrorCode.UNKNOWN,
                    "Could not connect to the device using Bluetooth Low Energy.",
                    "The service discovery process failed.",
                    "Make sure that the other device is properly configured and still within range."
            );

            handleConnectionFailure(error);
        }
    }

    /**
     * This method proceeds with the discovery lifecycle after the services
     * have been discovered on the remote device. It looks for the remote
     * service matching the expected description and checks whether the host
     * device should be the one to initiate a connection. This will happen if
     * the host device has a reliable control characteristic that compares
     * lexicographically lower than the its remote counterpart. The host will
     * also initiate the connection if it does not support advertising, since
     * that means that it won't be visible on the network. In case the host is
     * the initiator, it will being subscribing the remote's characteristics,
     * preparing for I/O.
     * @param gatt Not used.
     * @param services The list of services from which the implementation will
     *                 extract the matching service, if one exists.
     */
    private void handleServicesDiscovered(BluetoothGatt gatt, List<BluetoothGattService> services) {

        BleForeignService foreignService = getForeignService(services);

        // Is the service valid or a protocol violation?
        if (foreignService == null) {

            UlxError error = new UlxError(
                    UlxErrorCode.PROTOCOL_VIOLATION,
                    "Could not connect to remote device.",
                    "The device failed to comply with the protocol by specifying the wrong type of service.",
                    "Please verify that the remote device is properly configured."
            );

            handleConnectionFailure(error);

            return;
        }

        BluetoothGattCharacteristic domesticReliableControl = getDomesticService().getReliableControl();
        BluetoothGattCharacteristic foreignReliableControl = foreignService.getReliableControl();

        Objects.requireNonNull(domesticReliableControl);
        Objects.requireNonNull(foreignReliableControl);

        // The Reliable Control characteristic UUID is used to compare the
        // domestic (host) and foreign (discovered) service UUIDs. This will
        // determine which device is active (initiator) and which is passive.
        String domesticServiceUuid = domesticReliableControl.getUuid().toString();
        String foreignServiceUuid = foreignReliableControl.getUuid().toString();

        // Compare the two IDs in lexicographic order (case-insensitive). The
        // lowest one will be the initiator.
        int comparison = domesticServiceUuid.compareToIgnoreCase(foreignServiceUuid);

        // In the first case, the host will be the initiator (peripheral). This
        // happens if out of the two UUIDs it compares lexicographically
        // smaller, or if the device is physically incapable of advertising
        // (which means that it cannot be seen, and therefore it must be the
        // one to initiate). The host will also be the initiator if it does not
        // support advertising, which means that the remote peer will not see it
        if (comparison < 0 || !getBluetoothAdapter().isMultipleAdvertisementSupported()) {
            subscribeCharacteristic(foreignReliableControl);
        }

        // If the UUID is larger and the host supports advertising, wait
        // passively for the other device to connect.
        else if (comparison > 0) {

            // Don't disconnect because all connections with this device would
            // then be lost.
            //getGattClient().disconnect();

            // This isn't really an error, but we must respond back to a
            // connection request. Regardless, this workflow should be reviewed
            // in the future, since flagging an error here is not adequate.
            UlxError error = new UlxError(
                    UlxErrorCode.NOT_CONNECTABLE,
                    "Could not connect to remote device.",
                    "The remote device is the one expected to initiate the connection.",
                    "Wait for the remote device to connect."
            );

            // Propagate to the delegate.
            handleConnectionFailure(error);
        }
    }

    /**
     * This method looks for a service matching the domestic service UUID on the
     * given list of services. This corresponds to looking up a list of
     * discovered services for one matching the host's description, which works
     * as a means of validation. If no service in the list matches the
     * description, this method will return null. Otherwise, a BleForeignService
     * is instantiated, which means that this method also works as a factory.
     * @param services The list of services to verify.
     * @return An instance of BleForeignService if one service checks out.
     */
    private BleForeignService getForeignService(List<BluetoothGattService> services) {

        for (BluetoothGattService gattService : services) {

            String foreignServiceUuid = gattService.getUuid().toString();
            String domesticServiceUuid = BleDomesticService.getGattServiceIdentifier();

            // Service UUID match?
            if (!foreignServiceUuid.equalsIgnoreCase(domesticServiceUuid)) {
                continue;
            }

            // Even if the UUIDs match, the validation may still fail, in which
            // case this will return null. That being the case, this constitutes
            // a protocol violation (e.g. a service with a valid UUID failed to
            // validate.
            return BleForeignService.validateAndMake(gattService);
        }

        return null;
    }

    /**
     * Makes a request to subscribe the given characteristic, by configuring
     * the characteristic on the remote server. This consists of writing the
     * Client Characteristic Configuration Descriptor (CCCD) and enabling
     * indications on the descriptor.
     * @param characteristic The characteristic to subscribe.
     */
    public void subscribeCharacteristic(BluetoothGattCharacteristic characteristic) {
        Log.i(getClass().getCanonicalName(), "ULX requested to subscribe remote characteristic");

        BluetoothGattDescriptor descriptor = getDescriptor(characteristic);

        // The descriptor was not found
        if (descriptor == null) {
            UlxError error = new UlxError(
                    UlxErrorCode.PROTOCOL_VIOLATION,
                    "Could not connect to the remote device.",
                    "The remote device does not conform with the expected protocol.",
                    "Make sure that the remote device is running the correct software version."
            );
            handleCharacteristicSubscriptionFailure(error);
            return;
        }

        // To enable notifications on Android, you normally have to locally
        // enable the notification for the particular characteristic you are
        // interested in. Once that’s done, you also have to enable
        // notifications on the peer device by writing to the device’s Client
        // Characteristic Configuration Descriptor (CCCD)
        if (!getBluetoothGatt().setCharacteristicNotification(characteristic, true)) {
            UlxError error = new UlxError(
                    UlxErrorCode.UNKNOWN,
                    "Could not connect to the remote device.",
                    "Could not enable notifications for I/O on the remote device.",
                    "Make sure that the remote device is running the correct software version or try restarting the Bluetooth adapters."
            );
            handleCharacteristicSubscriptionFailure(error);
            return;
        }

        // Store the Enable Indication value locally
        if (!descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)) {
            UlxError error = new UlxError(
                    UlxErrorCode.UNKNOWN,
                    "Could not connect to the remote device.",
                    "Failed to properly configure the server to enable indications.",
                    "Make sure that the remote device is running the correct software version or try restarting the Bluetooth adapters."
            );
            handleCharacteristicSubscriptionFailure(error);
            return;
        }

        // Commit to the remote device
        if (!getBluetoothGatt().writeDescriptor(descriptor)) {
            UlxError error = new UlxError(
                    UlxErrorCode.UNKNOWN,
                    "Could not connect to the remote device.",
                    "Failed to properly configure the server to enable indications.",
                    "Make sure that the remote device is running the correct software version or try restarting the Bluetooth adapters."
            );
            handleCharacteristicSubscriptionFailure(error);
            //return;
        }
    }

    /**
     * Returns the BluetoothGattDescriptor that is expected for the given
     * characteristic, or returns null if none matches. The characteristic
     * can be either the Reliable Output or Reliable Control characteristic,
     * since the implementation will validate which of the two is being passed.
     * It will also assert that the proper properties were defined, so that
     * the protocol is asserted for compliance.
     * @param characteristic The characteristic.
     * @return The descriptor corresponding to the characteristic or null.
     */
    private BluetoothGattDescriptor getDescriptor(BluetoothGattCharacteristic characteristic) {

        UUID descriptorUuid = null;

        // Reliable output
        if (getDomesticService().isReliableOutput(characteristic)) {
            if (0 != (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE)) {
                descriptorUuid = getDomesticService().getDescriptorReliableOutputRead().getUuid();
            }
        }

        // Reliable control
        else if (0 != (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE)) {
            descriptorUuid = getDomesticService().getDescriptorReliableControl().getUuid();
        }

        return characteristic.getDescriptor(descriptorUuid);
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        super.onDescriptorWrite(gatt, descriptor, status);

        if (status == BluetoothGatt.GATT_SUCCESS) {
            handleCharacteristicSubscribed(gatt, descriptor);
        }

        else {

            UlxError error = new UlxError(
                    UlxErrorCode.UNKNOWN,
                    "Could not connect to remote device.",
                    "Could not write to the remote descriptor.",
                    "Try connecting again or updating the software on the devices."
            );

            handleCharacteristicSubscriptionFailure(error);
        }
    }

    private void handleCharacteristicSubscribed(BluetoothGatt gatt, BluetoothGattDescriptor descriptor) {
        Log.i(getClass().getCanonicalName(), String.format("ULX characteristic subscribed with descriptor %s", descriptor.getUuid().toString()));

        if (getDomesticService().isReliableOutput(descriptor)) {
            handleOpenStream();
        }

        else handleSuccessfulConnection();
    }

    /**
     * Propagates a delegate notification indicating that the connection has
     * completed successfully, including the subscription of the control
     * characteristic.
     */
    private void handleSuccessfulConnection() {
        ConnectorDelegate connectorDelegate = getConnectorDelegate();
        if (connectorDelegate != null) {
            connectorDelegate.onConnected(this);
        }
    }

    private void handleOpenStream() {
        /*
        GattClientDelegate.InputStreamDelegate delegate = getInputStreamDelegate();
            delegate.opened(this);
         */
    }

    /**
     * Handles the event of a failed connection attempt by notifying the
     * delegate of an onConnectionFailure() event. This method does not perform
     * clean up, and rather the callers should do that.
     * @param error The error (UlxError) to propagate.
     */
    private void handleCharacteristicSubscriptionFailure(UlxError error) {
        Log.e(getClass().getCanonicalName(), "ULX characteristic subscription failure");

        ConnectorDelegate connectorDelegate = getConnectorDelegate();
        if (connectorDelegate != null) {
            connectorDelegate.onConnectionFailure(this, error);
        }
    }

    public BluetoothGattService getServiceMatching(BluetoothGattService service) {
        return getServiceMatching(service.getUuid());
    }

    public BluetoothGattService getServiceMatching(UUID uuid) {
        return getBluetoothGatt().getService(uuid);
    }
}
