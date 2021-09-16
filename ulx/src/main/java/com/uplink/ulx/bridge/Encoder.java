package com.uplink.ulx.bridge;

import android.util.Log;

import com.uplink.ulx.model.Message;
import com.uplink.ulx.model.MessageInfo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class Encoder {

    public static byte[] encode(Message message) {

        // Allocate the buffer in BIG ENDIAN
        ByteBuffer byteBuffer = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);

        // Allocate the necessary memory
        byte[] size = byteBuffer.putInt(message.getData().length).array();
        byte[] data = message.getData();
        byte[] buffer = new byte[data.length + size.length];

        // Make the copy
        System.arraycopy(size, 0, buffer, 0, size.length);
        System.arraycopy(data, 0, buffer, size.length, data.length);

        return buffer;
    }

    public static Message decode(byte[] buffer) {

        // Don't proceed if we can't parse the payload size
        if (buffer.length < 4) {
            return null;
        }

        int size;

        // Always use BIG ENDIAN
        ByteBuffer byteBuffer = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);

        // Parse the payload size
        byte[] sizeBuffer = Arrays.copyOfRange(buffer, 0, 4);

        byteBuffer.put(sizeBuffer);
        byteBuffer.rewind();

        size = byteBuffer.getInt();

        // Check if we enough data to read the payload, or whether we should
        // just keep waiting
        if (buffer.length < size + 4) {
            Log.i("Encoder", String.format("ULX buffer skipping parse with expected size %d and actual %d", size, buffer.length));
            return null;
        }

        // Allocate memory for the payload
        byte[] data = new byte[size];

        // Copy the payload to the output buffer
        System.arraycopy(buffer, 4, data, 0, size);

        // Create a message
        return new Message(
                new MessageInfo(0, null, true),
                data
        );
    }
}
