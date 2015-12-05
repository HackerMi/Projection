package xiaomi.com.projection.activity;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import upnp.typedef.device.Device;
import upnp.typedef.device.Icon;
import xiaomi.com.projection.R;
import xiaomi.com.projection.device.control.MediaDevice;

public class ProjectionAdapter extends ArrayAdapter<MediaDevice> {

    private static final String TAG = "DeviceAdapter";
    private List<MediaDevice> devices;
    private int resourceId;
    private LayoutInflater inflater;

    public ProjectionAdapter(Context context, int resource, List<MediaDevice> objects) {
        super(context, resource, objects);

        this.resourceId = resource;
        this.devices = objects;
        this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        MediaDevice device = getItem(position);

        if (convertView == null) {
            convertView = inflater.inflate(resourceId, null);
        }

        ImageView icon = (ImageView) convertView.findViewById(R.id.deviceIcon);
        String url = getIconUrl(device.device);
        if (url != null) {
//            Log.d(TAG, "Icon url: " + url);
        }

        TextView name = (TextView) convertView.findViewById(R.id.device_name);
        name.setText(device.device.getFriendlyName());

        return convertView;
    }

    private String getIconUrl(Device device) {
        String url = null;

        for (Icon icon: device.getIcons()) {
            url = icon.getUrl();
            break;
        }

        return url;
    }
}