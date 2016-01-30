package com.xiaomi.upnp.examples.projection.source;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.xiaomi.upnp.examples.AppConstants;
import com.xiaomi.upnp.examples.R;
import com.xiaomi.upnp.examples.projection.source.device.sink.control.SinkDevice;
import com.xiaomi.upnp.examples.projection.source.device.source.control.SourceDevice;
import com.xiaomi.upnp.examples.projection.source.device.source.host.SourceDeviceImpl;
import com.xiaomi.upnp.examples.projection.source.feature.projection.ProjectionJob;
import com.xiaomi.upnp.examples.projection.source.feature.projection.ProjectionWorker;

import java.util.ArrayList;
import java.util.List;

import upnp.typedef.device.DiscoveryType;
import upnp.typedef.device.urn.DeviceType;
import upnp.typedef.device.urn.Urn;
import upnp.typedef.deviceclass.DeviceClass;
import upnp.typedef.error.UpnpError;
import upnp.typedef.exception.UpnpException;
import upnp.worker.WorkExecutor;
import upnp.worker.WorkExecutorFactory;
import upnps.manager.UpnpManager;
import upnps.manager.ctrlpoint.device.AbstractDevice;
import upnps.manager.handler.MyCompletionHandler;
import upnps.manager.handler.MyScanListener;

public class ProjectionSourceActivity extends Activity {

    private static final String TAG = "Source";
    private LocalBroadcastManager broadcastManager;
    private DeviceListAdapter deviceAdapter;
    private SourceDevice source;
    private Handler handler;
    private Context context;
    private WorkExecutor executor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.media_projection_source_main);

        handler = new Handler();

        deviceAdapter = new DeviceListAdapter(this, R.layout.media_projection_source_item, new ArrayList<AbstractDevice>());
        ListView deviceListView = (ListView) findViewById(R.id.deviceList);
        deviceListView.setAdapter(deviceAdapter);
        deviceListView.setOnItemClickListener(onItemClickListener);

//        broadcastManager = LocalBroadcastManager.getInstance(this);

        context = this;

        try {
            SourceDeviceImpl.getInstance().initialize(this, handler);
            SourceDeviceImpl.getInstance().start();
        } catch (UpnpException e) {
            e.printStackTrace();
        }

        executor = WorkExecutorFactory.create(WorkExecutor.Type.SERIAL);
        executor.addWorker(ProjectionJob.class, new ProjectionWorker(context));
        executor.start();

        startDiscovery();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");

        super.onResume();
//        registerReceiver();

    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();

//        broadcastManager.unregisterReceiver(receiver);
        stopDiscovery();
    }

    private AdapterView.OnItemClickListener onItemClickListener = new AdapterView.OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            AbstractDevice device = deviceAdapter.getItem(position);
            if (device == null) {
                Log.d(TAG, "Can't found device info, display position is " + position);
                return;
            }

            if (device instanceof SinkDevice)  {
                SinkDevice d = (SinkDevice) device;
                executor.put(new ProjectionJob(source, d, handler));
            }

            if (device instanceof SourceDevice)  {
                executor.put(new ProjectionJob(source, null, handler));
            }
        }
    };

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(AppConstants.UPNP_INIT_FAILED);
        filter.addAction(AppConstants.UPNP_INIT_SUCCEED);
//        broadcastManager.registerReceiver(receiver, filter);
    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.e(TAG, "onReceive");

            switch (intent.getAction()) {
                case AppConstants.UPNP_INIT_SUCCEED:
                    Log.d(TAG, "upnp init succeed");

                    break;

                case AppConstants.UPNP_INIT_FAILED:
                    Log.d(TAG, "upnp init failed");
                    break;
            }
        }
    };

    public void startDiscovery() {
        try {
            UpnpManager.getControlPoint().start();
        } catch (UpnpException e) {
            e.printStackTrace();
        }

        UpnpManager.getClassProvider().addDeviceClass(new DeviceClass(new DeviceType("SinkDevice", "1"), SinkDevice.class));
        UpnpManager.getClassProvider().addDeviceClass(new DeviceClass(new DeviceType("SourceDevice", "1"), SourceDevice.class));

        List<Urn> types = new ArrayList<>();
        types.add(new DeviceType("SinkDevice", "1"));
        types.add(new DeviceType("SourceDevice", "1"));

        try {
            UpnpManager.getControlPoint().startScan(types,
                    new MyCompletionHandler() {
                        @Override
                        public void onSucceed() {
                            Log.d(TAG, "startDiscovery OK!");
                        }

                        @Override
                        public void onFailed(UpnpError error) {
                            Log.d(TAG, "startDiscovery failed: " + error);
                        }
                    },
                    new MyScanListener() {
                        @Override
                        public void onDeviceFound(final AbstractDevice device) {
                            Log.e(TAG, "onDeviceFound: " + device.device.getDeviceType().getName() + " discoverType: " + device.device.getDeviceType());
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (device instanceof SourceDevice) {
                                        SourceDevice d = (SourceDevice) device;
                                        if (d.device.getDiscoveryTypes().contains(DiscoveryType.LOCAL)) {
                                            Log.e(TAG, "local source found!");
                                            source = d;
                                        }
                                    }

                                    deviceAdapter.add(device);
                                }
                            });
                        }

                        @Override
                        public void onDeviceLost(final AbstractDevice device) {
                            Log.e(TAG, "onDeviceLost");
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (device instanceof SourceDevice) {
                                        SourceDevice d = (SourceDevice) device;
                                        if (d.device.getDiscoveryTypes().contains(DiscoveryType.LOCAL)) {
                                            Log.e(TAG, "local source lost!");
                                            source = null;
                                        }
                                    }

                                    deviceAdapter.remove(device);
                                }
                            });
                        }
                    });
        } catch (UpnpException e) {
            e.printStackTrace();
        }
    }

    public void stopDiscovery() {
        SourceDeviceImpl.getInstance().stop();

        try {
            UpnpManager.getControlPoint().stopScan(
                    new MyCompletionHandler() {
                        @Override
                        public void onSucceed() {
                            Log.d(TAG, "stopDiscovery onSucceed");
                        }

                        @Override
                        public void onFailed(UpnpError error) {
                            Log.d(TAG, "stopDiscovery onFailed");
                        }
                    });
        } catch (UpnpException e) {
            e.printStackTrace();
        }

        deviceAdapter.clear();
        source = null;
    }
}
