package com.uplink.ulx.drivers.controller;

import android.content.Context;

import com.uplink.ulx.TransportType;
import com.uplink.ulx.UlxError;
import com.uplink.ulx.UlxErrorCode;
import com.uplink.ulx.drivers.commons.StateManager;
import com.uplink.ulx.drivers.model.Device;
import com.uplink.ulx.threading.ExecutorPool;
import com.uplink.ulx.utils.SetOnceRef;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * A DriverManager is a collection of drivers that also behaves as a driver
 * itself. For example, when the driver manager is requested to start, all of
 * its drivers will be requested to start. This creates a point of convergence
 * in the drivers' states, while offering an API that enables managing drivers
 * in bundles as it was a driver itself. All delegate notifications for a driver
 * also apply to the DriverManager (NetworkDelegate and StateDelegate). The
 * manager's transport type corresponds to all driver's transport types ORed
 * together. The DriverManager is said to be RUNNING or STARTING if at least
 * one of its underlying drivers is RUNNING or STARTING as well, and IDLE iff
 * all of its drivers are IDLE. This abstracts away the details of each driver
 * and instead enables the SDK to work as a single unit for all types of
 * transport.
 */
public class DriverManager implements Driver, Driver.NetworkDelegate, Driver.StateDelegate {

    private final SetOnceRef<StateManager> stateManager;
    private final String identifier;
    private final SetOnceRef<DriverFactory> driverFactory = new SetOnceRef<>();
    private volatile Driver.NetworkDelegate networkDelegate;
    private volatile StateDelegate stateDelegate;
    private int transportType;
    private HashMap<String, Driver> failedDrivers;
    private final Context context;

    // TODO this boolean flag appears to be a workaround for duplicate
    //  notifications when the adapter becomes ready. This can be motivated by
    //  two things: (1) both the browser and the advertiser give a ready
    //  notification, and (2) the implementation did not unsubscribe from
    //  adapter state change events. I'm not sure how the former can be fixed
    //  other than only one of the two giving a notification, but the latter
    //  should be fixed because it's documented that the implementation
    //  unsubscribed from adapter state change events. Ideally, this problem
    //  is not solved by the driver manager, but by the subscription listener.
    private boolean ready;

    /**
     * Factory method. Initializes the driver manager with given parameters. The
     * delegates (NetworkDelegate and StateDelegate) are kept as weak references
     * to avoid cyclic memory references. The Android environment Context is
     * kept as a weak reference as well, to prevent memory leaks. The transport
     * type corresponds to the transport of all supported drivers ORed together.
     * @param identifier A unique identifier for debug purposes.
     * @param networkDelegate The NetworkDelegate.
     * @param stateDelegate The StateDelegate.
     * @param context The Android environment Context.
     */
    public static DriverManager newInstance(
            String identifier,
            Driver.NetworkDelegate networkDelegate,
            StateDelegate stateDelegate,
            Context context
    ) {
        final DriverManager instance = new DriverManager(
                identifier,
                networkDelegate,
                stateDelegate,
                context
        );
        instance.initialize();
        return instance;
    }

    private DriverManager(
            String identifier,
            Driver.NetworkDelegate networkDelegate,
            StateDelegate stateDelegate,
            Context context
    ) {

        Objects.requireNonNull(identifier);
        Objects.requireNonNull(networkDelegate);
        Objects.requireNonNull(stateDelegate);
        Objects.requireNonNull(context);

        this.identifier = identifier;
        this.transportType = TransportType.NONE;
        this.stateManager = new SetOnceRef<>();
        this.networkDelegate = networkDelegate;
        this.stateDelegate = stateDelegate;
        this.context = context;
        this.ready = false;
    }
    
