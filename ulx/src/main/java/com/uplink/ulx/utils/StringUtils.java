package com.uplink.ulx.utils;

import java.util.UUID;

/**
 * String utilities class, that provides several static utility methods for
 * string management.
 */
public class StringUtils {

    final private static char[] hexArray = "0123456789ABCDEF".toCharArray();

    /**
     * Private constructor prevents instantiation.
     */
    private StringUtils() {
    }

    /**
     * Converts a byte array to its hexadecimal string representation. If the
     * byte array is null or empty, this method returns an empty string.
     * @param bytes The byte array to convert.
     * @return The hexadecimal string representation.
     */
    public static String byteArrayToHexString(byte[] bytes) {

        if (bytes == null || bytes.length == 0) {
            return "";
        }

        char[] hexChars = new char[bytes.length * 2];

        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }

        return new String(hexChars);
    }

    /**
     * Expects a string with an hexadecimal representation and converts it to
     * byte array. Each pair of hex digits is mapped to a byte of the end array.
     * If the string is empty, this method returns a byte array with length
     * zero.
     * @param str The string to convert.
     * @return The byte array corresponding to the given string.
     */
    public static byte[] hexStringToByteArray(String str) {

        int len = str.length();

        byte[] data = new byte[len / 2];

        for (int i = 0 ; i < len ; i += 2) {
            data[i / 2] = (byte) ((Character.digit(str.charAt(i), 16) << 4)
                    + Character.digit(str.charAt(i+1), 16));
        }

        return data;
    }

    /**
     * Checks whether a given string is composed exclusively by a sequence of
     * hexadecimal characters.
     * @param str The string to check.
     * @return Whether the string is hexadecimal.
     */
    public static boolean isHex(String str) {
        return str.matches("^[0-9a-fA-F]+$");
    }

    /**
     * Generates an identifier by creating a random UUID and converting it to
     * a string.
     * @return A random identifier.
     */
    public static String generateRandomIdentifier() {
        return UUID.randomUUID().toString();
    }

    /**
     * Compares the first `size` bytes of the given blocks of memory, returning
     * zero if they match or a value different from zero representing which is
     * greater if they do not. If the given byte arrays are shorter than the
     * given size, the buffer will be read beyond its size.
     * @param lhs A byte sequence.
     * @param rhs Another byte sequence.
     * @param size The amount of bytes to compare.
     * @return A comparison value, indicating whether the two buffers are equal.
     */
    public static int compare(byte[] lhs, byte[] rhs, int size) {

        for (int i = 0 ; i < size ; i++) {
            if (lhs[i] != rhs[i]) {
                if (lhs[i] >= 0 && rhs[i] >= 0)
                    return lhs[i] - rhs[i];
                if (lhs[i] < 0 && rhs[i] >= 0)
                    return 1;
                if (rhs[i] < 0 && lhs[i] >= 0)
                    return -1;
                if (lhs[i] < 0 && rhs[i] < 0) {
                    byte x1 = (byte) (256 + lhs[i]);
                    byte x2 = (byte) (256 + rhs[i]);
                    return x1 - x2;
                }
            }
        }
        return 0;
    }
}
