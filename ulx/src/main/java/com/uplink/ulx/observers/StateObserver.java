package com.uplink.ulx.observers;

import com.uplink.ulx.UlxError;

/**
 * State observers handles state change events, such as the framework starting
 * and stopping, among others. This is helpful for tracking the framework's
 * lifecycle.
 */
public interface StateObserver {

    /**
     * This notification is issued upon a successful call to ULX.start() on the
     * singleton instance. When this notification is triggered, the services are
     * actively being advertised on the network and device matches can occur at
     * any time.
     */
    void onUlxStart();

    /**
     * This notification is issued when the services are requested to stop, or
     * otherwise forced to do so. If the services were forced to stop (such as
     * the adapter being turned of) the error instance will indicate the cause
     * of failure. If this is being triggered due to a successful call to stop(),
     * then the error parameter will be set to null.
     * @param error An error indicating the cause for the stoppage, if any.
     */
    void onUlxStop(UlxError error);

    /**
     * This notification is issued in response to a failed start request. This
     * means that the device is not actively participating on the network with
     * any transport nor trying to recover from the failure. If, at some point,
     * the framework finds indications that recovery is possible, an onReady()
     * notification is issued. The services will not start unless explicitly
     * told to.
     * @param error An error indicating the cause of failure.
     */
    void onUlxFailedStarting(UlxError error);

    /**
     * This notification is issued after a failed start request resulting in
     * failure (onUlxFailedStarting(UlxError)) and the framework identifying
     * that the cause of failure might not apply anymore. Attempting to start
     * the framework's services is not guaranteed to succeed as other causes
     * for failure might still exist, but they are likely to do so. It's up to
     * the receiver to decide whether the services should be started. This
     * event is only triggered once, upon which the SDK stops listening to
     * adapter state change events.
     */
    void onUlxReady();

    /**
     * This notification is issued whenever the framework changes state. This
     * method could be used as an alternative to onUlxStart() and onUlxStop(Error),
     * as it indicates when the framework enters into State.Running and
     * State.Idle states, but also State.Starting and State.Stopping. Whether
     * to use this method or the other specific notifications is a design call,
     * as both types of notification are guaranteed to always be triggered when
     * state changes occur. However, the more specific onUlxStart() and
     * onUlxStop(UlxError) are preferable. Notice, for instance, that this
     * method does not provide error information in case of stoppage.
     */
    void onUlxStateChange();
}
