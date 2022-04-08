package com.uplink.ulx.drivers.bluetooth.ble.model.foreign;

import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Looper;

import com.uplink.ulx.TransportType;
import com.uplink.ulx.UlxError;
import com.uplink.ulx.UlxErrorCode;
import com.uplink.ulx.drivers.bluetooth.ble.gattClient.GattClient;
import com.uplink.ulx.drivers.bluetooth.ble.gattServer.MtuRegistry;
import com.uplink.ulx.drivers.commons.model.OutputStreamCommons;
import com.uplink.ulx.drivers.model.IoResult;
import com.uplink.ulx.utils.ByteUtils;

import java.util.Objects;

import androidx.annotation.MainThread;
import timber.log.Timber;

/**
 * A {@link BleForeignOutputStream} implements the output logic for streams on
 * the client side of the connection. This implementation interacts with the
 * {@link GattClient} by setting the characteristic's value on the remote
 * central, through calls to {@link BluetoothGattCharacteristic#setValue(byte[])}
 * and {@link GattClient#writeCharacteristic(BluetoothGattCharacteristic)}, and
 * {@link #onCharacteristicWritten(GattClient)} and {@link
 * #onCharacteristicWriteFailure(GattClient, UlxError)} to receive the results
 * for a write operation. The actual logic is implemented by the {@link
 * GattClient}, although the {@link BleForeignOutputStream} implements the
 * abstraction for the {@link com.uplink.ulx.drivers.model.OutputStream} (by
 * extending {@link OutputStreamCommons}).
 */
public class BleForeignOutputStream extends OutputStreamCommons implements GattClient.OutputStreamDelegate {

    private final BluetoothGattCharacteristic outputCharacteristic;
    private final GattClient gattClient;
    private int mtu;

    /**
     * Factory method. Initializes with given arguments.
     * @param identifier An identifier used for JNI bridging and debugging.
     * @param gattClient The {@link GattClient} interacting with this stream.
     * @param outputCharacteristic The reliable output characteristic.
     */

    public static BleForeignOutputStream newInstance(
            String identifier,
            GattClient gattClient,
            BluetoothGattCharacteristic outputCharacteristic
    ) {
        final BleForeignOutputStream instance = new BleForeignOutputStream(
                identifier,
                gattClient,
                outputCharacteristic
        );
        instance.initialize();
        return instance;
    }

    private BleForeignOutputStream(
            String identifier,
            GattClient gattClient,
            BluetoothGattCharacteristic outputCharacteristic
    ) {
        super(identifier, TransportType.BLUETOOTH_LOW_ENERGY, true);

        Objects.requireNonNull(gattClient);
        Objects.requireNonNull(outputCharacteristic);

        this.mtu = gattClient.getMtu();
        this.gattClient = gattClient;
        this.outputCharacteristic = outputCharacteristic;
    }

    /**
     * Getter for the {@link GattClient} that will be interacting with the
     * output stream. The {@link GattClient} is the entity responsible for
     * writing to the remote characteristic and receiving responses from the
     * central.
     * @return The {@link GattClient}.
     */
    private GattClient getGattClient() {
        return this.gattClient;
    }

    /**
     * Getter for the output characteristic that the stream uses to write to
     * the remote central.
     * @return The {@link BluetoothGattCharacteristic} used to produce output.
     */
    private BluetoothGattCharacteristic getOutputCharacteristic() {
        return this.outputCharacteristic;
    }

    /**
     * Defines the MTU to use for the stream.
     * @param mtu The MTU to use.
     */
    private void setMtu(int mtu) {
        this.mtu = mtu;
    }

    /**
     * Returns the MTU that was previously negotiated between the devices or
     * the default value for BLE MTU ({@link MtuRegistry#DEFAULT_MTU}). In
     * fact, the implementation will return only 99% percent of the negotiated
     * MTU, and only down to a minimum of {@link MtuRegistry#DEFAULT_MTU}. This
     * is motivated by a bug in the Android stack, in which the negotiated MTU
     * will cause the server to refuse to respond to a write request and crash
     * the connection.
     * @return The MTU.
     */
    private int getMtu() {
        return Math.max(MtuRegistry.DEFAULT_MTU, (int)(this.mtu * .99));
    }

    @Override
    public void requestAdapterToOpen() {
        super.onOpen();
    }

    @Override
    public void requestAdapterToClose() {
        // TODO
    }

    @Override
    @MainThread
    public IoResult flush(byte[] data) {

        assert Looper.myLooper() == Looper.getMainLooper();

        byte[] dataToSend = ByteUtils.trimCopyToSize(data, getMtu());

        Timber.i(
                "ULX writing %d of %d buffered bytes (%d%%) to stream %s",
                dataToSend.length,
                data.length,
                (int) (dataToSend.length / (float) (data.length) * 100),
                getIdentifier()
        );

        if (!getOutputCharacteristic().setValue(dataToSend)) {

            UlxError error = new UlxError(
                    UlxErrorCode.UNKNOWN,
                    "Could not send the message to the destination.",
                    "Failed to write because the value could not be set " +
                            "locally on the BLE characteristic.",
                    "Try resetting the Bluetooth adapter."
            );

            return new IoResult(0, error);
        }

        if (!getGattClient().writeCharacteristic(getOutputCharacteristic())) {

            UlxError error = new UlxError(
                    UlxErrorCode.UNKNOWN,
                    "Could not send the message to the destination.",
                    "Failed to send the data to the remote device.",
                    "Try resetting the Bluetooth adapter."
            );

            return new IoResult(0, error);
        }

        // Not sure what it means if these values don't match
        if (dataToSend.length != getOutputCharacteristic().getValue().length) {
            throw new RuntimeException("The BLE implementation wrote to a " +
                    "characteristic but the value returned differs from what " +
                    "was expected. This means that some sort of corruption " +
                    "occurred, and must be fixed by the SDK.");
        }

        return new IoResult(dataToSend.length, null);
    }

    @Override
    public void onMtuNegotiated(GattClient gattClient, int mtu) {
        setMtu(mtu);
    }

    @Override
    public void onCharacteristicWritten(GattClient gattClient) {
        onSpaceAvailable();
    }

    @Override
    public void onCharacteristicWriteFailure(GattClient gattClient, UlxError error) {
        Timber.e(
                "ULX failed writing to characteristic with error %s",
                error.toString()
        );

        // Flag as closed
        close(error);
    }

    @Override
    public void onClose(UlxError error) {
        getGattClient().stop(error);
        super.onClose(error);
    }
}
