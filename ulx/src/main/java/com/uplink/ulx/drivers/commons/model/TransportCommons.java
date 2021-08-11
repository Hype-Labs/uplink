package com.uplink.ulx.drivers.commons.model;

import com.uplink.ulx.drivers.model.Channel;
import com.uplink.ulx.drivers.model.Transport;

/**
 * Implements all functionality that is common to Transport implementations,
 * regardless of their transport types. This is mostly a container for several
 * meta information regarding the transport, since those don't implement a lot
 * of logic. This includes identifiers, transport types, and reliable channel.
 */
public abstract class TransportCommons implements Transport {

    private final String identifier;
    private final int transportType;
    private final Channel reliable;

    /**
     * Constructor. Initializes with the given arguments.
     * @param identifier An identifier for the Transport.
     * @param transportType The Transport's transport type.
     * @param reliable The Transport's reliable Channel.
     */
    public TransportCommons(String identifier, int transportType, Channel reliable) {
        this.identifier = identifier;
        this.transportType = transportType;
        this.reliable = reliable;
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
    public final Channel getReliableChannel() {
        return this.reliable;
    }
}
