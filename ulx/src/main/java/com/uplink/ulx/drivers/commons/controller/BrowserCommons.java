package com.uplink.ulx.drivers.commons.controller;

import android.content.Context;

import com.uplink.ulx.UlxError;
import com.uplink.ulx.drivers.commons.StateManager;
import com.uplink.ulx.drivers.controller.Browser;
import com.uplink.ulx.drivers.model.Connector;
import com.uplink.ulx.drivers.model.Device;
import com.uplink.ulx.drivers.model.Stream;

import java.lang.ref.WeakReference;
import java.util.Objects;

/**
 * This is a base class for browsers that implements common functionality
 * that is needed by all Browser implementations. It implements all basic
 * functionality that is common to browsers, such as state management, delegates,
 * transport type, and so on. Being abstract, it leaves two methods for children
 * to implement: requestAdapterToStart() and requestAdapterToStop(),
 * which consist of methods that implement the specific logic for each different
 * transport. This leaves out the logic that is specific to each implementation,
 * while at the same time already implementing several conveniences for any new
 * implementations.
 */
public abstract class BrowserCommons implements
        Browser,
        Browser.StateDelegate,
        Browser.NetworkDelegate,
        StateManager.Delegate,
        Connector.InvalidationDelegate,
        Stream.InvalidationDelegate
{
    private final String identifier;
    private final int transportType;
    private final StateManager stateManager;
    private final WeakReference<Context> context;
    private WeakReference<Browser.StateDelegate> stateDelegate;
    private WeakReference<Browser.NetworkDelegate> networkDelegate;

    /**
     * Constructor. Initializes with the given arguments.
     * @param identifier A string identifier for the instance.
     * @param transportType The transport type, set by the child class.
     * @param context The Android environment Context.
     */
    public BrowserCommons(String identifier, int transportType, Context context) {

        Objects.requireNonNull(identifier);
        Objects.requireNonNull(context);

        this.identifier = identifier;
        this.stateManager = new StateManager(this);
        this.transportType = transportType;
        this.context = new WeakReference<>(context);
    }

    /**
     * Getter for the StateManager. This is private to prevent misuse of the
     * manager, by somehow trying to manipulate its state. The entirety of the
     * state manager's lifecycle is managed by this abstract implementation.
     * @return The instance's StateManager.
     * @see StateManager
     */
    public StateManager getStateManager() {
        return this.stateManager;
    }

    /**
     * This method commits a request to start scanning for other devices on the
     * network. This is called as a result of a successful start() request,
     * after having checked for the browser's state and requested states.
     * In other words, this method does not corresponds one-to-one with calls
     * to the start() method, and instead flags the moment in which the
     * implementation should actually enable the scanner, looking for devices
     * that are advertising themselves in proximity. The implementation should
     * initiate the necessary procedures to make that happen, after which it
     * must issue a notification to the state manager indicating the outcome of
     * the operation.
     */
    public abstract void requestAdapterToStart();

    /**
     * This method commits a request to stop scanning for other devices on the
     * network. By this method being called, it should be assumed that the
     * browser is active. This means that the browser's state and other
     * preconditions have already been checked, before requesting the adapter
     * to actually stop scanning. Therefore, no state checks are necessary at
     * this point. This should occur as a result to some call to stop(),
     * although calls to the two methods do not correspond one-to-one. For
     * example, if the stop() method is called more than once, the state
     * manager will make sure that only a single call passes through, and
     * commit that only call so that requests to stop the browser do not
     * overlap. When done, the implementation should notify the state manager
     * with respect to the outcome of the operation.
     */
    public abstract void requestAdapterToStop();

    @Override
    public final String getIdentifier() {
        return this.identifier;
    }

    @Override
    public final Browser.State getState() {
        return Browser.State.fromInt(getStateManager().getState().getValue());
    }

    @Override
    public final void setStateDelegate(Browser.StateDelegate stateDelegate) {
        this.stateDelegate = new WeakReference<>(stateDelegate);
    }

    @Override
    public final Browser.StateDelegate getStateDelegate() {
        if (this.stateDelegate != null) {
            return this.stateDelegate.get();
        }
        return null;
    }

    @Override
    public final void setNetworkDelegate(Browser.NetworkDelegate networkDelegate) {
        this.networkDelegate = new WeakReference<>(networkDelegate);
    }

    @Override
    public final Browser.NetworkDelegate getNetworkDelegate() {
        if (this.networkDelegate != null) {
            return this.networkDelegate.get();
        }
        return null;
    }

    @Override
    public final Context getContext() {
        return this.context.get();
    }

    @Override
    public final int getTransportType() {
        return this.transportType;
    }

    @Override
    public void requestStart(StateManager stateManager) {
        requestAdapterToStart();
    }

    @Override
    public void onStart(StateManager stateManager) {
        Browser.StateDelegate stateDelegate = getStateDelegate();
        if (stateDelegate != null) {
            stateDelegate.onStart(this);
        }
    }

    @Override
    public void onStop(StateManager stateManager, UlxError error) {
        Browser.StateDelegate stateDelegate = getStateDelegate();
        if (stateDelegate != null) {
            stateDelegate.onStop(this, error);
        }
    }

    @Override
    public void requestStop(StateManager stateManager) {
        this.requestAdapterToStop();
    }

    @Override
    public void onFailedStart(StateManager stateManager, UlxError error) {
        Browser.StateDelegate stateDelegate = getStateDelegate();
        if (stateDelegate != null) {
            stateDelegate.onFailedStart(this, error);
        }
    }

    @Override
    public void onStateChange(StateManager stateManager) {
        Browser.StateDelegate stateDelegate = getStateDelegate();
        if (stateDelegate != null) {
            stateDelegate.onStateChange(this);
        }
    }

    @Override
    public void start() {
        getStateManager().start();
    }

    @Override
    public void stop() {
        getStateManager().stop();
    }

    @Override
    public void onStart(Browser browser) {
        getStateManager().notifyStart();
    }

    @Override
    public void onStop(Browser browser, UlxError error) {
        getStateManager().notifyStop(error);
    }

    public void onFailedStart(Browser browser, UlxError error) {
        getStateManager().notifyFailedStart(error);
    }

    @Override
    public void onDeviceFound(Browser browser, Device device) {
        Browser.NetworkDelegate networkDelegate = getNetworkDelegate();
        if (networkDelegate != null) {
            networkDelegate.onDeviceFound(this, device);
        }
    }

    @Override
    public void onDeviceLost(Browser browser, Device device, UlxError error) {
        Browser.NetworkDelegate networkDelegate = getNetworkDelegate();
        if (networkDelegate != null) {
            networkDelegate.onDeviceLost(this, device, error);
        }

    }

    @Override
    public void onReady(Browser browser) {
        Browser.StateDelegate stateDelegate = this.getStateDelegate();
        if (stateDelegate != null) {
            stateDelegate.onReady(this);
        }
    }

    @Override
    public void onInvalidation(Stream stream, UlxError error) {
        // TODO
    }
}
