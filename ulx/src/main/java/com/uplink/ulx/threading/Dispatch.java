package com.uplink.ulx.threading;

import android.os.Handler;
import android.os.Looper;

public class Dispatch {

    private static Handler mainHandler;

    private Dispatch() {
    }

    private static Handler getMainHandler() {
        if (Dispatch.mainHandler == null) {
            Dispatch.mainHandler = new Handler(Looper.getMainLooper());
        }
        return Dispatch.mainHandler;
    }

    public static void post(Runnable runnable) {
        getMainHandler().post(runnable);
    }
}
