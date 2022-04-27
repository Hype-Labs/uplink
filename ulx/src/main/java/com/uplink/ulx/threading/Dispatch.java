package com.uplink.ulx.threading;

import android.os.Handler;
import android.os.Looper;

public class Dispatch {

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Dispatch() {
    }

    public static void post(Runnable runnable) {
        mainHandler.post(runnable);
    }

    public static void postDelayed(Runnable runnable, long delayMillis) {
        mainHandler.postDelayed(runnable, delayMillis);
    }

    public static void cancel(Runnable runnable) {
        mainHandler.removeCallbacks(runnable);
    }
}
