package com.uplink.ulx.bridge.network.controller;

import android.util.Log;

import com.uplink.ulx.UlxError;
import com.uplink.ulx.bridge.SequenceGenerator;
import com.uplink.ulx.bridge.io.controller.IoController;
import com.uplink.ulx.bridge.io.model.AcknowledgementPacket;
import com.uplink.ulx.bridge.io.model.DataPacket;
import com.uplink.ulx.bridge.io.model.HandshakePacket;
import com.uplink.ulx.bridge.io.model.Packet;
import com.uplink.ulx.bridge.io.model.UpdatePacket;
import com.uplink.ulx.bridge.network.model.Link;
import com.uplink.ulx.bridge.network.model.RoutingTable;
import com.uplink.ulx.bridge.network.model.Ticket;
import com.uplink.ulx.drivers.model.Device;
import com.uplink.ulx.drivers.model.InputStream;
import com.uplink.ulx.drivers.model.OutputStream;
import com.uplink.ulx.model.Instance;

import java.lang.ref.WeakReference;
import java.util.Objects;

/**
 * The {@link NetworkController} is the module that manages network-related
 * stuff, such as routing tables, reachability, network updates, and so on.
 * This class offers a low-level API for sending content ({@link
 * #send(byte[], Instance)}) and negotiating ({@link #negotiate(Device)}).
 */
public class NetworkController implements IoController.Delegate, RoutingTable.Delegate {

    /**
     * This constant yields the maximum hop count that the network will accept
     * to process. Its original value is 3, since that should be enough to
     * prevent network loops (given that split horizon is in place). In order
     * to enable the maximum supported number of hops, set {@link
     * RoutingTable#HOP_COUNT_INFINITY}.
     */
    public static final int MAXIMUM_HOP_COUNT = 4;

    /**
     * The {@link NetworkController} delegate receives notifications from the
     * network controller, with respect to instances being found and some
     * aspects of packet lifecycles.
     */
    public interface Delegate {

        /**
         * A new {@link Instance} has been found on the network. This differs
         * from a {@link Device} being found in the sense that instances may
         * correspond to a device over multiple transports, while a {@link
         * Device} corresponds to a single transport. For example, a device
         * being found over Bluetooth LE corresponds to a {@link Device}; if
         * the device is also found over Infrastructure WiFi, it will be a
         * different {@link Device}, but both will be encapsulated under the
         * same {@link Instance}. In order to solve this problem, the
         * implementation will have to negotiate with the remote device for
         * some sort of proof-of-identity.
         *
         * @param networkController The {@link NetworkController} issuing the
         *                          notification.
         * @param instance          The {@link Instance} that was found.
         */
        void onInstanceFound(NetworkController networkController, Instance instance);

        /**
         * This method gives indication to the {@link Delegate} that an {@link
         * Instance} that was previously known is no longer reachable on the
         * network. This means that no known path exists to that {@link
         * Instance}, and thus the host is not capable of exchanging content
         * with it anymore.
         * @param networkController The {@link NetworkController} issuing the
         *                          notification.
         * @param instance The {@link Instance} that was lost.
         * @param error An error, describing a probable cause for the loss.
         */
        void onInstanceLost(NetworkController networkController, Instance instance, UlxError error);

        /**
         * This {@link Delegate} notification gives indication that data has
         * been received, which corresponds to a {@link DataPacket} being
         * received.
         *
         * @param networkController The {@link NetworkController}.
         * @param data              The data that was received.
         * @param origin            The {@link Instance} that sent the data.
         */
        void onReceived(NetworkController networkController, byte[] data, Instance origin);

        /**
         * This {@link Delegate} notification is called when the packet
         * corresponding to the given {@link Ticket} is known to have been sent.
         * This corresponds to the packet having been written to the streams,
         * and possibly not yet have left the device.
         *
         * @param networkController The {@link NetworkController}.
         * @param ticket            The {@link Ticket} for the packet that was sent.
         */
        void onSent(NetworkController networkController, Ticket ticket);

