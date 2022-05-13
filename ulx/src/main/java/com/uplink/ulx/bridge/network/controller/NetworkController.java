package com.uplink.ulx.bridge.network.controller;

import android.content.Context;

import com.uplink.ulx.UlxError;
import com.uplink.ulx.bridge.SequenceGenerator;
import com.uplink.ulx.bridge.io.controller.IoController;
import com.uplink.ulx.bridge.io.model.AcknowledgementPacket;
import com.uplink.ulx.bridge.io.model.DataPacket;
import com.uplink.ulx.bridge.io.model.HandshakePacket;
import com.uplink.ulx.bridge.io.model.InternetPacket;
import com.uplink.ulx.bridge.io.model.InternetResponsePacket;
import com.uplink.ulx.bridge.io.model.InternetUpdatePacket;
import com.uplink.ulx.bridge.io.model.Packet;
import com.uplink.ulx.bridge.io.model.UpdatePacket;
import com.uplink.ulx.bridge.network.model.Link;
import com.uplink.ulx.bridge.network.model.RoutingTable;
import com.uplink.ulx.bridge.network.model.Ticket;
import com.uplink.ulx.drivers.commons.NetworkStateListener;
import com.uplink.ulx.drivers.model.Channel;
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
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import timber.log.Timber;

/**
 * The {@link NetworkController} is the module that manages network-related
 * stuff, such as routing tables, reachability, network updates, and so on.
 * This class offers a low-level API for sending content ({@link
 * #send(byte[], Instance)}) and negotiating ({@link #negotiate(Device)}).
 */
