package com.example.updateservice.dfu.fragment;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.updateservice.dfu.DfuService;
import com.example.updateservice.R;

public class UploadCancelFragment extends DialogFragment {

    private static final String TAG = "UploadCancelFragment";

    private CancelFragmentListener listener;

    public interface CancelFragmentListener {
        void onCancelUpload();
    }

    public static UploadCancelFragment getInstance() {
        return new UploadCancelFragment();
    }

    @Override
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);

        try {
            listener = (CancelFragmentListener) context;
        } catch (final ClassCastException e) {
            Log.d(TAG, "The parent Activity must implement CancelFragmentListener interface");
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        return new AlertDialog.Builder(requireContext()).setTitle(R.string.dfu_confirmation_dialog_title).setMessage(R.string.dfu_upload_dialog_cancel_message).setCancelable(false)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        final LocalBroadcastManager manager = LocalBroadcastManager.getInstance(UploadCancelFragment.this.requireContext());
                        final Intent pauseAction = new Intent(DfuService.BROADCAST_ACTION);
                        pauseAction.putExtra(DfuService.EXTRA_ACTION, DfuService.ACTION_ABORT);
                        manager.sendBroadcast(pauseAction);

                        listener.onCancelUpload();
                    }
                }).setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                }).create();
    }

    @Override
    public void onCancel(@NonNull final DialogInterface dialog) {
        final LocalBroadcastManager manager = LocalBroadcastManager.getInstance(requireContext());
        final Intent pauseAction = new Intent(DfuService.BROADCAST_ACTION);
        pauseAction.putExtra(DfuService.EXTRA_ACTION, DfuService.ACTION_RESUME);
        manager.sendBroadcast(pauseAction);
    }
}
