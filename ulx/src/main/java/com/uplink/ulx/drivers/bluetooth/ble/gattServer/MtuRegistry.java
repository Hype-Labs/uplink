package com.uplink.ulx.drivers.bluetooth.ble.gattServer;

import android.bluetooth.BluetoothDevice;

import java.util.HashMap;

/**
 * This is a registry that is used by the GATT server to keep track of the MTU
 * for each device that has negotiated with it. It will use the device's
 * address ({@link BluetoothDevice#getAddress()}) to associate the device with
 * a given MTU integer value.
 */
public class MtuRegistry {

    private HashMap<String, Integer> registry;

    /**
     * Constructor.
     */
    public MtuRegistry() {
        this.registry = null;
    }

    /**
     * Returns the underlying supporting data structure for the MTU table. This
     * consists of an hash map that maps device addresses with their respective
     * MTU values.
     * @return The registry.
     */
    protected final HashMap<String, Integer> getRegistry() {
        if (this.registry == null) {
            this.registry = new HashMap<>();
        }
        return this.registry;
    }

    /**
     * Associates a device ({@link BluetoothDevice}) with a given MTU.
     * @param bluetoothDevice The device.
     * @param mtu The MTU.
     */
    public void set(BluetoothDevice bluetoothDevice, int mtu) {
        set(bluetoothDevice.getAddress(), mtu);
    }

    /**
     * Associates a device address with the given MTU.
     * @param address The address.
     * @param mtu The MTU.
     */
    public void set(String address, int mtu) {
        getRegistry().put(address, mtu);
    }

    /**
     * Returns the MTU that has previously been associated with the given
     * {@link BluetoothDevice}. If no association was previously done, this
     * method returns zero.
     * @param bluetoothDevice The device.
     * @return The MTU or zero.
     */
    public int get(BluetoothDevice bluetoothDevice) {
        return get(bluetoothDevice.getAddress());
    }

    /**
     * Returns the MTU that has previously been associated with the given
     * address. If no association was previously done, this method returns zero.
     * @param address The address.
     * @return The MTU or zero.
     */
    public int get(String address) {
        Integer mtu = getRegistry().get(address);
        return mtu != null ? mtu : 0;
    }
}
