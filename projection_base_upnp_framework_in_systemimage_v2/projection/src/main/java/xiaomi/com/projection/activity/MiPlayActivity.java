package xiaomi.com.projection.activity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.RemoteDisplay;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.Formatter;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

import upnp.typedef.device.urn.DeviceType;
import upnp.typedef.device.DiscoveryType;
import upnp.typedef.exception.UpnpException;
import upnps.api.manager.UpnpManager;
import upnps.api.manager.ctrlpoint.device.AbstractDevice;
import upnps.api.manager.handler.MyCompletionHandler;
import upnps.api.manager.handler.MyDiscoveryListener;
import upnps.api.manager.host.config.DeviceConfig;
import xiaomi.com.projection.R;
import xiaomi.com.projection.application.AppConstants;
import xiaomi.com.projection.application.UpnpApplication;
import xiaomi.com.projection.device.control.MediaDevice;
import xiaomi.com.projection.device.control.StreamTransport;

public class MiPlayActivity extends Activity {

    public static final String SELF_DEVICE_ID = "deviceId";

    private static final String TAG = "MiPlayActivity";
    private String selfDeviceId;
    private ProjectionAdapter adapter;
    private Handler handler;
    private xiaomi.com.projection.device.host.MediaDevice hostDevice;
    private Handler remoteHandler;
    private String deviceIp;
    private static final int DEFAULT_CONTROL_PORT = 7236;
    private RemoteDisplay mRemoteDisplay;
    private DisplayManager mDisplayManager;
    private MediaDevice curProjectionDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_discovery);

        handler = new Handler();

        adapter = new ProjectionAdapter(this, R.layout.item_device, new ArrayList<MediaDevice>());
        ListView deviceListView = (ListView) findViewById(R.id.listView);
        deviceListView.setAdapter(adapter);
        deviceListView.setOnItemClickListener(onItemClickListener);

        Log.d(TAG, "onCreate");

        registerReceiver();

        if (UpnpApplication.getAppContext().isInitialized()) {
            Log.d(TAG, "upnp has been initialized");
            startUpnp();
        }
        
        mDisplayManager = (DisplayManager)this.getSystemService(Context.DISPLAY_SERVICE);
        remoteHandler =  new Handler() {};
        deviceIp = getDeviceAddress();
        startRtspServer(deviceIp, DEFAULT_CONTROL_PORT);
    }


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
            if (device == null) {
                Log.d(TAG, "device is null");
                return;
            }
            if (!device.device.getDeviceId().equals(selfDeviceId)) {
                curProjectionDevice = device;
                configPeerDevice(device, deviceIp);
                setProjection(device, true);
            } else {
                if (curProjectionDevice != null) {
                    setProjection(curProjectionDevice, false);
                }
            }
        }
    };

    private void startDiscovery() {
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

                        if (device.device.getDeviceId().equals(selfDeviceId)) {
                            Log.d(TAG, "这是自己哦，先忽略。");
//                            return;
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

                        if (device.device.getDeviceId().equals(selfDeviceId)) {
                            Log.d(TAG, "这是自己哦，先忽略。");
//                            return;
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

    private void stopDiscovery() {
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
    
    private void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(AppConstants.UPNP_INIT_FAILED);
        filter.addAction(AppConstants.UPNP_INIT_SUCCEED);
        registerReceiver(receiver, filter);
    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.e(TAG, "onReceive");

            switch (intent.getAction()) {
                case AppConstants.UPNP_INIT_SUCCEED:
                    Log.d(TAG, "upnp init succeed");
                    startUpnp();
                    break;

                case AppConstants.UPNP_INIT_FAILED:
                    Log.d(TAG, "upnp init failed");
                    break;
            }
        }
    };

    private void startUpnp() {
        Log.d(TAG, "discover");

        try {
            UpnpManager.getUpnp().start();
            UpnpManager.getFileServer().start();
        } catch (UpnpException e) {
            e.printStackTrace();
        }

        try {
            initMediaDevice();

            /**
             * 4 - start
             */
            hostDevice.start(new MyCompletionHandler() {
                @Override
                public void onSucceed() {
                    Log.d(TAG, "start onSucceed");
                    startDiscovery();
                }

                @Override
                public void onFailed(int errCode, String description) {
                    Log.d(TAG, "start onFailed: " + errCode + " reason: " + description);
                }
            });
        } catch (UpnpException e) {
            e.printStackTrace();
        }
    }

    private void initMediaDevice() throws UpnpException {
        /**
         * 1 - init device configration
         */
        DeviceConfig config = new DeviceConfig();
        config.discoveryType(DiscoveryType.UPNP);
        config.deviceType(xiaomi.com.projection.device.host.MediaDevice.DEVICE_TYPE);
        config.deviceName("Phone");
        config.modelNumber("1");
        config.modelName("projection");
        config.modelDescription("Projection for demo");
        config.modelUrl("http://www.mi.com/projection");
        config.manufacturer("Xiaomi");
        config.manufacturerUrl("http://www.xiaomi.com");
        config.service(xiaomi.com.projection.device.host.MediaDevice.SERVICE_StreamTransport, "services/StreamTransport.xml");
        config.service(xiaomi.com.projection.device.host.MediaDevice.SERVICE_MediaRenderingControl, "services/MediaRenderingControl.xml");

        /**
         * 2 - create device
         */
        hostDevice = new xiaomi.com.projection.device.host.MediaDevice(this, config);
        Log.e(TAG, "deviceId: " + hostDevice.getDeviceId());

        selfDeviceId = hostDevice.getDeviceId();
    }
    
    private String getDeviceAddress() {
        WifiManager wifiManager = (WifiManager)getSystemService(Context.WIFI_SERVICE);
        if (!wifiManager.isWifiEnabled()) {
            Log.d(TAG, "wifi not enable");
            return null;
        }
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ipAddress = wifiInfo.getIpAddress();
        return Formatter.formatIpAddress(ipAddress);
    }
    

    private void configPeerDevice(MediaDevice device, String deviceIp) {
        if (deviceIp == null) {
            return;
        }

        String uri = String.format("wfd://%s:%d", deviceIp, DEFAULT_CONTROL_PORT);
        Log.d(TAG, "configPeerDevice, SetAVTransportURI, uri: " + uri);
        device.getStreamTransport().SetAVTransportURI((long)0, uri, " ", new StreamTransport.SetAVTransportURI_CompletedHandler() {
            @Override
            public void onSucceed() {
                Log.d(TAG, "set URI ok!");
            }

            @Override
            public void onFailed(int errCode, String description) {
                Log.d(TAG, "set URI fail!");
            }
        });
    }

    private void setProjection(MediaDevice device, final boolean on) {
        Log.d(TAG, "setProjection: " + on);
        if (on) {
            device.getStreamTransport().Play((long) 0, StreamTransport.TransportPlaySpeed.V_1, new StreamTransport.Play_CompletedHandler() {
                @Override
                public void onSucceed() {
                    Log.d(TAG, "play OK!");
                }

                @Override
                public void onFailed(int errCode, String description) {
                    Log.d(TAG, "play failed!");
                }
            });
        } else {
            device.getStreamTransport().Stop((long) 0, new StreamTransport.Stop_CompletedHandler() {
                @Override
                public void onSucceed() {
                    Log.d(TAG, "stop OK!");
                }

                @Override
                public void onFailed(int errCode, String description) {
                    Log.d(TAG, "stop failed!");
                }
            });
        }
    }

    private void startRtspServer(String ip, int port) {
        String iface = ip + ":" + port;

        //RemoteDisplay is hide class, the project need to complie with system image 
        RemoteDisplay.Listener listener = new RemoteDisplay.Listener() {
            @Override
            public void onDisplayConnected(Surface surface,
                                           int width, int height, int flags, int session) {
                Log.i(TAG, "Connected RTSP connection with Wifi display: ");
                VirtualDisplay display = mDisplayManager.createVirtualDisplay(
                        "aa", width, height, getResources().getConfiguration().densityDpi, surface, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR);
                if (display != null) {
                    Log.i(TAG, "virtual display create success!");
                }
            }

            @Override
            public void onDisplayDisconnected() {
                Log.i(TAG, "Closed RTSP connection with Wifi display: ");
            }

            @Override
            public void onDisplayError(int error) {
                Log.i(TAG, "Lost RTSP connection with Wifi display due to error ");
                stopServer();
            }
        };

        mRemoteDisplay = RemoteDisplay.listen(iface, listener, remoteHandler);
    }

    private void stopServer() {
        mRemoteDisplay.dispose();
    }
}
