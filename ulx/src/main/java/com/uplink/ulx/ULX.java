package com.uplink.ulx;

import android.content.Context;

import com.uplink.ulx.model.Instance;
import com.uplink.ulx.model.State;
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
}
