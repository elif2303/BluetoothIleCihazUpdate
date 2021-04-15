package com.example.updateservice.dfu.fragment;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import com.example.updateservice.R;

public class ZipInfoFragment extends DialogFragment {

    @Override
    @NonNull
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.fragment_zip_info, null);
        return new AlertDialog.Builder(requireContext())
                .setView(view)
                .setTitle(R.string.dfu_file_info)
                .setPositiveButton(R.string.ok, null)
                .create();
    }
}
