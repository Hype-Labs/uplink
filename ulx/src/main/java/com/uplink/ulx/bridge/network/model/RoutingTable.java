package com.uplink.ulx.bridge.network.model;

import android.util.Log;

import com.uplink.ulx.UlxError;
import com.uplink.ulx.UlxErrorCode;
import com.uplink.ulx.drivers.model.Device;
import com.uplink.ulx.model.Instance;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.annotation.Nullable;

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
         * @param instance The {@link Instance} that was lost.
         * @param error An error, as an attempt to explain the loss.
         */
        void onInstanceLost(RoutingTable routingTable, Instance instance, UlxError error);

        /**
         * This {@link Delegate} call indicates that the information for a given
         * link was created or updated. That is, a new entry has replaced a
         * previous one as being the "best link". If no previous entry existed,
         * then this will be the first one, which should correspond to an
         * instance being found event as well. It's notable that the given
         * {@link Link} is not necessarily better than the previous one; it
         * might happen that the previous best link was lost and replaced by
         * a second best alternative.
         * @param routingTable  The {@link RoutingTable} issuing the notification.
         * @param link The {@link Link} that was updated.
         */
        void onLinkUpdate(RoutingTable routingTable, Link link);
    }

    /**
     * This represents {@link RoutingTable} entries. Such entries keep track
     * of which {@link Instance}s are reachable over which {@link Device}s,
     * as well as some other metadata associated with the link. This is
     * represented with the {@link Link} class.
     */
    protected static class Entry {

        private final Device device;
        private List<Link> links;

        /**
         * Constructor.
         * @param device The {@link Device} that functions as next-hop for all
         *               the {@link Link}s represented by this data structure.
         */
        Entry(Device device) {
            this.device = device;
            this.links = null;
        }

        /**
         * Returns the {@link Device} that functions as the next-hop for the
         * {@link Link}s represented in this entry. That is, all of the {@link
         * Instance}s represented by this entry are reachable by sending a
         * packet to that {@link Device}.
         * @return The {@link Device} for the {@link Link}s represented.
         */
        protected final Device getDevice() {
            return this.device;
        }

        /**
         * Returns the underlying data structure that is used to keep track of
         * known {@link Link}s.
         * @return The {@link List} that is used.
         */
        protected final List<Link> getLinks() {
            if (this.links == null) {
                this.links = new ArrayList<>();
            }
            return this.links;
        }

        /**
         * Collects all destination {@link Instance}s that are made accessible
         * by this {@link Entry}.
         * @return A list of instances.
         */
        protected final List<Instance> collectDestinations() {
            List<Instance> instances = new ArrayList<>();

            for (Link link : getLinks()) {
                instances.add(link.getDestination());
            }

            return instances;
        }

        /**
         * Registers the given {@link Instance} as being made reachable by this
         * {@link RoutingTable.Entry} (e.g. the {@link Device} that it holds).
         * This may correspond to a new {@link Link}, in which case one is
         * created. If the {@link Link} already exists, however, it will be
         * updated with the given metadata.
         * @param instance The {@link Instance} to register as reachable.
         * @param hopCount The minimum known number of hops.
         * @param internetHopCount The number of hops that it takes for the
         *                         {@link Link} to reach the Internet.
         * @return The {@link Link} that was created or updated.
         */
        public Link add(Instance instance, int hopCount, int internetHopCount) {

            Link link = get(instance);

            if (link == null) {

                // Register a new link
                link = new Link(getDevice(), instance, hopCount, internetHopCount);
                getLinks().add(link);
            } else {

                // If the link is already known, we'll consider this an update
                link.setHopCount(hopCount);
                link.setInternetHopCount(internetHopCount);
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
         * Returns the Internet-connected {@link Link} that compares the best
         * against all others, also known to have {@link #getDevice()} as the
         * next hop.
         * @return The best known Internet connection for this {@link Entry}.
         */
        public Link getBestInternetLink() {

            List<Link> internetLinks = compileInternetLinks();

            // Sorting the links will compare them over stability and number of
            // hops. This will yield the best links at the top. However, this
            // are not necessarily the best Internet links, but rather overall
            // links. Still, we known that the links provide Internet
            // reachability, even if not ideally.
            Collections.sort(internetLinks, (o1, o2) ->
                    Integer.signum(o1.getInternetHopCount() - o2.getInternetHopCount()));

            return internetLinks.size() > 0 ? internetLinks.get(0) : null;
        }

        /**
         * Compiles a {@link List} of {@link Link}s that are known to be
         * connected to the Internet over the next-hop that corresponds to
         * this table entry. If there are no known links to be connected, the
         * implementation will return an empty {@link List}.
         * @return A list of Internet-connected {@link Link}s.
         */
        private List<Link> compileInternetLinks() {
            List<Link> linkList = new ArrayList<>();

            for (Link link : getLinks()) {
                if (link.getInternetHopCount() < RoutingTable.HOP_COUNT_INFINITY) {
                    linkList.add(link);
                }
            }

            return linkList;
        }
    }

    private HashMap<Device, Entry> linkMap;
    private WeakReference<Delegate> delegate;

    /**
     * Constructor.
     */
     public RoutingTable() {
         this.linkMap = null;
         this.delegate = null;
     }

    /**
     * Returns the {@link Device}-to-{@link Link} map, which is used to keep
     * track of which links are reachable over which devices. Links inform of
     * which {@link Instance}s are reachable, which means that the system can
     * use this data structure as a foundation for the mesh.
     * @return The map that tracks {@link Instance} reachability.
     */
    protected final HashMap<Device, Entry> getLinkMap() {
        if (this.linkMap == null) {
            this.linkMap = new HashMap<>();
        }
        return this.linkMap;
    }

    /**
     * Returns an {@link Entry} for the given {@link Device}. If one does not
     * exist, one will be created.
     * @param device The {@link Device} that maps to the {@link Entry}.
     * @return The {@link Entry} corresponding to the given {@link Device}.
     */
    protected Entry getLinkMapEntry(Device device) {
        Entry entry = getLinkMap().get(device);

        // Create an Entry if one does not exist
        if (entry == null) {
            getLinkMap().put(device, entry = new Entry(device));
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
    public synchronized Device getDevice(String identifier) {
        for (Map.Entry<Device, Entry> entry : getLinkMap().entrySet()) {
            if (entry.getKey().getIdentifier().equals(identifier)) {
                return entry.getKey();
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
    public synchronized void register(Device device) {

        // If the registry already exists, don't proceed. Doing so would erase
        // the current registry, and all known links would be lost.
        if (getLinkMap().containsKey(device)) {
            return;
        }

        // Register a new entry
        getLinkMap().put(device, new Entry(device));
    }

    /**
     * This method clears the entire registry for the given device. This
     * corresponds to all links correspond to that {@link Device} as next hop
     * being lost, which means that some {@link Instance}s may be lost
     * altogether. This method should be called when the connection with the
     * given {@link Device} drops and it becomes unreachable.
     * @param device The {@link Device} to remove.
     */
    public synchronized void unregister(Device device) {

        Entry entry = getLinkMap().get(device);

        // Don't proceed if the device is already not registered
        if (entry == null) {
            return;
        }

        // Collect all instances that are linked by the given device. We'll
        // use these later to assert which ones were lost
        List<Instance> destinations = entry.collectDestinations();

        // Remove the map entry
        getLinkMap().remove(device);

        // For the collected destinations, propagate the ones that are no longer
        // reachable as lost. This means that eliminating the device entry also
        // eliminated the last thing that made them reachable
        notifyAllUnreachableAsLost(destinations);
    }

    /**
     * Flags each given {@link Instance} as lost if no more paths exist for
     * it. This should be called after an {@link Entry} is cleared from the
     * table.
     * @param destinations The destinations to lose.
     */
    private void notifyAllUnreachableAsLost(List<Instance> destinations) {

        for (Instance instance : destinations) {

            // Instances that are no longer reachable are lost
            if (!isReachable(instance)) {

                UlxError error = new UlxError(
                        UlxErrorCode.UNKNOWN,
                        "The instance was lost.",
                        "It is no longer reachable on the network.",
                        "Check if the adapters on the destination " +
                                "device are on, or if its connected in close range."
                );

                notifyOnInstanceLost(instance, error);
            }
        }
    }

    /**
     * Registers or updates an entry on the routing table that maps the given
     * {@link Instance} as being reachable through the given {@link Device}.
     * This corresponds to a {@link Link} being created on the table.
     * @param device The next-hop {@link Device}.
     * @param instance The {@link Instance} that is now reachable.
     * @param hopCount The minimum known hop count.
     * @param internetHopCount The number of hops that it takes for the instance
     *                         to reach the Internet.
     */
    public synchronized void registerOrUpdate(Device device, Instance instance, int hopCount, int internetHopCount) {
        Log.i(getClass().getCanonicalName(),
                String.format(
                        "ULX-M registering an update: %s %s %d %d",
                        device.getIdentifier(),
                        instance.getStringIdentifier(),
                        hopCount,
                        internetHopCount
                )
        );

        // Values of infinity are deleted instead, since the instance is no
        // longer reachable
        if (hopCount == HOP_COUNT_INFINITY) {

            throw new RuntimeException("Link removal is not supported yet");

            //unregister(device, instance);
            //return;
        }

        // We're querying the best link (or any link) so that we know whether
        // the link already existed after insertion
        Link oldBestLink = getBestLink(instance, null);

        // Register the link
        Link newLink = getLinkMapEntry(device).add(instance, hopCount, internetHopCount);

        // If the link did not previously exist, then it's new, which results
        // in two events (found and update). If it's not new, then it's an
        // update if its better than the previous one.
        if (oldBestLink == null) {
            Log.i(getClass().getCanonicalName(), String.format("ULX-M %s instance is new and will be propagated as found", instance.getStringIdentifier()));
            notifyOnInstanceFound(instance);
            notifyOnLinkUpdate(newLink);
        } else {

            // Query the best link again, to check if quality changed
            Link newBestLink = getBestLink(instance, null);

            if (newBestLink.compareTo(oldBestLink) != 0) {
                Log.i(getClass().getCanonicalName(), "ULX link quality changed, and an update will be propagated");
                notifyOnLinkUpdate(newLink);
            } else {
                Log.e(getClass().getCanonicalName(), String.format("ULX link not being relaxed: %s %s", oldBestLink.toString(), newBestLink.toString()));
            }
        }
    }

    /**
     * Removes a {@link Link} from the registry, corresponding to the {@link
     * Link} that makes the given {@link Instance} reachable through the given
     * {@link Device}. If this is the last {@link Link} in the registry to
     * make the {@link Instance} reachable, then the {@link Instance} will be
     * given as lost.
     * @param device The next hop {@link Device} for the {@link Link}.
     * @param instance The {@link Instance} for the {@link Link} to remove.
     */
    private void unregister(Device device, Instance instance) {
        /*
        Log.i(getClass().getCanonicalName(), "ULX deleting an instance from the registry");

        Link bestLink = getBestLink(instance, null);

        // The absence of a "best link" means the absence of any link at all,
        // even thought that shouldn't happen at this point
        if (bestLink == null) {
            Log.e(getClass().getCanonicalName(), "ULX is trying to delete a link for an instance that is not reachable");
            return;
        }

        // If nothing is removed, no changes occurred
        if (!getLinkMapEntry(device).remove(instance)) {
            Log.e(getClass().getCanonicalName(), "ULX is trying to delete a link that is not registered");
            return;
        }

        // Query the best link again, to see whether that changed
        Link newBestLink = getBestLink(instance, null);

        // No new "best link" means no link at all, so the instance is lost
        if (newBestLink == null) {
            Log.i(getClass().getCanonicalName(), String.format("ULX instance %s is lost, since no other links exist", instance.getStringIdentifier()));

            UlxError error = new UlxError(
                    UlxErrorCode.UNKNOWN,
                    "The instance is no longer reachable.",
                    "All links to the instance were lost.",
                    "Try turning the adapters on the destination " +
                            "on, or bringing it closer in distance."
            );

            notifyOnInstanceLost(instance, error);

            // A link update is flagged as a lost instance by the link being
            // null (e.g. no link is known to it)
            notifyOnLinkUpdate(null);

            return;
        }

        // If the link quality changed, then we propagate an update. Most
        // likely, this will relax the link's quality
        if (bestLink.compareTo(newBestLink) != 0) {
            Log.i(getClass().getCanonicalName(), "ULX link quality degraded, and an update is being propagated");
            notifyOnLinkUpdate(newBestLink);
        }
         */
    }

    /**
     * Returns the best known mesh link to the Internet. The factor of quality
     * is determined by comparing the links using the {@link Link#compareTo(Link)}
     * method, which accounts for stability and number of hops. The {@code
     * splitHorizon} argument is not used yet, but will be in future versions
     * to prevent determining paths that loop on some previous hop. If the
     * split horizon is {@code null}, the implementation should perform the
     * calculation without having the previous hop in consideration.
     * @param splitHorizon Not used.
     * @return The best known mesh link to the Internet.
     */
    public Link getBestInternetLink(Device splitHorizon) {

        List<Link> links = compileInternetLinks();

        // Sorting the list will yield the best links at the top
        Collections.sort(links, (o1, o2)
                -> Integer.signum(o1.getInternetHopCount() - o2.getInternetHopCount()));

        // The best link should be the first, if one exists
        return links.size() > 0 ? links.get(0) : null;
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
    public Link getBestLink(Instance instance, Device splitHorizon) {

        List<Link> links = compileLinksTo(instance, splitHorizon);

        // Sorting the list will yield the best links at the top
        Collections.sort(links);

        // The best link should be the first, if one exists
        return links.size() > 0 ? links.get(0) : null;
    }

    /**
     * Determines whether an {@link Instance} is still reachable.
     * @param instance The {@link Instance} to check.
     * @return Whether the {@link Instance} is reachable.
     */
    private boolean isReachable(Instance instance) {
        return !compileLinksTo(instance, null).isEmpty();
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

        List<Link> linkList = new ArrayList<>();

        for (Map.Entry<Device, Entry> entry : getLinkMap().entrySet()) {
            Link link = entry.getValue().get(instance);

            if (link != null && !link.getNextHop().equals(splitHorizon)) {
                linkList.add(link);
            }
        }

        return linkList;
    }

    /**
     * Compiles a {@link List} of Internet links known to the {@link
     * RoutingTable}. This includes all links that are known, and the list is
     * created on the fly with each call. The list will include only one {@link
     * Link} per connected {@link Device}, corresponding to the best known link
     * going through that device.
     * @return A {@link List} of known Internet {@link Link}s.
     */
    private List<Link> compileInternetLinks() {

        List<Link> linkList = new ArrayList<>();

        for (Map.Entry<Device, Entry> entry : getLinkMap().entrySet()) {
            Link link = entry.getValue().getBestInternetLink();

            if (link != null) {
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
     * Delegate#onInstanceLost(RoutingTable, Instance, UlxError)}.
     * @param instance The {@link Instance} that is no longer reachable.
     * @param error An error, describing the failure.
     */
    private void notifyOnInstanceLost(Instance instance, UlxError error) {
        Delegate delegate = getDelegate();
        if (delegate != null) {
            delegate.onInstanceLost(this, instance, error);
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
     * Returns a list of {@link Device}s that are known to the {@link
     * RoutingTable}. This will correspond to all devices currently connected
     * in direct link (LoS).
     * @return The list of known {@link Device}s.
     */
    public Set<Device> getDeviceList() {
        return getLinkMap().keySet();
    }

    /**
     * Returns a {@link List} of all {@link Link}s known to the routing table.
     * All links will be under the threshold of {@link #MAXIMUM_HOP_COUNT}
     * number of hops. The returned list is a copy and is generated with each
     * call.
     * @return A {@link List} of {@link Link}s.
     */
    public List<Link> getLinks() {

        List<Link> linkList = new ArrayList<>();

        for (Map.Entry<Device, Entry> entry : getLinkMap().entrySet()) {
            for (Link link : entry.getValue().getLinks()) {
                if (link.getHopCount() < MAXIMUM_HOP_COUNT) {
                    linkList.add(link);
                }
            }
        }

        return linkList;
    }

    /**
     * Compiles all {@link Instance}s that are known to the routing table, as
     * a set. This method iterates through the entire table, which can be
     * optimized in the future.
     * @return A {@link Set} of all known {@link Instance}s.
     */
    public Set<Instance> getInstances() {

        Set<Instance> instanceSet = new HashSet<>();

        for (Map.Entry<Device, Entry> entry : getLinkMap().entrySet()) {
            for (Link link : entry.getValue().getLinks()) {
                instanceSet.add(link.getDestination());
            }
        }

        return instanceSet;
    }

    public void log() {
        Log.e(getClass().getCanonicalName(), "ULX-M logging the routing table");
        for (HashMap.Entry<Device, Entry> entry : getLinkMap().entrySet()) {

            Entry deviceEntry = entry.getValue();

            for (Link link : deviceEntry.getLinks()) {
                Log.e(getClass().getCanonicalName(), String.format("ULX-M RTR %s", link.toString()));
            }
        }
    }
}
