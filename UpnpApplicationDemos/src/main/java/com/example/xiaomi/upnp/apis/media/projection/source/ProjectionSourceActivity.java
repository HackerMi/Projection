package com.example.xiaomi.upnp.apis.media.projection.source;

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

import com.example.xiaomi.upnp.apis.AppConstants;
import com.example.xiaomi.upnp.apis.R;
import com.example.xiaomi.upnp.apis.media.projection.source.device.sink.control.SessionManager;
import com.example.xiaomi.upnp.apis.media.projection.source.device.sink.control.SinkDevice;
import com.example.xiaomi.upnp.apis.media.projection.source.device.sink.control.StreamTransport;
import com.example.xiaomi.upnp.apis.media.projection.source.device.source.control.MediaProjection;
import com.example.xiaomi.upnp.apis.media.projection.source.device.source.control.SourceDevice;
import com.example.xiaomi.upnp.apis.media.projection.source.device.source.host.SourceDeviceImpl;

import java.util.ArrayList;
import java.util.List;

import upnp.typedef.device.DiscoveryType;
import upnp.typedef.device.urn.DeviceType;
import upnp.typedef.device.urn.Urn;
import upnp.typedef.deviceclass.DeviceClass;
import upnp.typedef.exception.UpnpException;
import upnp.typedef.session.ChannelType;
import upnp.typedef.session.MediaFormat;
import upnp.typedef.session.ProtocolType;
import upnp.typedef.session.ServiceInstanceIDs;
import upnp.typedef.session.SessionCapabilities;
import upnp.typedef.session.SessionCapability;
import upnps.api.manager.UpnpManager;
import upnps.api.manager.ctrlpoint.device.AbstractDevice;
import upnps.api.manager.handler.MyCompletionHandler;
import upnps.api.manager.handler.MyScanListener;

public class ProjectionSourceActivity extends Activity {

    private static final String TAG = "Source";
    private LocalBroadcastManager broadcastManager;
    private DeviceListAdapter deviceAdapter;
    private SinkDevice curSinkDevice;
    private SourceDevice localSource;
    private String sourceSessionId;
    private String sinkSessionId;
    private SessionCapabilities capabilities = new SessionCapabilities();
    private Handler handler;

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

        try {
            SourceDeviceImpl.getInstance().initialize(this);
            setCapabilities();
        } catch (UpnpException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");

        super.onResume();
//        registerReceiver();
        startDiscovery();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();

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
                if (localSource != null) {
                    processProjection(localSource, d, true);
                    curSinkDevice = d;
                }
            }

