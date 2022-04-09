package com.uplink.ulx.drivers.bluetooth.ble.model.passive;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.os.Build;
import android.os.Looper;

import com.uplink.ulx.utils.StringUtils;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import androidx.annotation.MainThread;

/**
 * This class provides a description of the service that is published by the
 * BLE implementation. It defines the service and characteristic attributes,
 * such as identifiers, properties, permissions, and so on. This works as a
 * factory for the BLE service. It can also be used to validate other services
 * as they are found on the network, since all services must match this
 * specification; this is true with BleForeignService, which uses this
 * specification for validation purposes.
 *
 * The class is thread-safe
 */
public class BleDomesticService {

    /**
     * The control value size corresponds to the number of bytes in the control
     * characteristic that are used for control operations. The implementation
     * will use only this amount of bytes from the characteristic as I/O for
     * that purpose.
     */
    public static final int CONTROL_VALUE_SIZE = 4;

    private static final String GATT_SERVICE_IDENTIFIER_16BITS = "00009F63-0000-1000-8000-00805F9B34FB";
    private static final String GATT_SERVICE_IDENTIFIER = "B7912165-5ABC-4EE0-9F33-835FE8D8DE1A";

    // Characteristic identifiers (the unreliable ones are not used yet)
    public static final String RELIABLE_INPUT_IDENTIFIER = "6BD2F771-CA1E-4D8F-AC93-79129E5D8A30";
    public static final String RELIABLE_OUTPUT_IDENTIFIER = "C869D972-E667-49BA-A0D2-1EC2BD45D6B7";
    private static final String UNRELIABLE_INPUT_IDENTIFIER = "00AA86C3-9BB1-48C2-A4C4-6A575BA43E35";
    private static final String UNRELIABLE_OUTPUT_IDENTIFIER = "4E6EFC38-6A36-4822-A863-6CC415C7C86C";

    public static final String DESCRIPTOR_CONFIGURATION = "00002902-0000-1000-8000-00805F9B34FB";

    private final String gattServiceCharacteristicControlIdentifier;
    private final byte [] controlValue;

    private BluetoothGattService coreService;

    private BluetoothGattCharacteristic reliableInputWrite;
    private BluetoothGattCharacteristic reliableOutputRead;
    private BluetoothGattCharacteristic reliableControl;

    private BluetoothGattDescriptor reliableDescriptorOutputRead;
    private BluetoothGattDescriptor reliableDescriptorControl;

    public BleDomesticService() {
        String controlId = UUID.randomUUID().toString();
        if (Build.MODEL.startsWith("P43")) {// Allview
            controlId = controlId.replaceAll("^\\w\\w","00");
        }
        this.gattServiceCharacteristicControlIdentifier = controlId;
        controlValue = Arrays.copyOfRange(
                StringUtils.hexStringToByteArray(getControlCharacteristicIdentifier()),
                0,
                CONTROL_VALUE_SIZE
        );
    }

    /**
     * Get a random UUID that persists throughout the lifetime of the class
     * instance, and corresponds to the identifier that will be assigned to the control
     * characteristic. This identifier will be used, among other things, to determine who is the
     * initiator of a given link. For example, if two devices are acting both as server and client
     * simultaneously, the lexicographic lower of the two will be the one to initiate the
     * connection.
     *
     * @return The identifier to use for the control characteristic.
     */
    public final String getControlCharacteristicIdentifier() {
        return gattServiceCharacteristicControlIdentifier;
    }

    /**
     * This method returns the core Bluetooth GATT service built according to
     * the specification provided by this class. This will correspond to the
     * service with all characteristics (and respective descriptors) ready to
     * be advertised. If the service has not been initialized at this point,
     * it will be now.
     * @return The core Bluetooth GATT service.
     */
    @MainThread
    public final BluetoothGattService getCoreService() {
        if (this.coreService == null) {

            assert Looper.myLooper() == Looper.getMainLooper();

            BluetoothGattService coreService = new BluetoothGattService(
                    UUID.fromString(GATT_SERVICE_IDENTIFIER),
                    BluetoothGattService.SERVICE_TYPE_PRIMARY
            );

            if (addCharacteristics(coreService)) {
                this.coreService = coreService;
            }
        }

        return this.coreService;
    }

