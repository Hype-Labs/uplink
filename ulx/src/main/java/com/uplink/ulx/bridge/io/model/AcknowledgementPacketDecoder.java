package com.uplink.ulx.bridge.io.model;

import android.util.Log;

import com.uplink.ulx.UlxError;
import com.uplink.ulx.UlxErrorCode;
import com.uplink.ulx.model.Instance;
import com.uplink.ulx.serialization.Decoder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class AcknowledgementPacketDecoder implements Decoder {

    @Override
    public Result decode(byte[] data) throws IOException {
        Log.i(getClass().getCanonicalName(), "ULX attempting to decode AcknowledgementPacket");

        ByteArrayInputStream inputStream = new ByteArrayInputStream(data);

        // Read the version and packet type, since the packet type will be
        // used to determine whether the packet is accepted
        int version = inputStream.read();
        int type = inputStream.read();

        // EOF at this point means that we reject the packet as if we're
        // rejecting it because of its type. This is because we don't know
        // the packet type yet.
        if (version == -1 || type == -1) {
            return null;
        }

        // Reject the packet based on the wrong version.
        if (version != getVersion()) {
            return null;
        }

        // Reject the packet based on the wrong packet type.
        if (type != PacketType.ACKNOWLEDGEMENT.getId()) {
            return null;
        }

        // Read the sequence identifier
        byte[] sequenceBuffer = new byte[4];

        if (inputStream.read(sequenceBuffer) != sequenceBuffer.length) {
            return new Result(null, 0, makeGeneralError());
        }

        // The sequence is a 32-bit integer in big-endian
        int sequenceIdentifier = ByteBuffer.wrap(sequenceBuffer).getInt();

        // Read the destination, which is the origin for the packet we're
        // acknowledging
        byte[] destination = new byte[16];

        if (inputStream.read(destination) != destination.length) {
            return new Result(null, 0, makeGeneralError());
        }

        // Read the origin, which should be the host device if it's the one
        // who it's meant for
        byte[] origin = new byte[16];

        if (inputStream.read(origin) != origin.length) {
            return new Result(null, 0, makeGeneralError());
        }

        // Create the object
        Object object = makeObject(
                sequenceIdentifier,
                destination,
                origin
        );

        // Return the result with the decoded object
        return new Result(object, data.length - inputStream.available(), null);
    }

    @Override
    public int getVersion() {
        return 0;
    }

    private UlxError makeGeneralError() {
        return new UlxError(
                UlxErrorCode.UNKNOWN,
                "Could not receive a message from the stream.",
                "There appears to not be enough data.",
                "Wait for more data to arrive."
        );
    }

    private Object makeObject(int sequenceIdentifier, byte[] destination, byte[] origin) {
        return new AcknowledgementPacket(
                sequenceIdentifier,
                new Instance(origin),
                new Instance(destination)
        );
    }
}
