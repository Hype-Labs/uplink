package com.uplink.ulx.model;

import com.uplink.ulx.drivers.model.Device;

import java.util.HashMap;

public class Registry<T> {

    private HashMap<String, T> nativeRegistry;
    private HashMap<String, Device> abstractRegistry;
    private HashMap<String, String> nativeToAbstractRegistry;
    private HashMap<String, String> abstractToNativeRegistry;

    public Registry() {
        this.nativeRegistry = null;
        this.abstractRegistry = null;
    }

    protected final HashMap<String, T> getNativeRegistry() {
        if (this.nativeRegistry == null) {
            this.nativeRegistry = new HashMap<>();
        }
        return this.nativeRegistry;
    }

    protected final HashMap<String, Device> getAbstractRegistry() {
        if (this.abstractRegistry == null) {
            this.abstractRegistry = new HashMap<>();
        }
        return this.abstractRegistry;
    }

    protected final HashMap<String, String> getNativeToAbstractRegistry() {
        if (this.nativeToAbstractRegistry == null) {
            this.nativeToAbstractRegistry = new HashMap<>();
        }
        return this.nativeToAbstractRegistry;
    }

    protected final HashMap<String, String> getAbstractToNativeRegistry() {
        if (this.abstractToNativeRegistry == null) {
            this.abstractToNativeRegistry = new HashMap<>();
        }
        return this.abstractToNativeRegistry;
    }

    public void set(String address, T nativeDevice) {
        getNativeRegistry().put(address, nativeDevice);
    }

    public void set(String identifier, Device device) {
        getAbstractRegistry().put(identifier, device);
    }

    public void associate(String address, String identifier) {
        getNativeToAbstractRegistry().put(address, identifier);
        getAbstractToNativeRegistry().put(identifier, address);
    }

    public T getNativeDevice(String address) {
        return getNativeRegistry().get(address);
    }

    public Device getDevice(String identifier) {
        return getAbstractRegistry().get(identifier);
    }

    public Device getDeviceFromAddress(String address) {
        String identifier = getNativeToAbstractRegistry().get(address);

        // Native registry not found
        if (identifier == null) {
            return null;
        }

        return getDevice(identifier);
    }

    public T getNativeFromIdentifier(String identifier) {
        String address = getAbstractToNativeRegistry().get(identifier);

        // Abstract registry not found
        if (address == null) {
            return null;
        }

        return getNativeDevice(address);
    }
}
