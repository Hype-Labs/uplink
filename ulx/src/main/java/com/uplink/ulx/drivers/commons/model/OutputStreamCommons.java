package com.uplink.ulx.drivers.commons.model;

import com.uplink.ulx.drivers.model.IOResult;
import com.uplink.ulx.drivers.model.OutputStream;
import com.uplink.ulx.drivers.model.Stream;

import java.lang.ref.WeakReference;
import java.util.Objects;

/**
 * This class implements the part of functionality that is shared by all
 * OutputStream implementations. It will mostly handle buffering, and enable
 * base classes to interact with that buffer for the purposes of I/O. It
 * implements the OutputStream interface, which in turn extends the Stream
 * interface, meaning that this constitutes a Stream in all effect.
 */
public abstract class OutputStreamCommons extends StreamCommons implements OutputStream, OutputStream.Delegate {

    private WeakReference<OutputStream.Delegate> delegate;

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
        OutputStream.Delegate delegate = this.getDelegate();
        if (delegate != null) {
            delegate.hasSpaceAvailable(this);
        }
    }

    /**
     * This method removes the given byte count from the output buffer. The new
     * buffer will be as long as the bytes that are still remaining in the
     * current buffer. The given byte count (which is assumed to have already
     * been flushed) is discarded by copying the remaining bytes on the stream.
     * The discarded data corresponds to the first byteCount bytes in the buffer.
     * @param byteCount The number of bytes to discard.
     */
    private void removeBytesFromBuffer(int byteCount) {
        synchronized (getBufferLock()) {
            byte[] newBuffer = new byte[getBuffer().length - byteCount];
            System.arraycopy(getBuffer(), byteCount, newBuffer, 0, getBuffer().length - byteCount);
            setBuffer(newBuffer);
        }
    }

    @Override
    public IOResult write(byte[] buffer) {

        if (getState() != State.OPEN) {
            throw new RuntimeException("Could not write to the OutputStream because the stream is not open");
        }

        if (buffer == null) {
            throw new RuntimeException("Could not write to the OutputStream because the destination buffer is null");
        }

        if (buffer.length == 0) {
            throw new RuntimeException("Could not write to the OutputStream because the origin buffer is zero-length");
        }

        return write(buffer);
    }
}