        /**
         * This {@link Delegate} notification is called when a {@link DataPacket}
         * could not be sent. The implementation will not attempt to resend the
         * packet, and no recovery is in progress.
         *
         * @param networkController The {@link NetworkController}.
         * @param ticket            The {@link Ticket} corresponding to the packet that
         *                          could not be sent.
         * @param error             An error, describing a probable cause for the failure.
         */
        void onSendFailure(NetworkController networkController, Ticket ticket, UlxError error);

        /**
         * This {@link Delegate} notification is called when an acknowledgement
         * is received in confirmation of a another packet having been delivered
         * to the destination. The packet will correspond to the one that was
         * created for the given {@link Ticket}.
         *
         * @param networkController The {@link NetworkController}.
         * @param ticket The {@link Ticket} corresponding to the {@link Packet}
         *               that was acknowledged.
         */
        void onAcknowledgement(NetworkController networkController, Ticket ticket);
    }

    private abstract static class NetworkPacket extends IoController.IoPacket {

        /**
         * Constructor.
         * @param packet The {@link Packet} being wrapped.
         */
        public NetworkPacket(Packet packet) {
            super(packet);
        }

        public abstract void handleOnWritten();
        public abstract void handleOnWriteFailure(UlxError error);
    }

    private IoController ioController;
    private RoutingTable routingTable;
    private final Instance hostInstance;

    private WeakReference<Delegate> delegate;

    // This identifier is used to give a sequence number to packets as they are
    // being dispatched. The sequence begins in 0 and resets at 65535.
    private SequenceGenerator sequenceGenerator;

    /**
     * Constructor.
     */
    public NetworkController(Instance hostInstance) {

        Objects.requireNonNull(hostInstance);

        this.ioController = null;
        this.routingTable = null;
        this.hostInstance = hostInstance;

        this.delegate = null;

        this.sequenceGenerator = null;
    }

    /**
     * Returns the {@link IoController} that is responsible for packet I/O
     * operations. The controller's delegate {@link IoController.Delegate} will
     * also be set for the context instance.
     * @return The {@link IoController}.
     */
    public final IoController getIoController() {
        if (this.ioController == null) {
            this.ioController = new IoController();
            this.ioController.setDelegate(this);
        }
        return this.ioController;
    }

    /**
     * Returns the {@link RoutingTable} that is used by the controller to keep
     * track of devices and links on the network.
     * @return The {@link RoutingTable}.
     */
    protected final RoutingTable getRoutingTable() {
        if (this.routingTable == null) {
            this.routingTable = new RoutingTable();
            this.routingTable.setDelegate(this);
        }
        return this.routingTable;
    }

    /**
     * Returns a strong reference to the {@link Delegate} instance that is
     * currently receiving delegate notifications. If no delegate has been
     * registered or if the delegate has been deallocated, this method returns
     * null.
     * @return A strong reference to the {@link Delegate}.
     */
    private Delegate getDelegate() {
        return this.delegate != null ? this.delegate.get() : null;
    }

    /**
     * Sets the {@link Delegate} that will receive notifications from the
     * network controller in the future. If any previous delegate has been set,
     * it will be overridden. The instance keeps a weak reference to the
     * delegate, in order to prevent cyclic dependencies.
     * @param delegate The {@link Delegate} to set.
     */
    public final void setDelegate(Delegate delegate) {
        this.delegate = new WeakReference<>(delegate);
    }

    /**
     * Returns the {@link SequenceGenerator} that is used by this instance in
     * order to produce sequence numbers for packets.
     * @return The {@link SequenceGenerator}.
     */
    private SequenceGenerator getSequenceGenerator() {
        if (this.sequenceGenerator == null) {
            this.sequenceGenerator = new SequenceGenerator(65535);
        }
        return this.sequenceGenerator;
    }

