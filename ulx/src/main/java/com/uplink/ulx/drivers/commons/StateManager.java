package com.uplink.ulx.drivers.commons;

import com.uplink.ulx.UlxError;
import com.uplink.ulx.model.State;
import com.uplink.ulx.utils.BiConsumer;
import com.uplink.ulx.utils.Consumer;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A StateManager is the entity responsible for managing the state of many
 * classes through the SDK implementation. Each stateful class (e.g. Connector,
 * Advertiser, Browser, etc), should encapsulate a state manager and subscribe
 * to its delegate (i.e. implement StateManager.Delegate). When requested for
 * some sort of activity, such as starting and stopping, those classes should
 * instead propagate the request to the state manager, which performs some state
 * verifications before committing the request through the delegate, with
 * requestStart and requestStop. This is mostly meant to prevent the overlap of
 * multiple simultaneous requests. When those activities are completed, the
 * implementations should again notify the StateManager of the outcome, through
 * one of the notifyXXX methods. For each situation, the StateManager will look
 * at the instance's state and decide how to act on the event.
 */
public class StateManager {

    /**
     * A StateManager Delegate is one that receives event notifications that
     * respect to a stateful entity's lifecycle. Two of its methods (those that
     * begin with "request") are instructions given by the state manager to
     * commit actions; for example, when the StateManager calls requestStart,
     * it's telling the delegate that all state checks were performed and that
     * the start request should be committed (executed). The request of the
     * methods are state change updates, and correspond to the ones that begin
     * with "on".
     */
    public interface Delegate {

        /**
         * Ask the stateful entity to commit a start request. When this method
         * is called, all the necessary state assertions have already been
         * performed and two start requests are known to not overlap.
         * @param stateManager The StateManager issuing the notification.
         */
        void requestStart(StateManager stateManager);

        /**
         * Ask the stateful entity to commit a stop request. When this method
         * is called, all the necessary state assertions have already been
         * performed and two stop requests are known to not overlap.
         * @param stateManager The StateManager issuing the notification.
         */
        void requestStop(StateManager stateManager);

        /**
         * This notification is triggered by the state manager after a start
         * request is known to have completed successfully.
         * @param stateManager The StateManager issuing the notification.
         */
        void onStart(StateManager stateManager);

        /**
         * This notification is triggered by the state manager after the
         * stateful entity is known to have stopped. If this is occurring in
         * response to a successful stop request, then the error parameter will
         * be null. However, it the stateful entity is being forced to stop
         * (even if during the process of a stop request), the error parameter
         * will indicate a probable cause for the error.
         * @param stateManager The StateManager issuing the notification.
         * @param error An error, indicating a probable cause for a failure, if
         *              one exists.
         */
        void onStop(StateManager stateManager, UlxError error);

        /**
         * This notification is issued by the state manager after the stateful
         * entity is known to have failed to start to perform its duties. The
         * implementation will not attempt to recover from the failure, but
         * rather instead notify the delegate, which should decide on how to
         * act.
         * @param stateManager The StateManager issuing the notification.
         * @param error An error, indicating the cause for the failure.
         */
        void onFailedStart(StateManager stateManager, UlxError error);

        /**
         * This notification is triggered when the state manager goes through
         * a state update. This method can be used instead of the other state-
         * specific versions, although those are often preferred. It's
         * noticeable, for example, that this method does not have any form
         * of error information.
         * @param stateManager The StateManager issuing the notification.
         */
        default void onStateChange(StateManager stateManager) {
        }
    }

    private State state;
    private State requestedState;
    private final Delegate delegate;

    /**
     * Creates {@link StateManager} instance given optional callbacks. (See {@link Delegate} for
     * description of the methods)
     *
     * @return the new instance of {@link StateManager}
     */
    public static StateManager createInstance(
            @Nullable Consumer<StateManager> requestStart,
            @Nullable Consumer<StateManager> requestStop,
            @Nullable Consumer<StateManager> onStart,
            @Nullable BiConsumer<StateManager, UlxError> onStop,
            @Nullable BiConsumer<StateManager, UlxError> onFailedStart,
            @Nullable Consumer<StateManager> onStateChanged
    ) {
        //noinspection deprecation
        return new StateManager(createDelegate(
                requestStart,
                requestStop,
                onStart,
                onStop,
                onFailedStart,
                onStateChanged
        ));
    }

    /**
     * Constructor. Initializes with the given delegate, keeping a weak reference to it. It also
     * sets the current and requested states to IDLE.
     *
     * @param delegate The state manager's delegate.
     * @deprecated use {@link #createInstance(Consumer, Consumer, Consumer, BiConsumer, BiConsumer, Consumer)}
     * instead, which allows to provide optional method references to private methods
     */
    @Deprecated
    public StateManager(Delegate delegate) {

        Objects.requireNonNull(delegate);

        this.state = State.IDLE;
        this.requestedState = State.IDLE;
        this.delegate = delegate;
    }

