package com.uplink.ulx.drivers.model;

import com.uplink.ulx.UlxError;

import java.util.List;

import androidx.annotation.NonNull;

/**
 * A Connector is an abstract entity that manages connections. Connectors may
 * further be divided into two subtypes: domestic and foreign. A domestic
 * connector is one that is capable of initiating the connection with another
 * device, while a foreign connector is one that passively waits for the other
 * peer to be the initiator. This is common in technologies where devices can't
 * always be the initiators, such as Bluetooth Low Energy; in that case, one of
 * the devices (the central) waits passively for the other (the peripheral) to
 * initiate the connection, since centrals (GATT server) are not capable of
 * doing that themselves, as per the BLE protocol specification.
 *
 * The connectors have two types of delegates: a StateDelegate and an
 * InvalidationCallback. The former (StateDelegate) is useful for tracking the
 * connector's lifecycle events, such as the connector initiating or terminating
 * a connection. The latter (InvalidationCallback), triggers notifications that
 * are related to the moment when the connector becomes invalid, meaning that
 * it no longer serves the purpose of managing connections, and therefore must
 * be disposed.
 */
public interface Connector {

    /**
     * Enumeration for the Connector's state. The state that a connector is in
     * indicates what sort of activity is expected from it. From a DISCONNECTED
     * connector, for example, no activity is expected until one of the peers
     * is requested to initiate the connection. It's notable that connections
     * may be initiated by peers other than the host device, which means that
     * connectors may initiate without being requested to do so.
     */
    enum State {

        /**
         * The connector is not connected, and no activity is expected until
         * one of the peers initiates the connection. This may occur by the
         * device other than the host, which means that this state may change
         * even if the connector is not requested to connect.
         */
        DISCONNECTED(0),

        /**
         * The connector is connecting to a remote peer. This state may or may
         * not be represented when the peer is the one to initiate the
         * connection, meaning that in some cases the state may go directly from
         * DISCONNECTED to CONNECTED, without going through this state first.
         * In any case, implementations should always try to go through this
         * state first, even if just to flag that the transition is occurring.
         */
        CONNECTING(1),

        /**
         * The connector is connected, which means that the streams are open
         * and the channel is capable of performing I/O. This state may change
         * if either of the devices requests the connection to be terminated,
         * or if the connection fails abruptly, such as when the two devices
         * go out of range.
         */
        CONNECTED(2),

        /**
         * This state indicates that the connector is trying to disconnect
         * gracefully, in contrast with the connection being terminated
         * abruptly by abnormal circumstances, such as the devices going out
         * of range. In cases where the other peer is the one to initiate the
         * disconnection, this state may not occur, although implementations
         * should try to force their states to go through it for the sake of
         * maintaining the designed flow.
         */
        DISCONNECTING(3)
        ;

        private final int value;

        /**
         * Constructor initializes the instance with a given value. The value
         * is determined by specification, and corresponds to the states listed
         * by the enumeration.
         * @param value The ordinal value associated with the state.
         */
        State(int value) {
            this.value = value;
        }

        /**
         * Returns the ordinal value associated with the Connector.State
         * instance. This value is determined by specification, and can be
         * used for JNI bridging and debugging purposes.
         * @return The state's ordinal value.
         */
        public int getValue() {
            return this.value;
        }

        /**
         * Converts an ordinal value to its corresponding state, as per a
         * previously determined specification. If the value does not correspond
         * to a known state, this method returns null instead.
         * @param value The ordinal value to convert.
         * @return The state corresponding to the value, or null.
         */
        public static State fromInt(int value) {
            switch (value) {
                case 0: return DISCONNECTED;
                case 1: return CONNECTING;
                case 2: return CONNECTED;
                case 3: return DISCONNECTING;
            }
            return null;
        }
    }

    /**
     * A Connector's StateDelegate receives notifications for the connector's
     * lifecycle events.
     */
    interface StateDelegate {

        /**
         * This event notification is triggered when (1) the connector
         * successfully completes a connection request or (2) the peer has
         * successfully established a connection with the host. In either case,
         * the connector is said to be active, and the streams can be activated
         * and prepared for I/O.
         * @param connector The connector issuing the notification.
         */
        void onConnected(@NonNull Connector connector);

        /**
         * This event notification is triggered when the connection is dropped,
         * either because one of the two peers caused a disconnection, or
         * because the connection could no longer be maintained. When the latter
         * happens, the error parameter should indicate a probable cause for
         * the forced disconnection. That's also true when the peer is the one
         * to initiate the disconnection, since, from the host's perspective,
         * an unwanted disconnection is still an error.
         * @param connector The connector issuing the notification.
         * @param error An error, indicating a probable cause.
         */
        void onDisconnection(@NonNull Connector connector, UlxError error);