    /**
     * Getter for the host instance that lives in the host device. This is
     * passed from above and kept here for negotiation purposes.
     * @return The host {@link Instance}.
     */
    private Instance getHostInstance() {
        return this.hostInstance;
    }

    /**
     * Negotiates with the given {@link Device} by sending an handshake packet
     * that identifies the host instance. The negotiation process does not
     * involve any other steps from the host's end, other than waiting for the
     * remote device to reply with a similar handshake packet. That moment
     * flags an instance being found event. This method also registers the
     * {@link Device} in the device registry, meaning that when input is
     * received from this device it will be recognized.
     * @param device The {@link Device}.
     */
    public void negotiate(Device device) {

        // Register the device
        getRoutingTable().register(device);

        // Create the handshake packet and send
        HandshakePacket packet = new HandshakePacket(
                getSequenceGenerator().generate(),
                getHostInstance()
        );

        // Instantiate the IoPacket with the Device as lookup
        IoController.IoPacket ioPacket = new NetworkPacket(packet) {

            @Override
            public Device getDevice() {
                return device;
            }

            @Override
            public void handleOnWritten() {
                // Nothing to do
            }

            @Override
            public void handleOnWriteFailure(UlxError error) {
                // Negotiation failed?
            }
        };

        // Queue for dispatch
        getIoController().add(ioPacket);
    }

    /**
     * Creates a {@link DataPacket} that is sent to the given destination. The
     * {@link DataPacket} is created from the given {@code data} and {@code
     * destination}, with an incremented sequence identifier (see {@link
     * SequenceGenerator}). The {@link Link} to the destination is calculated
     * from the {@link RoutingTable}, which should yield the best next-hop that
     * makes the destination reachable. That calculation is perform when the
     * time comes for the packet to be dispatched by the {@link IoController}.
     * This method returns a {@link Ticket}, which can be used by callers in
     * order to track progress delivery for the packet.
     * @param data The data to send, as a byte array.
     * @param destination The destination for the data.
     * @return A {@link Ticket} used to track the result of the operation.
     */
    public Ticket send(byte[] data, Instance destination) {

        // Create a data packet with the necessary info
        DataPacket packet = new DataPacket(
                getSequenceGenerator().generate(),
                getHostInstance(),
                destination,
                data
        );

        // Create a Ticket for the operation
        Ticket ticket = new Ticket(packet.getSequenceIdentifier(), destination);

        // Instantiate the IoPacket with the proper next-hop lookup
        IoController.IoPacket ioPacket = new NetworkPacket(packet) {

            @Override
            public Device getDevice() {
                return getBestLinkNextHopDevice(destination, null);
            }

            @Override
            public void handleOnWritten() {
                notifyOnSent(ticket);
            }

            @Override
            public void handleOnWriteFailure(UlxError error) {
                notifyOnSendFailure(ticket, error);
            }
        };

        // Queue for dispatch
        getIoController().add(ioPacket);

        return ticket;
    }

    /**
     * Sends an acknowledgement back to the originator indicating that the
     * packet was received.
     * @param packet The {@link DataPacket} that was received.
     */
    private void acknowledge(DataPacket packet) {
        Log.i(getClass().getCanonicalName(), String.format("ULX acknowledging packet %d", packet.getSequenceIdentifier()));

        // The acknowledgement's destination is the packet's origin, so the
        // two are switched
        AcknowledgementPacket acknowledgementPacket = new AcknowledgementPacket(
                packet.getSequenceIdentifier(),
                packet.getOrigin(),
                getHostInstance()
        );

        // Instantiate the IoPacket with the proper next-hop lookup
        IoController.IoPacket ioPacket = new NetworkPacket(acknowledgementPacket) {

            @Override
            public Device getDevice() {
                return getBestLinkNextHopDevice(packet.getOrigin(), null);
            }

            @Override
            public void handleOnWritten() {
                // Do nothing
            }

            @Override
            public void handleOnWriteFailure(UlxError error) {
                // Could not acknowledge?
            }
        };

        // Queue for dispatch
        getIoController().add(ioPacket);
    }

