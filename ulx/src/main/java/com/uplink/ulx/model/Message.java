package com.uplink.ulx.model;

/**
 * Messages are abstract entities that help keeping track of data as it is sent
 * over the network, by holding a unique identifier. The identifier is actually
 * held by the underlying MessageInfo instance (getMessageInfo()), but the
 * Message class provides dynamic access to that attribute. This is mostly
 * motivated by the fact that the SDK does not keep messages, mostly for memory
 * saving purposes. As such, it's up to the developer to hold or discard the
 * data as needed. Instead, MessageInfo instances are used to keep track of the
 * state of any given Message.
 * @see MessageInfo
 */
public class Message {

    private final byte [] data;
    private final MessageInfo msgInfo;

    /**
     * Constructor.
     * @param msgInfo Message metadata.
     * @param data Message data.
     */
    public Message(MessageInfo msgInfo, byte [] data) {
        this.msgInfo = msgInfo;
        this.data = data;
    }

    /**
     * Getter for the message data. The data holds the content that was sent
     * or received with a given message.
     * @return The message's data.
     */
    public final byte[] getData() {
        return this.data;
    }

    /**
     * Getter for the messages metadata. Instances of MessageInfo hold message
     * metadata. Currently, this only includes the message's identifier, but
     * future releases will use this for other purposes.
     * @return The message's metadata.
     */
    public final MessageInfo getMessageInfo() {
        return this.msgInfo;
    }

    /**
     * Getter for the message's identifier. This method accesses the identifier
     * from the underlying {@link MessageInfo}. It returns an identifier that
     * has been uniquely assigned to this message.
     * @return The message's identifier.
     */
    public final int getIdentifier() {
        return getMessageInfo().getIdentifier();
    }

    /**
     * Getter for the message's destination. This method accesses the
     * destination from the underlying {@link MessageInfo}.
     * @return The message's destination.
     */
    public Instance getDestination() {
        return getMessageInfo().getDestination();
    }

    /**
     * Getter for the boolean flag that indicates whether an acknowledgement
     * request was asked from the destination.
     * @return Whether an acknowledgement was requested.
     */
    public boolean isAcknowledgementRequested() {
        return getMessageInfo().isAcknowledgementRequested();
    }

    @Override
    public boolean equals(Object obj) {

        if (this == obj) {
            return true;
        }

        if (obj == null) {
            return false;
        }

        if (!obj.getClass().equals(Message.class)) {
            return false;
        }

        return equals((Message)obj);
    }

    /**
     * Compares two Message instances. This method returns true if the two
     * instances (this and message) have the same message identifier. It
     * returns false otherwise.
     * @param message The Message instance to compare.
     * @return Whether the two messages have equal identifier.
     */
    boolean equals(Message message) {
        return message != null && this.getIdentifier() == message.getIdentifier();
    }

    @Override
    public int hashCode() {
        return getMessageInfo().hashCode();
    }
}
