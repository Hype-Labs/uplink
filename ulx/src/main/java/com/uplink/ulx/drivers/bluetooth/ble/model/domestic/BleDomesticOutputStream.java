package com.uplink.ulx.drivers.bluetooth.ble.model.domestic;

import android.util.Log;

import com.uplink.ulx.TransportType;
import com.uplink.ulx.drivers.commons.model.OutputStreamCommons;

public class BleDomesticOutputStream extends OutputStreamCommons {

    /**
     * Constructor. Initializes with given arguments.
     * @param identifier An identifier used for JNI bridging and debugging.
     * @param invalidationDelegate The stream's InvalidationDelegate.
     */
    public BleDomesticOutputStream(String identifier, InvalidationDelegate invalidationDelegate) {
        super(identifier, TransportType.BLUETOOTH_LOW_ENERGY, true, invalidationDelegate);
    }

    @Override
    public void requestAdapterToOpen() {

        // Perhaps it will be enough to wait for the peripheral to connect and
        // manage the streams; we'll see how that plays out.
        Log.i(getClass().getCanonicalName(), "ULX domestic output stream is " +
                "being requested to open, but that is not supported yet");
    }

    @Override
    public void requestAdapterToClose() {
        Log.i(getClass().getCanonicalName(), "ULX domestic output stream is " +
                "being requested to close, but that is not supported yet");
    }

    /**
     * Calling this method gives the stream an indication that its
     * characteristic was subscribed by the remote peer, and thus it's now
     * able of performing I/O. Calling this method is necessary in order to
     * complete the stream's lifecycle events. This method should not be public,
     * but the BleAdvertiser currently lives in a different package; this is
     * expected to change in the future, so this method not be called.
     */
    public void notifyAsOpen() {
        super.onOpen(this);

        // Ready ƒor I/O
        Delegate delegate = getDelegate();
        if (delegate != null) {
            delegate.hasSpaceAvailable(this);
        }
    }
}
