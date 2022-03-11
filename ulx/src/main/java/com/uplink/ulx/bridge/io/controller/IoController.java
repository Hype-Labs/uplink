package com.uplink.ulx.bridge.io.controller;

import android.util.Log;

import com.uplink.ulx.UlxError;
import com.uplink.ulx.UlxErrorCode;
import com.uplink.ulx.bridge.io.model.AcknowledgementPacket;
import com.uplink.ulx.bridge.io.model.AcknowledgementPacketDecoder;
import com.uplink.ulx.bridge.io.model.AcknowledgementPacketEncoder;
import com.uplink.ulx.bridge.io.model.DataPacket;
import com.uplink.ulx.bridge.io.model.DataPacketDecoder;
import com.uplink.ulx.bridge.io.model.DataPacketEncoder;
import com.uplink.ulx.bridge.io.model.HandshakePacket;
import com.uplink.ulx.bridge.io.model.HandshakePacketDecoder;
import com.uplink.ulx.bridge.io.model.HandshakePacketEncoder;
import com.uplink.ulx.bridge.io.model.InternetPacket;
import com.uplink.ulx.bridge.io.model.InternetPacketDecoder;
import com.uplink.ulx.bridge.io.model.InternetPacketEncoder;
import com.uplink.ulx.bridge.io.model.InternetResponsePacket;
import com.uplink.ulx.bridge.io.model.InternetResponsePacketDecoder;
import com.uplink.ulx.bridge.io.model.InternetResponsePacketEncoder;
import com.uplink.ulx.bridge.io.model.Packet;
import com.uplink.ulx.bridge.io.model.UpdatePacket;
import com.uplink.ulx.bridge.io.model.UpdatePacketDecoder;
import com.uplink.ulx.bridge.io.model.UpdatePacketEncoder;
import com.uplink.ulx.drivers.commons.model.Buffer;
import com.uplink.ulx.drivers.model.Device;
import com.uplink.ulx.drivers.model.InputStream;
import com.uplink.ulx.drivers.model.IoResult;
import com.uplink.ulx.drivers.model.OutputStream;
import com.uplink.ulx.drivers.model.Stream;
import com.uplink.ulx.serialization.Decoder;
import com.uplink.ulx.serialization.Encoder;
import com.uplink.ulx.serialization.Serializer;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Objects;
import java.util.Queue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The {@link IoController} is the entity that is responsible for converting
 * the stream's binary data into in-memory {@link Packet} objects. This module
 * registers encoders and decoders for all supported types of {@link Packet}s,
 * which will be the fundamental unit of communication between the devices. The
 * packets are encoded into and decoded from binary formats, which is what is
 * used in the communication links. Each packet gets decoded by other devices,
 * also by the {@link IoController}, which then propagates the decoded packet
 * objects through a {@link Delegate}. This class also manages intermediary
 * buffers for the streams. These correspond to buffers that hold the data
 * read from the streams, keeping the streams free. The packets are parsed from
 * those buffers. The current implementation is easy to break because the
 * buffers will keep growing until some sort of memory overflow occurs, although
 * that is something that should be fixed in the future.
 */
