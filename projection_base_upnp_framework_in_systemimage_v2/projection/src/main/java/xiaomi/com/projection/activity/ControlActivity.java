package xiaomi.com.projection.activity;

import android.app.Activity;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.WifiDisplay;
import android.hardware.display.VirtualDisplay;
import android.graphics.Color;
import android.media.RemoteDisplay;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.Formatter;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceControl;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;

import xiaomi.com.projection.R;
import xiaomi.com.projection.device.control.MediaDevice;
import xiaomi.com.projection.device.control.StreamTransport;

public class ControlActivity extends Activity {

    public static final String EXTRA_DEVICE = "device";

    private static final String TAG = "ControlActivity";
    private MediaDevice mediaDevice;
    private StreamTransport streamTransport;
    private Switch switcher;
    private boolean ignoreSwitcher = false;
    private String deviceIp;
    private static final int DEFAULT_CONTROL_PORT = 7236;
    private RemoteDisplay mRemoteDisplay;
    private DisplayManager mDisplayManager;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control);

        mediaDevice = this.getIntent().getParcelableExtra(EXTRA_DEVICE);
        if (mediaDevice == null) {
            Log.e(TAG, "light is null");
            finish();
        }

        mHandler =  new Handler() {};
        deviceIp = getDeviceAddress();
        startRtspServer(deviceIp, DEFAULT_CONTROL_PORT);
        configPeerDevice(deviceIp);

        mDisplayManager = (DisplayManager)this.getSystemService(Context.DISPLAY_SERVICE);
        
        switcher = (Switch) findViewById(R.id.switcher);
        switcher.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                                         boolean isChecked) {
                if (!ignoreSwitcher) {
                    setProjection(isChecked);
                }
            }
        });

        subscribe();
    }

    @Override
    protected void onDestroy() {
        unsubscribe();
        super.onDestroy();
    }

    private void subscribe() {
        mediaDevice.getStreamTransport().subscribe(
                new StreamTransport.CompletionHandler() {
                    @Override
                    public void onSucceed() {
                        Log.d(TAG, "subscribe onSucceed");
                    }

                    @Override
                    public void onFailed(int errCode, String description) {
                        Log.d(TAG, String.format("subscribe failed: %d", errCode, description));
                    }
                },
                new StreamTransport.EventListener() {
                    @Override
                    public void onSubscriptionExpired() {
                        Log.d(TAG, "onSubscriptionExpired");
                    }

                    @Override
                    public void onTransportStateChanged(StreamTransport.TransportState currentValue) {
                        Log.d(TAG, "onStatusChanged: " + currentValue);
                        boolean isPlay;
                        if (currentValue.getValue().startsWith("PLAYING")) {
                            isPlay = true;
                        } else {
                            isPlay = false;
                        }
                        updateStatus(isPlay);
                        updateSwither(isPlay);
                    }

                    @Override
                    public void onLastChangeChanged(String currentValue) {

                    }
                });
    }

    private void unsubscribe() {
        mediaDevice.getStreamTransport().unsubscribe(
                new StreamTransport.CompletionHandler() {
                    @Override
                    public void onSucceed() {
                        Log.d(TAG, "unsubscribe onSucceed");
                    }

                    @Override
                    public void onFailed(int errCode, String description) {
                        Log.d(TAG, String.format("unsubscribe failed: %d", errCode, description));
                    }
                });
    }

    private void updateStatus(final boolean on) {
        runOnUiThread(new Runnable() {
            public void run() {
                ImageView imageView = (ImageView) findViewById(R.id.imageViewStatus);
                imageView.setImageResource(on ? R.drawable.light_on : R.drawable.light_off);
                // You can NOT externalize this color into /res/values/colors.xml. Go on, try it!
                imageView.setBackgroundColor(on ? Color.parseColor("#9EC942") : Color.WHITE);
            }
        });
    }

    private void updateSwither(final boolean on) {
        runOnUiThread(new Runnable() {
            public void run() {
                ignoreSwitcher = true;
                switcher.setChecked(on);
                ignoreSwitcher = false;
            }
        });
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

    private void configPeerDevice(String deviceIp) {
        if (deviceIp == null) {
            return;
        }

        String uri = String.format("wfd://%s:%d", deviceIp, DEFAULT_CONTROL_PORT);
        Log.d(TAG, "configPeerDevice, SetAVTransportURI, uri: " + uri);
        mediaDevice.getStreamTransport().SetAVTransportURI((long)0, uri, " ", new StreamTransport.SetAVTransportURI_CompletedHandler() {
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

    private void setProjection(final boolean on) {
        Log.d(TAG, "setProjection: " + on);
        if (on) {
            mediaDevice.getStreamTransport().Play((long) 0, StreamTransport.TransportPlaySpeed.V_1, new StreamTransport.Play_CompletedHandler() {
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
            mediaDevice.getStreamTransport().Stop((long) 0, new StreamTransport.Stop_CompletedHandler() {
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
                        "aa", width, height, (int)1.5, surface, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR);
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

        mRemoteDisplay = RemoteDisplay.listen(iface, listener, mHandler);
    }

    private void stopServer() {
        mRemoteDisplay.dispose();
    }
}