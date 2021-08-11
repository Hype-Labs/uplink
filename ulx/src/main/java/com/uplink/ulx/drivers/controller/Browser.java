package com.uplink.ulx.drivers.controller;

import android.content.Context;

import com.uplink.ulx.UlxError;
import com.uplink.ulx.drivers.model.Device;

/**
 * A browser is responsible for looking for advertisement packets and other
 * forms of advertisement that are being broadcast on the network, or that
 * live in some registry of some sort. For example, a BLE Browser will be
 * responsible reading BLE advertisement packets, informing the host device
 * of other devices that are publishing themselves on the network. The
 * specificities of how that's performed is transport-specific.
 */
public interface Browser {

    /**
     * Browser.State is an enumeration equivalent to State, with the
     * exception that it is used exclusively to represent Browser states.
     * This may change in the future, and the two implementations can be merged
     * together. The Browser.State instance represents the type of activities
     * that a browser is performing at any given time. For example, if the
     * browser's state is State.STARTING, then the transport technologies
     * that it supports are initiating, and the device is preparing to start
     * scanning the network for other devices.
     */
    enum State {

        /**
         * The browser is idle and no activity is expected from it until it
         * is requested to start. The device is not scanning for other devices
         * on the network nor will it be until the browser is requested to
         * start.
         */
        IDLE(0),

        /**
         * The browser is starting, meaning that it's preparing itself to scan
         * the network for other devices advertising themselves. This state is
         * expected to change as soon as the browser is known to be somehow
         * scanning the network, or otherwise have failed to do so.
         */
        STARTING(1),

        /**
         * The browser being in a RUNNING state means that the device is
         * somehow actively scanning for others on the network. This means that
         * new connections can be established at any time. This state is not
         * expected to change unless the browser is requested to stop or
         * otherwise forced to do so by the system.
         */
        RUNNING(2),

        /**
         * The browser is stopping, meaning that it might still be scanning for
         * other devices on the network, but the process to stop doing so has
         * already begun. For now, new connections may still occur, but it's
         * possible that not all features are active anymore. This state is
         * expected to change soon.
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
     * A StateDelegate keeps track of events associated with the browser's
     * state. This includes all state changes, such as starting and stopping.
     * This can be useful to track the browser's lifecycle.
     */
    interface StateDelegate {

        /**
         * The browser has started. This means that the device is actively
         * scanning the network for other devices over the transport implemented
         * by it, and its state is currently State.RUNNING.
         * @param browser The browser triggering the notification.
         */
        void onStart(Browser browser);

        /**
         * The browser has stopped. This means that the device is not scanning
         * for other devices advertising themselves on the network over the
         * transport implemented by it, and its state is currently State.IDLE.
         * If the stoppage was requested, then the error parameter will be null,
         * indicating a successful response to the stop request. When the
         * browser stops because it was forced to do so, such as when the
         * adapter is turned off by the user, the error parameter will indicate
         * a probable cause for the failure.
         * @param browser The browser triggering the notification.
         * @param error An error indicating the
         */
        void onStop(Browser browser, UlxError error);

        /**
         * The browser failed to start. This means that a request for the
         * browser to start resulted in an error, and that the browser cannot
         * perform its duties. The error parameter will indicate a probable
         * cause for the failure. The browser's state is currently State.IDLE.
         * @param browser The browser issuing the notification.
         * @param error An error indicating the cause for the failure.
         */
        void onFailedStart(Browser browser, UlxError error);

        /**
         * This notification is issued after a start request results in an
         * error, and the implementation identifies that the causes for the
         * failure might no longer apply. This is common, for example, when
         * the browser fails to start because the adapter is turned off, in
         * which case it will subscribe to adapter state changes and wait for
         * the adapter to become enabled. In that case, this notification is
         * triggered by the browser, giving the implementation the choice
         * as to whether to try to start the browser again. The implementation,
         * thus, will not try to start, and rather ask the delegate what to do.
         * @param browser The browser issuing the notification.
         */
        void onReady(Browser browser);

        /**
         * This notification is triggered when the browser changes state. This
         * method can be used to track state changes on the browser, although
         * the other state-specific delegate methods are often preferred. It's
         * notable, for example, that this method does not provide error
         * information.
         * @param browser The browser changing state.
         */
        default void onStateChange(Browser browser) {
        }
    }

