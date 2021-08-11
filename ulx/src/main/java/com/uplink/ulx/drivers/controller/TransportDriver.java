package com.uplink.ulx.drivers.controller;

/**
 * A TransportDriver is an abstraction of a driver that extends on the Driver
 * interface to further add an Advertiser and Browser pair. This makes
 * TransportDriver suitable for drivers that implement actual transports, but
 * often not for bundled implementations (such as the DriverManager).
 */
public interface TransportDriver extends Driver {

    /**
     * Getter for the driver's Advertiser, which will share the same transport.
     * @return The driver's advertiser.
     */
    Advertiser getAdvertiser();

    /**
     * Getter for the driver's browser, which will share the same transport.
     * @return The driver's browser.
     */
    Browser getBrowser();
}
