package com.uplink.ulx.drivers.bluetooth.ble.gattServer;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.os.Build;

import com.uplink.ulx.TransportType;
import com.uplink.ulx.UlxError;
import com.uplink.ulx.UlxErrorCode;
import com.uplink.ulx.threading.ExecutorPool;

import java.lang.ref.WeakReference;
import java.util.Objects;

/**
 * This class is an extension of the BluetoothGattServerCallback that overrides
 * all methods that are necessary to listen to GATT server events, such as the
 * service registration lifecycle, devices connecting, and so on. This class
 * interfaces all activity with the GATT server, and handles and propagates
 * notifications to the delegate.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class GattServerCallback extends BluetoothGattServerCallback {

    /**
     * The GattServerCallback Delegate receives notifications for GATT server
     * events, such as the service being registered, devices being found and
     * connected, and so on.
     */
    interface Delegate {

        /**
         * This delegate callback is called after the given service is known to
         * have successfully registered with the GATT server. This means that
         * the service can start performing some actions, such as advertising
         * the device on the network, or accepting connections.
         * @param service The service that was added.
         */
        void onServiceAdded(BluetoothGattService service);

        /**
         * This delegate callback method is called when the service is known to
         * have failed to register with the GATT server. This means that the
         * GATT server is not ready to perform its duties, and therefore the
         * implementation may not begin some actions, such as advertising the
         * device on the network or accepting connections.
         * @param service The service that failed to add.
         * @param error An error, describing a probable cause for the failure.
         */
        void onServiceAdditionFailed(BluetoothGattService service, UlxError error);
    }

    private final WeakReference<Delegate> delegate;

    /**
     * Constructor. Initializes with the given delegate.
     * @param delegate The delegate to receive notifications from the instance.
     */
    public GattServerCallback(Delegate delegate) {

        Objects.requireNonNull(delegate);

        this.delegate = new WeakReference<>(delegate);
    }

    /**
     * Returns a strong reference to the delegate.
     * @return The GattServerCallback delegate.
     */
    private Delegate getDelegate() {
        return this.delegate.get();
    }

    @Override
    public void onServiceAdded(final int status, final BluetoothGattService service) {
        super.onServiceAdded(status, service);

        ExecutorPool.getExecutor(TransportType.BLUETOOTH_LOW_ENERGY).execute(() -> {

            // Success
            if (status == BluetoothGatt.GATT_SUCCESS) {
                notifyOnServiceAdded(service);
            }

            // Failure
            else notifyOnServiceFailedToAdd(service);
        });
    }

    /**
     * This method will notify the delegate of a successful service registration
     * within the GATT server.
     * @param service The service that was registered.
     */
    private void notifyOnServiceAdded(BluetoothGattService service) {
        getDelegate().onServiceAdded(service);
    }

    /**
     * This method will notify the delegate of a failed process when attempting
     * to add the service to the GATT server. This method is currently
     * propagating a generic error, although future revisions may try to figure
     * out a proper cause for the failure and provide better error information.
     * @param service The service that failed to add.
     */
    private void notifyOnServiceFailedToAdd(BluetoothGattService service) {

        // We should check the status for better error information
        UlxError error = new UlxError(
                UlxErrorCode.UNKNOWN,
                "Could not advertise using Bluetooth Low Energy.",
                "The service could not be properly registered to initiate the activity.",
                "Try restarting the Bluetooth adapter."
        );

        getDelegate().onServiceAdditionFailed(service, error);
    }
}
