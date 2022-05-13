package com.uplink.ulx.bridge.network.model;

import com.uplink.ulx.UlxError;
import com.uplink.ulx.UlxErrorCode;
import com.uplink.ulx.drivers.model.Device;
import com.uplink.ulx.model.Instance;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import timber.log.Timber;

public class RoutingTable {

    /**
     * Represents an infinite number of hops, meaning that links with that hop
     * count are not reachable.
     */
    public static int HOP_COUNT_INFINITY = 0xFF;

    /**
     * This constant yields the maximum hop count that the network will accept
     * to process. Its original value is 3, since that should be enough to
     * prevent network loops (given that split horizon is in place). In order
     * to enable the maximum supported number of hops, set {@link
     * RoutingTable#HOP_COUNT_INFINITY}.
     */
    public static final int MAXIMUM_HOP_COUNT = 3;

    /**
     * The {@link RoutingTable} delegate propagates events that occur in the
     * context of the routing table, specifically {@link Instance}s being found
     * and lost on the network.
     */
    public interface Delegate {

        /**
         * This {@link Delegate} notification is triggered when the {@link
         * RoutingTable} identifies a {@link Link} to an {@link Instance} that
         * was previously unreachable. This means that the {@link Instance}
         * would not be reachable without that {@link Link}, and thus if the
         * {@link Link} is lost so will the {@link Instance}. On the other hand,
         * after an {@link Instance} being found, other {@link Link}s can be
         * found for it, giving redundancy and more options to make the {@link
         * Instance} reachable.
         * @param routingTable The {@link RoutingTable} issuing the notification.
         * @param instance The {@link Instance} that was found.
         */
        void onInstanceFound(RoutingTable routingTable, Instance instance);

        /**
         * This {@link Delegate} notification is triggered when the last known
         * {@link Link} to an {@link Instance} is lost. This means that the
         * {@link Instance} is no longer reachable and communication with it
         * is not possible. This will only occur when no {@link Link}s
         * whatsoever are known to the {@link Instance}.
         * @param routingTable The {@link RoutingTable} issuing the notification.
         * @param lastDevice The last lastDevice through which the instance was available until now
         * @param instance The {@link Instance} that was lost.
         * @param error An error, as an attempt to explain the loss.
         */
        void onInstanceLost(
                RoutingTable routingTable,
                @NonNull Device lastDevice,
                Instance instance,
                UlxError error
        );

        /**
         * This {@link Delegate} call indicates that the information for a given
         * link was created or updated. That is, a new entry has replaced a
         * previous one as being the "best link". If no previous entry existed,
         * then this will be the first one, which should correspond to an
         * instance being found event as well. It's notable that the given
         * {@link Link} is not necessarily better than the previous one; it
         * might happen that the previous best link was lost and replaced by
         * a second best alternative.
         * @param routingTable The {@link RoutingTable} issuing the notification.
         * @param link The {@link Link} that was updated.
         */
        void onLinkUpdate(RoutingTable routingTable, Link link);

        /**
         * This {@link Delegate} call happens after {@link #onLinkUpdate(RoutingTable, Link)} in
         * cases where a secondBestLink degradation took place. The goal is to notify the
         * bestLinkDevice about how we would reach the given instance otherwise
         *
         * @param routingTable   The {@link RoutingTable} issuing the notification.
         * @param bestLinkDevice The receiver of the update
         * @param destination    The destination at hand
         * @param hopCount       Hop count needed to reach the destination
         */
        void onSplitHorizonLinkUpdate(
                RoutingTable routingTable,
                Device bestLinkDevice,
                Instance destination,
                int hopCount
        );
    }

    /**
     * This represents {@link RoutingTable} entries. Such entries keep track
     * of which {@link Instance}s are reachable over which {@link Device}s,
     * as well as some other metadata associated with the link. This is
     * represented with the {@link Link} class.
     */
    private static class Entry {

        @NonNull
        private final Device device;
        private int internetHopCount;
        private List<Link> links;
        private InternetLink internetLink;

        /**
         * Constructor.
         * @param device The {@link Device} that functions as next-hop for all
         *               the {@link Link}s represented by this data structure.
         */
        Entry(@NonNull Device device) {
            this.device = device;
            this.links = null;
            this.internetHopCount = HOP_COUNT_INFINITY;
        }

