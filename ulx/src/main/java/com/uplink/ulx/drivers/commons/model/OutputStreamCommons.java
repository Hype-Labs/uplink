package com.uplink.ulx.drivers.commons.model;

import android.annotation.SuppressLint;
import android.util.Log;

import com.uplink.ulx.UlxError;
import com.uplink.ulx.drivers.model.IoResult;
import com.uplink.ulx.drivers.model.OutputStream;
import com.uplink.ulx.threading.Dispatch;

import java.util.List;
import java.util.Vector;

/**
 * This class implements the part of functionality that is shared by all
 * OutputStream implementations. It will mostly handle buffering, and enable
 * base classes to interact with that buffer for the purposes of I/O. It
 * implements the OutputStream interface, which in turn extends the Stream
 * interface, meaning that this constitutes a Stream in all effect.
 */
public abstract class OutputStreamCommons extends StreamCommons implements OutputStream {

    private List<Callback> callbacks;
    private Buffer buffer;

    /**
     * Constructor. Initializes with given arguments.
     * @param identifier An identifier used for JNI bridging and debugging.
     * @param transportType The stream's transport type.
     * @param reliable A boolean flag, indicating whether the stream is reliable.
     */
    public OutputStreamCommons(
            String identifier,
            int transportType,
            boolean reliable
    ) {
        super(identifier, transportType, reliable);

        this.callbacks = null;
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
        synchronized (this) {
            if (callbacks == null) {
                callbacks = new Vector<>();
            }
        }
        callbacks.add(callback);
    }

    private List<Callback> getCallbacks() {
        return callbacks;
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

    @SuppressLint("NewApi") // forEach() is actually supported
    private void notifyHasSpaceAvailable() {
        List<Callback> callbacks = this.getCallbacks();
        if (callbacks != null) {
            callbacks.forEach(callback -> callback.onSpaceAvailable(this));
        }
    }

    @Override
    public IoResult write(byte[] data) {

        if (getState() != State.OPEN) {
            throw new RuntimeException("Could not write to the OutputStream because the stream is not open");
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

        Dispatch.post(() -> {

            synchronized (getBuffer().getLock()) {

                byte[] data = getBuffer().getData();

                // Ask the stream to flush data
                IoResult result = flush(data);

                // Trim the buffer
                getBuffer().trim(result.getByteCount());

                final UlxError error = result.getError();
                if (error != null) {
                    Log.w(
                            getClass().getCanonicalName(),
                            String.format(
                                    "Output stream failed to flush buffer data. Cause: %s\nInvalidating...",
                                    error.getReason()
                            )
                    );

                    notifyInvalidated(error);
                }
            }
        });
    }

    @SuppressLint("NewApi")// forEach() is actually supported
    protected final void notifyInvalidated(UlxError error) {
        // TODO make sure StateDelegate.onClose() is also called after this method
        final List<InvalidationCallback> callbacks = getInvalidationCallbacks();
        if (callbacks != null) {
            callbacks.forEach(invalidationCallback -> invalidationCallback.onInvalidation(
                    this,
                    error
            ));
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
