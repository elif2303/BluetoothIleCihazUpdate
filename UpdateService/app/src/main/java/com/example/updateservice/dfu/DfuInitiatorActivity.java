package com.example.updateservice.dfu;


import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.updateservice.R;
import com.example.updateservice.scanner.ScannerFragment;

public class DfuInitiatorActivity extends AppCompatActivity implements ScannerFragment.OnDeviceSelectedListener {

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // The activity must be started with a path to the HEX file
        final Intent intent = getIntent();
        if (!intent.hasExtra(DfuService.EXTRA_FILE_PATH))
            finish();

        if (savedInstanceState == null) {
            final ScannerFragment fragment = ScannerFragment.getInstance(null); // Device that is advertising directly does not have the GENERAL_DISCOVERABLE nor LIMITED_DISCOVERABLE flag set.
            fragment.show(getSupportFragmentManager(), null);
        }
    }

    @Override
    public void onDeviceSelected(@NonNull final BluetoothDevice device, final String name) {
        final Intent intent = getIntent();
        final String overwrittenName = intent.getStringExtra(DfuService.EXTRA_DEVICE_NAME);
        final String path = intent.getStringExtra(DfuService.EXTRA_FILE_PATH);
        final String initPath = intent.getStringExtra(DfuService.EXTRA_INIT_FILE_PATH);
        final String address = device.getAddress();
        final String finalName = overwrittenName == null ? (name != null ? name : getString(R.string.not_available)) : overwrittenName;
        final int type = intent.getIntExtra(DfuService.EXTRA_FILE_TYPE, DfuService.TYPE_AUTO);
        final boolean keepBond = intent.getBooleanExtra(DfuService.EXTRA_KEEP_BOND, false);

        // Start DFU service with data provided in the intent
        final Intent service = new Intent(this, DfuService.class);
        service.putExtra(DfuService.EXTRA_DEVICE_ADDRESS, address);
        service.putExtra(DfuService.EXTRA_DEVICE_NAME, finalName);
        service.putExtra(DfuService.EXTRA_FILE_TYPE, type);
        service.putExtra(DfuService.EXTRA_FILE_PATH, path);
        if (intent.hasExtra(DfuService.EXTRA_INIT_FILE_PATH))
            service.putExtra(DfuService.EXTRA_INIT_FILE_PATH, initPath);
        service.putExtra(DfuService.EXTRA_KEEP_BOND, keepBond);
        service.putExtra(DfuService.EXTRA_UNSAFE_EXPERIMENTAL_BUTTONLESS_DFU, true);
        startService(service);
        finish();
    }

    @Override
    public void onDialogCanceled() {
        finish();
    }
}
