package com.uplink.ulx.drivers.commons.model;

import com.uplink.ulx.UlxError;
import com.uplink.ulx.UlxErrorCode;
import com.uplink.ulx.drivers.model.IoResult;
import com.uplink.ulx.drivers.model.OutputStream;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import androidx.annotation.CallSuper;
import timber.log.Timber;

/**
 * This class implements the part of functionality that is shared by all
 * OutputStream implementations. It will mostly handle buffering, and enable
 * base classes to interact with that buffer for the purposes of I/O. It
 * implements the OutputStream interface, which in turn extends the Stream
 * interface, meaning that this constitutes a Stream in all effect.
 */
public abstract class OutputStreamCommons extends StreamCommons implements OutputStream {

    private final List<Callback> callbacks;
    private Buffer buffer;

    /**
     * Constructor. Initializes with given arguments.
     *
     * @param identifier        An identifier used for JNI bridging and debugging.
     * @param transportType     The stream's transport type.
     * @param reliable          A boolean flag, indicating whether the stream is reliable.
     */
    public OutputStreamCommons(
            String identifier,
            int transportType,
            boolean reliable
    ) {
        super(identifier, transportType, reliable);

        this.callbacks = new CopyOnWriteArrayList<>();
        this.buffer = null;
    }

    /**
     * Returns the buffer that is being used for the stream to cache output
     * data. If the buffer hasn't been created yet, it will now.
     * @return The stream's buffer.
     */
    protected synchronized final Buffer getBuffer() {
        if (this.buffer == null) {
            this.buffer = new Buffer(0);
        }
        return this.buffer;
    }

    @Override
    public final void addCallback(Callback callback) {
        callbacks.add(callback);
    }

    @Override
    public void removeCallback(Callback callback) {
        callbacks.remove(callback);
    }

    protected void onSpaceAvailable() {

        synchronized (getBuffer().getLock()) {

            // An empty buffer means that we tell the delegate that we're ready
            // to make more data.
            if (getBuffer().isEmpty()) {
                notifyHasSpaceAvailable();
            }

            // A non-empty buffer means that we keep writing
            else flushAndTrim();
        }
    }

    private void notifyHasSpaceAvailable() {
        for (Callback callback : callbacks) {
            callback.onSpaceAvailable(this);
        }
    }

    @Override
    public IoResult write(byte[] data) {

        // This can happen if the stream got closed while the packet was being dispatched
        if (getState() != State.OPEN) {
            final UlxError error = new UlxError(
                    UlxErrorCode.STREAM_IS_NOT_OPEN,
                    "Cannot write data to stream",
                    "Stream is not open",
                    "Open the stream or use another one"
            );
            return new IoResult(0, error);
        }

        if (data == null) {
            throw new RuntimeException("Could not write to the OutputStream because the destination buffer is null");
        }

        if (data.length == 0) {
            throw new RuntimeException("Could not write to the OutputStream because the origin buffer is zero-length");
        }

        int byteCount;

        // Write to buffer
        synchronized (getBuffer().getLock()) {
            byteCount = getBuffer().append(data);
            flushAndTrim();
        }

        // Return the number of bytes buffered, not actually written
        return new IoResult(byteCount, null);
    }

    private void flushAndTrim() {

        synchronized (getBuffer().getLock()) {

            byte[] data = getBuffer().getData();

            // Ask the stream to flush data
            final IoResult result = flush(data);

            // Trim the buffer
            getBuffer().trim(result.getByteCount());

            final UlxError error = result.getError();
            if (error != null) {
                Timber.w(
                        "Output stream failed to flush buffer data. Cause: %s\nInvalidating...",
                        error.getReason()
                );

                // Since we are invalidating the stream, let's also clear its buffer
                // This will ensure that we won't try to flush the same data anymore
                final int bytesLeft = data.length - result.getByteCount();
                if (bytesLeft > 0) {
                    getBuffer().trim(bytesLeft);
                }

                close(error);
            }
        }
    }

    @Override
    @CallSuper
    public void onClose(UlxError error) {
        notifyInvalidatedAndClosed(error);
        super.onClose(error);
    }

    /**
     * Cleans up used resources and notifies callbacks that the stream is invalidated and closed
     * @param error an error describing cause of the shutdown
     */
    private void notifyInvalidatedAndClosed(UlxError error) {
        final List<InvalidationCallback> callbacks = getInvalidationCallbacks();
        if (callbacks != null) {
            for (InvalidationCallback invalidationCallback : callbacks) {
                invalidationCallback.onInvalidation(this, error);
            }
        }
        final StateDelegate stateDelegate = getStateDelegate();
        if (stateDelegate != null) {
            stateDelegate.onClose(this, error);
        }
    }

    /**
     * Asks the stream to flush as much data as it can from the given byte
     * array. This means that the implementation will attempt to actually write
     * the data to whatever is its output medium. The result of the operation
     * should be notified on the {@link OutputStreamCommons} through the
     * {@link Callback} API interface.
     * @param data The data to write.
     * @return The {@link IoResult} for the operation.
     */
    protected abstract IoResult flush(byte[] data);
}
