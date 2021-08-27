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
}
