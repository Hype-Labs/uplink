package com.uplink.ulx.bridge.io.model;

import com.uplink.ulx.model.Instance;

import java.util.Locale;
import java.util.Objects;

/**
 * An {@link HandshakePacket} is one that is exchanged by the devices as soon
 * as they see each other and the streams are ready to communicate. This enables
 * the devices to provide each other with identification and to get acquainted
 * with other metadata.
 */
public class HandshakePacket extends AbstractPacket {

    private final Instance originator;
    private final int internetHops;

    /**
     * Constructor.
     * @param sequenceIdentifier The packet's sequence identifier.
     * @param originator The originator {@link Instance}'s identifier.
     * @param internetHops How many hops it takes for the originator to reach
     *                     the Internet.
     */
    public HandshakePacket(int sequenceIdentifier, Instance originator, int internetHops) {
        super(sequenceIdentifier, PacketType.HANDSHAKE);

        Objects.requireNonNull(originator);

        this.originator = originator;
        this.internetHops = internetHops;
    }

    /**
     * Returns the host instance that is propagating its own identifier. This
     * identifier will be used to map the device on the network.
     * @return The host instance's identifier.
     */
    public final Instance getOriginator() {
        return this.originator;
    }

    /**
     * Getter for the number of hops that it takes for the packet's originator
     * to reach the Internet. Zero means a direct connection, while infinity
     * means no connection at all.
     * @return The number of hops it takes for the originator to reach the
     * Internet.
     */
    public final int getInternetHops() {
        return this.internetHops;
    }

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH,"%s(seq: %d, src: %s, i-hops: %d)",
                getClass().getSimpleName(),
                getSequenceIdentifier(),
                getOriginator().getStringIdentifier(),
                getInternetHops()
        );
    }
}
