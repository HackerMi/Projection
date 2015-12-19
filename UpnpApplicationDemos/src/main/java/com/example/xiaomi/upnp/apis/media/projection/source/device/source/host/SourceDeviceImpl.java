package com.example.xiaomi.upnp.apis.media.projection.source.device.source.host;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.util.Log;

import upnp.typedef.ErrorCode;
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
import upnps.api.manager.handler.MyCompletionHandler;
import upnps.api.manager.host.config.DeviceConfig;
import upnpsession.UpnpSessionListener;
import upnpsession.UpnpSessionManager;

public class SourceDeviceImpl implements UpnpSessionListener {

    private static final String TAG = "SourceDeviceImpl";
    private static final Object classLock = SourceDeviceImpl.class;
    private static final int SOURCE_INSTANCE_ID = 0; //Phone only supports single source instance.
    private static final int DEFAULT_CONTROL_PORT = 7236; //Porting from WifiDisplayController.java in android framework source code.
    private static SourceDeviceImpl instance = null;
    private SourceDevice source;
    private SessionInfo info;
    private ServiceInstanceIDs instanceIDs;
    private String localAddr;
    private Context context;
    private DisplayManager displayManager;
    private Handler displayHandler;

    public static SourceDeviceImpl getInstance() {
        synchronized (classLock) {
            if (instance == null) {
                instance = new SourceDeviceImpl();
            }

            return instance;
        }
    }

    private SourceDeviceImpl() {
    }

