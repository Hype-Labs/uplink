package com.uplink.ulx.bridge.io.model;

import com.uplink.ulx.serialization.Encoder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class UpdatePacketEncoder implements Encoder {

    @Override
    public Encoder.Result encode(Object object) throws IOException {

        // This shouldn't happen, since the registry maps encoders by type.
        // However, if it does, it will indicate an error in the registry or
        // the packet's type.
        if (!(object instanceof UpdatePacket)) {
            throw new RuntimeException("An encoder received the wrong type of " +
                    "packet. This is unexpected, since encoders are supposed to " +
                    "be mapped by type.");
        }

        // The cast should be safe now
        UpdatePacket packet = (UpdatePacket)object;

        // Encode the sequence identifier
        ByteBuffer sequenceIdentifier = ByteBuffer.allocate(4).putInt(
                packet.getSequenceIdentifier()
        );

        // Encode
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        outputStream.write(getVersion());
        outputStream.write(packet.getType().getId());
        outputStream.write(sequenceIdentifier.array());
        outputStream.write(packet.getHopCount());   // 1 byte only
        outputStream.write(packet.isReachable() ? 1 : 0);
        outputStream.write(packet.isInternetReachable() ? 1 : 0);
        outputStream.write(packet.getInstance().getIdentifier());

        return new Result(outputStream.toByteArray(), null);
    }

    @Override
    public int getVersion() {
        return 0;
    }
}
