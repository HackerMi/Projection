package com.xiaomi.upnp.examples.projection.source.feature.projection;

import android.util.Log;

import com.xiaomi.upnp.examples.projection.source.device.sink.control.SessionManager;
import com.xiaomi.upnp.examples.projection.source.device.sink.control.SinkDevice;
import com.xiaomi.upnp.examples.projection.source.device.sink.control.StreamTransport;
import com.xiaomi.upnp.examples.projection.source.device.source.control.MediaProjection;
import com.xiaomi.upnp.examples.projection.source.device.source.control.SourceDevice;

import upnp.typedef.error.UpnpError;
import upnp.typedef.exception.UpnpException;
import upnp.typedef.session.ServiceInstanceIDs;
import upnp.typedef.session.SessionCapabilities;

/**
 * Created by wangwei_b on 12/21/15.
 */
public class ProjectionControllerImpl {
    private static final String TAG = ProjectionControllerImpl.class.getSimpleName();
    public static final int CREATE_SESSION_TIMEOUT = 1000 * 20;
    public static final int DESTORY_SESSION_TIMEOUT = 1000 * 20;
    public static final int START_PROJECTION_TIMEOUT = 1000 * 20;
    public static final int STOP_PROJECTION_TIMEOUT = 1000 * 20;
    private final ProjectionContext context = new ProjectionContext();

    private class ProjectionContext {
        boolean sessionCreated;
        boolean projectionStarted;
        long streamTransportID;
        long mediaProjectionID;
        String sinkSessionID;
        String sourceSessionID;
        String transportURI;
        SessionCapabilities capabilities;

        public void clear() {
            sessionCreated = false;
            projectionStarted = false;
            streamTransportID = -1;
            mediaProjectionID = -1;
            sinkSessionID = null;
            sourceSessionID = null;
            transportURI = null;
            capabilities = null;
        }
    }

    public ProjectionControllerImpl() {
        context.clear();
    }

