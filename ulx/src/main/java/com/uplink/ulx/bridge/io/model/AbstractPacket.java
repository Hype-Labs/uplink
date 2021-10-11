package com.uplink.ulx.bridge.io.model;

public class AbstractPacket implements Packet {

    private final int sequenceIdentifier;
    private final PacketType type;

    public AbstractPacket(int sequenceIdentifier, PacketType type) {
        this.sequenceIdentifier = sequenceIdentifier;
        this.type = type;
    }

    @Override
    public final PacketType getType() {
        return this.type;
    }

    @Override
    public final int getSequenceIdentifier() {
        return this.sequenceIdentifier;
    }
}