    private Device getBestLinkNextHopDevice(Instance destination, Device previousHop) {
        Link link = getRoutingTable().getBestLink(destination, previousHop);

        if (link == null) {
            return null;
        }

        return link.getNextHop();
    }

    @Override
    public void onPacketReceived(IoController controller, InputStream inputStream, Packet packet) {
        Log.i(getClass().getCanonicalName(), "ULX packet received");

        switch (packet.getType()) {

            case HANDSHAKE:
                handleHandshakePacketReceived(inputStream, (HandshakePacket) packet);
                break;

            case UPDATE:
                handleUpdatePacketReceived(inputStream, (UpdatePacket) packet);
                break;

            case DATA:
                handleDataPacketReceived((DataPacket) packet);
                break;

            case ACKNOWLEDGEMENT:
                handleAcknowledgementPacketReceived((AcknowledgementPacket) packet);
                break;
        }
    }

    @Override
    public void onPacketWritten(IoController controller, OutputStream outputStream, IoController.IoPacket ioPacket) {
        Log.i(getClass().getCanonicalName(), "ULX packet written");
        ((NetworkPacket)ioPacket).handleOnWritten();
    }

    @Override
    public void onPacketWriteFailure(IoController controller, OutputStream outputStream, IoController.IoPacket ioPacket, UlxError error) {
        Log.e(getClass().getCanonicalName(), "ULX packet could not be written");
        ((NetworkPacket)ioPacket).handleOnWriteFailure(error);
    }

    /**
     * Handles the event of a {@link HandshakePacket} being received. This will
     * correspond to the packet being processed as a new registry in the {@link
     * RoutingTable}. The {@link Device} that originated the packet is
     * interpreted from the given {@link InputStream}, which should have some
     * form of correspondence in the {@link RoutingTable}. This means that the
     * {@link Device} should already be known, which makes sense since the
     * stream is active (e.g. otherwise a packet could not be received).
     * @param inputStream The {@link InputStream} that received the packet.
     * @param packet The {@link HandshakePacket} that was received.
     */
    private void handleHandshakePacketReceived(InputStream inputStream, HandshakePacket packet) {

        // Get the device corresponding to the InputStream, since that's the
        // originator that will be mapped to the instance
        Device device = getRoutingTable().getDevice(inputStream.getIdentifier());

        // If the device has not been registered, this is most likely a
        // programming error, since we're already communicating with it
        if (device == null) {
            handleHandshakePacketDeviceNotFound(inputStream, packet);
            return;
        }

        // Create a link to the negotiated Instance, having the device has
        // next hop. Since this is a handshake packet, the hop count is always
        // 1, and the device is in direct link
        getRoutingTable().registerOrUpdate(
                device,
                packet.getOriginator(),
                1,
                false
        );
    }

    /**
     * Processes an {@link UpdatePacket} being received, which corresponds to
     * a network update. The packet was received from the given {@link
     * InputStream}. This method will register a new instance being notified
     * by a remote device or update an existing entry on the {@link RoutingTable}.
     * This update may or may not affect the routing procedures that will be in
     * place at the moment, since that only happens if the given update is
     * better.
     * @param inputStream The source {@link InputStream} for the update.
     * @param packet The {@link UpdatePacket} that was received.
     */
    private void handleUpdatePacketReceived(InputStream inputStream, UpdatePacket packet) {

        Device device = getRoutingTable().getDevice(inputStream.getIdentifier());

        // The device should be recognized, since we're talking with it
        if (device == null) {
            Log.e(getClass().getCanonicalName(), "ULX received an update " +
                    "packet from a device that it doesn't recognize. This is " +
                    "unexpected, an shouldn't happen. Is likely that this " +
                    "represents an event that is being improperly indicated or " +
                    "that a corruption occurred in the registry.");
            return;
        }

        // This should correspond to an update, since we haven't proceeded with
        // new registrations above
        getRoutingTable().registerOrUpdate(
                device,
                packet.getInstance(),
                packet.getHopCount(),
                packet.isInternetReachable()
        );
    }

