package com.uplink.ulx.bridge.io.model;

import com.uplink.ulx.model.Instance;

import java.util.Locale;
import java.util.Objects;

/**
 * An {@link AcknowledgementPacket} is one that is used to acknowledge the
 * reception of a packet back to its destination. When such a packet is
 * received, the origin {@link #getOrigin()} is acknowledging to the destination
 * {@link #getDestination()} that it received its packet with the sequence
 * identifier {@link #getSequenceIdentifier()}. The sequence identifier for
 * this packet will be the same as the sequence identifier for the original
 * packet.
 */
public class AcknowledgementPacket extends AbstractPacket {

    private final Instance origin;
    private final Instance destination;

    /**
     * Constructor. Initializes with the given arguments.
     * @param sequenceIdentifier The sequence identifier for the packet being
     *                           acknowledged.
     * @param origin The device that sent the acknowledgement.
     * @param destination The device that sent the original packet.
     */
    public AcknowledgementPacket(int sequenceIdentifier, Instance origin, Instance destination) {
        super(sequenceIdentifier, PacketType.ACKNOWLEDGEMENT);

        Objects.requireNonNull(origin);
        Objects.requireNonNull(destination);

        this.origin = origin;
        this.destination = destination;
    }

    /**
     * Returns the originator {@link Instance}, which is the device that is
     * acknowledging the reception of a packet.
     * @return The originator {@link Instance}
     */
    public final Instance getOrigin() {
        return this.origin;
    }

    /**
     * Returns the destination {@link Instance}, which is the device that sent
     * the original packet and is meant to receive this one.
     * @return The destination {@link Instance}.
     */
    public final Instance getDestination() {
        return this.destination;
    }

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH,"%s(seq: %d, src: %s, dst: %s)",
                getClass().getSimpleName(),
                getSequenceIdentifier(),
                getOrigin().getStringIdentifier(),
                getDestination().getStringIdentifier()
        );
    }
}
