package com.uplink.ulx.bridge.io.model;

import com.uplink.ulx.model.Instance;

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
    private final boolean internetReachable;

    public UpdatePacket(
            int sequenceIdentifier,
            Instance instance,
            int hopCount,
            boolean reachable,
            boolean internetReachable
    ) {
        super(sequenceIdentifier, PacketType.UPDATE);

        // The hop count must be lower than what would fill a whole byte
        assert hopCount >= 0 && hopCount <= 0xFF;

        this.instance = Objects.requireNonNull(instance);
        this.hopCount = hopCount;
        this.reachable = reachable;
        this.internetReachable = internetReachable;
    }

    public final Instance getInstance() {
        return this.instance;
    }

    public final int getHopCount() {
        return this.hopCount;
    }

    public final boolean isReachable() {
        return this.reachable;
    }

    public final boolean isInternetReachable() {
        return this.internetReachable;
    }
}
