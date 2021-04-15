package com.example.updateservice.dfu.adapter;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static com.example.updateservice.R.*;


public class FileBrowserAppsAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final Resources resources;

    public FileBrowserAppsAdapter(final Context context) {
        inflater = LayoutInflater.from(context);
        resources = context.getResources();
    }

    @Override
    public int getCount() {
        return resources.getStringArray(array.dfu_app_file_browser).length;
    }

    @Override
    public Object getItem(final int position) {
        return resources.getStringArray(array.dfu_app_file_browser_action)[position];
    }

    @Override
    public long getItemId(final int position) {
        return position;
    }

    @Override
    public View getView(final int position, @Nullable final View convertView, @NonNull final ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = inflater.inflate(layout.app_file_browser_item, parent, false);
        }

        final TextView item = (TextView) view;
        item.setText(resources.getStringArray(array.dfu_app_file_browser)[position]);
        item.getCompoundDrawablesRelative()[0].setLevel(position);
        return view;
    }
}
