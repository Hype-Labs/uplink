package com.uplink.ulx.drivers.controller;

import android.content.Context;

import com.uplink.ulx.UlxError;
import com.uplink.ulx.drivers.model.Device;

import androidx.annotation.NonNull;

/**
 * A Driver is an entity that manages a given type of transport. This class
 * works as an abstraction for that transport, usually consisting of a specific
 * implementation of some system framework. A driver may manage more than one
 * such frameworks, and even other drivers, meaning that it can be a powerful
 * abstraction of several types of transports. This is the case of the
 * DriverManager, which creates an abstraction that enables managing all
 * supported drivers as if they were just one. Drivers do not necessarily
 * manage Advertiser and Browser pairs, being that the main difference with
 * TransportDriver.
 * @see TransportDriver
 */
public interface Driver {

    /**
     * A Driver.State is an enumeration equivalent to State, with the exception
     * that it is used exclusively to represent driver states. This may change
     * in the future, and the two implementations can be merged together. The
     * Driver.State instance represents the type of activities that a driver
     * is performing. For example, if the driver's state is State.STARTING,
     * then the transport technologies that it supports are initiating, and the
     * device is preparing to become active on the network.
     */
    enum State {

        /**
         * The driver is idle and no activity is expected from it until it is
         * requested to start. The device is not visible on the network nor
         * will it be until the driver is started.
         */
        IDLE(0),

        /**
         * The driver is starting, meaning that it's preparing itself to become
         * active on the network. This state is expected to change soon, as soon
         * as the driver is known to be somehow participating on the network,
         * or otherwise have failed to do so.
         */
        STARTING(1),

        /**
         * The driver being in a RUNNING state means that the device is somehow
         * actively participating on the network. Other devices can be found
         * at any time. This state is not expected to change unless the driver
         * is requested to stop or otherwise forced to do so by the system.
         */
        RUNNING(2),

        /**
         * The driver is stopping, meaning that it is still participating on
         * the network, but in the process of not doing that anymore. For now,
         * other new devices may still be found, but it's possible that not all
         * features are active anymore. This state is expected to change soon.
         */
        STOPPING(3)
        ;

        private final int value;

        /**
         * Constructor, initializes the enumeration with code values.
         * @param value The value to initialize the enumeration.
         */
        State(int value) {
            this.value = value;
        }

        /**
         * Each enumeration value has an associated numeric value that is
         * determined by convention. This value is used in JNI bridging.
         * @return The numeric value associated with the enumeration instance.
         */
        public int getValue() {
            return this.value;
        }

        /**
         * Converts an integer value to its corresponding enumeration. This
         * value is determined by convention, and can be used for JNI bridging.
         * If the given integer is not recognized as a valid enumeration value,
         * this method returns null.
         * @param value The value to convert.
         * @return The converted value, or null, if that's not possible.
         */
        public static State fromInt(int value) {
            switch (value) {
                case 0: return IDLE;
                case 1: return STARTING;
                case 2: return RUNNING;
                case 3: return STOPPING;
            }

            return null;
        }
    }

    /**
     * A Driver.StateDelegate keeps track of events associated with the driver's
     * state. This includes all state changes, such as starting and stopping.
     * This can be useful to track the driver's lifecycle.
     */
    interface StateDelegate {

        /**
         * The driver has started. This means that the device is actively
         * participating on the network over the transport implemented by the
         * driver, and its state is currently Driver.State.RUNNING.
         * @param driver The driver triggering the notification.
         */
        void onStart(Driver driver);

        /**
         * The driver has stopped. This means that the device is not
         * participating on the network over the transport implemented by the
         * driver, and its state is currently Driver.State.IDLE. If the stoppage
         * was requested from the driver, then the error parameter will be null,
         * indicating a successful response to the stop request. When the driver
         * stops because it was forced to do so, such as when the adapter is
         * turned off by the user, the error parameter will indicate a probable
         * cause for the failure.
         * @param driver The driver triggering the notification.
         * @param error An error indicating the
         */
        void onStop(Driver driver, UlxError error);

        /**
         * The driver failed to start. This means that a request for the driver
         * to start resulted in an error, and that the driver cannot perform
         * its duties. The error parameter will indicate a probable cause for
         * the failure. The driver's state is currently Driver.State.IDLE.
         * @param driver The driver issuing the notification.
         * @param error An error indicating the cause for the failure.
         */
        void onFailedStart(Driver driver, UlxError error);

        /**
         * This notification is issued after a start request results in an
         * error, and the implementation identifies that the causes for the
         * failure might no longer apply. This is common, for example, when
         * the driver fails to start because the adapter is turned off, in
         * which case the driver will subscribe to adapter state changes and
         * wait for the adapter to become enabled. In that case, this
         * notification is triggered by the driver, giving the implementation
         * the choice as to whether to try to start the driver again. The
         * implementation, thus, will not try to start the driver, and rather
         * ask the delegate over what to do. For bundled drivers, such as the
         * DriverManager, this event means that at least one of its managed
         * drivers has become available, in which case the bundled driver may
         * decide what to do based on the state of other drivers. For example,
         * the DriverManager may request the driver to start if any of its
         * other drivers are running as well.
         * @param driver The driver issuing the notification.
         */
        void onReady(Driver driver);

