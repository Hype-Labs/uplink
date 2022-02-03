package com.uplink.ulx.bridge.network.controller;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.uplink.ulx.UlxError;
import com.uplink.ulx.bridge.SequenceGenerator;
import com.uplink.ulx.bridge.io.controller.IoController;
import com.uplink.ulx.bridge.io.model.AcknowledgementPacket;
import com.uplink.ulx.bridge.io.model.DataPacket;
import com.uplink.ulx.bridge.io.model.HandshakePacket;
import com.uplink.ulx.bridge.io.model.InternetPacket;
import com.uplink.ulx.bridge.io.model.InternetResponsePacket;
import com.uplink.ulx.bridge.io.model.Packet;
import com.uplink.ulx.bridge.io.model.UpdatePacket;
import com.uplink.ulx.bridge.network.model.Link;
import com.uplink.ulx.bridge.network.model.RoutingTable;
import com.uplink.ulx.bridge.network.model.Ticket;
import com.uplink.ulx.drivers.model.Device;
import com.uplink.ulx.drivers.model.InputStream;
import com.uplink.ulx.drivers.model.OutputStream;
import com.uplink.ulx.model.Instance;
import com.uplink.ulx.threading.Dispatch;
import com.uplink.ulx.threading.ExecutorPool;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;

/**
 * The {@link NetworkController} is the module that manages network-related
 * stuff, such as routing tables, reachability, network updates, and so on.
 * This class offers a low-level API for sending content ({@link
 * #send(byte[], Instance)}) and negotiating ({@link #negotiate(Device)}).
 */
public class NetworkController implements IoController.Delegate, RoutingTable.Delegate {

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
         * @param instance The {@link Instance} that was found.
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
         * @param data The data that was received.
         * @param origin The {@link Instance} that sent the data.
         */
        void onReceived(NetworkController networkController, byte[] data, Instance origin);

        /**
         * This {@link Delegate} notification is called when the packet
         * corresponding to the given {@link Ticket} is known to have been sent.
         * This corresponds to the packet having been written to the streams,
         * and possibly not yet have left the device.
         *
         * @param networkController The {@link NetworkController}.
         * @param ticket The {@link Ticket} for the packet that was
         *               sent.
         */
        void onSent(NetworkController networkController, Ticket ticket);

        /**
         * This {@link Delegate} notification is called when a {@link DataPacket}
         * could not be sent. The implementation will not attempt to resend the
         * packet, and no recovery is in progress.
         *
         * @param networkController The {@link NetworkController}.
         * @param ticket The {@link Ticket} corresponding to the packet that
         *               could not be sent.
         * @param error  An error, describing a probable cause for the failure.
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

    /**
     * This is a delegate that listens to the result of Internet requests.
     */
    public interface InternetRequestDelegate {

        /**
         * This {@link Delegate} notification is called when a response is
         * received from the server, corresponding to a previous Internet
         * request. The callback is called only if the request was made by
         * the host device, which means that the mesh responses are not
         * propagated with this delegate call. This is, however, used
         * internally, for the purposes of listening to the result of mesh
         * Internet requests.
         * @param networkController The {@link NetworkController}.
         * @param code The HTTP response code.
         * @param message The HTTP response body.
         */
        void onInternetResponse(NetworkController networkController, int code, String message);

        /**
         * This {@link Delegate} notification gives indicating that an Internet
         * request failed to complete. This means that the request could not be
         * performed locally, but also that the host device failed to propagate
         * the request on the network.
         * @param networkController The {@link NetworkController}.
         * @param sequence A sequence number for the original packet.
         */
        void onInternetRequestFailure(NetworkController networkController, int sequence);
    }

    /**
     * A {@link NetworkPacket} is an abstraction of the {@link
     * IoController.IoPacket} model that forces the implementation of specific
     * method callbacks for handling write success and failures. This will serve
     * the purpose of the network layer reacting to those events on given
     * packets.
     */
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

    private final WeakReference<Context> context;

    private IoController ioController;
    private RoutingTable routingTable;
    private final Instance hostInstance;