    /**
     * Updates the stateful entity's state. If the state corresponds to an
     * actual update (e.g. it differs from the one that is already set), a
     * delegate notification will be issued to indicate that fact.
     * @param state The state to set.
     */
    public void setState(State state) {

        boolean triggerDelegate = false;

        synchronized (this) {
            if (this.state != state) {
                this.state = state;
                triggerDelegate = true;
            }
        }

        // Don't keep the lock during the delegate call
        if (triggerDelegate) {
            getDelegate().onStateChange(this);
        }
    }

    /**
     * Returns the stateful entity's current state.
     * @return The current state.
     */
    public synchronized final State getState() {
        return this.state;
    }

    /**
     * Getter for the state manager's delegate.
     * @return The state manager's delegate.
     * @see Delegate
     */
    private Delegate getDelegate() {
        return this.delegate;
    }

    /**
     * Updates the StateManager's requested state, which is used to keep track
     * of the last state requested from the instance. For example, if the state
     * manager is requested to stop while still completing a start request, the
     * previous request will not be interrupted (because, in many cases, it
     * can't), and instead the StateManager will keep note that a stoppage was
     * requested. As soon as any activity is completed, the StateManager will
     * query the requested state in order to identify whether the current state
     * should be reversed. That being the case, the StateManager will trigger
     * the appropriate delegate call to request the change from the stateful
     * entity.
     * @param requestedState The last requested state.
     */
    private void setRequestedState(State requestedState) {
        this.requestedState = requestedState;
    }

    /**
     * Getter for the StateManager's requested state, which corresponds to the
     * last of the start/stop calls to have been performed. When the stateful
     * entity is no longer busy, having finished a previous request, this value
     * will be queried in order to figure out whether to revert the state.
     * @return The StateManager's last requested state.
     */
    private State getRequestedState() {
        return this.requestedState;
    }

    /**
     * Asks the state manager to start. The state manager will keep RUNNING as
     * the last requested state and perform some state checks in order to decide
     * on how to act. Upon successful (when the manager is IDLE), the stateful
     * entity will be requested to start (Delegate.requestStart(StateManager)).
     * When STARTING or STOPPING nothing happens, and instead the state manager
     * waits for the outcome of the operation before deciding whether to ask
     * the managed entity to revert it. When already running the state manager
     * doesn't do anything, but rather notifies the delegate of an onStart()
     * event, as if the stateful entity had just started; this guarantees that
     * the function call is met with a delegate response.
     */
    public synchronized void start() {

        Delegate delegate = getDelegate();
        setRequestedState(State.RUNNING);

        switch (getState()) {

            case IDLE: {
                setState(State.STARTING);
                delegate.requestStart(this);
                break;
            }

            case RUNNING: {
                // Already running, notify as if just started
                delegate.onStart(this);
                break;
            }

            case STARTING:
            case STOPPING: {
                break;
            }
        }
    }

    /**
     * Asks the state manager to process a stop request on the stateful entity.
     * The state manager will keep IDLE as the last requested state, and, when
     * the stateful entity stops being busy performing the activity that it is
     * performing, the state manager will decide on whether to revert that
     * result. When RUNNING, the stop request will pass through, causing a
     * state update and requestStop(StateManager) call on the delegate. When
     * STARTING or STOPPING, the StateManager will instead just update the
     * requested state, and wait for those operations to complete. The IDLE
     * state does not cause a state change, and instead the delegate is
     * immediately notified with an onStop() event, as if the stateful entity
     * had just stopped. This guarantees that each call to stop() is met with
     * a delegate response.
     */
    public synchronized void stop() {

        Delegate delegate = getDelegate();
        setRequestedState(State.IDLE);

        switch (getState()) {

            case IDLE: {
                delegate.onStop(this, null);
                break;
            }

            case RUNNING: {
                // Already running, notify as if just started
                setState(State.STOPPING);
                delegate.requestStop(this);
                break;
            }

            case STARTING:
            case STOPPING: {
                break;
            }
        }
    }

