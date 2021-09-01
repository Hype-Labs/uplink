package com.uplink.ulx.drivers.bluetooth.ble.gattServer;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.uplink.ulx.TransportType;
import com.uplink.ulx.UlxError;
import com.uplink.ulx.UlxErrorCode;
import com.uplink.ulx.drivers.bluetooth.ble.model.domestic.BleDomesticService;
import com.uplink.ulx.threading.ExecutorPool;

import java.lang.ref.WeakReference;
import java.util.Objects;

/**
 * This class is an implementation of a GATT server mediator, which manages the
 * lifecycle for the native system GATT server. This includes configuring and
 * asserting that all necessary pre-requisites necessary to start the server
 * are met, as well as listening to feedback from the server, such as delegate
 * events.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class GattServer extends BluetoothGattServerCallback {

    /**
     * The GattServer.Delegate interface is used to keep track of weak reference
     * to a delegate instance that will be listening to GattServer lifecycle
     * events. These events include the service being added and foreign devices
     * connecting to the server.
     */
    public interface Delegate {

        /**
         * This delegate call is triggered when the GattServer has finished
         * registering the BLE service within the adapter. This means that the
         * device is capable of performing operations such as advertising,
         * since registering the service is a pre-condition for activities
         * such as that one.
         * @param gattServer The instance triggering the notification.
         * @param service The BluetoothGattService that was added.
         */
        void onServiceAdded(GattServer gattServer, BluetoothGattService service);

        /**
         * This delegate call is triggered when the GattServer tries to register
         * a service within the adapter but fails to do so. The error parameter
         * will indicate a possible or probable explanation for the failure.
         * @param gattServer The instance triggering the notification.
         * @param error An error, indicating a probable cause for the failure.
         */
        void onServiceAdditionFailed(GattServer gattServer, UlxError error);

        /**
         * This delegate notification is triggered by the GattServer instance
         * receives a subscription to the control characteristic of the service,
         * indicating that the devices have connected (even though the streams
         * are yet to be opened). This means that, according to the service
         * specification, the devices are considered connected.
         * @param gattServer The instance triggering the notification.
         * @param device The BluetoothDevice that was connected.
         */
        void onDeviceConnected(GattServer gattServer, BluetoothDevice device);

        /**
         * This method is used as an indication that the output stream has been
         * subscribed by the remote device. This is the equivalent of that
         * stream being open.
         * @param gattServer The instance triggering the notification.
         * @param bluetoothDevice The BluetoothDevice that has subscribed.
         */
        void onOutputStreamSubscribed(GattServer gattServer, BluetoothDevice bluetoothDevice);
    }

    private WeakReference<Delegate> delegate;

    private final BleDomesticService domesticService;
    private final BluetoothManager bluetoothManager;
    private final WeakReference<Context> context;

    private BluetoothGattServer bluetoothGattServer;

    /**
     * Constructor. Initializes with the given parameters.
     * @param domesticService The service configuration specification.
     * @param bluetoothManager The BluetoothManager instance.
     * @param context The Android environment Context.
     */
    public GattServer(
            BleDomesticService domesticService,
            BluetoothManager bluetoothManager,
            Context context)
    {
        Objects.requireNonNull(domesticService);
        Objects.requireNonNull(bluetoothManager);
        Objects.requireNonNull(context);

        this.domesticService = domesticService;
        this.bluetoothManager = bluetoothManager;
        this.context = new WeakReference<>(context);
    }

    /**
     * Returns the BleDomesticService used to configure the GattServer instance
     * when registering the BLE service within the adapter.
     * @return The BleDomesticService.
     */
    private BleDomesticService getDomesticService() {
        return this.domesticService;
    }

    /**
     * Returns the BluetoothManager instance that the GattServer uses to
     * interact with the adapter.
     * @return The BluetoothManager.
     */
    private BluetoothManager getBluetoothManager() {
        return this.bluetoothManager;
    }

    /**
     * Returns the Android environment Context given at construction time.
     * @return The Android environment Context.
     */
    private Context getContext() {
        return this.context.get();
    }

    /**
     * This method instantiates and opens a connection with the GATT server,
     * returning its handle as a result, in case the connection has not been
     * established before. It also passes the GattServerCallback as an argument
     * to listen to GATT server events, as return by getGattServerCallback().
     * @return The BluetoothGatServer GATT server handler instance.
     */
    private BluetoothGattServer getBluetoothGattServer() {
        if (this.bluetoothGattServer == null) {
            this.bluetoothGattServer = getBluetoothManager().openGattServer(
                    getContext(),
                    this
            );
        }
        return this.bluetoothGattServer;
    }

    /**
     * Sets the delegate for the GattServer. This delegate will get all
     * notifications after the call completes, replacing any previous delegate
     * that might have existed.
     * @param delegate The delegate to set.
     * @see Delegate
     */
    public final void setDelegate(Delegate delegate) {
        this.delegate = new WeakReference<>(delegate);
    }

    /**
     * Returns a strong reference to the delegate if one has previously been
     * set. If not, this method returns null instead.
     * @return The Delegate currently subscribed to GattServer notifications.
     */
    private Delegate getDelegate() {
        return this.delegate != null ? this.delegate.get() : null;
    }

    /**
     * Requested the GattServer to register the BLE service within the adapter.
     * This will make the service available for operations, such as advertising.
     * As soon as this process completes, the delegate will get a notification
     * for either onServiceAdded() or onServiceFailedToAdd(), yielding the
     * result of the operation.
     */
    public void addService() {

        Delegate delegate = getDelegate();

        // Don't proceed without the delegate; is there any clean up to do?
        if (delegate == null) {
            return;
        }

        BluetoothGattService coreService = getDomesticService().getCoreService();

        // Try adding the service, or fail. This will result in either
        // serviceAdded() or serviceFailedToAdd() to be called, in both of
        // which cases the lock must be released.
        if (!getBluetoothGattServer().addService(coreService)) {

            // We should check the service status for better error info
            UlxError error = new UlxError(
                    UlxErrorCode.UNKNOWN,
                    "Could not advertise using Bluetooth Low Energy.",
                    "The service could not be properly registered to initiate the activity.",
                    "Try restarting the Bluetooth adapter."
            );

            notifyFailedServiceAddition(error);
        }
    }

    @Override
    public void onServiceAdded(final int status, final BluetoothGattService service) {
        super.onServiceAdded(status, service);

        ExecutorPool.getExecutor(TransportType.BLUETOOTH_LOW_ENERGY).execute(() -> {

            if (status == BluetoothGatt.GATT_SUCCESS) {
                notifyOnServiceAdded(service);
            } else {

                UlxError error = new UlxError(
                        UlxErrorCode.UNKNOWN,
                        "Could not advertise using Bluetooth Low Energy.",
                        "The service could not be properly registered to initiate the activity.",
                        "Try restarting the Bluetooth adapter."
                );

                notifyFailedServiceAddition(error);
            }
        });
    }

    /**
     * This method will notify the delegate of a successful service registration
     * within the GATT server.
     * @param service The service that was registered.
     */
    private void notifyOnServiceAdded(BluetoothGattService service) {
        Delegate delegate = getDelegate();
        if (delegate != null) {
            delegate.onServiceAdded(this, service);
        }
    }

    /**
     * This method will notify the delegate of a failed process when attempting
     * to add the service to the GATT server.
     * @param error An error, describing the cause for the failure.
     */
    private void notifyFailedServiceAddition(UlxError error) {
        Delegate delegate = getDelegate();
        if (delegate != null) {
            delegate.onServiceAdditionFailed(this, error);
        }
    }

    @Override
    public void onDescriptorWriteRequest(
            BluetoothDevice device,
            int requestId,
            BluetoothGattDescriptor descriptor,
            boolean preparedWrite,
            boolean responseNeeded,
            int offset,
            byte[] value
    ) {
        Log.i(getClass().getCanonicalName(), "ULX descriptor got a write request");

        // Respond to the requester
        if (responseNeeded) {

            // TODO this method returns a boolean value that should be checked
            getBluetoothGattServer().sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    offset,
                    value
            );
        }

        // The connection process is completed when the reliable control
        // characteristic is subscribed
        if (getDomesticService().isReliableControl(descriptor)) {
            handleDeviceConnected(device);
        }

        // When subscribing the reliable output characteristic, the devices
        // are already connected and preparing the streams for I/O
        else if (getDomesticService().isReliableOutput(descriptor)) {
            handleReliableOutputStreamOpen(device);
        }
    }

    private void handleReliableOutputStreamOpen(BluetoothDevice device) {
        Delegate delegate = getDelegate();
        if (delegate != null) {
            delegate.onOutputStreamSubscribed(this, device);
        }
    }

    /**
     * Propagates a delegate notification for onDeviceConnected(), indicating
     * that the given BluetoothDevice has subscribed the control characteristic.
     * @param device The device that connected.
     */
    private void handleDeviceConnected(BluetoothDevice device) {
        Delegate delegate = getDelegate();
        if (delegate != null) {
            delegate.onDeviceConnected(this, device);
        }
    }

    @Override
    public void onMtuChanged(BluetoothDevice device, int mtu) {
        super.onMtuChanged(device, mtu);

        Log.i(getClass().getCanonicalName(), String.format("ULX MTU has changed to %d for device %s", mtu, device.getAddress()));
    }
}
