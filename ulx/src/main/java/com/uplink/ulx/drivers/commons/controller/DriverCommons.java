package com.uplink.ulx.drivers.commons.controller;

import android.content.Context;

import com.uplink.ulx.UlxError;
import com.uplink.ulx.bridge.Bridge;
import com.uplink.ulx.drivers.commons.StateManager;
import com.uplink.ulx.drivers.controller.Advertiser;
import com.uplink.ulx.drivers.controller.Browser;
import com.uplink.ulx.drivers.controller.Driver;
import com.uplink.ulx.drivers.controller.TransportDriver;
import com.uplink.ulx.drivers.model.Device;
import com.uplink.ulx.threading.ExecutorPool;
import com.uplink.ulx.utils.SetOnceRef;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import timber.log.Timber;

/**
 * This class implements the parts of the logic that are shared by all Driver
 * implementations. It programs the responses for Advertiser and Browser
 * delegates (both StateDelegate and NetworkDelegate), as well as the usual
 * identifiers, transport type, state management, etc. The delegate calls are
 * propagated to the driver's own delegates. As an abstract class, this leaves
 * the methods getAdvertiser() and getBrowser() to be implemented, leaving it
 * up to those inheriting the functionality to instantiate those units. This
 * means that Driver implementations should worry about the specific Browser
 * and Advertiser that they implement, since this abstract unit is not capable
 * of deciding on the specific implementations of those that it's supposed to
 * use. However, this class still manages both the Browser and Advertiser from
 * an abstract perspective, meaning that calls to the driver are propagated to
 * both, and that the driver tries to synchronize the states of the two.
 * <br>
 * CONTRACT: subclasses must hide their constructors, create a factory method(s) and
 * call {@link DriverCommons#initialize()} before returning the new instance
 */
