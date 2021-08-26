package com.uplink.ulx.drivers.bluetooth.ble.model.foreign;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import com.uplink.ulx.drivers.bluetooth.ble.model.domestic.BleDomesticService;

import java.util.UUID;

/**
 * This class represents a service that is published by another device, hence
 * it being "foreign". It parses the service characteristics and descriptors,
 * and performs validation of services found on the network.
 */
public class BleForeignService {

    private final BluetoothGattService bluetoothGattService;

    private final BluetoothGattCharacteristic reliableInputWrite;
    private final BluetoothGattCharacteristic reliableOutputRead;
    private final BluetoothGattCharacteristic reliableControl;

    private final BluetoothGattDescriptor descriptorReliableOutputRead;
    private final BluetoothGattDescriptor descriptorReliableControl;

    /**
     * This constructor initializes the instance with the given characteristics
     * and descriptors and encapsulates them in the newly created instance. This
     * constructor is private because the instance must be constructed with
     * validateAndMake(BluetoothGattService, BleDomesticService), which performs
     * additional validation.
     * @param bluetoothGattService The BluetoothGattService.
     * @param reliableInputWrite The reliable input characteristic.
     * @param reliableOutputRead The reliable output characteristic.
     * @param reliableControl The reliable control characteristic.
     * @param descriptorReliableOutputRead The reliable output descriptor.
     * @param descriptorReliableControl The reliable control descriptor.
     */
    private BleForeignService(
            BluetoothGattService bluetoothGattService,
            BluetoothGattCharacteristic reliableInputWrite,
            BluetoothGattCharacteristic reliableOutputRead,
            BluetoothGattCharacteristic reliableControl,
            BluetoothGattDescriptor descriptorReliableOutputRead,
            BluetoothGattDescriptor descriptorReliableControl
    ) {
        this.bluetoothGattService = bluetoothGattService;
        this.reliableInputWrite = reliableInputWrite;
        this.reliableOutputRead = reliableOutputRead;
        this.reliableControl = reliableControl;
        this.descriptorReliableOutputRead = descriptorReliableOutputRead;
        this.descriptorReliableControl = descriptorReliableControl;
    }

    /**
     * This method receives a BluetoothGattService and validates whether the
     * service is in accordance with the service specification given by the
     * BleDomesticService specification. This means that the BLE characteristics
     * and descriptors must match the given specification. This should be called
     * on discovered services in order to validate whether the service matches
     * the specification. If it doesn't, this method returns null and, if it
     * does, it returns an instance of BleForeignService with the parsed
     * characteristics and descriptors instead. It's noticeable that it does
     * not provide any information as to why the services don't match because
     * that's not relevant; any form of mismatch with the specification is
     * considered a protocol violation.
     * @param bluetoothGattService The BluetoothGattService to validate.
     * @return A BleForeignService instance if the service validates, or null.
     */
    public static BleForeignService validateAndMake(
            BluetoothGattService bluetoothGattService
    ) {
        // Characteristics that will be passed to the constructor
        BluetoothGattCharacteristic reliableInputWrite = null;
        BluetoothGattCharacteristic reliableOutputRead = null;
        BluetoothGattCharacteristic reliableControl = null;
        BluetoothGattDescriptor descriptorReliableOutputRead;
        BluetoothGattDescriptor descriptorReliableControl;

        for (BluetoothGattCharacteristic characteristic : bluetoothGattService.getCharacteristics()) {

            String characteristicUuid = characteristic.getUuid().toString();

            // Does the reliable input characteristic display reliable input properties?
            if (characteristicUuid.equalsIgnoreCase(BleDomesticService.RELIABLE_INPUT_IDENTIFIER)) {
                if (!hasReliableInputProperties(characteristic)) {
                    return null;
                }

                reliableInputWrite = characteristic;
            }

            // Does the reliable output characteristic display reliable output properties?
            else if (characteristicUuid.equalsIgnoreCase(BleDomesticService.RELIABLE_OUTPUT_IDENTIFIER)) {
                if (!hasReliableOutputProperties(characteristic)) {
                    return null;
                }

                reliableOutputRead = characteristic;
            }

            // Only the control characteristic remaining
            else {

                // The control characteristic is reliable output
                if (!hasReliableOutputProperties(characteristic)) {
                    return null;
                }

                reliableControl = characteristic;
            }
        }

        // Make sure that all characteristics have been identified
        if (reliableInputWrite == null || reliableOutputRead == null || reliableControl == null) {
            return null;
        }

        UUID descriptorUuid = UUID.fromString(BleDomesticService.DESCRIPTOR_CONFIGURATION);

        // Extract the descriptors
        descriptorReliableOutputRead = reliableOutputRead.getDescriptor(descriptorUuid);
        descriptorReliableControl = reliableControl.getDescriptor(descriptorUuid);

        // Are the descriptors present?
        if (descriptorReliableOutputRead == null || descriptorReliableControl == null) {
            return null;
        }

        return new BleForeignService(
                bluetoothGattService,
                reliableInputWrite,
                reliableOutputRead,
                reliableControl,
                descriptorReliableOutputRead,
                descriptorReliableControl
        );
    }

    /**
     * Getter for the BluetoothGattService.
     * @return The BluetoothGattService.
     */
    public final BluetoothGattService getBluetoothGattService() {
        return this.bluetoothGattService;
    }

    /**
     * Getter for the reliable input characteristic.
     * @return The reliable input BluetoothGattCharacteristic.
     */
    public final BluetoothGattCharacteristic getReliableInputWrite() {
        return this.reliableInputWrite;
    }

    /**
     * Getter for the reliable output characteristic.
     * @return The reliable output BluetoothGattCharacteristic.
     */
    public final BluetoothGattCharacteristic getReliableOutputRead() {
        return this.reliableOutputRead;
    }

    /**
     * Getter for the reliable control characteristic.
     * @return The reliable control BluetoothGattCharacteristic.
     */
    public final BluetoothGattCharacteristic getReliableControl() {
        return this.reliableControl;
    }

    /**
     * Getter for the reliable output descriptor.
     * @return The reliable output BluetoothGattDescriptor.
     */
    public final BluetoothGattDescriptor getDescriptorReliableOutputRead() {
        return this.descriptorReliableOutputRead;
    }

    /**
     * Getter for the reliable control descriptor.
     * @return The reliable control BluetoothGattDescriptor.
     */
    public final BluetoothGattDescriptor getDescriptorReliableControl() {
        return this.descriptorReliableControl;
    }

    /**
     * Returns true if the given BluetoothCattCharacteristic display the proper
     * properties to be classified as a reliable input characteristic, making
     * sure that it's in accordance with the specification.
     * @param bluetoothGattCharacteristic The characteristic to validate.
     * @return Whether the characteristic has reliable input properties.
     */
    private static boolean hasReliableInputProperties(BluetoothGattCharacteristic bluetoothGattCharacteristic) {
        return (bluetoothGattCharacteristic.getProperties() & (BluetoothGattCharacteristic.PROPERTY_WRITE)) == BluetoothGattCharacteristic.PROPERTY_WRITE;
    }

    /**
     * Returns true if the given BluetoothGattCharacteristic display the proper
     * properties to be classified as a reliable output characteristic, making
     * sure that it's in accordance with the specification.
     * @param bluetoothGattCharacteristic The characteristic to validate.
     * @return Whether the characteristic has reliable output properties.
     */
    private static boolean hasReliableOutputProperties(BluetoothGattCharacteristic bluetoothGattCharacteristic) {
        return (bluetoothGattCharacteristic.getProperties() & (BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_INDICATE)) == (BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_INDICATE);
    }
}
