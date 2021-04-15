package com.example.updateservice.dfu.settings;


import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;

import android.text.TextUtils;

import androidx.preference.PreferenceFragmentCompat;

import com.example.updateservice.R;

import no.nordicsemi.android.dfu.DfuServiceInitiator;
import no.nordicsemi.android.dfu.DfuSettingsConstants;


public class SettingsFragment extends PreferenceFragmentCompat implements DfuSettingsConstants, SharedPreferences.OnSharedPreferenceChangeListener{

    public static final String SETTINGS_KEEP_BOND = "settings_keep_bond";

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResource(R.xml.settings_dfu);

        // set initial values
        updateNumberOfPacketsSummary();
        updateMBRSize();
    }


    @Override
    public void onResume() {
        super.onResume();

        // attach the preference change listener. It will update the summary below interval preference
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();

        // unregister listener
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
        final SharedPreferences preferences = getPreferenceManager().getSharedPreferences();

        if (SETTINGS_PACKET_RECEIPT_NOTIFICATION_ENABLED.equals(key)) {
            final boolean disabled = !preferences.getBoolean(SETTINGS_PACKET_RECEIPT_NOTIFICATION_ENABLED, true);
            if (disabled && Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                new AlertDialog.Builder(requireContext()).setMessage(R.string.dfu_settings_dfu_number_of_packets_info).setTitle(R.string.dfu_settings_dfu_information)
                        .setPositiveButton(R.string.ok, null).show();
            }
        } else if (SETTINGS_NUMBER_OF_PACKETS.equals(key)) {
            updateNumberOfPacketsSummary();
        } else if (SETTINGS_MBR_SIZE.equals(key)) {
            updateMBRSize();
        } else if (SETTINGS_ASSUME_DFU_NODE.equals(key) && sharedPreferences.getBoolean(key, false)) {
            new AlertDialog.Builder(requireContext()).setMessage(R.string.dfu_settings_dfu_assume_dfu_mode_info).setTitle(R.string.dfu_settings_dfu_information)
                    .setPositiveButton(R.string.ok, null)
                    .show();
        }
    }

    private void updateNumberOfPacketsSummary() {
        final androidx.preference.PreferenceScreen screen = getPreferenceScreen();
        final SharedPreferences preferences = getPreferenceManager().getSharedPreferences();

        String value = preferences.getString(SETTINGS_NUMBER_OF_PACKETS, String.valueOf(SETTINGS_NUMBER_OF_PACKETS_DEFAULT));
        // Security check
        if (TextUtils.isEmpty(value)) {
            value = String.valueOf(SETTINGS_NUMBER_OF_PACKETS_DEFAULT);
            preferences.edit().putString(SETTINGS_NUMBER_OF_PACKETS, value).apply();
        }
        screen.findPreference(SETTINGS_NUMBER_OF_PACKETS).setSummary(value);

        final int valueInt = Integer.parseInt(value);
        if (valueInt > 200 && Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            new AlertDialog.Builder(requireContext()).setMessage(R.string.dfu_settings_dfu_number_of_packets_info).setTitle(R.string.dfu_settings_dfu_information)
                    .setPositiveButton(R.string.ok, null)
                    .show();
        }
    }

    private void updateMBRSize() {
        final androidx.preference.PreferenceScreen screen = getPreferenceScreen();
        final SharedPreferences preferences = getPreferenceManager().getSharedPreferences();

        final String value = preferences.getString(SETTINGS_MBR_SIZE, String.valueOf(DfuServiceInitiator.DEFAULT_MBR_SIZE));
        screen.findPreference(SETTINGS_MBR_SIZE).setSummary(value);
    }
}