            if (device instanceof SourceDevice)  {
                SourceDevice d = (SourceDevice) device;
                if (curSinkDevice != null && d.device.getDiscoveryTypes().contains(DiscoveryType.LOCAL)) {
                    processProjection(d, curSinkDevice, false);
                    curSinkDevice = null;
                }
            }
        }
    };

    private void setCapabilities() {
        capabilities.add(new SessionCapability(ChannelType.P2P, ProtocolType.HTTP, MediaFormat.VIDEO_ALL, null));
        capabilities.add(new SessionCapability(ChannelType.P2P, ProtocolType.RTSP, MediaFormat.VIDEO_ALL, null));
        capabilities.add(new SessionCapability(ChannelType.P2P, ProtocolType.WFD, MediaFormat.VIDEO_ALL, null));
    }

    private void processProjection(SourceDevice source, SinkDevice sink, boolean operation) {
        if (operation) {
            sink.getSessionManager().PrepareForSession(capabilities.toString(),
                    "",
                    SessionManager.A_ARG_TYPE_Direction.V_Input,
                    new SessionManager.PrepareForSession_CompletedHandler() {

                        @Override
                        public void onSucceed(String theSessionID, String theAddress, String theServiceInstanceIDs) {
                            sinkSessionId = theSessionID;
                            Log.e(TAG, "AVPlayer PrepareForSession onSucceed");
                            Log.d(TAG, "sessionId: " + theSessionID);
                            Log.d(TAG, "address: " + theAddress);
                            Log.d(TAG, "theServiceInstanceIDs: " + theServiceInstanceIDs);

                            prepareSourceSession(sinkSessionId);
                        }

                        @Override
                        public void onFailed(int errCode, String description) {
                            Log.d(TAG, "PrepareForSession onFailed: " + errCode + " " + description);
                        }
                    }
            );

        } else {

        }
    }

    private void prepareSourceSession(String sinkSessionId) {
        localSource.getSessionManager().PrepareForSession(capabilities.toString(),
                sinkSessionId,
                com.example.xiaomi.upnp.apis.media.projection.source.device.source.control.SessionManager.A_ARG_TYPE_Direction.V_Output,
                new com.example.xiaomi.upnp.apis.media.projection.source.device.source.control.SessionManager.PrepareForSession_CompletedHandler() {

                    @Override
                    public void onSucceed(String theSessionID, String theAddress, String theServiceInstanceIDs) {
                        Log.e(TAG, "AVServer PrepareForSession onSucceed");
                        sourceSessionId = theSessionID;
                        Log.d(TAG, "sessionId: " + theSessionID);
                        Log.d(TAG, "address: " + theAddress);
                        Log.d(TAG, "theServiceInstanceIDs: " + theServiceInstanceIDs);

                        ServiceInstanceIDs instanceIDs = new ServiceInstanceIDs();
                        if (instanceIDs.parse(theServiceInstanceIDs)) {
                            int ids = instanceIDs.getInstanceId(com.example.xiaomi.upnp.apis.media.projection.source.device.source.host.SourceDevice.SERVICE_MediaProjection);
                        }

                        localSource.getMediaProjection().Start((long) 0, new MediaProjection.Start_CompletedHandler() {

                            @Override
                            public void onSucceed() {
                                localSource.getMediaProjection().GetTransportURI((long) 0, new MediaProjection.GetTransportURI_CompletedHandler() {
                                    @Override
                                    public void onSucceed(String theCurrentURI) {
                                        curSinkDevice.getStreamTransport().SetAVTransportURI((long) 0, theCurrentURI, null, new StreamTransport.SetAVTransportURI_CompletedHandler() {
                                            @Override
                                            public void onSucceed() {
                                                curSinkDevice.getStreamTransport().Play((long) 0, StreamTransport.TransportPlaySpeed.V_1, new StreamTransport.Play_CompletedHandler() {
                                                    @Override
                                                    public void onSucceed() {

                                                    }

                                                    @Override
                                                    public void onFailed(int errCode, String description) {

                                                    }
                                                });
                                            }

                                            @Override
                                            public void onFailed(int errCode, String description) {

                                            }
                                        });
                                    }

                                    @Override
                                    public void onFailed(int errCode, String description) {

                                    }
                                });
                            }

                            @Override
                            public void onFailed(int errCode, String description) {

                            }
                        });
                    }

                    @Override
                    public void onFailed(int errCode, String description) {
                        Log.d(TAG, "PrepareForSession onFailed: " + errCode + " " + description);
                    }
                });
    }

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
            UpnpManager.getUpnp().start();
            SourceDeviceImpl.getInstance().start();
        } catch (UpnpException e) {
            e.printStackTrace();
        }

        UpnpManager.getClassProvider().addDeviceClass(new DeviceClass(new DeviceType("SinkDevice", "1"), SinkDevice.class));
        UpnpManager.getClassProvider().addDeviceClass(new DeviceClass(new DeviceType("SourceDevice", "1"), SourceDevice.class));

        List<Urn> types = new ArrayList<>();
        types.add(new DeviceType("SinkDevice", "1"));
        types.add(new DeviceType("SourceDevice", "1"));

        UpnpManager.getUpnp().startScan(types,
                new MyCompletionHandler() {
                    @Override
                    public void onSucceed() {
                        Log.d(TAG, "startDiscovery OK!");
                    }

                    @Override
                    public void onFailed(int errCode, String description) {
                        Log.d(TAG, String.format("startDiscovery failed: %d %s", errCode, description));
                    }
                },
                new MyScanListener() {
                    @Override
                    public void onDeviceFound(final AbstractDevice device) {
                        Log.e(TAG, "onDeviceFound: " + device.device.getDeviceType().getName() + " discoverType: " + device.device.getDeviceType());
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (device instanceof SourceDevice)  {
                                    SourceDevice d = (SourceDevice) device;
                                    if (d.device.getDiscoveryTypes().contains(DiscoveryType.LOCAL)) {
                                        localSource = d;
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
                                if (device instanceof SourceDevice)  {
                                    SourceDevice d = (SourceDevice) device;
                                    if (d.device.getDiscoveryTypes().contains(DiscoveryType.LOCAL)) {
                                        localSource = null;
                                    }
                                }

                                deviceAdapter.remove(device);
                            }
                        });
                    }
                });
    }

    public void stopDiscovery() {
        SourceDeviceImpl.getInstance().stop();

        UpnpManager.getUpnp().stopScan(
                new MyCompletionHandler() {
                    @Override
                    public void onSucceed() {
                        Log.d(TAG, "stopDiscovery onSucceed");
                    }

                    @Override
                    public void onFailed(int errCode, String description) {
                        Log.d(TAG, "stopDiscovery onFailed");
                    }
                });

        deviceAdapter.clear();
        localSource = null;
    }
}
