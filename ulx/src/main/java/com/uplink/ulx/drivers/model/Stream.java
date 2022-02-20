package com.uplink.ulx.drivers.model;

import com.uplink.ulx.TransportType;
import com.uplink.ulx.UlxError;

import androidx.annotation.NonNull;

/**
 * A Stream is the main I/O abstraction unit provided by the SDK. A Stream,
 * however, does not have a concrete implementation, since streams must either
 * be input streams (InputStream) or output streams (OutputStream). This means
 * that no concrete implementation directly implements this class, but rather
 * that this abstraction works as basis for the other two. Therefore, a Stream
 * in itself is capable of neither input or output; rather, its InputStream
 * counterpart reads input, while its OutputStream counterpart writes output.
 * Both, however, share this abstraction in common. The abstraction enables
 * state management (Stream.State and Stream.StateDelegate), invalidation
 * observability (Stream.InvalidationDelegate), and actions to open and close
 * the streams, among other utilities.
 */
public interface Stream {

    /**
     * A Stream's state indicates what sort of activity is expected from the
     * stream. An OPEN stream, for example, is capable of performing I/O
     * operations, while a CLOSED stream is not.
     */
    enum State {

        /**
         * The stream is closed and no I/O is possible or expected from it. This
         * state is not expected to change unless the stream is requested to
         * open.
         */
        CLOSED(0),

        /**
         * The stream is closed but already in the process of opening. This
         * means that I/O is not yet possible, and any attempt at writing data
         * to or reading data from the stream is expected to fail. This state
         * is, however, expected to change soon.
         */
        OPENING(1),

        /**
         * The stream is open and I/O is possible at any time. For input streams
         * this means that data can be received at any point, while for output
         * streams this means that data can be written to it. This state is not
         * expected to change until the stream is requested to close, or it
         * forcibly closes by becoming invalid.
         */
        OPEN(2),

        /**
         * The stream is open but has already initiated the processes for
         * closing. All pending I/O may or may not succeed, and new one is
         * expected to fail. This state is expected to change soon.
         */
        CLOSING(3)
        ;

        private final int value;

        /**
         * Constructor. Initializes enumeration instances with the given
         * ordinal value, as per specification.
         * @param value The ordinal value corresponding to the state.
         */
        State(int value) {
            this.value = value;
        }

        /**
         * Getter for the ordinal value corresponding to the state instance.
         * @return The ordinal value corresponding to the state.
         */
        public int getValue() {
            return this.value;
        }

        /**
         * Converts a given ordinal value into its corresponding Stream.State
         * instance, if one exists; otherwise, it returns null.
         * @param value The value to convert.
         * @return The corresponding Stream.State, or null, if none exists.
         */
        public static State fromInt(int value) {
            switch (value) {
                case 0: return CLOSED;
                case 1: return OPENING;
                case 2: return OPEN;
                case 3: return CLOSING;
            }
            return null;
        }
    }

    /**
     * A Stream's StateDelegate is one that receives notifications for stream
     * lifecycle events. Such delegates can track the stream's lifecycle and
     * decide whether to perform I/O according to the stream's state.
     */
    interface StateDelegate {

        /**
         * The stream is open and ready for I/O.
         * @param stream The stream issuing the notification.
         */
        void onOpen(Stream stream);

        /**
         * The stream closed and is no longer capable of performing I/O. Any
         * pending I/O will fail to deliver. If the stream was requested to
         * close and is closing gracefully, the error parameter will be null.
         * If not, the error parameter will indicate a probable cause for the
         * closure; for example, if the stream closes due to an invalidation
         * of the Connector, on account of the devices going out of range.
         * @param stream The stream issuing the notification.
         * @param error An error, indicating the cause for the closure.
         */
        void onClose(Stream stream, UlxError error);

        /**
         * The stream failed to activate, meaning that any I/O performed on it
         * will fail. The error parameter should indicate a probable cause for
         * the failure.
         * @param stream The stream issuing the notification.
         * @param error An error, indicating a probable cause for the failure.
         */
        void onFailedOpen(Stream stream, UlxError error);

        /**
         * This notification is issued every time that the stream changes state,
         * making this method useful for tracking the stream's lifecycle.
         * However, the more event-specific methods on the delegate are often
         * preferred, since this method is generic and does not provide any
         * error information.
         * @param stream The stream issuing the notification.
         */
        default void onStateChange(Stream stream) {
        }
    }

