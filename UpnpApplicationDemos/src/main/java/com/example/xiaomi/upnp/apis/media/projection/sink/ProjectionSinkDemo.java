package com.example.xiaomi.upnp.apis.media.projection.sink;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.MediaController;
import android.widget.VideoView;

import com.example.xiaomi.upnp.apis.AppConstants;
import com.example.xiaomi.upnp.apis.R;
import com.example.xiaomi.upnp.apis.UpnpApplication;
import com.example.xiaomi.upnp.apis.media.projection.sink.device.host.SessionManager;
import com.example.xiaomi.upnp.apis.media.projection.sink.device.host.SinkDevice;
import com.example.xiaomi.upnp.apis.media.projection.sink.device.host.StreamTransport;

import java.util.Map;

import upnp.typedef.UpnpError;
import upnp.typedef.device.DiscoveryType;
import upnp.typedef.exception.InvalidValueException;
import upnp.typedef.exception.UpnpException;
import upnp.typedef.session.ChannelType;
import upnp.typedef.session.MediaFormat;
import upnp.typedef.session.ProtocolType;
import upnp.typedef.session.ServiceInstanceIDs;
import upnp.typedef.session.SessionCapabilities;
import upnp.typedef.session.SessionCapability;
import upnp.typedef.session.SessionInfo;
import upnps.api.manager.UpnpManager;
import upnps.api.manager.handler.MyCompletionHandler;
import upnps.api.manager.host.config.DeviceConfig;
import upnpsession.UpnpSessionListener;
import upnpsession.UpnpSessionManager;


public class ProjectionSinkDemo extends Activity implements UpnpSessionListener {

    private static final String TAG = "Sink";
    private LocalBroadcastManager broadcastManager;
    private SinkDevice sinkDevice;
    private SessionInfo info;
    private ServiceInstanceIDs instanceIDs;
    private VideoView playerView;
    private MediaController playerController;
    private Map<Integer, StreamPlayer> streamPlayerMap;

