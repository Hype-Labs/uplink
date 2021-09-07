package com.uplink.ulx.drivers.commons.model;

import com.uplink.ulx.drivers.model.IOResult;
import com.uplink.ulx.drivers.model.InputStream;
import com.uplink.ulx.drivers.model.Stream;
import com.uplink.ulx.utils.ByteUtils;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * InputStreamCommons extends on the StreamCommons abstraction to include input
 * buffer management and I/O delegate notifications. This is still an abstract
 * base class that is to be inherited by transport-specific implementations,
 * since the transport-specific requestAdapterToOpen and requestAdapterToClose
 * methods are still left for child classes.
 */
public abstract class InputStreamCommons extends StreamCommons implements InputStream, InputStream.Delegate {

    private WeakReference<Delegate> delegate;
    private boolean dataAvailableNeeded;

    /**
     * Constructor. Initializes with the given arguments. By default, this also
     * initializes the stream to trigger hasDataAvailable delegate notifications
     * as soon as data arrives.
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
        this.dataAvailableNeeded = true;
    }

    /**
     * This method changes the boolean that flags the need for hasDataAvailable
     * delegate notifications. When set, this flag gives indication that the
     * implementation should issue hasDataAvailable notifications, when
     * convenient. When false, the implementation will know that the buffer is
     * being read in sequence, and therefore the notification is not to be
     * issued.
     * @param dataAvailableNeeded Whether the hasDataAvailable notification is
     *                            to be triggered the next time that data is
     *                            made available to the stream.
     */
    private void setDataAvailableNeeded(boolean dataAvailableNeeded) {
        this.dataAvailableNeeded = dataAvailableNeeded;
    }

    /**
     * Returns a boolean flag indicating whether an hasDataAvailable delegate
     * notification is needed for the stream to request the delegate to read
     * more data. This will be true once the stream has depleted all content
     * and more data having arrived and appended to the buffer.
     * @return Whether to trigger hasDataAvailable notifications on the delegate.
     */
    private boolean isDataAvailableNeeded() {
        return this.dataAvailableNeeded;
    }

    /**
     * Reads data from the input buffer onto the given buffer, returning the
     * number of bytes read and a possible error to flag problems with the
     * operation. The stream's buffer will be truncated by the amount of bytes
     * in the destination buffer; it's notable that this data will copied to
     * the destination buffer and removed from the input buffer, which means
     * that it will be lost and cannot be read again. If the buffer becomes
     * depleted, this will also flag the stream to trigger an hasDataAvailable
     * delegate notification the next time that data is made available.
     * @param destination The destination buffer for the copy.
     * @return The result (IOResult) of the operation.
     */
    private IOResult readBuffer(byte[] destination) {
        /*
        synchronized (getBufferLock()) {

            byte[] source = getBuffer();

            // Copy at most the number of bytes corresponding to the shortest
            // of the two buffers
            int byteCount = Math.min(source.length, destination.length);

            // Copy the buffer content from the source to the destination
            System.arraycopy(
                    source, 0,
                    destination, 0,
                    byteCount
            );

            // Eliminate the bytes read from the source
            source = Arrays.copyOfRange(
                    source,
                    byteCount,
                    source.length
            );

            // Flag dataAvailableNeeded and reset the buffer
            if (source.length == 0) {
                setDataAvailableNeeded(true);
                setBuffer(null);
            } else {
                setDataAvailableNeeded(false);
                setBuffer(source);
            }

            return new IOResult(byteCount, null);
        }

         */
        return null;
    }

    /**
     * This method is called by child classes to give indication that new data
     * has arrived and is to be processed. The implementation will append the
     * data to the input buffer and trigger an hasDataAvailable delegate
     * notification if one is needed. This should trigger the necessary
     * processes to read the data from the buffer.
     * @param data The data to append and process.
     */
    public final void dataReceived(byte[] data) {
        /*
        synchronized (getBufferLock()) {
            addDataToBuffer(data);
            if (isDataAvailableNeeded()) {
                hasDataAvailable(this);
            }
        }

         */
    }

    /**
     * Appends the given data to the input buffer. If a buffer has not been
     * allocated yet, it will be now.
     * @param data The data to append.
     */
    private void addDataToBuffer(byte [] data) {
        /*
        synchronized (getBufferLock()) {
            byte[] buffer = ByteUtils.concatenateByteArrays(getBuffer(), data);
            setBuffer(buffer);
        }

         */
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
    public final IOResult read(byte[] buffer) {

        if (getState() != State.OPEN) {
            throw new RuntimeException("Could not read from the InputStream because the stream is not open");
        }

        if (buffer == null) {
            throw new RuntimeException("Could not read from the InputStream because the destination buffer is null");
        }

        if (buffer.length == 0) {
            throw new RuntimeException("Could not read from the InputStream because the destination buffer is zero-length");
        }

        return readBuffer(buffer);
    }

    @Override
    public void hasDataAvailable(InputStream inputStream) {
        InputStream.Delegate delegate = this.getDelegate();
        if (delegate != null) {
            delegate.hasDataAvailable(this);
        }
    }
}
