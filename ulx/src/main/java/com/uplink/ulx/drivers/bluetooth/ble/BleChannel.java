package com.uplink.ulx.drivers.bluetooth.ble;

import com.uplink.ulx.TransportType;
import com.uplink.ulx.drivers.model.InputStream;
import com.uplink.ulx.drivers.model.OutputStream;
import com.uplink.ulx.drivers.commons.model.ChannelCommons;

/**
 * A BleChannel is an implementation of the Channel interface that is supposed
 * to implement logic that is specific to BLE. However, this class does not
 * have any functional responsibilities (only structural) and therefore it does
 * not implement any logic whatsoever. This may change in the future, still.
 */
class BleChannel extends ChannelCommons {

    /**
     * Constructor. Initializes with given arguments.
     * @param identifier An identifier for the Channel.
     * @param inputStream The stream responsible for input operations.
     * @param outputStream The stream responsible for output operations.
     */
    public BleChannel(String identifier, InputStream inputStream, OutputStream outputStream){
        super(identifier, TransportType.BLUETOOTH_LOW_ENERGY, inputStream, outputStream, true);
    }
}
