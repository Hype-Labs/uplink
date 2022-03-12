package com.uplink.ulx.drivers.commons.model;

import android.annotation.SuppressLint;
import android.util.Log;

import com.uplink.ulx.UlxError;
import com.uplink.ulx.drivers.model.Connector;
import com.uplink.ulx.drivers.commons.StateManager;
import com.uplink.ulx.threading.ExecutorPool;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import androidx.annotation.NonNull;

/**
 * This class implements the common functionality that Connector implementations
 * share. This includes managing the delegates (both StateDelegate and
 * NetworkDelegate), as well as thread management, but also identifiers,
 * transport type, state management, and so on. The implementation will
 * especially manage the Connector's lifecycle, leaving the methods
 * requestAdapterToConnect() and requestAdapterToDisconnect() for the transport-
 * specific implementations. This means that each implementation will worry
 * about the details of its specific transport, leaving this commonalities for
 * this class, which is to be used as a base class. This class also leaves
 * the Stream's InvalidationCallback methods for the child class to implement.
 */
public abstract class ConnectorCommons implements
        Connector,
        Connector.StateDelegate,
        StateManager.Delegate {

    private final String identifier;
    private final int transportType;
    private final StateManager stateManager;
    private WeakReference<StateDelegate> stateDelegate;
    private final List<InvalidationCallback> invalidationCallbacks = new ArrayList<>();
    private ExecutorService executorService;

    /**
     * Constructor. Initializes with the given arguments.
     * @param identifier An identifier for the Connector.
     * @param transportType The Connector's transport type.
     */
    public ConnectorCommons(String identifier, int transportType) {
        this.identifier = identifier;
        this.stateManager = new StateManager(this);
        this.transportType = transportType;
    }

    /**
     * Returns the state manager (StateManager) associated with this instance.
     * @return The Connector's StateManager.
     */
    private StateManager getStateManager() {
        return this.stateManager;
    }

    /**
     * This method returns the ExecutorService that is used by the connector to
     * dispatch work, namely connect and disconnect requests. This guarantees
     * that the calling thread (which may often be the main thread) is freed
     * before the implementation begins to mess with the adapter. This executor
     * is unique per transport type, meaning that each transport has its own
     * allocated dispatcher, and this it is shared with other implementations
     * that use the same transport type and use ExecutorServices to dispatch
     * work.
     * @return An ExecutorService for dispatching work.
     */
    private ExecutorService getExecutorService() {
        if (this.executorService == null) {
            this.executorService = ExecutorPool.getExecutor(getTransportType());
        }
        return this.executorService;
    }

    /**
     * Requesting a Connector to connect will commit a start request by
     * initiating the necessary procedures for the two devices to connect. How
     * that happens, exactly, depends on the transport being implemented, and
     * it's up to the base class to know. When this method is called, the
     * connector's state has already been checked, meaning that no state or
     * requested state checks are necessary. In other words, when this method
     * is called, the connection should be initiated without any further checks.
     * As soon as done, either successfully or with an error, the implementation
     * must notify the state manager of completion.
     */
    public abstract void requestAdapterToConnect();

    /**
     * This method commits a disconnect request by asking the connector to
     * terminate an active connection. This method being called should mean
     * that all state checks have already been performed, meaning that the
     * implementation should simply disconnect from the device that the
     * connector refers. Once done, the implementation must notify the state
     * manager of the result of the operation, regardless of whether that
     * results in success or failure.
     */
    public abstract void requestAdapterToDisconnect();

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
    public final StateDelegate getStateDelegate() {
        if (this.stateDelegate != null) {
            return this.stateDelegate.get();
        }
        return null;
    }

    @Override
    public final void setStateDelegate(StateDelegate stateDelegate) {
        this.stateDelegate = new WeakReference<>(stateDelegate);
    }

    @NonNull
    @Override
    public final List<InvalidationCallback> getInvalidationCallbacks() {
        synchronized (invalidationCallbacks) {
            // The caller can (and, in fact, does) modify callbacks list, so we'll give them a copy
            return new ArrayList<>(invalidationCallbacks);
        }
    }

    @Override
    public final void addInvalidationCallback(InvalidationCallback invalidationCallback) {
        synchronized (invalidationCallbacks) {
            invalidationCallbacks.add(invalidationCallback);
        }
    }

    @Override
    public void removeInvalidationCallback(InvalidationCallback callback) {
        synchronized (invalidationCallbacks) {
            invalidationCallbacks.remove(callback);
        }
    }

    @Override
    public void connect() {
        Log.i(getClass().getCanonicalName(), String.format("ULX connector %s being requested to connect", getIdentifier()));
        getStateManager().start();
    }

    @Override
    public void disconnect() {
        Log.i(getClass().getCanonicalName(), String.format("ULX connector %s being requested to disconnect", getIdentifier()));
        getStateManager().stop();
    }

    @Override
    public void requestStart(StateManager stateManager) {
        Log.i(getClass().getCanonicalName(), String.format("ULX connector %s being requested to start", getIdentifier()));
        requestAdapterToConnect();
    }

    @Override
    public void onStart(StateManager stateManager) {
        StateDelegate stateDelegate = this.getStateDelegate();
        if (stateDelegate != null) {
            stateDelegate.onConnected(this);
        }
    }

    @Override
    public void onStop(StateManager stateManager, UlxError error) {
        StateDelegate stateDelegate = this.getStateDelegate();
        if (stateDelegate != null) {
            stateDelegate.onDisconnection(this, error);
        }
    }

    @Override
    public void requestStop(StateManager stateManager) {
        Log.i(getClass().getCanonicalName(), String.format("ULX connector %s being requested to stop", getIdentifier()));
        this.requestAdapterToDisconnect();
    }

    @Override
    public void onFailedStart(StateManager stateManager, UlxError error) {
        Log.e(getClass().getCanonicalName(), String.format("ULX connector %s failed to connect", getIdentifier()));
        StateDelegate stateDelegate = this.getStateDelegate();
        if (stateDelegate != null) {
            stateDelegate.onConnectionFailure(this, error);
        }
    }

    @Override
    public void onStateChange(StateManager stateManager) {
        StateDelegate stateDelegate = this.getStateDelegate();
        if (stateDelegate != null) {
            stateDelegate.onStateChange(this);
        }
    }

    @Override
    public void onConnected(Connector connector) {
        Log.i(getClass().getCanonicalName(), String.format("ULX connector %s connected", getIdentifier()));
        getStateManager().notifyStart();
    }

    @SuppressLint("NewApi") // forEach() is actually supported with desugaring library
    @Override
    public void onDisconnection(Connector connector, UlxError error) {
        Log.e(getClass().getCanonicalName(), String.format("ULX connector %s disconnected with error %s", getIdentifier(), error));
        getStateManager().notifyStop(error);

        if (error != null) { // Unexpected disconnection is also invalidation
            getInvalidationCallbacks().forEach(callback -> callback.onInvalidation(this, error));
        }
    }

    @Override
    public void onConnectionFailure(Connector connector, UlxError error) {
        Log.e(getClass().getCanonicalName(), String.format("ULX connector %s failed to connect with error: %s", getIdentifier(), error.toString()));
        getStateManager().notifyFailedStart(error);
    }

    @Override
    public void onStateChange(Connector connector) {
        StateDelegate stateDelegate = getStateDelegate();
        if (stateDelegate != null) {
            stateDelegate.onStateChange(this);
        }
    }
}