public class NetworkController implements IoController.Delegate,
                                          RoutingTable.Delegate,
                                          NetworkStateListener.Observer {

    private static final int INTERNET_CONNECT_TIMEOUT_MS = 10_000;
    private static final int INTERNET_READ_TIMEOUT_MS = 10_000;

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
         * This {@link Delegate} notification gives indicating that an Internet request failed to
         * complete. This means that the request could not be performed locally, but also that the
         * host device failed to propagate the request on the network.
         *
         * @param networkController The {@link NetworkController}.
         * @param errorMessage      message describing the reason of the failure
         */
        void onInternetRequestFailure(
                NetworkController networkController,
                String errorMessage
        );
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

    private final Context context;

    private IoController ioController;
    private RoutingTable routingTable;
    private final Instance hostInstance;

    private Delegate delegate;
    private InternetRequestDelegate internetRequestDelegate;

    // This identifier is used to give a sequence number to packets as they are
    // being dispatched. The sequence begins in 0 and resets at 65535.
    private SequenceGenerator sequenceGenerator;

    /**
     * Stores last internet reachability flag sent by {@link NetworkStateListener}.
     * {@code null} means that we haven't received such yet
     */
    @Nullable
    private volatile Boolean isInternetReachable;

    /**
     * Maps devices to the last i-hops value sent to them
     */
    private final ConcurrentMap<Device, Integer> sentIHops;

    private NetworkStateListener networkStateListener;

    public static NetworkController newInstance(Instance hostInstance, Context context) {
        final NetworkController instance = new NetworkController(hostInstance, context);

        final NetworkStateListener networkStateListener = new NetworkStateListener(
                context,
                instance
        );
        instance.setNetworkStateListener(networkStateListener);
        networkStateListener.register();

        return instance;
    }

    /**
     * Constructor.
     */
    private NetworkController(Instance hostInstance, Context context) {

        Objects.requireNonNull(hostInstance);
        Objects.requireNonNull(context);

        this.ioController = null;
        this.routingTable = null;
        this.hostInstance = hostInstance;

        this.delegate = null;
        this.internetRequestDelegate = null;

        this.sequenceGenerator = null;

        this.context = context;

        sentIHops = new ConcurrentHashMap<>();
    }

    private void setNetworkStateListener(NetworkStateListener networkStateListener) {
        this.networkStateListener = networkStateListener;
    }

    /**
     * Returns the Android environment {@link Context} that is used by the
     * implementation to check for Internet connectivity.
     * @return The Android environment {@link Context}.
     */
    @NonNull
    private Context getContext() {
        return this.context;
    }

    /**
     * Returns the {@link IoController} that is responsible for packet I/O
     * operations. The controller's delegate {@link IoController.Delegate} will
     * also be set for the context instance.
     * @return The {@link IoController}.
     */
    public final synchronized IoController getIoController() {
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
    protected synchronized final RoutingTable getRoutingTable() {
        if (this.routingTable == null) {
            this.routingTable = new RoutingTable();
            this.routingTable.setDelegate(this);
        }
        return this.routingTable;
    }

    /**
     * Returns a strong reference to the {@link Delegate} instance that is
     * currently receiving delegate notifications. If no delegate has been
     * registered, this method returns
     * null.
     * @return A strong reference to the {@link Delegate}.
     */
    private Delegate getDelegate() {
        return delegate;
    }

    /**
     * Sets the {@link Delegate} that will receive notifications from the
     * network controller in the future. If any previous delegate has been set,
     * it will be overridden. The instance keeps a weak reference to the
     * delegate, in order to prevent cyclic dependencies.
     * @param delegate The {@link Delegate} to set.
     */
    public final void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    private InternetRequestDelegate getInternetRequestDelegate() {
        return internetRequestDelegate;
    }

    public final void setInternetRequestDelegate(InternetRequestDelegate internetRequestDelegate) {
        this.internetRequestDelegate = internetRequestDelegate;
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
     * Registers a device and thus prepares for potential packet exchange with it
     * @param device the device to add
     */
    public void addDevice(@NonNull Device device) {
        // Register the device
        getRoutingTable().register(device);
        Timber.d("ULX device %s registered in routing table", device.getIdentifier());
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
    public void negotiate(@NonNull Device device) {
        Timber.d("ULX initiating negotiation with %s", device.getIdentifier());

        ExecutorPool.getInternetExecutor().execute(() -> {
            Timber.d("ULX calculating hop count");

            int internetHopCount = RoutingTable.HOP_COUNT_INFINITY;
            try {
                // This call may make requests to the Internet, which is why we're
                // using a different thread
                internetHopCount = getIncrementedInternetHopCount(device);
            } catch (InterruptedException e) {
                Timber.i(e, "Thread interrupted while determining internet availability");
                // Reset the interruption flag
                Thread.currentThread().interrupt();
            }

            final int finalIHopsCount = internetHopCount;

            sentIHops.put(device, finalIHopsCount);

            Timber.e("ULX handshake packet with i-hops %d", internetHopCount);

            // Switch back to the main thread
            Dispatch.post(() -> {

                // Create the handshake packet and send
                HandshakePacket packet = new HandshakePacket(
                        getSequenceGenerator().generate(),
                        getHostInstance(),
                        finalIHopsCount
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
     * Returns the number of hops that takes for this device to reach the Internet, relying on
     * either a direct connection or the mesh, incremented by 1 (for sending updates.) If a direct
     * connection exists, this method returns {@code 1}. If it cannot connect directly, then it
     * checks for the availability of a mesh link that is connected; if one exists, the number of
     * hops for the best scored link will be returned, otherwise {@link
     * RoutingTable#HOP_COUNT_INFINITY} is returned instead.
     *
     * @param splitHorizon the device which will receive the result and should, therefore, not be
     *                     considered
     * @return The number of hops that it takes for the device to reach the Internet, incremented by
     * 1 or {@link RoutingTable#HOP_COUNT_INFINITY}
     */
    @WorkerThread
    private int getIncrementedInternetHopCount(Device splitHorizon) throws InterruptedException {

        //noinspection ConstantConditions there's no way for the field to be set to null
        if (isInternetReachable = networkStateListener.isInternetAvailable()) {
            return 1;
        }

        final RoutingTable.InternetLink link = getRoutingTable().getBestInternetLink(splitHorizon);

        return link != null
                ? Math.min(link.second + 1, RoutingTable.HOP_COUNT_INFINITY)
                : RoutingTable.HOP_COUNT_INFINITY;
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
        Timber.i(
                "ULX-M is dumping the routing table to device %s",
                device.getIdentifier()
        );

        for (Instance instance : getRoutingTable().getInstances()) {

            // We propagate the best link that doesn't rely on the given device
            // as split horizon
            Link link = getRoutingTable().getBestLink(instance, device);

            if (link == null) {
                Timber.i(
                        "ULX no known path to %s with %s as split horizon",
                        instance.getStringIdentifier(),
                        device.getIdentifier()
                );
                continue;
            }

            // The host instance should never be on the table
            assert !link.getDestination().equals(getHostInstance());

            // No link should have "device" as the next hop
            assert !link.getNextHop().getIdentifier().equals(device.getIdentifier());

            int hopCount = Math.min(link.getHopCount() + 1, RoutingTable.HOP_COUNT_INFINITY);

            // Don't propagate events that reach the maximum number of hops
            if (hopCount >= RoutingTable.MAXIMUM_HOP_COUNT) {
                Timber.i(
                        "ULX-M is not propagating a link update, the hop count was exceeded (%d/%d) for link %s",
                        hopCount,
                        RoutingTable.MAXIMUM_HOP_COUNT,
                        link
                );
                continue;
            }

            Timber.i(
                    "ULX-M dumping link %s to device %s",
                    link.toString(),
                    device.getIdentifier()
            );

            UpdatePacket updatePacket = new UpdatePacket(
                    getSequenceGenerator().generate(),
                    link.getDestination(),
                    hopCount
            );

            getIoController().add(new NetworkPacket(updatePacket) {

                @Override
                public void handleOnWritten() {
                    Timber.e("ULX update packet was written");
                }

                @Override
                public void handleOnWriteFailure(UlxError error) {
                    Timber.e("ULX update packet was NOT written");
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

        Timber.i(
                "ULX-M is done dumping the routing table to device %s",
                device.getIdentifier()
        );
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
        Timber.i("ULX acknowledging packet %d", packet.getSequenceIdentifier());

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

    private Device getBestLinkNextHopDevice(Instance destination, @Nullable Device previousHop) {
        Link link = getRoutingTable().getBestLink(destination, previousHop);

        if (link == null) {
            return null;
        }

        return link.getNextHop();
    }

    private Device getBestInternetLinkNextHopDevice(Device previousHop) {
        final RoutingTable.InternetLink link = getRoutingTable().getBestInternetLink(previousHop);
        return link != null ? link.first : null;
    }

    @Override
    public void onPacketReceived(IoController controller, InputStream inputStream, Packet packet) {
        Timber.i(
                "ULX-M packet received from input stream %s: %s",
                inputStream.getIdentifier(),
                packet.toString()
        );

        // Compute the device, according to the InputStream given
        synchronized (getRoutingTable()) {
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

                case INTERNET_UPDATE:
                    handleIHopsUpdate(
                            device,
                            ((InternetUpdatePacket) packet).getHopCount()
                    );
                    break;
            }
        }

        Timber.d("Packet handled");
    }

    @Override
    public void onPacketWritten(IoController controller, OutputStream outputStream, IoController.IoPacket ioPacket) {
        Timber.i(
                "ULX packet %s written to %s",
                ioPacket.toString(),
                outputStream.getIdentifier()
        );
        ((NetworkPacket)ioPacket).handleOnWritten();
    }

    @Override
    public void onPacketWriteFailure(IoController controller, OutputStream outputStream, IoController.IoPacket ioPacket, UlxError error) {
        Timber.e("ULX packet could not be written. %s", error);
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
     * @param device The {@link Device} that sent the packet.
     * @param packet The {@link HandshakePacket} that was received.
     */
    private void handleHandshakePacketReceived(Device device, HandshakePacket packet) {

        // If the device has not been registered, this is most likely a
        // programming error, since we're already communicating with it
        if (device == null) {
            handleHandshakePacketDeviceNotFound(null, packet);
            return;
        }

        final Instance instance = packet.getOriginator();

        Timber.i(
                "ULX-M device %s is instance %s",
                device.getIdentifier(),
                instance.getStringIdentifier()
        );

        // Create a link to the negotiated Instance, having the device has
        // next hop. Since this is a handshake packet, the hop count is always
        // 1, and the device is in direct link
        getRoutingTable().registerOrUpdate(
                device,
                instance,
                1
        );

        handleIHopsUpdate(device, packet.getInternetHops());

        getRoutingTable().log();
    }

    public void removeDevice(Device device, UlxError error) {

        // Clearing the device from the routing table could result in several
        // lost instances
        getRoutingTable().unregister(device);

        // TODO there might still be pending I/O with this device, which must
        //      be cleared.
        getRoutingTable().log();

        // In some cases we cannot get any indication of failure from the stream itself, even if
        // there was an operation pending. One example is that notification sent callback won't be
        // called if the adapter has been turned off. So we'll invalidate the streams just in case
        final Channel channel = device.getTransport().getReliableChannel();
        getIoController().onInvalidation(channel.getInputStream(), error);
        getIoController().onInvalidation(channel.getOutputStream(), error);
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
            Timber.e("ULX received an update " +
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
            Timber.i("ULX-M update packet being " +
                             "ignored because it refers to the host instance");
            return;
        }

        // This should correspond to an update, since we haven't proceeded with
        // new registrations above
        getRoutingTable().registerOrUpdate(
                device,
                packet.getInstance(),
                packet.getHopCount()
        );

        getRoutingTable().log();
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
        Timber.e("ULX a handshake was received " +
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
                Timber.e("ULX could not find a device with an input device");
                return;
            }

            forwardMeshPacket(packet, device);
        }
    }

    private void handleInternetPacketReceived(Device device, InternetPacket packet) {
        Instance originator = packet.getOriginator();
        final URL url = packet.getUrl();
        final String data = packet.getData();
        final int test = packet.getTest();
        int hopCount = packet.getHopCount() + 1;
        // I'm not a fan of the pattern of passing the InternetRequestDelegate
        // through the stack, but this will work as a work around for now.
        makeInternetRequest(
                originator,
                packet.getSequenceIdentifier(),
                url,
                data,
                test,
                hopCount,
                new InternetRequestDelegate() {

            @Override
            public void onInternetResponse(NetworkController networkController, int code, String message) {
                Timber.i("ULX is redirecting an Internet response packet");

                final InternetResponsePacket responsePacket = new InternetResponsePacket(
                        getSequenceGenerator().generate(),
                        code,
                        message,
                        originator
                );

                // We're here because we received an Internet request and
                // forwarded the packet to the server. This is the response
                // from the server, which means that we need to relay it
                // through the mesh, back to the originator.
                IoController.IoPacket ioPacket = new NetworkPacket(responsePacket) {

                    @Override
                    public void handleOnWritten() {
                        // Ok
                        Timber.i("ULX relayed an Internet packet");
                    }

                    @Override
                    public void handleOnWriteFailure(UlxError error) {
                        // ??
                        Timber.e("ULX could not relay an Internet packet");
                    }

                    @Override
                    public Device getDevice() {
                        final Link link = getRoutingTable().getBestLink(originator, null);
                        return link != null ? link.getNextHop() : null;
                    }
                };

                // Queue for dispatch
                getIoController().add(ioPacket);
            }

            @Override
            public void onInternetRequestFailure(
                    NetworkController networkController,
                    String errorMessage
            ) {
                Timber.e("ULX failed to relay an Internet packet");
                // We might end up here for several reasons, all of indicating
                // that the request may not proceed. The request proceeds by
                // relying on the mesh.
                final Device nextHop = getBestInternetLinkNextHopDevice(device);
                if (nextHop != null && hopCount < RoutingTable.MAXIMUM_HOP_COUNT) {
                    makeMeshInternetRequest(
                            getSequenceGenerator().generate(),
                            originator,
                            url,
                            data,
                            test,
                            hopCount
                    );
                } else {
                    // Forward the failure to the originator
                    final InternetResponsePacket responsePacket = new InternetResponsePacket(
                            getSequenceGenerator().generate(),
                            InternetResponsePacket.CODE_IO_GENERIC_FAILURE,
                            errorMessage,
                            originator
                    );
                    forwardInternetResponsePacket(responsePacket, null);
                }
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

        if (getHostInstance().equals(packet.getOriginator())) {
            // It was our request that failed

            if (packet.getCode() != InternetResponsePacket.CODE_IO_GENERIC_FAILURE) {
                notifyOnInternetResponse(
                        getInternetRequestDelegate(),
                        packet.getCode(),
                        packet.getData()
                );
            } else {
                // The request has failed
                notifyOnInternetRequestFailure(getInternetRequestDelegate(), packet.getData());
            }
        } else {
            // Forward the response
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
        Timber.i(
                "ULX is forwarding a mesh packet [%d] to %s",
                packet.getSequenceIdentifier(),
                destination.getStringIdentifier()
        );

        // Instantiate the IoPacket with the proper next-hop lookup
        IoController.IoPacket ioPacket = new NetworkPacket(packet) {

            @Override
            public Device getDevice() {
                Device device = getBestLinkNextHopDevice(destination, splitHorizon);
                Timber.i(
                        "ULX best link next hop is %s",
                        device != null ? device.getIdentifier() : null
                );
                return device;
            }

            @Override
            public void handleOnWritten() {
                Timber.i("ULX-M packet relayed successfully");
                // Do nothing; we're currently not tracking mesh relays
            }

            @Override
            public void handleOnWriteFailure(UlxError error) {
                Timber.e("ULX-M packet relay failed");
                // Do nothing; we're currently not tracking mesh relays
            }
        };

        // Queue for dispatch
        getIoController().add(ioPacket);
    }

    private void forwardInternetResponsePacket(
            InternetResponsePacket packet,
            @Nullable Device splitHorizon
    ) {
        Timber.i("ULX is forwarding an Internet response packet");

        // Instantiate the IoPacket with the proper next-hop lookup
        IoController.IoPacket ioPacket = new NetworkPacket(packet) {

            @Override
            public Device getDevice() {
                return getBestLinkNextHopDevice(packet.getOriginator(), splitHorizon);
            }

            @Override
            public void handleOnWritten() {
                Timber.i("ULX Internet response packet relayed successfully");
                // Do nothing; we're currently not tracking mesh relays
            }

            @Override
            public void handleOnWriteFailure(UlxError error) {
                Timber.e("ULX Internet response packet relay failed");
                // Do nothing; we're currently not tracking mesh relays
            }
        };

        // Queue for dispatch
        getIoController().add(ioPacket);
    }

    /**
     * Handles an update from a peer device regarding its internet reachability. Updates {@link
     * RoutingTable} and notifies other devices about i-hops count update if necessary
     *
     * @param device     the device which has its i-hops count updated
     * @param iHopsCount new i-hops count for the instance
     */
    private void handleIHopsUpdate(Device device, int iHopsCount) {

        synchronized (getRoutingTable()) {
            getRoutingTable().updateInternetHopsCount(
                    device,
                    iHopsCount
            );

            if (isInternetReachable == null) {
                // This should not happen normally. If we receive iHops update from a device,
                // we should have negotiated with it by now, which includes setting
                // isInternetReachable flag
                return;
            }

            //noinspection ConstantConditions once non-null, the field is never set to null
            if (isInternetReachable) {
                Timber.v(
                        "We have our own internet. " +
                                "No need to propagate i-hops update from another device"
                );
            } else {
                final RoutingTable.InternetLink newBestLink = getRoutingTable()
                        .getBestInternetLink(null);


                if (newBestLink != null) {
                    // Send updated i-hops count to everyone except through which
                    // internet is to be accessed
                    scheduleInternetUpdatePacket(newBestLink.second + 1, newBestLink.first);

                    final RoutingTable.InternetLink secondBestLink = getRoutingTable()
                            .getBestInternetLink(newBestLink.first);

                    // Tell our 'internet provider' device about our alternative i-hops
                    scheduleInternetUpdatePacketForDevice(
                            createInternetUpdatePacket(
                                    secondBestLink != null ? secondBestLink.second + 1 : RoutingTable.HOP_COUNT_INFINITY
                            ),
                            newBestLink.first
                    );
                } else {
                    // Tell everyone that we don't have internet access anymore
                    scheduleInternetUpdatePacket(RoutingTable.HOP_COUNT_INFINITY, null);
                }
            }
        }
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
                Timber.e("ULX could not find a device with an input device");
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
    public void onInstanceLost(
            RoutingTable routingTable,
            @NonNull Device lastDevice,
            Instance instance,
            UlxError error
    ) {
        // HOP_COUNT_INFINITY means that the instance is unreachable
        final UpdatePacket updatePacket = new UpdatePacket(
                getSequenceGenerator().generate(),
                instance,
                RoutingTable.HOP_COUNT_INFINITY
        );
        scheduleUpdatePacket(updatePacket, lastDevice);

        notifyOnInstanceLost(instance, error);
    }

    @Override
    public void onConnectivityChanged(boolean isInternetAvailable) {
        Timber.v("Connectivity changed. Internet available: %s", isInternetAvailable);

        final int internetHopCount;
        final RoutingTable.InternetLink bestInternetLink;

        isInternetReachable = isInternetAvailable;

        if (isInternetAvailable) {
            internetHopCount = 1;
            // We don't need any link to reach internet
            bestInternetLink = null;
        } else {
            bestInternetLink = getRoutingTable().getBestInternetLink(null);
            internetHopCount = bestInternetLink != null
                    ? Math.min(bestInternetLink.second + 1, RoutingTable.HOP_COUNT_INFINITY)
                    : RoutingTable.HOP_COUNT_INFINITY;
        }

        scheduleInternetUpdatePacket(
                internetHopCount,
                bestInternetLink != null ? bestInternetLink.first : null
        );

        if (bestInternetLink != null) {
            final RoutingTable.InternetLink secondBestLink = getRoutingTable()
                    .getBestInternetLink(bestInternetLink.first);
            final int secondHopCount = secondBestLink != null
                    ? Math.min(secondBestLink.second + 1, RoutingTable.HOP_COUNT_INFINITY)
                    : RoutingTable.HOP_COUNT_INFINITY;
            scheduleInternetUpdatePacketForDevice(
                    createInternetUpdatePacket(secondHopCount),
                    bestInternetLink.first
            );
        }
    }

    /**
     * Send notification to all of the known instances except splitHorizon about internet
     * availability change
     *
     * @param internetHopCount new i-hops count (from the recipients' perspective)
     * @param splitHorizon     device to skip
     */
    @GuardedBy("iHopsLock")
    private void scheduleInternetUpdatePacket(
            int internetHopCount,
            Device splitHorizon
    ) {
        final InternetUpdatePacket packet = createInternetUpdatePacket(internetHopCount);

        for (Device device : getRoutingTable().getDeviceList()) {
            if (!device.equals(splitHorizon)) {
                scheduleInternetUpdatePacketForDevice(packet, device);
            }
        }
    }

    @NonNull
    private InternetUpdatePacket createInternetUpdatePacket(int internetHopCount) {
        return new InternetUpdatePacket(
                getSequenceGenerator().generate(),
                getHostInstance(),
                internetHopCount
        );
    }

    /**
     * Sends notification to the given device about internet availability change
     *
     * @param packet packet to send
     * @param device device to notify
     */
    private void scheduleInternetUpdatePacketForDevice(InternetUpdatePacket packet, Device device) {
        if (Integer.valueOf(packet.getHopCount())
                .equals(sentIHops.put(device, packet.getHopCount()))) {
            Timber.v(
                    "InternetUpdate packet with i-hops count %d won't be sent to [%s], because it is already up-to-date",
                    packet.getHopCount(),
                    device
            );
            return;
        }


        Timber.d("Scheduling packet [%s] for device [%s]", packet, device.getIdentifier());

        getIoController().add(new NetworkPacket(packet) {
            @Override
            public void handleOnWritten() {
                Timber.d("Internet update packet sent");
            }

            @Override
            public void handleOnWriteFailure(UlxError error) {
                Timber.w("Failed to send internet update packet: %s", error.getReason());
            }

            @Override
            public Device getDevice() {
                return device;
            }
        });
    }

    @Override
    public void onLinkUpdate(RoutingTable routingTable, Link link) {
        update(link);
    }

    private void update(Link link) {

        assert link != null;

        int hopCount = Math.min(link.getHopCount() + 1, RoutingTable.HOP_COUNT_INFINITY);

        Timber.i("ULX-M is propagating link update %s", link.toString());

        // This version propagates all updates, but that might turn out to be
        // too much. Future versions may condense several updates together, so
        // that the network is not so flooded.
        UpdatePacket updatePacket = new UpdatePacket(
                getSequenceGenerator().generate(),
                link.getDestination(),
                hopCount
        );

        // Schedule the update packet to be sent
        scheduleUpdatePacket(updatePacket, link.getNextHop());
    }

    @Override
    public void onSplitHorizonLinkUpdate(
            RoutingTable routingTable,
            Device bestLinkDevice,
            Instance destination,
            int hopCount
    ) {
        Timber.i(
                "Sending split-horizon update to %s regarding instance %s. Hop count: %d",
                bestLinkDevice.getIdentifier(),
                destination,
                hopCount
        );

        final UpdatePacket updatePacket = new UpdatePacket(
                getSequenceGenerator().generate(),
                destination,
                hopCount
        );

        scheduleUpdatePacketForDevice(updatePacket, bestLinkDevice);
    }

    /**
     * Propagates the given {@link UpdatePacket} to all {@link Device}s connected in line of sight
     * (LoS).
     *
     * @param updatePacket The packet to propagate.
     * @param splitHorizon A device to ignore, which is the next-hop for the updated link or a
     *                     just-lost device
     */
    private void scheduleUpdatePacket(UpdatePacket updatePacket, @NonNull Device splitHorizon) {

        for (Device device : getRoutingTable().getDeviceList()) {
            Timber.i(
                    "ULX-M scheduling update packet %s to %s with split horizon %s",
                    updatePacket.toString(),
                    device.getIdentifier(),
                    splitHorizon.getIdentifier()
            );

            // Ignore the split horizon
            if (device.equals(splitHorizon)) {
                Timber.i(
                        "ULX-M not propagating update packet %s to device %s because the device is the split horizon",
                        updatePacket,
                        splitHorizon.getIdentifier()
                );
                continue;
            }

            // The update packet is propagated to all devices in direct link
            // (except the split horizon)
            scheduleUpdatePacketForDevice(updatePacket, device);
        }
    }

    /**
     * Send update packet to the given device
     *
     * @param updatePacket The packet
     * @param device       The receiver
     */
    private void scheduleUpdatePacketForDevice(UpdatePacket updatePacket, Device device) {
        getIoController().add(new NetworkPacket(updatePacket) {

            @Override
            public void handleOnWritten() {
                Timber.e("ULX update packet was written");
            }

            @Override
            public void handleOnWriteFailure(UlxError error) {
                Timber.e("ULX update packet was NOT written");
            }

            @Override
            public Device getDevice() {
                return device;
            }
        });
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
     */
    public void sendInternet(URL url, JSONObject jsonObject, int test) {
        final Instance originator = getHostInstance();
        final String data = jsonObject.toString();
        // I'm not a fan of the pattern of passing the InternetRequestDelegate
        // through the stack, but this will work as a work around for now.
        makeInternetRequest(
                originator,
                getSequenceGenerator().generate(),
                url,
                data,
                test,
                0,
                new InternetRequestDelegate() {

            @Override
            public void onInternetResponse(NetworkController networkController, int code, String message) {
                final InternetRequestDelegate requestDelegate = getInternetRequestDelegate();
                if (requestDelegate != null) {
                    requestDelegate.onInternetResponse(networkController, code, message);
                }
            }

            @Override
            public void onInternetRequestFailure(
                    NetworkController networkController,
                    String errorMessage
            ) {
                Timber.e("ULX failed to send an Internet request");
                // We might end up here for several reasons, all of indicating
                // that the request may not proceed. The request proceeds by
                // relying on the mesh.
                final Device nextHop = getBestInternetLinkNextHopDevice(null);
                if (nextHop != null && 0 < RoutingTable.MAXIMUM_HOP_COUNT) {
                    makeMeshInternetRequest(
                            getSequenceGenerator().generate(),
                            originator,
                            url,
                            data,
                            test,
                            0 // will be incremented by the next hop
                    );
                } else {
                    notifyOnInternetRequestFailure(getInternetRequestDelegate(), errorMessage);
                }
            }
        });
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
            Timber.i("ULX attempting a direct request to the Internet");

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

                connection.setConnectTimeout(INTERNET_CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(INTERNET_READ_TIMEOUT_MS);

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
                    Timber.i("ULX read server response line: %s", line);
                    stringBuilder.append(line).append("\n");
                }

                // Read the response and propagate
                notifyOnInternetResponse(internetRequestDelegate, connection.getResponseCode(), stringBuilder.toString());

            } catch (IOException e) {
                Timber.w(e, "Failed to send internet request");
                notifyOnInternetRequestFailure(
                        internetRequestDelegate,
                        e.getMessage()
                );

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
     * This method creates an {@link InternetPacket} and registers it with the {@link IoController}
     * to be sent over the network. The next-hop device will be computed when the packet is being,
     * according to the result of calling {@link #getBestInternetLinkNextHopDevice(Device)}.
     *
     * @param url      The destination {@link URL}.
     * @param data     The data to send to the server.
     * @param test     The test ID.
     * @param hopCount hop count between the hosting instance and the originator
     */
    private void makeMeshInternetRequest(
            int sequence,
            Instance originator,
            URL url,
            String data,
            int test,
            int hopCount
    ) {
        Timber.i("ULX attempting a mesh request to the Internet");

        InternetPacket internetPacket = new InternetPacket(
                sequence,
                url,
                data,
                test,
                originator,
                hopCount
        );

        NetworkPacket networkPacket = new NetworkPacket(internetPacket) {

            @Override
            public void handleOnWritten() {
                // Why are we interested on this? There's no event for this at
                // the moment, but maybe future versions will
            }

            @Override
            public void handleOnWriteFailure(UlxError error) {
                String errorMessage = error.getDescription();
                if (originator.equals(getHostInstance())) {
                    // It was our request that failed
                    notifyOnInternetRequestFailure(
                            getInternetRequestDelegate(),
                            errorMessage
                    );
                } else {
                    // Forward the failure to the originator
                    final InternetResponsePacket responsePacket = new InternetResponsePacket(
                            getSequenceGenerator().generate(),
                            InternetResponsePacket.CODE_IO_GENERIC_FAILURE,
                            errorMessage,
                            originator
                    );
                    forwardInternetResponsePacket(responsePacket, null);
                }
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
        Timber.i("ULX got a response from the server");
        if (internetRequestDelegate != null) {
            internetRequestDelegate.onInternetResponse(this, code, message);
        }
    }

    /**
     * Propagates a notification to the {@link Delegate} indicating that an Internet request failed.
     * This means that the request could not be completed locally nor could the device request it be
     * sent remotely.
     *
     * @param errorMessage message describing the reason of the failure
     */
    private void notifyOnInternetRequestFailure(
            InternetRequestDelegate internetRequestDelegate,
            String errorMessage
    ) {
        if (internetRequestDelegate != null) {
            internetRequestDelegate.onInternetRequestFailure(this, errorMessage);
        }
    }

    /**
     * Cleans up allocated resources
     */
    public void destroy() {
        networkStateListener.destroy();
    }
}