        /**
         * Returns the {@link Device} that functions as the next-hop for the
         * {@link Link}s represented in this entry. That is, all of the {@link
         * Instance}s represented by this entry are reachable by sending a
         * packet to that {@link Device}.
         * @return The {@link Device} for the {@link Link}s represented.
         */
        @NonNull
        protected final Device getDevice() {
            return this.device;
        }

        /**
         * Returns the underlying data structure that is used to keep track of
         * known {@link Link}s.
         * @return The {@link List} that is used.
         */
        @NonNull
        protected final List<Link> getLinks() {
            if (this.links == null) {
                this.links = new CopyOnWriteArrayList<>();
            }
            return this.links;
        }

        /**
         * Registers the given {@link Instance} as being made reachable by this
         * {@link RoutingTable.Entry} (e.g. the {@link Device} that it holds).
         * This may correspond to a new {@link Link}, in which case one is
         * created. If the {@link Link} already exists, however, it will be
         * updated with the given metadata.
         * @param instance The {@link Instance} to register as reachable.
         * @param hopCount The minimum known number of hops.
         * @return The {@link Link} that was created or updated.
         */
        private Link add(Instance instance, int hopCount) {

            Link link = get(instance);

            if (link == null) {

                // Register a new link
                link = new Link(
                        getDevice(),
                        instance,
                        hopCount
                );
                getLinks().add(link);
            } else {

                // If the link is already known, we'll consider this an update
                link.setHopCount(hopCount);
            }

            return link;
        }

        /**
         * Removes the {@link Link} to the given {@link Instance} from the
         * registry. If the link is removed, the method will return {@code true}.
         * But if the link didn't already exit, nothing happens, and the method
         * returns {@code false} instead. This means that this method is safe to
         * call without previously checking for the instance's existance.
         * @param instance Whether the {@link Link} was removed.
         */
        public boolean remove(Instance instance) {

            Link link = get(instance);

            // We're trying to remove a link that isn't already there
            if (link == null) {
                return false;
            }

            // Remove it
            getLinks().remove(link);

            return true;
        }

        /**
         * Returns the {@link Link} that is known to make the given {@link
         * Instance} reachable, if one exists. If it doesn't, then this method
         * returns {@code null}. Each {@link Instance} should only be made
         * reachable through a single {@link Link} per {@link Device}, although
         * that is not enforced by this data structure. Still, this method
         * returns the first occurrence, which relies on the assumption that
         * only one exists.
         * @param instance The {@link Instance} to check.
         * @return The {@link Link} that makes the {@link Instance} reachable.
         */
        public Link get(Instance instance) {
            for (Link link : getLinks()) {
                if (link.getDestination().equals(instance)) {
                    return link;
                }
            }
            return null;
        }

        /**
         * Setter for the number of hops that it takes for the {@link #device} to reach the
         * Internet. A value of {@link RoutingTable#HOP_COUNT_INFINITY} means that the Internet is
         * not reachable at all.
         *
         * @param hopCount The number of hops to reach the Internet.
         */
        public void setInternetHopCount(int hopCount) {
            internetHopCount = hopCount;
            // Reset the invalidated internetLink
            internetLink = null;
        }

        /**
         * Getter for the number of hops that it takes for the {@link #device} to reach the
         * Internet. A value of {@link RoutingTable#HOP_COUNT_INFINITY} means that the Internet is
         * not reachable at all.
         *
         * @return The number of hops to reach the Internet.
         */
        public int getInternetHopCount() {
            return internetHopCount;
        }

        /**
         * @return the most fresh link
         */
        @Nullable
        public Link getMostRecentLink() {
            final List<Link> links = getLinks();
            // Since links are added to the end of the list at the time of their creation,
            // the most recent one is always the last
            return links.isEmpty() ? null : links.get(links.size() - 1);
        }

        /**
         * @return {@link InternetLink} for this {@link Entry}
         */
        public InternetLink getInternetLink() {
            if (internetLink == null) {
                internetLink = new InternetLink(device, getInternetHopCount());
            }
            return internetLink;
        }
    }

