package com.uplink.ulx.bridge.io.model;

import com.uplink.ulx.model.Instance;

import java.util.Locale;
import java.util.Objects;

public class InternetResponsePacket extends AbstractPacket {

    private final int code;
    private final String data;
    private final Instance originator;

    public InternetResponsePacket(int sequenceIdentifier, int code, String data, Instance originator) {
        super(sequenceIdentifier, PacketType.INTERNET_RESPONSE);

        Objects.requireNonNull(data);
        Objects.requireNonNull(originator);

        this.code = code;
        this.data = data;
        this.originator = originator;
    }

    public final int getCode() {
        return this.code;
    }

    public final String getData() {
        return this.data;
    }

    public final Instance getOriginator() {
        return this.originator;
    }

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH,"%s(seq: %d, dst: %s)",
                getClass().getSimpleName(),
                getSequenceIdentifier(),
                getOriginator().getStringIdentifier()
        );
    }
}