    /**
     * Notifies the state manager that the managed entity has started. If this
     * is the result of a successful call to start(), the state will be updated
     * and the delegate notified. This is also true for spontaneous starts,
     * which are allowed. Since the stateful entity is assumed to be RUNNING
     * at this point, the state manager will decide whether to revert the state
     * by asking the managed entity to stop, if needed.
     */
    public synchronized void notifyStart() {

        Delegate delegate = getDelegate();

        switch (getState()) {

            case STARTING:      // Requested
            case IDLE: {        // Spontaneous

                // The start is spontaneous (e.g. was not requested)
                setState(State.RUNNING);

                if (getRequestedState() == getState()) {
                    delegate.onStart(this);
                }

                break;
            }

            case RUNNING:
            case STOPPING: {
                break;
            }
        }

        // Revert?
        if (getRequestedState() == State.IDLE) {
            stop();
        }
    }

    /**
     * This method is called by the stateful entity to notify the state manager
     * that the implementation has stopped. If this stoppage is occurring as a
     * result of a successful stop request, the error parameter must be null.
     * Otherwise, the error parameter must be given. The state manager will
     * decide on what to do depending on the current state and on the requested
     * state, as well as whether the notification is a result of an error
     * situation.
     * @param error An error, possibly indicating a forced stoppage, as well as
     *              the cause for it.
     */
    public synchronized void notifyStop(@Nullable UlxError error) {

        Delegate delegate = getDelegate();

        if (error != null) {

            switch (getState()) {

                case STOPPING:          // Requested (but with error)
                case RUNNING: {         // Spontaneous

                    setState(State.IDLE);
                    delegate.onStop(this, error);

                    break;
                }

                case STARTING:

                    setState(State.IDLE);
                    delegate.onFailedStart(this, error);
                    break;

                case IDLE: {    // Spontaneous, but not running or trying to
                    break;
                }
            }
        } else {

            switch (getState()) {

                case RUNNING: {

                    if (getRequestedState() == getState()) {
                        setState(State.IDLE);
                        delegate.onStop(this, null);
                    }

                    break;
                }

                case STOPPING: {

                    setState(State.IDLE);

                    if (getRequestedState() == getState()) {
                        delegate.onStop(this, null);
                    }

                    break;
                }

                case STARTING: {

                    // We're told that we've stopped while in a starting state,
                    // but no error was given.
                    throw new RuntimeException("Unexpected stop notification " +
                            "must have an error.");
                }

                case IDLE: {
                    break;
                }
            }

            // Revert?
            if (getRequestedState() == State.RUNNING) {
                start();
            }
        }
    }

    /**
     * This notification is called by the stateful entity in order to give
     * indication to the StateManager that it could not complete a response to
     * a start request. The state manager will check its current state and act
     * accordingly. This consists of notifying a delegate onFailedStart() when
     * the state is STARTING, which means that this is being called in response
     * to a start request. All other states are no-ops, expect if the failure
     * is being given in response to a spontaneous start, in which case the
     * delegate will still be notified. The error parameter is mandatory.
     * @param error An error indication the cause for the failure.
     */
    public synchronized void notifyFailedStart(UlxError error) {

        Delegate delegate = getDelegate();

        if (getRequestedState() == State.RUNNING) {

            switch (getState()) {

                case STARTING: {

                    setState(State.IDLE);
                    delegate.onFailedStart(this, error);

                    break;
                }

                case IDLE:
                case RUNNING:
                case STOPPING: {
                    break;
                }
            }
        } else {
            setState(State.IDLE);
            delegate.onStop(this, error);
        }
    }

    @NonNull
    private static Delegate createDelegate(
            @Nullable Consumer<StateManager> requestStart,
            @Nullable Consumer<StateManager> requestStop,
            @Nullable Consumer<StateManager> onStart,
            @Nullable BiConsumer<StateManager, UlxError> onStop,
            @Nullable BiConsumer<StateManager, UlxError> onFailedStart,
            @Nullable Consumer<StateManager> onStateChanged
    ) {
        return new Delegate() {
            @Override
            public void requestStart(StateManager stateManager) {
                if (requestStart != null) {
                    requestStart.accept(stateManager);
                }
            }

            @Override
            public void requestStop(StateManager stateManager) {
                if (requestStop != null) {
                    requestStop.accept(stateManager);
                }
            }

            @Override
            public void onStart(StateManager stateManager) {
                if (onStart != null) {
                    onStart.accept(stateManager);
                }
            }

            @Override
            public void onStop(StateManager stateManager, UlxError error) {
                if (onStop != null) {
                    onStop.accept(stateManager, error);
                }
            }

            @Override
            public void onFailedStart(StateManager stateManager, UlxError error) {
                if (onFailedStart != null) {
                    onFailedStart.accept(stateManager, error);
                }
            }

            @Override
            public void onStateChange(StateManager stateManager) {
                if (onStateChanged != null) {
                    onStateChanged.accept(stateManager);
                } else {
                    Delegate.super.onStateChange(stateManager);
                }
            }
        };
    }
}
