package com.uplink.ulx.observers;

import com.uplink.ulx.UlxError;
import com.uplink.ulx.model.Instance;

/**
 * Network observers handle network events, such as instances being found and
 * lost on the network. Implementations should keep track of found instances
 * and clean up when lost. Attempting to send messages to lost instances results
 * in a MessageObserver.onUlxMessageFailedSending(MessageInfo, Instance, Error)
 * event with an error.
 */
public interface NetworkObserver {

    /**
     * This notification is issued when an instance is found with a matching app
     * identifier. At this point, communication with the given instance should
     * already be possible.
     * @param instance The found instance.
     */
    void onUlxInstanceFound(Instance instance);

    /**
     * This notification is issued when a previously found instance is lost,
     * such as it going out of range, or the adapter being turned off. The
     * error parameter indicates the cause for the loss. When a cause cannot
     * be properly determined the framework uses a probable one instead, usually
     * indicating that the device appeared to go out of range.
     * @param instance The lost instance.
     * @param error An error describing the cause for the loss.
     */
    void onUlxInstanceLost(Instance instance, UlxError error);
}
