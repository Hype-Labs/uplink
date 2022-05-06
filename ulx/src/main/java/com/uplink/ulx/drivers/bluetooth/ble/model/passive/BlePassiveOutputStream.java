package com.uplink.ulx.drivers.bluetooth.ble.model.passive;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Looper;

import com.uplink.ulx.TransportType;
import com.uplink.ulx.UlxError;
import com.uplink.ulx.UlxErrorCode;
import com.uplink.ulx.drivers.bluetooth.ble.gattServer.GattServer;
import com.uplink.ulx.drivers.commons.model.OutputStreamCommons;
import com.uplink.ulx.drivers.model.IoResult;
import com.uplink.ulx.utils.SerialOperationsManager;

import timber.log.Timber;

public class BlePassiveOutputStream extends OutputStreamCommons {

    private final GattServer gattServer;
    private final BluetoothDevice bluetoothDevice;
    private final BluetoothGattCharacteristic characteristic;

    /**
     * Factory method. Initializes with given arguments.
     *
     * @param identifier        An identifier used for JNI bridging and debugging.
     * @param gattServer        The GATT server that is managing this stream.
     * @param bluetoothDevice   The corresponding {@link BluetoothDevice}.
     * @param characteristic    The characteristic used by the stream for output.
     * @param operationsManager operations manager to serialize BLE operations
     */
    public static BlePassiveOutputStream newInstance(
            String identifier,
            GattServer gattServer,
            BluetoothDevice bluetoothDevice,
            BluetoothGattCharacteristic characteristic,
            SerialOperationsManager operationsManager
    ) {
        final BlePassiveOutputStream instance = new BlePassiveOutputStream(
                identifier,
                gattServer,
                bluetoothDevice,
                characteristic,
                operationsManager
        );
        instance.initialize();
        return instance;
    }

    private BlePassiveOutputStream(
            String identifier,
            GattServer gattServer,
            BluetoothDevice bluetoothDevice,
            BluetoothGattCharacteristic characteristic,
            SerialOperationsManager operationsManager
    )
    {
        super(identifier, TransportType.BLUETOOTH_LOW_ENERGY, true, operationsManager);

        // This will be used to interact with the adapter
        this.gattServer = gattServer;

        this.bluetoothDevice = bluetoothDevice;
        this.characteristic = characteristic;
    }

    /**
     * Returns the GATT server abstraction that was set at construction time
     * and that is going to be used by the stream to interact with the adapter.
     * @return The GATT server.
     */
    private GattServer getGattServer() {
        return this.gattServer;
    }

    /**
     * Returns the {@link BluetoothDevice} that was associated with this stream
     * at construction time.
     * @return The {@link BluetoothDevice}.
     */
    private BluetoothDevice getBluetoothDevice() {
        return this.bluetoothDevice;
    }

    /**
     * Returns the Bluetooth GATT characteristic that the stream implementation
     * uses to produce output.
     * @return The output characteristic.
     */
    private BluetoothGattCharacteristic getCharacteristic() {
        return this.characteristic;
    }

    @Override
    public void requestAdapterToOpen() {

        // Perhaps it will be enough to wait for the peripheral to connect and
        // manage the streams; we'll see how that plays out.
        Timber.e("ULX passive output stream is " +
                         "being requested to open, but that is not supported yet");
    }

    @Override
    public void requestAdapterToClose() {
        Timber.e("ULX passive output stream is " +
                         "being requested to close, but that is not supported yet");
    }

    /**
     * Calling this method gives the stream an indication that its
     * characteristic was subscribed by the remote peer, and thus it's now
     * able of performing I/O. Calling this method is necessary in order to
     * complete the stream's lifecycle events. This method should not be public,
     * but the BleAdvertiser currently lives in a different package; this is
     * expected to change in the future, so this method not be called.
     */
    public void notifyAsOpen() {
        super.onOpen();
    }

    @Override
    public IoResult flush(byte[] data) {

        assert Looper.myLooper() == Looper.getMainLooper();

        // Write to the characteristic and update the remote
        int written = getGattServer().updateCharacteristic(
                getBluetoothDevice(),
                getCharacteristic(),
                true,
                data
        );

        final UlxError error;
        if (written == 0) {
            error = new UlxError(
                    UlxErrorCode.UNKNOWN,
                    "Could not write any data",
                    "Failed to update characteristic",
                    "Reconnect to the device"
            );
        } else {
            error = null;
        }

        return new IoResult(written, error);
    }

    /**
     * This method should be called by the {@link GattServer} to give indication
     * to the stream that an indication was successfully given to the remote
     * peer of a characteristic update.
     */
    public void notifySuccessfulIndication() {
        onSpaceAvailable();
    }

    /**
     * This method should be called by the {@link GattServer} to give indication
     * to the stream that an indication was not received by the remote peer,
     * meaning that there's no known acknowledgement for a previous update of a
     * characteristic.
     * @param error An error, describing a probable cause for the failure.
     */
    public void notifyFailedIndication(UlxError error) {
        Timber.e(
                "ULX failed to receive indication for a characteristic update [%s]",
                error.toString()
        );
        close(error);
    }
}
