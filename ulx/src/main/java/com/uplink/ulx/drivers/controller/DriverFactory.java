package com.uplink.ulx.drivers.controller;

import android.content.Context;

import com.uplink.ulx.TransportType;
import com.uplink.ulx.drivers.bluetooth.ble.BleDriver;
import com.uplink.ulx.drivers.bluetooth.commons.BluetoothPermissionChecker;
import com.uplink.ulx.utils.StringUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * A DriverFactory is a helper class that instantiates drivers. It iterates
 * through all transport types and initializes the drivers that correspond to
 * it. The drivers are given the same delegates as the ones set for this class,
 * that being true for both StateDelegate and NetworkDelegate classes. This
 * class is also where all drivers are stored, meaning that it works as the
 * main in-memory storage unit for them.
 */
class DriverFactory {

    private List<Driver> allDrivers;
    private final Context context;
    private final WeakReference<Driver.StateDelegate> stateDelegate;
    private final WeakReference<Driver.NetworkDelegate> networkDelegate;
    private final Compatibility compatibility;

    /**
     * The constructor initializes the factory with a given Android environment
     * context and state and network delegates for the drivers.
     * @param context The Android environment context.
     * @param stateDelegate The Driver.StateDelegate for all drivers.
     * @param networkDelegate The Driver.NetworkDelegate for all drivers.
     */
    public DriverFactory(Context context, Driver.StateDelegate stateDelegate, Driver.NetworkDelegate networkDelegate) {
        this.context = context;
        this.stateDelegate = new WeakReference<>(stateDelegate);
        this.networkDelegate = new WeakReference<>(networkDelegate);
        this.compatibility = new Compatibility(context);
    }

    /**
     * Getter for the Android environment context set at construction time.
     * @return The Android environment context.
     */
    private Context getContext() {
        return this.context;
    }

    /**
     * Returns the Compatibility class that is used by this implementation to
     * check if a given transport type is supported by the system, as well as
     * whether different transports are capable of working together. For
     * example, if two transports use the same adapter, it's possible that the
     * two cannot function at the same time.
     * @return This class's Compatibility helper.
     */
    private Compatibility getCompatibility() {
        return this.compatibility;
    }

    /**
     * Getter for the Driver.StateDelegate instance that will be propagated to
     * the drivers when those are instantiated. After the drivers are
     * initialized, this will no longer be used.
     * @return The Driver.StateDelegate that will be used to initialize drivers.
     */
    private Driver.StateDelegate getStateDelegate() {
        return this.stateDelegate.get();
    }

    /**
     * Getter for the Driver.NetworkDelegate instance that will be propagated to
     * the drivers when those are instantiated. After the drivers are
     * initialized, this will no longer be used.
     * @return The NetworkDelegate that will be used to initialize drivers.
     */
    private Driver.NetworkDelegate getNetworkDelegate() {
        return this.networkDelegate.get();
    }

    /**
     * This method is the main entry point for the factory, since it lazily
     * instantiates and initializes all drivers. It iterates through all
     * transport types and checks whether the each is supported and whether
     * the necessary permissions are in place, in which case a driver will be
     * created for it. These drivers are then stored in the class, preventing
     * any future initializations.
     * @return A list of supported drivers.
     */
    public List<Driver> getAllDrivers() {
        if (this.allDrivers == null) {
            this.allDrivers = new ArrayList<>();

            int support = TransportType.NONE;

            for (int it = 1; it < TransportType.ALL ; it <<= 1) {

                Driver driver = null;

                // Don't continue if support has already been given
                if ((it & support) != 0) {
                    continue;
                }

                // Bluetooth Low Energy
                if ((it & TransportType.BLUETOOTH_LOW_ENERGY) != 0 && getCompatibility().isCompatible(TransportType.BLUETOOTH_LOW_ENERGY)) {
                    if (BluetoothPermissionChecker.isBleGranted(getContext())) {
                        driver = new BleDriver(StringUtils.generateRandomIdentifier(), getContext());
                    }
                }

                if (driver != null) {

                    Driver.StateDelegate stateDelegate = getStateDelegate();
                    Driver.NetworkDelegate networkDelegate = getNetworkDelegate();

                    // The driver's delegates are propagated to our own
                    driver.setStateDelegate(stateDelegate);
                    driver.setNetworkDelegate(networkDelegate);

                    // Keep track of which transport types are already supported
                    // It's notable that a driver may support multiple
                    // transports, in which case driver.getTransportType() will
                    // not equal the "it" iterator
                    support |= driver.getTransportType();

                    this.allDrivers.add(driver);
                }
            }
        }
        return this.allDrivers;
    }

    /**
     * This method returns true if all the drivers have the same state as the
     * one given as argument, and false otherwise. A single driver not sharing
     * that state is enough for this to yield false.
     * @param state The state to check.
     * @return Whetehr all drivers share the same state.
     */
    public boolean allDriversHaveState(Driver.State state) {
        for (Driver driver : getAllDrivers()) {
            if (driver.getState() != state) {
                return false;
            }
        }
        return true;
    }
}
