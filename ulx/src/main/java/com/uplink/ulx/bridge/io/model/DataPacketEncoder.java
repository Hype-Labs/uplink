package com.uplink.ulx.bridge.io.model;

import com.uplink.ulx.serialization.Encoder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class DataPacketEncoder implements Encoder {

    @Override
    public Encoder.Result encode(Object object) throws IOException {

        // This shouldn't happen, but it will, if there's an error in the
        // registry, somewhere.
        if (!(object instanceof DataPacket)) {
            throw new RuntimeException("An encoder received the wrong type of " +
                    "packet. This is unexpected, since encoders are supposed to " +
                    "be mapped by type.");
        }

        // The cast should be safe now
        DataPacket packet = (DataPacket)object;

        // The encoder is created as a byte array output stream
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // Encode non-byte stuff
        ByteBuffer sequenceIdentifierBuffer = ByteBuffer.allocate(4).putInt(packet.getSequenceIdentifier());
        ByteBuffer payloadSizeBuffer = ByteBuffer.allocate(4).putInt(packet.getData().length);

        // Encode the data into the stream
        outputStream.write(getVersion());
        outputStream.write(packet.getType().getId());
        outputStream.write(sequenceIdentifierBuffer.array());
        outputStream.write(packet.getOrigin().getIdentifier());
        outputStream.write(packet.getDestination().getIdentifier());
        outputStream.write(payloadSizeBuffer.array());
        outputStream.write(packet.getData());

        return new Result(outputStream.toByteArray(), null);
    }

    @Override
    public int getVersion() {
        return 0;
    }
}
