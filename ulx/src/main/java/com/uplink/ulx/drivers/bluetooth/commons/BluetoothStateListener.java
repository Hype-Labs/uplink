package com.uplink.ulx.drivers.bluetooth.commons;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;

import com.uplink.ulx.TransportType;
import com.uplink.ulx.drivers.commons.AbstractStateListener;
import com.uplink.ulx.drivers.commons.BroadcastReceiverDelegate;
import com.uplink.ulx.threading.ExecutorPool;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is shared by all Bluetooth implementations (Bluetooth Classic and
 * Bluetooth Low Energy) in order to assert whether the Bluetooth adapter is on
 * and to track changes to its state. Users must register observers within the
 * singleton instance of this class (addObserver(Observer)) and register the
 * instance to listen to adapter state change events (register(Context)) before
 * getting any notifications. In order to stop such notifications, callers may
 * either remove observers or unregister the adapter state subscription
 * altogether, in which case no more observer notifications will be issued.
 */
public class BluetoothStateListener extends AbstractStateListener implements BroadcastReceiverDelegate {

    /**
     * Observers of this type get notifications of adapter state changes, as to
     * either an adapter has become enabled or disabled. Such notifications are
     * only triggered if the state listener is subscribed to state change events,
     * otherwise the Observers will not get any notifications.
     */
    public interface Observer {

        /**
         * The notification is issued by an adapter observer when the adapter is
         * turned on, but only if the instance is at the moment subscribed to
         * adapter state changes.
         * @param bluetoothStateListener The BluetoothStateListener issuing the
         *                               notification.
         */
        void onAdapterEnabled(BluetoothStateListener bluetoothStateListener);

        /**
         * This notification is issued by an adapter observer when the adapter is
         * turned off, but only if the instance is at the moment subscribed to
         * adapter state changes.
         * @param bluetoothStateListener The BluetoothStateListener issuing the
         *                               notification.
         */
        void onAdapterDisabled(BluetoothStateListener bluetoothStateListener);
    }

    private List<Observer> observers;
    private static BluetoothStateListener instance;
    private BluetoothBroadcastReceiver bluetoothBroadcastReceiver;

    private static final String BLUETOOTH_ADAPTER_STATUS_ACTION_STATE_CHANGED = "android.bluetooth.adapter.action.STATE_CHANGED";

    /**
     * Private accessor prevents direct instantiation. This class is to be
     * consumed by means of its facade API.
     * @return The singleton instance.
     */
    private static BluetoothStateListener getInstance() {
        if (instance == null) {
            instance = new BluetoothStateListener();
        }
        return instance;
    }

    /**
     * Returns the list of Observers that are currently getting notifications
     * for adapter state change events from this class.
     * @return A list of Observers.
     */
    private List<Observer> getObservers() {
        if (this.observers == null) {
            this.observers = new ArrayList<>();
        }
        return this.observers;
    }

    /**
     * Register the BroadcastReceiver within the Context of the given Android
     * environment. By calling this method, the state listener will subscribe
     * to adapter state change events, and the observers will start getting
     * notifications.
     * @param context The Android environment Context.
     */
    public static void register(Context context) {
        getInstance().registerBroadcastReceiver(context);
    }

    /**
     * Undoes a previous registration of the BroadcastReceiver within the
     * Android environment Context used at the time of registration. After this
     * method is called, the observers will no longer get notifications from the
     * class, and adapter state changes will not be tracked.
     */
    public static void unregister() {
        getInstance().unregisterBroadcastReceiver();
    }

    /**
     * Adds an instance to the list of Observers to get notifications when
     * adapter state changes occur.
     * @param observer The observer to add.
     * @see Observer
     */
    public static void addObserver(Observer observer) {
        if (getInstance().getObservers().contains(observer)) {
            return;
        }
        getInstance().getObservers().add(observer);
    }

    /**
     * Removes a previously registered Observer from the list of instances to
     * notify when the adapter state changes occur. If the observer is not
     * registered, this method is a no-op.
     * @param observer The observer to remove.
     * @see Observer
     */
    public static void removeObserver(Observer observer) {
        getInstance().getObservers().remove(observer);
    }

    @Override
    protected BroadcastReceiver getBroadcastReceiver() {
        if (this.bluetoothBroadcastReceiver == null) {
            this.bluetoothBroadcastReceiver = new BluetoothBroadcastReceiver(this);
        }
        return this.bluetoothBroadcastReceiver;
    }

    @Override
    protected IntentFilter getIntentFilter() {
        return new IntentFilter(BLUETOOTH_ADAPTER_STATUS_ACTION_STATE_CHANGED);
    }

    @Override
    public void onAdapterEnabled(BroadcastReceiver broadcastReceiver) {
        for (final Observer observer : getObservers()) {
            observer.onAdapterEnabled(BluetoothStateListener.this);
        }
    }

    @Override
    public void onAdapterDisabled(BroadcastReceiver broadcastReceiver) {
        for (final Observer observer : getObservers()) {
            observer.onAdapterDisabled(BluetoothStateListener.this);
        }
    }
}
