package com.xiaomi.upnp.examples.projection.source.feature.projection;

import android.util.Log;

import com.xiaomi.upnp.examples.projection.source.device.sink.control.SinkDevice;
import com.xiaomi.upnp.examples.projection.source.device.source.control.SourceDevice;

/**
 * Created by wangwei_b on 12/21/15.
 */
public class ProjectionController {
    private static final String TAG = ProjectionController.class.getSimpleName();
    private static final Object classLock = ProjectionController.class;
    private static ProjectionController instance = null;
    private com.xiaomi.upnp.examples.projection.source.feature.projection.ProjectionControllerImpl impl = null;

    public static ProjectionController getInstance() {
        synchronized (classLock) {
            if (instance == null) {
                instance = new ProjectionController();
            }

            return instance;
        }
    }

    private ProjectionController() {
        impl = new com.xiaomi.upnp.examples.projection.source.feature.projection.ProjectionControllerImpl();
    }

    public synchronized boolean doStart(SourceDevice source, SinkDevice sink) {
        if (source == null || sink == null) {
            Log.e(TAG, "doStart param is invalid");
            return false;
        }

        boolean ret = false;
        do {
            ret = impl.doCreateSession(source, sink);
            if (!ret) {
                Log.e(TAG, "doStart -> doCreateSession failed");
                break;
            }

            ret = impl.doStartProjection(source, sink);
            if (!ret) {
                Log.e(TAG, "doStart -> doStartProjection failed");
                break;
            }

        } while (false);

        return ret;
    }

    public synchronized boolean doStop(SourceDevice source, SinkDevice sink) {
        if (source == null || sink == null) {
            Log.e(TAG, "doStop param is invalid");
            return false;
        }

        boolean ret = false;
        do {
            ret = impl.doStopProjection(source, sink);
            if (!ret) {
                Log.e(TAG, "doStop -> doStopProjection failed");
                break;
            }

            ret = impl.doDestroySession(source, sink);
            if (!ret) {
                Log.e(TAG, "doStop -> doDestroySession failed");
                break;
            }

        } while (false);

        return ret;
    }
}
