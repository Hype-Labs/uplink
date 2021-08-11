package com.uplink.ulx.drivers.bluetooth.ble.gattClient;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattService;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.uplink.ulx.TransportType;
import com.uplink.ulx.threading.ExecutorPool;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * This class extends BluetoothGattCallback in order to listen to BluetoothGatt
 * events, such as the service discovery and connection lifecycle, I/O, and
 * changes to MTU. This class implements a delegate paradigm in order to
 * communicate as these events occur.
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class GattClientCallback extends BluetoothGattCallback {

    /**
     * The GattClientCallback Delegate gets notifications for GATT client
     * events, such as the service discovery and connection lifecycle, I/O,
     * and other attributes.
     */
    public interface Delegate {

        /**
         * This delegate notification is called by the GattClientCallback when
         * some services are successfully discovered. This means that the
         * services were discovered and that their characteristics can be
         * listed, but it doesn't mean that they correspond to the required
         * service specification. That is, these services must still be
         * validated by the implementation, and ignored if they do not
         * correspond.
         * @param gattClientCallback The instance issuing the notification.
         * @param gatt The Bluetooth GATT profile.
         * @param services A List of services (BluetoothGattService) that have
         *                 been discovered.
         */
        void onServicesDiscovered(
                GattClientCallback gattClientCallback,
                BluetoothGatt gatt,
                List<BluetoothGattService> services
        );

        /**
         * This delegate method is called when the service discovery process
         * fails. The status argument should provide further clarification as
         * to why the process failed.
         * @param gattClientCallback The instance issuing the notification.
         * @param gatt The Bluetooth GATT profile.
         * @param status An indicator of the cause for failure, as documented
         *               by BluetoothGatt.
         */
        void onFailedServiceDiscovery(
                GattClientCallback gattClientCallback,
                BluetoothGatt gatt,
                int status
        );
    }

    private final WeakReference<GattClientCallback.Delegate> delegate;

    /**
     * Constructor. Initializes with the given delegate.
     * @param delegate The instance delegate.
     */
    public GattClientCallback(GattClientCallback.Delegate delegate) {
        this.delegate = new WeakReference<>(delegate);
    }

    /**
     * Returns the delegate currently receiving notifications for events related
     * with the GattClientCallback.
     * @return The delegate.
     */
    private GattClientCallback.Delegate getDelegate() {
        return this.delegate.get();
    }

    @Override
    public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
        ExecutorPool.getExecutor(TransportType.BLUETOOTH_LOW_ENERGY).execute(() -> {

            // Services discovered
            if (status == BluetoothGatt.GATT_SUCCESS) {
                getDelegate().onServicesDiscovered(this, gatt, gatt.getServices());
            }

            // The service wasn't discovered, something went wrong
            else getDelegate().onFailedServiceDiscovery(this, gatt, status);
        });
    }
}