    /**
     * The {@link Device}-to-{@link Link} map, which is used to keep
     * track of which links are reachable over which devices. Links inform of
     * which {@link Instance}s are reachable, which means that the system can
     * use this data structure as a foundation for the mesh.
     */
    @GuardedBy("this") // The lock is needed for consistency of get-then-act operations,
    // but is not needed for visibility purposes, since it is unnecessary for ConcurrentMap
    private final ConcurrentMap<Device, Entry> linkMap;
    private WeakReference<Delegate> delegate;

    /**
     * Constructor.
     */
     public RoutingTable() {
         this.linkMap = new ConcurrentHashMap<>();
         this.delegate = null;
     }

    /**
     * Returns an {@link Entry} for the given {@link Device}. If one does not
     * exist, one will be created.
     * @param device The {@link Device} that maps to the {@link Entry}.
     * @return The {@link Entry} corresponding to the given {@link Device}.
     */
    protected synchronized Entry getLinkMapEntry(@NonNull Device device) {
        Entry entry = linkMap.get(device);

        // Create an Entry if one does not exist
        if (entry == null) {
            linkMap.put(device, entry = new Entry(device));
        }

        return entry;
    }

    /**
     * Sets the {@link Delegate} that will be getting notifications from the
     * {@link RoutingTable} in the future. If a previous delegate has been set,
     * it will be overridden.
     * @param delegate The {@link Delegate} to set.
     */
    public final void setDelegate(Delegate delegate) {
        this.delegate = new WeakReference<>(delegate);
    }

    /**
     * Returns the {@link Delegate} that was previously set. If none was set,
     * this method returns {@code null} instead. When a {@link Delegate}
     * instance is returned, a strong reference is returned, even though the
     * delegate is kept as a weak reference.
     * @return The current {@link Delegate}.
     */
    protected final Delegate getDelegate() {
        return this.delegate != null ? this.delegate.get() : null;
    }

    /**
     * Returns a {@link Device} that was previously registered with {@link
     * #register(Device)}. If no such device was registered, this method
     * returns {@code null} instead.
     * @param identifier The {@link Device}'s identifier.
     * @return The {@link Device} registered with the given identifier or {@code
     * null}.
     */
    public Device getDevice(String identifier) {
        for (final Device device : linkMap.keySet()) {
            if (device.getIdentifier().equals(identifier)) {
                return device;
            }
        }

        return null;
    }

    /**
     * Registers a new {@link Device} instance as a device that is identifiable
     * by the routing table. The device an be retrieved with {@link
     * #getDevice(String)}, and the lookup will occur through the device's
     * identifier, with {@link Device#getIdentifier()}.
     * @param device The {@link Device} to register.
     */
    public void register(@NonNull Device device) {
        // If the registry already exists, don't proceed. Doing so would erase
        // the current registry, and all known links would be lost.
        linkMap.putIfAbsent(device, new Entry(device));
    }

    /**
     * This method clears the entire registry for the given device. This
     * corresponds to all links correspond to that {@link Device} as next hop
     * being lost, which means that some {@link Instance}s may be lost
     * altogether. This method should be called when the connection with the
     * given {@link Device} drops and it becomes unreachable.
     * @param device The {@link Device} to remove.
     */
    public synchronized void unregister(@NonNull Device device) {

        final Entry entry = linkMap.get(device);

        // Don't proceed if the device is already not registered
        if (entry == null) {
            return;
        }

        // Remove the map entry. Doing so before unregisterAndNotify()
        // prevents updates to be sent to the device being unregistered
        linkMap.remove(device);

        // Send notifications for every instance that is lost, if needed
        for (Link link : entry.getLinks()) {
            unregisterAndNotify(link);
        }
    }

