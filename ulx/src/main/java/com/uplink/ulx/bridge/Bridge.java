package com.uplink.ulx.bridge;

import android.bluetooth.BluetoothDevice;
import android.renderscript.ScriptGroup;
import android.util.Log;

import com.uplink.ulx.UlxError;
import com.uplink.ulx.UlxErrorCode;
import com.uplink.ulx.drivers.commons.model.Buffer;
import com.uplink.ulx.drivers.model.Connector;
import com.uplink.ulx.drivers.model.Device;
import com.uplink.ulx.drivers.model.IOResult;
import com.uplink.ulx.drivers.model.InputStream;
import com.uplink.ulx.drivers.model.OutputStream;
import com.uplink.ulx.drivers.model.Stream;
import com.uplink.ulx.model.Instance;
import com.uplink.ulx.model.Message;
import com.uplink.ulx.model.MessageInfo;
import com.uplink.ulx.threading.ExecutorPool;
import com.uplink.ulx.utils.ByteUtils;
import com.uplink.ulx.utils.StringUtils;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * This bridge is the main point of communication between the Java service and
 * the JNI implementation, when one exists. It implements some of the logic
 * regarding service initialization, but its main responsibility is to bridge
 * calls between the Java abstraction and the native implementation. This class
 * will issue calls to the Core and return responses in the form of a delegate.
 * This also means that the bridge must handle the thread context changes that
 * occur between those processes.
 */
