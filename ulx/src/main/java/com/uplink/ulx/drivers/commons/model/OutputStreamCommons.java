package com.uplink.ulx.drivers.commons.model;

import com.uplink.ulx.drivers.model.IoResult;
import com.uplink.ulx.drivers.model.OutputStream;
import com.uplink.ulx.drivers.model.Stream;

import java.lang.ref.WeakReference;

/**
 * This class implements the part of functionality that is shared by all
 * OutputStream implementations. It will mostly handle buffering, and enable
 * base classes to interact with that buffer for the purposes of I/O. It
 * implements the OutputStream interface, which in turn extends the Stream
 * interface, meaning that this constitutes a Stream in all effect.
 */
public abstract class OutputStreamCommons extends StreamCommons implements OutputStream, OutputStream.Delegate {

    private WeakReference<OutputStream.Delegate> delegate;
    private Buffer buffer;

    /**
     * Constructor. Initializes with given arguments.
     * @param identifier An identifier used for JNI bridging and debugging.
     * @param transportType The stream's transport type.
     * @param reliable A boolean flag, indicating whether the stream is reliable.
     * @param invalidationDelegate The stream's InvalidationDelegate.
     */
    public OutputStreamCommons(
            String identifier,
            int transportType,
            boolean reliable,
            Stream.InvalidationDelegate invalidationDelegate
    ) {
        super(identifier, transportType, reliable, invalidationDelegate);

        this.delegate = null;
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
    public final void setDelegate(OutputStream.Delegate delegate) {
        this.delegate = new WeakReference<>(delegate);
    }

    @Override
    public final OutputStream.Delegate getDelegate() {
        return this.delegate.get();
    }

    @Override
    public void hasSpaceAvailable(OutputStream outputStream) {

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
        OutputStream.Delegate delegate = this.getDelegate();
        if (delegate != null) {
            delegate.hasSpaceAvailable(this);
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

        synchronized (getBuffer().getLock()) {

            // TODO notice that the data being returned here is not trimmed to
            //      the actual byte count. The data will have the same size as
            //      the buffer's capacity, which is not what is expected when
            //      writing. Instead, the whole buffer should never be returned
            //      to the outside since it can create major problems: e.g.
            //      we write the buffer's to its "capacity", instead of its
            //      "byte count"/size.

            byte[] data = getBuffer().getData();

            // Ask the stream to flush data
            IoResult result = flush(data);

            // Trim the buffer
            getBuffer().trim(result.getByteCount());
        }
    }

    /**
     * Asks the stream to flush as much data as it can from the given byte
     * array. This means that the implementation will attempt to actually write
     * the data to whatever is its output medium. The result of the operation
     * should be notified on the {@link OutputStreamCommons} through the
     * {@link OutputStream.Delegate} API interface.
     * @param data The data to write.
     * @return The {@link IoResult} for the operation.
     */
    protected abstract IoResult flush(byte[] data);
}