    /**
     * Handles the situation of an {@link InputStream} receiving a {@link
     * HandshakePacket} for a {@link Device} that it does not recognize. The
     * current version simply logs an error, but this exception should be
     * properly managed by future versions.
     * @param inputStream The {@link InputStream} that originated the packet.
     * @param packet The {@link HandshakePacket}.
     */
    private void handleHandshakePacketDeviceNotFound(InputStream inputStream, HandshakePacket packet) {

        // I'm logging an error because the current implementation still has
        // poor recovery policies and not a lot of error handling. Future
        // versions should review this and decide something else.
        Log.e(getClass().getCanonicalName(), "ULX an handshake was received " +
                "for an identifiable device, and that's unexpected. The device " +
                "should be known, since the streams have already been open.");
    }

    /**
     *
     * @param packet The {@link DataPacket} that was received.
     */
    private void handleDataPacketReceived(DataPacket packet) {

        // If the host device is the destination, then the packet is meant for
        // this device, in which case it's forwarded.
        if (packet.getDestination().equals(getHostInstance())) {
            notifyOnReceived(packet.getData(), packet.getOrigin());
            acknowledge(packet);
        }

        // TODO if the packet is not meant for us, the mesh kicks in
        else {
            Log.e(getClass().getCanonicalName(), "ULX is dropping a mesh packet");
        }
    }

    /**
     * Handles an {@link AcknowledgementPacket} being received, which consists
     * of propagating a {@link Delegate} notification for {@link
     * Delegate#onAcknowledgement(NetworkController, Ticket)}.
     * @param packet The {@link AcknowledgementPacket} that was received.
     */
    private void handleAcknowledgementPacketReceived(AcknowledgementPacket packet) {
        Ticket ticket = new Ticket(packet.getSequenceIdentifier(), packet.getOrigin());
        notifyOnAcknowledgement(ticket);
    }

    /**
     * Propagates a notification to the {@link Delegate} giving indication that
     * a {@link DataPacket} has been received. This means that the packet is
     * meant for the host device, and thus will not be redirected to any other
     * devices.
     * @param data The data that was received.
     * @param origin The {@link Instance} that originated.
     */
    private void notifyOnReceived(byte[] data, Instance origin) {
        Delegate delegate = getDelegate();
        if (delegate != null) {
            delegate.onReceived(this, data, origin);
        }
    }

    /**
     * Propagates a notification to the {@link Delegate} giving indication that
     * a {@link DataPacket} was sent, meaning that it was written to the output
     * streams. This is indicated by passing a {@link Ticket}, which will
     * correspond to the operation of the packet having been sent.
     * @param ticket The {@link Ticket} for the event.
     */
    private void notifyOnSent(Ticket ticket) {
        Delegate delegate = getDelegate();
        if (delegate != null) {
            delegate.onSent(this, ticket);
        }
    }

    /**
     * Propagates a notification to the {@link Delegate} giving indication that
     * a {@link DataPacket} could not be sent due to some error. This indication
     * is given through a {@link Ticket} that corresponds to the operation of
     * the packet having been sent.
     * @param ticket The {@link Ticket} for the packet that could not be sent.
     * @param error An error ({@link UlxError}) describing the cause for failure.
     */
    private void notifyOnSendFailure(Ticket ticket, UlxError error) {
        Delegate delegate = getDelegate();
        if (delegate != null) {
            delegate.onSendFailure(this, ticket, error);
        }
    }

    /**
     * Propagates a notification to the {@link Delegate} giving indication that
     * an {@link AcknowledgementPacket} was received, acknowledging a packet
     * that was previously sent. The event of sending that packet corresponds
     * to the given {@link Ticket}.
     * @param ticket The {@link Ticket} for the acknowledged event.
     */
    private void notifyOnAcknowledgement(Ticket ticket) {
        Delegate delegate = getDelegate();
        if (delegate != null) {
            delegate.onAcknowledgement(this, ticket);
        }
    }

