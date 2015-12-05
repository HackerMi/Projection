package xiaomi.com.projection.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

import upnp.typedef.device.urn.DeviceType;
import upnps.api.manager.UpnpManager;
import upnps.api.manager.ctrlpoint.device.AbstractDevice;
import upnps.api.manager.handler.MyCompletionHandler;
import upnps.api.manager.handler.MyDiscoveryListener;
import xiaomi.com.projection.R;
import xiaomi.com.projection.device.control.MediaDevice;

public class DiscoveryActivity extends Activity {

    public static final String SELF_DEVICE_ID = "deviceId";

    private static final String TAG = "DiscoveryActivity";
    private String deviceId;
    private ProjectionAdapter adapter;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_discovery);
//        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
//        setSupportActionBar(toolbar);

        deviceId = this.getIntent().getStringExtra(SELF_DEVICE_ID);
        Log.d(TAG, "deviceId: " + deviceId);

        handler = new Handler();

        adapter = new ProjectionAdapter(this, R.layout.item_device, new ArrayList<MediaDevice>());
        ListView deviceListView = (ListView) findViewById(R.id.listView);
        deviceListView.setAdapter(adapter);
        deviceListView.setOnItemClickListener(onItemClickListener);

        Log.d(TAG, "onCreate");

        start();
    }

//    @Override
//    public void supportInvalidateOptionsMenu() {
//        super.supportInvalidateOptionsMenu();
//    }

    @Override
    protected void onPause() {
        super.onPause();

        Log.d(TAG, "onPause");

//        stop();
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.d(TAG, "onResume");

//        start();
    }

    private AdapterView.OnItemClickListener onItemClickListener = new AdapterView.OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            MediaDevice device = adapter.getItem(position);
            if (device != null) {
                Intent intent = new Intent(view.getContext(), ControlActivity.class);
                intent.putExtra(ControlActivity.EXTRA_DEVICE, device);

                startActivity(intent);
            }
        }
    };

    private void start() {
        List<DeviceType> types = new ArrayList<DeviceType>();
        types.add(new DeviceType("MediaDevice", "1"));

        UpnpManager.getUpnp().startDiscovery(types,
                new MyCompletionHandler() {
                    @Override
                    public void onSucceed() {
                        Log.d(TAG, "startDiscovery OK");
                    }

                    @Override
                    public void onFailed(int errCode, String description) {
                        Log.d(TAG, String.format("startDiscovery failed: %d", errCode, description));
                    }
                },
                new MyDiscoveryListener() {

                    @Override
                    public void onDeviceFound(final AbstractDevice device) {
                        Log.d(TAG, "onDeviceFound");

                        if (device.device.getDeviceId().equals(deviceId)) {
                            Log.d(TAG, "这是自己哦，先忽略。");
                            return;
                        }

                        if (device instanceof MediaDevice) {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    MediaDevice projection = (MediaDevice)device;
                                    adapter.add(projection);
                                }
                            });
                        }
                    }

                    @Override
                    public void onDeviceLost(final AbstractDevice device) {
                        Log.d(TAG, "onDeviceFound");

                        if (device.device.getDeviceId().equals(deviceId)) {
                            Log.d(TAG, "这是自己哦，先忽略。");
                            return;
                        }

                        if (device instanceof MediaDevice) {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    MediaDevice projection = (MediaDevice)device;
                                    adapter.remove(projection);
                                }
                            });
                        }
                    }
                });
    }

    private void stop() {
        UpnpManager.getUpnp().stopDiscovery(new MyCompletionHandler() {
            @Override
            public void onSucceed() {
                Log.d(TAG, "stopDiscovery onSucceed");
            }

            @Override
            public void onFailed(int errCode, String description) {
                Log.d(TAG, "stopDiscovery onFailed");
            }
        });

        adapter.clear();
    }
}
