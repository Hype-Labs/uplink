package com.uplink.ulx.bridge.io.model;

import com.uplink.ulx.model.Instance;

import java.util.Objects;

/**
 * An {@link HandshakePacket} is one that is exchanged by the devices as soon
 * as they see each other and the streams are ready to communicate. This enables
 * the devices to provide each other with identification and to get acquainted
 * with other metadata.
 */
public class HandshakePacket extends AbstractPacket {

    private final Instance originator;

    /**
     * Constructor.
     * @param sequenceIdentifier The packet's sequence identifier.
     * @param originator The originator {@link Instance}'s identifier.
     */
    public HandshakePacket(int sequenceIdentifier, Instance originator) {
        super(sequenceIdentifier, PacketType.HANDSHAKE);

        this.originator = Objects.requireNonNull(originator);
    }

    /**
     * Returns the host instance that is propagating its own identifier. This
     * identifier will be used to map the device on the network.
     * @return The host instance's identifier.
     */
    public final Instance getOriginator() {
        return this.originator;
    }
}
