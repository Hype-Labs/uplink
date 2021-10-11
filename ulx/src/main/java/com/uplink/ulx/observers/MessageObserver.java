package com.uplink.ulx.observers;

import com.uplink.ulx.UlxError;
import com.uplink.ulx.model.Instance;
import com.uplink.ulx.model.Message;
import com.uplink.ulx.model.MessageInfo;

/**
 * Message observers listen to message events, such as messages being received,
 * sent, delivered, or failing. The concepts of "sending" and "delivering" are
 * important to distinguish. A message being "sent" indicates that it was
 * written to the output streams, but not necessarily delivered to its
 * destination. This only means that the content is circulating on the network,
 * and as such might not have left the device yet. A message being "delivered",
 * on the other hand, indicates that its destination has acknowledge reception
 * and the content is already available on the end device.
 */
public interface MessageObserver {

    /**
     * This notification is issued when a message arrives from a foreign source.
     * The framework passes the data as it is received and makes no attempt of
     * processing it (other than encrypting and decrypting it, when applicable).
     * The instance parameter indicates the originating instance.
     * @param data The data that was received.
     * @param origin The {@link Instance} that originated the message.
     */
    void onUlxMessageReceived(byte[] data, Instance origin);

    /**
     * This notification is issued when a message is known to have failed being
     * sent to the network. This means that the message never entirely left the
     * device, and as such it will not be received by the destination. Common
     * causes for this include the destination instance being lost while the
     * content is being sent, causing the output streams to close. The SDK does
     * not implement failed delivery notifications yet, meaning that even if
     * this notification is not issued the message may still not reach its
     * destination. The messageInfo (MessageInfo) parameter holds some metadata
     * about the original message. Currently, it only holds the message's
     * identifier, but more data can be used in the future. If the original
     * message was kept, the identifiers can be compared in order to map the
     * event with the message's content. This is motivated by the fact that the
     * SDK does not keep the message's data in order to save memory.
     * @param messageInfo A container for the data and metadata for the message.
     * @param error An error indicating the cause of failure.
     */
    void onUlxMessageFailedSending(MessageInfo messageInfo, UlxError error);

    /**
     * This notification indicates that the message with the identifier given by
     * the messageInfo parameter has been sent to the network. This does not
     * mean that it has been delivered, but rather that it was written to the
     * streams and acknowledged by the next hop device. The delegate method
     * {@link #onUlxMessageDelivered(MessageInfo, Instance, float, boolean)},
     * on the other hand, indicates delivery to the receiving end. That method
     * is preferred if the intent is to track delivery, especially when messages
     * are being sent over a mesh network and not direct link. At this point,
     * it's not known whether the content has been or will be delivered. The
     * messageInfo instance maps to a message identifier of a {@link Message}
     * instance that was returned by the send method. In order to keep track of
     * which messages are sent, store this identifier in a data structure and
     * wait for notifications with the same identifier. This step is, however,
     * optional.
     * @param messageInfo A container for the message being sent.
     */
    void onUlxMessageSent(MessageInfo messageInfo);

    /**
     * This notification indicates that the message with the identifier given by
     * the messageInfo parameter has reached its destination and that . The
     * amount of data that has been delivered is indicated by the "progress"
     * argument. This argument holds a value between 0 and 1, indicating the
     * percentage of the data that the destination has acknowledge back to the
     * origin. This notification is only triggered if the trackProgress of the
     * send(byte [], Instance, boolean) method is set to true. As
     * acknowledgements incur extra overhead on the network, this option must
     * be explicitly set. A value of 1 could indicate completion, but the
     * preferred method is to check the "complete" flag, in order to avoid
     * floating-point arithmetic. Notice that the destination only gets a
     * notification when the message is fully received, not before, since
     * progress tracking at the destination is not implemented yet. This will
     * change in future release, and progress bars will be possible on both the
     * originating and receiving devices.
     * @param messageInfo Metadata about the message being delivered.
     */
    void onUlxMessageDelivered(MessageInfo messageInfo);
}