    public synchronized boolean doCreateSession(SourceDevice source, SinkDevice sink) {
        long start = System.currentTimeMillis();

        context.clear();

        do {
            try {
                sink.getSessionManager().GetSessionCapabilities(new SessionManager.GetSessionCapabilities_CompletedHandler() {
                    @Override
                    public void onSucceed(String theSource, String theSink) {
                        synchronized (context) {
                            context.capabilities = new SessionCapabilities(theSink);
                            context.notify();
                        }
                        Log.d(TAG, "Sink SessionCapabilities: " + context.capabilities);
                    }

                    @Override
                    public void onFailed(UpnpError error) {
                        Log.d(TAG, "GetSessionCapabilities onFailed: " + error);
                        synchronized (context) {
                            context.notify();
                        }
                    }
                });
            } catch (UpnpException e) {
                e.printStackTrace();
                break;
            }

            synchronized (context) {
                try {
                    context.wait(CREATE_SESSION_TIMEOUT);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }

            if (context.capabilities == null) {
                Log.e(TAG, "sink capabilities is invalid!");
                break;
            }

            try {
                source.getSessionManager().GetSessionCapabilities(new com.xiaomi.upnp.examples.projection.source.device.source.control.SessionManager.GetSessionCapabilities_CompletedHandler() {
                    @Override
                    public void onSucceed(String theSource, String theSink) {
                        SessionCapabilities sourceCaps = new SessionCapabilities(theSource);
                        Log.d(TAG, "AVServer SessionCapabilities: " + sourceCaps);

                        synchronized (context) {
                            context.capabilities = context.capabilities.getIntersection(sourceCaps);
                            context.notify();
                        }

                        Log.e(TAG, "InterSection: " + context.capabilities);
                    }

                    @Override
                    public void onFailed(UpnpError error) {
                        Log.d(TAG, "GetSessionCapabilities onFailed: " + error);
                        synchronized (context) {
                            context.notify();
                        }
                    }
                });
            } catch (UpnpException e) {
                e.printStackTrace();
                break;
            }

            synchronized (context) {
                try {
                    context.wait(CREATE_SESSION_TIMEOUT);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }

            if (context.capabilities == null) {
                Log.e(TAG, "capabilities intersection is invalid!");
                break;
            }

            try {
                sink.getSessionManager().PrepareForSession(context.capabilities.toString(),
                        "",
                        SessionManager.A_ARG_TYPE_Direction.V_Input,
                        new SessionManager.PrepareForSession_CompletedHandler() {

                            @Override
                            public void onSucceed(String theSessionID, String theAddress, String theServiceInstanceIDs) {
                                Log.e(TAG, "Sink PrepareForSession onSucceed");
                                Log.d(TAG, "sessionId: " + theSessionID);
                                Log.d(TAG, "address: " + theAddress);
                                Log.d(TAG, "theServiceInstanceIDs: " + theServiceInstanceIDs);

                                synchronized (context) {
                                    context.sinkSessionID = theSessionID;
                                    ServiceInstanceIDs instanceIDs = new ServiceInstanceIDs();
                                    if (instanceIDs.parse(theServiceInstanceIDs)) {
                                        context.streamTransportID = instanceIDs.getInstanceId(SinkDevice.SERVICE_StreamTransport);
                                        Log.d(TAG, "sink streamTransportID: " + context.streamTransportID);
                                    }
                                    context.notify();
                                }
                            }

                            @Override
                            public void onFailed(UpnpError error) {
                                Log.d(TAG, "[Remote] Sink PrepareForSession onFailed: " + error);

                                synchronized (context) {
                                    context.sessionCreated = false;
                                    context.notify();
                                }
                            }
                        }
                );
            } catch (UpnpException e) {
                e.printStackTrace();
                break;
            }

            synchronized (context) {
                try {
                    context.wait(CREATE_SESSION_TIMEOUT);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }

            if (context.sinkSessionID == null) {
                Log.e(TAG, "sink session ID is invalid!");
                break;
            }

            try {
                source.getSessionManager().PrepareForSession(context.capabilities.toString(),
                        context.sinkSessionID,
                        com.xiaomi.upnp.examples.projection.source.device.source.control.SessionManager.A_ARG_TYPE_Direction.V_Output,
                        new com.xiaomi.upnp.examples.projection.source.device.source.control.SessionManager.PrepareForSession_CompletedHandler() {

                            @Override
                            public void onSucceed(String theSessionID, String theAddress, String theServiceInstanceIDs) {
                                Log.e(TAG, "Source PrepareForSession onSucceed");
                                Log.d(TAG, "sessionId: " + theSessionID);
                                Log.d(TAG, "address: " + theAddress);
                                Log.d(TAG, "theServiceInstanceIDs: " + theServiceInstanceIDs);

                                synchronized (context) {
                                    context.sourceSessionID = theSessionID;
                                    ServiceInstanceIDs instanceIDs = new ServiceInstanceIDs();
                                    if (instanceIDs.parse(theServiceInstanceIDs)) {
                                        context.mediaProjectionID = instanceIDs.getInstanceId(SourceDevice.SERVICE_MediaProjection);
                                        Log.d(TAG, "source mediaProjectionID: " + context.mediaProjectionID);
                                    }
                                    context.sessionCreated = true;
                                    context.notify();
                                }
                            }

                            @Override
                            public void onFailed(UpnpError error) {
                                Log.d(TAG, "[Remote] Source PrepareForSession onFailed: " + error);
                                synchronized (context) {
                                    context.sessionCreated = false;
                                    context.notify();
                                }
                            }
                        });
            } catch (UpnpException e) {
                e.printStackTrace();
                break;
            }

            synchronized (context) {
                try {
                    context.wait(CREATE_SESSION_TIMEOUT);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }
        } while (false);

        long end = System.currentTimeMillis();
        Log.e(TAG, String.format("doCreateSession -> %s [%d ms]", context.sessionCreated ? "created" : "created failed", end - start));

        return context.sessionCreated;
    }

    public synchronized boolean doDestroySession(SourceDevice source, SinkDevice sink) {
        long start = System.currentTimeMillis();

        if (context.sinkSessionID == null || context.sourceSessionID == null || !context.sessionCreated) {
            Log.e(TAG, "session id is invalid, can't destory session.");
            return false;
        }

        do {
            try {
                sink.getSessionManager().SessionComplete(context.sinkSessionID, new SessionManager.SessionComplete_CompletedHandler() {
                    @Override
                    public void onSucceed() {
                        synchronized (context) {
                            context.sinkSessionID = null;
                            context.notify();
                        }
                    }

                    @Override
                    public void onFailed(UpnpError error) {
                        Log.d(TAG, "[Remote] Sink SessionComplete onFailed: " + error);
                        synchronized (context) {
                            context.notify();
                        }
                    }
                });
            } catch (UpnpException e) {
                e.printStackTrace();
                break;
            }

            synchronized (context) {
                try {
                    context.wait(DESTORY_SESSION_TIMEOUT);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }

            try {
                source.getSessionManager().SessionComplete(context.sourceSessionID, new com.xiaomi.upnp.examples.projection.source.device.source.control.SessionManager.SessionComplete_CompletedHandler() {
                    @Override
                    public void onSucceed() {
                        synchronized (context) {
                            context.sourceSessionID = null;
                            context.sessionCreated = false;
                            context.notify();
                        }
                    }

                    @Override
                    public void onFailed(UpnpError error) {
                        Log.d(TAG, "[Remote] Source SessionComplete onFailed: " + error);
                        synchronized (context) {
                            context.notify();
                        }
                    }
                });
            } catch (UpnpException e) {
                e.printStackTrace();
                break;
            }

            synchronized (context) {
                try {
                    context.wait(DESTORY_SESSION_TIMEOUT);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }
        } while (false);

        long end = System.currentTimeMillis();
        Log.e(TAG, String.format("doDestroySession -> %s [%d ms]", !context.sessionCreated ? "destoried" : "destory failed", end - start));

        return !context.sessionCreated;
    }

    public synchronized boolean doStartProjection(SourceDevice source, SinkDevice sink) {
        if (!context.sessionCreated) {
            Log.e(TAG, "session is not ready");
            return false;
        }

        if (context.projectionStarted) {
            Log.e(TAG, "projection is started");
            return true;
        }

        long start = System.currentTimeMillis();

        do {
            try {
                source.getMediaProjection().Start(context.mediaProjectionID, new MediaProjection.Start_CompletedHandler() {
                    @Override
                    public void onSucceed() {
                        synchronized (context) {
                            context.notify();
                        }
                    }

                    @Override
                    public void onFailed(UpnpError error) {
                        Log.d(TAG, "[Remote] Source Start onFailed: " + error);
                        synchronized (context) {
                            context.notify();
                        }
                    }
                });
            } catch (UpnpException e) {
                e.printStackTrace();
                break;
            }

            synchronized (context) {
                try {
                    context.wait(START_PROJECTION_TIMEOUT);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }

            try {
                source.getMediaProjection().GetTransportURI(context.mediaProjectionID, new MediaProjection.GetTransportURI_CompletedHandler() {
                    @Override
                    public void onSucceed(String theCurrentURI) {
                        synchronized (context) {
                            Log.e(TAG, "source transport URI: " + theCurrentURI);
                            context.transportURI = theCurrentURI;
                            context.notify();
                        }
                    }

                    @Override
                    public void onFailed(UpnpError error) {
                        Log.d(TAG, "[Remote] Source GetTransportURI onFailed: " + error);
                        synchronized (context) {
                            context.notify();
                        }
                    }
                });
            } catch (UpnpException e) {
                e.printStackTrace();
                break;
            }

            synchronized (context) {
                try {
                    context.wait(START_PROJECTION_TIMEOUT);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }

            try {
                sink.getStreamTransport().SetAVTransportURI(context.streamTransportID, context.transportURI, " ", new StreamTransport.SetAVTransportURI_CompletedHandler() {
                    @Override
                    public void onSucceed() {
                        synchronized (context) {
                            context.notify();
                        }
                    }

                    @Override
                    public void onFailed(UpnpError error) {
                        Log.d(TAG, "[Remote] Sink SetAVTransportURI onFailed: " + error);
                        synchronized (context) {
                            context.notify();
                        }
                    }
                });
            } catch (UpnpException e) {
                e.printStackTrace();
                break;
            }

            synchronized (context) {
                try {
                    context.wait(START_PROJECTION_TIMEOUT);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }


            try {
                sink.getStreamTransport().Play(context.streamTransportID, StreamTransport.TransportPlaySpeed.V_1, new StreamTransport.Play_CompletedHandler() {
                    @Override
                    public void onSucceed() {
                        synchronized (context) {
                            context.projectionStarted = true;
                            context.notify();
                        }
                    }

                    @Override
                    public void onFailed(UpnpError error) {
                        Log.d(TAG, "[Remote] Sink Play onFailed: " + error);
                        synchronized (context) {
                            context.notify();
                        }
                    }
                });
            } catch (UpnpException e) {
                e.printStackTrace();
                break;
            }

            synchronized (context) {
                try {
                    context.wait(START_PROJECTION_TIMEOUT);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }

        } while(false);

        long end = System.currentTimeMillis();
        Log.e(TAG, String.format("doStartProjection -> %s [%d ms]", context.projectionStarted ? "started" : "start failed", end - start));

        return context.projectionStarted;
    }

    public synchronized boolean doStopProjection(SourceDevice source, SinkDevice sink) {
        if (!context.sessionCreated) {
            Log.e(TAG, "session is not ready");
            return false;
        }

        if (!context.projectionStarted) {
            Log.e(TAG, "projection is stopped");
            return true;
        }

        long start = System.currentTimeMillis();

        do {
            try {
                sink.getStreamTransport().Stop(context.streamTransportID, new StreamTransport.Stop_CompletedHandler() {
                    @Override
                    public void onSucceed() {
                        synchronized (context) {
                            context.notify();
                        }
                    }

                    @Override
                    public void onFailed(UpnpError error) {
                        Log.d(TAG, "[Remote] Sink Stop onFailed: " + error);
                        synchronized (context) {
                            context.notify();
                        }
                    }
                });
            } catch (UpnpException e) {
                e.printStackTrace();
                break;
            }

            synchronized (context) {
                try {
                    context.wait(STOP_PROJECTION_TIMEOUT);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }

            try {
                source.getMediaProjection().Stop(context.mediaProjectionID, new MediaProjection.Stop_CompletedHandler() {
                    @Override
                    public void onSucceed() {
                        synchronized (context) {
                            context.notify();
                        }
                    }

                    @Override
                    public void onFailed(UpnpError error) {
                        Log.d(TAG, "[Remote] Source Stop onFailed: " + error);
                        synchronized (context) {
                            context.notify();
                        }
                    }
                });
            } catch (UpnpException e) {
                e.printStackTrace();
                break;
            }

            synchronized (context) {
                try {
                    context.wait(STOP_PROJECTION_TIMEOUT);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }

        } while(false);

        long end = System.currentTimeMillis();
        Log.e(TAG, String.format("doStopProjection -> %s [%d ms]", !context.projectionStarted ? "stopped" : "stop failed", end - start));

        return !context.projectionStarted;
    }
}
