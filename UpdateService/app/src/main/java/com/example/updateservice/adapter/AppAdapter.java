package com.example.updateservice.adapter;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.updateservice.R;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class AppAdapter extends BaseAdapter {
    private static final String CATEGORY = "no.nordicsemi.android.nrftoolbox.LAUNCHER";
    private static final String NRF_CONNECT_PACKAGE = "no.nordicsemi.android.mcp";

    private final Context context;
    private final PackageManager packageManager;
    private final LayoutInflater inflater;
    private final List<ResolveInfo> applications;

    public AppAdapter(@NonNull final Context context) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);

        // get nRF installed app plugins from package manager
        final PackageManager pm = packageManager = context.getPackageManager();
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(CATEGORY);

        final List<ResolveInfo> appList = applications = pm.queryIntentActivities(intent, 0);
        // TODO remove the following loop after some time, when there will be no more MCP 1.1 at the market.
        for (final ResolveInfo info : appList) {
            if (NRF_CONNECT_PACKAGE.equals(info.activityInfo.packageName)) {
                appList.remove(info);
                break;
            }
        }
        Collections.sort(appList, new ResolveInfo.DisplayNameComparator(pm));
    }

    @Override
    public int getCount() {
        return applications.size();
    }

    @Override
    public Object getItem(final int position) {
        return applications.get(position);
    }

    @Override
    public long getItemId(final int position) {
        return position;
    }

    @Override
    public View getView(final int position, @Nullable final View convertView, @NonNull final ViewGroup parent) {
        View view = convertView;
        if (view == null) {

            final ViewHolder holder = new ViewHolder();
            holder.view = view;
            holder.icon = view.findViewById(R.id.icon);
            view.setTag(holder);
        }

        final ResolveInfo info = applications.get(position);
        final PackageManager pm = packageManager;

        final ViewHolder holder = (ViewHolder) view.getTag();
        holder.icon.setImageDrawable(info.loadIcon(pm));
        holder.label.setText(info.loadLabel(pm).toString().toUpperCase(Locale.US));
        holder.view.setOnClickListener(v -> {
            final Intent intent = new Intent();
            intent.setComponent(new ComponentName(info.activityInfo.packageName, info.activityInfo.name));
            intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            context.startActivity(intent);
        });

        return view;
    }

    private class ViewHolder {
        private View view;
        private ImageView icon;
        private TextView label;
    }
}