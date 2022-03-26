package com.uplink.ulx.bridge;

import com.uplink.ulx.drivers.model.Device;
import com.uplink.ulx.model.Instance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;

// TODO this API should be reviewed; this registry is practically unusable.

/**
 * This is a class that is used to keep track of {@link Device} associations
 * with a generic type {@code T}. Each {@link Device} will correspond to several
 * instances of {@code T}, while each {@code T} instance should correspond to
 * several {@link Device} instances. This makes it a many-to-many association.
 * An example of {@code T} could be {@link android.bluetooth.BluetoothDevice},
 * the Android native representation of a Bluetooth device. This will create
 * an association between the native device abstraction and our own.
 * @param <T> A generic type that is to be associated with {@link Device}.
 */
public class Registry<T> {

    private Map<String, Device> deviceRegistry;

    private Map<String, List<String>> genericToDeviceRegistry;

    /**
     * Constructor.
     */
    public Registry() {
        this.deviceRegistry = null;

        this.genericToDeviceRegistry = null;
    }

    /**
     * Returns the hash map that is used to keep track of the Device abstract
     * entity. The map keeps tracks of devices using their String identifiers.
     * @return The abstract registry hash map.
     */
    private Map<String, Device> getDeviceRegistry() {
        if (this.deviceRegistry == null) {
            this.deviceRegistry = new HashMap<>();
        }
        return this.deviceRegistry;
    }

    /**
     * Returns the hash map that is used to map generic identifiers to a list
     * of Device identifiers.
     * @return The registry that maps generic identifiers to Device instances.
     */
    private Map<String, List<String>> getGenericToDeviceRegistry() {
        if (this.genericToDeviceRegistry == null) {
            this.genericToDeviceRegistry = new HashMap<>();
        }
        return this.genericToDeviceRegistry;
    }

    /**
     * This method checks if the given {@code identifier} is already present on
     * the generic-to-device relationship and, if not, creates a new collection
     * of items, empty and ready to use. This collection is the one that is
     * returned.
     * @param identifier The identifier to check.
     * @return The collection associated with the given identifier.
     */
    @NonNull
    protected final List<String> getGenericToDeviceList(String identifier) {

        List<String> list = getGenericToDeviceRegistry().get(identifier);

        // Create if none exists
        if (list == null) {
            getGenericToDeviceRegistry().put(identifier, list = new ArrayList<>());
        }

        return list;
    }

    /**
     * Sets the given {@code device} as the instance matching the given
     * {@code identifier}. This relationship will be included in the device
     * registry.
     * @param identifier The identifier.
     * @param device The device ({@link Device}).
     */
    public void setDevice(String identifier, Device device) {
        getDeviceRegistry().put(identifier, device);
    }

    public void unsetDevice(String identifier) {
        getDeviceRegistry().remove(identifier);
    }

    /**
     * Creates an association between the two given identifiers (generic and
     * device). Each of those identifiers is assigned a collection of elements
     * which will contain the other one. This way, each identifier is mapped
     * with zero, one, or more identifiers, a list that is guaranteed to contain
     * this relationship. This can be used to track several types of
     * relationships with devices: {@link Instance}, native system types, etc.
     * @param genericIdentifier The generic identifier for type T.
     * @param deviceIdentifier The device identifier.
     */
    public void associate(String genericIdentifier, String deviceIdentifier) {
        getGenericToDeviceList(genericIdentifier).add(deviceIdentifier);
    }

    public void dissociate(String genericIdentifier, String deviceIdentifier) {
        getGenericToDeviceList(genericIdentifier).remove(deviceIdentifier);
    }

    /**
     * Returns the {@link Device} instance that has previously been associated
     * with the given {@code identifier}. If no such association exists, this
     * method will return {@code null} instead.
     * @param identifier The identifier to check.
     * @return The {@link Device} instance associated with the identifier.
     */
    public Device getDeviceInstance(String identifier) {
        return getDeviceRegistry().get(identifier);
    }

    /**
     * Returns a list of {@link Device} identifiers that have previously been
     * associated with the given generic identifier. If no such association
     * exists, this method returns an empty list.
     * @param identifier The generic identifier to check.
     * @return The list of device identifiers associated with the identifier.
     */
    @NonNull
    public List<String> getDeviceIdentifiersFromGenericIdentifier(String identifier) {
        return getGenericToDeviceList(identifier);
    }

    /**
     * Returns a list of {@link Device} instances that have previously been
     * associated with the given generic {@code identifier}. This corresponds to
     * iterating the list of associations and replacing each {@link String}
     * identifier there with the {@link Device} instance that is mapped to it.
     * If any given such identifier is seen but no matching {@link Device}
     * exists, {@code null} will be used in its place.
     * @param identifier The identifier to check.
     * @return A list of {@link Device} instances associated with the identifier.
     */
    public List<Device> getDevicesFromGenericIdentifier(String identifier) {

        List<Device> devices = new ArrayList<>();

        // Iterate the list of identifiers and query the instance; if one does
        // not exist, null will be added.
        for (String it : getDeviceIdentifiersFromGenericIdentifier(identifier)) {
            devices.add(getDeviceInstance(it));
        }

        return devices;
    }
}
