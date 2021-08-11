package com.uplink.ulx.drivers.commons;

import android.content.BroadcastReceiver;

/**
 * A BroadcastReceiverDelegate receives adapter state changes from broadcast
 * receivers, which in turn subscribe to adapter activity and query the adapter
 * for activity.
 */
public interface BroadcastReceiverDelegate {

    /**
     * This notification is triggered when the adapter is known to have just
     * become enabled.
     * @param broadcastReceiver The BroadcastReceiver issuing the notification.
     */
    void onAdapterEnabled(BroadcastReceiver broadcastReceiver);

    /**
     * This notification is triggered when the adapter is known to have just
     * become disabled.
     * @param broadcastReceiver The BroadcastReceiver issuing the notification.
     */
    void onAdapterDisabled(BroadcastReceiver broadcastReceiver);
}
