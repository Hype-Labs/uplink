package com.uplink.ulx.threading;

import android.os.Handler;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import androidx.annotation.NonNull;

/**
 * A serial {@link java.util.concurrent.Executor} that posts its tasks to a {@link Handler}
 */
public class HandlerExecutor implements Executor {
    private final Handler handler;

    public HandlerExecutor(@NonNull Handler handler) {
        this.handler = handler;
    }

    @Override
    public void execute(@NonNull Runnable command) {
        if (!handler.post(command)) {
            throw new RejectedExecutionException(handler + " is shutting down");
        }
    }
}
