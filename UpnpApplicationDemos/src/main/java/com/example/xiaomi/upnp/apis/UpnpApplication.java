package com.example.xiaomi.upnp.apis;

import android.app.Application;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import upnp.typedef.ErrorCode;
import upnps.api.manager.UpnpManager;

public class UpnpApplication extends Application {

    private static final String TAG = UpnpApplication.class.getSimpleName();
    private static UpnpApplication instance;
    private boolean isInitialized;
    private LocalBroadcastManager broadcastManager;

    @Override
    public void onCreate() {
        super.onCreate();

        instance = this;

        broadcastManager = LocalBroadcastManager.getInstance(this);
        UpnpManager.getInstance().initialize(this);

        new OpenTask().execute();
    }

    @Override
    public void onTerminate() {
        new CloseTask().execute();

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

                isInitialized = true;
                Intent intent = new Intent(AppConstants.UPNP_INIT_SUCCEED);
                broadcastManager.sendBroadcast(intent);
            } else {
                Log.e(TAG, "UpnpManager open Failed: " + integer);
                Intent intent = new Intent(AppConstants.UPNP_INIT_FAILED);
                broadcastManager.sendBroadcast(intent);
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