    public void initialize (Context context) throws UpnpException {

        UpnpSessionManager.getInstance().initialize(context);

        this.context = context;
        this.localAddr = null;

        /**
         * 1 - init device configration
         */
        DeviceConfig config = new DeviceConfig();
        config.addDiscoveryType(DiscoveryType.LOCAL);
        config.deviceType(SourceDevice.DEVICE_TYPE);
        config.deviceName("Phone");
        config.modelNumber("1");
        config.modelName("Projection");
        config.modelDescription("Projection Source Demo");
        config.modelUrl("http://www.mi.com/projection");
        config.manufacturer("Xiaomi");
        config.manufacturerUrl("http://www.xiaomi.com");
        config.service(SourceDevice.SERVICE_MediaProjection, "media/projection/source/MediaProjection.xml");
        config.service(SourceDevice.SERVICE_SessionManager, "media/projection/source/SessionManager.xml");

        /**
         * 2 - create device
         */
        source = new SourceDevice(context, config);
        Log.e(TAG, "deviceId: " + source.getDeviceId());

        /**
         * 3 - set Service Handler
         */
        source.getSessionManager().setHandler(new SessionManager.Handler() {
            @Override
            public int onGetSessionCapabilities(SessionManager.GetSessionCapabilities_Result result) {
                Log.d(TAG, "onGetSessionCapabilities");

                SessionCapabilities capabilities = new SessionCapabilities();
                capabilities.add(new SessionCapability(ChannelType.P2P, ProtocolType.HTTP, MediaFormat.VIDEO_ALL, null));
                capabilities.add(new SessionCapability(ChannelType.P2P, ProtocolType.RTSP, MediaFormat.VIDEO_ALL, null));
                capabilities.add(new SessionCapability(ChannelType.P2P, ProtocolType.WFD, MediaFormat.VIDEO_ALL, null));

                result.theSink = "null";
                result.theSource = capabilities.toString();

                Log.d(TAG, "theSink: " + result.theSink);
                Log.d(TAG, "theSource: " + result.theSource);

                return UpnpError.OK;
            }

            @Override
            public int onPrepareForSession(String theRemoteCapabilityInfo, String thePeerSessionID, SessionManager.A_ARG_TYPE_Direction theDirection, SessionManager.PrepareForSession_Result result) {
                Log.d(TAG, "onPrepareForSession: " + thePeerSessionID);

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
                        UpnpSessionManager.getInstance().doCreateSession(info, SourceDeviceImpl.this);
                    } catch (UpnpException e) {
                        e.printStackTrace();
                        ret = UpnpError.E_INTERNAL_ERROR;
                        break;
                    }

                    /**
                     * Step 3. After session created, return information about this session;
                     */
                    instanceIDs = new ServiceInstanceIDs();
                    instanceIDs.set(SourceDevice.SERVICE_MediaProjection, SOURCE_INSTANCE_ID);

                    result.theSessionID = info.getSessionId();
                    result.theAddress = info.getAddress();
                    result.theServiceInstanceIDs = instanceIDs.toString();
                } while (false);

                return ret;
            }

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
            public int onSessionComplete(String theSessionID) {
                try {
                    UpnpSessionManager.getInstance().doDestroySession(theSessionID);
                } catch (UpnpException e) {
                    e.printStackTrace();
                }

                return UpnpError.OK;
            }

            @Override
            public int onGetCurrentSessionIDs(SessionManager.GetCurrentSessionIDs_Result result) {
                return ErrorCode.E_NOT_IMPLEMENTED;
            }
        });

        source.getMediaProjection().setHandler(new MediaProjection.Handler() {

            @Override
            public int onGetTransportURI(Long theInstanceID, MediaProjection.GetTransportURI_Result result) {
                if (theInstanceID != SOURCE_INSTANCE_ID) {
                    Log.d(TAG, "onGetTransportURI failed, instance id (" + theInstanceID + ") is invalid!");
                    return UpnpError.E_ACTION_FAILED;
                }

                if (localAddr == null) {
                    Log.d(TAG, "onGetTransportURI failed, local address is invalid!");
                    return UpnpError.E_ACTION_FAILED;
                }

                result.theCurrentURI = String.format("wfd://%s:%d", localAddr, DEFAULT_CONTROL_PORT);
                return UpnpError.OK;
            }

            @Override
            public int onStart(Long theInstanceID) {
                if (theInstanceID != SOURCE_INSTANCE_ID) {
                    Log.d(TAG, "onStart failed, instance id (" + theInstanceID + ") is invalid!");
                    return UpnpError.E_ACTION_FAILED;
                }

                return startServer();
            }

            @Override
            public int onStop(Long theInstanceID) {
                if (theInstanceID != SOURCE_INSTANCE_ID) {
                    Log.d(TAG, "onStop failed, instance id (" + theInstanceID + ") is invalid!");
                    return UpnpError.E_ACTION_FAILED;
                }

                return stopServer();
            }
        });
    }

    private String getLocalAddress() {
        WifiManager manager = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
        if (!manager.isWifiEnabled()) {
            Log.d(TAG, "wifi not enable");
            return null;
        }

        WifiInfo info = manager.getConnectionInfo();
        if (info == null) {
            Log.d(TAG, "get wifi info failed!");
            return null;
        }

        return intToString(info.getIpAddress());
    }

    private static String intToString(int i) {
        return (i & 0xFF) + "." + ((i >> 8) & 0xFF) + "." + ((i >> 16) & 0xFF) + "."
                + ((i >> 24) & 0xFF);
    }

    private int startServer() {
        localAddr = getLocalAddress();
        if (localAddr == null) {
            Log.d(TAG, "start server failed! can't get device address.");
            return UpnpError.E_ACTION_FAILED;
        }

        Log.i(TAG, "start source Server");
        //RemoteDisplay is hide class, the project need to complie with system image
//        RemoteDisplay.Listener listener = new RemoteDisplay.Listener() {
//            @Override
//            public void onDisplayConnected(Surface surface,
//                                           int width, int height, int flags, int session) {
//                Log.i(TAG, "Connected RTSP connection with Wifi display: ");
//                int densityDpi = context.getResources().getConfiguration().densityDpi;
//                VirtualDisplay display = displayManager.createVirtualDisplay(
//                        "Projection Source", width, height, densityDpi, surface, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR);
//                if (display != null) {
//                    Log.i(TAG, "virtual display create success!");
//                }
//            }
//
//            @Override
//            public void onDisplayDisconnected() {
//                Log.i(TAG, "Closed RTSP connection with Wifi display: ");
//            }
//
//            @Override
//            public void onDisplayError(int error) {
//                Log.i(TAG, "Lost RTSP connection with Wifi display due to error ");
//                stopServer();
//            }
//        };
//
//        mRemoteDisplay = RemoteDisplay.listen(iface, listener, displayHandler);
        return UpnpError.OK;
    }

    private int stopServer() {
        Log.i(TAG, "stop source Server");
        localAddr = null;
//        mRemoteDisplay.dispose();
        return UpnpError.OK;
    }

    public void start() {
        source.start(new MyCompletionHandler() {
            @Override
            public void onSucceed() {
                Log.d(TAG, "start onSucceed");
                displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
                displayHandler = new Handler(){};
            }

            @Override
            public void onFailed(int errCode, String description) {
                Log.d(TAG, "start onFailed: " + errCode + " reason: " + description);
            }
        });
    }

    public void stop() {
        source.stop(new MyCompletionHandler() {
            @Override
            public void onSucceed() {
                Log.d(TAG, "stop onSucceed");
            }

            @Override
            public void onFailed(int errCode, String description) {
                Log.d(TAG, "stop onFailed: " + errCode + " reason: " + description);
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
        Log.d(TAG, "onSessionDestroy: " + sessionId);
    }
}
