package com.uplink.ulx.drivers.controller;

import android.content.Context;
import com.uplink.ulx.UlxError;
import com.uplink.ulx.drivers.model.Connector;
import com.uplink.ulx.drivers.model.Device;

import java.util.List;

/**
 * An advertiser is responsible for publishing a device on the network over a
 * specific type of transport. For example, a BLE Advertiser will be responsible
 * for sending out BLE advertisement packets, making the host device visible to
 * others on the network. The specificities of how that's performed is
 * transport-specific.
 */
public interface Advertiser {

    /**
     * Advertiser.State is an enumeration equivalent to State, with the
     * exception that it is used exclusively to represent Advertiser states.
     * This may change in the future, and the two implementations can be merged
     * together. The Advertiser.State instance represents the type of activities
     * that an advertiser is performing at any given time. For example, if the
     * advertiser's state is State.STARTING, then the transport technologies
     * that it supports are initiating, and the device is preparing to become
     * visible on the network by advertising itself.
     */
    enum State {

        /**
         * The advertiser is idle and no activity is expected from it until it
         * is requested to start. The device is not visible on the network nor
         * will it be until the advertiser is started.
         */
        IDLE(0),

        /**
         * The advertiser is starting, meaning that it's preparing itself to
         * become active on the network. This state is expected to change as
         * soon as the advertiser is known to be somehow publishing the device
         * on the network, making it visible to others, or otherwise have
         * failed to do so.
         */
        STARTING(1),

        /**
         * The advertiser being in a RUNNING state means that the device is
         * somehow actively advertising itself on the network. The device is
         * visible to others, meaning that new connections can be established
         * at any time. This state is not expected to change unless the
         * advertiser is requested to stop or otherwise forced to do so by the
         * system.
         */
        RUNNING(2),

        /**
         * The advertiser is stopping, meaning that it is still advertising the
         * host device on the network, but in the process of not doing that
         * anymore. For now, new connections may still occur, but it's possible
         * that not all features are active anymore. This state is expected to
         * change soon.
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
     * This represents a general purpose delegate that receives notifications
     * from the {@link Advertiser} that are not related with state or network
     * events.
     */
    interface Delegate {

        /**
         * Requests the {@link Delegate} to process a restart on the adapter.
         * Since the delegate for the {@link Advertiser} should be the {@link
         * Driver}, the delegate is expected to manage a {@link Browser} as
         * well, which means that it's capable of checking whether there's
         * activity occurring that shouldn't be terminated.
         */
        void onAdapterRestartRequest(Advertiser advertiser);
    }

    /**
     * A StateDelegate keeps track of events associated with the advertiser's
     * state. This includes all state changes, such as starting and stopping.
     * This can be useful to track the advertiser's lifecycle.
     */
    interface StateDelegate {

        /**
         * The advertiser has started. This means that the device is actively
         * participating on the network over the transport implemented by the
         * advertiser, and its state is currently State.RUNNING.
         * @param advertiser The advertiser triggering the notification.
         */
        void onStart(Advertiser advertiser);

        /**
         * The advertiser has stopped. This means that the device is not
         * participating on the network over the transport implemented by it,
         * nd its state is currently State.IDLE. If the stoppage was requested
         * from the advertiser, then the error parameter will be null,
         * indicating a successful response to the stop request. When the
         * advertiser stops because it was forced to do so, such as when the
         * adapter is turned off by the user, the error parameter will indicate
         * a probable cause for the failure.
         * @param advertiser The advertiser triggering the notification.
         * @param error An error indicating the
         */
        void onStop(Advertiser advertiser, UlxError error);

        /**
         * The advertiser failed to start. This means that a request for the
         * advertiser to start resulted in an error, and that the advertiser
         * cannot perform its duties. The error parameter will indicate a
         * probable cause for the failure. The advertiser's state is currently
         * State.IDLE.
         * @param advertiser The advertiser issuing the notification.
         * @param error An error indicating the cause for the failure.
         */
        void onFailedStart(Advertiser advertiser, UlxError error);

        /**
         * This notification is issued after a start request results in an
         * error, and the implementation identifies that the causes for the
         * failure might no longer apply. This is common, for example, when
         * the advertiser fails to start because the adapter is turned off, in
         * which case it will subscribe to adapter state changes and wait for
         * the adapter to become enabled. In that case, this notification is
         * triggered by the advertiser, giving the implementation the choice
         * as to whether to try to start the advertiser again. The
         * implementation, thus, will not try to start, and rather ask the
         * delegate over what to do.
         * @param advertiser The advertiser issuing the notification.
         */
        void onReady(Advertiser advertiser);

        /**
         * This notification is triggered when the advertiser changes state.
         * This method can be used to track state changes on the advertiser,
         * although the other state-specific delegate methods are often
         * preferred. It's notable, for example, that this method does not
         * provide error information.
         * @param advertiser The advertiser changing state.
         */
        default void onStateChange(Advertiser advertiser) {
        }
    }

    /**
     * A NetworkDelegate is one that receives notifications for devices being
     * found and lost on the network. From an advertiser perspective, this
     * refers to devices that have found the host device and initiated a
     * connection.
     */
    interface NetworkDelegate {

