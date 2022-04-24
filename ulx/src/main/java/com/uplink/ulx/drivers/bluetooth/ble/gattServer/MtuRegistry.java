package com.uplink.ulx.drivers.bluetooth.ble.gattServer;

import android.bluetooth.BluetoothDevice;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * This is a registry that is used by the GATT server to keep track of the MTU
 * for each device that has negotiated with it. It will use the device's
 * address ({@link BluetoothDevice#getAddress()}) to associate the device with
 * a given MTU integer value.
 */
public class MtuRegistry {

    /**
     * This is the maximum support value for MTU; 512 bytes per transmission.
     */
    public static final int MAXIMUM_MTU = 512;

    /**
     * The "normal" (also the minimum) MTU size for BLE is 20 bytes. The
     * implementation will always go for the maximum (512), but this is still
     * the default value. If the MTU negotiation fails, this is what the
     * implementation will use.
     */
    public static final int DEFAULT_MTU = 20;

    public static final int MTU_REQUEST_TIMEOUT_MS = 20000;

    /**
     * Data structure for the MTU table. Maps device addresses with their respective MTU values.
     */
    private final ConcurrentMap<String, Integer> registry;

    /**
     * Constructor.
     */
    public MtuRegistry() {
        this.registry = new ConcurrentHashMap<>();
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
        registry.put(address, mtu);
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
        Integer mtu = registry.get(address);
        return mtu != null ? mtu : DEFAULT_MTU;
    }
}
