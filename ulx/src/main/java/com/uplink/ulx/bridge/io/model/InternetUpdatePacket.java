package com.uplink.ulx.bridge.io.model;

import com.uplink.ulx.model.Instance;

import androidx.annotation.NonNull;

/**
 * A packet that is sent to all connected instances when the host's i-hops count changes
 */
public class InternetUpdatePacket extends AbstractPacket {
    private final Instance originator;
    private final int hopCount;

    /**
     * Constructor
     *
     * @param sequenceIdentifier packet sequence identifier
     * @param originator         The originator {@link Instance}'s identifier.
     * @param hopCount           number of hops needed to reach an originator with internet
     *                           connection.
     */
    public InternetUpdatePacket(int sequenceIdentifier, Instance originator, int hopCount) {
        super(sequenceIdentifier, PacketType.INTERNET_UPDATE);
        this.originator = originator;
        this.hopCount = hopCount;
    }

    public Instance getOriginator() {
        return originator;
    }

    public int getHopCount() {
        return hopCount;
    }

    @NonNull
    @Override
    public String toString() {
        return "InternetUpdatePacket{" +
                "originator=" + originator +
                ", hopCount=" + hopCount +
                "} " + super.toString();
    }
}
