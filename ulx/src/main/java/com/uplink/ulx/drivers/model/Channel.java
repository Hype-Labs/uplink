package com.uplink.ulx.drivers.model;

import com.uplink.ulx.TransportType;

/**
 * A Channel is an umbrella abstraction of I/O streams. As it currently stands,
 * channels cannot be used to manage streams as bundles; for example, a channel
 * cannot be requested to "open", and rather that operation must be performed
 * in the streams directly. This might change in the future. For now, the
 * channel merely aggregates I/O stream pairs within the context of a given
 * Device.
 *
 * Channels can be reliable or unreliable, in the sense that they either operate
 * with confirmation of delivery, order, and data integrity, or they don't.
 * Reliable channels operate in a manner that is comparable to that of TCP,
 * while unreliable channels operate in a manner that is comparable to that of
 * UDP.
 */
public interface Channel {

    /**
     * A Channel's identifier is expected to be equal to the Device umbrella
     * under which it lives, may implementations may choose differently. This
     * identifier is used for JNI bridging and debug purposes. Given that more
     * than one Channel may correspond to a single Device, this identifier is
     * not guaranteed to be unique, but it should be unique in comparison to
     * channels that live under a different umbrella Device.
     * @return The Channel's identifier.
     */
    String getIdentifier();

    /**
     * The Channel's transport type corresponds to the transport that it uses
     * when performing I/O. For example, a channel that communicates over
     * Bluetooth Low Energy, will yield TransportType.BLUETOOTH_LOW_ENERGY.
     * @return The Channel's transport type.
     * @see TransportType
     */
    int getTransportType();

    /**
     * A Channel's input stream (InputStream) is one that is capable of
     * receiving data, or reading data from the adapter. This is a getter for
     * the stream that does just that.
     * @return The Channel's input stream.
     * @see InputStream
     */
    InputStream getInputStream();

    /**
     * A Channel's output stream (OutputStream) is one that is capable of
     * performing output operations, such as sending data to another device.
     * This is a getter for the stream that does just that.
     * @return The Channel's output stream.
     * @see OutputStream
     */
    OutputStream getOutputStream();

    /**
     * This getter returns a flag that indicates whether the Channel is
     * reliable. A reliable channel is one that guarantees the order of delivery
     * of data, performs retransmissions when data corruption occurs, checks
     * for data integrity, and so on. A reliable Channel is one with streams
     * that work over protocols that are comparable with TCP, while an
     * unreliable Channel is one that exposes streams that work over protocols
     * that are comparable with UDP.
     * @return Whether the channel is reliable.
     */
    boolean isReliable();
}
