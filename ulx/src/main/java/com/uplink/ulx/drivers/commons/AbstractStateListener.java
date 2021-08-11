package com.uplink.ulx.drivers.commons;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;

import java.lang.ref.WeakReference;
import java.util.Objects;

/**
 * An AbstractStateListener is a class that manages the subscription of the
 * BroadcastReceiver implemented by child classes. Those need to implement the
 * getBroadcastReceiver() and getIntentFilter() methods in order to subscribe
 * to adapter state change events, but the subscription of the BroadcastReceiver
 * within the Android environment Context is handled by this abstract class.
 */
public abstract class AbstractStateListener {

    private WeakReference<Context> context;
    private boolean registered;

    /**
     * Constructor. Initializes the AbstractStateListener as not having
     * registered the BroadcastReceiver.
     */
    public AbstractStateListener() {
        this.context = null;
        this.registered = false;
    }

    /**
     * Returns the Android environment Context that was set with the last call
     * to registerBroadcastReceiver.
     * @return The Android environment Context.
     */
    private Context getContext() {
        return this.context.get();
    }

    /**
     * Returns the boolean flag that gives indication as to whether the state
     * listener is currently registered within the Android environment and thus
     * actively receiving notifications.
     * @return Whether the BroadcastReceiver is registered.
     */
    private boolean isRegistered() {
        return this.registered;
    }

    /**
     * This method register the BroadcastReceiver returned by the
     * getBroadcastReceiver() abstract method within the context of the Android
     * environment, which, as given, will be kept as a weak reference. This will
     * enable the BroadcastReceiver to start getting notifications of interest,
     * as specified by the getIntentFilter() abstract method.
     * @param context The Android environment Context.
     */
    public synchronized void registerBroadcastReceiver(Context context) {

        Objects.requireNonNull(context);

        if (this.registered) {
            return;
        }

        this.registered = true;
        this.context = new WeakReference<>(context);

        getContext().registerReceiver(getBroadcastReceiver(), getIntentFilter());
    }

    /**
     * This method should be called by the concrete implementations when there
     * is no more intent of listening to adapter state changes, in which case
     * the BroadcastReceiver will be unregistered from the Android environment
     * Context and not receive any more notifications.
     */
    public synchronized void unregisterBroadcastReceiver() {

        if (!isRegistered()) {
            return;
        }

        getContext().unregisterReceiver(getBroadcastReceiver());

        this.registered = false;
        this.context = null;            // No longer needed
    }

    /**
     * Child classes should implement this method and return the
     * BroadcastReceiver that will be used to listen do adapter state change
     * events.
     * @return The BroadcastReceiver to subscribe.
     */
    protected abstract BroadcastReceiver getBroadcastReceiver();

    /**
     * Child classes may use this IntentFilter in order to match the type of
     * activity that they are expecting to listen from the adapter.
     * @return An IntentFilter configured for the purposes of the base class.
     */
    protected abstract IntentFilter getIntentFilter();
}
