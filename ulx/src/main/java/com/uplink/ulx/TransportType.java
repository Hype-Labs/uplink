package com.uplink.ulx;

/**
 * This bitwise enumeration lists types of transport made available by the
 * framework. These values can be combined using bitwise operators to indicate
 * multi-transport configurations. The current implementation supports only
 * BLUETOOTH_LOW_ENERGY, and the others should be supported in the future.
 */
public class TransportType
{
    /**
     * Transport uses Bluetooth Low Energy technology.
     */
    public static final int BLUETOOTH_LOW_ENERGY = 0x01;

    /**
     * Transport uses Bluetooth Classic technology.
     */
    public static final int BLUETOOTH_CLASSIC = 0x02;

    /**
     * Transport uses Wi-Fi Direct technology.
     */
    public static final int WIFI_DIRECT = 0x04;

    /**
     * Transport uses Infrastructural Wi-Fi technology.
     */
    public static final int WIFI_INFRA = 0x08;

    /**
     * Transport uses an Internet connection.
     */
    public static final int WEB = 0x10;

    /**
     * This constant is used to signify all available types of transport
     * instead of relying on bitwise operators while listing all available
     * constants.
     */
    public static final int ALL = 0x1f;

    /**
     * Constant indicating the absence of any type of transport.
     */
    public static final int NONE = 0x00;

    /**
     * Private constructors prevents instantiation.
     */
    private TransportType() {
    }

    /**
     * Short descriptions are not suitable for displaying to the end user, but
     * they are useful for development, as they are short and unique for each
     * kind of transport. All short descriptions are three characters long. BLE
     * stands for Bluetooth Low Energy, BLC stands for Bluetooth Classic, WFD
     * stands for Wi-Fi Direct, WFI stands for Infrastructural Wi-Fi, and WEB
     * stands for an Internet transport. Combined transports yield a MIX tag
     * and the absence of transports yields NON.
     * @param transportType The transport type to describe.
     * @return The transport type short description.
     */
    public static String getShortDescription(int transportType) {

        switch (transportType) {

            case BLUETOOTH_LOW_ENERGY:
                return "BLE";

            case BLUETOOTH_CLASSIC:
                return "BLC";

            case WIFI_DIRECT:
                return "WFD";

            case WIFI_INFRA:
                return "WFI";

            case WEB:
                return "WEB";

            case NONE:
                return "NON";
        }

        return "MIX";
    }

    /**
     * This method will return a string describing the type of transport given
     * as argument. These strings should be suitable for presenting to the end
     * user, as they are descriptive of the underlying type of transport. This
     * method always returns the transport type's full name if it's a single
     * type of transport, and None if there's no transport type at all, as well
     * as Mixed if several types of transport are represented by the enumeration.
     * @param transportType The transport type to describe.
     * @return A full-length description of the transport type.
     */
    public static String getDescription(int transportType) {

        switch (transportType) {

            case BLUETOOTH_LOW_ENERGY:
                return "Bluetooth Low Energy";

            case BLUETOOTH_CLASSIC:
                return "Bluetooth Classic";

            case WIFI_DIRECT:
                return "Wi-Fi Direct";

            case WIFI_INFRA:
                return "Infrastructure Wi-Fi";

            case WEB:
                return "Online";

            case NONE:
                return "None";
        }

        return "Mixed";
    }

    /**
     * This method takes a transport type identifier in the form of an integer
     * and checks if it corresponds to a valid transport type. A valid transport
     * type is any that fits the model described by TransportType.
     * @param transportType The transport type to validate.
     * @return Whether the given transport type is valid.
     */
    public static boolean isValid(int transportType) {
        return transportType >= TransportType.NONE && transportType <= TransportType.ALL;
    }
}
