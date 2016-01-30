package com.xiaomi.upnp.examples.projection.source.device.source.host;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

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
import upnps.manager.handler.MyCompletionHandler;
import upnps.manager.host.config.DeviceConfig;
import upnpsession.UpnpSessionListener;
import upnpsession.UpnpSessionManager;
//import android.media.RemoteDisplay;

public class SourceDeviceImpl implements UpnpSessionListener {

    private static final String TAG = SourceDeviceImpl.class.getSimpleName();
    private static final Object classLock = SourceDeviceImpl.class;
    private static final int SINGLE_SOURCE_INSTANCE_ID = 0; //Phone only supports single source instance.
    private static final int DEFAULT_CONTROL_PORT = 7236; //Porting from WifiDisplayController.java in android framework source code.
    private static SourceDeviceImpl instance = null;
    private SourceDevice source;
    private SessionInfo info;
    private ServiceInstanceIDs instanceIDs;
    private String localAddr;
    private Context context;
    private DisplayManager displayManager;
    private Handler handler;
    private RemoteDisplay remoteDisplay;

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

    public void initialize (Context c, Handler h) throws UpnpException {

        UpnpSessionManager.getInstance().initialize(c);

        this.context = c;
        this.localAddr = null;
        this.handler = h;

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
            public UpnpError onGetSessionCapabilities(SessionManager.GetSessionCapabilities_Result result) {
                Log.d(TAG, "onGetSessionCapabilities");

                SessionCapabilities capabilities = new SessionCapabilities();
                capabilities.add(new SessionCapability(ChannelType.LAN, ProtocolType.HTTP, MediaFormat.VIDEO_ALL, null));
                capabilities.add(new SessionCapability(ChannelType.LAN, ProtocolType.RTSP, MediaFormat.VIDEO_ALL, null));
                capabilities.add(new SessionCapability(ChannelType.LAN, ProtocolType.WFD, MediaFormat.VIDEO_ALL, null));
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
            public UpnpError onPrepareForSession(String theRemoteCapabilityInfo, String thePeerSessionID, SessionManager.A_ARG_TYPE_Direction theDirection, SessionManager.PrepareForSession_Result result) {
                Log.d(TAG, "onPrepareForSession: " + thePeerSessionID);

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
                        UpnpSessionManager.getInstance().doCreateSession(info, SourceDeviceImpl.this);
                    } catch (UpnpException e) {
                        e.printStackTrace();
                        ret = UpnpError.UPNP_INTERNAL_ERROR;
                        break;
                    }

                    /**
                     * Step 3. After session created, return information about this session;
                     */
                    instanceIDs = new ServiceInstanceIDs();
                    instanceIDs.set(SourceDevice.SERVICE_MediaProjection, SINGLE_SOURCE_INSTANCE_ID);

                    result.theSessionID = info.getSessionId();
                    result.theAddress = info.getAddress();
                    result.theServiceInstanceIDs = instanceIDs.toString();
                } while (false);

                return ret;
            }

            @Override
            public UpnpError onGetCurrentSessionInfo(String theSessionID, SessionManager.GetCurrentSessionInfo_Result result) {
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
            public UpnpError onSessionComplete(String theSessionID) {
                try {
                    UpnpSessionManager.getInstance().doDestroySession(theSessionID);
                } catch (UpnpException e) {
                    e.printStackTrace();
                }
                info = null;
                return UpnpError.OK;
            }

            @Override
            public UpnpError onGetCurrentSessionIDs(SessionManager.GetCurrentSessionIDs_Result result) {
                return UpnpError.NOT_IMPLEMENTED;
            }
        });

        source.getMediaProjection().setHandler(new MediaProjection.Handler() {

            @Override
            public UpnpError onGetTransportURI(Long theInstanceID, MediaProjection.GetTransportURI_Result result) {
                if (theInstanceID != SINGLE_SOURCE_INSTANCE_ID) {
                    Log.d(TAG, "onGetTransportURI failed, instance id (" + theInstanceID + ") is invalid!");
                    return UpnpError.UPNP_ACTION_FAILED;
                }

                if (localAddr == null) {
                    Log.d(TAG, "onGetTransportURI failed, local address is invalid!");
                    return UpnpError.UPNP_ACTION_FAILED;
                }

                result.theCurrentURI = String.format("wfd://%s:%d", localAddr, DEFAULT_CONTROL_PORT);
                return UpnpError.OK;
            }

            @Override
            public UpnpError onStart(Long theInstanceID) {
                if (theInstanceID != SINGLE_SOURCE_INSTANCE_ID) {
                    Log.d(TAG, "onStart failed, instance id (" + theInstanceID + ") is invalid!");
                    return UpnpError.UPNP_ACTION_FAILED;
                }

                return startServer();
            }

            @Override
            public UpnpError onStop(Long theInstanceID) {
                if (theInstanceID != SINGLE_SOURCE_INSTANCE_ID) {
                    Log.d(TAG, "onStop failed, instance id (" + theInstanceID + ") is invalid!");
                    return UpnpError.UPNP_ACTION_FAILED;
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

    private UpnpError startServer() {
        localAddr = getLocalAddress();
        if (localAddr == null) {
            Log.d(TAG, "start server failed! can't get device address.");
            return UpnpError.UPNP_ACTION_FAILED;
        }

        Log.i(TAG, "start source Server");        
        String iface = localAddr + ":" + DEFAULT_CONTROL_PORT;

        //RemoteDisplay is hide class, the project need to complie with system image
        RemoteDisplay.Listener listener = new RemoteDisplay.Listener() {
            @Override
            public void onDisplayConnected(Surface surface,
                                           int width, int height, int flags, int session) {
                Log.i(TAG, "Connected RTSP connection with Wifi display: ");
                int densityDpi = context.getResources().getConfiguration().densityDpi;
                VirtualDisplay display = displayManager.createVirtualDisplay(
                        "Projection Source", width, height, densityDpi, surface, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR);
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
            }
        };

        remoteDisplay = RemoteDisplay.listen(iface, listener, handler);
        return UpnpError.OK;
    }

    private UpnpError stopServer() {
        Log.i(TAG, "stop source Server");
        localAddr = null;
        remoteDisplay.dispose();
        
        return UpnpError.OK;
    }

    public void start() {
        try {
            source.start(new MyCompletionHandler() {
                @Override
                public void onSucceed() {
                    Log.d(TAG, "start onSucceed");
                    displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
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

    public void stop() {
        try {
            source.stop(new MyCompletionHandler() {
                @Override
                public void onSucceed() {
                    Log.d(TAG, "stop onSucceed");
                }

                @Override
                public void onFailed(UpnpError error) {
                    Log.d(TAG, "stop onFailed: " + error);
                }
            });
        } catch (UpnpException e) {
            e.printStackTrace();
        }
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