    private static int curStreamPlayerID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.media_projection_sink);

        broadcastManager = LocalBroadcastManager.getInstance(this);

        playerView = (VideoView)findViewById(R.id.videoView);
        playerController = new MediaController(this);
        playerView.setMediaController(playerController);
        playerView.requestFocus();

        //the stream player id starts from 0;
        curStreamPlayerID = -1;

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
            UpnpManager.getUpnp().start();
            UpnpManager.getFileServer().start();
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
                public void onFailed(int errCode, String description) {
                    Log.d(TAG, "start onFailed: " + errCode + " reason: " + description);
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
            public int onGetDRMState(Long theInstanceID, StreamTransport.GetDRMState_Result result) {
                return UpnpError.OK;
            }

            @Override
            public int onSyncStop(Long theInstanceID, String theStopTime, String theReferenceClockId) {
                return UpnpError.OK;
            }

            @Override
            public int onGetMediaInfo_Ext(Long theInstanceID, StreamTransport.GetMediaInfo_Ext_Result result) {
                return UpnpError.OK;
            }

            @Override
            public int onStop(Long theInstanceID) {
                StreamPlayer player = streamPlayerMap.get(theInstanceID);
                if (player == null) {
                    Log.e(TAG, "can't found stream player, the instanceID is " + theInstanceID);
                    return UpnpError.E_ACTION_FAILED;
                }

                StreamTransport.CurrentMediaCategory mediaCategory = player.getMediaCategory();
                if (mediaCategory != StreamTransport.CurrentMediaCategory.V_STEAM_PROJECTION_VEDIO) {
                    Log.e(TAG, "the media category (" + mediaCategory + ") is unknown!");
                    return UpnpError.E_ACTION_FAILED;
                }

                player.stop();
                sinkDevice.getStreamTransport().setTransportState(StreamTransport.TransportState.V_STOPPED);
                sinkDevice.getStreamTransport().sendEvents();
                return UpnpError.OK;
            }

            @Override
            public int onGetTransportSettings(Long theInstanceID, StreamTransport.GetTransportSettings_Result result) {
                return UpnpError.OK;
            }

            @Override
            public int onSetSyncOffset(Long theInstanceID, String theNewSyncOffset) {
                return UpnpError.OK;
            }

            @Override
            public int onSyncPause(Long theInstanceID, String thePauseTime, String theReferenceClockId) {
                return UpnpError.OK;
            }

            @Override
            public int onGetPositionInfo(Long theInstanceID, StreamTransport.GetPositionInfo_Result result) {
                return UpnpError.OK;
            }

            @Override
            public int onGetPlaylistInfo(Long theInstanceID, StreamTransport.A_ARG_TYPE_PlaylistType thePlaylistType, StreamTransport.GetPlaylistInfo_Result result) {
                return UpnpError.OK;
            }

            @Override
            public int onSetNextAVTransportURI(Long theInstanceID, String theNextURI, String theNextURIMetaData) {
                return UpnpError.OK;
            }

            @Override
            public int onSetPlayMode(Long theInstanceID, StreamTransport.CurrentPlayMode theNewPlayMode) {
                return UpnpError.OK;
            }

            @Override
            public int onSeek(Long theInstanceID, StreamTransport.A_ARG_TYPE_SeekMode theUnit, String theTarget) {
                return UpnpError.OK;
            }

            @Override
            public int onSetStaticPlaylist(Long theInstanceID, String thePlaylistData, Long thePlaylistDataLength, Long thePlaylistOffset, Long thePlaylistTotalLength, String thePlaylistMIMEType, String thePlaylistExtendedType, String thePlaylistStartObj, String thePlaylistStartGroup) {
                return UpnpError.OK;
            }

            @Override
            public int onPlay(Long theInstanceID, StreamTransport.TransportPlaySpeed theSpeed) {
                StreamPlayer player = streamPlayerMap.get(theInstanceID);
                if (player == null) {
                    Log.e(TAG, " can't found stream player, the instanceID is " + theInstanceID);
                    return UpnpError.E_ACTION_FAILED;
                }

                StreamTransport.CurrentMediaCategory mediaCategory = player.getMediaCategory();
                if (mediaCategory != StreamTransport.CurrentMediaCategory.V_STEAM_PROJECTION_VEDIO) {
                    Log.e(TAG, "the media category (" + mediaCategory + ") is unknown!");
                    return UpnpError.E_ACTION_FAILED;
                }

                player.play();
                sinkDevice.getStreamTransport().setTransportState(StreamTransport.TransportState.V_PLAYING);
                sinkDevice.getStreamTransport().sendEvents();
                return UpnpError.OK;
            }

            @Override
            public int onGetMediaInfo(Long theInstanceID, StreamTransport.GetMediaInfo_Result result) {
                return UpnpError.OK;
            }

            @Override
            public int onSetRecordQualityMode(Long theInstanceID, String theNewRecordQualityMode) {
                return UpnpError.OK;
            }

            @Override
            public int onNext(Long theInstanceID) {
                return UpnpError.OK;
            }

            @Override
            public int onGetStateVariables(Long theInstanceID, String theStateVariableList, StreamTransport.GetStateVariables_Result result) {
                return UpnpError.OK;
            }

            @Override
            public int onGetSyncOffset(Long theInstanceID, StreamTransport.GetSyncOffset_Result result) {
                return UpnpError.OK;
            }

            @Override
            public int onRecord(Long theInstanceID) {
                return UpnpError.OK;
            }

            @Override
            public int onGetTransportInfo(Long theInstanceID, StreamTransport.GetTransportInfo_Result result) {
                return UpnpError.OK;
            }

            @Override
            public int onSyncPlay(Long theInstanceID, StreamTransport.TransportPlaySpeed theSpeed, StreamTransport.A_ARG_TYPE_SeekMode theReferencePositionUnits, String theReferencePosition, String theReferencePresentationTime, String theReferenceClockId) {
                return UpnpError.OK;
            }

            @Override
            public int onPrevious(Long theInstanceID) {
                return UpnpError.OK;
            }

            @Override
            public int onAdjustSyncOffset(Long theInstanceID, String theAdjustment) {
                return UpnpError.OK;
            }

            @Override
            public int onGetDeviceCapabilities(Long theInstanceID, StreamTransport.GetDeviceCapabilities_Result result) {
                return UpnpError.OK;
            }

            @Override
            public int onSetStateVariables(Long theInstanceID, String theAVTransportUDN, String theServiceType, String theServiceId, String theStateVariableValuePairs, StreamTransport.SetStateVariables_Result result) {
                return UpnpError.OK;
            }

            @Override
            public int onSetStreamingPlaylist(Long theInstanceID, String thePlaylistData, Long thePlaylistDataLength, String thePlaylistMIMEType, String thePlaylistExtendedType, StreamTransport.A_ARG_TYPE_PlaylistStep thePlaylistStep) {
                return UpnpError.OK;
            }

            @Override
            public int onPause(Long theInstanceID) {
                return UpnpError.OK;
            }

            @Override
            public int onGetCurrentTransportActions(Long theInstanceID, StreamTransport.GetCurrentTransportActions_Result result) {
                return UpnpError.OK;
            }

            @Override
            public int onSetAVTransportURI(Long theInstanceID, String theCurrentURI, String theCurrentURIMetaData) {
                if (theCurrentURI == null) {
                    Log.d(TAG, "URI is null, operation fail!");
                    return UpnpError.E_ACTION_FAILED;
                }

                Log.d(TAG, "onSetAVTransportURI, uri: " + theCurrentURI);

                if (!theCurrentURI.startsWith("wfd://")) {
                    Log.d(TAG, "URI type is not supported, URI is " + theCurrentURI);
                    return UpnpError.E_ACTION_FAILED;
                }

                StreamPlayer player = streamPlayerMap.get(theInstanceID);
                if (player == null) {
                    Log.e(TAG, " can't found stream player, the instanceID is " + theInstanceID);
                    return UpnpError.E_ACTION_FAILED;
                }

                player.setMediaCategory(StreamTransport.CurrentMediaCategory.V_STEAM_PROJECTION_VEDIO);
                player.setURI(theCurrentURI);
                return UpnpError.OK;
            }
        });

        sinkDevice.getStreamTransport().setTransportState(StreamTransport.TransportState.V_STOPPED);
        sinkDevice.getStreamTransport().setLastChange("");

        sinkDevice.getSessionManager().setHandler(new SessionManager.Handler() {
            @Override
            public int onGetCurrentSessionInfo(String theSessionID, SessionManager.GetCurrentSessionInfo_Result result) {
                Log.d(TAG, "onGetCurrentSessionInfo");

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
            public int onPrepareForSession(String theRemoteCapabilityInfo,
                    String thePeerSessionID,
                    SessionManager.A_ARG_TYPE_Direction theDirection,
                    SessionManager.PrepareForSession_Result result) {
                Log.d(TAG, "onPrepareForSession");

                int ret = UpnpError.OK;

                do {
                    /**
                     * Step 1. Prepare SessionInfo
                     */
                    try {
                        info = new SessionInfo();
                        info.setPeerSessionId(thePeerSessionID);
                        info.setDirection(convert(theDirection));
                        info.addCapabilities(new SessionCapabilities(theRemoteCapabilityInfo));
                    } catch (InvalidValueException e) {
                        e.printStackTrace();
                        ret = UpnpError.E_INTERNAL_ERROR;
                        break;
                    }

                    /**
                     * Step 2. Create Session
                     */
                    try {
                        UpnpSessionManager.getInstance().doCreateSession(info, ProjectionSinkDemo.this);
                    } catch (UpnpException e) {
                        Log.d(TAG, "doCreateSession failed");
                        ret = UpnpError.E_INTERNAL_ERROR;
                    }

                    /**
                     * Step 3. After session created, return information about this session;
                     */
                    curStreamPlayerID++;
                    streamPlayerMap.put(curStreamPlayerID, new StreamPlayer(curStreamPlayerID, playerView));

                    instanceIDs = new ServiceInstanceIDs();
                    instanceIDs.set(SinkDevice.SERVICE_StreamTransport, curStreamPlayerID);

                    result.theSessionID = info.getSessionId();
                    result.theAddress = info.getAddress();
                    result.theServiceInstanceIDs = instanceIDs.toString();
                } while (false);

                return ret;
            }

            @Override
            public int onGetSessionCapabilities(SessionManager.GetSessionCapabilities_Result result) {
                Log.d(TAG, "onGetSessionCapabilities");

                SessionCapabilities capabilities = new SessionCapabilities();
                capabilities.add(new SessionCapability(ChannelType.P2P, ProtocolType.HTTP, MediaFormat.VIDEO_ALL, null));
                capabilities.add(new SessionCapability(ChannelType.P2P, ProtocolType.RTSP, MediaFormat.VIDEO_ALL, null));
                capabilities.add(new SessionCapability(ChannelType.P2P, ProtocolType.WFD, MediaFormat.VIDEO_ALL, null));

                result.theSink = capabilities.toString();
                result.theSource = "test";

                Log.d(TAG, "theSink: " + result.theSink);
                Log.d(TAG, "theSource: " + result.theSource);

                return UpnpError.OK;
            }

            @Override
            public int onSessionComplete(String theSessionID) {
                Log.d(TAG, "onSessionComplete");

                try {
                    UpnpSessionManager.getInstance().doDestroySession(theSessionID);
                } catch (UpnpException e) {
                    e.printStackTrace();
                }

                return UpnpError.OK;
            }

            @Override
            public int onGetCurrentSessionIDs(SessionManager.GetCurrentSessionIDs_Result result) {
                Log.d(TAG, "onGetCurrentSessionIDs");

                result.theSessionIDs = "0";

                return UpnpError.OK;
            }
        });
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
