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
import android.os.Looper;
import android.os.SystemClock;

import com.uplink.ulx.UlxError;
import com.uplink.ulx.UlxErrorCode;
import com.uplink.ulx.drivers.bluetooth.ble.gattServer.MtuRegistry;
import com.uplink.ulx.drivers.bluetooth.ble.model.active.BleForeignService;
import com.uplink.ulx.drivers.bluetooth.ble.model.passive.BleDomesticService;
import com.uplink.ulx.drivers.bluetooth.commons.BluetoothConnectionState;
import com.uplink.ulx.drivers.commons.StateManager;
import com.uplink.ulx.model.State;
import com.uplink.ulx.utils.Completable;
import com.uplink.ulx.utils.SerialOperationsManager;
import com.uplink.ulx.utils.SetOnceRef;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import timber.log.Timber;

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
        void onDisconnection(GattClient gattClient, UlxError error);
    }

    /**
     * The InputStreamDelegate receives notifications for InputStream related
     * events. These include some of the stream's lifecycle events, as well as
     * I/O.
     */
    public interface InputStreamDelegate {

        /**
         * This event being triggered means that the device has successfully
         * subscribed the output characteristic for the remote peer. In the
         * local device the name is reversed because what constitutes output
         * for the remote peer is also input for the host device. As soon as
         * the characteristic is subscribed, the host is capable of receiving
         * input from the remote device (central), and thus I/O can be expected
         * at any time.
         * @param gattClient The GattClient triggering the notification.
         */
        void onOpen(GattClient gattClient);

        /**
         * This event is triggered by the {@link GattClient} to give indication
         * that a subscribed characteristic was updated by the remote peer. This
         * corresponds to the {@link GattClient} receiving input from the remote
         * peer. The delegate should interpret the operation by reading the
         * value from the characteristic.
         * @param gattClient The {@link GattClient} triggering the notification.
         * @param characteristic The characteristic that was updated.
         */
        void onCharacteristicChanged(GattClient gattClient, BluetoothGattCharacteristic characteristic);
    }

    /**
     * The OutputStreamDelegate receives notifications for OutputStream related
     * events. These include some of the stream's lifecycle events, as well as
     * I/O.
     */
    public interface OutputStreamDelegate {

        /**
         * This callback is triggered when the MTU is negotiated by the peers.
         * The {@link com.uplink.ulx.drivers.model.OutputStream} should keep
         * this value because it's the amount of data that it's supposed to
         * send per burst. If this method is never called, it means that the
         * MTU negotiation failed, and thus the stream should use the default.
         * @param gattClient The GattClient triggering the notification.
         * @param mtu The negotiated MTU.
         */
        void onMtuNegotiated(GattClient gattClient, int mtu);

        /**
         * This callback is triggered by the {@link GattClient} when a
         * characteristic as been successfully written. This means that the
         * write operation was acknowledged by the remote peer, and thus that
         * the delivery already occurred.
         * @param gattClient The GattClient triggering the notification.
         */
        void onCharacteristicWritten(GattClient gattClient);

        /**
         * This callback is triggered when the {@link GattClient} attempts to
         * write to the reliable output characteristic but it fails to do so.
         * The error argument will give indication of a probable cause for the
         * failure.
         * @param gattClient The GattClient triggering the notification.
         * @param error An error, indicting a probable cause for the failure.
         */
        void onCharacteristicWriteFailure(GattClient gattClient, UlxError error);
    }

    private GattClient.ConnectorDelegate connectorDelegate;
    private WeakReference<InputStreamDelegate> inputStreamDelegate;
    private WeakReference<OutputStreamDelegate> outputStreamDelegate;

    private final SetOnceRef<StateManager> stateManager;

    private final BleDomesticService domesticService;
    private final BluetoothDevice bluetoothDevice;
    private final BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;

    private int mtu;

    private final Context context;

    private final SerialOperationsManager operationsManager;
    private volatile Completable connectionOperation;
    private volatile Completable mtuOperation;
    private volatile Completable discoverServicesOperation;
    private volatile Completable subscribeCharacteristicOperation;
    private volatile Completable writeOperation;
    private volatile Completable disconnectOperation;

    public static GattClient newInstance(
            BluetoothDevice bluetoothDevice,
            BluetoothManager bluetoothManager,
            BleDomesticService domesticService,
            SerialOperationsManager operationsManager,
            Context context
    ) {
        final GattClient instance = new GattClient(
                bluetoothDevice,
                bluetoothManager,
                domesticService,
                operationsManager,
                context
        );
        instance.initialize();
        return instance;
    }

    /**
     * Constructor. Initializes with the given parameters.
     *
     * @param bluetoothDevice   The BluetoothDevice being abstracted.
     * @param bluetoothManager  An abstraction to perform high level Bluetooth management.
     * @param domesticService   The BLE service description.
     * @param operationsManager operations manager to serialize BLE operations
     * @param context           The Android environment context.
     */
    private GattClient(
            BluetoothDevice bluetoothDevice,
            BluetoothManager bluetoothManager,
            BleDomesticService domesticService,
            SerialOperationsManager operationsManager,
            Context context
    ) {
        Objects.requireNonNull(bluetoothDevice);
        Objects.requireNonNull(bluetoothManager);
        Objects.requireNonNull(domesticService);
        Objects.requireNonNull(context);

        this.connectorDelegate = null;
        this.inputStreamDelegate = null;
        this.outputStreamDelegate = null;

        this.stateManager = new SetOnceRef<>();

        this.bluetoothDevice = bluetoothDevice;
        this.bluetoothManager = bluetoothManager;
        this.bluetoothAdapter = null;
        this.bluetoothGatt = null;
        this.mtu = MtuRegistry.DEFAULT_MTU;

        this.domesticService = domesticService;
        this.operationsManager = operationsManager;

        this.context = context;
    }

    private void initialize() {
        stateManager.setRef(new StateManager(new StateManager.Delegate(){
            @Override
            public void requestStart(StateManager stateManager) {
                doConnect();
            }

            @Override
            public void requestStop(StateManager stateManager) {
                cleanup();
            }

            @Override
            public void onStart(StateManager stateManager) {
                negotiateMtu();
            }

            @Override
            public void onStop(StateManager stateManager, UlxError error) {
                cleanup();

                if (error != null) {
                    notifyOnDisconnection(error);
                } else {
                    // TODO
                }
            }

            private void cleanup() {
                Timber.d("Cleaning up!");
                // Clean up; without this, future attempts to connect between these
                // same two devices should result in more 133 error codes. See:
                // https://stackoverflow.com/questions/25330938/android-bluetoothgatt-status-133-register-callback
                disconnectOperation = operationsManager.enqueue(
                        new SerialOperationsManager.Task() {
                            @Override
                            public void run(Completable completable) {
                                if (bluetoothGatt != null) {
                                    Timber.d("Disconnecting from gatt!");
                                    try {
                                        bluetoothGatt.disconnect();
                                    } catch (Exception e) {
                                        // DeadObjectException can be thrown here
                                        Timber.w(e, "Exception occurred while disconnecting");
                                        completable.markAsComplete(false);
                                    }
                                } else {
                                    completable.markAsComplete();
                                }
                            }

                            @Override
                            public void onComplete(SerialOperationsManager.Status status) {
                                // Close should not be immediately called after disconnect, as we
                                // need either confirmation that the state changed to disconnected. Since this
                                // callback might never come (e.g. if we are disconnecting while connecting),
                                // then a timeout of 2 seconds is also set to close the connection.
                                // Without doing so, the connection could not be correctly closed and
                                // leave memory leaks in the BLE stack, which would result in errors later
                                // on or even needing to reboot the device to fix it.
                                operationsManager.enqueue(completable -> {
                                    if (bluetoothGatt != null) {
                                        try {
                                            bluetoothGatt.close();
                                        } catch (Exception e) {
                                            Timber.w(e, "Failed to close BluetoothGatt");
                                        }
                                    }
                                    completable.markAsComplete();
                                });
                            }
                        },
                        2000
                );
            }

            @Override
            public void onFailedStart(StateManager stateManager, UlxError error) {
                cleanup();
                notifyOnConnectionFailure(error);
            }

            @Override
            public void onStateChange(StateManager stateManager) {
                StateManager.Delegate.super.onStateChange(stateManager);
            }
        }, this.getClass().getSimpleName()));
    }

    /**
     * Sets the ConnectorDelegate that is to listen to connection related
     * events. If a delegate was previously set, any new calls to this method
     * will override the previous setting.
     *
     * @param connectorDelegate The ConnectorDelegate to set.
     */
    public final void setConnectorDelegate(ConnectorDelegate connectorDelegate) {
        this.connectorDelegate = connectorDelegate;
    }

    /**
     * The current ConnectorDelegate, which will be listening to connection
     * events. If no delegate was previously set, this method returns null.
     *
     * @return The current ConnectorDelegate, or null, if one was not set.
     */
    public final ConnectorDelegate getConnectorDelegate() {
        return connectorDelegate;
    }

    /**
     * Sets the InputStreamDelegate that is to receive future notifications that
     * respect to input stream events. The implementation will keep a weak
     * reference to the given instance, as to prevent cyclic dependencies. If
     * another InputStreamDelegate was previously set, calling this method will
     * override it.
     *
     * @param inputStreamDelegate The instance to receive the callback events.
     */
    public final void setInputStreamDelegate(InputStreamDelegate inputStreamDelegate) {
        this.inputStreamDelegate = new WeakReference<>(inputStreamDelegate);
    }

    /**
     * Returns a strong reference to the InputStreamDelegate that was last set,
     * and also the one that is, at the moment, receiving InputStream events.
     * If none was set, this method returns null.
     *
     * @return The current {@link InputStreamDelegate}.
     */
    public final InputStreamDelegate getInputStreamDelegate() {
        return this.inputStreamDelegate != null ? this.inputStreamDelegate.get() : null;
    }

    /**
     * Sets the {@link OutputStreamDelegate} that is to receive future
     * notifications that respect to output stream events. The implementation
     * will keep a weak reference to the given instance, as to prevent cyclic
     * references. If another {@link OutputStreamDelegate} was previously set,
     * calling this method will override it.
     *
     * @param outputStreamDelegate The instance to receive the callback events.
     */
    public final void setOutputStreamDelegate(OutputStreamDelegate outputStreamDelegate) {
        this.outputStreamDelegate = new WeakReference<>(outputStreamDelegate);
    }

    /**
     * Returns a strong reference to the {@link OutputStreamDelegate} that was
     * last set, and also the one that is, at the moment, receiving
     * {@link com.uplink.ulx.drivers.model.OutputStream} events. If none was
     * set, this method returns null.
     *
     * @return The current {@link OutputStreamDelegate}.
     */
    public final OutputStreamDelegate getOutputStreamDelegate() {
        return this.outputStreamDelegate != null ? this.outputStreamDelegate.get() : null;
    }

    /**
     * Returns the BluetoothDevice associated with this GattClient. This
     * corresponds to the device abstraction that is represented by this class
     * instance.
     *
     * @return The BluetoothDevice instance.
     */
    public final BluetoothDevice getBluetoothDevice() {
        return this.bluetoothDevice;
    }

    /**
     * Getter for the BluetoothManager instance, which is passed down from the
     * Driver. This class is used to obtain the BluetoothAdapter and conduct
     * overall management of Bluetooth.
     *
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
     *
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
     *
     * @return The BLE domestic service.
     */
    private BleDomesticService getDomesticService() {
        return this.domesticService;
    }

    /**
     * Returns the last known value for the MTU. If the MTU has already been
     * negotiated, whatever was the negotiated value will be returned. If not,
     * then this returns DEFAULT_MTU (which is 20).
     * @return The MTU.
     */
    public int getMtu() {
        return this.mtu;
    }

    /**
     * Returns the Android environment Context given at construction time.
     *
     * @return The Android environment Context.
     */
    private Context getContext() {
        return this.context;
    }

    /**
     * Connects to the GATT server on this device and returns the BluetoothGatt
     * handler for the Bluetooth GATT profile.
     *
     * @return The Bluetooth GATT profile abstraction.
     */
    @MainThread
    @Nullable
    private BluetoothGatt getBluetoothGatt() {

        // Make sure we're on the main thread
        assert Looper.myLooper() == Looper.getMainLooper();

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
     * Getter for the StateManager that is managing the class's state.
     * @return The GattClient's state manager instance.
     * @see StateManager
     */
    private StateManager getStateManager() {
        return stateManager.getRef();
    }

    /**
     * Requests the BluetoothGatt to connect, which corresponds to a connection
     * to the device that it represents.
     */
    public void connect() {
        getStateManager().start();
    }

    public void doConnect() {

        connectionOperation = operationsManager.enqueue(
                new SerialOperationsManager.Task() {
                    @Override
                    public void run(Completable completable) {
                        BluetoothGatt bluetoothGatt = getBluetoothGatt();

                        if (bluetoothGatt != null && bluetoothGatt.connect()) {
                            Timber.i(
                                    "ULX connection request for native device %s accepted",
                                    getBluetoothDevice().getAddress()
                            );
                        } else {
                            completable.markAsComplete();

                            Timber.e(
                                    "ULX connection request for native device %s rejected",
                                    getBluetoothDevice().getAddress()
                            );

                            // We should try to figure out a better error message
                            UlxError error = new UlxError(
                                    UlxErrorCode.UNKNOWN,
                                    "Could not connect to the device.",
                                    "The connection could not be initiated.",
                                    "Please try connecting again."
                            );

                            getStateManager().notifyFailedStart(error);
                        }
                    }

                    @Override
                    public void onComplete(SerialOperationsManager.Status status) {
                        if (status != SerialOperationsManager.Status.Timeout) {
                            // this sleep is here to avoid TONS of problems in BLE, that occur
                            // whenever we start service discovery immediately after the
                            // connection is established
                            // TODO remove the sleep after a delay is added to SerialOperationsManager
                            SystemClock.sleep(600);


                            getStateManager().notifyStart();
                        } else {
                            Timber.i(
                                    "Connection Operation timed out! Device %s",
                                    bluetoothGatt.getDevice().getAddress()
                            );
                            final UlxError error = new UlxError(
                                    UlxErrorCode.CONNECTION_TIMEOUT,
                                    "Could not connect to the remote device.",
                                    "The connection timed out.",
                                    "Please try reconnecting or restarting the Bluetooth adapter."
                            );
                            getStateManager().notifyFailedStart(error);
                        }
                    }
                },
                // Set a timeout for 30 s
                30_000
        );
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt bluetoothGatt, int status, int newState) {
        Timber.i(
                "ULX connection changed for native device %s with status %s. New state: %s",
                bluetoothGatt.getDevice().getAddress(),
                BluetoothConnectionState.fromValue(status).toString(),
                BluetoothConnectionState.fromValue(newState).toString()
        );
        ;

        //  hex	    Decimal	    reason
        //  0x08	8	        connection timeout
        //  0x13	19	        connection terminated by peer
        //  0x16	22	        connection terminated by local host
        //  0x22	34	        connection failed for LMP response tout
        //  0x85	133	        gatt_error

        ConnectorDelegate connectorDelegate = getConnectorDelegate();

        synchronized (this) {
            if (getStateManager().getState() == State.IDLE) {
                Timber.w("onConnectionStateChange() event received in IDLE state. Most likely the connection was timed out");
                return;
            }
        }

        // Don't proceed without a delegate; although this means that the
        // state change will simply be ignored.
        if (connectorDelegate == null) {
            Timber.i(
                    "ULX is ignoring a connection state change because the GATT client callback delegate was not set");
            return;
        }

        // Is the client connected?
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            connectionOperation.markAsComplete();
        } else { // As per documentation, the other possibility is only BluetoothProfile.STATE_DISCONNECTED
            if (disconnectOperation != null) {
                disconnectOperation.markAsComplete();
            }

            if (getStateManager().getState() == State.STOPPING) {
                assert false; // Graceful disconnections are not expected at this point
                // Graceful, expected stop
                getStateManager().notifyStop(null);
            } else {
                final UlxError error = new UlxError(
                        UlxErrorCode.UNKNOWN,
                        "Could not connect or hold the connection to the remote device.",
                        "The connection could not be established or was lost.",
                        "Please try reconnecting or restarting the Bluetooth adapter."
                );

                getStateManager().notifyStop(error);
            }
        }
    }

    /**
     * This method negotiates the Maximum Transmissible Unit (MTU) with the
     * remote peer by asking it for its maximum supported value.
     */
    private void negotiateMtu() {
        mtuOperation = operationsManager.enqueue(
                new SerialOperationsManager.Task() {
                    @Override
                    public void run(Completable completable) {
                        Timber.i(
                                "ULX is requesting the MTU from the remote native device %s",
                                getBluetoothDevice().getAddress()
                        );

                        // 512 is the maximum MTU possible, and its the one we're aiming for.
                        // In case of failure, we skip the MTU negotiation and go straight to
                        // discovering the services, since MTU failure is not blocking.
                        if (!getBluetoothGatt().requestMtu(MtuRegistry.MAXIMUM_MTU)) {
                            Timber.i(
                                    "ULX MTU request for remote native device %s failed, and the services will be discovered instead",
                                    getBluetoothDevice().getAddress()
                            );
                            completable.markAsComplete();
                        }
                    }

                    @Override
                    public void onComplete(SerialOperationsManager.Status status) {
                        onMtuNegotiationAttemptFinished();
                    }
                },
                MtuRegistry.MTU_REQUEST_TIMEOUT_MS
        );
    }

    /**
     * Called when MTU negotiation attempt finished - either successfully or not.
     * This will proceed with connection process, but only if an MTU request is currently pending
     */
    private void onMtuNegotiationAttemptFinished() {
        discoverServices();
    }

    @Override
    public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
        Timber.i(
                "ULX negotiated MTU value %d with remote device %s and status %d",
                mtu,
                gatt.getDevice().getAddress(),
                status
        );

        // Keep the negotiated MTU in case of success. Failure means that we
        // stick with the default.
        if (status == BluetoothGatt.GATT_SUCCESS) {
            this.mtu = mtu;

            // This will notify the stream if it is already set, although that
            // is unlikely.
            notifyOnMtuNegotiation();
        }

        if (mtuOperation != null) {
            mtuOperation.markAsComplete();
        }
    }

    /**
     * Propagates a notification of a change of MTU, if the {@link
     * OutputStreamDelegate} is set. Does nothing otherwise. The value to
     * propagate to the delegate is the last known MTU, as kept by the
     * {@link GattClient} upon negotiation.
     */
    private void notifyOnMtuNegotiation() {
        OutputStreamDelegate outputStreamDelegate = getOutputStreamDelegate();
        if (outputStreamDelegate != null) {
            outputStreamDelegate.onMtuNegotiated(this, getMtu());
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

        discoverServicesOperation = operationsManager.enqueue(
                new SerialOperationsManager.Task() {
                    @Override
                    public void run(Completable completable) {
                        Timber.i(
                                "ULX discovering services on remote native device %s",
                                getBluetoothDevice().getAddress()
                        );

                        BluetoothGatt bluetoothGatt = getBluetoothGatt();

                        if (!bluetoothGatt.discoverServices()) {
                            completable.markAsComplete();

                            // It's not that the service discovery failed, but rather that it
                            // couldn't be initiated
                            UlxError error = new UlxError(
                                    UlxErrorCode.UNKNOWN,
                                    "The connection to the remote device failed.",
                                    "Failed to discover the Bluetooth LE services offered by the remote device.",
                                    "Try connecting again, or restarting the Bluetooth adapter."
                            );

                            notifyOnConnectionFailure(error);
                        }
                    }

                    @Override
                    public void onComplete(SerialOperationsManager.Status status) {
                        // if timeout has occurred retry operation?
                        if (status == SerialOperationsManager.Status.Timeout) {
                            Timber.i(
                                    "Services Discovery Timed out! Device %s",
                                    bluetoothGatt.getDevice().getAddress()
                            );
                            handleServicesDiscoveryFail();
                        }
                    }
                },
        4_000);

    }

    @Override
    public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
        Timber.i(
                "ULX services discovered for remote native device %s with status %d",
                gatt.getDevice().getAddress(),
                status
        );

        if(discoverServicesOperation != null) {
            discoverServicesOperation.markAsComplete();
        }

        // Services discovered
        if (status == BluetoothGatt.GATT_SUCCESS) {
            handleServicesDiscovered(gatt.getServices());
        }

        // The service wasn't discovered, something went wrong
        else {
            handleServicesDiscoveryFail();
        }
    }

    /**
     * This method will generate and error and notify the delegate that the connection
     * procedure has failed. This can occur when the discoverServices operation has
     * failed or timed out. Without having the list of services, the connection cannot
     * proceed.
     */
    private void handleServicesDiscoveryFail() {
        // We should be checking the status for this
        UlxError error = new UlxError(
                UlxErrorCode.UNKNOWN,
                "Could not connect to the device using Bluetooth Low Energy.",
                "The service discovery process failed.",
                "Make sure that the other device is properly configured and still within range."
        );

        notifyOnConnectionFailure(error);
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
     *
     * @param services The list of services from which the implementation will
     *                 extract the matching service, if one exists.
     */
    private void handleServicesDiscovered(List<BluetoothGattService> services) {
        Timber.i(
                "ULX validating remote services for native device %s",
                getBluetoothDevice().getAddress()
        );

        BleForeignService foreignService = getForeignService(services);

        // Is the service valid or a protocol violation?
        if (foreignService == null) {

            UlxError error = new UlxError(
                    UlxErrorCode.PROTOCOL_VIOLATION,
                    "Could not connect to remote device.",
                    "The device failed to comply with the protocol by specifying the wrong type of service.",
                    "Please verify that the remote device is properly configured."
            );

            notifyOnConnectionFailure(error);

            return;
        }

        BluetoothGattCharacteristic domesticReliableControl = getDomesticService().getReliableControl();
        BluetoothGattCharacteristic foreignReliableControl = foreignService.getReliableControl();

        Objects.requireNonNull(domesticReliableControl);
        Objects.requireNonNull(foreignReliableControl);

        subscribeCharacteristic(foreignReliableControl);
    }

    /**
     * This method looks for a service matching the domestic service UUID on the
     * given list of services. This corresponds to looking up a list of
     * discovered services for one matching the host's description, which works
     * as a means of validation. If no service in the list matches the
     * description, this method will return null. Otherwise, a BleForeignService
     * is instantiated, which means that this method also works as a factory.
     *
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
     *
     * @param characteristic The characteristic to subscribe.
     */
    public void subscribeCharacteristic(BluetoothGattCharacteristic characteristic) {
        subscribeCharacteristicOperation = operationsManager.enqueue(
                new SerialOperationsManager.Task() {
                    @Override
                    public void run(Completable completable) {
                        Timber.i(
                                "ULX requested to subscribe remote characteristic on native device %s",
                                getBluetoothDevice().getAddress()
                        );

                        BluetoothGattDescriptor descriptor = getDescriptor(characteristic);

                        // The descriptor was not found
                        if (descriptor == null) {
                            UlxError error = new UlxError(
                                    UlxErrorCode.PROTOCOL_VIOLATION,
                                    "Could not connect to the remote device.",
                                    "The remote device does not conform with the expected protocol.",
                                    "Make sure that the remote device is running the correct software version."
                            );
                            notifyOnConnectionFailure(error);
                            completable.markAsComplete();
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
                            notifyOnConnectionFailure(error);
                            completable.markAsComplete();
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
                            notifyOnConnectionFailure(error);
                            completable.markAsComplete();
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
                            notifyOnConnectionFailure(error);
                            completable.markAsComplete();
                            //return;
                        }
                    }

                    @Override
                    public void onComplete(SerialOperationsManager.Status status) {
                        if (status == SerialOperationsManager.Status.Timeout) {
                            Timber.i("Characteristic Subscribe timed out! Device %s", bluetoothGatt.getDevice().getAddress());
                        }
                    }
                }, 20_000

        );
    }

    /**
     * Returns the BluetoothGattDescriptor that is expected for the given
     * characteristic, or returns null if none matches. The characteristic
     * can be either the Reliable Output or Reliable Control characteristic,
     * since the implementation will validate which of the two is being passed.
     * It will also assert that the proper properties were defined, so that
     * the protocol is asserted for compliance.
     *
     * @param characteristic The characteristic.^
     * @return The descriptor corresponding to the characteristic or null.
     */
    private BluetoothGattDescriptor getDescriptor(BluetoothGattCharacteristic characteristic) {

        assert Looper.myLooper() == Looper.getMainLooper();

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

        if (subscribeCharacteristicOperation != null) {
            subscribeCharacteristicOperation.markAsComplete();
        }

        if (status == BluetoothGatt.GATT_SUCCESS) {
            handleCharacteristicSubscribed(descriptor);
        } else {

            UlxError error = new UlxError(
                    UlxErrorCode.UNKNOWN,
                    "Could not connect to remote device.",
                    "Could not write to the remote descriptor.",
                    "Try connecting again or updating the software on the devices."
            );

            notifyOnConnectionFailure(error);
        }
    }

    /**
     * This method is called when a remote characteristic has been subscribed,
     * and the implementation is looking to proceed with connection lifecycle.
     * What that means depends on what characteristic is being subscribed,
     * control or output.
     *
     * @param descriptor The descriptor for the subscribed characteristic.
     */
    private void handleCharacteristicSubscribed(BluetoothGattDescriptor descriptor) {
        if (getDomesticService().isReliableOutput(descriptor)) {
            Timber.i(
                    "ULX reliable output characteristic subscribed on remote native device %s",
                    getBluetoothDevice().getAddress()
            );
            notifyOnOpen();
        } else {
            Timber.i(
                    "ULX control characteristic subscribed on remote native device %s",
                    getBluetoothDevice().getAddress()
            );
            notifyOnConnected();
        }
    }

    /**
     * Propagates a delegate notification indicating that the connection has
     * completed successfully, including the subscription of the control
     * characteristic.
     */
    private void notifyOnConnected() {
        ConnectorDelegate connectorDelegate = getConnectorDelegate();
        if (connectorDelegate != null) {
            connectorDelegate.onConnected(this);
        }
    }

    /**
     * Propagates a delegate notification indicating a successful subscription
     * of the remote output characteristic by the host's input stream. This
     * corresponds to the stream having been opened.
     */
    private void notifyOnOpen() {
        GattClient.InputStreamDelegate inputStreamDelegate = getInputStreamDelegate();
        if (inputStreamDelegate != null) {
            inputStreamDelegate.onOpen(this);
        }
    }

    /**
     * Handles the event of a failed connection attempt by notifying the
     * delegate of an onConnectionFailure() event. This method does not perform
     * clean up, and rather the callers should do that.
     *
     * @param error The error (UlxError) to propagate.
     */
    private void notifyOnConnectionFailure(UlxError error) {
        ConnectorDelegate connectorDelegate = getConnectorDelegate();
        if (connectorDelegate != null) {
            connectorDelegate.onConnectionFailure(this, error);
        }
    }

    private void notifyOnDisconnection(UlxError error) {
        ConnectorDelegate connectorDelegate = getConnectorDelegate();
        if (connectorDelegate != null) {
            connectorDelegate.onDisconnection(this, error);
        }
    }

    /**
     * This method returns a local GATT service matching the given GATT service
     * specification. The lookup will be done according to the given service's
     * UUID. This can be used to translate a remote service into a matching
     * local one.
     *
     * @param service The service to match.
     * @return A matching service or null, if one does not exist.
     */
    @MainThread
    public BluetoothGattService getServiceMatching(BluetoothGattService service) {
        return getServiceMatching(service.getUuid());
    }

    /**
     * This method looks for a local GATT service with the given UUID.
     *
     * @param uuid The service UUID to look for.
     * @return A matching service or null, if one does not exist.
     */
    @MainThread
    public BluetoothGattService getServiceMatching(UUID uuid) {
        return getBluetoothGatt().getService(uuid);
    }

    public boolean writeCharacteristic(BluetoothGattCharacteristic characteristic) {
        operationsManager.enqueue(
                new SerialOperationsManager.Task() {
                    @Override
                    public void run(Completable completable) {
                        writeOperation = completable;
                        boolean success = getBluetoothGatt().writeCharacteristic(characteristic);
                        if (!success) {
                            completable.markAsComplete(false);
                        }
                    }

                    @Override
                    public void onComplete(SerialOperationsManager.Status status) {
                        switch (status) {
                            case Success:
                                notifyOnCharacteristicWriteSuccess();
                                break;
                            case Timeout:
                                final UlxError errorT = new UlxError(
                                        UlxErrorCode.UNKNOWN,
                                        "Could not send data to the remote device.",
                                        "Operation timed out when writing to the stream.",
                                        "Try restarting the Bluetooth adapter."
                                );

                                notifyOnCharacteristicWriteFailure(errorT);
                                Timber.i(
                                        "Write Characteristic timed out! Device %s",
                                        bluetoothGatt.getDevice().getAddress()
                                );
                            case Failure:
                                final UlxError error = new UlxError(
                                        UlxErrorCode.UNKNOWN,
                                        "Could not send data to the remote device.",
                                        "Failed to produce output when writing to the stream.",
                                        "Try restarting the Bluetooth adapter."
                                );

                                notifyOnCharacteristicWriteFailure(error);
                                break;
                        }
                    }
                },
                5_000
        );

        // since the operation is being enqueued, this function just returns
        // whether the gatt object is valid
        return bluetoothGatt != null;
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        Timber.i(
                "ULX wrote to characteristic for device %s with status %d",
                gatt.getDevice().getAddress(),
                status
        );

        // Do not remove the Completable from the queue - it will be removed in its onComplete()
        if (writeOperation != null) {
            writeOperation.markAsComplete(status == BluetoothGatt.GATT_SUCCESS);
        } else {
            Timber.e("Unexpected onCharacteristicWrite() event");
        }
    }

    private void notifyOnCharacteristicWriteSuccess() {
        OutputStreamDelegate outputStreamDelegate = getOutputStreamDelegate();
        if (outputStreamDelegate != null) {
            outputStreamDelegate.onCharacteristicWritten(this);
        }
    }

    private void notifyOnCharacteristicWriteFailure(UlxError error) {
        OutputStreamDelegate outputStreamDelegate = getOutputStreamDelegate();
        if (outputStreamDelegate != null) {
            outputStreamDelegate.onCharacteristicWriteFailure(this, error);
        }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        Timber.i(
                "ULX remote characteristic changed on device %s",
                gatt.getDevice().getAddress()
        );
        notifyOnCharacteristicChanged(characteristic);
    }

    /**
     * Propagates a notification for characteristic update that was received,
     * by calling the {@link InputStreamDelegate#onCharacteristicChanged(BluetoothGatt, BluetoothGattCharacteristic)}
     * delegate method.
     * @param characteristic The characteristic that was updated.
     */
    private void notifyOnCharacteristicChanged(BluetoothGattCharacteristic characteristic) {
        InputStreamDelegate inputStreamDelegate = getInputStreamDelegate();
        if (inputStreamDelegate != null) {
            inputStreamDelegate.onCharacteristicChanged(this, characteristic);
        }
    }

    public void stop(UlxError error) {
        getStateManager().notifyStop(error);
    }
}
