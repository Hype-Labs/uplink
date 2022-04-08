package com.uplink.ulx.bridge.io.model;

import com.uplink.ulx.serialization.Encoder;
import com.uplink.ulx.utils.ByteUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class InternetPacketEncoder implements Encoder {

    @Override
    public Result encode(Object object) throws IOException {

        // This shouldn't happen, since the encoders are mapped by packet type.
        // If it does, then it means that the registry has some sort of error,
        // or that the wrong packet type was received. Either way, it's a
        // programming error.
        if (!(object instanceof InternetPacket)) {
            throw new RuntimeException("An encoder received the wrong type of " +
                    "packet. This is unexpected, since encoders are supposed to " +
                    "be mapped by type.");
        }

        // The cast should be safe now
        InternetPacket packet = (InternetPacket)object;

        // Encode the sequence identifier
        ByteBuffer sequenceIdentifier = ByteBuffer.allocate(4).putInt(
                packet.getSequenceIdentifier()
        );

        // Encode the URI
        String uriString = packet.getUrl().toString();
        byte[] uriData = uriString.getBytes(StandardCharsets.UTF_8);

        // Encode the content
        String messageString = packet.getData();
        byte[] messageData = ByteUtils.compress(messageString.getBytes(StandardCharsets.UTF_8));

        // Encode the content length
        ByteBuffer messageSizeData = ByteBuffer.allocate(4).putInt(messageData.length);

        // Encode
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        outputStream.write(getVersion());                           // 1 byte
        outputStream.write(packet.getType().getId());               // 1 byte
        outputStream.write(sequenceIdentifier.array());             // 4 bytes
        outputStream.write(packet.getOriginator().getIdentifier()); // 16 bytes
        outputStream.write(packet.getHopCount());                   // 1 byte
        outputStream.write(packet.getTest());                       // 1 byte
        outputStream.write(uriData.length); // Max 255?             // 1 byte
        outputStream.write(uriData);                                // uriData.length
        outputStream.write(messageSizeData.array());                // 4 bytes
        outputStream.write(messageData);                            // Payload

        return new Result(outputStream.toByteArray(), null);
    }

    @Override
    public int getVersion() {
        return 0;
    }
}
