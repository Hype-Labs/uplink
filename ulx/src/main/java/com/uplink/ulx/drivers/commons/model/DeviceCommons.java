package com.uplink.ulx.drivers.commons.model;

import com.uplink.ulx.drivers.model.Connector;
import com.uplink.ulx.drivers.model.Device;
import com.uplink.ulx.drivers.model.Transport;

/**
 * Implements all functionality that is common to Device implementations,
 * regardless of their transport types. This is mostly a container for several
 * meta information regarding the device, since those don't implement a lot of
 * logic. This includes identifiers, transport types, and the device's connector
 * and transport pairs.
 */
public abstract class DeviceCommons implements Device {

    private final String identifier;
    private final int transportType;
    private final Connector connector;
    private final Transport transport;

    /**
     * Constructor. Initializes with the given arguments.
     * @param identifier An identifier for the Device.
     * @param transportType The Device's transport type.
     * @param connector The Device's Connector.
     * @param transport The Device's Transport.
     */
    public DeviceCommons(String identifier, int transportType, Connector connector, Transport transport) {
        this.identifier = identifier;
        this.transportType = transportType;
        this.connector = connector;
        this.transport = transport;
    }

    @Override
    public final String getIdentifier() {
        return this.identifier;
    }

    @Override
    public final int getTransportType() {
        return this.transportType;
    }

    @Override
    public final Transport getTransport() {
        return this.transport;
    }

    @Override
    public final Connector getConnector() {
        return this.connector;
    }

    @Override
    public int hashCode() {
        return getIdentifier().hashCode();
    }

    @Override
    public boolean equals(Object other) {

        if (!(other instanceof DeviceCommons)) {
            return false;
        }

        // Two devices equal if their identifiers equal
        return getIdentifier().equals(((DeviceCommons)other).getIdentifier());
    }
}
