package com.uplink.ulx.model;

import com.uplink.ulx.utils.StringUtils;

import java.util.Arrays;

import androidx.annotation.NonNull;

/**
 * Instances map to devices participating on the network. Instances are
 * advertised on the network and, when found, a notification is issued
 * indicating that the instance is available for communicating. Usually,
 * there's no need to instantiate objects of this type at all, as the framework
 * handles that automatically. Instance identifiers are unique for each device,
 * and are divided in two parts: an app identifier and a device identifier. The
 * app identifier is always the same, as the SDK fragments the network using
 * that, so the app never needs to deal with instances from different vendors.
 * Device identifiers are automatically assigned by the SDK. The two identifiers
 * combined are 16 bytes long, being the first four assigned to the app
 * identifier and the rest to the device.
 */
public class Instance {

    private final byte [] identifier;
    private byte [] appIdentifier;
    private byte [] deviceIdentifier;

    /**
     * Constructor. Initializes the instance with the given identifier. The
     * identifier must be pre-validated and exactly 16 bytes long.
     * @param identifier The instance's identifier.
     */
    public Instance(byte[] identifier) {
        this.identifier = identifier;
        this.appIdentifier = null;
        this.deviceIdentifier = null;
    }

    /**
     * Getter for the instance's full identifier.
     * @return The instance's identifier.
     */
    public final byte [] getIdentifier() {
        return this.identifier;
    }

    /**
     * Returns the instance's identifier in string form, by writing the
     * identifier byte array using hexadecimal notation.
     * @return The instance's identifier in string form.
     */
    @NonNull
    public String getStringIdentifier() {
        return StringUtils.byteArrayToHexString(getIdentifier()).toUpperCase();
    }

    /**
     * This getter provides the app identifier in which the instance is
     * participating. All found instances have the same identifier, one that is
     * equal to that of the host device. App identifiers are always 4 bytes
     * long and may never be null.
     * @return The instance's app identifier.
     */
    public final byte [] getAppIdentifier() {
        if (this.appIdentifier == null) {
            this.appIdentifier = Arrays.copyOfRange(getIdentifier(), 0, 4);
        }
        return this.appIdentifier;
    }

    /**
     * This getter provides the instance's app identifier using an hexadecimal
     * encoding.
     * @return The instance's app identifier using an hexadecimal encoding.
     */
    public String getStringAppIdentifier() {
        return StringUtils.byteArrayToHexString(getAppIdentifier()).toUpperCase();
    }

    /**
     * Getter that returns the device identifier generated for the device by
     * the SDK.
     * @return The instance's device identifier.
     */
    public final byte [] getDeviceIdentifier() {
        if (this.deviceIdentifier == null) {
            this.deviceIdentifier = Arrays.copyOfRange(getIdentifier(), 4, 16);
        }
        return this.deviceIdentifier;
    }

    /**
     * Getter that returns the device identifier generated for the device by
     * the SDK, encoded as an hexadecimal string.
     * @return The instance's device identifier using an hexadecimal encoding.
     */
    public String getStringDeviceIdentifier() {
        return StringUtils.byteArrayToHexString(getDeviceIdentifier()).toUpperCase();
    }

    @SuppressWarnings("ControlFlowStatementWithoutBraces")
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Instance)) return false;
        final Instance instance = (Instance) o;
        return Arrays.equals(identifier, instance.identifier);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(identifier);
    }

    @NonNull
    @Override
    public String toString() {
        return "Instance{" +
                "identifier=" + getStringIdentifier() +
                '}';
    }
}
