package com.uplink.ulx.drivers.commons.model;

import com.uplink.ulx.UlxError;
import com.uplink.ulx.UlxErrorCode;
import com.uplink.ulx.drivers.model.InputStream;
import com.uplink.ulx.drivers.model.IoResult;

import timber.log.Timber;

/**
 * InputStreamCommons extends on the StreamCommons abstraction to include input
 * buffer management and I/O delegate notifications. This is still an abstract
 * base class that is to be inherited by transport-specific implementations,
 * since the transport-specific requestAdapterToOpen and requestAdapterToClose
 * methods are still left for child classes.
 */
public abstract class InputStreamCommons extends StreamCommons implements InputStream {

    private volatile Delegate delegate;
    /**
     * The buffer that is used by the stream to queue content that is received while it is not read
     * by the implementation.
     */
    private final Buffer buffer;

    /**
     * Constructor. Initializes with the given arguments.
     * @param identifier An identifier used for JNI bridging and debugging.
     * @param transportType The stream's transport type.
     * @param reliable A boolean flag, indicating whether the stream is reliable.
     */
    public InputStreamCommons(
            String identifier,
            int transportType,
            boolean reliable
    ) {
        super(identifier, transportType, reliable);

        this.delegate = null;
        this.buffer = new Buffer(0);
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

        Timber.i(
                "ULX input stream %s received %d bytes of data",
                getIdentifier(),
                data.length
        );

        // The onDataAvailable event is only triggered if data is being
        // appended to an empty buffer
        boolean isDataAvailableNeeded;

        // Append the data
        synchronized (buffer.getLock()) {
            isDataAvailableNeeded = this.buffer.isEmpty();
            this.buffer.append(data);
        }

        // Propagate the onDataAvailable() event, if needed
        if (isDataAvailableNeeded) {
            onDataAvailable();
        }
    }

    @Override
    public void setDelegate(InputStream.Delegate delegate) {
        this.delegate = delegate;
    }

    @Override
    public final InputStream.Delegate getDelegate() {
        return delegate;
    }

    @Override
    public final IoResult read(byte[] buffer) {

        if (getState() != State.OPEN) {
            final UlxError error = new UlxError(
                    UlxErrorCode.STREAM_IS_NOT_OPEN,
                    "Could not read from the InputStream",
                    "Stream is not open",
                    "Please wait"
            );
            Timber.e(error.toString());
            return new IoResult(0, error);
        }

        if (buffer == null) {
            throw new RuntimeException("Could not read from the InputStream because the destination buffer is null");
        }

        if (buffer.length == 0) {
            throw new RuntimeException("Could not read from the InputStream because the destination buffer is zero-length");
        }

        int byteCount;

        // Consume the data
        synchronized (this.buffer.getLock()) {
            byteCount = this.buffer.consume(buffer);
        }

        return new IoResult(byteCount, null);
    }

    private void onDataAvailable() {
        InputStream.Delegate delegate = getDelegate();
        if (delegate != null) {
            delegate.onDataAvailable(this);
        }
    }
}