    private void initialize() {
        stateManager.setRef(new StateManager(
                new StateManager.Delegate() {
                    @Override
                    public void requestStart(StateManager stateManager) {
                        requestAllDriversToStart();
                    }

                    @Override
                    public void onStart(StateManager stateManager) {
                        StateDelegate stateDelegate = getStateDelegate();
                        if (stateDelegate != null) {
                            stateDelegate.onStart(DriverManager.this);
                        }
                    }

                    @Override
                    public void onFailedStart(StateManager stateManager, UlxError error) {
                        StateDelegate stateDelegate = getStateDelegate();
                        if (stateDelegate != null) {
                            stateDelegate.onFailedStart(DriverManager.this, error);
                        }
                    }

                    @Override
                    public void onStop(StateManager stateManager, UlxError error) {
                        StateDelegate stateDelegate = getStateDelegate();
                        if (stateDelegate != null) {
                            stateDelegate.onStop(DriverManager.this, error);
                        }
                    }

                    @Override
                    public void onStateChange(StateManager stateManager) {
                        StateDelegate stateDelegate = getStateDelegate();
                        if (stateDelegate != null) {
                            stateDelegate.onStateChange(DriverManager.this);
                        }
                    }

                    @Override
                    public void requestStop(StateManager stateManager) {
                        requestAllDriversToStop();
                    }
                }));

        driverFactory.setRef(new DriverFactory(getContext(), this, this));
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public int getTransportType() {
        if (this.transportType == TransportType.NONE) {
            for (Driver driver : getAllDrivers()) {
                this.transportType |= driver.getTransportType();
            }
        }
        return this.transportType;
    }

    @Override
    public Context getContext() {
        return this.context;
    }

    @Override
    public State getState() {
        return State.fromInt(getStateManager().getState().getValue());
    }

    @Override
    public void setStateDelegate(StateDelegate driverStateDelegate) {
        this.stateDelegate = driverStateDelegate;
    }

    @Override
    public StateDelegate getStateDelegate() {
        return this.stateDelegate;
    }

    @Override
    public void setNetworkDelegate(NetworkDelegate driverNetworkDelegate) {
        this.networkDelegate = driverNetworkDelegate;
    }

    @Override
    public Driver.NetworkDelegate getNetworkDelegate() {
        return networkDelegate;
    }

    /**
     * Getter for the driver factory. If the factory was not initialized before
     * it will be now. The current instance is also given as a delegate for the
     * drivers to be constructed.
     * @return The DriverFactory.
     */
    private DriverFactory getDriverFactory() {
        return this.driverFactory.getRef();
    }

    /**
     * Helper method that returns the list of all drivers initialized by the
     * DriverFactory.
     * @return The list of all initialized drivers.
     */
    private List<Driver> getAllDrivers() {
        return getDriverFactory().getAllDrivers();
    }

    /**
     * Getter for the StateManager that is managing the class's state.
     * @return The DriverManager's state manager instance.
     * @see StateManager
     */
    private StateManager getStateManager() {
        return stateManager.getRef();
    }

    /**
     * The "ready" flag is used as a means to prevent the delegate from getting
     * onReady() notifications when the adapter is off, after the caller asks
     * the driver manager to stop. This setter sets the value for that flag.
     * @param ready The readiness value to set.
     */
    private void setReady(boolean ready) {
        this.ready = ready;
    }

    /**
     * This method returns the value currently set for the "ready" flag. This
     * flag is used to prevent onReady() notifications on the delegate when the
     * adapter is off, after the caller asks the driver manager to stop.
     * @return The current readiness vale.
     */
    private boolean isReady() {
        return this.ready;
    }

    /**
     * Returns the list of drivers that were requests to start but failed in
     * doing so. This is a hash map of (identifier, driver) pairs, which maps
     * drivers with their random identifiers. These identifiers must be unique
     * for this reason.
     * @return A map of drivers that failed to start.
     */
    private HashMap<String, Driver> getFailedDrivers() {
        if (this.failedDrivers == null) {
            this.failedDrivers = new HashMap<>();
        }
        return this.failedDrivers;
    }

    /**
     * Registers a new driver as having failed to start. The implementation will
     * keep track of this registry until the driver the driver becomes ready.
     * @param driver The driver to register as having failed.
     */
    private void addFailedDriver(Driver driver) {
        getFailedDrivers().put(driver.getIdentifier(), driver);
    }

    /**
     * Removes a driver from the registry of failed drivers. This should happen
     * after the driver has failed to start, but became ready in the meanwhile.
     * This does not mean that the driver has finally been successfully in
     * starting, but rather that whatever the causes were for it to not being
     * capable of starting might no longer apply. This often refers to the
     * adapter being turned off, the main reason for drivers to fail.
     * @param driver The driver to remove from the registry.
     */
    private void removeFailedDriver(Driver driver) {
        getFailedDrivers().remove(driver.getIdentifier());
    }

    @Override
    public void start() {
        ExecutorPool.getDriverManagerExecutor().execute(() -> {
            setReady(false);
            getStateManager().start();
        });
    }

    @Override
    public void stop() {
        ExecutorPool.getDriverManagerExecutor().execute(() -> {
            // To avoid receiving isReady notifications when the adapter is off,
            // after the user asks the driver to stop.
            setReady(true);
            getStateManager().stop();
        });
    }

    @Override
    public void destroy() {
        for (Driver driver : getDriverFactory().getAllDrivers()) {
            driver.destroy();
        }
    }

    /**
     * This is a helper method to decide whether a driver should be started.
     * Currently, it doesn't do anything, and rather just always returns true.
     * However, future implementations may use logic to decide whether to start
     * the given driver.
     * @param driver The driver to device upon.
     * @return Whether to start the driver (currently always true).
     */
    private boolean shouldStartDriver(Driver driver) {
        return true;
    }

    /**
     * Iterates all drivers requesting them to start. Only drivers that are IDLE
     * and for which shouldStartDriver(Driver) returns true will be requested
     * to start. If no drivers can be started, for whatever reason, the delegate
     * will be notified.
     */
    private void requestAllDriversToStart() {

        // This error is a bit too generic for the purpose that it serves. It
        // just so happens that this method does not have a lot of information
        // on the reason why the drivers have failed, which is something that
        // should be addressed.
        UlxError error = new UlxError(
            UlxErrorCode.UNKNOWN,
            "Couldn't join the network because no driver could be initialized.",
            "There might be insufficient permissions or not enough transports available.",
            "Try turning the Bluetooth and WiFi adapters on and granting all necessary permissions."
        );

        // Did any drivers register successfully?
        if (getDriverFactory().getAllDrivers().size() == 0) {
            getStateManager().notifyFailedStart(error);
            return;
        }

        boolean someDriverStarting = false;

        // Request drivers to start
        for (Driver driver : getAllDrivers()) {
            if (driver.getState() == State.IDLE && shouldStartDriver(driver)) {
                driver.start();
                someDriverStarting = true;
            }
        }

        // Did we succeed on any drivers?
        if (!someDriverStarting) {
            if (getDriverFactory().allDriversHaveState(State.IDLE)) {
                getStateManager().notifyFailedStart(error);
            }
        }
    }

    /**
     * Requests all drivers to stop. Only drivers that are not IDLE will be
     * requested to stop. Even those that are STARTING will be requested to
     * stop, in which case the driver should revert its state as soon as the
     * starting process completes.
     */
    private void requestAllDriversToStop() {
        for (Driver driver : getDriverFactory().getAllDrivers()) {
            if (driver.getState() != State.IDLE) {
                driver.stop();
            }
        }
    }

    @Override
    public void onDeviceFound(Driver driver, Device provider) {
        NetworkDelegate networkDelegate = getNetworkDelegate();
        if (networkDelegate != null) {
            networkDelegate.onDeviceFound(this, provider);
        }
    }

    @Override
    public void onDeviceLost(Driver driver, Device provider, UlxError error) {
        NetworkDelegate networkDelegate = getNetworkDelegate();
        if (networkDelegate != null) {
            networkDelegate.onDeviceLost(this, provider, error);
        }
    }

    @Override
    public void onStart(Driver driver) {
        ExecutorPool.getDriverManagerExecutor().execute(() -> {
            if (DriverManager.this.getState() != State.RUNNING) {
                getStateManager().notifyStart();
            }
        });
    }

    @Override
    public void onFailedStart(final Driver driver, final UlxError error) {
        ExecutorPool.getDriverManagerExecutor().execute(() -> {
            addFailedDriver(driver);

            ExecutorPool.getScheduledExecutor(getTransportType()).schedule(() -> {
                if (getDriverFactory().allDriversHaveState(State.IDLE)) {
                    getStateManager().notifyFailedStart(error);
                }
            }, 1, TimeUnit.SECONDS);
        });
    }

    @Override
    public void onStop(final Driver driver, final UlxError error) {
        ExecutorPool.getDriverManagerExecutor().execute(() -> {
            if (getDriverFactory().allDriversHaveState(State.IDLE)) {
                getStateManager().notifyStop(error);
            }
        });
    }

    @Override
    public void onReady(final Driver driver) {
        ExecutorPool.getDriverManagerExecutor().execute(() -> {
            removeFailedDriver(driver);

            // The driver is requested to start if the DriverManager is still
            // running; this will attempt to converge the state of all drivers.
            if (getState() == State.RUNNING) {
                driver.start();
            } else if (!isReady()) {
                setReady(true);

                // Notify the delegate
                StateDelegate stateDelegate = getStateDelegate();
                if (stateDelegate != null) {
                    stateDelegate.onReady(this);
                }
            }
        });
    }

    @Override
    public void onStateChange(Driver driver) {
    }
}
