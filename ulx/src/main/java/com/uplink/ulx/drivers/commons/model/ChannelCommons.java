package com.uplink.ulx.drivers.commons.model;

import com.uplink.ulx.drivers.model.Channel;
import com.uplink.ulx.drivers.model.InputStream;
import com.uplink.ulx.drivers.model.OutputStream;

/**
 * Implements all functionality that is common to Channels, regardless of their
 * transport types. This is mostly a container for several metainformation
 * regarding the channel, since those don't implement a lot of logic. This
 * includes identifiers, transport types, and the I/O stream pair.
 */
public abstract class ChannelCommons implements Channel {

    private final String identifier;
    private final int transportType;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private final boolean reliable;

    /**
     * Constructor. Initializes with the given arguments.
     * @param identifier An identifier for the Channel.
     * @param transportType The Channel's transport type.
     * @param inputStream The stream responsible for input operations.
     * @param outputStream The stream responsible for output operations.
     * @param reliable Whether the Channel represents reliable I/O.
     */
    public ChannelCommons(String identifier, int transportType, InputStream inputStream, OutputStream outputStream, boolean reliable){
        this.identifier = identifier;
        this.transportType = transportType;
        this.inputStream = inputStream;
        this.outputStream = outputStream;
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
    public final InputStream getInputStream() {
        return this.inputStream;
    }

    @Override
    public final OutputStream getOutputStream() {
        return this.outputStream;
    }

    @Override
    public final boolean isReliable() {
        return this.reliable;
    }
}
