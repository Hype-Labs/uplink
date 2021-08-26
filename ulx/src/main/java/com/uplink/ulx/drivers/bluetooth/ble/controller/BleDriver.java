package com.uplink.ulx.drivers.bluetooth.ble.controller;

import android.bluetooth.BluetoothManager;
import android.content.Context;

import com.uplink.ulx.TransportType;
import com.uplink.ulx.drivers.bluetooth.ble.model.domestic.BleDomesticService;
import com.uplink.ulx.drivers.controller.Advertiser;
import com.uplink.ulx.drivers.bluetooth.commons.BluetoothStateListener;
import com.uplink.ulx.drivers.controller.Browser;
import com.uplink.ulx.drivers.controller.Driver;
import com.uplink.ulx.drivers.commons.controller.DriverCommons;

/**
 * This is the implementation of the Driver interface for the specific transport
 * of Bluetooth Low Energy. It initializes the Browser and the Advertiser, with
 * instances of BleBrowser and BleAdvertiser. It also instantiates other objects
 * that are common to the two of them, such as the BleDomesticService and the
 * BluetoothManager.
 */
public class BleDriver extends DriverCommons implements Driver {

    private BluetoothManager bluetoothManager;
    private BleDomesticService bleDomesticService;
    private Advertiser advertiser;
    private Browser browser;

    /**
     * Constructor. Initializes with the given parameters. It also makes sure
     * that the given Android environment context is registered as a broadcast
     * receiver (BroadcastReceiver), so that the implementation can later
     * subscribe to adapter state change events.
     * @param identifier The Driver's identifier to use.
     * @param context The Android environment context.
     */
    public BleDriver(String identifier, Context context) {
        super(identifier, TransportType.BLUETOOTH_LOW_ENERGY, context);
        BluetoothStateListener.register(context);
    }

    /**
     * Getter and factory for the BleDomesticService, which is used by the
     * implementation to hold a bunch of configuration attributes, used by
     * the implementation to match advertisers and scanners on the network.
     * This will make the scanner look for the services that are being
     * advertised, as well as match the necessary service attributes.
     * @return The BLE domestic service abstraction.
     */
    private BleDomesticService getDomesticService() {
        if (this.bleDomesticService == null) {
            this.bleDomesticService = new BleDomesticService();
        }
        return this.bleDomesticService;
    }

    /**
     * Getter and factory for the BluetoothManager, which is used throughout
     * the implementation to manage the Bluetooth adapter. This will be passed
     * to the Browser and the Advertiser, and this is shared by both
     * implementations.
     * @return The BluetoothManager instance.
     */
    private BluetoothManager getBluetoothManager() {
        if (this.bluetoothManager == null) {
            this.bluetoothManager = (BluetoothManager) getContext().getSystemService(Context.BLUETOOTH_SERVICE);
        }
        return this.bluetoothManager;
    }

    @Override
    public Advertiser getAdvertiser() {
        if (this.advertiser == null) {
            this.advertiser = new BleAdvertiser(
                    getIdentifier(),
                    getBluetoothManager(),
                    getDomesticService(),
                    getContext()
            );
            this.advertiser.setStateDelegate(this);
            this.advertiser.setNetworkDelegate(this);
        }
        return this.advertiser;
    }

    @Override
    public Browser getBrowser() {
        if (this.browser == null) {
            this.browser = new BleBrowser(
                    getIdentifier(),
                    getBluetoothManager(),
                    getDomesticService(),
                    getContext()
            );
            this.browser.setStateDelegate(this);
            this.browser.setNetworkDelegate(this);
        }
        return this.browser;
    }
}
