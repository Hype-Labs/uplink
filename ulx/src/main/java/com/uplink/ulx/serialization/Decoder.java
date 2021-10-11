package com.uplink.ulx.serialization;

import com.uplink.ulx.UlxError;

import java.io.IOException;

/**
 * A {@link Decoder} is a class that, given a byte array, attempts to interpret
 * that byte array as the encoded version of an object type that it recognizes.
 * This corresponds to decoding data received from the network into local object
 * instances.
 */
public interface Decoder {

    /**
     * This class represents the result of a decoding operation, by yielding
     * an {@link Object} that was decoded or an {@link UlxError}, if the
     * operation failed.
     */
    class Result {

        private final Object object;
        private final int byteCount;
        private final UlxError error;

        /**
         * Constructor.
         * @param object The {@link Object} that was decoded.
         * @param byteCount The number of bytes that were consumed by the
         *                  operation.
         * @param error An error {@link UlxError}, if the operation failed.
         */
        public Result(Object object, int byteCount, UlxError error) {
            this.object = object;
            this.byteCount = byteCount;
            this.error = error;
        }

        /**
         * Returns the {@link Object} that was decoded as a result of the
         * operation. If the operation failed, this will return code {@code
         * null} and {@link #getError()} will return non-{@code null} instead.
         * @return The {@link Object} that was decoded.
         */
        public final Object getObject() {
            return this.object;
        }

        /**
         * Returns the number of bytes that were processed by the operation in
         * order to decode the object. This corresponds to the actual amount of
         * bytes consumed from the stream.
         * @return The number of bytes consumed from the stream.
         */
        public final int getByteCount() {
            return this.byteCount;
        }

        /**
         * An error, that is given in case the operation fails. In case of
         * success, this method returns {@code null}.
         * @return An error for the operation or {@code null}.
         */
        public final UlxError getError() {
            return this.error;
        }
    }

    /**
     * Attempts to interpret the given data as an object of a recognized type.
     * If no such type is recognized, this method returns {@code null}.
     * @param data The data to interpret.
     * @return A {@link Result} representing the result of the operation.
     * @throws IOException If an exception occurs while attempting to read the
     * data.
     */
    Result decode(byte[] data) throws IOException;

    /**
     * Returns the version identifier used by the {@link Decoder}.
     * @return The version identifier.
     */
    int getVersion();
}
