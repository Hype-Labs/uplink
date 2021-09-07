package com.uplink.ulx;

import android.content.Context;

import com.uplink.ulx.model.Instance;
import com.uplink.ulx.model.Message;
import com.uplink.ulx.model.State;
import com.uplink.ulx.observers.MessageObserver;
import com.uplink.ulx.observers.NetworkObserver;
import com.uplink.ulx.observers.StateObserver;

/**
 * This class is the main entry point for the SDK. It provides facade access to
 * the service running on the background. This class wraps the domestic instance
 * created for the host device. Each app can only create a single instance,
 * which is why this class is a singleton. This class allows users to listen to
 * events on the created instance by subscribing observers, as well as starting
 * and stopping the services.
 */
public class ULX {

    /**
     * Private constructor prevents instantiation.
     */
    private ULX() {
    }

    /**
     * Getter for the framework's state. The state the framework is in indicates
     * what activities it's performing. For instance, if the framework's state
     * is State.Running, then the device is known to be discoverable by others.
     * @return The framework's state.
     */
    public static State getState() {
        return Implementation.getInstance().getState();
    }

    /**
     * This getter returns the Instance object associated with the host
     * instance, created on the host device. This returns null until the
     * framework is first requested to start. At that point, the appIdentifier
     * is queried from the previously set value in order to create an Instance.
     * This object is then kept throughout the framework's lifecycle.
     * @return The framework's local instance.
     */
    public static Instance getHostInstance() {
        return Implementation.getInstance().getHostInstance();
    }

    /**
     * The SDK needs the app's context in order to perform some activities. This
     * setter must be called before starting the services, or an exception will
     * be raised. It only allows setting the context once. This method is thread
     * safe and in case of several concurrent threads attempting to set a
     * context, only the first to get a lock will succeed. All others will be
     * no-ops. The context persists for the duration of the singleton's instance.
     * @param context The context to set.
     */
    public static void setContext(Context context) {
        Implementation.getInstance().setContext(context);
    }

    /**
     * App identifiers are used to uniquely distinguish between apps cooperating
     * on the same network. This value must be an 8-characters long hexadecimal
     * string, and must be set before any activity is requested from the SDK,
     * otherwise resulting in an exception. Also, it can only be set once.
     * @param appIdentifier The app identifier to set.
     */
    public static void setAppIdentifier(String appIdentifier) {
        Implementation.getInstance().setAppIdentifier(appIdentifier);
    }

    /**
     * Getter for the app identifier that was previously set with
     * setAppIdentifier(String). This is the same app identifier that the
     * framework will be using to uniquely identify apps on the network.
     * @return The app identifier.
     */
    public static String getAppIdentifier() {
        return Implementation.getInstance().getAppIdentifier();
    }

    /**
     * Adds a state observer. State observers get notifications for state and
     * lifecycle events. If the observer has already been registered it will
     * not be registered twice, preventing the observer from getting duplicate
     * notifications.
     * @param stateObserver The state observer (StateObserver) to register.
     */
    public static void addStateObserver(StateObserver stateObserver) {
        Implementation.getInstance().addStateObserver(stateObserver);
    }

    /**
     * Removes a previously registered state observer (StateObserver). If the
     * observer is not present on the registry, because it was not added or
     * because it has already been removed, this method does nothing. After
     * being removed, the observer will no longer get notifications.
     * @param stateObserver The state observer (StateObserver) to remove.
     */
    public static void removeStateObserver(StateObserver stateObserver) {
        Implementation.getInstance().removeStateObserver(stateObserver);
    }

    /**
     * Adds a network observer. Network observers get notifications for network
     * events, such as instances being found and lost on the network. If the
     * observer has already been registered it will not be registered twice,
     * preventing the observer from getting duplicate notifications.
     * @param networkObserver The network observer ({@code NetworkObserver})
     *                        to register.
     */
    public static void addNetworkObserver(NetworkObserver networkObserver) {
        Implementation.getInstance().addNetworkObserver(networkObserver);
    }

    /**
     * Removes a previously registered network observer ({@code NetworkObserver}).
     * If the observer is not present on the registry, because it was not added
     * or because it has already been removed, this method will do nothing.
     * After being removed, the observer will no longer get notifications for
     * network-related events.
     * @param networkObserver The network observer ({@code NetworkObserver} to
     *                        remove.
     */
    public static void removeNetworkObserver(NetworkObserver networkObserver) {
        Implementation.getInstance().removeNetworkObserver(networkObserver);
    }

