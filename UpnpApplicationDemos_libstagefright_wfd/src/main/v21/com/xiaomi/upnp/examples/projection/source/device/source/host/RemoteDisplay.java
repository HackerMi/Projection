package com.xiaomi.upnp.examples.projection.source.device.source.host;

import android.os.Handler;
import android.util.Log;
import android.view.Surface;

/**
 * Created by wangwei_b on 1/14/16.
 */
public final class RemoteDisplay {
    /* these constants must be kept in sync with IRemoteDisplayClient.h */
    private static final String TAG = RemoteDisplay.class.getSimpleName();
    public static final int DISPLAY_FLAG_SECURE = 1 << 0;

    public static final int DISPLAY_ERROR_UNKOWN = 1;
    public static final int DISPLAY_ERROR_CONNECTION_DROPPED = 2;

    private final CloseGuard mGuard = CloseGuard.get();
    private final Listener mListener;
    private final Handler mHandler;

    private long mPtr;

    private native long nativeListen(String iface);
    private native void nativeDispose(long ptr);
    private native void nativePause(long ptr);
    private native void nativeResume(long ptr);

    static {
        try {
//            System.loadLibrary("wfd_jni");
            System.loadLibrary("RemoteDisplaySource");
        } catch (Exception e) {
            Log.e(TAG, "load failed", e);
        }
        Log.v(TAG, "load RemoteDisplaySource sucess");
//        Log.v(TAG, "load wfd_jni sucess");
    }

    private RemoteDisplay(Listener listener, Handler handler) {
        mListener = listener;
        mHandler = handler;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            dispose(true);
        } finally {
            super.finalize();
        }
    }

    /**
     * Starts listening for displays to be connected on the specified interface.
     *
     * @param iface The interface address and port in the form "x.x.x.x:y".
     * @param listener The listener to invoke when displays are connected or disconnected.
     * @param handler The handler on which to invoke the listener.
     */
    public static RemoteDisplay listen(String iface, Listener listener, Handler handler) {
        if (iface == null) {
            throw new IllegalArgumentException("iface must not be null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler must not be null");
        }

        RemoteDisplay display = new RemoteDisplay(listener, handler);
        display.startListening(iface);
        return display;
    }

    /**
     * Disconnects the remote display and stops listening for new connections.
     */
    public void dispose() {
        dispose(false);
    }

    public void pause() {
        nativePause(mPtr);
    }

    public void resume() {
        nativeResume(mPtr);
    }

    private void dispose(boolean finalized) {
        if (mPtr != 0) {
            if (mGuard != null) {
                if (finalized) {
                    mGuard.warnIfOpen();
                } else {
                    mGuard.close();
                }
            }

            nativeDispose(mPtr);
            mPtr = 0;
        }
    }

    private void startListening(String iface) {
        Log.e("@@@@", "new RemoteDisplay startListening");
        mPtr = nativeListen(iface);
        if (mPtr == 0) {
            throw new IllegalStateException("Could not start listening for "
                    + "remote display connection on \"" + iface + "\"");
        }
        mGuard.open("dispose");
    }

    // Called from native.
    private void notifyDisplayConnected(final Surface surface,
                                        final int width, final int height, final int flags, final int session) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onDisplayConnected(surface, width, height, flags, session);
            }
        });
    }

    // Called from native.
    private void notifyDisplayDisconnected() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onDisplayDisconnected();
            }
        });
    }

    // Called from native.
    private void notifyDisplayError(final int error) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onDisplayError(error);
            }
        });
    }

    /**
     * Listener invoked when the remote display connection changes state.
     */
    public interface Listener {
        void onDisplayConnected(Surface surface,
                                int width, int height, int flags, int session);
        void onDisplayDisconnected();
        void onDisplayError(int error);
    }
}