    /**
     * This getter returns the first CONTROL_VALUE_SIZE bytes of the control
     * characteristic, which are used (together with service UUID 16bit version)
     * to advertise the service in the network. This will correspond to the
     * service's advertise data, and help other devices decide whether to be
     * the initiators of the connection between the two.
     * @return The first CONTROL_VALUE_SIZE bytes of the control characteristic.
     */
    public final byte[] getControlValue() {
        return this.controlValue;
    }

    /**
     * This method commits all defined characteristics to the service by
     * adding them to the service specification. This will result in the given
     * instance of BluetoothGattService having the characteristics added. If
     * the characteristics have already been previously added, the behaviour is
     * undefined.
     * @param service The service acting as the receiver for the characteristics.
     * @return Whether the characteristics have been successfully added.
     */
    @MainThread
    private boolean addCharacteristics(BluetoothGattService service) {
        assert Looper.myLooper() == Looper.getMainLooper();

        return service.addCharacteristic(getReliableInputCharacteristic())
                && service.addCharacteristic(getReliableOutputCharacteristic())
                && service.addCharacteristic(getReliableControl())
                ;
    }

    /**
     * This getter returns the reliable input GATT characteristic, used by the
     * server to accept input from the peripherals. The characteristic will have
     * WRITE properties, since that's how the GATT server will accept input
     * from the clients.
     * @return The reliable input GATT characteristic.
     */
    public final BluetoothGattCharacteristic getReliableInputCharacteristic() {
        if (this.reliableInputWrite == null) {
            this.reliableInputWrite = new BluetoothGattCharacteristic(
                    UUID.fromString(RELIABLE_INPUT_IDENTIFIER),
                    BluetoothGattCharacteristic.PROPERTY_WRITE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE
            );
        }
        return this.reliableInputWrite;
    }

    /**
     * This getter returns the the reliable output GATT characteristic, meaning
     * the characteristic that is used for reliable output operations by the
     * GATT server. The characteristic will have READ properties, since the
     * client is expected to subscribe and read from it in order for the server
     * to produce output.
     * @return The reliable output BluetoothGattCharacteristic.
     */
    @MainThread
    public final BluetoothGattCharacteristic getReliableOutputCharacteristic() {
        if (this.reliableOutputRead == null) {
            assert Looper.myLooper() == Looper.getMainLooper();
            this.reliableOutputRead = new BluetoothGattCharacteristic(
                    UUID.fromString(RELIABLE_OUTPUT_IDENTIFIER),
                    BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_INDICATE,
                    BluetoothGattCharacteristic.PERMISSION_READ
            );

            if (!this.reliableOutputRead.addDescriptor(getDescriptorReliableOutputRead())) {
                return null;
            }
        }
        return this.reliableOutputRead;
    }

    /**
     * This method returns the GATT characteristic used for control operations.
     * Such operations include the server issuing action requests to the
     * peripheral, such as it disconnecting. Such operations occur on a request
     * basis, meaning that the peripheral might decide to cooperate and comply
     * with the requested operations. This will be a readable characteristic,
     * since the peripheral should get updates from the central by having the
     * characteristic change in value.
     * @return The control BluetoothGattCharacteristic.
     */
    @MainThread
    public final BluetoothGattCharacteristic getReliableControl() {
        if (this.reliableControl == null) {
            assert Looper.myLooper() == Looper.getMainLooper();
            this.reliableControl = new BluetoothGattCharacteristic(
                    UUID.fromString(getControlCharacteristicIdentifier()),
                    BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_INDICATE,
                    BluetoothGattCharacteristic.PERMISSION_READ
            );

            if (!this.reliableControl.addDescriptor(getDescriptorReliableControl())) {
                return null;
            }
        }
        return this.reliableControl;
    }

    /**
     * This getter returns the reliable output GATT descriptor, as used by the
     * reliable output GATT characteristic. If the descriptor has not been
     * instantiated yet, it will be now.
     * @return The reliable output GATT descriptor.
     */
    public final BluetoothGattDescriptor getDescriptorReliableOutputRead() {
        if (this.reliableDescriptorOutputRead == null) {
            this.reliableDescriptorOutputRead = new BluetoothGattDescriptor(
                    UUID.fromString(DESCRIPTOR_CONFIGURATION),
                    BluetoothGattDescriptor.PERMISSION_WRITE
            );
        }
        return this.reliableDescriptorOutputRead;
    }