        /**
         * This notification is triggered when the driver changes state. This
         * method can be used to track state changes on the driver, although
         * the other state-specific delegate methods are often preferred. It's
         * notable, for example, that this method does not provide error
         * information.
         * @param driver The driver changing state.
         */
        default void onStateChange(Driver driver) {
        }
    }

    /**
     * A NetworkDelegate is a delegate that gets notifications for other devices
     * being found and lost on the network.
     */
    interface NetworkDelegate {

        /**
         * A device was found on the network. This means that the device is
         * ready to communicate, not just that it's visible by the host.
         * @param driver The driver issuing the notification.
         * @param device The device that was found.
         */
        void onDeviceFound(Driver driver, Device device);

        /**
         * This delegate method is called when a given device is lost on the
         * network. This means that the device is no longer available to
         * communicate, and that all future attempts to do so will fail. The
         * error parameter will give a probable cause the device being lost,
         * such as the device going out of range, or the adapter being turned
         * off.
         * @param driver The driver issuing the notification.
         * @param device The device that was lost.
         * @param error An error (ULXError), indicating the cause for failure.
         */
        void onDeviceLost(Driver driver, Device device, UlxError error);
    }

    /**
     * This identifier is used to uniquely identify drivers throughout the
     * system, such as when using JNI.
     * @return The driver's identifier.
     */
    String getIdentifier();

    /**
     * Returns the driver's transport type, as indicated by the TransportType
     * class and its associated numeric constants. A driver may represent a
     * single or many transports, the return value of this method being composed
     * of several transports ORed together.
     * @return The driver's transport type.
     */
    int getTransportType();

    /**
     * Returns the Android Context that is associated with the driver, and that
     * is used to interact with the Android environment. This context must be
     * set even if the drivers do not require it to operate.
     * @return The Android environment context.
     * @see Context
     */
    @NonNull
    Context getContext();

    /**
     * Returns the driver's current state, according to the Driver.State
     * enumeration.
     * @return The driver's state.
     * @see Driver.State
     */
    State getState();

    /**
     * Sets the StateDelegate instance that is to get notifications for state
     * change events occurring in the driver. If another delegate has previously
     * been set, this will replace it.
     * @param stateDelegate The StateDelegate to set.
     * @see StateDelegate
     */
    void setStateDelegate(StateDelegate stateDelegate);

    /**
     * Returns the StateDelegate instance that is currently getting
     * notifications for the driver's state changes. If no delegate was
     * previously set, this method returns null.
     * @return The driver's current StateDelegate.
     * @see StateDelegate
     */
    StateDelegate getStateDelegate();

    /**
     * Setter for the driver's network delegate (NetworkDelegate). After this
     * method is called, the given instance will start getting notifications
     * for devices being found and lost on the network over the driver's
     * transport.
     * @param networkDelegate The NetworkDelegate to set.
     * @see NetworkDelegate
     */
    void setNetworkDelegate(Driver.NetworkDelegate networkDelegate);

    /**
     * Getter for the driver's NetworkDelegate, which gets notifications for
     * found and lost devices. If no delegate was previously set, this method
     * returns null.
     * @return The driver's network delegate (NetworkDelegate).
     * @see NetworkDelegate
     */
    Driver.NetworkDelegate getNetworkDelegate();

    /**
     * Requests the driver to start. If the driver is already running, then
     * a StateDelegate notification for onStart(Driver) will be called as if
     * the driver had just started. When starting, this method will be a no-op,
     * and the implementation will just keep waiting for the process to
     * complete. In any of the other two states (STOPPING and IDLE), the
     * implementation will attempt to start the driver as soon as possible.
     * After this method is called, the driver's state should be STARTING.
     */
    void start();

    /**
     * Requests the driver to stop. If the driver is already IDLE, then a
     * StateDelegate notification for onStop(Driver, ULXError) will be called
     * as if the driver has just stopped. When STOPPING, this method will be a
     * no-op, and the implementation will just keep waiting for the process to
     * complete. In any of the other two states (RUNNING and STARTING), the
     * implementation will attempt to start the driver as soon as possible.
     * Stopping a driver should not (if possible) terminate any active
     * connections, and communicating with all previously found devices should
     * still be possible. Instead, the device will no longer advertise itself
     * or scan for other devices on the network, meaning that no new devices
     * will be found over direct link for the implemented transport.
     */
    void stop();

    /**
     * Destroying a driver consists of stopping it while at the same closing
     * any active connections that it has previously established. The
     * implementations should also clear any cache and in-memory storage that
     * they currently hold, effectively clearing everything that was constructed
     * from having previously started.
     */
    void destroy();
}
