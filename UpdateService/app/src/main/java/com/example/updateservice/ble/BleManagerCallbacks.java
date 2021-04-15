package com.example.updateservice.ble;


import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;

public interface BleManagerCallbacks {

    /**
     * Called when the Android device started connecting to given device.
     * The {@link #onDeviceConnected(BluetoothDevice)} will be called when the device is connected,
     * or {@link #onError(BluetoothDevice, String, int)} in case of error.
     * @param device the device that got connected
     */
    void onDeviceConnecting(final BluetoothDevice device);

    /**
     * Called when the device has been connected. This does not mean that the application may start communication.
     * A service discovery will be handled automatically after this call. Service discovery
     * may ends up with calling {@link #onDeviceReady(BluetoothDevice)}
     * or {@link #onDeviceNotSupported(BluetoothDevice)} if required services have not been found.
     * @param device target device
     */
    void onDeviceConnected(final BluetoothDevice device);

    /**
     * Method called when all initialization requests has been completed.
     * @param device target device
     */
    void onDeviceReady(final BluetoothDevice device);

    /**
     * This method should return true if Battery Level notifications should be enabled on the target device.
     * If there is no Battery Service, or the Battery Level characteristic does not have NOTIFY property,
     * this method will not be called for this device.
     * <p>This method may return true only if an activity is bound to the service (to display the information
     * to the user), always (e.g. if critical battery level is reported using notifications) or never, if
     * such information is not important or the manager wants to control Battery Level notifications on its own.</p>
     * @param device target device
     * @return true to enabled battery level notifications after connecting to the device, false otherwise
     */
    boolean shouldEnableBatteryLevelNotifications(final BluetoothDevice device);

    /**
     * Called when user initialized disconnection.
     * @param device target device
     */
    void onDeviceDisconnecting(final BluetoothDevice device);

    /**
     * Called when the device has disconnected (when the callback returned
     * {@link BluetoothGattCallback#onConnectionStateChange(BluetoothGatt, int, int)} with state DISCONNECTED),
     * but ONLY if the {@link BleManager#shouldAutoConnect()} method returned false for this device when it was connecting.
     * Otherwise the {@link #onLinklossOccurred(BluetoothDevice)} method will be called instead.
     * @param device the device that got disconnected
     */
    void onDeviceDisconnected(final BluetoothDevice device);

    /**
     * This callback is invoked when the Ble Manager lost connection to a device that has been connected with autoConnect option.
     * Otherwise a {@link #onDeviceDisconnected(BluetoothDevice)} method will be called on such event.
     * @param device target device
     */
    void onLinklossOccurred(final BluetoothDevice device);

    /**
     * Called when an {@link BluetoothGatt#GATT_INSUFFICIENT_AUTHENTICATION} error occurred and the device bond state is NOT_BONDED
     * @param device target device
     */
    void onBondingRequired(final BluetoothDevice device);

    /**
     * Called when the device has been successfully bonded.
     * @param device target device
     */
    void onBonded(final BluetoothDevice device);

    /**
     * Called when a BLE error has occurred
     *
     * @param device target device
     * @param message the error message
     * @param errorCode the error code
     */
    void onError(final BluetoothDevice device, final String message, final int errorCode);

    /**
     * Called when service discovery has finished but the main services were not found on the device.
     * @param device target device
     */
    void onDeviceNotSupported(final BluetoothDevice device);
}
