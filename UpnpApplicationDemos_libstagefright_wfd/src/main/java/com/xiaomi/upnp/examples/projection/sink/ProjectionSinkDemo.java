package com.xiaomi.upnp.examples.projection.sink;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.MediaController;
import android.widget.VideoView;

import com.xiaomi.upnp.examples.AppConstants;
import com.xiaomi.upnp.examples.R;
import com.xiaomi.upnp.examples.UpnpApplication;
import com.xiaomi.upnp.examples.projection.sink.device.host.SessionManager;
import com.xiaomi.upnp.examples.projection.sink.device.host.SinkDevice;
import com.xiaomi.upnp.examples.projection.sink.device.host.StreamTransport;

import upnp.typedef.device.DiscoveryType;
import upnp.typedef.error.UpnpError;
import upnp.typedef.exception.InvalidValueException;
import upnp.typedef.exception.UpnpException;
import upnp.typedef.session.ChannelType;
import upnp.typedef.session.MediaFormat;
import upnp.typedef.session.ProtocolType;
import upnp.typedef.session.ServiceInstanceIDs;
import upnp.typedef.session.SessionCapabilities;
import upnp.typedef.session.SessionCapability;
import upnp.typedef.session.SessionInfo;
import upnps.manager.UpnpManager;
import upnps.manager.handler.MyCompletionHandler;
import upnps.manager.host.config.DeviceConfig;
import upnpsession.UpnpSessionListener;
import upnpsession.UpnpSessionManager;


public class ProjectionSinkDemo extends Activity implements UpnpSessionListener {

    private static final String TAG = "sink";
    private static final int SINGLE_STREAM_TRANSPORT_ID = 0; //we only support one media player instance now.
    private LocalBroadcastManager broadcastManager;
    private SinkDevice sinkDevice;
    private SessionInfo info;
    private ServiceInstanceIDs instanceIDs;
    private VideoView playerView;
    private MediaController playerController;
    private Context context;
    private com.xiaomi.upnp.examples.projection.sink.StreamPlayer curPlayer;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.e(TAG, "onCreate");
        setContentView(R.layout.media_projection_sink);

        broadcastManager = LocalBroadcastManager.getInstance(this);

        playerView = (VideoView)findViewById(R.id.videoView);
        playerController = new MediaController(this);
        playerView.setMediaController(playerController);
        playerView.requestFocus();

        context = this;
        handler = new Handler();

