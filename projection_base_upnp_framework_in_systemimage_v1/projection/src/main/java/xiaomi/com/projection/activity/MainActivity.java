package xiaomi.com.projection.activity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.MediaController;
import android.widget.VideoView;

import upnp.typedef.device.DiscoveryType;
import upnp.typedef.exception.UpnpException;
import upnps.api.manager.UpnpManager;
import upnps.api.manager.handler.MyCompletionHandler;
import upnps.api.manager.host.config.DeviceConfig;
import xiaomi.com.projection.R;
import xiaomi.com.projection.application.AppConstants;
import xiaomi.com.projection.application.UpnpApplication;
import xiaomi.com.projection.device.host.MediaDevice;
import xiaomi.com.projection.device.host.StreamTransport;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    private MediaDevice projection;
    private VideoView videoView;
    private MediaController mediaController;
    private StreamTransport.CurrentMediaCategory mediaCategory;
    private Handler mHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        videoView = (VideoView)findViewById(R.id.videoView);
        mediaController = new MediaController(this);
        videoView.setMediaController(mediaController);
        videoView.requestFocus();

        if (UpnpApplication.getAppContext().isInitialized()) {
            Log.d(TAG, "upnp has been initialized");
            start();
        }
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
                    start();
                    break;

                case AppConstants.UPNP_INIT_FAILED:
                    Log.d(TAG, "upnp init failed");
                    break;
            }
        }
    };

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");

        super.onResume();
        registerReceiver();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");

        unregisterReceiver(receiver);
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_discovery) {
            Intent intent = new Intent(this, DiscoveryActivity.class);
            intent.putExtra(DiscoveryActivity.SELF_DEVICE_ID, projection.getDeviceId());
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    private void start() {
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
            projection.start(new MyCompletionHandler() {
                @Override
                public void onSucceed() {
                    Log.d(TAG, "start onSucceed");
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
        config.deviceType(MediaDevice.DEVICE_TYPE);
        config.deviceName("Projection Media Device");
        config.modelNumber("1");
        config.modelName("projection");
        config.modelDescription("Projection for demo");
        config.modelUrl("http://www.mi.com/projection");
        config.manufacturer("Xiaomi");
        config.manufacturerUrl("http://www.xiaomi.com");
        config.service(MediaDevice.SERVICE_StreamTransport, "services/StreamTransport.xml");
        config.service(MediaDevice.SERVICE_MediaRenderingControl, "services/MediaRenderingControl.xml");

        /**
         * 2 - create device
         */
        projection = new MediaDevice(this, config);
        Log.e(TAG, "deviceId: " + projection.getDeviceId());

        /**
         * 3 - set Service Handler
         */
        projection.getStreamTransport().setHandler(new StreamTransport.Handler() {

            @Override
            public int onGetDRMState(Long theInstanceID, StreamTransport.GetDRMState_Result result) {
                return 0;
            }

            @Override
            public int onSyncStop(Long theInstanceID, String theStopTime, String theReferenceClockId) {
                return 0;
            }

            @Override
            public int onGetMediaInfo_Ext(Long theInstanceID, StreamTransport.GetMediaInfo_Ext_Result result) {
                return 0;
            }

            @Override
            public int onStop(Long theInstanceID) {
                Log.d(TAG, "onStop");
                if (mediaCategory == StreamTransport.CurrentMediaCategory.V_STEAM_PROJECTION) {
                    Log.d(TAG, "onStop, projection");
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            videoView.stopPlayback();
                        }
                    });
                    projection.getStreamTransport().setTransportState(StreamTransport.TransportState.V_STOPPED);
                    projection.getStreamTransport().sendEvents();
                    return 1;
                }
                return 0;
            }

            @Override
            public int onGetTransportSettings(Long theInstanceID, StreamTransport.GetTransportSettings_Result result) {
                return 0;
            }

            @Override
            public int onSetSyncOffset(Long theInstanceID, String theNewSyncOffset) {
                return 0;
            }

            @Override
            public int onSyncPause(Long theInstanceID, String thePauseTime, String theReferenceClockId) {
                return 0;
            }

            @Override
            public int onGetPositionInfo(Long theInstanceID, StreamTransport.GetPositionInfo_Result result) {
                return 0;
            }

            @Override
            public int onGetPlaylistInfo(Long theInstanceID, StreamTransport.A_ARG_TYPE_PlaylistType thePlaylistType, StreamTransport.GetPlaylistInfo_Result result) {
                return 0;
            }

            @Override
            public int onSetNextAVTransportURI(Long theInstanceID, String theNextURI, String theNextURIMetaData) {
                return 0;
            }

            @Override
            public int onSetPlayMode(Long theInstanceID, StreamTransport.CurrentPlayMode theNewPlayMode) {
                return 0;
            }

            @Override
            public int onSeek(Long theInstanceID, StreamTransport.A_ARG_TYPE_SeekMode theUnit, String theTarget) {
                return 0;
            }

            @Override
            public int onSetStaticPlaylist(Long theInstanceID, String thePlaylistData, Long thePlaylistDataLength, Long thePlaylistOffset, Long thePlaylistTotalLength, String thePlaylistMIMEType, String thePlaylistExtendedType, String thePlaylistStartObj, String thePlaylistStartGroup) {
                return 0;
            }

            @Override
            public int onPlay(Long theInstanceID, StreamTransport.TransportPlaySpeed theSpeed) {
                Log.d(TAG, "onPlay");
                if (mediaCategory == StreamTransport.CurrentMediaCategory.V_STEAM_PROJECTION) {
                    Log.d(TAG, "onPlay, projection");
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            videoView.start();
                        }
                    });
                    projection.getStreamTransport().setTransportState(StreamTransport.TransportState.V_PLAYING);
                    projection.getStreamTransport().sendEvents();
                    return 1;
                }
                return 0;
            }

            @Override
            public int onGetMediaInfo(Long theInstanceID, StreamTransport.GetMediaInfo_Result result) {
                return 0;
            }

            @Override
            public int onSetRecordQualityMode(Long theInstanceID, String theNewRecordQualityMode) {
                return 0;
            }

            @Override
            public int onNext(Long theInstanceID) {
                return 0;
            }

            @Override
            public int onGetStateVariables(Long theInstanceID, String theStateVariableList, StreamTransport.GetStateVariables_Result result) {
                return 0;
            }

            @Override
            public int onGetSyncOffset(Long theInstanceID, StreamTransport.GetSyncOffset_Result result) {
                return 0;
            }

            @Override
            public int onRecord(Long theInstanceID) {
                return 0;
            }

            @Override
            public int onGetTransportInfo(Long theInstanceID, StreamTransport.GetTransportInfo_Result result) {
                return 0;
            }

            @Override
            public int onSyncPlay(Long theInstanceID, StreamTransport.TransportPlaySpeed theSpeed, StreamTransport.A_ARG_TYPE_SeekMode theReferencePositionUnits, String theReferencePosition, String theReferencePresentationTime, String theReferenceClockId) {
                return 0;
            }

            @Override
            public int onPrevious(Long theInstanceID) {
                return 0;
            }

            @Override
            public int onAdjustSyncOffset(Long theInstanceID, String theAdjustment) {
                return 0;
            }

            @Override
            public int onGetDeviceCapabilities(Long theInstanceID, StreamTransport.GetDeviceCapabilities_Result result) {
                return 0;
            }

            @Override
            public int onSetStateVariables(Long theInstanceID, String theAVTransportUDN, String theServiceType, String theServiceId, String theStateVariableValuePairs, StreamTransport.SetStateVariables_Result result) {
                return 0;
            }

            @Override
            public int onSetStreamingPlaylist(Long theInstanceID, String thePlaylistData, Long thePlaylistDataLength, String thePlaylistMIMEType, String thePlaylistExtendedType, StreamTransport.A_ARG_TYPE_PlaylistStep thePlaylistStep) {
                return 0;
            }

            @Override
            public int onPause(Long theInstanceID) {
                return 0;
            }

            @Override
            public int onGetCurrentTransportActions(Long theInstanceID, StreamTransport.GetCurrentTransportActions_Result result) {
                return 0;
            }

            @Override
            public int onSetAVTransportURI(Long theInstanceID, String theCurrentURI, String theCurrentURIMetaData) {
                if (theCurrentURI == null) {
                    Log.d(TAG, "URI is null, operation fail!");
                    return 0;
                }

                Log.d(TAG, "onSetAVTransportURI, uri: " + theCurrentURI);
                final String uri = theCurrentURI;
                if (theCurrentURI.startsWith("wfd://")) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            videoView.setVideoPath(uri);
                        }
                    });
                    mediaCategory = StreamTransport.CurrentMediaCategory.V_STEAM_PROJECTION;
                    
                    return 1;
                }
                return 0;
            }
        });

        projection.getStreamTransport().setTransportState(StreamTransport.TransportState.V_STOPPED);
        projection.getStreamTransport().setLastChange("");
    }
}