    /**
     * Adds a message observer. After being added, the observer will get
     * notifications for message states and progress tracking. Notifications
     * will be triggered when messages are received, when they fail delivery,
     * or when a message is delivered. If the observer has previously already
     * been registered, it will not be registered twice, and the method will
     * do nothing.
     * @param messageObserver The message observer to add.
     * @see MessageObserver
     */
    public static void addMessageObserver(MessageObserver messageObserver) {
        Implementation.getInstance().addMessageObserver(messageObserver);
    }

    /**
     * This method removes a message observer ({@link MessageObserver}) that was
     * previously registered with {@link #addMessageObserver(MessageObserver)}.
     * If the observer was not previously registered or has already been removed,
     * this method does nothing. After being removed, the observer will no
     * longer get any notifications from the SDK.
     * @param messageObserver The message observer to remove.
     * @see MessageObserver
     */
    public static void removeMessageObserver(MessageObserver messageObserver) {
        Implementation.getInstance().removeMessageObserver(messageObserver);
    }

    /**
     * Calling this method requests the framework to start its services, by
     * publishing itself on the network and browsing for other devices. In case
     * of success, network observers will get a `onStart` notification,
     * indicating that the device is somehow participating on the network. This
     * might not mean that the device is both advertising and scanning, but that
     * it is participating in either or both ways. In case of failure, the
     * observers get an `onFailedStarting` notification. This is common if all
     * adapters are off, for example. At that point, it's useless trying to
     * start the framework again. Instead, the implementation should wait for
     * an observer notification indicating that recovery is possible, with
     * `onReady`. If the services have already been requested to run but have
     * not succeeded nor failed (that is, the request is still being processed)
     * this method does nothing. If the services are already running, the
     * observers get an immediate notification indicating that the services have
     * started as if they just did, with `onStart`.
     */
    public static void start() {
        Implementation.getInstance().start();
    }

    /**
     * Calling this method requests the framework to stop its services by no
     * longer publishing itself on the network nor browsing for other instances.
     * This does not imply previously found instances to be lost; ongoing
     * operations should continue, and communicating with known instances should
     * still be possible, but the framework will no longer find or be found by
     * other instances on direct link. However, instances may still be found in
     * mesh.
     */
    public static void stop() {
        Implementation.getInstance().stop();
    }

    /**
     * This method attempts to send a message to a given instance. The instance
     * must be a previously found and not lost instance, or else this method
     * fails with an error. It returns immediately (non blocking), queues the
     * data to be sent, and returns the message (Message) that was created for
     * wrapping the data. That data structure is helpful for tracking the
     * progress of messages as they are being sent over the network. The message
     * or the data are not strongly kept by the framework. The data is copied
     * and kept while it's queued, but the memory is released as its fragments
     * are sent. If the data is needed for later use, it should be kept at this
     * point, or otherwise it won't be recoverable. Progress notifications are
     * issued to message observers (MessageObserver). When listening to progress
     * tracking notifications, two concepts are important to distinguish:
     * sending and delivering. A message being sent
     * (MessageObserver.onULXMessageSent(MessageInfo, Instance, float, boolean))
     * indicates that the data was buffered, but has not necessarily arrived to
     * its destination. Delivery
     * (MessageObserver.onULXMessageDelivered(MessageInfo, Instance, float, boolean))
     * on the other hand, indicates that the content has reached its destination
     * and that has been acknowledged by the receiving instance. This
     * distinction is especially important in mesh, when the proxy device may
     * not be the same as the one the data is intended to. The trackProgress
     * argument indicates whether to track delivery. The data being queued to
     * the output stream (sent) is always notified, regardless of that setting.
     * Notice that passing true to this parameter incurs extra overhead on the
     * network, as it implies acknowledgements from the destination back to the
     * origin. If progress tracking is not needed, this should always be set to
     * false. In case an error occurs that prevents the message from reaching
     * the destination, the delegate gets a failure notification
     * (MessageObserver.onULXMessageFailedSending(MessageInfo, Instance, Error))
     * with an appropriate error message describing the reasons. If a proper
     * reason cannot be determined, a probable one is used instead.
     * @param data The data to be sent.
     * @param instance The destination instance.
     * @param trackProgress Whether to track delivery progress.
     * @return A message wrapper containing some metadata.
     */
    public static Message send(byte [] data, Instance instance, boolean trackProgress) {
        return Implementation.getInstance().send(data,instance,trackProgress);
    }

    /**
     * Sends a message to a given instance without tracking progress. This
     * method calls send(byte [], Instance, boolean) with the progress tracking
     * option set to false. All other technicalities described for that method
     * also apply.
     * @param data The data to send.
     * @param instance The instance to send the data to.
     * @return A message wrapper containing some metadata.
     */
    public static Message send(byte [] data, Instance instance) {
        return send(data, instance, false);
    }
}