    /**
     * This getter returns the control GATT descriptor, used by the control
     * characteristic. If the descriptor has not been instantiated yet, it will
     * be now.
     * @return The reliable control GATT descriptor.
     */
    public final BluetoothGattDescriptor getDescriptorReliableControl() {
        if (this.reliableDescriptorControl == null) {
            this.reliableDescriptorControl = new BluetoothGattDescriptor(
                    UUID.fromString(DESCRIPTOR_CONFIGURATION),
                    BluetoothGattDescriptor.PERMISSION_WRITE
            );
        }
        return this.reliableDescriptorControl;
    }

    /**
     * Checks whether the given BluetoothGattCharacteristic has the same UUID
     * as is expected for the reliable control characteristic. This can be
     * used to validate a given characteristic when it's being subscribed.
     * @param characteristic The characteristic to validate.
     * @return Whether the given characteristic has the UUID expected for the
     * reliable control characteristic.
     */
    public boolean isReliableControl(BluetoothGattCharacteristic characteristic) {

        BluetoothGattCharacteristic reliableControl = Objects.requireNonNull(getReliableControl());
        String characteristicUuid = characteristic.getUuid().toString();
        String reliableControlUuid = reliableControl.getUuid().toString();

        return characteristicUuid.equalsIgnoreCase(reliableControlUuid);
    }

    /**
     * Checks whether the given BluetoothGattDescriptor belongs to a GATT
     * characteristic that uses the same UUID as is expected for the control
     * characteristic. This can be used as an helper method to validate whether
     * the descriptor corresponds to the control characteristic.
     * @param descriptor The descriptor to check.
     * @return Whether the descriptor is from a reliable control characteristic.
     */
    public boolean isReliableControl(BluetoothGattDescriptor descriptor) {
        return isReliableControl(descriptor.getCharacteristic());
    }

    /**
     * Checks whether the given BluetoothGattCharacteristic has the same UUID
     * as is expected for the reliable output characteristic. This can be
     * used to validate a given characteristic when it's being subscribed.
     * @param characteristic The characteristic to validate.
     * @return Whether the given characteristic has the UUID expected for the
     * reliable output characteristic.
     */
    public boolean isReliableOutput(BluetoothGattCharacteristic characteristic) {
        String characteristicUuid = characteristic.getUuid().toString();
        String reliableOutputUuid = Objects.requireNonNull(getReliableOutputCharacteristic()).getUuid().toString();

        return characteristicUuid.equalsIgnoreCase(reliableOutputUuid);
    }

    /**
     * Checks whether the given BluetoothGattDescriptor belongs to a GATT
     * characteristic that uses the same UUID as is expected for the reliable
     * output characteristic. This can be used as an helper method to validate
     * whether the descriptor corresponds to the output stream.
     * @param descriptor The descriptor to check.
     * @return Whether the descriptor is from a reliable output characteristic.
     */
    public boolean isReliableOutput(BluetoothGattDescriptor descriptor) {
        return isReliableOutput(descriptor.getCharacteristic());
    }

    /**
     * Returns the Bluetooth GATT service identifier according to the
     * specification. This identifier is used by the service so that the
     * implementation is aware that a given service is being published, and
     * therefore the device is likely to be advertising a service with a
     * matching specification. This constitutes a form of pre-validation of
     * the service, meaning that it is likely that the advertising device will
     * conform with the service. However, any device can advertise services
     * with this identifier, so the implementation should still perform further
     * validation.
     * @return The Bluetooth GATT service identifier.
     */
    public static String getGattServiceIdentifier() {
        return GATT_SERVICE_IDENTIFIER;
    }

    /**
     * This method returns a 16bit version of the service identifier that is
     * advertised by the devices. This corresponds to a shorter version of the
     * service identifier that will fit within the advertise broadcast data,
     * and that can be used as a pre-map of available services during the
     * discovery process. The validation of this identifier should not cause
     * the implementation to ignore the validation of the full identifier.
     * @return The 16bit version of the service identifier.
     */
    public static String getGattServiceIdentifier16bits() {
        return GATT_SERVICE_IDENTIFIER_16BITS;
    }
}
