package com.uplink.ulx.bridge;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.Log;

import com.uplink.ulx.UlxError;
import com.uplink.ulx.bridge.network.controller.NetworkController;
import com.uplink.ulx.bridge.network.model.Ticket;
import com.uplink.ulx.drivers.model.Connector;
import com.uplink.ulx.drivers.model.Device;
import com.uplink.ulx.drivers.model.InputStream;
import com.uplink.ulx.drivers.model.OutputStream;
import com.uplink.ulx.drivers.model.Stream;
import com.uplink.ulx.model.Instance;
import com.uplink.ulx.model.Message;
import com.uplink.ulx.model.MessageInfo;
import com.uplink.ulx.threading.ExecutorPool;
import com.uplink.ulx.utils.StringUtils;

import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

import androidx.annotation.Nullable;

/**
 * This bridge is the main point of communication between the Java service and
 * the JNI implementation, when one exists. It implements some of the logic
 * regarding service initialization, but its main responsibility is to bridge
 * calls between the Java abstraction and the native implementation. This class
 * will issue calls to the Core and return responses in the form of a delegate.
 * This also means that the bridge must handle the thread context changes that
 * occur between those processes. At the moment, this class is a god-object,
 * given that it's assuming all of the responsibilities that should be assigned
 * to the core; this is expected to change once the native core implementation
 * is in place.
 */
