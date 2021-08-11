package com.uplink.ulx.utils;

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
}