public class Bridge implements
        Connector.StateDelegate,
        InputStream.Delegate,
        OutputStream.Delegate,
        Stream.StateDelegate
{
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
        void onMessageSendFailed(Bridge bridge, MessageInfo messageInfo, UlxError error);

        /**
         * This delegate notification is called when a message is received. The
         * given message contains the payload and the {@link MessageInfo}
         * metadata corresponding to that message. The message might not have
         * been acknowledged to the originator yet.
         * @param bridge The {@link Bridge} issuing the notification.
         * @param message The {@link Message} received.
         */
        void onMessageReceived(Bridge bridge, Message message);
    }

    private static Bridge instance = null;

    private WeakReference<StateDelegate> stateDelegate;
    private WeakReference<NetworkDelegate> networkDelegate;
    private WeakReference<MessageDelegate> messageDelegate;

    // The North bridge registry is a temporary data structure that will be
    // removed once routing tables are in place. When that happens, the south
    // bridge registry should be renamed to just "registry".
    private Registry<Instance> northRegistry;
    private Registry<BluetoothDevice> southRegistry;

    // The message queue is also a temporary data structure. Currently, the
    // implementation sends a single message at a time, and thus the full
    // delivery of the queued contents will mean that the first message in
    // the queue has been delivered. Future versions will be more complex,
    // and handle several messages at a time.
    private Queue<Message> messageQueue;
    private Queue<MessageInfo> messageInfoQueue;

    // This is another temporary data structure that is used to hold the
    // buffered data from input streams. It will be held until the data is
    // successfully read and parsed.
    private HashMap<String, Buffer> inputMap;

    /**
     * Private constructor prevents instantiation.
     */
    private Bridge() {
        this.stateDelegate = null;
        this.networkDelegate = null;
        this.messageDelegate = null;

        this.northRegistry = null;
        this.southRegistry = null;

        this.messageQueue = null;
        this.messageInfoQueue = null;

        this.inputMap = null;
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
     * This is getter for a temporary data structure that maps {@link Instance}
     * with {@link Device} in direct link for the North bridge. The reason this
     * is temporary is because this type of association will not make sense
     * once the routing tables are in place, since instances can be reached
     * through indirect links. This will be used in the beginning to test some
     * basic I/O, but should be removed in future versions.
     * @return The North bridge registry.
     */
    private Registry<Instance> getNorthRegistry() {
        if (this.northRegistry == null) {
            this.northRegistry = new Registry<>();
        }
        return this.northRegistry;
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
     * Returns the message queue, which is used to queue messages that are
     * waiting to be dispatched by the device.
     * @return The message queue.
     */
    private Queue<Message> getMessageQueue() {
        if (this.messageQueue == null) {
            this.messageQueue = new Queue<>();
        }
        return this.messageQueue;
    }

    /**
     * Returns the message info queue, which is used to queue message metadata
     * for messages that are pending replies, such as indications of success
     * or failure.
     * @return The message info queue.
     */
    private Queue<MessageInfo> getMessageInfoQueue() {
        if (this.messageInfoQueue == null) {
            this.messageInfoQueue = new Queue<>();
        }
        return this.messageInfoQueue;
    }

    /**
     * Returns the input map, which is used to hold information with respect to
     * input that is being read from the input streams. The streams are
     * identified by the {@code String} key of the map. This data will be held
     * until a full packet can be processed.
     * @return The input map.
     */
    private HashMap<String, Buffer> getInputMap() {
        if (this.inputMap == null) {
            this.inputMap = new HashMap<>();
        }
        return this.inputMap;
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

            // Generate an identifier for the host instance
            byte[] hostIdentifier = generateIdentifier(appIdentifier);
            Instance hostInstance = new Instance(hostIdentifier);

            // If the Bridge was deallocated in the meanwhile, do nothing.
            if (strongSelf != null) {
                strongSelf.getStateDelegate().onInitialization(strongSelf, hostInstance);
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
    }

    @Override
    public void onConnectionFailure(Connector connector, UlxError error) {
    }

    @Override
    public void onStateChange(Connector connector) {
    }

    @Override
    public void hasDataAvailable(InputStream inputStream) {
        Log.i(getClass().getCanonicalName(), "ULX input stream has data available");

        IOResult result;

        // This is the buffer that will receive the data
        Buffer buffer = getBufferForStream(inputStream);

        Message message;

        synchronized (buffer.getLock()) {

            // We're allocating 1024 bytes, being that double the maximum we'd
            // ever need; but this is a temporary allocation.
            byte[] aux = new byte[1024];

            do {

                // Read from the stream
                result = inputStream.read(aux);

                Log.i(getClass().getCanonicalName(), String.format("ULX input stream read %d bytes", result.getByteCount()));

                // Append to the buffer, the amount of bytes read
                buffer.append(aux, result.getByteCount());

            } while (result.getByteCount() > 0);

            // Proceed by parsing messages from the input
            message = attemptParse(buffer);

            if (message == null) {
                return;
            }

            Log.i(getClass().getCanonicalName(), String.format("ULX got message [%d] from (null)", message.getIdentifier()));

            // Reduce the buffer by the amount parsed
            buffer.trim(message.getData().length);
        }

        MessageDelegate messageDelegate = getMessageDelegate();
        if (messageDelegate != null) {
            messageDelegate.onMessageReceived(this, message);
        }
    }

    private Buffer getBufferForStream(InputStream inputStream) {

        Buffer buffer = getInputMap().get(inputStream.getIdentifier());

        // Allocate a buffer, if needed
        if (buffer == null) {
            getInputMap().put(inputStream.getIdentifier(), buffer = new Buffer(0));
        }

        return buffer;
    }

    private Message attemptParse(Buffer buffer) {
        Log.i(getClass().getCanonicalName(), "ULX is attempting to parse a message");

        // For now, we're just discarding
        synchronized (buffer.getLock()) {

            Message message;

            synchronized (buffer.getLock()) {
                message = Encoder.decode(buffer.getData());
            }

            // If no message can be parsed, don't proceed
            if (message == null) {
                Log.i(getClass().getCanonicalName(), String.format("ULX input stream could not parse message from buffer with %d; waiting for more data", buffer.getOccupiedByteCount()));
                return null;
            }

            Log.i(getClass().getCanonicalName(), String.format("ULX parsed a message, clearing %d from %d bytes from the input buffer", 4 + message.getData().length, buffer.getOccupiedByteCount()));

            // Reduce the buffer by the amount parsed
            // The "4" being added is the reserved bytes for size. In the final
            // version, with the protocol in place, this will not be a magical
            // constant, but instead we'll have a more complex parser for the
            // headers
            synchronized (buffer.getLock()) {
                buffer.trim(message.getData().length + 4);
            }

            return message;
        }
    }

    @Override
    public void hasSpaceAvailable(OutputStream outputStream) {
        Log.i(getClass().getCanonicalName(), "ULX output stream has space available");

        // The stream declaring space available should not be a synonym of a
        // message being dispatched, but the current implementation dispatches
        // a single message at a time. This is temporary implementation of the
        // Bridge, so it will work for now.
        handleMessageSent();

        // Dispatch more, while we have them
        while (!getMessageQueue().isEmpty()) {
            if (dequeueMessage()) {
                break;
            }
        }
    }

    /**
     * Removes a {@link MessageInfo} from the message info queue and declares
     * it as sent. The current implementation only dispatches a single message
     * at a time, so this will correspond to the message for the data that was
     * just written.
     */
    private void handleMessageSent() {

        // The MessageInfo is removed from the queue
        MessageInfo messageInfo = getMessageInfoQueue().dequeue();

        // Propagate a notification for dispatched messages. This will be null
        // when the stream is first opened, in which case we're not flagging
        // any message has having been sent.
        if (messageInfo != null) {
            notifyMessageSent(messageInfo);
        }
    }

    /**
     * Propagates a notification to the {@link MessageDelegate} that the {@link
     * Message} corresponding to the given {@link MessageInfo} was successfully
     * written to the output stream.
     * @param messageInfo The {@link MessageInfo} with the meta information for
     *                    the {@link Message} that was sent.
     */
    private void notifyMessageSent(MessageInfo messageInfo) {
        MessageDelegate messageDelegate = getMessageDelegate();
        if (messageDelegate != null) {
            messageDelegate.onMessageSent(this, messageInfo);
        }
    }

    @Override
    public void onOpen(Stream stream) {
        Log.i(getClass().getCanonicalName(), "ULX bridge stream is now open");

        Device device = getSouthRegistry().getDeviceInstance(stream.getIdentifier());

        // Make sure the device was previously registered
        Objects.requireNonNull(device);

        // Get the streams and check their states
        InputStream inputStream = device.getTransport().getReliableChannel().getInputStream();
        OutputStream outputStream = device.getTransport().getReliableChannel().getOutputStream();

        Log.i(getClass().getCanonicalName(), String.format("ULX bridge stream states (Input: %s, Output: %s)",
                inputStream.getState().toString(),
                outputStream.getState().toString()
        ));

        // When both streams are open, we can proceed with the creation of the
        // instance, which will map the given device.
        if (inputStream.getState() == Stream.State.OPEN && outputStream.getState() == Stream.State.OPEN) {
            associateInstance(device);
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

        // We're assuming the delegates for all I/O streams
        InputStream inputStream = device.getTransport().getReliableChannel().getInputStream();
        OutputStream outputStream = device.getTransport().getReliableChannel().getOutputStream();

        // Assume the state delegates
        inputStream.setStateDelegate(this);
        outputStream.setStateDelegate(this);

        // Assume the stream-specific delegates as well
        inputStream.setDelegate(this);
        outputStream.setDelegate(this);

        // Open the streams
        inputStream.open();
        outputStream.open();
    }

    /**
     * Creates a new Instance and associates with the given Device. This method
     * is a simplification of what it should be, since it currently only
     * supports a single transport and doesn't care about extending into other
     * types of transport. The given device will be mapped with an instance
     * created with the same identifier, which is also not how the protocol is
     * actually designed. Instead, this new identifier should be negotiated,
     * implying already some sort of I/O. This implementation will not negotiate
     * and instead just open the streams and given the Instance as ready for I/O.
     * @param device The {@code Device} to register.
     */
    private void associateInstance(Device device) {

        // TODO this method is temporary as well; once the routing tables are
        //      in place this should be refactored to work the them instead.

        byte[] identifier = ByteUtils.uuidToBytes(UUID.fromString(device.getIdentifier()));
        Instance instance = new Instance(identifier);

        // Associate the device with the newly created instance
        getNorthRegistry().setDevice(device.getIdentifier(), device);
        getNorthRegistry().setGeneric(instance.getStringIdentifier(), instance);
        getNorthRegistry().associate(instance.getStringIdentifier(), device.getIdentifier());

        // Notify the delegate of a newly found instance. Future versions might
        // skip this step is the instance had already been found before over
        // another type of transport.
        NetworkDelegate networkDelegate = getNetworkDelegate();
        if (networkDelegate != null) {
            networkDelegate.onInstanceFound(this, instance);
        }
    }

    public void send(Message message) {

        Log.i(getClass().getCanonicalName(), String.format("ULX queueing message [%d] to destination %s", message.getIdentifier(), message.getDestination().getStringIdentifier()));

        // Queue the message
        getMessageQueue().queue(message);

        // Attempt to dispatch
        dequeueMessage();
    }

    private void send(Message message, Device device) {

        byte[] data = Encoder.encode(message);

        // Send
        device.getTransport().getReliableChannel().getOutputStream().write(data);
    }

    private boolean dequeueMessage() {

        Message message = getMessageQueue().dequeue();

        // Nothing to do; wait for more input
        if (message == null) {
            return false;
        }

        Log.i(getClass().getCanonicalName(), String.format("ULX dequeued message [%d] to destination %s", message.getIdentifier(), message.getDestination().getStringIdentifier()));

        // The message gets replaced with the message info structure, which is
        // lighter because it does not include the payload
        getMessageInfoQueue().queue(message.getMessageInfo());

        // Get the devices associated with the destination
        String destinationIdentifier = message.getDestination().getStringIdentifier();
        List<Device> devices = getNorthRegistry().getDevicesFromGenericIdentifier(destinationIdentifier);

        // The instance is not mapped to any known devices
        if (devices.size() == 0) {

            UlxError error = new UlxError(
                    UlxErrorCode.NOT_CONNECTED,
                    "Could not send a message to the given destination.",
                    "The destination is not known or reachable.",
                    "Try coming in close range with the destination or " +
                            "making sure that the connection has been established " +
                            "before attempting to send content."
            );

            notifyFailedSend(message.getMessageInfo(), error);

            // The message was not sent, but it was dequeued
            return true;
        }

        // If more than one device is mapped, something went wrong
        if (devices.size() != 1) {
            throw new RuntimeException("An unexpected amount of devices is mapped " +
                    "to the same instance. This is not expected because the " +
                    "current version of the SDK supports only a single transport " +
                    "and is not using routing tables.");
        }

        // Proceed
        send(message, devices.get(0));

        return true;
    }

    /**
     * Propagates a notification for a failed message, when attempting to send
     * one. This means that the message did not leave the device, in part or in
     * full.
     * @param messageInfo The {@link MessageInfo} corresponding to the failed
     *                    {@link Message}.
     * @param error An error, indicating the probable cause for the failure.
     */
    private void notifyFailedSend(MessageInfo messageInfo, UlxError error) {
        MessageDelegate messageDelegate = getMessageDelegate();
        if (messageDelegate != null) {
            messageDelegate.onMessageSendFailed(this, messageInfo, error);
        }
    }
}
