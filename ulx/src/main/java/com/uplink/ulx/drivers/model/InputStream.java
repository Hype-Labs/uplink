package com.uplink.ulx.drivers.model;

/**
 * An InputStream is one that is capable of reading data. Input streams work by
 * enabling read operations with read(byte []). This method should be called in
 * sequence until the IOResult returned by it yields a byte count of zero, in
 * which case the stream is known to have been fully read. When that happens,
 * callers should not attempt to read any more data from it, and instead wait
 * for a delegate call to hasDataAvailable(InputStream), which will indicate
 * that the stream is then available to receive more read operations. An error
 * when reading from the stream does not invalidate it, but it still holds that
 * the implementation should wait for a delegate call before attempting to read
 * data again.
 */
public interface InputStream extends Stream {

    /**
     * An InputStream.Delegate receives notifications as to when the stream has
     * data available for reading.
     */
    interface Delegate {

        /**
         * This delegate call is triggered when the stream receives data and
         * successfully buffered it locally for the implementation to read.
         * When this callback is called, the implementation should read data
         * from the stream as soon as possible, since the data will be taking
         * up memory in the buffer until that happens.
         * @param inputStream The InputStream issuing the notification.
         */
        void hasDataAvailable(InputStream inputStream);
    }

    /**
     * Setter for the input stream's delegate. If another delegate has
     * previously been set, it will be overridden.
     * @param delegate The delegate to set.
     */
    void setDelegate(InputStream.Delegate delegate);

    /**
     * Returns the current delegate that is getting notifications from the
     * stream, with respect to data being available for reading.
     * @return The InputStream's current delegate.
     */
    InputStream.Delegate getDelegate();

    /**
     * Reads data from the stream onto the given buffer. The stream will attempt
     * to read at most buffer.length bytes, and never exceed that amount. If the
     * stream does not hold, at the moment, enough information to fill the
     * buffer, it will read as much as it can, writing to the buffer, and
     * returning a byte count (IOResult.getByteCount()) that is lower than the
     * buffer's length. If the operation fails, the IOResult will contain an
     * error (UlxError), accessible with IOResult.getError(). In that case, the
     * buffer is not guaranteed to have been left untouched, and some info
     * might already have been read to it. However, this data is not guaranteed
     * to hold its integrity, and therefore should be discarded. Since streams
     * buffer the data locally before being read, this method should be called
     * as soon as data is known to be available (Delegate.hasDataAvailable()),
     * preventing the stream from holding up memory. If the read operation
     * returns a byte count (IOResult.getByteCount()) that is equal to the
     * buffer's length, the stream may still hold additional data, and therefore
     * the implementation should attempt to read again, even without getting a
     * delegate notification for that purpose. In other words, implementations
     * should read from the stream until IOResult.getByteCount() returns zero
     * or results in an error. After the data is read, it will be cleared from
     * the stream's buffer; this means that it becomes unrecoverable and cannot
     * be read again.
     * @param buffer The buffer to read data into.
     * @return The IOResult for the read operation.
     * @see InputStream.Delegate
     * @see IOResult
     */
    IOResult read(byte [] buffer);
}
