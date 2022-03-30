package com.uplink.ulx.drivers.bluetooth.ble.model.domestic;

import com.uplink.ulx.TransportType;
import com.uplink.ulx.drivers.commons.model.InputStreamCommons;
import com.uplink.ulx.drivers.model.InputStream;

import timber.log.Timber;

public class BleDomesticInputStream extends InputStreamCommons {

    /**
     * Factory method. Initializes with the given arguments. By default, this also
     * initializes the stream to trigger onDataAvailable delegate notifications
     * as soon as data arrives.
     * @param identifier An identifier used for JNI bridging and debugging.
     */

    public static BleDomesticInputStream newInstance(String identifier) {
        final BleDomesticInputStream instance = new BleDomesticInputStream(identifier);
        instance.initialize();
        return instance;
    }

    private BleDomesticInputStream(String identifier) {
        super(identifier, TransportType.BLUETOOTH_LOW_ENERGY, true);
    }

    @Override
    public void requestAdapterToOpen() {

        // Perhaps it will be enough to wait for the peripheral to connect and
        // manage the streams; we'll see how that plays out.
        Timber.e("ULX domestic input stream is " +
                         "being requested to open, but that is not supported yet");
    }

    @Override
    public void requestAdapterToClose() {
        Timber.e("ULX domestic input stream is " +
                         "being requested to close, but that is not supported yet");
    }

    /**
     * This method is called when the domestic input stream
     * ({@link BleDomesticInputStream}) is known to have been open. This means
     * that the stream meets all requirements to perform I/O. However, in the
     * case of a domestic input stream this isn't much, since the stream is
     * already open from creation. Regardless, this event must be triggered in
     * order to complete its lifecycle events. This method should not be public,
     * but the advertiser currently lives in a different package. This should
     * change in future, so this method should not be called.
     */
    public void notifyAsOpen() {
        super.onOpen();
    }

    /**
     * This method is called to give indication to the stream that data was
     * received. The stream will propagate the call for the data to be
     * processed, resulting in an {@link
     * InputStream.Delegate#onDataAvailable(InputStream)} call to the delegate.
     * @param data The data that was received.
     */
    public void notifyDataAvailable(byte[] data) {
        notifyDataReceived(data);
    }
}
