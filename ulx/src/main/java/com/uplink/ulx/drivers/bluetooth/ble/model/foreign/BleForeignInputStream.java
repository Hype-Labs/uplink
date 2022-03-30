package com.uplink.ulx.drivers.bluetooth.ble.model.foreign;

import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Looper;

import com.uplink.ulx.TransportType;
import com.uplink.ulx.UlxError;
import com.uplink.ulx.drivers.bluetooth.ble.gattClient.GattClient;
import com.uplink.ulx.drivers.commons.model.InputStreamCommons;

import java.util.Objects;

public class BleForeignInputStream extends InputStreamCommons implements GattClient.InputStreamDelegate {

    private final BluetoothGattCharacteristic characteristic;
    private final GattClient gattClient;

    /**
     * Factory method. Initializes with the given arguments. By default, this also
     * initializes the stream to trigger onDataAvailable delegate notifications
     * as soon as data arrives.
     * @param identifier An identifier used for JNI bridging and debugging.
     * @param gattClient The {@link GattClient} used to interact with the adapter.
     * @param inputCharacteristic The reliable input characteristic.
     */

    public static BleForeignInputStream newInstance(
            String identifier,
            GattClient gattClient,
            BluetoothGattCharacteristic inputCharacteristic
    ) {
        final BleForeignInputStream instance = new BleForeignInputStream(
                identifier,
                gattClient,
                inputCharacteristic
        );
        instance.initialize();
        return instance;
    }

    private BleForeignInputStream(
            String identifier,
            GattClient gattClient,
            BluetoothGattCharacteristic inputCharacteristic
    ) {
        super(identifier, TransportType.BLUETOOTH_LOW_ENERGY, true);

        Objects.requireNonNull(gattClient);
        Objects.requireNonNull(inputCharacteristic);

        this.gattClient = gattClient;
        this.characteristic = inputCharacteristic;
    }

    /**
     * Getter for the {@link GattClient} that is being used by the stream to
     * interact with the adapter.
     * @return The {@link GattClient} used by the stream.
     */
    private GattClient getGattClient() {
        return this.gattClient;
    }

    /**
     * The reliable input characteristic that is associated with this stream.
     * @return The {@link BluetoothGattCharacteristic} associated with the
     * stream.
     */
    private BluetoothGattCharacteristic getCharacteristic() {
        return this.characteristic;
    }

    @Override
    public void requestAdapterToOpen() {
        assert Looper.myLooper() == Looper.getMainLooper();
        getGattClient().subscribeCharacteristic(getCharacteristic());
    }

    @Override
    public void requestAdapterToClose() {
    }

    @Override
    public void onOpen(GattClient gattClient) {
        super.onOpen();
    }

    @Override
    public void onCharacteristicChanged(GattClient gattClient, BluetoothGattCharacteristic characteristic) {
        notifyDataReceived(characteristic.getValue());
    }

    @Override
    protected void onClose(UlxError error) {
        getGattClient().stop(error);
        super.onClose(error);
    }
}
