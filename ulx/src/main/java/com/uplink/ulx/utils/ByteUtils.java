package com.uplink.ulx.utils;

import android.os.SystemClock;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import timber.log.Timber;

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

    /**
     * Creates a copy of the given byte array, but only up to the maximum
     * specified amount of bytes. That array will be trimmed to the lowest of
     * {@code data.length} and {@code maxSize}.
     * @param data The data to copy.
     * @param maxSize The maximum size for the output array.
     * @return A partial or total copy of the input {@code data} array.
     */
    public static byte[] trimCopyToSize(byte[] data, int maxSize) {

        byte[] copy = new byte[Math.min(maxSize, data.length)];

        System.arraycopy(data, 0, copy, 0, copy.length);

        return copy;
    }

    public static byte[] compress(byte[] bytes) {
        final Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);

        Timber.v("Compressing %d bytes", bytes.length);
        final long startTimestamp = SystemClock.elapsedRealtime();

        deflater.setInput(bytes);
        deflater.finish();

        int bytesWritten;
        final List<byte[]> buffers = new ArrayList<>();

        do {
            buffers.add(new byte[bytes.length]);
            bytesWritten = deflater.deflate(buffers.get(buffers.size() - 1));
        } while (bytesWritten == bytes.length);

        final byte[] result = new byte[(buffers.size() - 1) * bytes.length + bytesWritten];

        for (int i = 0; i < buffers.size(); i++) {
            System.arraycopy(
                    buffers.get(i),
                    0,
                    result,
                    i * bytes.length,
                    i == buffers.size() - 1 ? bytesWritten : bytes.length
            );
        }

        deflater.end();

        Timber.v(
                "Compressed to %d bytes. Time used: %d ms",
                result.length,
                SystemClock.elapsedRealtime() - startTimestamp
        );

        return result;
    }

    public static byte[] decompress(byte[] bytes) {
        Timber.v("Decompressing %d bytes", bytes.length);
        final long startTimestamp = SystemClock.elapsedRealtime();

        final Inflater decompressor = new Inflater();
        decompressor.setInput(bytes, 0, bytes.length);

        int bytesWritten;
        final List<byte[]> buffers = new ArrayList<>();

        do {
            buffers.add(new byte[bytes.length]);
            try {
                bytesWritten = decompressor.inflate(buffers.get(buffers.size() - 1));
            } catch (DataFormatException e) {
                Timber.e(e, "Failed to decompress bytes");
                bytesWritten = 0;
            }
        } while (bytesWritten == bytes.length);

        decompressor.end();

        final byte[] result = new byte[(buffers.size() - 1) * bytes.length + bytesWritten];

        for (int i = 0; i < buffers.size(); i++) {
            System.arraycopy(
                    buffers.get(i),
                    0,
                    result,
                    i * bytes.length,
                    i == buffers.size() - 1 ? bytesWritten : bytes.length
            );
        }

        Timber.v(
                "Decompressing finished. Result size: %d. Time: %d ms",
                result.length,
                SystemClock.elapsedRealtime() - startTimestamp
        );

        return result;
    }
}