public class IoController implements InputStream.Delegate,
                                     OutputStream.Delegate,
                                     Stream.InvalidationCallback {

    /**
     * A {@link IoController.Delegate} yields notifications with respect to
     * packets circulating on the network.
     */
    public interface Delegate {

        /**
         * Event triggered when a {@link Packet} is received and successfully
         * parsed.
         * @param controller The {@link IoController} issuing the notification.
         * @param inputStream The {@link InputStream} that received the packet.
         * @param packet The {@link Packet} that was received.
         */
        void onPacketReceived(IoController controller, InputStream inputStream, Packet packet);

        /**
         * Event triggered to give indication to the {@link Delegate} that the
         * given {@link IoPacket} was written to the given {@link OutputStream}.
         * @param controller The {@link IoController} issuing the notification.
         * @param outputStream The {@link OutputStream} that wrote the packet.
         * @param ioPacket The {@link IoPacket} that was written;
         */
        void onPacketWritten(IoController controller, OutputStream outputStream, IoPacket ioPacket);

        /**
         * Event triggered when a packet could not be sent. This could be
         * because the packet could not be encoded or some issue with the stream.
         * The {@code error} parameter will give an indication of the cause.
         * @param controller The {@link IoController} issuing the notification.
         * @param outputStream The {@link OutputStream} that failed to write.
         * @param ioPacket The {@link IoPacket} that could not be written.
         * @param error An error ({@link UlxError}), indicating a probable cause
         */
        void onPacketWriteFailure(IoController controller, OutputStream outputStream, IoPacket ioPacket, UlxError error);
    }

    /**
     * This class wraps a {@link Packet} and is the entity that is queued by
     * the {@link IoController} when dispatching content. This is meant as a
     * means by which the implementation can keep track of which packets are
     * pending and being delivered. Callers may extend this class to include
     * other meta information, to make the packet tracking more useful.
     */
    public abstract static class IoPacket {

        private final Packet packet;

        /**
         * Constructor.
         * @param packet The {@link Packet} being wrapped.
         */
        public IoPacket(Packet packet) {
            this.packet = packet;
        }

        /**
         * Getter for the {@link Packet} that is being wrapped.
         * @return The wrapped {@link Packet}.
         */
        public final Packet getPacket() {
            return this.packet;
        }

        /**
         * This method should be overridden by callers in order to specify the
         * calculation of the next-hop device. This method will only be called
         * when the packet is actually being dispatched, which makes it the
         * ideal time to check for ideal paths against the routing table or
         * the likes of it. If the next-hop is already known when the packet
         * is first created, that can also be specified by the caller, simply
         * by returning it here. If this method returns {@code null}, the
         * controller will assume that the next-hop is not known, and thus
         * declared the packet dispatch as failed.
         * @return The next-hop {@link Device} for the {@link Packet}.
         */
        @Nullable
        public abstract Device getDevice();

        @NonNull
        @Override
        public String toString() {

            Device device = getDevice();

            return String.format(Locale.ENGLISH, "IoPacket(%s): %s",
                    device == null ? "(null)" : device.getIdentifier(),
                    getPacket().toString()
            );
        }
    }

    private HashMap<String, Buffer> inputMap;
    private WeakReference<Delegate> delegate;
    private Serializer serializer;
    private Queue<IoPacket> queue;
    private IoPacket currentPacket;
    private final Object currentPacketLock = new Object();

    /**
     * Constructor.
     */
    public IoController() {
        this.inputMap = null;
        this.delegate = null;
        this.serializer = null;
        this.queue = null;
        this.currentPacket = null;
    }

    /**
     * Returns the input map, which is used to hold information with respect to
     * input that is being read from the input streams. The streams are
     * identified by the {@code String} key of the map. This data will be held
     * until a full packet can be processed.
     * @return The input map.
     */
    private HashMap<String, Buffer> getInputMap() {
        if (this.inputMap == null) {
            this.inputMap = new HashMap<>();
        }
        return this.inputMap;
    }

    /**
     * Returns the {@link Buffer} for the given {@link InputStream} If one does
     * not exist, one will be created at this point. When created, the buffers
     * have a capacity of 0 (zero) bytes. After being created, this method will
     * deterministically return the same {@link Buffer} for the same {@link
     * InputStream}.
     * @param inputStream The {@link InputStream}.
     * @return The {@link Buffer} corresponding to the given {@link InputStream}.
     */
    private Buffer getBufferForStream(InputStream inputStream) {

        Buffer buffer = getInputMap().get(inputStream.getIdentifier());

        // Allocate a buffer, if needed
        if (buffer == null) {
            getInputMap().put(inputStream.getIdentifier(), buffer = new Buffer(0));
        }

        return buffer;
    }

    /**
     * Returns the serializer that the {@link IoController} uses to encode and
     * decode objects when communicating over the network. This method
     * guarantees that the encoders and decoders for the packet types that are
     * currently used by the implementation are registered with the serializer.
     * Those packet types are: {@link HandshakePacket}, {@link UpdatePacket}, and {@link
     * DataPacket}.
     * @return The {@link Serializer} used by the bridge.
     */
    private Serializer getSerializer() {
        if (this.serializer == null) {
            this.serializer = new Serializer();
            this.serializer.register(HandshakePacket.class, new HandshakePacketEncoder(), new HandshakePacketDecoder());
            this.serializer.register(UpdatePacket.class, new UpdatePacketEncoder(), new UpdatePacketDecoder());
            this.serializer.register(DataPacket.class, new DataPacketEncoder(), new DataPacketDecoder());
            this.serializer.register(AcknowledgementPacket.class, new AcknowledgementPacketEncoder(), new AcknowledgementPacketDecoder());
            this.serializer.register(InternetPacket.class, new InternetPacketEncoder(), new InternetPacketDecoder());
            this.serializer.register(InternetResponsePacket.class, new InternetResponsePacketEncoder(), new InternetResponsePacketDecoder());
        }
        return this.serializer;
    }

    /**
     * Sets the {@link Delegate} that will get notifications from the controller
     * going forward. If a previous delegate has been set, it will be overridden.
     * A weak reference will be kept to that delegate in order to prevent cyclic
     * memory references.
     * @param delegate The {@link Delegate} to set.
     */
    public void setDelegate(Delegate delegate) {
        this.delegate = new WeakReference<>(delegate);
    }

    /**
     * Returns the {@link Delegate} that was previously set to receive
     * notifications from the controller, or {@code null}, if one has not been
     * set.
     * @return The {@link Delegate} or null.
     */
    private Delegate getDelegate() {
        return this.delegate != null ? this.delegate.get() : null;
    }

    /**
     * Returns the {@link Queue} that is managed by the {@link IoController}
     * to keep the order of packets being delivered.
     * @return The {@link IoPacket} {@link Queue}.
     */
    private Queue<IoPacket> getQueue() {
        if (this.queue == null) {
            this.queue = new LinkedList<>();
        }
        return this.queue;
    }

    private void setCurrentPacket(IoPacket currentPacket) {
        this.currentPacket = currentPacket;
    }

    private IoPacket getCurrentPacket() {
        return this.currentPacket;
    }

    /**
     * Queues an {@link IoPacket} to be sent by the {@link IoController} as
     * soon as it's capable of processing new data. The packet will be added
     * to a queue and dispatched when its time comes.
     * @param ioPacket The {@link IoPacket} to queue.
     */
    public void add(IoPacket ioPacket) {
        Log.i(getClass().getCanonicalName(), String.format("ULX queueing packet %s", ioPacket));

        // Queue the packet
        synchronized (getQueue()) {
            getQueue().add(ioPacket);
        }

        // Attempt to process it, if the controller is idle
        attemptDequeue();
    }

    /**
     * This method attempts to dequeue a packet from the {@link IoPacket} {@link
     * Queue}. This happens in synchronization with other queue operations, so
     * those operations will not overlap. If a {@link IoPacket} is successfully
     * dequeued, it will be encoded and written to the reliable {@link
     * OutputStream}, as returned by the {@link Device} returned by {@link
     * IoPacket#getDevice()}. If no packet can be dequeued, this method does
     * nothing, and simply returns.
     */
    private void attemptDequeue() {
        Log.i(getClass().getCanonicalName(), "ULX dequeueing packet");

        IoPacket ioPacket;

        Device device;

        // The loop will break if current packet's device is not null
        while (true) {
            synchronized (currentPacketLock) {
                // If another packet is already being processed, do not proceed, and
                // instead let if finish
                if (getCurrentPacket() != null) {
                    Log.i(getClass().getCanonicalName(), "ULX packet not dequeued because the queue is busy");
                    return;
                }

                // Pop the next packet to dispatch
                synchronized (getQueue()) {
                    ioPacket = getQueue().poll();
                }

                // Don't proceed if the queue is empty
                if (ioPacket == null) {
                    Log.i(getClass().getCanonicalName(), "ULX queue not proceeding because the queue is empty");
                    return;
                }

                Log.i(
                        getClass().getCanonicalName(),
                        String.format("ULX current packet is %s", ioPacket)
                );

                // Flag the controller as busy, so packets do not overlap
                setCurrentPacket(ioPacket);
            }

            device = ioPacket.getDevice();

            if (device != null) {
                break;
            } else {
                Log.e(getClass().getCanonicalName(), "ULX destination not found");

                UlxError error = new UlxError(
                        UlxErrorCode.UNKNOWN,
                        "Could not send a packet to a destination.",
                        "The destination is not known or reachable.",
                        "Try bringing the destination closer to the host " +
                                "device or restarting the Bluetooth adapter."
                );

                // Clear the current packet, so the queue may proceed
                setCurrentPacket(null);

                notifyOnPacketWriteFailure(null, ioPacket, error);
            }
        }

        // Write to the stream
        add(ioPacket, device.getTransport().getReliableChannel().getOutputStream());
    }

    /**
     * This method encodes the given packet using the {@link Serializer}
     * associated with the context {@link IoController}. The given packet type
     * should be recognized by the {@link IoController}. The current form of
     * packet registry is not very dynamic, but that should be changed in the
     * future, in order to support a wider variety of packet types.
     * @param ioPacket The {@link IoPacket} to encode and send.
     * @param outputStream The {@link OutputStream} to write to.
     */
    private void add(IoPacket ioPacket, OutputStream outputStream) {

        Encoder.Result result;

        try {
            result = getSerializer().encode(ioPacket.getPacket());
        } catch (IOException e) {

            // By keeping a null result we're assuming that this exception is
            // the result of the packet being rejected, which is not true. This
            // should be changed in the future.
            result = null;
        }

        // Failing to encode a packet is a programming error, because it means
        // that the proper encoder was not registered or it's the wrong packet
        // type
        if (result == null) {
            throw new RuntimeException(String.format("Failed to encode a packet " +
                    "of type %s. An encoder for the packet type was not " +
                    "registered or the type is not recognized.", ioPacket.getPacket().getClass().getCanonicalName()));
        }

        // An error from the encoder is not considered a programming error
        if (result.getError() != null) {
            notifyOnPacketWriteFailure(outputStream, ioPacket, result.getError());
            return;
        }

        // Write the result
        Log.i(getClass().getCanonicalName(), String.format("ULX-M writing packet %s", ioPacket.toString()));
        outputStream.write(result.getData());
    }

    /**
     * Propagates a notification giving indication that a {@link Packet} was
     * received, by calling the {@link Delegate's} {@link
     * Delegate#onPacketReceived(IoController, InputStream, Packet)}.
     * @param inputStream The {@link InputStream} that received the packet.
     * @param packet The {@link Packet} that was received.
     */
    private void notifyOnPacketReceived(InputStream inputStream, Packet packet) {
        Delegate delegate = getDelegate();
        if (delegate != null) {
            delegate.onPacketReceived(this, inputStream, packet);
        }
    }

    /**
     * Propagates a notification giving indication that the given {@link
     * IoPacket} was written to the given {@link OutputStream}. The packet has
     * not necessarily been delivered, only written to the output stream.
     * @param outputStream The {@link OutputStream} that wrote the packet.
     * @param ioPacket The {@link IoPacket} that was written.
     */
    private void notifyOnPacketWritten(OutputStream outputStream, IoPacket ioPacket) {
        Delegate delegate = getDelegate();
        if (delegate != null) {
            delegate.onPacketWritten(this, outputStream, ioPacket);
        }
    }

    /**
     * Propagates a notification of an {@link
     * Delegate#onPacketWriteFailure(IoController, OutputStream, IoPacket, UlxError)}
     * event to the {@link Delegate}.
     * @param outputStream The {@link OutputStream} that failed to write.
     * @param ioPacket The {@link IoPacket}.
     * @param error An error, describing the cause for the failure.
     */
    private void notifyOnPacketWriteFailure(OutputStream outputStream, IoPacket ioPacket, UlxError error) {
        Delegate delegate = getDelegate();
        if (delegate != null) {
            delegate.onPacketWriteFailure(this, outputStream, ioPacket, Objects.requireNonNull(error));
        }
    }

    @Override
    public void onDataAvailable(InputStream inputStream) {
        Log.i(getClass().getCanonicalName(), String.format("ULX input stream %s has data available", inputStream.getIdentifier()));

        // This is the buffer that will receive the data
        Buffer buffer = getBufferForStream(inputStream);

        Decoder.Result result;

        synchronized (buffer.getLock()) {

            // We're allocating 1024 bytes, being that double the maximum we'd
            // ever need (since it's BLE); but this is a temporary allocation.
            byte[] aux = new byte[1024];

            IoResult ioResult;

            do {

                // Read from the stream
                ioResult = inputStream.read(aux);

                // Append to the buffer, the amount of bytes read
                buffer.append(aux, ioResult.getByteCount());

            } while (ioResult.getByteCount() > 0);

            // The stream is depleted for the moment; either decoding succeeds
            // or we need to wait for more data.
            try {
                Log.i(getClass().getCanonicalName(), String.format("ULX attempting to decode packet from input stream %s", inputStream.getIdentifier()));
                result = getSerializer().decode(buffer);
            } catch (IOException e) {

                // If an exception occurs, we consider it the same as the input
                // being rejected, but this is probably the wrong approach.
                result = null;
            }

            // None of the decoders recognized the packet
            if (result == null) {
                Log.i(getClass().getCanonicalName(), "ULX packet could not be decoded because its type is not recognized.");
                Log.i(getClass().getCanonicalName(), String.format("ULX buffer size is %d", buffer.getOccupiedByteCount()));
                return;
            }

            // The packet's type is recognized, but there's not enough data yet
            // for decoding the packet in full
            if (result.getObject() == null) {
                Log.i(getClass().getCanonicalName(), "ULX packet could not be decoded; waiting for more data.");
                return;
            }

            // Reduce the buffer by the amount decoded
            buffer.trim(result.getByteCount());
        }

        // If this happens, one of the decoders is returning the wrong type,
        // or an unexpected decoder was registered. Either way, it's a
        // programming error.
        if (!(result.getObject() instanceof Packet)) {
            throw new RuntimeException("Decoded an object that is not a " +
                    "subclass of the expected Packet type. This means that a " +
                    "decoder was registered that is not processing packets, " +
                    "which is not supported by the implementation.");
        }

        // Propagate
        notifyOnPacketReceived(inputStream, (Packet)result.getObject());
    }

    @Override
    public void onSpaceAvailable(OutputStream outputStream) {
        Log.d(
                getClass().getCanonicalName(),
                String.format("ULX Space available on stream %s", outputStream.getIdentifier())
        );

        synchronized (currentPacketLock) {
            // If this happens, something's wrong
            assert getCurrentPacket() != null;

            // Notify the delegate
            notifyOnPacketWritten(outputStream, getCurrentPacket());

            // Set as the now active packet
            setCurrentPacket(null);
        }

        // Move on to the next one
        attemptDequeue();
    }

    @Override
    public void onInvalidation(Stream stream, UlxError error) {
        Log.w(
                getClass().getCanonicalName(),
                String.format("ULX Stream %s invalidated. Reason: %s",
                              stream.getIdentifier(),
                              error.getReason()
                )
        );

        final IoPacket currentPacket;

        synchronized (currentPacketLock) {
            currentPacket = getCurrentPacket();

            final Device device = currentPacket != null ? currentPacket.getDevice() : null;

            // Drop current packet if it cannot longer identify its device or
            // if it belongs to the invalidated stream
            if (device == null || stream.equals(device.getTransport().getReliableChannel().getOutputStream())) {
                setCurrentPacket(null);
            }
        }

        if (stream instanceof OutputStream) {
            // Drop all packets using the stream
            dropPacketsForStream((OutputStream) stream);
        }

        if (currentPacket != null) {
            // If an output packet was being processed, proceed with the next one
            attemptDequeue();
        }

        stream.removeInvalidationCallback(this);
    }

    /**
     * Drops all the packets that were intended to be written via the given stream
     *
     * @param stream The failed stream
     */
    private void dropPacketsForStream(OutputStream stream) {
        synchronized (getQueue()) {
            getQueue().removeIf(ioPacket -> {
                final Device device = ioPacket.getDevice();

                // If the packet cannot identify its device - drop the packet
                if (device == null) {
                    return true;
                }

                // If the packet was intended for the stream that failed - drop the packet
                return stream.equals(device.getTransport().getReliableChannel().getOutputStream());
            });
        }
    }
}
