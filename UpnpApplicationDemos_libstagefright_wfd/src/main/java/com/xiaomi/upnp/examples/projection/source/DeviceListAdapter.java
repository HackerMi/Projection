package com.xiaomi.upnp.examples.projection.source;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.xiaomi.upnp.examples.R;

import java.util.List;

import upnps.manager.ctrlpoint.device.AbstractDevice;


public class DeviceListAdapter extends ArrayAdapter<AbstractDevice> {

    private static final String TAG = "DeviceListAdapter";
    private List<AbstractDevice> devices;
    private int resourceId;
    private LayoutInflater inflater;

    public DeviceListAdapter(Context context, int resource, List<AbstractDevice> objects) {
        super(context, resource, objects);

        this.resourceId = resource;
        this.devices = objects;
        this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        AbstractDevice device = getItem(position);

        if (convertView == null) {
            convertView = inflater.inflate(resourceId, null);
        }

        TextView name = (TextView) convertView.findViewById(R.id.device_name);
        name.setText(device.device.getFriendlyName());

        return convertView;
    }
}