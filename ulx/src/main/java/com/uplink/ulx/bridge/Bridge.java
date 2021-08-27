package com.uplink.ulx.bridge;

import android.icu.util.Output;
import android.util.Log;

import com.uplink.ulx.UlxError;
import com.uplink.ulx.drivers.model.Connector;
import com.uplink.ulx.drivers.model.Device;
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

    private static Bridge instance = null;

    private WeakReference<StateDelegate> stateDelegate;
    private WeakReference<NetworkDelegate> networkDelegate;

    /**
     * Private constructor prevents instantiation.
     */
    private Bridge() {
        this.stateDelegate = null;
        this.networkDelegate = null;
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
    }

    @Override
    public void hasSpaceAvailable(OutputStream outputStream) {
    }

    @Override
    public void onOpen(Stream stream) {
        Log.i(getClass().getCanonicalName(), "ULX stream is now open");
    }

    @Override
    public void onClose(Stream stream, UlxError error) {
    }

    @Override
    public void onFailedOpen(Stream stream, UlxError error) {
        Log.e(getClass().getCanonicalName(), String.format("ULX stream failed to open [%s]", error.toString()));
    }

    @Override
    public void onStateChange(Stream stream) {
    }

    public void addDevice(Device device) {

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
     * This is a temporary data structure that is currently being managed by
     * this bridge, but that in the future will be implemented by the native
     * bridge. This happens in this way because the current version does not
     * yet implement the native JNI bridge. When it does, the native
     * implementation will be the one managing Device-to-Instance mappings.
     * This is also simplified over the fact that the implementation currently
     * only supports BLE.
     */
    private HashMap<Device, Instance> instanceRegistry;

    private HashMap<Device, Instance> getInstanceRegistry() {
        if (this.instanceRegistry == null) {
            this.instanceRegistry = new HashMap<>();
        }
        return this.instanceRegistry;
    }
/*
    public void addDevice(Device device) {

        byte[] identifier = ByteUtils.uuidToBytes(UUID.fromString(device.getIdentifier()));
        Instance instance = new Instance(identifier);

        // Associate
        getInstanceRegistry().put(device, instance);



        /*
        NetworkDelegate networkDelegate = getNetworkDelegate();
        if (networkDelegate != null) {
            networkDelegate.onInstanceFound(this, instance);
        }
         *
    }
*/
    public Message send(int messageId, byte[] data, Instance instance, boolean acknowledge) {

        Message message = new Message(new MessageInfo(messageId), data);

        ExecutorPool.getCoreExecutor().execute(
                () -> sendData(messageId, data, instance.getIdentifier(), acknowledge)
        );

        return message;
    }

    public void sendData(int messageId, byte[] data, byte[] instance, boolean acknowledge) {
    }
}
