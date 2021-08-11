package com.uplink.ulx.service;

import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.uplink.ulx.model.State;
import com.uplink.ulx.UlxError;
import com.uplink.ulx.bridge.Bridge;
import com.uplink.ulx.drivers.controller.Driver;
import com.uplink.ulx.drivers.controller.DriverManager;
import com.uplink.ulx.drivers.model.Device;
import com.uplink.ulx.model.Instance;

import java.lang.ref.WeakReference;
import java.util.UUID;

/**
 * This is an extension of an Android Service that is used to run the SDK as a
 * background service. It serves the purpose of keeping the SDK running as long
 * lived component, while keeping the app threads clear of additional work. The
 * implementation components will communicate with this service through means
 * of RPC calls, and the Service will notify those components back through
 * means of a delegate.
 */
public class Service extends android.app.Service implements Driver.StateDelegate, Driver.NetworkDelegate, Bridge.Delegate {

    /**
     * The Service Delegate receives notifications from the background service
     * indicating activity on the SDK. This will include activity for all sorts
     * of state changes on the SDK, ranging from lifecycle, device discovery,
     * and I/O, since all of those run on the background service.
     */
    public interface Delegate {

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
     * This is service Binder, specifically for the Service calls, that is used
     * for the RPC mechanism used by the service to communicate with the app.
     */
    /* package */ class LocalBinder extends Binder {

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
    private WeakReference<Delegate> delegate;

    /**
     * Sole constructor, initializes some variables.
     */
    public Service() {

        this.delegate = null;
        this.binder = new LocalBinder();
    }

    /**
     * Sets the delegate for the service, which will get notifications from it.
     * This delegate is not initialized at construction time because of the way
     * in which services are instantiated, so it must be set after that.
     * @param delegate The delegate to set.
     */
    public final void setDelegate(Delegate delegate) {
        this.delegate = new WeakReference<>(delegate);
    }

    /**
     * Returns the delegate for the service. A weak reference is returned. If
     * no delegate was set, this returns a weak reference to null.
     * @return The service's delegate.
     */
    public final WeakReference<Delegate> getDelegate() {
        return this.delegate != null ? this.delegate : new WeakReference<>(null);
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
        Log.d("ULX", "onBind(Intent)");
        return this.binder;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.d("ULX", "onTaskRemoved(Intent)");

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
        Bridge.getInstance().setDelegate(this);
        Bridge.getInstance().initialize(appIdentifier);
    }

    @Override
    public void onInitialization(Bridge bridge, Instance hostInstance) {
        Delegate delegate = getDelegate().get();

        if (delegate != null) {
            delegate.onInitialization(this, hostInstance);
        }
    }

    @Override
    public void onStart(Driver driver) {
        Delegate delegate = getDelegate().get();

        if (delegate != null) {
            delegate.onStart(this);
        }
    }

    @Override
    public void onStop(Driver driver, UlxError error) {
        Delegate delegate = getDelegate().get();

        if (delegate != null) {
            delegate.onStop(this, error);
        }
    }

    @Override
    public void onFailedStart(Driver driver, UlxError error) {
        Delegate delegate = getDelegate().get();

        if (delegate != null) {
            delegate.onFailedStart(this, error);
        }
    }

    @Override
    public void onReady(Driver driver) {
        Delegate delegate = getDelegate().get();

        if (delegate != null) {
            delegate.onReady(this);
        }
    }

    @Override
    public void onStateChange(Driver driver) {
        Delegate delegate = getDelegate().get();

        if (delegate != null) {
            delegate.onStateChange(this);
        }
    }

    @Override
    public void onDeviceFound(Driver driver, Device device) {
    }

    @Override
    public void onDeviceLost(Driver driver, Device device, UlxError error) {
    }
}
