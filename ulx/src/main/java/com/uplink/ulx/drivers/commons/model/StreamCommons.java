package com.uplink.ulx.drivers.commons.model;

import com.uplink.ulx.UlxError;
import com.uplink.ulx.drivers.commons.StateManager;
import com.uplink.ulx.drivers.model.Stream;

import java.io.ObjectStreamClass;
import java.lang.ref.WeakReference;
import java.util.Objects;

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
        Stream.StateDelegate,
        StateManager.Delegate {

    private final String identifier;
    private final int transportType;
    private final boolean reliable;
    private final StateManager stateManager;
    private WeakReference<StateDelegate> stateDelegate;
    private WeakReference<Stream.InvalidationDelegate> invalidationDelegate;
    private Object bufferLock;
    private byte [] buffer;

    /**
     * Constructor. Initializes with the given arguments.
     * @param identifier An identifier used for JNI bridging and debugging.
     * @param transportType The stream's transport type.
     * @param reliable A boolean flag, indicating whether the stream is reliable.
     * @param invalidationDelegate The stream's InvalidationDelegate.
     */
    public StreamCommons(String identifier, int transportType, boolean reliable, Stream.InvalidationDelegate invalidationDelegate) {

        Objects.requireNonNull(identifier);
        Objects.requireNonNull(invalidationDelegate);

        this.identifier = identifier;
        this.stateManager = new StateManager(this);
        this.transportType = transportType;
        this.reliable = reliable;
        this.invalidationDelegate = new WeakReference<>(invalidationDelegate);
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
     * Returns an object (Object) that is used by the implementation to server
     * as a lock for buffer operations. When reading, writing, copying, or
     * clearing the buffer, this lock must be acquired first.
     * @return The lock object to use for buffer operations.
     */
    protected final synchronized Object getBufferLock() {
        if (this.bufferLock == null) {
            this.bufferLock = new Object();
        }
        return this.bufferLock;
    }

    /**
     * Sets the buffer for the stream. The given buffer may be null, in which
     * case the buffer is entirely cleared. When that happens, the buffer will
     * later be reallocated, when needed. Otherwise, the buffer is kept as a
     * strong reference and held for I/O operations.
     * @param buffer The buffer to set.
     */
    protected final void setBuffer(byte[] buffer) {
        synchronized (getBufferLock()) {
            this.buffer = buffer;
        }
    }

    /**
     * Returns the buffer to use for I/O operations. If the buffer has already
     * been previously allocated and not cleared, this method will allocate a
     * new byte array. If the buffer is non-null, meaning that it was allocated
     * and not cleared, this will return the buffer that already exists.
     * @return The buffer used for I/O operations.
     */
    protected final byte[] getBuffer() {
        synchronized (getBufferLock()) {
            if (this.buffer == null) {
                this.buffer = new byte[0];
            }
            return this.buffer;
        }
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
        requestAdapterToOpen();
    }

    @Override
    public void onStart(StateManager stateManager) {
        StateDelegate stateDelegate = this.getStateDelegate();
        if (stateDelegate != null) {
            stateDelegate.onOpen(this);
        }
    }

    @Override
    public void onStop(StateManager stateManager, UlxError error) {
        StateDelegate stateDelegate = this.getStateDelegate();
        if (stateDelegate != null) {
            stateDelegate.onClose(this, error);
        }
    }

    @Override
    public void requestStop(StateManager stateManager) {
        this.close();
    }

    @Override
    public void onFailedStart(StateManager stateManager, UlxError error) {
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
        getStateManager().start();
    }

    @Override
    public void close() {
        getStateManager().stop();
    }

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

    @Override
    public void onOpen(Stream stream) {
        getStateManager().notifyStart();
    }

    @Override
    public void onClose(Stream stream, UlxError error) {
        getStateManager().notifyStop(error);
    }

    @Override
    public void onFailedOpen(Stream stream, UlxError error) {
        getStateManager().notifyFailedStart(error);
    }

    @Override
    public void onStateChange(Stream stream) {
        getStateDelegate().onStateChange(stream);
    }
}
