package com.uplink.ulx.serialization;

import com.uplink.ulx.drivers.commons.model.Buffer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link Serializer} is a registry of {@link Encoder} and {@link Decoder}
 * pairs that are capable of processing the same type of objects. When encoding
 * objects, this class will look at the object's type and attempt to encode the
 * object as a byte array, with {@link #encode(Object)}. This can result in one
 * of three scenarios: the operation succeeds and the returned {@link
 * Encoder.Result} contains a byte array, it fails for some encoding reason and
 * the {@link Encoder.Result} contains a {@link com.uplink.ulx.UlxError}, or
 * none of the registered encoders accepts the object type, in which case
 * {@code null} is returned instead. The same logic is also true when decoding
 * objects, even if in that situation it's harder to reject the objects by type.
 */
public class Serializer {

    protected static class Pair {

        private final Encoder encoder;
        private final Decoder decoder;

        Pair(Encoder encoder, Decoder decoder) {
            this.encoder = encoder;
            this.decoder = decoder;
        }

        Encoder getEncoder() {
            return this.encoder;
        }

        Decoder getDecoder() {
            return this.decoder;
        }
    }

    private HashMap<Class<?>, Pair> serializers;

    public Serializer() {
        this.serializers = null;
    }

    protected final HashMap<Class<?>, Pair> getSerializers() {
        if (this.serializers == null) {
            this.serializers = new HashMap<>();
        }
        return this.serializers;
    }

    public void register(Class<?> type, Encoder encoder, Decoder decoder) {
        getSerializers().put(type, new Pair(encoder, decoder));
    }

    public Encoder.Result encode(Object object) throws IOException {
        Pair pair = getSerializers().get(object.getClass());

        if (pair == null) {
            return null;
        }

        return pair.getEncoder().encode(object);
    }

    public Decoder.Result decode(byte[] data) throws IOException {

        // Try decoders one by one until one of them accepts the input
        for (Map.Entry<Class<?>, Pair> entry : getSerializers().entrySet()) {
            Decoder.Result result = entry.getValue().getDecoder().decode(data);

            if (result != null) {
                return result;
            }
        }

        // Couldn't find a suitable decoder
        return null;
    }

    public Decoder.Result decode(Buffer buffer) throws IOException {
        return decode(buffer.getData());
    }
}