    private WeakReference<Delegate> delegate;
    private WeakReference<InternetRequestDelegate> internetRequestDelegate;

    // This identifier is used to give a sequence number to packets as they are
    // being dispatched. The sequence begins in 0 and resets at 65535.
    private SequenceGenerator sequenceGenerator;

    /**
     * Constructor.
     */
    public NetworkController(Instance hostInstance, Context context) {

        Objects.requireNonNull(hostInstance);
        Objects.requireNonNull(context);

        this.ioController = null;
        this.routingTable = null;
        this.hostInstance = hostInstance;

        this.delegate = null;
        this.internetRequestDelegate = null;

        this.sequenceGenerator = null;

        this.context = new WeakReference<>(context);
    }

    /**
     * Returns the Android environment {@link Context} that is used by the
     * implementation to check for Internet connectivity.
     * @return The Android environment {@link Context}.
     */
    private Context getContext() {
        return this.context.get();
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

    private InternetRequestDelegate getInternetRequestDelegate() {
        return this.internetRequestDelegate != null ? this.internetRequestDelegate.get() : null;
    }

    public final void setInternetRequestDelegate(InternetRequestDelegate internetRequestDelegate) {
        this.internetRequestDelegate = new WeakReference<>(internetRequestDelegate);
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

        // TODO the device must be registered before the thread context change
        //      because it creates a race condition with incoming negotiation
        //      packets. I'm not a fan of this approach, and I think that the
        //      thread management should be reviewed overall.
        // Register the device
        getRoutingTable().register(device);

        ExecutorPool.getInternetExecutor().execute(() -> {

            // This call may make requests to the Internet, which is why we're
            // using a different thread
            int internetHopCount = getInternetHopCount();

            // Increment the hop count, since it's being propagated as an HS
            if (internetHopCount < RoutingTable.HOP_COUNT_INFINITY) {
                internetHopCount += 1;
            }

            final int iHops = internetHopCount;

            Log.e(getClass().getCanonicalName(), String.format("ULX handshake packet with i-hops %d", iHops));

            // Switch back to the main thread
            Dispatch.post(() -> {

                // Create the handshake packet and send
                HandshakePacket packet = new HandshakePacket(
                        getSequenceGenerator().generate(),
                        getHostInstance(),
                        iHops
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

                // Ideally, the table would be dumped with the update packet
                // itself, since this version takes up a lot more space.
                dumpRoutingTable(device);
            });
        });
    }

    /**
     * Pings google.com on multiple interfaces in order to check if that server
     * is reachable. This will indicate that the host device is directly
     * connected to the Internet. Future versions should avoid pinging Google,
     * but rather a server within the application's ecosystem or some other
     * method.
     * @param context The Android environment context.
     * @return Whether the device is connected to the Internet.
     */
    private static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

        if (activeNetwork != null && activeNetwork.isConnected()) {
            try {
                URL url = new URL("http://www.google.com/");
                HttpURLConnection connection = (HttpURLConnection)url.openConnection();
                connection.setRequestProperty("User-Agent", "test");
                connection.setRequestProperty("Connection", "close");
                connection.setConnectTimeout(5000);
                connection.connect();
                connection.getResponseCode();

                // Any status code is OK
                return true;

            } catch (IOException e) {
                Log.e(NetworkController.class.getCanonicalName(), "ULX Internet not directly available");
                return false;
            }
        }

        return false;
    }

    /**
     * Returns the number of hops that takes for this device to reach the
     * Internet, relying on either a direct connection or the mesh. If a direct
     * connection exists, this method returns {@code 0} (zero). If it cannot
     * connect directly, then it checks for the availability of a mesh link
     * that is connected; if one exists, the number of hops for the best scored
     * link will be returned, otherwise {@link RoutingTable#HOP_COUNT_INFINITY}
     * is returned instead.
     * @return The number of hops that it takes for the device to reach the
     * Internet.
     */
    private int getInternetHopCount() {

        if (isNetworkAvailable(getContext())) {
            return 0;
        }

        Link link = getRoutingTable().getBestInternetLink(null);

        if (link == null) {
            return RoutingTable.HOP_COUNT_INFINITY;
        }

        return link.getHopCount();
    }

    /**
     * This method dumps the entire routing table to the given device. Each
     * known link is sent as a separate packet, which is not ideal. This works
     * as a quick workaround to make it simple to dump the table. Each link
     * may constitute an update on the destination device, which in turn will
     * forward the updates, if it sees fit.
     * @param device The destination device.
     */
    private void dumpRoutingTable(Device device) {
        Log.i(getClass().getCanonicalName(), String.format("ULX-M is dumping the routing table to device %s", device.getIdentifier()));

        for (Instance instance : getRoutingTable().getInstances()) {

            // We propagate the best link that doesn't rely on the given device
            // as split horizon
            Link link = getRoutingTable().getBestLink(instance, device);

            if (link == null) {
                Log.i(getClass().getCanonicalName(), String.format("ULX no known path to %s with %s as split horizon", instance.getStringIdentifier(), device.getIdentifier()));
                continue;
            }

            // The host instance should never be on the table
            assert !link.getDestination().equals(getHostInstance());

            // No link should have "device" as the next hop
            assert !link.getNextHop().getIdentifier().equals(device.getIdentifier());

            int hopCount = Math.min(link.getHopCount() + 1, RoutingTable.HOP_COUNT_INFINITY);
            int internetHopCount = Math.min(link.getInternetHopCount() + 1, RoutingTable.HOP_COUNT_INFINITY);

            // Don't propagate events that reach the maximum number of hops
            if (hopCount >= RoutingTable.MAXIMUM_HOP_COUNT) {
                Log.i(getClass().getCanonicalName(), String.format("ULX-M is not propagating a link update, the hop count was exceeded (%d/%d) for link %s", hopCount, RoutingTable.MAXIMUM_HOP_COUNT, link == null ? "(null)" : link.toString()));
                continue;
            }

            Log.i(getClass().getCanonicalName(), String.format("ULX-M dumping link %s to device %s", link.toString(), device.getIdentifier()));

            UpdatePacket updatePacket = new UpdatePacket(
                    getSequenceGenerator().generate(),
                    link.getDestination(),
                    hopCount,
                    true,
                    internetHopCount
            );

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

        /*
        for (Link link : getRoutingTable().getLinks()) {

            // Skip the host instance (Should this even be in the table?)
            if (link.getDestination().equals(getHostInstance())) {
                Log.i(getClass().getCanonicalName(), String.format("ULX-M not dumping link %s to device %s, the destination is host %s", link.toString(), device.getIdentifier(), getHostInstance().getStringIdentifier()));
                continue;
            }

            // Don't propagate updates that have the next hop as split horizon
            if (link.getNextHop().equals(device)) {
                Log.i(getClass().getCanonicalName(), String.format("ULX-M not dumping link %s to device %s, the device is the split horizon", link.toString(), device.getIdentifier()));
                continue;
            }

            int hopCount = Math.min(link.getHopCount() + 1, RoutingTable.HOP_COUNT_INFINITY);
            int internetHopCount = Math.min(link.getInternetHopCount() + 1, RoutingTable.HOP_COUNT_INFINITY);

            // Don't propagate events that reach the maximum number of hops
            if (hopCount >= RoutingTable.MAXIMUM_HOP_COUNT) {
                Log.i(getClass().getCanonicalName(), String.format("ULX-M is not propagating a link update, the hop count was exceeded (%d/%d) for link %s", hopCount, RoutingTable.MAXIMUM_HOP_COUNT, link == null ? "(null)" : link.toString()));
                continue;
            }

            Log.i(getClass().getCanonicalName(), String.format("ULX-M dumping link %s to device %s", link.toString(), device.getIdentifier()));

            UpdatePacket updatePacket = new UpdatePacket(
                    getSequenceGenerator().generate(),
                    link.getDestination(),
                    hopCount,
                    true,
                    internetHopCount
            );

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
         */

        Log.i(getClass().getCanonicalName(), String.format("ULX-M is done dumping the routing table to device %s", device.getIdentifier()));
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
                Device device = getBestLinkNextHopDevice(destination, null);
                assert device != null;
                return device;
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
                getHostInstance(),
                packet.getOrigin()
        );

        // Instantiate the IoPacket with the proper next-hop lookup
        IoController.IoPacket ioPacket = new NetworkPacket(acknowledgementPacket) {

            @Override
            public Device getDevice() {
                return getBestLinkNextHopDevice(acknowledgementPacket.getDestination(), null);
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

    private Device getBestInternetLinkNextHopDevice(Device previousHop) {
        Link link = getRoutingTable().getBestInternetLink(previousHop);
        return link != null ? link.getNextHop() : null;
    }

    @Override
    public void onPacketReceived(IoController controller, InputStream inputStream, Packet packet) {
        Log.i(getClass().getCanonicalName(), String.format("ULX-M packet received from input stream %s: %s", inputStream.getIdentifier(), packet.toString()));

        // Compute the device, according to the InputStream given
        Device device = getRoutingTable().getDevice(inputStream.getIdentifier());

        switch (packet.getType()) {

            case HANDSHAKE:
                handleHandshakePacketReceived(device, (HandshakePacket) packet);
                break;

            case UPDATE:
                handleUpdatePacketReceived(device, (UpdatePacket) packet);
                break;

            case DATA:
                handleDataPacketReceived(device, (DataPacket) packet);
                break;

            case ACKNOWLEDGEMENT:
                handleAcknowledgementPacketReceived(device, (AcknowledgementPacket) packet);
                break;

            case INTERNET:
                handleInternetPacketReceived(device, (InternetPacket) packet);
                break;

            case INTERNET_RESPONSE:
                handleInternetResponsePacketReceived(device, (InternetResponsePacket) packet);
                break;
        }
    }

    @Override
    public void onPacketWritten(IoController controller, OutputStream outputStream, IoController.IoPacket ioPacket) {
        Log.i(getClass().getCanonicalName(), String.format("ULX packet %s written to %s", ioPacket.toString(), outputStream.getIdentifier()));
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
     * @param device The {@link Device} that received the packet.
     * @param packet The {@link HandshakePacket} that was received.
     */
    private void handleHandshakePacketReceived(Device device, HandshakePacket packet) {

        // If the device has not been registered, this is most likely a
        // programming error, since we're already communicating with it
        if (device == null) {
            handleHandshakePacketDeviceNotFound(null, packet);
            return;
        }

        Log.i(getClass().getCanonicalName(), String.format("ULX-M device %s is instance %s", device.getIdentifier(), packet.getOriginator().getStringIdentifier()));

        // Create a link to the negotiated Instance, having the device has
        // next hop. Since this is a handshake packet, the hop count is always
        // 1, and the device is in direct link
        getRoutingTable().registerOrUpdate(
                device,
                packet.getOriginator(),
                1,
                packet.getInternetHops()
        );
    }

    public void removeDevice(Device device) {

        // Clearing the device from the routing table could result in several
        // lost instances
        getRoutingTable().unregister(device);

        // TODO there might still be pending I/O with this device, which must
        //      be cleared.
    }

    /**
     * Processes an {@link UpdatePacket} being received, which corresponds to
     * a network update. The packet was received from the given {@link
     * InputStream}. This method will register a new instance being notified
     * by a remote device or update an existing entry on the {@link RoutingTable}.
     * This update may or may not affect the routing procedures that will be in
     * place at the moment, since that only happens if the given update is
     * better.
     * @param device The source {@link Device} for the update.
     * @param packet The {@link UpdatePacket} that was received.
     */
    private void handleUpdatePacketReceived(Device device, UpdatePacket packet) {

        // The device should be recognized, since we're talking with it
        if (device == null) {
            Log.e(getClass().getCanonicalName(), "ULX received an update " +
                    "packet from a device that it doesn't recognize. This is " +
                    "unexpected, an shouldn't happen. Is likely that this " +
                    "represents an event that is being improperly indicated or " +
                    "that a corruption occurred in the registry.");
            return;
        }

        // We're ignoring update packets that refer to the host instance itself.
        // Doing this check here is OK, but this would better be done at the
        // originator, since that would save one additional packet.
        if (packet.getInstance().equals(getHostInstance())) {
            Log.i(getClass().getCanonicalName(), "ULX-M update packet being " +
                    "ignored because it refers to the host instance");
            return;
        }

        // This should correspond to an update, since we haven't proceeded with
        // new registrations above
        getRoutingTable().registerOrUpdate(
                device,
                packet.getInstance(),
                packet.getHopCount(),
                packet.getInternetHopCount()
        );
    }

    /**
     * Handles the situation of an {@link InputStream} receiving a {@link
     * HandshakePacket} for a {@link Device} that it does not recognize. The
     * current version simply logs an error, but this exception should be
     * properly managed by future versions.
     * @param device The {@link Device} that originated the packet.
     * @param packet The {@link HandshakePacket}.
     */
    private void handleHandshakePacketDeviceNotFound(Device device, HandshakePacket packet) {

        // I'm logging an error because the current implementation still has
        // poor recovery policies and not a lot of error handling. Future
        // versions should review this and decide something else.
        Log.e(getClass().getCanonicalName(), "ULX an handshake was received " +
                "for an identifiable device, and that's unexpected. The device " +
                "should be known, since the streams have already been open.");
    }

    /**
     * This method is called when a {@link DataPacket} is received. It checks
     * the packet's destination and decides whether the packet is meant for the
     * host instance or is meant to be redirected.
     * @param device The {@link Device} that served as previous hop.
     * @param packet The {@link DataPacket} that was received.
     */
    private void handleDataPacketReceived(Device device, DataPacket packet) {

        // If the host device is the destination, then the packet is meant for
        // this device, in which case it's forwarded.
        if (packet.getDestination().equals(getHostInstance())) {
            notifyOnReceived(packet.getData(), packet.getOrigin());
            acknowledge(packet);
        } else {

            if (device == null) {
                Log.e(getClass().getCanonicalName(), "ULX could not find a device with an input device");
                return;
            }

            forwardMeshPacket(packet, device);
        }
    }

    private void handleInternetPacketReceived(Device device, InternetPacket packet) {

        // I'm not a fan of the pattern of passing the InternetRequestDelegate
        // through the stack, but this will work as a work around for now.
        makeInternetRequest(packet.getOriginator(), packet.getSequenceIdentifier(), packet.getUrl(), packet.getData(), packet.getTest(), packet.getHopCount() + 1, new InternetRequestDelegate() {

            @Override
            public void onInternetResponse(NetworkController networkController, int code, String message) {
                Log.i(getClass().getCanonicalName(), "ULX is redirecting an Internet response packet");

                InternetResponsePacket responsePacket = new InternetResponsePacket(
                        getSequenceGenerator().generate(),
                        code,
                        message,
                        packet.getOriginator()
                );

                // We're here because we received an Internet request and
                // forwarded the packet to the server. This is the response
                // from the server, which means that we need to relay it
                // through the mesh, back to the originator.
                IoController.IoPacket ioPacket = new NetworkPacket(responsePacket) {

                    @Override
                    public void handleOnWritten() {
                        // Ok
                        Log.i(getClass().getCanonicalName(), "ULX relayed an Internet packet");
                    }

                    @Override
                    public void handleOnWriteFailure(UlxError error) {
                        // ??
                        Log.e(getClass().getCanonicalName(), "ULX could not relay an Internet packet");
                    }

                    @Override
                    public Device getDevice() {
                        // We're simply relaying to the device that forwarded
                        // us the packet in the first place. This is not ideal
                        // or correct because there might have been changes to
                        // the network. However, the current implementation is
                        // apparently not capable of forming a full mesh, and
                        // thus sometimes the proxy can't figure out a path to
                        // the originator. This should solve the problem for
                        // the POC, but the topology must be fixed later. e.g.:
                        // if the originator finds a path to the proxy, then
                        // the proxy must also find a path to the originator.
                        return device;
                    }
                };

                // Queue for dispatch
                getIoController().add(ioPacket);
            }

            @Override
            public void onInternetRequestFailure(NetworkController networkController, int sequence) {
                // TODO Should we send back a failure notification?
                Log.e(getClass().getCanonicalName(), "ULX failed to relay an Internet packet");
            }
        });
    }

    /**
     * Propagates a delegate notification for a {@link InternetResponsePacket}
     * being received. This happens if the packet is meant for the host device,
     * otherwise it will be propagated on the mesh.
     * @param device The {@link Device} that originated the packet.
     * @param packet The {@link InternetResponsePacket} received.
     */
    private void handleInternetResponsePacketReceived(Device device, InternetResponsePacket packet) {

        // Packets that are meant for the host device raise on the stack
        if (getHostInstance().equals(packet.getOriginator())) {
            notifyOnInternetResponse(getInternetRequestDelegate(), packet.getCode(), packet.getData());
        }

        // Packets for other instances are forwarded on the network
        else {
            forwardInternetResponsePacket(packet, device);
        }
    }

    private void forwardMeshPacket(DataPacket packet, Device splitHorizon) {
        forwardMeshPacket(packet, packet.getDestination(), splitHorizon);
    }

    private void forwardMeshPacket(AcknowledgementPacket packet, Device splitHorizon) {
        forwardMeshPacket(packet, packet.getDestination(), splitHorizon);
    }

    private void forwardMeshPacket(Packet packet, Instance destination, Device splitHorizon) {
        Log.i(getClass().getCanonicalName(), String.format("ULX is forwarding a mesh packet [%d] to %s", packet.getSequenceIdentifier(), destination.getStringIdentifier()));

        // Instantiate the IoPacket with the proper next-hop lookup
        IoController.IoPacket ioPacket = new NetworkPacket(packet) {

            @Override
            public Device getDevice() {
                Device device = getBestLinkNextHopDevice(destination, splitHorizon);
                assert device != null;
                Log.i(NetworkController.this.getClass().getCanonicalName(), String.format("ULX best link next hop is %s", device.getIdentifier()));
                return device;
            }

            @Override
            public void handleOnWritten() {
                Log.i(NetworkController.this.getClass().getCanonicalName(), "ULX-M packet relayed successfully");
                // Do nothing; we're currently not tracking mesh relays
            }

            @Override
            public void handleOnWriteFailure(UlxError error) {
                Log.e(NetworkController.this.getClass().getCanonicalName(), "ULX-M packet relay failed");
                // Do nothing; we're currently not tracking mesh relays
            }
        };

        // Queue for dispatch
        getIoController().add(ioPacket);
    }

    private void forwardInternetResponsePacket(InternetResponsePacket packet, Device splitHorizon) {
        Log.i(getClass().getCanonicalName(), "ULX is forwarding an Internet response packet");

        // Instantiate the IoPacket with the proper next-hop lookup
        IoController.IoPacket ioPacket = new NetworkPacket(packet) {

            @Override
            public Device getDevice() {
                Device device = getBestLinkNextHopDevice(packet.getOriginator(), splitHorizon);
                assert device != null;
                return device;
            }

            @Override
            public void handleOnWritten() {
                Log.i(NetworkController.this.getClass().getCanonicalName(), "ULX Internet response packet relayed successfully");
                // Do nothing; we're currently not tracking mesh relays
            }

            @Override
            public void handleOnWriteFailure(UlxError error) {
                Log.e(NetworkController.this.getClass().getCanonicalName(), "ULX Internet response packet relay failed");
                // Do nothing; we're currently not tracking mesh relays
            }
        };

        // Queue for dispatch
        getIoController().add(ioPacket);
    }

    /**
     * Handles an {@link AcknowledgementPacket} being received, which consists
     * of propagating a {@link Delegate} notification for {@link
     * Delegate#onAcknowledgement(NetworkController, Ticket)}.
     * @param device The previous hop {@link Device}.
     * @param packet The {@link AcknowledgementPacket} that was received.
     */
    private void handleAcknowledgementPacketReceived(Device device, AcknowledgementPacket packet) {

        // If the host device is the destination, then the packet is meant for
        // this device, in which case it's forwarded.
        if (packet.getDestination().equals(getHostInstance())) {
            Ticket ticket = new Ticket(packet.getSequenceIdentifier(), packet.getOrigin());
            notifyOnAcknowledgement(ticket);
        } else {

            if (device == null) {
                Log.e(getClass().getCanonicalName(), "ULX could not find a device with an input device");
                return;
            }

            forwardMeshPacket(packet, device);
        }
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
    public void onLinkUpdate(RoutingTable routingTable, Link link) {
        update(link);
    }

    private void update(Link link) {

        getRoutingTable().log();

        assert link != null;

        int hopCount = Math.min(link.getHopCount() + 1, RoutingTable.HOP_COUNT_INFINITY);
        int internetHopCount = Math.min(link.getInternetHopCount() + 1, RoutingTable.HOP_COUNT_INFINITY);

        // Don't propagate events that reach the maximum number of hops
        if (hopCount >= RoutingTable.MAXIMUM_HOP_COUNT) {
            Log.i(getClass().getCanonicalName(), String.format("ULX-M is not propagating a link update, the hop count was exceeded (%d/%d) for link %s", hopCount, RoutingTable.MAXIMUM_HOP_COUNT, link == null ? "(null)" : link.toString()));
            return;
        }

        Log.i(getClass().getCanonicalName(), String.format("ULX-M is propagating link update %s", link.toString()));

        // This version propagates all updates, but that might turn out to be
        // too much. Future versions may condense several updates together, so
        // that the network is not so flooded.
        UpdatePacket updatePacket = new UpdatePacket(
                getSequenceGenerator().generate(),
                link.getDestination(),
                hopCount,
                true,   // Because the link is never null
                internetHopCount
        );

        // Schedule the update packet to be sent
        scheduleUpdatePacket(updatePacket, link.getNextHop());
    }

    /**
     * Propagates the given {@link UpdatePacket} to all {@link Device}s
     * connected in line of sight (LoS).
     * @param updatePacket The packet to propagate.
     * @param splitHorizon A device to ignore, which should be the originator
     *                     for the update.
     */
    private void scheduleUpdatePacket(UpdatePacket updatePacket, Device splitHorizon) {

        assert splitHorizon != null;

        for (Device device : getRoutingTable().getDeviceList()) {
            Log.i(getClass().getCanonicalName(), String.format("ULX-M scheduling update packet %s to %s with split horizon %s", updatePacket.toString(), device.getIdentifier(), splitHorizon == null ? "(null)" : splitHorizon.getIdentifier()));

            // Ignore the split horizon
            if (device.getIdentifier().equals(splitHorizon.getIdentifier())) {
                Log.i(getClass().getCanonicalName(), String.format("ULX-M not propagating update packet %s to device %s because the device is the split horizon", updatePacket.toString(), splitHorizon == null ? "(null)" : splitHorizon.getIdentifier()));
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

    /**
     * Makes an Internet request by sending the given {@link JSONObject} to the
     * best Internet link available.
     * @param url The server URL.
     * @param jsonObject The JSON object to send.
     * @param test The test identifier.
     * @return A sequence identifier for the request.
     */
    public int sendInternet(URL url, JSONObject jsonObject, int test) {
        return makeInternetRequest(getHostInstance(), getSequenceGenerator().generate(), url, jsonObject.toString(), test, 0, getInternetRequestDelegate());
    }

    /**
     * This method attempts to send an {@code application/json} {@code POST}
     * request to the given {@link URL} with the given {@code data}. If the
     * request succeeds, then that is the response that is returned to the
     * caller. If not, the implementation will fallback to the mesh, and relay
     * an {@link InternetPacket} over the network.
     * @param originator The {@link Instance} that originated the request.
     * @param url The destination {@link URL}.
     * @param data The data to send to the server.
     * @param test The test ID.
     * @param hopCount The number of hops that the request has travelled so far.
     * @param internetRequestDelegate The delegate to get the response back from
     *                                the server.
     */
    private int makeInternetRequest(Instance originator, int sequenceIdentifier, URL url, String data, int test, int hopCount, InternetRequestDelegate internetRequestDelegate) {

        // Don't run networking stuff on the main thread
        ExecutorPool.getInternetExecutor().execute(() -> {
            Log.i(getClass().getCanonicalName(), "ULX attempting a direct request to the Internet");

            HttpURLConnection connection = null;

            try {

                // Open the connection. All requests are POST and JSON, for now
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("X-Sequence", Integer.toString(sequenceIdentifier));
                connection.setRequestProperty("X-Hops", Integer.toString(hopCount));
                connection.setRequestProperty("X-Proxy", getHostInstance().getStringIdentifier());
                connection.setRequestProperty("X-Originator", originator.getStringIdentifier());
                connection.setRequestProperty("X-Test", Integer.toString(test));

                // We're writing and reading
                connection.setDoInput(true);
                connection.setDoOutput(true);

                // Open the stream
                java.io.OutputStream outputStream = connection.getOutputStream();
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
                BufferedWriter bufferedWriter = new BufferedWriter(outputStreamWriter);

                // Write the data
                bufferedWriter.write(data);
                bufferedWriter.flush();
                bufferedWriter.close();

                // Do the connection
                connection.connect();

                // Read the response
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder stringBuilder = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    Log.i(getClass().getCanonicalName(), String.format("ULX read server response line: %s", line));
                    stringBuilder.append(line).append("\n");
                }

                // Read the response and propagate
                notifyOnInternetResponse(internetRequestDelegate, connection.getResponseCode(), stringBuilder.toString());

            } catch (IOException e) {

                // We might end up here for several reasons, all of indicating
                // that the request may not proceed. The request proceeds by
                // relying on the mesh.
                makeMeshInternetRequest(sequenceIdentifier, originator, url, data, test);

            } finally {

                // Close the connection, if one was created
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });

        return sequenceIdentifier;
    }

    /**
     * This method creates an {@link InternetPacket} and registers it with the
     * {@link IoController} to be sent over the network. The next-hop device
     * will be computed when the packet is being, according to the result of
     * calling {@link #getBestInternetLinkNextHopDevice(Device)}.
     * @param url The destination {@link URL}.
     * @param data The data to send to the server.
     * @param test The test ID.
     */
    private void makeMeshInternetRequest(int sequence, Instance originator, URL url, String data, int test) {
        Log.i(getClass().getCanonicalName(), "ULX attempting a mesh request to the Internet");

        InternetPacket internetPacket = new InternetPacket(
                sequence,
                url,
                data,
                test,
                originator
        );

        NetworkPacket networkPacket = new NetworkPacket(internetPacket) {

            @Override
            public void handleOnWritten() {
                // Why are we interested on this? There's no event for this at
                // the moment, but maybe future versions will
            }

            @Override
            public void handleOnWriteFailure(UlxError error) {
                notifyOnInternetRequestFailure(getInternetRequestDelegate(), internetPacket.getSequenceIdentifier());
            }

            @Override
            public Device getDevice() {
                return getBestInternetLinkNextHopDevice(null);
            }
        };

        // Send
        getIoController().add(networkPacket);
    }

    /**
     * Propagates a notification to the {@link Delegate} indicating that a
     * response was received from the server for a previous request.
     * @param code The HTTP status code response from the server.
     * @param message The content body received.
     */
    private void notifyOnInternetResponse(InternetRequestDelegate internetRequestDelegate, int code, String message) {
        Log.i(getClass().getCanonicalName(), "ULX got a response from the server");
        if (internetRequestDelegate != null) {
            internetRequestDelegate.onInternetResponse(this, code, message);
        }
    }

    /**
     * Propagates a notification to the {@link Delegate} indicating that an
     * Internet request failed. This means that the request could not be
     * completed locally nor could the device request it be sent remotely.
     * @param sequence The original packet's sequence number.
     */
    private void notifyOnInternetRequestFailure(InternetRequestDelegate internetRequestDelegate, int sequence) {
        if (internetRequestDelegate != null) {
            internetRequestDelegate.onInternetRequestFailure(this, sequence);
        }
    }
}
