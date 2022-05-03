package com.uplink.ulx.utils;

public interface Completable {
    default void markAsComplete() {
        markAsComplete(true);
    }

    void markAsComplete(boolean isSuccessful);
}
