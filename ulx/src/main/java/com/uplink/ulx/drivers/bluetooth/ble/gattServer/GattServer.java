package com.uplink.ulx.drivers.bluetooth.ble.gattServer;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Build;
import android.os.Looper;
import android.util.Log;

import com.uplink.ulx.UlxError;
import com.uplink.ulx.UlxErrorCode;
import com.uplink.ulx.drivers.bluetooth.ble.model.domestic.BleDomesticService;
import com.uplink.ulx.drivers.model.Device;
import com.uplink.ulx.threading.Dispatch;

import java.lang.ref.WeakReference;
import java.util.HashMap;
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

        /**
         * This method is used as an indication that a notification was sent
         * to the given {@link BluetoothDevice}. This should correspond to a
         * domestic reliable stream producing output, having gotten confirmation
         * from the remote peer that the content was received.
         * @param gattServer The instance triggering the notification.
         * @param bluetoothDevice The BluetoothDevice that was notified.
         */
        void onNotificationSent(GattServer gattServer, BluetoothDevice bluetoothDevice);

        /**
         * This method is used as an indication that the given BluetoothDevice
         * did not receive an indication as it was supposed. This could mean
         * that the device did not respond or that something else went wrong,
         * but the data should be assumed to have not have been delivered.
         * @param gattServer The instance triggering the notification.
         * @param bluetoothDevice The BluetoothDevice that was not notified.
         * @param error An error, indicating a probable cause for the failure.
         */
        void onNotificationNotSent(
                GattServer gattServer,
                BluetoothDevice bluetoothDevice,
                UlxError error
        );

        /**
         * This notification is triggered by the GATT server to the delegate
         * when a write request occurs. This will correspond to input being
         * received. If the event needs a response or needs to be queued by the
         * GATT server, the {@link GattServer} implementation will handle those
         * details. Instead, the delegate should focus on processing the
         * incoming data.
         * @param gattServer The {@link GattServer} issuing the notification.
         * @param bluetoothDevice The {@link BluetoothDevice} requesting the
         *                        write operation.
         * @param data The value that was written.
         */
        void onCharacteristicWriteRequest(
                GattServer gattServer,
                BluetoothDevice bluetoothDevice,
                byte[] data
        );
    }

    private WeakReference<Delegate> delegate;

    private final BleDomesticService domesticService;
    private final BluetoothManager bluetoothManager;
    private final WeakReference<Context> context;

    private MtuRegistry mtuRegistry;
    private HashMap<String, Device> deviceRegistry;

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

        this.mtuRegistry = null;
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
     * Returns the MTU registry that holds the MTU values negotiated with the
     * other peer devices.
     * @return The MTU registry.
     */
    private MtuRegistry getMtuRegistry() {
        if (this.mtuRegistry == null) {
            this.mtuRegistry = new MtuRegistry();
        }
        return this.mtuRegistry;
    }

    /**
     * Returns the registry that is used to keep track of devices on the
     * network. This will map native device addresses to abstract {@link Device}
     * instances, effectively bridging the native and framework device
     * abstractions.
     * @return The registry used to bridge native and abstract devices.
     */
    private HashMap<String, Device> getDeviceRegistry() {
        if (this.deviceRegistry == null) {
            this.deviceRegistry = new HashMap<>();
        }
        return this.deviceRegistry;
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
     * Request the {@link GattServer} to register the BLE service within the
     * adapter. This will make the service available for operations, such as
     * advertising. As soon as this process completes, the delegate will get a
     * notification for either {@link Delegate#onServiceAdded(int,
     * BluetoothGattService)} or {@link Delegate#onServiceAdditionFailed(
     * GattServer, UlxError)}, yielding the result of the operation.
     */
    public void addService() {

        assert Looper.myLooper() == Looper.getMainLooper();

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

            notifyOnServiceAdditionFailed(error);
        }
    }

    @Override
    public void onServiceAdded(final int status, final BluetoothGattService service) {
        super.onServiceAdded(status, service);

        if (status == BluetoothGatt.GATT_SUCCESS) {
            notifyOnServiceAdded(service);
        } else {

            UlxError error = new UlxError(
                    UlxErrorCode.UNKNOWN,
                    "Could not advertise using Bluetooth Low Energy.",
                    "The service could not be properly registered to initiate the activity.",
                    "Try restarting the Bluetooth adapter."
            );

            notifyOnServiceAdditionFailed(error);
        }
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
    private void notifyOnServiceAdditionFailed(UlxError error) {
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
        Log.i(getClass().getCanonicalName(), String.format("ULX descriptor got a write request with %d bytes from %s", value.length, device.getAddress()));

        Dispatch.post(() -> {

            // Respond to the requester
            if (responseNeeded) {
                Log.i(getClass().getCanonicalName(), String.format("ULX responding to a descriptor write request for device %s", device.getAddress()));

                if (!getBluetoothGattServer().sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        offset,
                        value
                )) {
                    Log.e(getClass().getCanonicalName(), "ULX failed to respond to a descriptor write request");
                    return;
                }
            }

            // The connection process is completed when the reliable control
            // characteristic is subscribed
            if (getDomesticService().isReliableControl(descriptor)) {
                notifyOnDeviceConnected(device);
            }

            // When subscribing the reliable output characteristic, the devices
            // are already connected and preparing the streams for I/O
            else if (getDomesticService().isReliableOutput(descriptor)) {
                notifyOnOutputStreamSubscribed(device);
            }
        });
    }

    /**
     * Propagates a notification to the delegate giving indication that the
     * reliable output stream was subscribed.
     * @param device The {@link BluetoothDevice} that subscribed the
     *               characteristic.
     */
    private void notifyOnOutputStreamSubscribed(BluetoothDevice device) {
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
    private void notifyOnDeviceConnected(BluetoothDevice device) {
        Delegate delegate = getDelegate();
        if (delegate != null) {
            delegate.onDeviceConnected(this, device);
        }
    }

    @Override
    public void onMtuChanged(BluetoothDevice bluetoothDevice, int mtu) {
        super.onMtuChanged(bluetoothDevice, mtu);

        Log.i(getClass().getCanonicalName(), String.format("ULX MTU has changed to %d for device %s", mtu, bluetoothDevice.getAddress()));

        // Keep it
        getMtuRegistry().set(bluetoothDevice, mtu);
    }

    @Override
    public void onCharacteristicWriteRequest(
            BluetoothDevice device,
            int requestId,
            BluetoothGattCharacteristic characteristic,
            boolean preparedWrite,
            boolean responseNeeded,
            int offset,
            byte[] value) {

        Dispatch.post(() -> {

            Log.i(getClass().getCanonicalName(), String.format("ULX characteristic changed with %d bytes of data from device %s", value.length, device.getAddress()));

            if (responseNeeded) {
                Log.i(getClass().getCanonicalName(), String.format("ULX sending characteristic write request response to device %s", device.getAddress()));

                if (!getBluetoothGattServer().sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        offset,
                        value
                )) {
                    Log.e(getClass().getCanonicalName(), "ULX failed to send characteristic write request response");
                    return;
                }
            }

            // Propagate the event
            notifyOnCharacteristicWriteRequest(device, value);
        });
    }

    private void notifyOnCharacteristicWriteRequest(
            BluetoothDevice bluetoothDevice,
            byte[] data) {
        Delegate delegate = getDelegate();
        if (delegate != null) {
            delegate.onCharacteristicWriteRequest(this, bluetoothDevice, data);
        }
    }

    private int getMtu(BluetoothDevice bluetoothDevice) {
        return Math.max(MtuRegistry.DEFAULT_MTU, (int)(getMtuRegistry().get(bluetoothDevice) * .99));
    }

    /**
     * Updates the value of the characteristic according to the data passed in
     * the {@code data} argument. The implementation will consume the least
     * amount of bytes between the given buffer and the available MTU. After
     * the characteristic is updated, the remote {@code bluetoothDevice} will
     * be notified of the change. If the implementation cannot perform any of
     * this operations, it will return zero, as an indication that it could not
     * write any bytes. In case of success it will return the number of bytes
     * written instead.
     * @param bluetoothDevice The remote {@link BluetoothDevice}.
     * @param characteristic The {@link BluetoothGattCharacteristic} to write.
     * @param confirm Whether to ask for a confirmation from the client.
     * @param data The data to send.
     * @return The amount of bytes consumed from the input data.
     */
    public int updateCharacteristic(BluetoothDevice bluetoothDevice, BluetoothGattCharacteristic characteristic, boolean confirm, byte[] data) {

        assert Looper.myLooper() == Looper.getMainLooper();

        byte[] dataToSend = new byte[Math.min(data.length, getMtu(bluetoothDevice))];

        // Copy the data
        System.arraycopy(data, 0, dataToSend, 0, dataToSend.length);

        // Set the value locally
        if (!characteristic.setValue(dataToSend)) {
            return 0;
        }

        try {

            // Notify the characteristic as changed
            if (!getBluetoothGattServer().notifyCharacteristicChanged(bluetoothDevice, characteristic, confirm)) {
                return 0;
            }
        } catch (Exception ex) {

            // java.lang.NullPointerException: Attempt to invoke virtual method 'int java.lang.Integer.intValue()' on a null object reference
            //        at android.os.Parcel.readException(Parcel.java:1698)
            //        at android.os.Parcel.readException(Parcel.java:1645)
            //        at android.bluetooth.IBluetoothGatt$Stub$Proxy.sendNotification(IBluetoothGatt.java:1318)
            //        at android.bluetooth.BluetoothGattServer.notifyCharacteristicChanged(BluetoothGattServer.java:590)
            //        at com.uplink.ulx.drivers.bluetooth.ble.gattServer.GattServer.updateCharacteristic(GattServer.java:465)
            //
            // This exception occurs sometimes, but it appears to be one that
            // we can't handle. This will result in the buffer not being trimmed,
            // meaning that the no data is removed from it, and the writer will
            // simply attempt to send the same data again.
            //
            // android.os.DeadObjectException
            //        at android.os.BinderProxy.transactNative(Native Method)
            //        at android.os.BinderProxy.transact(Binder.java:622)
            //        at android.bluetooth.IBluetoothGatt$Stub$Proxy.sendNotification(IBluetoothGatt.java:1317)
            //        at android.bluetooth.BluetoothGattServer.notifyCharacteristicChanged(BluetoothGattServer.java:590)
            //        at com.uplink.ulx.drivers.bluetooth.ble.gattServer.GattServer.updateCharacteristic(GattServer.java:467)
            //
            ex.printStackTrace();
            Log.e(getClass().getCanonicalName(), "ULX handled an exception by " +
                    "printing the stack trace, but operations will resume");

            return 0;
        }

        return dataToSend.length;
    }

    @Override
    public void onNotificationSent(BluetoothDevice bluetoothDevice, int status) {
        Log.i(getClass().getCanonicalName(), String.format("ULX sent a notification to %s", bluetoothDevice.getAddress()));

        Dispatch.post(() -> {

            if (status == BluetoothGatt.GATT_SUCCESS) {
                notifyOnNotificationSent(bluetoothDevice);
            } else {

                UlxError error = new UlxError(
                        UlxErrorCode.UNKNOWN,
                        "Could not deliver the message to the remote device.",
                        "The device failed to acknowledge reception of the data was not delivered.",
                        "Try sending the message again."
                );

                notifyOnNotificationSentFailure(bluetoothDevice, error);
            }
        });
    }

    /**
     * Propagates a notification for a notification that was successfully
     * acknowledge by the remote device.
     * @param bluetoothDevice The device that acknowledged reception.
     */
    private void notifyOnNotificationSent(BluetoothDevice bluetoothDevice) {
        Delegate delegate = getDelegate();
        if (delegate != null) {
            delegate.onNotificationSent(this, bluetoothDevice);
        }
    }

    /**
     * Propagates a notification for a {@link
     * Delegate#onNotificationSent(BluetoothDevice, int)} event to the delegate.
     * @param bluetoothDevice The {@link BluetoothDevice} that received the
     *                        notification/indication.
     */
    private void notifyOnNotificationSentFailure(BluetoothDevice bluetoothDevice, UlxError error) {
        Delegate delegate = getDelegate();
        if (delegate != null) {
            delegate.onNotificationNotSent(this, bluetoothDevice, error);
        }
    }
}
