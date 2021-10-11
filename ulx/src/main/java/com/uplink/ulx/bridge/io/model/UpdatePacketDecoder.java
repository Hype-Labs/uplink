package com.uplink.ulx.bridge.io.model;

import android.util.Log;

import com.uplink.ulx.UlxError;
import com.uplink.ulx.UlxErrorCode;
import com.uplink.ulx.model.Instance;
import com.uplink.ulx.serialization.Decoder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class UpdatePacketDecoder implements Decoder {

    @Override
    public Decoder.Result decode(byte[] data) throws IOException {
        Log.i(getClass().getCanonicalName(), "ULX attempting to decode UpdatePacket");

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
        if (type != PacketType.UPDATE.getId()) {
            return null;
        }

        // Read the sequence identifier
        byte[] sequenceBuffer = new byte[4];

        if (inputStream.read(sequenceBuffer) != sequenceBuffer.length) {
            return new Result(null, 0, makeGeneralError());
        }

        // The sequence is a 32-bit integer in big-endian
        int sequenceIdentifier = ByteBuffer.wrap(sequenceBuffer).getInt();

        // Read the hop count, event type, and whether it has Internet
        int hopCount = inputStream.read();
        int reachable = inputStream.read();
        int internetReachable = inputStream.read();

        // Read the instance
        byte[] instance = new byte[16];

        if (inputStream.read(instance) != instance.length) {
            return new Result(null, 0, makeGeneralError());
        }

        // Create the object. It's notable that version and type are losing
        // precision. This is an unchecked cast that is probably dangerous,
        // and should be reviewed in the future.
        Object object = makeObject(
                sequenceIdentifier,
                new Instance(instance),
                (byte)hopCount,
                reachable != 0,
                internetReachable != 0
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

    private Object makeObject(
            int sequenceIdentifier,
            Instance instance,
            byte hopCount,
            boolean reachable,
            boolean internetReachable) {

        return new UpdatePacket(
                sequenceIdentifier,
                instance,
                hopCount,
                reachable,
                internetReachable
        );
    }
}
