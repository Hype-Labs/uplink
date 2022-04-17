package com.uplink.ulx.bridge.io.model;

import com.uplink.ulx.model.Instance;

import java.util.Locale;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class InternetResponsePacket extends AbstractPacket {
    public static final int CODE_IO_GENERIC_FAILURE = 0;

    private final int code;
    @NonNull
    private final String data;
    private final Instance originator;

    /**
     * Constructor
     *
     * @param sequenceIdentifier packet sequence identifier
     * @param code               HTTP response code values under 100 (invalid HTTP codes) represent
     *                           an IO failure to make a request
     * @param data               If {@code code} is a valid http code - response message. Otherwise
     *                           - error description
     * @param originator         the instance that created the internet request to which this is a
     *                           response to
     */
    public InternetResponsePacket(
            int sequenceIdentifier,
            int code,
            @Nullable String data,
            Instance originator
    ) {
        super(sequenceIdentifier, PacketType.INTERNET_RESPONSE);

        Objects.requireNonNull(originator);

        this.code = code;
        this.data = data != null ? data : "";
        this.originator = originator;
    }

    public final int getCode() {
        return this.code;
    }

    @NonNull
    public final String getData() {
        return this.data;
    }

    /**
     * @return the instance that is originator of the internet request. This is the destination of
     * the response packet
     */
    public final Instance getOriginator() {
        return this.originator;
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "%s(seq: %d, dst: %s)",
                             getClass().getSimpleName(),
                             getSequenceIdentifier(),
                             getOriginator().getStringIdentifier()
        );
    }
}
