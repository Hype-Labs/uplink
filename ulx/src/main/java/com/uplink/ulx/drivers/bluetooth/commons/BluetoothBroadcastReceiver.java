package com.uplink.ulx.drivers.bluetooth.commons;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.uplink.ulx.drivers.commons.BroadcastReceiverDelegate;

import java.lang.ref.WeakReference;
import java.util.Objects;

/**
 * This class listen to adapter activity in order to assert the moment when the
 * Bluetooth adapter becomes enabled or disabled. It does so by listening to
 * broadcast intents sent by the Android environment Context. When such adapter
 * state changes are detected, this notifies a BroadcastReceiverDelegate. All
 * events that do not refer to the adapter's state being toggled are ignored.
 */
public class BluetoothBroadcastReceiver extends BroadcastReceiver {

    /**
     * This enumeration is used to track the previously known state of the
     * adapter, so that notifications are not triggered more than once. This is
     * used internally only.
     */
    private enum State {

        /**
         * The adapter's state is not known. The state will be queried on the
         * next status update.
         */
        UNKNOWN,

        /**
         * The adapter is known to be available. Any adapter activity through
         * which this state persists will be ignored.
         */
        ENABLED,

        /**
         * The adapter is known to be disabled. Any adapter activity through
         * which this state persists will be ignored.
         */
        DISABLED
    }

    private State state;
    private BluetoothAdapter bluetoothAdapter;
    private final WeakReference<BroadcastReceiverDelegate> delegate;

    /**
     * Constructors. Initializes with the given delegate. The adapters state
     * will also be initialized to State.UNKNOWN, meaning that the adapter
     * state is not queried at this point.
     * @param delegate The BroadcastReceiverDelegate that will get adapter state
     *                 change notifications from the class.
     * @see BroadcastReceiverDelegate
     */
    public BluetoothBroadcastReceiver(BroadcastReceiverDelegate delegate) {

        Objects.requireNonNull(delegate);

        this.state = State.UNKNOWN;
        this.bluetoothAdapter = null;
        this.delegate = new WeakReference<>(delegate);
    }

    /**
     * Setter for the adapter's known state.
     * @param state The adapter's most recent state update.
     */
    private void setState(State state) {
        this.state = state;
    }

    /**
     * Getter for the adapter's known state.
     * @return The adapter's known state.
     */
    private State getState() {
        return this.state;
    }

    /**
     * Returns the local system framework representation of the Bluetooth
     * adapter.
     * @return The Android Bluetooth adapter representation.
     */
    private BluetoothAdapter getBluetoothAdapter() {
        if (this.bluetoothAdapter == null) {
            this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }
        return this.bluetoothAdapter;
    }

    /**
     * Getter for the BroadcastReceiverDelegate, set at construction time.
     * @return The BroadcastReceiverDelegate.
     * @see BroadcastReceiverDelegate
     */
    private BroadcastReceiverDelegate getDelegate() {
        return this.delegate.get();
    }

    @SuppressWarnings("MissingPermission")
    @Override
    public void onReceive(Context context, Intent intent) {
        if (BluetoothPermissionChecker.isBleGranted(context)) {

            BroadcastReceiverDelegate callback = getDelegate();

            // The adapter is enabled, check if the state is consistent
            if (getBluetoothAdapter().isEnabled()) {
                if (getState() != State.ENABLED) {
                    setState(State.ENABLED);

                    if (callback != null) {
                        callback.onAdapterEnabled(this);
                    }
                }
            }

            // The adapter is OFF
            else if (getState() != State.DISABLED) {
                setState(State.DISABLED);

                if (callback != null) {
                    callback.onAdapterDisabled(this);
                }
            }
        }
    }
}
