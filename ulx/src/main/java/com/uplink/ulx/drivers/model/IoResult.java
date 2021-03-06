package com.uplink.ulx.drivers.model;

import com.uplink.ulx.UlxError;

/**
 * A {@link IoResult} holds the result of an input/output operation when either
 * reading from or writing to a stream. It holds a count of bytes that were
 * processed by the operation and an error. If the byte count is zero, than the
 * operation did not read or write any information, although that doesn't
 * necessarily mean that an error has occurred; instead, the stream may be out
 * of data to read, for example. A failed operation is one for which the error
 * information is non-null, in which case the operation is known to have failed.
 */
public class IoResult {

    private final int byteCount;
    private final UlxError error;

    /**
     * Constructor. Initializes with given arguments.
     * @param byteCount The number of bytes processed in the operation.
     * @param error An error, indicating whether the operation succeeded.
     */
    public IoResult(int byteCount, UlxError error) {
        this.byteCount = byteCount;
        this.error = error;
    }

    /**
     * Getter for the byte count, yielding the number of bytes processed by
     * the operation.
     * @return The number of bytes processed by the operation.
     */
    public final int getByteCount() {
        return this.byteCount;
    }

    /**
     * Getter for the error resulting from the operation. If no error occurred,
     * this will return null.
     * @return The error instance generated by the operation, if any.
     */
    public final UlxError getError() {
        return this.error;
    }
}
