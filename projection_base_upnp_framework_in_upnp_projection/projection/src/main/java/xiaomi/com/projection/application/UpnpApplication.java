package xiaomi.com.projection.application;

import android.app.Application;
import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;

import upnp.typedef.ErrorCode;
import upnp.typedef.device.urn.DeviceType;
import upnp.typedef.deviceclass.DeviceClass;
import upnps.api.manager.UpnpManager;
import xiaomi.com.projection.device.control.MediaDevice;

public class UpnpApplication extends Application {

    private static final String TAG = UpnpApplication.class.getSimpleName();
    private static UpnpApplication instance;
    private boolean isInitialized = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.e(TAG, "UpnpApplication onCreate enter");
        instance = this;

        UpnpManager.getInstance().initialize(this);

        new OpenTask().execute();
        Log.e(TAG, "UpnpApplication onCreate execute");
    }

    @Override
    public void onTerminate() {
        new CloseTask().execute();
        Log.e(TAG, "UpnpApplication onTerminate !!");
        super.onTerminate();
    }

    public static UpnpApplication getAppContext() {
        return instance;
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    private class OpenTask extends AsyncTask<Void, Void, Integer> {
        @Override
        protected Integer doInBackground(Void... params) {
            Log.e(TAG, "open...");
            return UpnpManager.getInstance().open();
        }

        @Override
        protected void onPostExecute(Integer integer) {
            super.onPostExecute(integer);

            if (integer == ErrorCode.OK) {
                Log.e(TAG, "UpnpManager open OK");

                UpnpManager.getClassProvider().addDeviceClass(new DeviceClass(new DeviceType("MediaDevice", "1"), MediaDevice.class));

                isInitialized = true;
                Intent intent = new Intent(AppConstants.UPNP_INIT_SUCCEED);
                sendBroadcast(intent);
            } else {
                Log.e(TAG, "UpnpManager open Failed: " + integer);
                Intent intent = new Intent(AppConstants.UPNP_INIT_FAILED);
                sendBroadcast(intent);
            }
        }
    }

    private class CloseTask extends AsyncTask<Void, Void, Integer> {
        @Override
        protected Integer doInBackground(Void... params) {
            Log.e(TAG, "close...");

            return UpnpManager.getInstance().close();
        }

        @Override
        protected void onPostExecute(Integer integer) {
            super.onPostExecute(integer);

            if (integer == ErrorCode.OK) {
                Log.e(TAG, "UpnpManager close OK");
            } else {
                Log.e(TAG, "UpnpManager close Failed: " + integer);
            }
        }
    }
}