public abstract class DriverCommons implements
        TransportDriver,
        Advertiser.Delegate,
        Advertiser.StateDelegate,
        Advertiser.NetworkDelegate,
        Browser.Delegate,
        Browser.StateDelegate,
        Browser.NetworkDelegate {

    private final String identifier;
    private final int transportType;
    private final SetOnceRef<StateManager> stateManager;
    private final Context context;
    private WeakReference<StateDelegate> statusDelegate;
    private WeakReference<Driver.NetworkDelegate> networkDelegate;
    private ExecutorService executorService;

    private UlxError browserError;
    private UlxError advertiserError;

    /**
     * Constructor. Initializes with the given arguments.
     * @param identifier An hex string identifier for the Driver.
     * @param transportType The Driver's transport type.
     * @param context The Android environment Context.
     */
    public DriverCommons(String identifier, int transportType, @NonNull Context context) {

        Objects.requireNonNull(identifier);
        Objects.requireNonNull(context);

        this.identifier = identifier;
        this.transportType = transportType;
        this.context = context;
        this.stateManager = new SetOnceRef<>();
        this.browserError = null;
        this.advertiserError = null;
    }

    protected void initialize(String className) {
        stateManager.setRef(new StateManager(new StateManager.Delegate() {
            @Override
            public void requestStart(StateManager stateManager) {
                getExecutorService().execute(() -> {
                    getAdvertiser().start();
                    getBrowser().start();
                });
            }

            @Override
            public void onStart(StateManager stateManager) {
                StateDelegate delegate = getStateDelegate();
                if (delegate != null) {
                    delegate.onStart(DriverCommons.this);
                }
            }

            @Override
            public void onStop(StateManager stateManager, UlxError error) {
                StateDelegate delegate = getStateDelegate();
                if (delegate != null) {
                    delegate.onStop(DriverCommons.this, error);
                }
            }

            @Override
            public void requestStop(StateManager stateManager) {
                getExecutorService().execute(() -> {
                    getBrowser().stop();
                    getAdvertiser().stop();
                });
            }

            @Override
            public void onFailedStart(StateManager stateManager, UlxError error) {
                StateDelegate delegate = getStateDelegate();
                if (delegate != null) {
                    delegate.onFailedStart(DriverCommons.this, error);
                }
            }

            @Override
            public void onStateChange(StateManager stateManager) {
                StateDelegate delegate = getStateDelegate();
                if (delegate != null) {
                    delegate.onStateChange(DriverCommons.this);
                }
            }
        }, className));
    }

    /**
     * Getter for the StateManager. This is private to prevent misuse of the
     * manager, by somehow trying to manipulate its state. The entirety of the
     * state manager's lifecycle is managed by this abstract implementation.
     * @return The instance's StateManager.
     * @see StateManager
     */
    private StateManager getStateManager() {
        return stateManager.getRef();
    }

    /**
     * This method returns the ExecutorService that is used by the driver to
     * dispatch work, namely start and stop requests. This guarantees that the
     * calling thread (which may often be the main thread) is freed before the
     * implementation begins to mess with the adapter. This executor is unique
     * per transport type, meaning that each transport has its own allocated
     * dispatcher, and this it is shared with other implementations that use
     * the same transport type and use ExecutorServices to dispatch work.
     * @return An ExecutorService for dispatching work.
     */
    protected final ExecutorService getExecutorService() {
        if (this.executorService == null) {
            this.executorService = ExecutorPool.getExecutor(getTransportType());
        }
        return this.executorService;
    }

    /**
     * Sets the browser has having failed to start. This will keep the error
     * reported for later reference.
     * @param browserError The error describing the browser failure.
     */
    private void setBrowserError(UlxError browserError) {
        this.browserError = browserError;
    }

    /**
     * Returns an error associated with a previous browser failure, if one has
     * occurred.
     * @return An error (ULXError) describing a browser failure, if any.
     */
    private UlxError getBrowserError() {
        return this.browserError;
    }

    /**
     * Sets the Advertiser has either having failed or not. If an error has
     * occurred when the Advertiser was starting, this error will describe the
     * problem, or otherwise set the error to null. Setting null clears any
     * previously set errors.
     * @param advertiserError The error to set, or null to clear.
     */
    private void setAdvertiserError(UlxError advertiserError) {
        this.advertiserError = advertiserError;
    }

    /**
     * Returns any previously Advertiser error, if one has been set, or null if
     * no error has been set or it has already been cleared.
     * @return The Advertiser error.
     */
    private UlxError getAdvertiserError() {
        return this.advertiserError;
    }

    /**
     * This method handles failed start notifications from both the Advertiser
     * and Browser, meaning that the two flows of execution converge here. The
     * given error (not any of the previous Advertiser or Browser errors) will
     * be used to notify the state manager; that should correspond to the most
     * recent error out of the two.
     * @param error The error that will be used to report a failed start.
     */
    private void handleFailedStart(UlxError error) {
        if (getAdvertiserError() != null && getBrowserError() != null) {
            getStateManager().notifyFailedStart(error);
        }
    }

    /**
     * This method is called whenever either the Browser or the Advertiser find
     * a Device on the network. Which of the two has found the device will not
     * be known from this point going forward, and the Device will notify the
     * delegate has having originated from itself.
     * @param device The device found.
     */
    private void handleDeviceFound(Device device) {
        Driver.NetworkDelegate delegate = this.getNetworkDelegate();
        if (delegate != null) {
            delegate.onDeviceFound(this, device);
        }
    }

    /**
     * This method is called when either the Advertiser or the Browser flag a
     * Device has being lost. This means that the device can no longer be used
     * for communication purposes. The Driver will issue this notification
     * without any regard for which of the Browser or the Advertiser have
     * originated, meaning that, from the delegate's perspective, the
     * notification is coming from the driver directly.
     * @param device The Device that has been lost.
     * @param error An error (ULXError), describing a probable cause for the loss.
     */
    private void handleDeviceLost(Device device, UlxError error) {
        Driver.NetworkDelegate delegate = this.getNetworkDelegate();
        if (delegate != null) {
            delegate.onDeviceLost(this, device, error);
        }
    }

    /**
     * This handler will be called whenever either of the Advertiser or the
     * Browser give indication to the Driver that they are capable of recovering
     * from a failure. The notification will only be propagated to the delegate
     * if the Driver is not IDLE, since in any other event this notification
     * will not be expected.
     */
    private void handleReadyNotification() {

        // Don't proceed when IDLE
        if (getState() != State.IDLE) {
            Timber.i("Not issuing ready Because state was Idle!");
            return;
        }

        // Notify the delegate
        Driver.StateDelegate stateDelegate = getStateDelegate();
        if (stateDelegate != null) {
            stateDelegate.onReady(this);
        }
    }

    @Override
    @NonNull
    public final Context getContext() {
        return this.context;
    }

    @Override
    public final String getIdentifier() {
        return this.identifier;
    }

    @Override
    public final int getTransportType() {
        return this.transportType;
    }

    @Override
    public State getState() {
        return State.fromInt(getStateManager().getState().getValue());
    }

    @Override
    public final Driver.NetworkDelegate getNetworkDelegate() {
        if (this.networkDelegate != null) {
            return this.networkDelegate.get();
        }
        return null;
    }

    @Override
    public final void setNetworkDelegate(Driver.NetworkDelegate driverNetworkDelegate) {
        this.networkDelegate = new WeakReference<>(driverNetworkDelegate);
    }

    @Override
    public final StateDelegate getStateDelegate() {
        if (this.statusDelegate != null) {
            return this.statusDelegate.get();
        }
        return null;
    }

    @Override
    public final void setStateDelegate(StateDelegate driverStateDelegate) {
        this.statusDelegate = new WeakReference<>(driverStateDelegate);
    }

    @Override
    @CallSuper
    public void destroy() {
        getAdvertiser().destroy();
        getBrowser().destroy();
    }

    @Override
    public void start() {
        setBrowserError(null);
        setAdvertiserError(null);
        getStateManager().start();
    }

    @Override
    public void stop() {
        getStateManager().stop();
    }

    @Override
    public void onStart(Advertiser advertiser) {
        if (getBrowser().getState() != Browser.State.RUNNING) {
            getStateManager().notifyStart();
        }
    }

    @Override
    public void onStop(Advertiser advertiser, UlxError error) {
        if (getBrowser().getState() == Browser.State.IDLE) {
            getStateManager().notifyStop(error);
        }
    }

    @Override
    public void onFailedStart(Advertiser advertiser, UlxError error) {
        setAdvertiserError(error);
        handleFailedStart(error);
    }

    @Override
    public void onDeviceFound(Advertiser advertiser, Device device) {
        handleDeviceFound(device);
    }

    @Override
    public void onDeviceLost(Advertiser advertiser, Device device, UlxError error) {
        handleDeviceLost(device, error);
    }

    @Override
    public void onReady(Advertiser advertiser) {
        handleReadyNotification();
    }

    @Override
    public void onStateChange(Advertiser advertiser) {
        Driver.StateDelegate stateDelegate = getStateDelegate();

        // As it currently stands, this will produce duplicate notifications,
        // which shouldn't happen. E.g. both the Browser and the Advertiser
        // transit to a STARTING state.
        if (stateDelegate != null) {
            stateDelegate.onStateChange(this);
        }
    }

    @Override
    public void onFailedStart(Browser browser, UlxError error) {
        setBrowserError(error);
        handleFailedStart(error);
    }

    @Override
    public void onDeviceFound(Browser browser, Device device) {
        handleDeviceFound(device);
    }

    @Override
    public void onDeviceLost(Browser browser, Device device, UlxError error) {
        handleDeviceLost(device, error);
    }

    @Override
    public void onStart(Browser browser) {
        if (getAdvertiser().getState() != Advertiser.State.RUNNING) {
            getStateManager().notifyStart();
        }
    }

    @Override
    public void onStop(Browser browser, UlxError error) {
        if (getAdvertiser().getState() == Advertiser.State.IDLE) {
            getStateManager().notifyStop(error);
        }
    }

    @Override
    public void onReady(Browser browser) {
        handleReadyNotification();
    }

    @Override
    public void onStateChange(Browser browser) {
        Driver.StateDelegate stateDelegate = getStateDelegate();

        // As it currently stands, this will produce duplicate notifications,
        // which shouldn't happen. E.g. both the Browser and the Advertiser
        // transit to a STARTING state.
        if (stateDelegate != null) {
            stateDelegate.onStateChange(this);
        }
    }

    @Override
    public boolean onAdapterRestartRequest(Browser browser) {
        if (browser.getState() == Browser.State.RUNNING) {
            return handleAdapterRestartRequest();
        } else {
            return false;
        }
    }

    @Override
    public void onAdapterRestartRequest(Advertiser advertiser) {
        if (advertiser.getState() == Advertiser.State.RUNNING) {
            handleAdapterRestartRequest();
        }
    }

    /**
     * @return whether the request was honored
     */
    private boolean handleAdapterRestartRequest() {
        if (Bridge.getInstance().hasActiveDevices()) {
            Timber.i("ULX driver is rejecting an adapter restart request");
            return false;
        }

        Timber.i("ULX is accepting an adapter restart request");

        // Restart the adapter
        return requestAdapterRestart();
    }

    /**
     * @return whether the request was successful or not
     */
    protected abstract boolean requestAdapterRestart();
}
