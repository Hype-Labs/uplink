package com.uplink.ulx.drivers.commons.controller;

import android.content.Context;

import com.uplink.ulx.UlxError;
import com.uplink.ulx.drivers.commons.StateManager;
import com.uplink.ulx.drivers.controller.Advertiser;
import com.uplink.ulx.drivers.model.Connector;
import com.uplink.ulx.drivers.model.Device;
import com.uplink.ulx.utils.SetOnceRef;

import java.util.Objects;

import androidx.annotation.CallSuper;

/**
 * This is a base class for advertisers that implements common functionality
 * that is needed by all Advertiser implementations. It implements all basic
 * functionality that is common to advertisers, such as state management,
 * delegates, transport type, and so on. Being abstract, it leaves two methods
 * for children to implement: requestAdapterToStart() and requestAdapterToStop(),
 * which consist of methods that implement the specific logic for each different
 * transport. This leaves out the logic that is specific to each implementation,
 * while at the same time already implementing several conveniences for any new
 * implementations.
 * <br>
 *  CONTRACT: subclasses must hide their constructors, create a factory method(s) and
 *  call {@link AdvertiserCommons#initialize()} before returning the new instance
 */
public abstract class AdvertiserCommons implements
        Advertiser,
        Connector.InvalidationCallback {

    private final String identifier;
    private final SetOnceRef<StateManager> stateManager;
    private final int transportType;
    private final Context context;
    private volatile Delegate delegate;
    private volatile StateDelegate stateDelegate;
    private volatile NetworkDelegate networkDelegate;

    /**
     * Constructor. Initializes with the given arguments.
     * @param identifier A string identifier for the instance.
     * @param transportType The transport type, set by the child class.
     * @param context The Android environment Context.
     */
    public AdvertiserCommons(String identifier, int transportType, Context context) {

        Objects.requireNonNull(identifier);
        Objects.requireNonNull(context);

        this.identifier = identifier;
        this.stateManager = new SetOnceRef<>();
        this.transportType = transportType;
        this.context = context;
    }

    @CallSuper
    protected void initialize() {
        stateManager.setRef(new StateManager(new StateManager.Delegate() {
            @Override
            public void requestStart(StateManager stateManager) {
                requestAdapterToStart();
            }

            @Override
            public void onStart(StateManager stateManager) {
                StateDelegate stateDelegate = getStateDelegate();
                if (stateDelegate != null) {
                    stateDelegate.onStart(AdvertiserCommons.this);
                }
            }

            @Override
            public void onStop(StateManager stateManager, UlxError error) {
                StateDelegate stateDelegate = getStateDelegate();
                if (stateDelegate != null) {
                    stateDelegate.onStop(AdvertiserCommons.this, error);
                }
            }

            @Override
            public void requestStop(StateManager stateManager) {
                requestAdapterToStop();
            }

            @Override
            public void onFailedStart(StateManager stateManager, UlxError error) {
                StateDelegate stateDelegate = getStateDelegate();
                if (stateDelegate != null) {
                    stateDelegate.onFailedStart(AdvertiserCommons.this, error);
                }
            }

            @Override
            public void onStateChange(StateManager stateManager) {
                StateDelegate stateDelegate = getStateDelegate();
                if (stateDelegate != null) {
                    stateDelegate.onStateChange(AdvertiserCommons.this);
                }
            }
        }));
    }

    /**
     * Getter for the StateManager. This is private to prevent misuse of the
     * manager, by somehow trying to manipulate its state. The entirety of the
     * state manager's lifecycle is managed by this abstract implementation.
     * @return The instance's StateManager.
     * @see StateManager
     */
    private StateManager getStateManager() {
        return this.stateManager.getRef();
    }

    /**
     * This method commits a request to start advertising the device on the
     * network. This is called as a result of a successful start() request,
     * after having checked for the advertiser's state and requested states.
     * In other words, this method does not corresponds one-to-one with calls
     * to the start() method, and instead flags the moment in which the
     * implementation should actually publish the device on the network. The
     * implementation should initiate the necessary procedures to make the
     * device visible, after which it must issue a notification on itself
     * indicating the outcome of the operation; this is why this abstraction
     * implements the Advertiser's delegates (StateDelegate and NetworkDelegate)
     * so that child classes can easily propagate such notifications on super.
     */
    public abstract void requestAdapterToStart();

    /**
     * This method commits a request to stop advertising the device, making it
     * invisible to others in direct link on the network. By this method being
     * called, it should be assumed that the advertiser is actively advertising
     * the device on the network. This means that the advertiser's state and
     * other preconditions have already been checked, before requesting the
     * adapter to actually stop advertising. Therefore, no state checks are
     * necessary at this point. This should occur as a result to some call to
     * stop(), although calls to the two methods do not correspond one-to-one.
     * For example, if the stop() method is called more than once, the state
     * manager will make sure that only a single call passes through, and
     * commit that only call so that requests to stop the advertiser do not
     * overlap. Here, the implementation should initiate the necessary
     * procedures to make the device invisible to others in the network. When
     * done, the implementation should notify the state manager with respect to
     * the outcome of the operation.
     */
    public abstract void requestAdapterToStop();

    @Override
    public final String getIdentifier() {
        return this.identifier;
    }

    @Override
    public final State getState() {
        return State.fromInt(getStateManager().getState().getValue());
    }

    @Override
    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    @Override
    public Delegate getDelegate() {
        return this.delegate;
    }

    @Override
    public final void setStateDelegate(StateDelegate stateDelegate) {
        this.stateDelegate = stateDelegate;
    }

    @Override
    public final StateDelegate getStateDelegate() {
        if (this.stateDelegate != null) {
            return this.stateDelegate;
        }
        return null;
    }

    @Override
    public final void setNetworkDelegate(NetworkDelegate networkDelegate) {
        this.networkDelegate = networkDelegate;
    }

    @Override
    public final NetworkDelegate getNetworkDelegate() {
        if (this.networkDelegate != null) {
            return this.networkDelegate;
        }
        return null;
    }

    @Override
    public final Context getContext() {
        return this.context;
    }

    @Override
    public final int getTransportType() {
        return this.transportType;
    }

    @Override
    public void start() {
        getStateManager().start();
    }

    @Override
    public void stop() {
        getStateManager().stop();
    }

    protected void onStart() {
        getStateManager().notifyStart();
    }

    protected void onStop(UlxError error) {
        getStateManager().notifyStop(error);
    }

    protected void onFailedStart(UlxError error) {
        getStateManager().notifyFailedStart(error);
    }

    protected void onDeviceFound(Device device) {
        NetworkDelegate networkDelegate = getNetworkDelegate();
        if (networkDelegate != null) {
            networkDelegate.onDeviceFound(this, device);
        }
    }

    protected void onReady() {
        StateDelegate stateDelegate = this.getStateDelegate();
        if (stateDelegate != null) {
            stateDelegate.onReady(this);
        }
    }
}
