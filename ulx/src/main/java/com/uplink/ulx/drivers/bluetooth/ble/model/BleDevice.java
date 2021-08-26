package com.uplink.ulx.drivers.bluetooth.ble.model;

import com.uplink.ulx.TransportType;
import com.uplink.ulx.drivers.model.Connector;
import com.uplink.ulx.drivers.model.Transport;
import com.uplink.ulx.drivers.commons.model.DeviceCommons;

/**
 * A BleDevice is a specialization of the Device interface that is meant
 * specifically for devices found over the BLE transport. Since the Device
 * abstraction doesn't (at this point) implement any transport-specific logic,
 * the abstraction simply serves as a means to identify the transport over
 * which the device was found. This, however, may change in the future.
 */
public class BleDevice extends DeviceCommons {

    /**
     * Constructor. Initializes with the given arguments.
     * @param identifier An identifier for the Device.
     * @param connector The Device's Connector.
     * @param transport The Device's Transport.
     */
    public BleDevice(String identifier, Connector connector, Transport transport) {
       super(identifier, TransportType.BLUETOOTH_LOW_ENERGY, connector, transport);
    }
}
