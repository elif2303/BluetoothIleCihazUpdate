package com.example.updateservice.scanner;

import android.bluetooth.BluetoothDevice;

import androidx.annotation.NonNull;

import no.nordicsemi.android.support.v18.scanner.ScanResult;

public class ExtendedBluetoothDevice {

    static final int NO_RSSI = -1000;
    public final BluetoothDevice device;
    /** The name is not parsed by some Android devices, f.e. Sony Xperia Z1 with Android 4.3 (C6903). It needs to be parsed manually. */
    public String name;
    public int rssi;
    public boolean isBonded;

    public ExtendedBluetoothDevice(@NonNull final ScanResult scanResult, ExtendedBluetoothDevice device) {
        this.device = scanResult.getDevice();
        this.name = scanResult.getScanRecord() != null ? scanResult.getScanRecord().getDeviceName() : null;
        this.rssi = scanResult.getRssi();
        this.isBonded = false;
    }

    public ExtendedBluetoothDevice(@NonNull final BluetoothDevice device) {
        this.device = device;
        this.name = device.getName();
        this.rssi = NO_RSSI;
        this.isBonded = true;
    }

    public boolean matches(ScanResult scanResult) {
        return device.getAddress().equals(scanResult.getDevice().getAddress());
    }
}