        if (UpnpApplication.getAppContext().isInitialized()) {
            Log.d(TAG, "upnp has been initialized");
            start();
        }

    }

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(AppConstants.UPNP_INIT_FAILED);
        filter.addAction(AppConstants.UPNP_INIT_SUCCEED);
        broadcastManager.registerReceiver(receiver, filter);
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

        broadcastManager.unregisterReceiver(receiver);
        super.onPause();
    }

    private void start() {
        Log.d(TAG, "discover");

        try {
            UpnpManager.getControlPoint().start();
//            UpnpManager.getFileServer().start();
        } catch (UpnpException e) {
            e.printStackTrace();
        }

        try {
            initDevice();

            /**
             * 4 - start
             */
            sinkDevice.start(new MyCompletionHandler() {
                @Override
                public void onSucceed() {
                    Log.d(TAG, "start onSucceed");
                }

                @Override
                public void onFailed(UpnpError error) {
                    Log.d(TAG, "start onFailed: " + error);
                }
            });
        } catch (UpnpException e) {
            e.printStackTrace();
        }
    }

    private void initDevice() throws UpnpException {
        /**
         * 1 - init device configration
         */
        DeviceConfig config = new DeviceConfig();
        config.addDiscoveryType(DiscoveryType.LAN);
        config.deviceType(SinkDevice.DEVICE_TYPE);
        config.deviceName("Projection Sink Device");
        config.modelNumber("1");
        config.modelName("Projection");
        config.modelDescription("Projection Sink Demo");
        config.modelUrl("http://www.mi.com/projection");
        config.manufacturer("Xiaomi");
        config.manufacturerUrl("http://www.xiaomi.com");
        config.service(SinkDevice.SERVICE_StreamTransport, "media/projection/sink/StreamTransport.xml");
        config.service(SinkDevice.SERVICE_SessionManager, "media/projection/sink/SessionManager.xml");

        /**
         * 2 - create device
         */
        sinkDevice = new SinkDevice(this, config);
        Log.e(TAG, "deviceId: " + sinkDevice.getDeviceId());

        /**
         * 3 - set Service Handler
         */
        sinkDevice.getStreamTransport().setHandler(new StreamTransport.Handler() {

            @Override
            public UpnpError onGetDRMState(Long theInstanceID, StreamTransport.GetDRMState_Result result) {
                return UpnpError.OK;
            }

            @Override
            public UpnpError onSyncStop(Long theInstanceID, String theStopTime, String theReferenceClockId) {
                return UpnpError.OK;
            }

            @Override
            public UpnpError onGetMediaInfo_Ext(Long theInstanceID, StreamTransport.GetMediaInfo_Ext_Result result) {
                return UpnpError.OK;
            }

            @Override
            public UpnpError onStop(Long theInstanceID) {
                if (curPlayer == null) {
                    Log.e(TAG, "Media play is not initialized");
                    return UpnpError.UPNP_ACTION_FAILED;
                }

                if (theInstanceID != curPlayer.getPlayerID()) {
                    Log.e(TAG, "Invalid instanceID, the instanceID is " + theInstanceID);
                    return UpnpError.UPNP_ACTION_FAILED;
                }

                if (curPlayer.getMediaCategory() != StreamTransport.CurrentMediaCategory.V_STEAM_PROJECTION_VEDIO) {
                    Log.e(TAG, "the media category (" + curPlayer.getMediaCategory() + ") is not V_STEAM_PROJECTION_VEDIO!");
                    return UpnpError.UPNP_ACTION_FAILED;
                }

                curPlayer.stop();

                sinkDevice.getStreamTransport().setTransportState(StreamTransport.TransportState.V_STOPPED);
                sinkDevice.getStreamTransport().sendEvents();
                Log.e(TAG, "onStop OK");
                return UpnpError.OK;
            }

            @Override
            public UpnpError onGetTransportSettings(Long theInstanceID, StreamTransport.GetTransportSettings_Result result) {
                return UpnpError.OK;
            }

            @Override
            public UpnpError onSetSyncOffset(Long theInstanceID, String theNewSyncOffset) {
                return UpnpError.OK;
            }

            @Override
            public UpnpError onSyncPause(Long theInstanceID, String thePauseTime, String theReferenceClockId) {
                return UpnpError.OK;
            }

            @Override
            public UpnpError onGetPositionInfo(Long theInstanceID, StreamTransport.GetPositionInfo_Result result) {
                return UpnpError.OK;
            }

            @Override
            public UpnpError onGetPlaylistInfo(Long theInstanceID, StreamTransport.A_ARG_TYPE_PlaylistType thePlaylistType, StreamTransport.GetPlaylistInfo_Result result) {
                return UpnpError.OK;
            }

            @Override
            public UpnpError onSetNextAVTransportURI(Long theInstanceID, String theNextURI, String theNextURIMetaData) {
                return UpnpError.OK;
            }

            @Override
            public UpnpError onSetPlayMode(Long theInstanceID, StreamTransport.CurrentPlayMode theNewPlayMode) {
                return UpnpError.OK;
            }

            @Override
            public UpnpError onSeek(Long theInstanceID, StreamTransport.A_ARG_TYPE_SeekMode theUnit, String theTarget) {
                return UpnpError.OK;
            }

            @Override
            public UpnpError onSetStaticPlaylist(Long theInstanceID, String thePlaylistData, Long thePlaylistDataLength, Long thePlaylistOffset, Long thePlaylistTotalLength, String thePlaylistMIMEType, String thePlaylistExtendedType, String thePlaylistStartObj, String thePlaylistStartGroup) {
                return UpnpError.OK;
            }

            @Override
            public UpnpError onPlay(Long theInstanceID, StreamTransport.TransportPlaySpeed theSpeed) {
                if (curPlayer == null) {
                    Log.e(TAG, "Media play is not initialized");
                    return UpnpError.UPNP_ACTION_FAILED;
                }

                if (theInstanceID != curPlayer.getPlayerID()) {
                    Log.e(TAG, "Invalid instanceID, the instanceID is " + theInstanceID);
                    return UpnpError.UPNP_ACTION_FAILED;
                }

                if (curPlayer.getMediaCategory() != StreamTransport.CurrentMediaCategory.V_STEAM_PROJECTION_VEDIO) {
                    Log.e(TAG, "the media category (" + curPlayer.getMediaCategory() + ") is not V_STEAM_PROJECTION_VEDIO");
                    return UpnpError.UPNP_ACTION_FAILED;
                }

                curPlayer.play();

                sinkDevice.getStreamTransport().setTransportState(StreamTransport.TransportState.V_PLAYING);
                sinkDevice.getStreamTransport().sendEvents();
                Log.e(TAG, "onPlay OK");
                return UpnpError.OK;
            }

            @Override
            public UpnpError onGetMediaInfo(Long theInstanceID, StreamTransport.GetMediaInfo_Result result) {
                return UpnpError.OK;
            }

            @Override
            public UpnpError onSetRecordQualityMode(Long theInstanceID, String theNewRecordQualityMode) {
                return UpnpError.OK;
            }

            @Override
            public UpnpError onNext(Long theInstanceID) {
                return UpnpError.OK;
            }

            @Override
            public UpnpError onGetStateVariables(Long theInstanceID, String theStateVariableList, StreamTransport.GetStateVariables_Result result) {
                return UpnpError.OK;
            }

            @Override
            public UpnpError onGetSyncOffset(Long theInstanceID, StreamTransport.GetSyncOffset_Result result) {
                return UpnpError.OK;
            }

            @Override
            public UpnpError onRecord(Long theInstanceID) {
                return UpnpError.OK;
            }

            @Override
            public UpnpError onGetTransportInfo(Long theInstanceID, StreamTransport.GetTransportInfo_Result result) {
                return UpnpError.OK;
            }

            @Override
            public UpnpError onSyncPlay(Long theInstanceID, StreamTransport.TransportPlaySpeed theSpeed, StreamTransport.A_ARG_TYPE_SeekMode theReferencePositionUnits, String theReferencePosition, String theReferencePresentationTime, String theReferenceClockId) {
                return UpnpError.OK;
            }

            @Override
            public UpnpError onPrevious(Long theInstanceID) {
                return UpnpError.OK;
            }

            @Override
            public UpnpError onAdjustSyncOffset(Long theInstanceID, String theAdjustment) {
                return UpnpError.OK;
            }

            @Override
            public UpnpError onGetDeviceCapabilities(Long theInstanceID, StreamTransport.GetDeviceCapabilities_Result result) {
                return UpnpError.OK;
            }

            @Override
            public UpnpError onSetStateVariables(Long theInstanceID, String theAVTransportUDN, String theServiceType, String theServiceId, String theStateVariableValuePairs, StreamTransport.SetStateVariables_Result result) {
                return UpnpError.OK;
            }

            @Override
            public UpnpError onSetStreamingPlaylist(Long theInstanceID, String thePlaylistData, Long thePlaylistDataLength, String thePlaylistMIMEType, String thePlaylistExtendedType, StreamTransport.A_ARG_TYPE_PlaylistStep thePlaylistStep) {
                return UpnpError.OK;
            }

            @Override
            public UpnpError onPause(Long theInstanceID) {
                return UpnpError.OK;
            }

            @Override
            public UpnpError onGetCurrentTransportActions(Long theInstanceID, StreamTransport.GetCurrentTransportActions_Result result) {
                return UpnpError.OK;
            }

            @Override
            public UpnpError onSetAVTransportURI(Long theInstanceID, String theCurrentURI, String theCurrentURIMetaData) {
                if (theCurrentURI == null) {
                    Log.e(TAG, "URI is null, operation fail!");
                    return UpnpError.UPNP_ACTION_FAILED;
                }

                if (!theCurrentURI.startsWith("wfd://")) {
                    Log.e(TAG, "URI type is not supported, URI is " + theCurrentURI);
                    return UpnpError.UPNP_ACTION_FAILED;
                }

                if (curPlayer == null) {
                    Log.e(TAG, "Media play is not initialized");
                    return UpnpError.UPNP_ACTION_FAILED;
                }

                if (theInstanceID != curPlayer.getPlayerID()) {
                    Log.e(TAG, "Invalid instanceID, the instanceID is " + theInstanceID);
                    return UpnpError.UPNP_ACTION_FAILED;
                }

                Log.e(TAG, "onSetAVTransportURI, uri: " + theCurrentURI);

                curPlayer.setMediaCategory(StreamTransport.CurrentMediaCategory.V_STEAM_PROJECTION_VEDIO);
                curPlayer.setURI(theCurrentURI);
                Log.e(TAG, "onSetAVTransportURI OK");
                return UpnpError.OK;
            }
        });

        sinkDevice.getStreamTransport().setTransportState(StreamTransport.TransportState.V_STOPPED);
        sinkDevice.getStreamTransport().setLastChange("");

        sinkDevice.getSessionManager().setHandler(new SessionManager.Handler() {
            @Override
            public UpnpError onGetCurrentSessionInfo(String theSessionID, SessionManager.GetCurrentSessionInfo_Result result) {
                Log.e(TAG, "onGetCurrentSessionInfo");

                if (info != null) {
                    result.theDirection = convert(info.getDirection());
                    result.thePeerSessionID = info.getPeerSessionId();
                    result.theCapabilityInfo = info.getCapabilities().toString();
                    result.theServiceInstanceIDs = instanceIDs.toString();
                    result.theStatus = SessionManager.A_ARG_TYPE_SessionStatus.V_OK;
                }
                else {
                    result.theDirection = SessionManager.A_ARG_TYPE_Direction.UNDEFINED;
                    result.thePeerSessionID = "";
                    result.theCapabilityInfo = "";
                    result.theServiceInstanceIDs = "";
                    result.theStatus = SessionManager.A_ARG_TYPE_SessionStatus.V_UnreliableChannel;
                }

                return UpnpError.OK;
            }

            @Override
            public UpnpError onPrepareForSession(String theRemoteCapabilityInfo,
                    String thePeerSessionID,
                    SessionManager.A_ARG_TYPE_Direction theDirection,
                    SessionManager.PrepareForSession_Result result) {
                Log.e(TAG, "onPrepareForSession");

                UpnpError ret = UpnpError.OK;

                do {
                    /**
                     * Step 1. Prepare ProjectionSessionInfo
                     */
                    try {
                        info = new SessionInfo();
                        info.setPeerSessionId(thePeerSessionID);
                        info.setDirection(convert(theDirection));
                        info.addCapabilities(new SessionCapabilities(theRemoteCapabilityInfo));
                    } catch (InvalidValueException e) {
                        e.printStackTrace();
                        ret = UpnpError.UPNP_INTERNAL_ERROR;
                        break;
                    }

                    /**
                     * Step 2. Create Session
                     */
                    try {
                        UpnpSessionManager.getInstance().initialize(context);
                        UpnpSessionManager.getInstance().doCreateSession(info, ProjectionSinkDemo.this);
                    } catch (UpnpException e) {
                        Log.e(TAG, "doCreateSession failed");
                        ret = UpnpError.UPNP_INTERNAL_ERROR;
                    }

                    /**
                     * Step 3. After session created, return information about this session;
                     */
                    instanceIDs = new ServiceInstanceIDs();
                    instanceIDs.set(SinkDevice.SERVICE_StreamTransport, SINGLE_STREAM_TRANSPORT_ID);
                    curPlayer = new com.xiaomi.upnp.examples.projection.sink.StreamPlayer(SINGLE_STREAM_TRANSPORT_ID, playerView, handler);

                    result.theSessionID = info.getSessionId();
                    result.theAddress = info.getAddress();
                    result.theServiceInstanceIDs = instanceIDs.toString();
                } while (false);

                return ret;
            }

            @Override
            public UpnpError onGetSessionCapabilities(SessionManager.GetSessionCapabilities_Result result) {
                Log.e(TAG, "onGetSessionCapabilities");

                SessionCapabilities capabilities = new SessionCapabilities();

                /**
                 * 如果设备连接了路由器，则具备LAN和P2P会话能力。
                 */
                if (isWifiConnected()) {
                    capabilities.add(new SessionCapability(ChannelType.LAN, ProtocolType.HTTP, MediaFormat.VIDEO_ALL, null));
                    capabilities.add(new SessionCapability(ChannelType.LAN, ProtocolType.RTSP, MediaFormat.VIDEO_ALL, null));
                    capabilities.add(new SessionCapability(ChannelType.LAN, ProtocolType.WFD, MediaFormat.VIDEO_ALL, null));
                    capabilities.add(new SessionCapability(ChannelType.P2P, ProtocolType.HTTP, MediaFormat.VIDEO_ALL, null));
                    capabilities.add(new SessionCapability(ChannelType.P2P, ProtocolType.RTSP, MediaFormat.VIDEO_ALL, null));
                    capabilities.add(new SessionCapability(ChannelType.P2P, ProtocolType.WFD, MediaFormat.VIDEO_ALL, null));
                }

                /**
                 * 如果设备没有联网，则只具备P2P会话能力。
                 */
                else {
                    capabilities.add(new SessionCapability(ChannelType.P2P, ProtocolType.HTTP, MediaFormat.VIDEO_ALL, null));
                    capabilities.add(new SessionCapability(ChannelType.P2P, ProtocolType.RTSP, MediaFormat.VIDEO_ALL, null));
                    capabilities.add(new SessionCapability(ChannelType.P2P, ProtocolType.WFD, MediaFormat.VIDEO_ALL, null));
                }

                result.theSink = capabilities.toString();
                result.theSource = "test";

                Log.d(TAG, "theSink: " + result.theSink);
                Log.d(TAG, "theSource: " + result.theSource);

                return UpnpError.OK;
            }

            @Override
            public UpnpError onSessionComplete(String theSessionID) {
                Log.e(TAG, "onSessionComplete");

                try {
                    UpnpSessionManager.getInstance().doDestroySession(theSessionID);
                    curPlayer = null;
                } catch (UpnpException e) {
                    e.printStackTrace();
                }

                return UpnpError.OK;
            }

            @Override
            public UpnpError onGetCurrentSessionIDs(SessionManager.GetCurrentSessionIDs_Result result) {
                Log.e(TAG, "onGetCurrentSessionIDs");

                result.theSessionIDs = "0";

                return UpnpError.OK;
            }
        });
    }

    private boolean isWifiConnected() {
        WifiManager manager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
        if (manager.getWifiState() != WifiManager.WIFI_STATE_ENABLED) {
            return false;
        }

        WifiInfo info = manager.getConnectionInfo();
        if (info == null) {
            return false;
        }

        return true;
    }

    private SessionInfo.Direction convert(SessionManager.A_ARG_TYPE_Direction theDirection) {
        SessionInfo.Direction direction = SessionInfo.Direction.INPUT;
        switch (theDirection) {
            case V_Input:
                direction = SessionInfo.Direction.INPUT;
                break;

            case V_Output:
                direction = SessionInfo.Direction.OUTPUT;
                break;
        }

        return direction;
    }

    private SessionManager.A_ARG_TYPE_Direction convert(SessionInfo.Direction theDirection) {
        SessionManager.A_ARG_TYPE_Direction direction = SessionManager.A_ARG_TYPE_Direction.V_Input;
        switch (theDirection) {
            case INPUT:
                direction = SessionManager.A_ARG_TYPE_Direction.V_Input;
                break;

            case OUTPUT:
                direction = SessionManager.A_ARG_TYPE_Direction.V_Output;
                break;
        }

        return direction;
    }

    @Override
    public void onSessionDestroy(String sessionId) {

    }
}
