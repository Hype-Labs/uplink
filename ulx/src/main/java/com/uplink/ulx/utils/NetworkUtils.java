package com.uplink.ulx.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import androidx.annotation.NonNull;
import timber.log.Timber;

public class NetworkUtils {
    /**
     * Pings google.com on multiple interfaces in order to check if that server
     * is reachable. This will indicate that the host device is directly
     * connected to the Internet. Future versions should avoid pinging Google,
     * but rather a server within the application's ecosystem or some other
     * method.
     * @param context The Android environment context.
     * @return Whether the device is connected to the Internet.
     */
    public static boolean isInternetReachable(@NonNull Context context) {
        Timber.d("Checking network availability");

        ConnectivityManager cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

        if (activeNetwork != null && activeNetwork.isConnected()) {
            HttpURLConnection connection = null;
            try {
                URL url = new URL("https://www.google.com/");
                connection = (HttpURLConnection)url.openConnection();
                connection.setRequestProperty("User-Agent", "test");
                connection.setRequestProperty("Connection", "close");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.connect();
                connection.getResponseCode();

                // Any status code is OK
                return true;

            } catch (IOException e) {
                Timber.e("ULX Internet not directly available");
                return false;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        return false;
    }
}