public class Bridge implements
                    NetworkController.Delegate,
                    NetworkController.InternetRequestDelegate,
                    Connector.StateDelegate,
                    Stream.StateDelegate, Connector.InvalidationCallback {
    /**
     * The Bridge Delegate gets notifications for bridge-related events, which
     * will include a wide variety of such events. At this moment, only the
     * initialization process is being reported, but future versions will
     * include all sorts of events coming from the Core. For example, this will
     * include connection events, I/O, and device discovery lifecycle.
     */
    public interface StateDelegate {

        /**
         * This delegate notification is triggered by the Bridge when the
         * service initialization has been completed, implying the
         * initialization of the core and the JNI bridge. From this moment on,
         * the implementation will have an associated Instance (host instance),
         * with a local identifier and other metadata. It's also possible for
         * the service to start making calls to the bridge, including crossing
         * the JNI bridge.
         * @param bridge The bridge (singleton) instance making the call.
         * @param hostInstance The domestic Instance created as a result of
         *                     the initialization process.
         */
        void onInitialization(Bridge bridge, Instance hostInstance);
    }

    /**
     * The NetworkDelegate gets notifications for network events coming from
     * the bridge, such as instances being found and lost on the network.
     */
    public interface NetworkDelegate {

        /**
         * A new instance has been found on the network. This differs from
         * "finding a device" in the sense that instances may correspond to
         * a device over multiple transports, while a Device corresponds to
         * only a single transport. For example, a device being found over
         * Bluetooth LE corresponds to a device; if the device is also found
         * over Infrastructure WiFi, it will be a different instance, but
         * both will be encapsulated under the same Instance. In order to
         * solve this problem, the implementation will have to negotiate with
         * the remote device for some sort of proof-of-identity.
         * @param bridge The Bridge issuing the notification.
         * @param instance The instance that was found.
         */
        void onInstanceFound(Bridge bridge, Instance instance);

        /**
         * When this delegate method is called, the given instance cannot be
         * reached over any type of transport. This means that the last
         * transport to be aware of it also lost it, and thus the instance
         * is not reachable in any way.
         * @param bridge The Bridge issuing the notification.
         * @param instance The instance that was lost.
         * @param error An error, providing an explanation for the loss.
         */
        void onInstanceLost(Bridge bridge, Instance instance, UlxError error);
    }

    /**
     * Message delegates receive notifications for events related with content
     * delivery lifecycle. This includes messages being delivered or not.
     */
    public interface MessageDelegate {

        /**
         * The {@link Message} corresponding to the given {@link MessageInfo}
         * was successfully written to the network, meaning that the output
         * was written to another device. It does not, however, mean that it
         * has reached its destination, since it's still circulating on the
         * network. Having been "sent" means that it left the device and has
         * not been acknowledged yet.
         * @param bridge The {@link Bridge} issuing the notification.
         * @param messageInfo The {@link MessageInfo} for the {@link Message}
         *                    that was sent.
         */
        void onMessageSent(Bridge bridge, MessageInfo messageInfo);

        /**
         * The message corresponding to the given {@link MessageInfo} could not
         * be sent. This means that the message did not leave the device, in
         * full or in part. This may happens in situations such as an instance
         * being lost while the content is being dispatched. In practice, some
         * content may already have been delivered — even acknowledge — but the
         * full content will not; the message delivery will not proceed and any
         * content that has already been delivered should probably be discarded.
         * @param bridge The {@link Bridge} issuing the notification.
         * @param messageInfo The {@link MessageInfo} for the {@link Message}.
         * @param error An error, indicating a probable cause for the failure.
         */
        void onMessageSendFailure(Bridge bridge, MessageInfo messageInfo, UlxError error);

        /**
         * This {@link MessageDelegate} notification is called when a {@link
         * Message} is known to have been acknowledged by a destination. This
         * means that the message was successfully received, and that the
         * destination acknowledges receiving it.
         * @param bridge The {@link Bridge} issuing the notification.
         * @param messageInfo The {@link MessageInfo} corresponding to the
         *                    {@link Message} that was acknowledged.
         */
        void onMessageDelivered(Bridge bridge, MessageInfo messageInfo);

        /**
         * This delegate notification is called when a message is received. The
         * given data contains the payload and the {@link Instance} corresponds
         * to the message's originator. The message might not have been
         * acknowledged to the originator yet.
         * @param bridge The {@link Bridge} issuing the notification.
         * @param data The payload received.
         * @param origin The originating {@link Instance}.
         */
        void onMessageReceived(Bridge bridge, byte[] data, Instance origin);

        /**
         * This {@link MessageDelegate} notification is called when an Internet
         * response is received meant for the host instance. This means that
         * the request was done either locally or through the mesh, but that
         * either way the response is meant for the host.
         * @param bridge the {@link Bridge}.
         * @param code The server HTTP status response code.
         * @param content The server response content.
         */
        void onInternetResponse(Bridge bridge, int code, String content);
    }

    private static Bridge instance = null;

    private Instance hostInstance;

    private WeakReference<StateDelegate> stateDelegate;
    private WeakReference<NetworkDelegate> networkDelegate;
    private WeakReference<MessageDelegate> messageDelegate;

    private Registry<BluetoothDevice> southRegistry;

    private NetworkController networkController;
    private HashMap<Ticket, MessageInfo> tickets;

    private WeakReference<Context> context;

    /**
     * Private constructor prevents instantiation.
     */
    private Bridge() {

        this.hostInstance = null;

        this.stateDelegate = null;
        this.networkDelegate = null;
        this.messageDelegate = null;

        this.southRegistry = null;

        this.networkController = null;
        this.tickets = null;

        this.context = null;
    }

    /**
     * Returns the host {@link Instance} if one has already been initialized.
     * If not, this method returns {@code null}.
     * @return The host {@link Instance}.
     */
    private Instance getHostInstance() {
        return this.hostInstance;
    }

    /**
     * Sets the Android environment {@link Context} that will be propagated
     * throughout the implementation.
     * @param context The {@link Context} to set.
     */
    public void setContext(Context context) {
        Objects.requireNonNull(context);
        this.context = new WeakReference<>(context);
    }

    /**
     * Returns the Android environment {@link Context}.
     * @return The Android environment {@link Context}.
     */
    private Context getContext() {
        return this.context.get();
    }

    /**
     * Accessor for the singleton default instance.
     * @return The singleton instance.
     */
    public static Bridge getInstance() {
        if (Bridge.instance == null) {
            Bridge.instance = new Bridge();
        }
        return Bridge.instance;
    }

    /**
     * Sets the state delegate that will receive notifications from the bridge,
     * while keeping a weak reference to it.
     * @param stateDelegate The state delegate (StateDelegate) to set.
     */
    public final void setStateDelegate(StateDelegate stateDelegate) {
        this.stateDelegate = new WeakReference<>(stateDelegate);
    }

    /**
     * Returns the state delegate that has previously been set. It's notable
     * that if the delegate was not previously set, this method will raise a
     * null pointer exception.
     * @return The bridge's delegate.
     */
    private StateDelegate getStateDelegate() {
        return this.stateDelegate.get();
    }

    /**
     * Sets the network delegate that will receive notifications from the
     * bridge with respect to network events. The delegate is kept as a weak
     * reference.
     * @param networkDelegate The network delegate (NetworkDelegate) to set.
     */
    public final void setNetworkDelegate(NetworkDelegate networkDelegate) {
        this.networkDelegate = new WeakReference<>(networkDelegate);
    }

    /**
     * Returns the delegate that has previously been set for receiving network
     * event notifications. If none has been set, this method raises a null
     * pointer exception.
     * @return The network delegate ({@code NetworkDelegate}).
     */
    @Nullable
    private NetworkDelegate getNetworkDelegate() {
        return this.networkDelegate.get();
    }

    /**
     * Sets the {@link MessageDelegate} that will receive notifications from the
     * bridge with respect to message lifecycle. If a previous delegate has
     * been set, it will be overridden. The instance will be kept as a weak
     * reference.
     * @param messageDelegate The {@link MessageDelegate} to set.
     */
    public void setMessageDelegate(MessageDelegate messageDelegate) {
        this.messageDelegate = new WeakReference<>(messageDelegate);
    }

    /**
     * Returns the {@link MessageDelegate} that has previously been set with
     * the {@link Bridge#setMessageDelegate(MessageDelegate)} method. This
     * corresponds to the delegate that is currently getting notifications for
     * message lifecycle events. If no delegate has previously been set, this
     * method returns {@code null}.
     * @return The current {@link MessageDelegate}.
     */
    public MessageDelegate getMessageDelegate() {
        return this.messageDelegate != null ? this.messageDelegate.get() : null;
    }

    /**
     * Returns the device registry that is used by the South bridge to map
     * native system framework {@link BluetoothDevice} instances with their
     * corresponding abstract {@link Device} instances. This will be used by
     * the South bridge to track which devices correspond to what native system
     * instances.
     * @return The South bridge registry.
     */
    private Registry<BluetoothDevice> getSouthRegistry() {
        if (this.southRegistry == null) {
            this.southRegistry = new Registry<>();
        }
        return this.southRegistry;
    }

    /**
     * The {@link NetworkController} is the low-level module that manages all
     * network related stuff. This includes routing tables, network updates,
     * and so on. This method returns {@code null} until the bridge has been
     * initialized, along with the {@link NetworkController}.
     * @return The {@link NetworkController}.
     */
    private NetworkController getNetworkController() {
        return this.networkController;
    }

    /**
     * Returns the data structure that is used to map {@link Ticket}s with
     * their corresponding {@link MessageInfo}.
     * @return The ticket hash map.
     */
    private HashMap<Ticket, MessageInfo> getTickets() {
        if (this.tickets == null) {
            this.tickets = new HashMap<>();
        }
        return this.tickets;
    }

    /**
     * This will be the main entry point to initialize the framework over the
     * JNI interface when that support is enabled. The call will cross over
     * JNI to run native initialization code. The app identifier is used to
     * create the domestic instance, which is the instance hosted by the device.
     * @param appIdentifier The app identifier.
     */
    public void initialize(final String appIdentifier) {

        WeakReference<Bridge> weakSelf = new WeakReference<>(this);

        // The app identifier must already be set at this point
        if (appIdentifier == null) {
            throw new RuntimeException("The service initialization may not proceed because the app identifier was not set.");
        }

        // The current implementation does not yet bind with JNI, but we're
        // already dispatching working on a dedicated thread so that this
        // management is initiated early on. When JNI support is enabled, this
        // executor should be maintained.
        ExecutorPool.getCoreExecutor().execute(() -> {

            Bridge strongSelf = weakSelf.get();

            if (strongSelf != null && strongSelf.getHostInstance() == null) {

                // Generate an identifier for the host instance
                byte[] hostIdentifier = generateIdentifier(appIdentifier);
                Instance hostInstance = new Instance(hostIdentifier);

                // Instantiate the network controller, which will hold the host
                // instance.
                this.networkController = new NetworkController(hostInstance, getContext());
                this.networkController.setDelegate(this);
                this.networkController.setInternetRequestDelegate(this);

                // Set the host instance
                this.hostInstance = hostInstance;

                Log.i(getClass().getCanonicalName(), String.format("ULX-M instance created with identifier %s", hostInstance.getStringIdentifier()));
            }

            // If the Bridge was deallocated in the meanwhile, do nothing.
            if (strongSelf != null) {
                StateDelegate delegate = getStateDelegate();
                if (delegate != null) {
                    delegate.onInitialization(strongSelf, getHostInstance());
                }
            }
        });
    }

    /**
     * This method generates a device identifier and prepends it with the given
     * app identifier. This method is not meant to be implemented in Java, but
     * rather as a native bind using JNI, meaning that this implementation is
     * temporary.
     * @param appIdentifier The app identifier to prepend.
     * @return A newly generated identifier for the host device.
     */
    private byte[] generateIdentifier(String appIdentifier) {

        byte[] appIdentifierBuf = parseAppIdentifier(appIdentifier);
        byte[] buffer = new byte[16];

        generateRandomIdentifier(buffer);

        // Replace the first few bytes with the app identifier
        System.arraycopy(appIdentifierBuf, 0, buffer, 0, appIdentifierBuf.length);

        return buffer;
    }

    /**
     * Generates a random 128 bits identifier by writing it to the given byte
     * array.
     * @param buffer The buffer to write.
     */
    private void generateRandomIdentifier(byte[] buffer) {

        // Random UUID
        UUID uuid = UUID.randomUUID();

        // Write on the buffer
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
        byteBuffer.putLong(uuid.getMostSignificantBits());
        byteBuffer.putLong(uuid.getLeastSignificantBits());
    }

    /**
     * Validates and parses an app identifier. The identifier must be an string
     * encoded in hexadecimal and exactly 8 characters long. If those conditions
     * are met, this method returns a byte array filled with bytes corresponding
     * to their hexadecimal counterpart.
     * @param appIdentifier The app identifier to parse.
     * @return The same identifier, decoded as a byte array.
     */
    private byte[] parseAppIdentifier(String appIdentifier) {
        validateAppIdentifier(appIdentifier);
        return StringUtils.hexStringToByteArray(appIdentifier);
    }

    /**
     * This method validates an app identifier, raising an exception if any
     * validation errors occurs. A valid app identifier is an hexadecimal
     * string with 8 characters.
     * @param appIdentifier The app identifier to validate.
     */
    private static void validateAppIdentifier(String appIdentifier) {

        if (appIdentifier == null) {
            throw new RuntimeException("Could not read an app identifier, got null instead.");
        }

        if (!StringUtils.isHex(appIdentifier)) {
            throw new RuntimeException(String.format("App identifiers are expected to be an hexadecimal string, but some characters in '%s' are not.", appIdentifier));
        }

        if (appIdentifier.length() != 8) {
            throw new RuntimeException(String.format("App identifiers are expected to be an hexadecimal string with 8 characters, but got a string (%s) with %d characters instead.", appIdentifier, appIdentifier.length()));
        }
    }

    @Override
    public void onConnected(Connector connector) {
        Log.i(getClass().getCanonicalName(), "ULX bridge connector connected");
    }

    @Override
    public void onDisconnection(Connector connector, UlxError error) {
        Log.e(getClass().getCanonicalName(), "ULX connector disconnected on the bridge");
        Log.e(getClass().getCanonicalName(), String.format("ULX connector is %s", connector.getIdentifier()));

        Device device = getSouthRegistry().getDeviceInstance(connector.getIdentifier());

        if (device == null) {
            Log.e(getClass().getCanonicalName(), "ULX device was not found on the registry");
            return;
        }

        // We've previously assumed the delegates for these
        InputStream inputStream = device.getTransport().getReliableChannel().getInputStream();
        OutputStream outputStream = device.getTransport().getReliableChannel().getOutputStream();

        // TODO how can we close the streams with an error? We shouldn't request
        //      the streams to close directly, since this is already known to be
        //      a failure situation, but the bridge shouldn't communicate with
        //      the streams at a low level either. I'm leaving them as is for
        //      now, but this should be structurally reviewed.

        // TODO For now, we're keeping the stream's delegates, but I'm not sure
        //      whether that should hold.
        //inputStream.setStateDelegate(null);
        //outputStream.setStateDelegate(null);
        //
        //inputStream.setDelegate(null);  // Was the IoController before
        //outputStream.setDelegate(null);

        // Clear the device from the lower grade controllers
        getNetworkController().removeDevice(device);

        // Unregister the device
        getSouthRegistry().unsetDevice(device.getIdentifier());
    }

    @Override
    public void onConnectionFailure(Connector connector, UlxError error) {
        Log.e(getClass().getCanonicalName(), "ULX connection failed");

        // Try again?
        connector.connect();
    }

    @Override
    public void onStateChange(Connector connector) {
    }

    @Override
    public void onOpen(Stream stream) {
        Device device = getSouthRegistry().getDeviceInstance(stream.getIdentifier());

        // Make sure the device was previously registered
        Objects.requireNonNull(device);

        // Get the streams and check their states
        InputStream inputStream = device.getTransport().getReliableChannel().getInputStream();
        OutputStream outputStream = device.getTransport().getReliableChannel().getOutputStream();

        Log.i(getClass().getCanonicalName(), String.format("ULX stream states are (Input: %s, Output: %s)",
                inputStream.getState().toString(),
                outputStream.getState().toString()
        ));

        // When both streams are open, we can proceed
        if (inputStream.getState() == Stream.State.OPEN && outputStream.getState() == Stream.State.OPEN) {
            getNetworkController().negotiate(device);
        }
    }

    @Override
    public void onClose(Stream stream, UlxError error) {
    }

    @Override
    public void onFailedOpen(Stream stream, UlxError error) {
        Log.e(getClass().getCanonicalName(), String.format("ULX bridge stream failed to open [%s]", error.toString()));
    }

    @Override
    public void onStateChange(Stream stream) {
    }

    /**
     * This method receives the given device and takes ownership of its stream
     * delegates, meaning that the I/O processes will be managed by the bridge
     * going forward. It also requests the streams to open, completing the
     * connection cycle. After this process is complete, the devices may
     * finally engage in communications.
     * @param device The device to takeover.
     */
    public void takeover(Device device) {

        // Register the device
        getSouthRegistry().setDevice(device.getIdentifier(), device);

        device.getConnector().addInvalidationCallback(this);

        // We're assuming the delegates for all I/O streams
        InputStream inputStream = device.getTransport().getReliableChannel().getInputStream();
        OutputStream outputStream = device.getTransport().getReliableChannel().getOutputStream();

        // Assume the state delegates
        inputStream.setStateDelegate(this);
        outputStream.setStateDelegate(this);

        // Assume the stream-specific delegates as well.
        // TODO I don't like that the getter for the IoController is public, but
        //  I'm not seeing how this can be set otherwise.
        //  Edit: one way that makes sense is to move the south bridge to the
        //  IoController; that is the one, after all, that manages the streams.
        inputStream.setDelegate(getNetworkController().getIoController());
        outputStream.setDelegate(getNetworkController().getIoController());

        // Open the streams
        inputStream.open();
        outputStream.open();
    }

    @Override
    public void onInvalidation(Connector connector, UlxError error) {
        // A connector has been invalidated - we need to remove the device from the registry
        final Device device = getSouthRegistry().getDeviceInstance(connector.getIdentifier());

        device.getConnector().removeInvalidationCallback(this);

        // Clear the device from the lower grade controllers
        getNetworkController().removeDevice(device);

        // Unregister the device
        getSouthRegistry().unsetDevice(device.getIdentifier());
    }

    public void send(Message message) {

        // Send the data to the network controller, which will look for proper
        // links on the routing tables
        Ticket ticket = getNetworkController().send(message.getData(), message.getDestination());

        // Associate the ticket
        setMessageInfo(ticket, message.getMessageInfo());
    }

    @Override
    public void onInstanceFound(NetworkController networkController, Instance instance) {
        NetworkDelegate networkDelegate = getNetworkDelegate();
        if (networkDelegate != null) {
            networkDelegate.onInstanceFound(this, instance);
        }
    }

    @Override
    public void onInstanceLost(NetworkController networkController, Instance instance, UlxError error) {
        NetworkDelegate networkDelegate = getNetworkDelegate();
        if (networkDelegate != null) {
            networkDelegate.onInstanceLost(this, instance, error);
        }
    }

    @Override
    public void onReceived(NetworkController networkController, byte[] data, Instance origin) {
        notifyOnMessageReceived(data, origin);
    }

    @Override
    public void onSent(NetworkController networkController, Ticket ticket) {
        notifyOnMessageSent(getMessageInfo(ticket));
    }

    @Override
    public void onSendFailure(NetworkController networkController, Ticket ticket, UlxError error) {
        notifyOnMessageSendFailure(getMessageInfo(ticket), error);

        // Send failures don't expect an acknowledgement
        clearMessageInfo(ticket);
    }

    @Override
    public void onAcknowledgement(NetworkController networkController, Ticket ticket) {
        MessageInfo messageInfo = getMessageInfo(ticket);

        assert messageInfo != null;

        notifyOnMessageDelivered(messageInfo);

        // Once the message is acknowledged, the ticket is no longer needed
        clearMessageInfo(ticket);
    }

    @Override
    public void onInternetResponse(NetworkController networkController, int code, String message) {
        Log.i(getClass().getCanonicalName(), String.format("ULX Internet response received [%d, %s]", code, message));
        notifyOnInternetResponse(code, message);
    }

    @Override
    public void onInternetRequestFailure(NetworkController networkController, int sequence) {
        Log.e(getClass().getCanonicalName(), "ULX internet request failure");
    }

    private void setMessageInfo(Ticket ticket, MessageInfo messageInfo) {
        getTickets().put(ticket, messageInfo);
    }

    private MessageInfo getMessageInfo(Ticket ticket) {
        return getTickets().get(ticket);
    }

    private void clearMessageInfo(Ticket ticket) {
        getTickets().remove(ticket);
    }

    /**
     * Propagates a notification to the {@link MessageDelegate} that the {@link
     * Message} corresponding to the given {@link MessageInfo} was successfully
     * written to the output stream.
     * @param messageInfo The {@link MessageInfo} with the meta information for
     *                    the {@link Message} that was sent.
     */
    private void notifyOnMessageSent(MessageInfo messageInfo) {
        MessageDelegate messageDelegate = getMessageDelegate();
        if (messageDelegate != null) {
            messageDelegate.onMessageSent(this, messageInfo);
        }
    }

    /**
     * Propagates a {@link MessageDelegate#onMessageSendFailure(Bridge,
     * MessageInfo, UlxError)} delegate event with the given {@link MessageInfo}
     * and {@link UlxError}.
     * @param messageInfo The {@link MessageInfo} for the message that failed.
     * @param error A {@link UlxError}, indicating the cause for the failure.
     */
    private void notifyOnMessageSendFailure(MessageInfo messageInfo, UlxError error) {
        MessageDelegate messageDelegate = getMessageDelegate();
        if (messageDelegate != null) {
            messageDelegate.onMessageSendFailure(this, messageInfo, error);
        }
    }

    /**
     * Propagates a notification to the {@link MessageDelegate} that the given
     * data payload has been received.
     * @param data The payload that was received.
     * @param origin The {@link Instance} that sent the message.
     */
    private void notifyOnMessageReceived(byte[] data, Instance origin) {
        MessageDelegate messageDelegate = getMessageDelegate();
        if (messageDelegate != null) {
            messageDelegate.onMessageReceived(this, data, origin);
        }
    }

    /**
     * Propagates a notification to the {@link MessageDelegate} giving 
     * indication that a {@link Message} was acknowledged by the destination.
     * This corresponds to calling {@link MessageDelegate#onMessageDelivered(Bridge, MessageInfo)}
     * on the {@link MessageDelegate}.
     * @param messageInfo The {@link MessageInfo} corresponding to the {@link
     *                    Message} that was acknowledged.
     */
    private void notifyOnMessageDelivered(MessageInfo messageInfo) {
        MessageDelegate messageDelegate = getMessageDelegate();
        if (messageDelegate != null) {
            messageDelegate.onMessageDelivered(this, messageInfo);
        }
    }

    private void notifyOnInternetResponse(int code, String content) {
        MessageDelegate messageDelegate = getMessageDelegate();
        if (messageDelegate != null) {
            messageDelegate.onInternetResponse(this, code, content);
        }
    }

    public void sendInternet(URL url, JSONObject jsonObject, int test) {
        getNetworkController().sendInternet(url, jsonObject, test);
    }
}
