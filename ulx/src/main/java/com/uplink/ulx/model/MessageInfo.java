package com.uplink.ulx.model;

/**
 * This class is used to hold message metadata. It's mostly useful to associate
 * messages with the metadata, as the SDK notifications usually use instances of
 * this class and not Message. This is motivated by the fact that Message holds
 * the message's payload, which would consume more memory.
 */
public class MessageInfo {

    private final int identifier;
    private final boolean acknowledgeRequested;
    private final Instance destination;

    /**
     * Constructor.
     * @param identifier The message's identifier.
     * @param destination The destination for the message.
     */
    public MessageInfo(int identifier, Instance destination) {
        this.identifier = identifier;
        this.acknowledgeRequested = true;
        this.destination = destination;
    }

    /**
     * Getter for the message's identifier. It returns an identifier that
     * has been uniquely assigned to the corresponding message.
     * @return The message's identifier.
     */
    public final int getIdentifier() {
        return this.identifier;
    }

    /**
     * Whether the message asked for an reception acknowledgement when it was
     * first sent.
     * @return A flag indicating whether an acknowledgement was requested.
     */
    public final boolean isAcknowledgementRequested() {
        return this.acknowledgeRequested;
    }

    /**
     * The message's destination.
     * @return The {@link Instance} destination.
     */
    public final Instance getDestination() {
        return this.destination;
    }

    /**
     * Compares with another MessageInfo instance. This method returns true if
     * the two instances (this and messageInfo) have the same identifier. It
     * returns false otherwise.
     * @param messageInfo The MessageInfo instance to compare.
     * @return Whether the two MessageInfo instances are equal.
     */
    boolean equals(MessageInfo messageInfo) {
        return messageInfo != null && this.getIdentifier() == messageInfo.getIdentifier();
    }

    /**
     * Compares with a Message instance. The method returns true if the
     * MessageInfo identifier and the Message's identifier correspond.
     * @param message The Message instance to compare.
     * @return Whether the two Message instances are equal.
     */
    boolean equals(Message message) {
        return message != null && this.getIdentifier() == message.getIdentifier();
    }

    @Override
    public boolean equals(Object obj) {

        if (this == obj) {
            return true;
        }

        if (obj == null) {
            return false;
        }

        if (!obj.getClass().equals(MessageInfo.class)) {
            return false;
        }

        return equals((MessageInfo)obj);
    }
}