        /**
         * This notification is issued to the delegate when a requested
         * connection is not capable of performing successfully. This may
         * occur for several reasons, such as the devices having become out of
         * range, a failed handshake, the adapter having been turned off, and
         * many more. Whichever the reason, the implementation will try to
         * identify the underlying cause, in which case the error parameter
         * will indicate the most likely identified cause for the failure.
         * @param connector The connector issuing the notification.
         * @param error An error, indicating a probable cause.
         */
        void onConnectionFailure(@NonNull Connector connector, UlxError error);

        /**
         * State change events are useful for tracking the connector's
         * lifecycle, since this notification is triggered everytime that the
         * connector's state changes. However, the more specific event-driven
         * delegate callbacks are often preferred, since they contribute to a
         * better segregation of the code as well as error information in some
         * events.
         * @param connector The connector issuing the notification.
         */
        default void onStateChange(Connector connector) {
        }
    }

    /**
     * An InvalidationCallback is one that tracks events as to when the
     * connector becomes invalid, meaning that it is no longer capable of
     * establishing or managing connections. Such delegates are not the same
     * as the StateDelegate because the entity that manages the connector's
     * lifecycle is probably interested in connectors that are capable of
     * performing their duties. The invalidation delegate, on the other hand,
     * handles the point in time in which connectors are no longer valid, and
     * thus proper clean up is in order.
     */
    interface InvalidationCallback {

        /**
         * This notification indicates that the connector has become invalid,
         * and therefore can no longer be used to manage the connection with
         * its corresponding peer. An invalid connector must be properly
         * disposed, and any attempt in requesting activity from it is expected
         * to fail. The error parameter should indicate a cause for the
         * connector's invalidation, which is most commonly either the adapter
         * having been turned off or the devices going out of range.
         * @param connector The connector issuing the notification.
         * @param error An error, indicating a probable cause for the invalidation.
         */
        void onInvalidation(Connector connector, UlxError error);
    }

    /**
     * Returns a unique identifier associated with the Connector for debug and
     * bridging purposes. This identifier is used when crossing the JNI bridge,
     * enabling the implementation to keep track of connectors on maps, in pair
     * with their identifiers.
     * @return The Connector's unique identifier.
     */
    String getIdentifier();

    /**
     * Returns the transport type that the Connector uses. This can be any of
     * transport types listed in TransportType, or any combination of those.
     * The connector will manage connections over the technologies that the
     * transport type corresponds.
     * @return The Connector's transport type.
     */
    int getTransportType();

    /**
     * Returns the Connector's current State, according to the Connector.State
     * enumeration.
     * @return The Connector's state.
     */
    State getState();

    /**
     * Sets the delegate that will get notifications for connector events. By
     * calling this method, if a previous delegate existed, it will be replaced.
     * The given instance will get all notifications from the connector going
     * forward.
     * @param stateDelegate The delegate to set.
     * @see StateDelegate
     */
    void setStateDelegate(StateDelegate stateDelegate);

    /**
     * Getter for the delegate that is currently getting notifications from the
     * connector. This method does not change the delegate in way, or triggers
     * any delegate notifications.
     * @return The current connector delegate.
     * @see StateDelegate
     */
    StateDelegate getStateDelegate();

    /**
     * Add a InvalidationCallback that will get notifications for the
     * connector becoming invalid.
     * @param callback The InvalidationCallback to set.
     * @see InvalidationCallback
     */
    void addInvalidationCallback(InvalidationCallback callback);

    /**
     * Unregisters InvalidationCallback that was previously registered.
     * If an unregistered callback is passed, nothing happens
     * @param callback callback to unregister
     */
    void removeInvalidationCallback(InvalidationCallback callback);

    /**
     * Getter for the InvalidationCallbacks that are designated for
     * receiving invalidation notifications.
     * @return The current InvalidationCallbacks.
     * @see InvalidationCallback
     */
    @NonNull
    List<InvalidationCallback> getInvalidationCallbacks();

    /**
     * Requests the Connector to initiate a connection with the other peer. How
     * that is performed will be transport-specific, meaning that each
     * implementation will behave differently depending on the transport that
     * it proposes to address. In some cases, such as with Bluetooth Low Energy,
     * that might even mean that the device does not initiate a connection at
     * all; for example, if the host device is a central (GATT server), it
     * cannot initiate the connection because the peripheral (GATT client) is
     * the one to have to initiate the connection. In that case, either the
     * attempt to connect fails or the central asks the peripheral to connect,
     * implying the existence of some sort of protocol and the peripheral's
     * cooperation. In other circumstances, such as with Bluetooth Classic,
     * which is purely peer-to-peer, all devices may initiate a connection,
     * and therefore the same rationale does not apply. In order to prevent
     * duplicate connections, the implementations may also have some sort of
     * protocol in place that helps them decide which should be the initiator;
     * for example, the implementation may exchange some sort of identifiers,
     * and the one to initiate is that with the ordinal-largest identifier of
     * the two. The result of the connection request will be informed through
     * a delegate call.
     */
    void connect();

}
