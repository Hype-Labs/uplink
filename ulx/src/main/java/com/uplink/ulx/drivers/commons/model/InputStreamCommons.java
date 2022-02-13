package com.uplink.ulx.drivers.commons.model;

import android.util.Log;

import com.uplink.ulx.drivers.model.IoResult;
import com.uplink.ulx.drivers.model.InputStream;
import com.uplink.ulx.drivers.model.Stream;

import java.lang.ref.WeakReference;

/**
 * InputStreamCommons extends on the StreamCommons abstraction to include input
 * buffer management and I/O delegate notifications. This is still an abstract
 * base class that is to be inherited by transport-specific implementations,
 * since the transport-specific requestAdapterToOpen and requestAdapterToClose
 * methods are still left for child classes.
 */
public abstract class InputStreamCommons extends StreamCommons implements InputStream, InputStream.Delegate {

    private WeakReference<Delegate> delegate;
    private Buffer buffer;

    /**
     * Constructor. Initializes with the given arguments.
     * @param identifier An identifier used for JNI bridging and debugging.
     * @param transportType The stream's transport type.
     * @param reliable A boolean flag, indicating whether the stream is reliable.
     * @param invalidationDelegate The stream's InvalidationDelegate.
     */
    public InputStreamCommons(
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
     * Returns the buffer that is used by the stream to queue content that is
     * received while it is not read by the implementation.
     * @return The stream's buffer.
     */
    private Buffer getBuffer() {
        if (this.buffer == null) {
            this.buffer = new Buffer(0);
        }
        return this.buffer;
    }

    /**
     * This method is called by child classes to give indication that new data
     * has arrived and is to be processed. The implementation will append the
     * data to the input buffer and trigger an onDataAvailable delegate
     * notification if one is needed. This should trigger the necessary
     * processes to read the data from the buffer.
     * @param data The data to append and process.
     */
    protected final void notifyDataReceived(byte[] data) {

        Log.i(getClass().getCanonicalName(), String.format("ULX input stream %s received %d bytes of data", getIdentifier(), data.length));

        // The onDataAvailable event is only triggered if data is being
        // appended to an empty buffer
        boolean isDataAvailableNeeded;

        // Append the data
        synchronized (getBuffer().getLock()) {
            isDataAvailableNeeded = getBuffer().isEmpty();
            getBuffer().append(data);
        }

        // Propagate the onDataAvailable() event, if needed
        if (isDataAvailableNeeded) {
            onDataAvailable(this);
        }
    }

    @Override
    public void setDelegate(InputStream.Delegate delegate) {
        this.delegate = new WeakReference<>(delegate);
    }

    @Override
    public final InputStream.Delegate getDelegate() {
        return this.delegate.get();
    }

    @Override
    public final IoResult read(byte[] buffer) {

        if (getState() != State.OPEN) {
            throw new RuntimeException("Could not read from the InputStream because the stream is not open");
        }

        if (buffer == null) {
            throw new RuntimeException("Could not read from the InputStream because the destination buffer is null");
        }

        if (buffer.length == 0) {
            throw new RuntimeException("Could not read from the InputStream because the destination buffer is zero-length");
        }

        int byteCount;

        // Consume the data
        synchronized (getBuffer().getLock()) {
            byteCount = getBuffer().consume(buffer);
        }

        return new IoResult(byteCount, null);
    }

    @Override
    public void onDataAvailable(InputStream inputStream) {
        InputStream.Delegate delegate = getDelegate();
        if (delegate != null) {
            delegate.onDataAvailable(this);
        }
    }
}
