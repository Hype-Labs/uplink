package com.uplink.ulx.service;

import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import com.uplink.ulx.UlxError;
import com.uplink.ulx.bridge.Bridge;
import com.uplink.ulx.drivers.controller.Driver;
import com.uplink.ulx.drivers.controller.DriverManager;
import com.uplink.ulx.drivers.model.Device;
import com.uplink.ulx.model.Instance;
import com.uplink.ulx.model.Message;
import com.uplink.ulx.model.MessageInfo;
import com.uplink.ulx.model.State;

import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.UUID;

import timber.log.Timber;

/**
 * This is an extension of an Android Service that is used to run the SDK as a
 * background service. It serves the purpose of keeping the SDK running as long
 * lived component, while keeping the app threads clear of additional work. The
 * implementation components will communicate with this service through means
 * of RPC calls, and the Service will notify those components back through
 * means of a delegate.
 */
public class Service extends android.app.Service implements
        Driver.StateDelegate,
        Driver.NetworkDelegate,
        Bridge.StateDelegate,
        Bridge.NetworkDelegate,
        Bridge.MessageDelegate
{
    /**
     * The Service Delegate receives notifications from the background service
     * indicating activity on the SDK. This will include activity for all sorts
     * of state changes on the SDK, ranging from lifecycle, device discovery,
     * and I/O, since all of those run on the background service.
     */
    public interface StateDelegate {

        /**
         * This method is called by the Service once the background service has
         * been initialized. This means that the service is running, connected,
         * and ready to accept requests, such as starting the SDK.
         * @param service The Service instance.
         * @param hostInstance The Instance that was created for the host device.
         */
        void onInitialization(Service service, Instance hostInstance);

        /**
         * This delegate method is called once the SDK is known to have
         * transited to a State.RUNNING state. This means that at least one of
         * the drivers has been started, and that the SDK is somehow
         * participating on the network.
         * @param service The Service instance.
         */
        void onStart(Service service);

        /**
         * This delegate method is called on the SDK is known to have transited
         * to State.IDLE state. This means that all drivers have stopped, either
         * because their requested or forced to do so. If case of a forced
         * stoppage, the error parameter will give indication as to what is a
         * likely cause for the stoppage. It's notable that the SDK may still
         * stop abruptly even when requested to stop; that is, this method may
         * still receive an error even if the SDK is in a State.STOPPING state.
         * @param service The Service instance.
         * @param error An error, in case the stoppage is forced.
         */
        void onStop(Service service, UlxError error);

        /**
         * This delegate callback gives indication that a start request failed
         * to complete. At this point, the SDK will be in a State.IDLE state,
         * indicating that it's not participating on the network. This method
         * will always get an error parameter, indicating a probable cause for
         * the failure.
         * @param service The Service instance.
         * @param error An error, indicating a probable cause for the failure.
         */
        void onFailedStart(Service service, UlxError error);

        /**
         * This delegate callback is called after the SDK has been forced to
         * stop or after failing to start, while the underlying causes for
         * failure have been identified to maybe having been resolved. This
         * means that attempting to start the SDK is somewhat more likely to
         * succeed, although no guarantees exist. This will most likely result
         * from an adapter being turned on after it having been off, in which
         * case the implementation will identify the possibility of a recovery.
         * When that happens, the implementation will no longer listen to
         * adapter state change events, meaning that this notification is only
         * triggered once. It's up to the delegate to decide whether to ask
         * the SDK start again.
         * @param service The Service instance.
         */
        void onReady(Service service);

        /**
         * This delegate notification is triggered by any state change on the
         * SDK's lifecycle. Although this method can be used to track the SDK's
         * changes in state, the more state-specific methods are often preferred,
         * since those will provide the necessary error information when needed.
         * @param service The Service instance.
         */
        default void onStateChange(Service service) {
        }
    }

    /**
     * The NetworkDelegate gets notifications for network events coming from
     * the service, such as instances being found and lost on the network.
     */
    public interface NetworkDelegate {

        /**
         * A new instance has been found on the network. This differs from
         * "finding a device" in the sense that instances may correspond to
         * a device over multiple transports, while a Device corresponds to
         * only a single transport. For example, a device being found over
         * Bluetooth LE corresponds to a device; if the device is also found
         * over Infrastructure WiFi, it will be a different instance, but
         * both will be encapsulated under the same Instance.
         * @param service The Service issuing the notification.
         * @param instance The instance that was found.
         */
        void onInstanceFound(Service service, Instance instance);

        /**
         * When this delegate method is called, the given instance cannot be
         * reached over any type of transport. This means that the last
         * transport to be aware of it also lost it, and thus the instance
         * is not reachable in any way.
         * @param service The Service issuing the notification.
         * @param instance The instance that was lost.
         * @param error An error, providing an explanation for the loss.
         */
        void onInstanceLost(Service service, Instance instance, UlxError error);
    }

    public interface MessageDelegate {

        void onMessageSent(Service service, MessageInfo messageInfo);
        void onMessageSendFailed(Service service, MessageInfo messageInfo, UlxError error);

        /**
         * This delegate notification is called when a message is received. The
         * message might not have been acknowledged to the originator yet.
         * @param service The {@link Service} issuing the notification.
         * @param data The data that was received.
         * @param origin The originating {@link Instance}.
         */
        void onMessageReceived(Service service, byte[] data, Instance origin);

        /**
         * This {@link MessageDelegate} notification is triggered when a {@link
         * Message} is known to have been acknowledged by its destination {@link
         * Instance}. This means that the {@link Message} was received and
         * successfully processed by the destination, and that an
         * acknowledgement was sent back to indicate just that. The {@link
         * Message} is represented by its {@link MessageInfo} details, since
         * the payload is no longer known at this point.
         * @param service The {@link Service} issuing the notification.
         * @param messageInfo The {@link MessageInfo} corresponding to the
         *                    {@link Message} that was delivered.
         */
        void onMessageDelivered(Service service, MessageInfo messageInfo);

        /**
         * This {@link MessageDelegate} notification is triggered when an
         * Internet response is received for the host device. This could
         * originate either from a direct call to the server or from a mesh
         * relay.
         * @param service The {@link Service}.
         * @param code The HTTP status code.
         * @param content The server response content.
         */
        void onInternetResponse(Service service, int code, String content);

        /**
         * This {@link MessageDelegate} is called when an Internet request
         * cannot be made. This must be distinguished from a server error
         * response, in the sense that would constitute a successful request
         * with an error response. This method, on the other hand, indicates
         * that the request was not performed at all. This could happen if
         * the implementation is not connected to any devices with reachable
         * Internet, for example.
         * @param service The {@link Service}.
         * @param error An error, indicating an estimation for the cause of
         *              failure.
         */
        void onInternetRequestFailure(Service service, UlxError error);
    }

    /**
     * This is service Binder, specifically for the Service calls, that is used
     * for the RPC mechanism used by the service to communicate with the app.
     */
    public class LocalBinder extends Binder {

        /**
         * Returns the Service instance associated with the Binder. This is
         * used by classes getting Service-related event notifications to refer
         * by to the service, when the Binder is passed as a reference.
         * @return The Service corresponding to the Binder instance.
         */
        public Service getService() {
            return Service.this;
        }
    }

    private Context context;
    private final IBinder binder;
    private DriverManager driverManager;
    private WeakReference<StateDelegate> stateDelegate;
    private WeakReference<NetworkDelegate> networkDelegate;
    private WeakReference<MessageDelegate> messageDelegate;

    /**
     * Sole constructor, initializes some variables.
     */
    public Service() {

        this.stateDelegate = null;
        this.networkDelegate = null;
        this.messageDelegate = null;

        this.binder = new LocalBinder();
    }

    /**
     * Sets the state delegate for the service, which will get notifications
     * from it. This delegate is not initialized at construction time because
     * of the way in which services are instantiated, so it must be set after
     * that.
     * @param stateDelegate The delegate to set.
     */
    public final void setStateDelegate(StateDelegate stateDelegate) {
        this.stateDelegate = new WeakReference<>(stateDelegate);
    }

    /**
     * Returns the state delegate for the service. A strong reference is
     * returned if the delegate has been set; if not, this method returns
     * null.
     * @return The service's delegate.
     */
    private StateDelegate getStateDelegate() {
        return this.stateDelegate != null ? this.stateDelegate.get() : null;
    }

    /**
     * Sets the network delegate ({@link NetworkDelegate}) that will get
     * notifications from the service.
     * @param networkDelegate The {@link NetworkDelegate} to set.
     */
    public final void setNetworkDelegate(NetworkDelegate networkDelegate) {
        this.networkDelegate = new WeakReference<>(networkDelegate);
    }

    /**
     * Returns the network delegate ({@link NetworkDelegate}) that will get
     * notifications from the service.
     * @return The network delegate ({@link NetworkDelegate}).
     */
    private NetworkDelegate getNetworkDelegate() {
        return this.networkDelegate != null ? this.networkDelegate.get() : null;
    }

    /**
     * Sets the message delegate ({@link MessageDelegate}) that will get
     * notifications from the service.
     * @param messageDelegate The {@link MessageDelegate} to set.
     */
    public final void setMessageDelegate(MessageDelegate messageDelegate) {
        this.messageDelegate = new WeakReference<>(messageDelegate);
    }

    /**
     * Returns the message delegate ({@link MessageDelegate}) that will get
     * notifications from the service.
     * @return The {@link MessageDelegate} or null, if none is set.
     */
    private MessageDelegate getMessageDelegate() {
        return this.messageDelegate != null ? this.messageDelegate.get() : null;
    }

    /**
     * Sets the Android application context that the service will use.
     * @param context The Android application context to set.
     */
    public final void setContext(Context context) {
        this.context = context;
    }

    /**
     * Returns the Android application context that was previously set or raises
     * an exception if it wasn't set before.
     * @return The Android application context.
     */
    public final Context getContext() {
        if (this.context == null) {
            throw new RuntimeException("The Android application context was not set on the service");
        }
        return this.context;
    }

    /**
     * Returns the driver manager or initializes one if one was not initialized
     * before. The driver manager is initialized with a random UUID, for debug
     * purposes.
     * @return The driver manager.
     * @see DriverManager
     */
    private DriverManager getDriverManager() {
        if (this.driverManager == null) {
            this.driverManager = new DriverManager(
                    UUID.randomUUID().toString(),
                    this,
                    this,
                    getContext()
            );
        }
        return this.driverManager;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Timber.i("ULX binding service");
        return this.binder;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {

        // Destroy everything and stop the service
        getDriverManager().destroy();
        stopSelf();
    }

    /**
     * Getter for the framework state according to the service, which is a
     * direct correspondence with the driver manager's state.
     * @return The framework's state.
     */
    public State getState() {
        return State.fromInt(getDriverManager().getState().getValue());
    }

    /**
     * Requests the driver manager to start, which will in turn propagate the
     * call to all underlying drivers.
     */
    public void start() {
        getDriverManager().start();
    }

    /**
     * Requests the driver manager to stop, which will in turn propagate the
     * call to all underlying drivers.
     */
    public void stop() {
        getDriverManager().stop();
    }

    /**
     * Initializes the framework with the given app identifier. This consists
     * of creating the necessary memory structure and identifiers that the
     * framework needs to run. In case of success, the service will be notified
     * through a delegate call.
     * @param appIdentifier The app identifier for the SDK instance.
     */
    public void initialize(String appIdentifier) {
        Bridge.getInstance().setContext(getContext());
        Bridge.getInstance().setStateDelegate(this);
        Bridge.getInstance().setNetworkDelegate(this);
        Bridge.getInstance().setMessageDelegate(this);
        Bridge.getInstance().initialize(appIdentifier);
    }

    @Override
    public void onInitialization(Bridge bridge, Instance hostInstance) {
        StateDelegate stateDelegate = getStateDelegate();
        if (stateDelegate != null) {
            stateDelegate.onInitialization(this, hostInstance);
        }
    }

    @Override
    public void onStart(Driver driver) {
        StateDelegate stateDelegate = getStateDelegate();
        if (stateDelegate != null) {
            stateDelegate.onStart(this);
        }
    }

    @Override
    public void onStop(Driver driver, UlxError error) {
        StateDelegate stateDelegate = getStateDelegate();
        if (stateDelegate != null) {
            stateDelegate.onStop(this, error);
        }
    }

    @Override
    public void onFailedStart(Driver driver, UlxError error) {
        StateDelegate stateDelegate = getStateDelegate();
        if (stateDelegate != null) {
            stateDelegate.onFailedStart(this, error);
        }
    }

    @Override
    public void onReady(Driver driver) {
        StateDelegate stateDelegate = getStateDelegate();
        if (stateDelegate != null) {
            stateDelegate.onReady(this);
        }
    }

    @Override
    public void onStateChange(Driver driver) {
        StateDelegate stateDelegate = getStateDelegate();
        if (stateDelegate != null) {
            stateDelegate.onStateChange(this);
        }
    }

    @Override
    public void onDeviceFound(Driver driver, Device device) {
        Timber.i("ULX found device %s", device.getIdentifier());

        // Register the device with the bridge
        Bridge.getInstance().takeover(device);
    }

    @Override
    public void onDeviceLost(Driver driver, Device device, UlxError error) {
        Timber.i("ULX lost device %s", device.getIdentifier());
    }

    @Override
    public void onInstanceFound(Bridge bridge, Instance instance) {
        NetworkDelegate networkDelegate = getNetworkDelegate();
        if (networkDelegate != null) {
            networkDelegate.onInstanceFound(this, instance);
        }
    }

    @Override
    public void onInstanceLost(Bridge bridge, Instance instance, UlxError error) {
        NetworkDelegate networkDelegate = getNetworkDelegate();
        if (networkDelegate != null) {
            networkDelegate.onInstanceLost(this, instance, error);
        }
    }

    public void send(Message message) {
        //pause();
        Bridge.getInstance().send(message);
    }

    @Override
    public void onMessageSent(Bridge bridge, MessageInfo messageInfo) {
        //resume();
        MessageDelegate messageDelegate = getMessageDelegate();
        if (messageDelegate != null) {
            messageDelegate.onMessageSent(this, messageInfo);
        }
    }

    @Override
    public void onMessageSendFailure(Bridge bridge, MessageInfo messageInfo, UlxError error) {
        //resume();
        MessageDelegate messageDelegate = getMessageDelegate();
        if (messageDelegate != null) {
            messageDelegate.onMessageSendFailed(this, messageInfo, error);
        }
    }

    @Override
    public void onMessageDelivered(Bridge bridge, MessageInfo messageInfo) {
        MessageDelegate messageDelegate = getMessageDelegate();
        if (messageDelegate != null) {
            messageDelegate.onMessageDelivered(this, messageInfo);
        }
    }

    @Override
    public void onMessageReceived(Bridge bridge, byte[] data, Instance origin) {
        MessageDelegate messageDelegate = getMessageDelegate();
        if (messageDelegate != null) {
            messageDelegate.onMessageReceived(this, data, origin);
        }
    }

    @Override
    public void onInternetResponse(Bridge bridge, int code, String content) {
        MessageDelegate messageDelegate = getMessageDelegate();
        if (messageDelegate != null) {
            messageDelegate.onInternetResponse(this, code, content);
        }
    }

    @Override
    public void onInternetRequestFailure(Bridge bridge, UlxError error) {
        MessageDelegate messageDelegate = getMessageDelegate();
        if (messageDelegate != null) {
            messageDelegate.onInternetRequestFailure(this, error);
        }
    }

    /**
     * Attempts to send a message over the Internet, relying on the mesh if the
     * host device is not connected to an Internet exit point at the moment. The
     * message will be sent to the given {@code URL} as JSON, by setting the
     * HTTP Content-Type header to {@code application/json}. The reply is
     * expected from a delegate.
     * @param url The destination {@code URL}.
     * @param jsonObject The data to send, encoded as JSON.
     */
    public void sendInternet(URL url, JSONObject jsonObject, int test) {
        Bridge.getInstance().sendInternet(url, jsonObject, test);
    }
}
