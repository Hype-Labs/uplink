package com.uplink.ulx.model;

/**
 * This enumeration provides a list of possible states that the framework can
 * be in. The state the framework is in indicates what activities it is
 * performing and what events are to be expected. The state can be queried
 * with the singleton instance method getState().
 */
public enum State {

    /**
     * The framework is not publishing the device on the network nor browsing
     * for other devices being published. The device is not participating on
     * the network, nor is it expected to be so until the framework is requested
     * to start, so no activity is expected until then. This state is not
     * expected to change until further activity is requested. The exception
     * is when the framework has previously been started, instances have been
     * found, and it was explicitly requested to stop. In that case I/O
     * operations can still occur with previously found instances. The fact
     * that the device is not advertising itself nor browsing for other
     * devices still holds.
     */
    IDLE(0),

    /**
     * The framework has been requested to start but the request is still
     * being processed. This state changes as soon as it is either actively
     * publishing the device on the network or actively browsing for other
     * devices. Instances cannot be found on the network yet. A state update
     * is expected soon.
     */
    STARTING(1),

    /**
     * The framework is actively participating on the network, meaning that
     * it could be advertising itself, browsing for other devices on the
     * network, or both. The framework is considered to be running if at least
     * one of its transport types is as well. If activity is not requested on
     * the framework (such as stopping) this state will change only if external
     * factors trigger a change in the adapter's state, such as the user turning
     * the adapter off, which will cause the framework to halt and become idle
     * with an error.
     */
    RUNNING(2),

    /**
     * The framework is actively participating on the network, and the process
     * to stop doing so has already begun but has yet not been completed. This
     * means that at least one of the transports is still stopping, although
     * others might have already done so. This state changes as soon as all of
     * the framework's transports have stopped. A state update is expected soon.
     */
    STOPPING(3)
    ;

    private final int value;

    /**
     * Initializes an enumeration instance with the given state code.
     * @param value The state code.
     */
    State(int value) {
        this.value = value;
    }

    /**
     * Returns the numeric value corresponding to the state code. Each state
     * has a different code that the framework uses internally. These values
     * are defined by convention.
     * @return The state's numeric value.
     */
    public final int getValue() {
        return this.value;
    }

    /**
     * Converts a given state value to a State instance, according to the
     * numeric specification for each state.
     * @param value The state value to convert.
     * @return The state value converted as a State instance.
     */
    public static State fromInt(int value) {

        switch (value) {
            case 0: return IDLE;
            case 1: return STARTING;
            case 2: return RUNNING;
            case 3: return STOPPING;
        }

        // Doesn't happen
        return IDLE;
    }
}