    /**
     * Registers or updates an entry on the routing table that maps the given
     * {@link Instance} as being reachable through the given {@link Device}.
     * This corresponds to a {@link Link} being created on the table.
     * @param device The next-hop {@link Device}.
     * @param instance The {@link Instance} that is (or was) reachable through the device.
     * @param hopCount The minimum known hop count.
     */
    public synchronized void registerOrUpdate(
            Device device,
            Instance instance,
            int hopCount
    ) {
        Timber.i(
                "ULX-M registering an update: %s %s %d",
                device.getIdentifier(),
                instance.getStringIdentifier(),
                hopCount
        );


        // We should not save links with more than maximum hop count.
        // This way we will be able to detect instance loss in circular connections
        // This also includes cases when hopCount == HOP_COUNT_INFINITY (i.e. unreachable)
        if (hopCount >= MAXIMUM_HOP_COUNT) {
            Timber.i(
                    "ULX will delete link for %s, because its hop count is %d (more than maximum)",
                    instance.getStringIdentifier(),
                    hopCount
            );

            // Find an existing entry for the given device
            final Entry existingEntry = linkMap.get(device);
            if (existingEntry != null) {
                // Find an existing link for the instance
                final Link existingLink = existingEntry.get(instance);
                if (existingLink != null) {
                    // Remove link from the registry
                    unregisterAndNotify(existingLink);
                }
            }
            return;
        }

        // We're querying the best link (or any link) so that we know whether
        // the link already existed after insertion
        final Link oldBestLink = getBestLink(instance, null);

        // Register the link
        Link newLink = getLinkMapEntry(device).add(instance, hopCount);

        // If the link did not previously exist, then it's new, which results
        // in two events (found and update). If it's not new, then it's an
        // update if its better than the previous one.
        if (oldBestLink == null) {
            Timber.i(
                    "ULX-M %s instance is new and will be propagated as found",
                    instance.getStringIdentifier()
            );
            notifyOnInstanceFound(instance);
            notifyOnLinkUpdate(newLink);
        } else {

            // Query the best link again, to check if quality changed
            Link newBestLink = getBestLink(instance, null);

            // We've just registered a new link, so there must be a best link at this point
            assert newBestLink != null;

            if (newBestLink.compareTo(oldBestLink) != 0) {
                Timber.i("ULX link quality changed, and an update will be propagated");
                notifyOnLinkUpdate(newLink);
            } else {
                Timber.e(
                        "ULX link not being relaxed: %s %s",
                        oldBestLink.toString(),
                        newBestLink.toString()
                );
            }
        }
    }

    /**
     * Updates information about i-hops count for the given instance
     *  @param device   The next-hop {@link Device}.
     * @param hopCount The number of hops that it takes for the instance to reach the Internet.
     */
    public synchronized void updateInternetHopsCount(Device device, int hopCount) {
        final Entry entry = linkMap.get(device);

        if (entry == null) {
            Timber.w(
                    "Unable to update i-hops count. Device [%s] was not found in routing table",
                    device
            );
            return;
        }

        if (entry.getLinks().isEmpty()) {
            Timber.d(
                    "Ignoring i-hops count update, because the device has not been negotiated with yet"
            );
            return;
        }

        if (hopCount < MAXIMUM_HOP_COUNT) {
            entry.setInternetHopCount(hopCount);
        } else {
            Timber.d("Internet hop count is higher than maximum. Considering it as unavailable");
            entry.setInternetHopCount(HOP_COUNT_INFINITY);
        }

        Timber.d("Updated i-hops count for device [%s]", device);
    }

