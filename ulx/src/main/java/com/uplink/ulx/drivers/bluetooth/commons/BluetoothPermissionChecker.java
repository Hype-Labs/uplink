package com.uplink.ulx.drivers.bluetooth.commons;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

/**
 * This class is a repository for static methods that check for permissions for
 * Bluetooth activity within the Android environment.
 */
public class BluetoothPermissionChecker {

    /**
     * Allows applications to connect to paired bluetooth devices.
     */
    private static final String ANDROID_PERMISSION_BLUETOOTH = "android.permission.BLUETOOTH";

    /**
     * Allows applications to discover and pair bluetooth devices.
     */
    private static final String ANDROID_PERMISSION_BLUETOOTH_ADMIN = "android.permission.BLUETOOTH_ADMIN";

    /**
     * Checks whether the android.permission.BLUETOOTH_ADMIN permission was granted.
     * @param context The Android environment Context.
     * @return Whether the permission was granted.
     */
    private static boolean isBluetoothAdminPermissionGranted(Context context) {
        return context.checkCallingOrSelfPermission(ANDROID_PERMISSION_BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Checks whether the android.hardware.bluetooth_le permission was granted.
     * @param context The Android environment Context.
     * @return Whether the permission was granted.
     */
    private static boolean isBluetoothLePermissionGranted(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    /**
     * Checks whether the android.permission.BLUETOOTH permission was granted.
     * @param context The Android environment Context.
     * @return Whether the permission was granted.
     */
    private static boolean isBluetoothPermissionGranted(Context context) {
        return context.checkCallingOrSelfPermission(ANDROID_PERMISSION_BLUETOOTH) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * This method checks if all necessary permissions to run BLE have been
     * granted. This includes android.permission.BLUETOOTH_ADMIN,
     * android.hardware.bluetooth_le and android.permission.BLUETOOTH.
     * @param context The Android environment Context.
     * @return Whether all necessary permissions have been granted.
     */
    public static boolean isBleGranted(Context context) {
        return isBluetoothAdminPermissionGranted(context) && isBluetoothLePermissionGranted(context) && isBluetoothPermissionGranted(context);
    }

    /**
     * This method checks if the android.permission.ACCESS_COARSE_LOCATION
     * permission has been granted by the user.
     * @param context The Android environment Context.
     * @return Whether the permission was granted.
     */
    public static boolean isLocationPermissionGranted(final Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }
}
