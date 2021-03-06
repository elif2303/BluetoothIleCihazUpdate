package com.example.updateservice.profile.multiconnect;


import android.bluetooth.BluetoothDevice;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

public interface IDeviceLogger {
    /**
     * Logs the given message with given log level into the device's log session.
     * @param device the target device
     * @param level the log level
     * @param message the message to be logged
     */
    void log(@NonNull final BluetoothDevice device, final int level, final String message);

    /**
     * Logs the given message with given log level into the device's log session.
     * @param device the target device
     * @param level the log level
     * @param messageRes string resource id
     * @param params additional (optional) parameters used to fill the message
     */
    void log(@NonNull final BluetoothDevice device, final int level, @StringRes final int messageRes, final Object... params);
}
