package com.uplink.ulx.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

/**
 * Byte utilities class, that provides several static utility methods for byte
 * and byte array management.
 */
public class ByteUtils {

    /**
     * Allocates fst new byte array and copies the contents of the the two
     * given byte arrays to the new buffer; fst is copied first, followed
     * by snd.
     * @param fst The first byte array to copy.
     * @param snd The second byte array to copy.
     * @return A newly allocated buffer with the concatenated contents.
     */
    public static byte[] concatenateByteArrays(byte[] fst, byte[] snd) {
        byte[] result = new byte[fst.length + snd.length];
        System.arraycopy(fst, 0, result, 0, fst.length);
        System.arraycopy(snd, 0, result, fst.length, snd.length);
        return result;
    }

    /**
     * Converts an UUID to byte array.
     * @param uuid UUID value.
     * @return Encoded into byte array {@link UUID}.
     */
    public static byte[] uuidToBytes(UUID uuid) {
        byte[] bytes = new byte[(Long.SIZE >> 3) * 2];

        uuidToBytes(uuid, bytes, 0);

        return bytes;
    }

    /**
     * Converts {@code UUID} type to byte array and stores it in specified
     * byte array.
     * @param uuid UUID to convert.
     * @param bytes Array of bytes.
     * @param off Offset in {@code bytes} array.
     * @return Number of bytes overwritten in {@code bytes} array.
     */
    public static int uuidToBytes(UUID uuid, byte[] bytes, int off) {

        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes, off, 16);

        byteBuffer.order(ByteOrder.BIG_ENDIAN);

        if (uuid != null) {
            byteBuffer.putLong(uuid.getMostSignificantBits());
            byteBuffer.putLong(uuid.getLeastSignificantBits());
        } else {
            byteBuffer.putLong(0);
            byteBuffer.putLong(0);
        }

        return 16;
    }
}
