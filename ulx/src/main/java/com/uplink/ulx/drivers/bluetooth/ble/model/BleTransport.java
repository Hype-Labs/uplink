package com.uplink.ulx.drivers.bluetooth.ble.model;

import com.uplink.ulx.TransportType;
import com.uplink.ulx.drivers.model.Channel;
import com.uplink.ulx.drivers.commons.model.TransportCommons;

/**
 * The BleTransport is an implementation of the Transport interface that focuses
 * specifically on the BLE transport technology. Since the Transport abstraction
 * does not implement any transport-specific functionality, this implementation
 * doesn't do much, other than creating the space for future revisions of
 * transport-specific logic.
 */
public class BleTransport extends TransportCommons {

    /**
     * Constructor. Initializes with the given arguments.
     * @param identifier An identifier for the Transport.
     * @param reliableChannel The Transport's reliable Channel.
     */
    public BleTransport(String identifier, Channel reliableChannel) {
        super(identifier, TransportType.BLUETOOTH_LOW_ENERGY, reliableChannel);
    }
}