    @Override
    public void onInstanceFound(RoutingTable routingTable, Instance instance) {
        notifyOnInstanceFound(instance);
    }

    @Override
    public void onInstanceLost(RoutingTable routingTable, Instance instance, UlxError error) {
        notifyOnInstanceLost(instance, error);
    }

    @Override
    public void onLinkUpdate(RoutingTable routingTable, Instance destination, Device splitHorizon, Link link) {

        int hopCount;
        boolean isReachable;
        boolean isInternetReachable;

        // Some information related with the update depends on the type of
        // event; if the link is null, then the instance is lost.
        if (link == null) {
            hopCount = RoutingTable.HOP_COUNT_INFINITY;
            isReachable = false;
            isInternetReachable = false;
        } else {
            hopCount = Math.min(link.getHopCount() + 1, RoutingTable.HOP_COUNT_INFINITY);
            isReachable = true;
            isInternetReachable = link.isInternetReachable();
        }

        // Don't propagate events that reach the maximum number of hops
        if (hopCount >= MAXIMUM_HOP_COUNT) {
            Log.i(getClass().getCanonicalName(), String.format("ULX is not propagating a multi-hop update; the hop count was exceeded (%d/%d).", hopCount, MAXIMUM_HOP_COUNT));
            return;
        }

        Log.i(getClass().getCanonicalName(), String.format("ULX is propagating an update packet for %s (%d/%d)", destination.getStringIdentifier(), hopCount, MAXIMUM_HOP_COUNT));

        // This version propagates all updates, but that might turn out to be
        // too much. Future versions may condense several updates together, so
        // that the network is not so flooded.
        UpdatePacket updatePacket = new UpdatePacket(
                getSequenceGenerator().generate(),
                destination,
                hopCount,
                isReachable,
                isInternetReachable
        );

        // Schedule the update packet to be sent
        scheduleUpdatePacket(updatePacket, splitHorizon);
    }

    /**
     * Propagates the given {@link UpdatePacket} to all {@link Device}s
     * connected in line of sight (LoS).
     * @param updatePacket The packet to propagate.
     * @param splitHorizon A device to ignore, which should be the originator
     *                     for the update.
     */
    private void scheduleUpdatePacket(UpdatePacket updatePacket, Device splitHorizon) {
        Log.i(getClass().getCanonicalName(), "ULX mesh scheduling an update packet");

        for (Device device : getRoutingTable().getDeviceList()) {

            // Ignore the split horizon
            if (device.equals(splitHorizon)) {
                continue;
            }

            // The update packet is propagated to all devices in direct link
            // (except the split horizon)
            getIoController().add(new NetworkPacket(updatePacket) {

                @Override
                public void handleOnWritten() {
                    Log.e(getClass().getCanonicalName(), "ULX update packet was written");
                }

                @Override
                public void handleOnWriteFailure(UlxError error) {
                    Log.e(getClass().getCanonicalName(), "ULX update packet was NOT written");
                }

                @Override
                public Device getDevice() {
                    return device;
                }
            });
        }
    }

    /**
     * Propagates a {@link Delegate} notification giving indication that an
     * {@link Instance} was found.
     * @param instance The {@link Instance} that was found.
     */
    private void notifyOnInstanceFound(Instance instance) {
        Delegate delegate = getDelegate();
        if (delegate != null) {
            delegate.onInstanceFound(this, instance);
        }
    }

    /**
     * Propagates a {@link Delegate} notification giving indication that an
     * {@link Instance} is no longer reachable.
     * @param instance The {@link Instance} that was lost.
     * @param error An error, describing the failure conditions.
     */
    private void notifyOnInstanceLost(Instance instance, UlxError error) {
        Delegate delegate = getDelegate();
        if (delegate != null) {
            delegate.onInstanceLost(this, instance, error);
        }
    }
}
