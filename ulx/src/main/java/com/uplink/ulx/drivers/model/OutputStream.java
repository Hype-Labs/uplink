package com.uplink.ulx.drivers.model;

/**
 * An OutputStream is one that is capable of writing data. In order to do that,
 * implementations call the write(byte []) method on the stream, looking into
 * the returned IOResult to check if the operation has succeeded. The operation
 * will have completed successfully if the number of bytes read (which can be
 * checked with IOResult.getByteCount()) is equal to amount of bytes that are
 * being written to the stream and no error is given. In that case, the caller
 * should keep writing data to the stream until IOResult.getByteCount() returns
 * a byte count that is less than the amount of bytes that is being written.
 * When that happens, the implementation should wait for a delegate call to
 * hasSpaceAvailable(OutputStream), which is an indication that the stream has
 * already flushed enough data to have more space available in its buffers to
 * continue with the operation.
 */
public interface OutputStream extends Stream {

    /**
     * An OutputStream.Delegate receives notifications as to when the stream has
     * data space available in its buffers to receive data for writing.
     */
    interface Delegate {

        /**
         * This callback gives indication to the delegate that the stream has
         * flushed enough data to have space in its buffers to accommodate more
         * data. This means that the stream is capable of processing more write
         * requests, and thus the implementation should write to it, if data is
         * pending to be written.
         * @param outputStream The stream issuing the notification.
         */
        void hasSpaceAvailable(OutputStream outputStream);
    }

    /**
     * Setter for the output stream's delegate. If another delegate has
     * previously been set, it will be overridden.
     * @param delegate The delegate to set.
     */
    void setDelegate(OutputStream.Delegate delegate);

    /**
     * Returns the current delegate that is getting notifications from the
     * stream, with respect to space being available on the stream for writing.
     * @return The InputStream's current delegate.
     */
    OutputStream.Delegate getDelegate();

    /**
     * Writes data to the stream. The data in the given buffer parameter will be
     * copied to a local buffer managed by the stream, before being flushed and
     * actually sent out. The given buffer remains untouched. If the operation
     * is successful, then the returned IOResult will yield a non-zero byte
     * count (IOResult.getByteCount()) and a null error (IOResult.getError()).
     * The byte count returned by the result corresponds to the number of bytes
     * that were actually written, meaning that the difference between the
     * buffer's length and that byte count yields the amount of bytes that
     * couldn't be written. When that happens, the implementation should wait
     * for a delegate call to hasSpaceAvailable(OutputStream) before attempting
     * to write in the stream again. If that difference is zero, however, then
     * the implementation should continue to write data until either all of it
     * is written or the amount of bytes written is lower than the given buffer.
     * The implementation should also wait for the delegate in case of error.
     * @param data The buffer to write to the stream.
     * @return The IOResult of the operation.
     * @see OutputStream.Delegate
     * @see IOResult
     */
    IOResult write(byte [] data);
}