    /**
     * A NetworkDelegate is one that receives notifications for devices being
     * found and lost on the network. From a browser perspective, this refers
     * to devices that are advertising themselves and were found by the scanner,
     * having already established a connection afterwards.
     */
    interface NetworkDelegate {

        /**
         * This delegate call indicates that the scanner has found another
         * device actively advertising itself, and that the host device has
         * already connected to it. The device should be ready to communicate.
         * @param browser The browser issuing the notification.
         * @param device The device being found on the network.
         */
        void onDeviceFound(Browser browser, Device device);

        /**
         * This notification is issued when communication with a device that
         * has previously been announced by the browser is no longer possible.
         * At this point, any attempts to communicate with the given device
         * will fail, and thus proper clean up is in order.
         * @param browser The browser issuing the notification.
         * @param device The device that is no longer reachable.
         * @param error An error indicating the cause for the device being lost.
         */
        void onDeviceLost(Browser browser, Device device, UlxError error);
    }

    /**
     * This identifier is used to uniquely indentify browsers throughout the
     * system, such as when using JNI.
     * @return The browser's identifier.
     */
    String getIdentifier();

    /**
     * Returns the browser's transport type, as indicated by the TransportType
     * class and its associated numeric constants. A browser may represent a
     * single or many transports, the return value of this method being composed
     * of several transports ORed together.
     * @return The browser's transport type.
     */
    int getTransportType();

    /**
     * Returns the Android Context that is associated with the browser, and
     * that is used to interact with the Android environment. This context must
     * be set even if the browser does not require it to operate.
     * @return The Android environment context.
     * @see Context
     */
    Context getContext();

    /**
     * Returns the browser's current state, according to the Browser.State
     * enumeration.
     * @return The browser's state.
     * @see Browser.State
     */
    Browser.State getState();

    /**
     * Sets the StateDelegate instance that is to get notifications for state
     * change events occurring in the browser. If another delegate has
     * previously been set, this will replace it.
     * @param stateDelegate The StateDelegate to set.
     * @see Browser.StateDelegate
     */
    void setStateDelegate(Browser.StateDelegate stateDelegate);

    /**
     * Returns the StateDelegate instance that is currently getting
     * notifications for the browser's state changes. If no delegate was
     * previously set, this method returns null.
     * @return The browser's current StateDelegate.
     * @see Browser.StateDelegate
     */
    Browser.StateDelegate getStateDelegate();

    /**
     * Setter for the browser's network delegate (NetworkDelegate). After this
     * method is called, the given instance will start getting notifications for
     * devices being found and lost on the network over the browser's transport.
     * This corresponds to devices that appear in the browser's scan results
     * and for which a connection has already been established.
     * @param networkDelegate The NetworkDelegate to set.
     * @see Browser.NetworkDelegate
     */
    void setNetworkDelegate(Browser.NetworkDelegate networkDelegate);

    /**
     * Getter for the browser's NetworkDelegate, which gets notifications for
     * found and lost devices. If no delegate was previously set, this method
     * returns null.
     * @return The browser's network delegate (NetworkDelegate).
     * @see Browser.NetworkDelegate
     */
    Browser.NetworkDelegate getNetworkDelegate();

    /**
     * Requests the browser to start. When running, the browser will be scanning
     * for other devices on the network, looking for broadcast advertisement
     * packets or whatever is the way in which devices advertise themselves
     * over the given technology stack. After this method is called, the browser
     * will be in a STARTING state if it was previously IDLE. If the browser is
     * already RUNNING, then the state is unaffected, and rather the
     * StateDelegate gets an onStart(Browser) notification as if the browser
     * had just started.
     */
    void start();

    /**
     * If the browser is currently scanning the network for other devices,
     * calling this method will initiate the necessary procedures to stop doing
     * so. Calling this method is not supposed to terminate ongoing connections,
     * although that is not necessarily true for all systems.
     */
    void stop();

    /**
     * Destroying an browser consists of stopping it while at the same closing
     * any active connections that it has previously established. The
     * implementations should also clear any cache and in-memory storage that
     * they hold, effectively clearing everything that was constructed from
     * having previously started.
     */
    void destroy();
}
