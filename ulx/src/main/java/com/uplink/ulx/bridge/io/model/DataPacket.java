package com.uplink.ulx.bridge.io.model;

import com.uplink.ulx.model.Instance;

import java.util.Locale;
import java.util.Objects;

public class DataPacket extends AbstractPacket {

    private final Instance origin;
    private final Instance destination;
    private final byte[] data;

    public DataPacket(int sequenceIdentifier, Instance origin, Instance destination, byte[] data) {
        super(sequenceIdentifier, PacketType.DATA);

        Objects.requireNonNull(origin);
        Objects.requireNonNull(destination);
        Objects.requireNonNull(data);

        this.origin = origin;
        this.destination = destination;
        this.data = data;
    }

    public final Instance getOrigin() {
        return this.origin;
    }

    public final Instance getDestination() {
        return this.destination;
    }

    public final byte[] getData() {
        return this.data;
    }

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH,"%s(seq: %d, src: %s, dst: %s, payload: %d)",
                getClass().getSimpleName(),
                getSequenceIdentifier(),
                getOrigin().getStringIdentifier(),
                getDestination().getStringIdentifier(),
                getData().length
        );
    }
}