    /**
     * Removes a {@link Link} from the registry, corresponding to the {@link Link} that makes the
     * given {@link Instance} reachable through the given {@link Device}. If this is the last {@link
     * Link} in the registry to make the {@link Instance} reachable, then the {@link Instance} will
     * be given as lost. Otherwise, if the best link has updated as a result of the change, the
     * update will be propagated. It is ok if the link's device (and thus the link itself) is not
     * already registered at this point. In such case, the method will just notify the remaining
     * devices about the link degradation, if necessary.
     *
     * @param link The link to remove
     */
    private synchronized void unregisterAndNotify(@NonNull Link link) {

        Timber.i("ULX deleting an instance from the registry");

        final Device device = link.getNextHop();
        final Instance instance = link.getDestination();

        final Link oldBestLink = getBestLink(instance, null);

        // Do not call getLinkMapEntry(), because it would create an empty entry if not found
        final Entry entry =  linkMap.get(device);
        if (entry != null) {
            // If the device is still registered, so must be the link
            if (!entry.remove(instance)) {
                Timber.e("ULX is trying to delete a link for an instance that is not reachable");
                return;
            }
        }

        // Query the best link again, to see whether that changed
        Link newBestLink = getBestLink(instance, null);

        // No new "best link" means no link at all, so the instance is lost
        if (newBestLink == null) {
            Timber.i(
                    "ULX instance %s is lost, since no other links exist",
                    instance.getStringIdentifier()
            );

            UlxError error = new UlxError(
                    UlxErrorCode.UNKNOWN,
                    "The instance is no longer reachable.",
                    "All links to the instance were lost.",
                    "Try turning the adapters on the destination " +
                            "on, or bringing it closer in distance."
            );

            notifyOnInstanceLost(device, instance, error);

            return;
        }

        // If oldBestLink is null, so must be the newBestLink, which makes this line unreachable
        assert oldBestLink != null;

        // If the link quality changed, then we propagate an update. Most
        // likely, this will relax the link's quality
        if (oldBestLink.compareTo(newBestLink) != 0) {
            Timber.i("ULX link quality degraded, and an update is being propagated");
            notifyOnLinkUpdate(newBestLink);
        }

        // No need to notify the split horizon if it is the owner of the instance in question
        if (newBestLink.getHopCount() > 1) {
            // Tell the split horizon about how we would reach the instance without him
            final Device splitHorizon = newBestLink.getNextHop();
            final Link secondBestLink = getBestLink(instance, splitHorizon);
            // TODO skip this step where the update wouldn't actually be an update for the split-horizon
            notifySplitHorizonUpdate(
                    splitHorizon,
                    instance,
                    secondBestLink != null ? secondBestLink.getHopCount() + 1 : HOP_COUNT_INFINITY
            );
        }
    }

    /**
     * Returns the best known mesh link to the Internet. The factor of quality is determined by
     * comparing the entries by {@link Entry#internetHopCount} and the most fresh timestamp among
     * their links. The {@code splitHorizon} argument is used to prevent determining paths that loop
     * on some previous hop. If the split horizon is {@code null}, the implementation should perform
     * the calculation without having the previous hop in consideration.
     *
     * @param splitHorizon the device to exclude from search
     * @return The best known mesh link to the Internet or {@code null}, if such doesn't exist
     */
    @Nullable
    public InternetLink getBestInternetLink(@Nullable Device splitHorizon) {

        final List<Entry> entries = new ArrayList<>(this.linkMap.values());

        // Sorting the list will yield the best entries at the top
        Collections.sort(
                entries,
                (o1, o2) -> {
                    final int iHopsDiff = Integer.signum(o1.getInternetHopCount() - o2.getInternetHopCount());
                    if (iHopsDiff != 0) {
                        return iHopsDiff;
                    } else if (o1.getInternetHopCount() < HOP_COUNT_INFINITY) {
                        final Link mostRecentLink1 = o1.getMostRecentLink();
                        final Link mostRecentLink2 = o2.getMostRecentLink();

                        // If internet hop count is set, there must be at least one link
                        assert mostRecentLink1 != null && mostRecentLink2 != null;

                        return Long.compare(
                                mostRecentLink1.getStability(),
                                mostRecentLink2.getStability()
                        );

                    } else {
                        // Entries without internet won't be considered anyway
                        return 0;
                    }
                }
        );


        for (Entry entry : entries) {
            if (!entry.getDevice().equals(splitHorizon)) {
                if (entry.getInternetHopCount() < HOP_COUNT_INFINITY) {
                    return entry.getInternetLink();
                } else {
                    // No need to continue traversing the sorted list - there's no link to internet
                    return null;
                }
            }
        }

        return null;
    }

    /**
     * Returns the best link known to a given instance. In fact, this method
     * currently just returns the first link that is found, since it currently
     * does not implement any heuristic for "best link". The {@code splitHorizon}
     * is a {@link Device} that is to be excluded from the search, since it
     * consists of the previous-hop {@link Device}. The packet shouldn't be
     * sent back, therefore that device is excluded from the search.
     * @param instance The {@link Instance} to reach.
     * @param splitHorizon A {@link Device} to exclude from the search as next
     *                     hop.
     * @return A {@link Link} to the given {@link Instance}, if one exists.
     */
    @Nullable
    public Link getBestLink(Instance instance, @Nullable Device splitHorizon) {

        List<Link> links = compileLinksTo(instance, splitHorizon);

        // Sorting the list will yield the best links at the top
        Collections.sort(links);

        // The best link should be the first, if one exists
        return links.size() > 0 ? links.get(0) : null;
    }

