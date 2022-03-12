package com.uplink.ulx.bridge.io.model;

import com.uplink.ulx.UlxError;
import com.uplink.ulx.UlxErrorCode;
import com.uplink.ulx.model.Instance;
import com.uplink.ulx.serialization.Decoder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import timber.log.Timber;

public class InternetPacketDecoder implements Decoder {

    @Override
    public Result decode(byte[] data) throws IOException {
        Timber.i("ULX attempting to decode InternetPacket");

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
        if (type != PacketType.INTERNET.getId()) {
            return null;
        }

        // Read the sequence identifier
        byte[] sequenceBuffer = new byte[4];

        if (inputStream.read(sequenceBuffer) != sequenceBuffer.length) {
            return new Result(null, 0, makeGeneralError());
        }

        // The sequence is a 32-bit integer in big-endian
        int sequenceIdentifier = ByteBuffer.wrap(sequenceBuffer).getInt();

        // Read the origin
        byte[] origin = new byte[16];

        if (inputStream.read(origin) != origin.length) {
            return new Result(null, 0, makeGeneralError());
        }

        // Read the hop count
        int hopCount = inputStream.read();

        if (hopCount == -1) {
            return new Result(null, 0, makeGeneralError());
        }

        // Read the test
        int test = inputStream.read();

        if (test == -1) {
            return new Result(null, 0, makeGeneralError());
        }

        // Read the URI
        int uriSize = inputStream.read();

        if (uriSize == -1) {
            return new Result(null, 0, makeGeneralError());
        }

        byte[] uriData = new byte[uriSize];

        if (inputStream.read(uriData) != uriSize) {
            return new Result(null, 0, makeGeneralError());
        }

        String uri = new String(uriData, StandardCharsets.UTF_8);

        // Read the message size
        byte[] messageSizeBuffer = new byte[4];

        if (inputStream.read(messageSizeBuffer) != messageSizeBuffer.length) {
            return new Result(null, 0, makeGeneralError());
        }

        int messageSize = ByteBuffer.wrap(messageSizeBuffer).getInt();

        // Read the message
        byte[] messageData = new byte[messageSize];

        if (inputStream.read(messageData) != messageData.length) {
            return new Result(null, 0, makeGeneralError());
        }

        String message = new String(messageData, StandardCharsets.UTF_8);

        // Create the object. It's notable that version and type are losing
        // precision. This is an unchecked cast that is probably dangerous,
        // and should be reviewed in the future.
        Object object = makeObject(
                sequenceIdentifier,
                origin,
                uri,
                message,
                test,
                hopCount
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

    /**
     * Creates an {@link InternetPacket} from the given arguments. The {@code
     * uri} and {@code origin} are instantiated as {@link java.net.URI} and
     * {@link Instance}, respectively.
     * @param sequenceIdentifier The packet's sequence identifier.
     * @param origin The origin identifier.
     * @param uri The server request URI.
     * @param message The server request content.
     * @param hopCount The number of hops that the packet has travelled.
     * @return An instantiated {@link InternetPacket}. This method may also
     * return {@code null} if the given {@code uri} is malformed.
     */
    private Object makeObject(int sequenceIdentifier, byte[] origin, String uri, String message, int test, int hopCount) {

        try {
            return new InternetPacket(
                    sequenceIdentifier,
                    new URL(uri),
                    message,
                    test,
                    new Instance(origin),
                    hopCount
            );
        } catch (MalformedURLException e) {
            Timber.e("ULX got a malformed URL");

            // This will result in the data being consumed, but the result
            // object being null
            return null;
        }
    }
}
