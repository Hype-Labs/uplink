package com.uplink.ulx.drivers.commons.model;

import com.uplink.ulx.UlxError;
import com.uplink.ulx.drivers.commons.StateManager;
import com.uplink.ulx.drivers.model.Connector;
import com.uplink.ulx.threading.ExecutorPool;
import com.uplink.ulx.utils.SetOnceRef;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

import androidx.annotation.NonNull;
import timber.log.Timber;

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
 * <br>
 * CONTRACT: subclasses must hide their constructors, create a factory method(s) and
 * call {@link ConnectorCommons#initialize()} before returning the new instance
 */
public abstract class ConnectorCommons implements
        Connector {
    @NonNull
    private final String identifier;
    private final int transportType;
    private final SetOnceRef<StateManager> stateManager;
    private WeakReference<StateDelegate> stateDelegate;
    private final List<InvalidationCallback> invalidationCallbacks = new ArrayList<>();
    private ExecutorService executorService;

    /**
     * Constructor. Initializes with the given arguments.
     * @param identifier An identifier for the Connector.
     * @param transportType The Connector's transport type.
     */
    public ConnectorCommons(@NonNull String identifier, int transportType) {
        this.identifier = identifier;
        this.stateManager = new SetOnceRef<>();
        this.transportType = transportType;
    }

    protected void initialize() {
        stateManager.setRef(new StateManager(new StateManager.Delegate() {
            @Override
            public void requestStart(StateManager stateManager) {
                Timber.i("ULX connector %s being requested to start", getIdentifier());
                requestAdapterToConnect();
            }

            @Override
            public void onStart(StateManager stateManager) {
                StateDelegate stateDelegate = getStateDelegate();
                if (stateDelegate != null) {
                    stateDelegate.onConnected(ConnectorCommons.this);
                }
            }

            @Override
            public void onStop(StateManager stateManager, UlxError error) {
                StateDelegate stateDelegate = getStateDelegate();
                if (stateDelegate != null) {
                    stateDelegate.onDisconnection(ConnectorCommons.this, error);
                }
            }

            @Override
            public void requestStop(StateManager stateManager) {
                Timber.i("ULX connector %s being requested to stop", getIdentifier());
                requestAdapterToDisconnect();
            }

            @Override
            public void onFailedStart(StateManager stateManager, UlxError error) {
                Timber.e("ULX connector %s failed to connect", getIdentifier());
                StateDelegate stateDelegate = getStateDelegate();
                if (stateDelegate != null) {
                    stateDelegate.onConnectionFailure(ConnectorCommons.this, error);
                }
            }

            @Override
            public void onStateChange(StateManager stateManager) {
                StateDelegate stateDelegate = getStateDelegate();
                if (stateDelegate != null) {
                    stateDelegate.onStateChange(ConnectorCommons.this);
                }
            }
        }));
    }

    /**
     * Returns the state manager (StateManager) associated with this instance.
     * @return The Connector's StateManager.
     */
    private StateManager getStateManager() {
        return stateManager.getRef();
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
        Timber.i(
                "ULX connector %s being requested to connect",
                getIdentifier()
        );
        getStateManager().start();
    }

    protected void onConnected() {
        Timber.i("ULX connector %s connected", getIdentifier());
        getStateManager().notifyStart();
    }

    public void onDisconnection(UlxError error) {
        Timber.e(
                "ULX connector %s disconnected with error %s",
                getIdentifier(),
                error
        );
        getStateManager().notifyStop(error);

        if (error != null) { // Unexpected disconnection is also invalidation
            for (InvalidationCallback callback : getInvalidationCallbacks()) {
                callback.onInvalidation(this, error);
            }
        }
    }

    protected void onConnectionFailure(UlxError error) {
        Timber.e(
                "ULX connector %s failed to connect with error: %s",
                getIdentifier(),
                error.toString()
        );
        getStateManager().notifyFailedStart(error);
    }

    @SuppressWarnings("ControlFlowStatementWithoutBraces")
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConnectorCommons)) return false;
        final ConnectorCommons that = (ConnectorCommons) o;
        return identifier.equals(that.identifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier);
    }
}
