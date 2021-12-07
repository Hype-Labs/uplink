package com.uplink.ulx.bridge.io.model;

import com.uplink.ulx.model.Instance;

import java.util.Locale;
import java.util.Objects;

/**
 * {@link UpdatePacket}s are used to represent {@link
 * com.uplink.ulx.bridge.network.model.Link}s as they are seen by the host
 * device. This wraps meta information about reachability to a given {@link
 * Instance}, including the minimum known hop count and whether it is known to
 * be connected to the internet. {@link UpdatePacket}s also represent a type
 * of event ({@link #isReachable()}), which indicates whether the packet was
 * created because the {@link Instance} was found or lost.
 */
public class UpdatePacket extends AbstractPacket {

    private final Instance instance;
    private final int hopCount;
    private final boolean reachable;
    private final int internetHopCount;

    /**
     * Constructor. Initializes with given arguments.
     * @param sequenceIdentifier Packet sequence identifier.
     * @param instance Destination {@link Instance} (the one being updated).
     * @param hopCount Number of hops to the {@link Instance}.
     * @param reachable Whether the instance is still reachable.
     * @param internetHopCount Number of hops to reach to the Internet, if a
     *                         request is made through this instance.
     */
    public UpdatePacket(
            int sequenceIdentifier,
            Instance instance,
            int hopCount,
            boolean reachable,
            int internetHopCount
    ) {
        super(sequenceIdentifier, PacketType.UPDATE);

        // The hop count must be lower than what would fill a whole byte
        assert hopCount >= 0 && hopCount <= 0xFF;

        this.instance = Objects.requireNonNull(instance);
        this.hopCount = hopCount;
        this.reachable = reachable;
        this.internetHopCount = internetHopCount;
    }

    /**
     * The destination {@link Instance} that the {@link UpdatePacket} refers.
     * @return The destination {@link Instance}.
     */
    public final Instance getInstance() {
        return this.instance;
    }

    /**
     * Returns the number of hops to reach the {@link Instance}.
     * @return The number of hops to reach the {@link Instance}.
     */
    public final int getHopCount() {
        return this.hopCount;
    }

    /**
     * Whether the {@link Instance} is reachable, according to the update, or
     * is being flagged as lost.
     * @return Whether the {@link Instance} is reachable.
     */
    public final boolean isReachable() {
        return this.reachable;
    }

    /**
     * The number of hops that it would take to reach the Internet if an
     * Internet request was to be made through this {@link Instance}.
     * @return The number of hops to reach the Internet.
     */
    public final int getInternetHopCount() {
        return this.internetHopCount;
    }

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH,"%s(seq: %d, i: %s, hops: %s, i-hops: %d)",
                getClass().getSimpleName(),
                getSequenceIdentifier(),
                getInstance().getStringIdentifier(),
                getHopCount(),
                getInternetHopCount()
        );
    }
}
