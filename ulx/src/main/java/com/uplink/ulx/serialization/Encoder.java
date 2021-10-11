package com.uplink.ulx.serialization;

import com.uplink.ulx.UlxError;

import java.io.IOException;

/**
 * An {@link Encoder} converts a given object to an encoded binary (byte array)
 * version. The encoded version should be reversible.
 */
public interface Encoder {

    /**
     * This class is used to encode the results of encoding operations. It
     * contains a byte array to hold the encoded data, which can be {@code null}
     * if an error is present.
     */
    class Result {

        private final byte[] data;
        private final UlxError error;

        /**
         * Constructor.
         * @param data The byte array that resulted from the encoding process.
         * @param error An error, if one exists.
         */
        public Result(byte[] data, UlxError error) {
            this.data = data;
            this.error = error;
        }

        /**
         * Returns the byte array that resulted from the encoding operation.
         * This method can return {@code null} if the operation did not succeed,
         * in which case {@link #getError()} must return non-{@code null}.
         * @return The byte array that resulted from the encoding operation.
         */
        public final byte[] getData() {
            return this.data;
        }

        /**
         * An error, if one exists, that resulted from the encoding operation.
         * This error can be set by the encoder, in which case the {@link
         * #getData()} method should return {@code null}.
         * @return An error, if one exists, that resulted from encoding.
         */
        public final UlxError getError() {
            return this.error;
        }
    }

    /**
     * Encodes the given object as a byte array. This method returns the {@link
     * Result} for the operation, containing the byte array that was the result
     * of the encoding process and possibly an error ({@link UlxError}).
     * @param object The object to encode.
     * @return The {@link Result} of the operation, corresponding to a
     * representation of the object in a binary format or an error.
     * @throws IOException When the object fails to be encoded due to an I/O
     * error.
     */
    Result encode(Object object) throws IOException;

    /**
     * Returns the version identifier used by the {@link Encoder}.
     * @return The version identifier.
     */
    int getVersion();
}