    /**
     * This delegate provides indication that the Stream has become invalid,
     * and this is no longer capable of performing its duties. This delegate is
     * called when the Connector's InvalidationDelegate is called as well.
     * When that happens, the stream should be disposed, since it can no longer
     * serve the purpose that it serves.
     */
    interface InvalidationDelegate {

        /**
         * The Stream became invalid, meaning that it can no longer be used to
         * perform any form of I/O with its corresponding device, now or in
         * the future. When this notification is issued, the implementation
         * should perform proper clean up, since the stream is now
         * unrecoverable. The error parameter indicates a probable cause for
         * the invalidation, such as the devices going out of range, or the
         * adapter being turned off.
         * @param stream The stream issuing the notification.
         * @param error A probable cause for the invalidation.
         */
        void onInvalidation(Stream stream, UlxError error);
    }

    /**
     * An identifier for the stream, often used for JNI bridging and debugging
     * purposes. It's notable that this identifier often corresponds to the
     * Device umbrella under which it lives, which has the implicit implication
     * that it's not globally unique; this is due to the fact that a single
     * Device instance may hold up to four streams. This identifier will be
     * an hexadecimal string.
     * @return The stream's identifier.
     */
    @NonNull
    String getIdentifier();

    /**
     * The stream's transport type indicates the type of transport that the
     * stream uses for I/O. For example, a stream that communicates over BLE
     * will have the TransportType.BLUETOOTH_LOW_ENERGY flag set.
     * @return The stream's transport type.
     * @see TransportType
     */
    int getTransportType();

    /**
     * Getter for the Stream's state. The stream's state indicates what sort
     * of activity is expected from it.
     * @return The stream's state.
     * @see Stream.State
     */
    State getState();

    /**
     * Sets the stream's state delegate (Stream.StateDelegate), which will get
     * notifications for the stream's lifecycle. If any delegate was previously
     * set, it will be overridden. The stream must hold a weak reference to this
     * delegate.
     * @param stateDelegate The StateDelegate to set.
     * @see Stream.StateDelegate
     */
    void setStateDelegate(StateDelegate stateDelegate);

    /**
     * Returns a strong reference to the stream's state delegate (StateDelegate)
     * which is at the moment receiving notifications for the stream's lifecycle
     * events.
     * @return The stream's state delegate.
     * @see Stream.StateDelegate
     */
    StateDelegate getStateDelegate();

    /**
     * Sets the stream's invalidation delegate (Stream.InvalidationDelegate),
     * which will being to receive invalidation event notifications from the
     * stream. If another delegate has previously been set, it will be
     * overridden, and all notifications will be triggered on the new instance.
     * The stream must keep a weak reference to this delegate.
     * @param invalidationDelegate The delegate to set.
     * @see Stream.InvalidationDelegate
     */
    void setInvalidationDelegate(Stream.InvalidationDelegate invalidationDelegate);

    /**
     * Returns a strong reference for the InvalidationDelegate that is currently
     * getting invalidation notifications from the stream. Although a strong
     * reference is returned, the Stream must hold a weak reference to this
     * object.
     * @return The stream's invalidation delegate.
     * @see Stream.InvalidationDelegate
     */
    Stream.InvalidationDelegate getInvalidationDelegate();

    /**
     * Returns a boolean flag indicating whether the stream is reliable. A
     * reliable stream performs delivery acknowledgements, integrity checks,
     * guaranteed order, and so on, while an unreliable does not. Unreliable
     * I/O is not yet supported, so in this version this will always yield true.
     * @return Whether the stream is reliable.
     */
    boolean isReliable();

    /**
     * Requests the stream to open. In some cases, this is actually a no-op,
     * since implementations may not need to open streams at all in order to
     * perform I/O operations. In those cases, the implementation may decide
     * to either flag the stream as open (e.g. StateDelegate.onOpen(Stream))
     * or flag it as closed and wait for the implementation to ask it to open;
     * which one to choose depends on whether the stream is already capable of
     * I/O, since that's the capability that reflects its state. After being
     * requested to open, the stream will perform any necessary procedures to
     * enable I/O between the two peers. However, opening a stream in one
     * direction does not necessarily bear consequences with respect to the
     * other; for example, if an input stream is requested to open, the
     * implementation might become able of receiving input, but not of producing
     * output.
     */
    void open();

    /**
     * Requests the stream close, meaning that it initiates any necessary
     * procedures to stop being active. This means that the stream will no
     * longer be capable of performing any I/O.
     */
    void close();
}
