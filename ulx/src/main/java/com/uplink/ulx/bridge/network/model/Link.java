package com.uplink.ulx.bridge.network.model;

import com.uplink.ulx.drivers.model.Device;
import com.uplink.ulx.model.Instance;

import java.util.Objects;

/**
 * A {@link Link} represents a link on the network thant makes a given {@link
 * Instance} reachable. It holds the {@link Device} that works as a next hop,
 * which means that in order to communicate with that {@link Instance} the
 * implementation knows to reach through the next hop {@link Device}. This
 * structure also holds some meta information, such as the minimum number of
 * hops that it takes to reach that instance, or whether it makes the Internet
 * reachable. This container is also hashable and comparable, so it's suitable
 * for use in complex data structures, such as hash tables and so on.
 */
public class Link implements Comparable<Link> {

    private final Device nextHop;
    private final Instance destination;
    private int hopCount;
    private boolean isInternetReachable;
    private final long timestamp;

    /**
     * Constructor.
     * @param nextHop The device in LoS that makes the {@link Instance} reachable.
     * @param destination The final destination {@link Instance}.
     * @param hopCount The number of hops to the final destination.
     * @param isInternetReachable Whether the link's destination is connected to
     *                            the Internet.
     */
    public Link(Device nextHop, Instance destination, int hopCount, boolean isInternetReachable)  {

        Objects.requireNonNull(nextHop);
        Objects.requireNonNull(destination);

        this.nextHop = nextHop;
        this.destination = destination;
        this.hopCount = hopCount;
        this.isInternetReachable = isInternetReachable;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Returns the next hop {@link Device} that is known for this {@link Link}.
     * This is the immediate-next device that makes the link reachable.
     * @return The next hop {@link Device}.
     */
    public final Device getNextHop() {
        return this.nextHop;
    }

    /**
     * Returns the destination {@link Instance} that is made reachable by this
     * {@link Link}.
     * @return The {@link Link}'s destination {@link Instance}.
     */
    public final Instance getDestination() {
        return this.destination;
    }

    /**
     * Updates the hop count defined for this {@link Link}. The previous value
     * is replaced by the update.
     * @param hopCount The new minimum known hop count to set.
     */
    public final void setHopCount(int hopCount) {
        this.hopCount = hopCount;
    }

    /**
     * Returns the estimated minimum amount of hops that is known to make the
     * {@link Instance} reachable over this {@link Link}. This value changes
     * with time, and can be updated with {@link #setHopCount(int)}. This value
     * isn't expected to grow too much, since mesh networks shouldn't support
     * infinitely huge amounts of hops.
     * @return The number of hops to reach the {@link Instance}.
     */
    public final int getHopCount() {
        return this.hopCount;
    }

    /**
     * Updates the indication as to whether the link makes the Internet
     * reachable. The previous value will be replaced.
     * @param isInternetReachable Whether the {@link Link} makes the Internet
     *                            reachable.
     */
    public final void setIsInternetReachable(boolean isInternetReachable) {
        this.isInternetReachable = isInternetReachable;
    }

    /**
     * Returns a flag that indicates whether the {@link Link} makes the Internet
     * reachable.
     * @return Whether the {@link Link} makes the Internet reachable.
     */
    public final boolean isInternetReachable() {
        return this.isInternetReachable;
    }

    /**
     * Returns the timestamp of creation for this link. This can be used to
     * check the link's stability: e.g. links that last longer are more stable.
     * @return The timestamp of creation for the link.
     */
    private long getTimestamp() {
        return this.timestamp;
    }

    /**
     * Returns the link's stability as a factor of time. The longer a given
     * link holds the more stable it is. This method returns the difference
     * between "now" and the link's time of creation, in milliseconds.
     * @return The link's stability as a factor of time.
     */
    public final long getStability() {
        return System.currentTimeMillis() - this.getTimestamp();
    }

    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }

        if (!(object instanceof Link)) {
            return false;
        }

        return equals((Link)object);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 31 * hash + getNextHop().hashCode();
        hash = 31 * hash + getDestination().hashCode();
        return hash;
    }

    /**
     * Compares two {@link Link}s by checking if the two have the same
     * destination and next hop. This means that the link is the same, since
     * each device is unaware of any other intermediary details.
     * @param other The other {@link Link} to check.
     * @return Whether the two {@link Link}s are equal.
     */
    public boolean equals(Link other) {
        return getDestination().equals(other.getDestination()) && getNextHop().equals(other.getNextHop());
    }

    @Override
    public int compareTo(Link o) {

        // The precedence goes to the hop count; if the hop count is lower,
        // we're not looking at the stability. This may prove wrong in the
        // future
        if (getHopCount() != o.getHopCount()) {
            return Integer.signum(-(getHopCount() - o.getHopCount()));
        }

        // The link's stability is the second factor: a link is better if it
        // has less hops and is more stable
        return Integer.signum((int) (getStability() - o.getStability()));
    }
}