        /**
         * Indication that another device has connected to the host device,
         * and that the initial procedures to open communication procedures
         * have been completed. The device should be ready to communicate.
         * @param advertiser The advertiser issuing the notification.
         * @param device The device being found on the network.
         */
        void onDeviceFound(Advertiser advertiser, Device device);

        /**
         * This notification is issued when communication with a device that
         * has previously been announced by the advertiser is no longer
         * possible. At this point, any attempts to communicate with the given
         * device are no longer possible, and thus proper clean up is in order.
         * @param advertiser The advertiser issuing the notification.
         * @param device The device that is no longer reachable.
         * @param error An error indicating the cause for the device being lost.
         */
        void onDeviceLost(Advertiser advertiser, Device device, UlxError error);
    }

    /**
     * This identifier is used to uniquely identify advertisers throughout
     * the system, such as when using JNI.
     * @return The advertiser's identifier.
     */
    String getIdentifier();

    /**
     * Returns the advertiser's transport type, as indicated by the
     * TransportType class and its associated numeric constants. An advertiser
     * may represent a single or many transports, the return value of this
     * method being composed of several transports ORed together.
     * @return The advertiser's transport type.
     */
    int getTransportType();

    /**
     * Returns the Android Context that is associated with the advertiser, and
     * that is used to interact with the Android environment. This context must
     * be set even if the advertisers do not require it to operate.
     * @return The Android environment context.
     * @see Context
     */
    Context getContext();

    /**
     * Returns the advertiser's current state, according to the Advertiser.State
     * enumeration.
     * @return The advertiser's state.
     * @see Advertiser.State
     */
    Advertiser.State getState();

    /**
     * Sets the general purpose {@link Advertiser.Delegate} that will receive
     * future general purpose notifications from the {@link Advertiser}.
     * @param delegate The {@link Advertiser.Delegate} to set.
     */
    void setDelegate(Advertiser.Delegate delegate);

    /**
     * Returns a strong reference to a previously set {@link
     * Advertiser.Delegate}. If no delegate was set, this method returns null.
     * @return The {@link Advertiser.Delegate}.
     */
    Advertiser.Delegate getDelegate();

    /**
     * Sets the StateDelegate instance that is to get notifications for state
     * change events occurring in the advertiser. If another delegate has
     * previously been set, this will replace it.
     * @param stateDelegate The StateDelegate to set.
     * @see Advertiser.StateDelegate
     */
    void setStateDelegate(Advertiser.StateDelegate stateDelegate);

    /**
     * Returns the StateDelegate instance that is currently getting
     * notifications for the advertiser's state changes. If no delegate was
     * previously set, this method returns null.
     * @return The advertiser's current StateDelegate.
     * @see Advertiser.StateDelegate
     */
    Advertiser.StateDelegate getStateDelegate();

    /**
     * Setter for the advertiser's network delegate (NetworkDelegate). After
     * this method is called, the given instance will start getting
     * notifications for devices being found and lost on the network over the
     * advertiser's transport. This corresponds to devices that see the
     * advertiser's advertisement packets and initiate a connection.
     * @param networkDelegate The NetworkDelegate to set.
     * @see Advertiser.NetworkDelegate
     */
    void setNetworkDelegate(Advertiser.NetworkDelegate networkDelegate);

    /**
     * Getter for the advertiser's NetworkDelegate, which gets notifications for
     * found and lost devices. If no delegate was previously set, this method
     * returns null.
     * @return The advertiser's network delegate (NetworkDelegate).
     * @see Advertiser.NetworkDelegate
     */
    Advertiser.NetworkDelegate getNetworkDelegate();

    /**
     * Requests the advertiser to start. When running, the advertiser will be
     * visible to the rest of network, and this method triggers the procedures
     * to do just that. This may include broadcasting advertisement packets but
     * also other procedures, depending on how the technology works. After this
     * method is called, the advertiser will be in a STARTING state if it was
     * previously IDLE. If the advertiser is already RUNNING, then the state is
     * unaffected, and rather the StateDelegate gets an onStart(Advertiser)
     * notification as if the advertiser had just started.
     */
    void start();

    /**
     * If the advertiser is currently publishing the device on the network to
     * make it visible to others, calling this method will initiate the
     * necessary procedures to stop doing so. This may include stopping the
     * broadcast of advertising packets, or otherwise clearing some registry
     * where the device is visible, which is the case of Infrastructure WiFi.
     * Calling this method is not supposed to terminate ongoing connections,
     * although that is not necessarily true in all systems.
     */
    void stop();

    /**
     * Destroying an advertiser consists of stopping it while at the same
     * closing any active connections that it has previously established. The
     * implementations should also clear any cache and in-memory storage that
     * it currently holds, effectively clearing everything that was constructed
     * from having previously started.
     */
    void destroy();

    /**
     * Lists the active connections that are managed by the {@link Advertiser}.
     * All {@link Connector}s in the list should have state {@link
     * Connector.State#CONNECTED}.
     * @return The list of active {@link Connector}s.
     */
    List<Connector> getActiveConnectors();
}
