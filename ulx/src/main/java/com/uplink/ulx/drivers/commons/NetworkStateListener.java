package com.uplink.ulx.drivers.commons;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;

import com.uplink.ulx.utils.NetworkUtils;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;
import timber.log.Timber;

/**
 * Used by {@link com.uplink.ulx.bridge.network.controller.NetworkController} to get notifications
 * about internet connectivity state changes
 */
public class NetworkStateListener {

    private volatile ConnectivityManager connectivityManager;

    /**
     * Interface for sending connectivity change updates to the client classes
     */
    public interface Observer {
        /**
         * Called when internet availability has changed.
         * <p>
         * WARNING: the method is called from a dedicated background thread
         *
         * @param isInternetAvailable whether internet is available
         */
        @WorkerThread
        void onConnectivityChanged(boolean isInternetAvailable);
    }

    /**
     * A background thread for handling network state updates
     */
    private final HandlerThread handlerThread = startNewHandlerThread(
            NetworkStateListener.class.getSimpleName()
    );

    private final Handler handler = new Handler(handlerThread.getLooper());

    private final Context context;
    private final Observer observer;

    /**
     * Network availability flag. {@code null} means "unknown"
     */
    private Boolean isNetworkAvailable;

    /**
     * Maps {@link Network}s to their reachability states. All networks present as keys are
     * available as per {@link ConnectivityManager}. The values indicate reachable (non-blocked)
     * state
     * <p>
     * Its future modifications and reads are confined to a single thread, so access to it doesn't
     * need to be synchronized
     * <p>
     * {@link Network} overrides equals() and hashCode() so it's safe to use this class in a {@link
     * HashMap}
     */
    private final Map<Network, Boolean> networksState = new HashMap<>();

    /**
     * Constructor. The callbacks will not be invoked until {@link #register()} is called
     *
     * @param context  Android environment's context
     * @param observer an observer to receive callbacks for
     */
    public NetworkStateListener(Context context, Observer observer) {
        this.context = context;
        this.observer = observer;
    }

    /**
     * Registers this listener for network state changes
     */
    public void register() {
        connectivityManager = ContextCompat.getSystemService(
                context,
                ConnectivityManager.class
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Registering for default network only should reduce the number of callbacks received
            connectivityManager.registerDefaultNetworkCallback(callback);
        } else {
            final NetworkRequest networkRequest = new NetworkRequest
                    .Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            connectivityManager.registerNetworkCallback(networkRequest, callback);
        }
    }

    private final ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            handler.post(() -> {
                if (!networksState.containsKey(network)) {
                    networksState.put(network, false);
                    updateState();
                }
            });
        }

        @Override
        public void onBlockedStatusChanged(@NonNull Network network, boolean blocked) {
            handler.post(() -> {
                // Network's reachable state is opposite to blocked
                networksState.put(network, !blocked);
                updateState();
            });
        }

        @Override
        public void onLost(@NonNull Network network) {
            handler.post(() -> {
                if (networksState.remove(network) != null) {
                    updateState();
                }
            });
        }
    };

    /**
     * Updates {@link #isNetworkAvailable} flag and notifies {@link #observer} if network's state
     * has changed
     */
    @WorkerThread
    private void updateState() {
        @Nullable final Boolean oldState = isNetworkAvailable;

        @NonNull
        Boolean newState = Boolean.FALSE;
        for (boolean isAvailable : networksState.values()) {
            if (isAvailable) {
                newState = Boolean.TRUE;
                break;
            }
        }

        // Let's verify if we can use the network
        newState &= NetworkUtils.isNetworkAvailable(context);

        isNetworkAvailable = newState;

        if (oldState != newState) {
            Timber.i("Network availability has changed to [%s]. Notifying the observer");
            observer.onConnectivityChanged(newState);
        } else {
            Timber.i("Network availability has not changed");
        }
    }

    /**
     * Unregisters the callback and cleans up used resources. You cannot call {@link #register()}
     * after that
     */
    public void destroy() {
        if (connectivityManager == null) {
            throw new IllegalStateException("Cannot destroy listener that was not registered");
        }
        connectivityManager.unregisterNetworkCallback(callback);
        // No need to quit safely - we are not interested in callbacks at this point
        handlerThread.quit();
    }

    /**
     * Creates and starts new {@link HandlerThread}
     *
     * @param name new thread's name
     * @return the newly-started thread
     */
    private static HandlerThread startNewHandlerThread(String name) {
        final HandlerThread result = new HandlerThread(name);
        result.start();
        return result;
    }
}
