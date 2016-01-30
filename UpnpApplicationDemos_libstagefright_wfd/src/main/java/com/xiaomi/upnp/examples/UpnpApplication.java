package com.xiaomi.upnp.examples;

import android.app.Application;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import upnp.typedef.exception.UpnpException;
import upnps.manager.UpnpManager;

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

    private class OpenTask extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... params) {
            Log.e(TAG, "open...");

            try {
                UpnpManager.getInstance().open();
                return true;
            } catch (UpnpException e) {
                e.printStackTrace();
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean integer) {
            super.onPostExecute(integer);

            if (integer) {
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

    private class CloseTask extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... params) {
            Log.e(TAG, "close...");

            try {
                UpnpManager.getInstance().close();
                return true;
            } catch (UpnpException e) {
                e.printStackTrace();
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean integer) {
            super.onPostExecute(integer);

            if (integer) {
                Log.e(TAG, "UpnpManager close OK");
            } else {
                Log.e(TAG, "UpnpManager close Failed: " + integer);
            }
        }
    }
}
