package com.uplink.ulx.bridge.io.model;

import androidx.annotation.NonNull;

/**
 * A packet that is sent to all connected instances when the host's i-hops count changes
 */
public class InternetUpdatePacket extends AbstractPacket {
    private final int hopCount;

    /**
     * Constructor
     *
     * @param sequenceIdentifier packet sequence identifier
     * @param hopCount           number of hops needed to reach an instance with internet
     *                           connection.
     */
    public InternetUpdatePacket(int sequenceIdentifier, int hopCount) {
        super(sequenceIdentifier, PacketType.INTERNET_UPDATE);
        this.hopCount = hopCount;
    }

    public int getHopCount() {
        return hopCount;
    }

    @NonNull
    @Override
    public String toString() {
        return "InternetUpdatePacket{" +
                "hopCount=" + hopCount +
                "} " + super.toString();
    }
}
