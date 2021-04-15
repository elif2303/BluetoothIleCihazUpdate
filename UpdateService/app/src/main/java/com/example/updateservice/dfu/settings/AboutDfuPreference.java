package com.example.updateservice.dfu.settings;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.AttributeSet;
import android.widget.Toast;

import androidx.preference.Preference;

import com.example.updateservice.R;

public class AboutDfuPreference extends Preference {

    public AboutDfuPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AboutDfuPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onClick() {
        final Context context = getContext();
        final Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://infocenter.nordicsemi.com/topic/sdk_nrf5_v16.0.0/examples_bootloader.html?cp=7_1_4_4"));
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // is browser installed?
        if (intent.resolveActivity(context.getPackageManager()) != null)
            context.startActivity(intent);
        else {
            Toast.makeText(getContext(), R.string.no_application, Toast.LENGTH_LONG).show();
        }
    }
}