    /**
     * Compiles all known {@link Link}s to the given {@link Instance}. The links
     * will not be in any particular order, but it's known that only one exists
     * per next-hop {@link Device}.
     * @param instance The {@link Instance} to lookup.
     * @param splitHorizon
     * @return A {@link List} of {@link Link}s to the given {@link Instance}.
     */
    private List<Link> compileLinksTo(Instance instance, @Nullable Device splitHorizon) {

        final List<Link> linkList = new ArrayList<>();

        for (Entry entry : linkMap.values()) {
            Link link = entry.get(instance);

            if (link != null && !link.getNextHop().equals(splitHorizon)) {
                linkList.add(link);
            }
        }

        return linkList;
    }

    /**
     * Propagates an {@link Delegate#onInstanceFound(RoutingTable, Instance)}
     * found event to the delegate.
     * @param instance The {@link Instance} that was found.
     */
    private void notifyOnInstanceFound(Instance instance) {
        Delegate delegate = getDelegate();
        if (delegate != null) {
            delegate.onInstanceFound(this, instance);
        }
    }

    /**
     * Propagates a notification for an {@link Instance} being lost, which
     * means that the instance is not reachable anymore. This is propagated
     * through a {@link Delegate} call to {@link
     * Delegate#onInstanceLost(RoutingTable, Device, Instance, UlxError)}.
     * @param lastDevice The last lastDevice through which the instance was available until now
     * @param instance The {@link Instance} that is no longer reachable.
     * @param error An error, describing the failure.
     */
    private void notifyOnInstanceLost(
            @NonNull Device lastDevice,
            Instance instance,
            UlxError error
    ) {
        Delegate delegate = getDelegate();
        if (delegate != null) {
            delegate.onInstanceLost(this, lastDevice, instance, error);
        }
    }

    /**
     * Propagates a {@link Delegate} notification for a {@link Link} update.
     * @param link The new best {@link Link} for its destination.
     */
    private void notifyOnLinkUpdate(Link link) {
        Delegate delegate = getDelegate();
        if (delegate != null) {
            delegate.onLinkUpdate(this, link);
        }
    }

    /**
     * Propagates a {@link Delegate} notification for the destination's reachability in absence of
     * the split horizon
     *  @param device           the device with the best link. This is the receiver for the update
     * @param destination      the subject instance
     * @param hopCount         the minimum hop count to the instance if we can't reach the split
 *                         horizon
     */
    private void notifySplitHorizonUpdate(
            @NonNull Device device,
            Instance destination,
            int hopCount
    ) {
        final Delegate delegate = getDelegate();
        if (delegate != null) {
            delegate.onSplitHorizonLinkUpdate(
                    this,
                    device,
                    destination,
                    hopCount
            );
        }
    }

    /**
     * Returns a list of {@link Device}s that are known to the {@link
     * RoutingTable}. This will correspond to all devices currently connected
     * in direct link (LoS).
     * @return The list of known {@link Device}s.
     */
    public Set<Device> getDeviceList() {
        return linkMap.keySet();
    }

    /**
     * Compiles all {@link Instance}s that are known to the routing table, as
     * a set. This method iterates through the entire table, which can be
     * optimized in the future.
     * @return A {@link Set} of all known {@link Instance}s.
     */
    public Set<Instance> getInstances() {

        Set<Instance> instanceSet = new HashSet<>();

        for (Entry entry : linkMap.values()) {
            for (Link link : entry.getLinks()) {
                instanceSet.add(link.getDestination());
            }
        }

        return instanceSet;
    }

    public void log() {
        Timber.e("ULX-M logging the routing table");
        for (Entry entry : linkMap.values()) {

            for (Link link : entry.getLinks()) {
                Timber.e("ULX-M RTR %s", link.toString());
            }
        }
    }

    /**
     * A {@link Pair}, representing a device and hops count needed to reach internet by using it
     */
    public static class InternetLink extends Pair<Device, Integer> {
        /**
         * Constructor for a Pair.
         *
         * @param device            the device
         * @param internetHopsCount hops count needed to reach internet via the device
         */
        public InternetLink(Device device, int internetHopsCount) {
            super(device, internetHopsCount);
        }
    }
}
