package com.uplink.ulx.bridge.io.model;

/**
 * This enumeration lists the packet types that are supported by the
 * implementation at an I/O level. This means that these are the types of
 * packets that are actually encoded in a binary format and sent over the
 * network.
 */
public enum PacketType {

    /**
     * An handshake packet uses type {@link HandshakePacket} to represent the
     * handshake between two devices. This provides each device with
     * identification information.
     */
    HANDSHAKE(0),

    /**
     * An update packet uses type {@link UpdatePacket} to represent network
     * updates that respect the mesh network. This includes mostly routing
     * table updates and Internet reachability dissemination, so that other
     * devices are aware of their surroundings.
     */
    UPDATE(1),

    /**
     * A data packet uses type {@link DataPacket} to represent actual content
     * being sent over the network. Such packets include a destination and a
     * payload.
     */
    DATA(2),

    /**
     * This type represents a packet that is used to acknowledge the reception
     * of some other packet.
     */
    ACKNOWLEDGEMENT(3),

    /**
     * This type represents a packet that is sent over the network to relay an
     * Internet request over the mesh.
     */
    INTERNET(4),

    /**
     * This type represents a response to an Internet request.
     */
    INTERNET_RESPONSE(5),

    /**
     * This type represents update in internet connectivity
     */
    INTERNET_UPDATE(6),
    ;

    private final int id;

    /**
     * Constructor.
     * @param id The packet type's numeric identification, used in the
     *           packet's encoded formats.
     */
    PacketType(int id) {
        this.id = id;
    }

    /**
     * Getter for the packet type's numeric identification. This field is
     * encoded in a binary format when the packet is being sent over the
     * network.
     * @return The packet type's numeric identification.
     */
    public final int getId() {
        return this.id;
    }
}
