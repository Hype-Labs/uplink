package com.uplink.ulx.bridge.io.model;

import com.uplink.ulx.serialization.Encoder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class InternetResponsePacketEncoder implements Encoder {

    @Override
    public Encoder.Result encode(Object object) throws IOException {

        // This shouldn't happen, since the encoders are mapped by packet type.
        // If it does, then it means that the registry has some sort of error,
        // or that the wrong packet type was received. Either way, it's a
        // programming error.
        if (!(object instanceof InternetResponsePacket)) {
            throw new RuntimeException("An encoder received the wrong type of " +
                    "packet. This is unexpected, since encoders are supposed to " +
                    "be mapped by type.");
        }

        // The cast should be safe now
        InternetResponsePacket packet = (InternetResponsePacket)object;

        // Encode the sequence identifier
        ByteBuffer sequenceIdentifier = ByteBuffer.allocate(4).putInt(
                packet.getSequenceIdentifier()
        );

        // Encode the data
        byte[] data = packet.getData().getBytes(StandardCharsets.UTF_8);

        // Encode the data size
        ByteBuffer messageSizeData = ByteBuffer.allocate(4).putInt(data.length);

        // Encode
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        outputStream.write(getVersion());
        outputStream.write(packet.getType().getId());
        outputStream.write(sequenceIdentifier.array());
        outputStream.write(packet.getOriginator().getIdentifier());
        outputStream.write(packet.getCode());
        outputStream.write(messageSizeData.array());    // 4 bytes
        outputStream.write(data);

        return new Result(outputStream.toByteArray(), null);
    }

    @Override
    public int getVersion() {
        return 0;
    }
}
