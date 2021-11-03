package com.uplink.ulx.bridge.io.model;

import com.uplink.ulx.model.Instance;

import java.net.URL;
import java.util.Objects;

/**
 * An {@link InternetPacket} is one that is sent over the mesh in order to
 * make a request to a server by relying on a remote peer. The packet traverses
 * the network until it finds an Internet-enabled destination.
 */
public class InternetPacket extends AbstractPacket {

    private final URL url;
    private final String data;
    private final Instance originator;
    private final int hopCount;
    private final int test;

    /**
     * Constructor. Initializes the hop count to zero.
     * @param url The {@link URL} server to query.
     * @param data The data to send to the server, as {@code application/json}.
     * @param test The test ID.
     * @param originator The {@link Instance} that originated the request, and
     *                   to which the response will be forwarded.
     */
    public InternetPacket(int sequenceIdentifier, URL url, String data, int test, Instance originator) {
        this(sequenceIdentifier, url, data, test, originator, 0);
    }

    /**
     * Constructor.
     * @param url The {@link URL} server to query.
     * @param data The data to send to the server, as {@code application/json}.
     * @param test The test ID.
     * @param originator The {@link Instance} that originated the request, and
     *                   to which the response will be forwarded.
     * @param hopCount The number of hops that the packet has travelled.
     */
    public InternetPacket(int sequenceIdentifier, URL url, String data, int test, Instance originator, int hopCount) {
        super(sequenceIdentifier, PacketType.INTERNET);

        Objects.requireNonNull(url);
        Objects.requireNonNull(data);
        Objects.requireNonNull(originator);

        this.url = url;
        this.data = data;
        this.test = test;
        this.originator = originator;
        this.hopCount = hopCount;
    }

    /**
     * Getter for {@link URL} that will be queried by the proxy.
     * @return The {@link URL}.
     */
    public final URL getUrl() {
        return this.url;
    }

    /**
     * Getter for the {@code application/json}-encoded data.
     * @return The data to send to the server.
     */
    public final String getData() {
        return this.data;
    }

    /**
     * Getter for the {@link Instance} that originated the packet.
     * @return The originator {@link Instance}.
     */
    public final Instance getOriginator() {
        return this.originator;
    }

    /**
     * Returns the number of hops that the packet has travelled.
     * @return The number of hops that the packet has travelled.
     */
    public final int getHopCount() {
        return this.hopCount;
    }

    /**
     * Returns the test identifier.
     * @return The test identifier.
     */
    public final int getTest() {
        return this.test;
    }
}
