package com.uplink.ulx.drivers.commons.model;

import android.util.Log;

import com.uplink.ulx.UlxError;
import com.uplink.ulx.drivers.commons.StateManager;
import com.uplink.ulx.drivers.model.Stream;

import java.lang.ref.WeakReference;
import java.util.Objects;

import androidx.annotation.NonNull;

/**
 * A StreamCommons is an abstraction of a stream base class that implements
 * functionality that is common to all streams. It handles delegates, as well
 * as other metainformation respecting the streams. All streams should
 * eventually inherit from this base class, but perhaps not directly, since the
 * InputStreamCommons and OutputStreamCommons further elaborate on the logic
 * that is specific for I/O operations; stream implementations should inherit
 * from those instead. For the most part, this class handles the stream's
 * lifecycle, processing start and stop requests, and propagating delegate
 * notifications.
 * @see InputStreamCommons
 * @see OutputStreamCommons
 */
public abstract class StreamCommons implements
        Stream,
        StateManager.Delegate {

    @NonNull
    private final String identifier;
    private final int transportType;
    private final boolean reliable;
    private final StateManager stateManager;
    private WeakReference<StateDelegate> stateDelegate;
    private WeakReference<Stream.InvalidationDelegate> invalidationDelegate;

    /**
     * Constructor. Initializes with the given arguments.
     * @param identifier An identifier used for JNI bridging and debugging.
     * @param transportType The stream's transport type.
     * @param reliable A boolean flag, indicating whether the stream is reliable.
     */
    public StreamCommons(@NonNull String identifier, int transportType, boolean reliable) {

        Objects.requireNonNull(identifier);

        this.identifier = identifier;
        this.stateManager = new StateManager(this);
        this.transportType = transportType;
        this.reliable = reliable;
    }

    /**
     * Getter for the StateManager. This is private to prevent misuse of the
     * manager, by somehow trying to manipulate its state. The entirety of the
     * state manager's lifecycle is managed by this abstract implementation.
     * @return The instance's StateManager.
     * @see StateManager
     */
    private StateManager getStateManager() {
        return this.stateManager;
    }

    /**
     * This method commits an open() request, in the sense that the stream is
     * actually requested to enabled itself for I/O, given that all state checks
     * and preconditions have already been verified. Whether the process for
     * enabling itself is synchronous or asynchronous, the stream must notify
     * the state delegate upon completion, by calling the appropriate methods
     * on itself. For example, if the process completes successfully, the
     * implementation should call super.onStart(Stream), meaning that, in a
     * sense, the stream is its own delegate.
     */
    public abstract void requestAdapterToOpen();

    /**
     * This method commits a stop() request, in the sense that the stream is
     * actually requested to disable itself, closing itself for I/O. When this
     * method is called, the stream's lifecycle state has already been checked,
     * as well as any other general pre-conditions; the implementation may skip
     * those checks, and focus on the transport-specific logic required to close
     * the stream. As soon as that is completed, the stream implementation
     * should trigger StateDelegate notifications on "super", meaning that the
     * implementations treat the stream as a delegate of itself.
     */
    public abstract void requestAdapterToClose();

    @Override
    public State getState() {
        return State.fromInt(getStateManager().getState().getValue());
    }

    @Override
    public void requestStart(StateManager stateManager) {
        Log.i(getClass().getCanonicalName(), String.format("ULX stream %s is being requested to start", getIdentifier()));
        requestAdapterToOpen();
    }

    @Override
    public void onStart(StateManager stateManager) {
        Log.i(getClass().getCanonicalName(), String.format("ULX stream %s started", getIdentifier()));
        StateDelegate stateDelegate = this.getStateDelegate();
        if (stateDelegate != null) {
            stateDelegate.onOpen(this);
        }
    }

    @Override
    public void onStop(StateManager stateManager, UlxError error) {
        Log.e(getClass().getCanonicalName(), String.format("ULX stream %s stopped with error %s", getIdentifier(), error.toString()));
        StateDelegate stateDelegate = this.getStateDelegate();
        if (stateDelegate != null) {
            stateDelegate.onClose(this, error);
        }
    }

    @Override
    public void requestStop(StateManager stateManager) {
        Log.i(getClass().getCanonicalName(), String.format("ULX stream %s is being requested to stop", getIdentifier()));
        this.close();
    }

    @Override
    public void onFailedStart(StateManager stateManager, UlxError error) {
        Log.e(getClass().getCanonicalName(), String.format("ULX stream %s failed to start", getIdentifier()));
        StateDelegate stateDelegate = this.getStateDelegate();
        if (stateDelegate != null) {
            stateDelegate.onFailedOpen(this, error);
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
    public void open() {
        Log.i(getClass().getCanonicalName(), String.format("ULX stream %s is being requested to open", getIdentifier()));
        getStateManager().start();
    }

    @Override
    public void close() {
        Log.i(getClass().getCanonicalName(), String.format("ULX stream %s is being requested close", getIdentifier()));
        getStateManager().stop();
    }

    @NonNull
    @Override
    public String getIdentifier() {
        return this.identifier;
    }

    @Override
    public int getTransportType() {
        return this.transportType;
    }

    @Override
    public StateDelegate getStateDelegate() {
        if (stateDelegate != null) {
            return stateDelegate.get();
        }
        return null;
    }

    @Override
    public void setStateDelegate(StateDelegate stateDelegate) {
        this.stateDelegate = new WeakReference<>(stateDelegate);
    }

    @Override
    public Stream.InvalidationDelegate getInvalidationDelegate() {
        if (this.invalidationDelegate != null) {
            return this.invalidationDelegate.get();
        }
        return null;
    }

    @Override
    public void setInvalidationDelegate(Stream.InvalidationDelegate invalidationDelegate) {
        this.invalidationDelegate = new WeakReference<>(invalidationDelegate);
    }

    @Override
    public boolean isReliable() {
        return this.reliable;
    }

    protected void onOpen() {
        Log.i(getClass().getCanonicalName(), String.format("ULX stream %s is now open", getIdentifier()));
        getStateManager().notifyStart();
    }

    protected void onClose(UlxError error) {
        Log.i(getClass().getCanonicalName(), String.format("ULX stream %s is now closed", getIdentifier()));
        getStateManager().notifyStop(error);
    }

    public void onStateChange(Stream stream) {
        getStateDelegate().onStateChange(stream);
    }

    @SuppressWarnings("ControlFlowStatementWithoutBraces")
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StreamCommons)) return false;
        final StreamCommons that = (StreamCommons) o;
        return identifier.equals(that.identifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier);
    }
}
