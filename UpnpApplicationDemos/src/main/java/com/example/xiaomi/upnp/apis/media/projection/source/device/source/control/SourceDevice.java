/* Automatic generated by DeviceToClazz */

package com.example.xiaomi.upnp.apis.media.projection.source.device.source.control;

import android.os.Parcel;
import android.util.Log;

import upnps.api.manager.ctrlpoint.device.AbstractDevice;
import upnp.typedef.device.Device;
import upnp.typedef.device.Service;

public class SourceDevice extends AbstractDevice {

private static final String TAG = SourceDevice.class.getSimpleName();
    private MediaProjection _MediaProjection;
    private SessionManager _SessionManager;

    public MediaProjection getMediaProjection() {
        return _MediaProjection;
    }
    public SessionManager getSessionManager() {
        return _SessionManager;
    }

    private static final Object classLock = SourceDevice.class;
    private static final String DEVICE_TYPE = "SourceDevice";
    private static final String ID_MediaProjection = "urn:upnp-org:serviceId:MediaProjection";
    private static final String ID_SessionManager = "urn:upnp-org:serviceId:SessionManager";

    public static SourceDevice create(Device device) {
        Log.d(TAG, "create");

        synchronized (classLock) {
            SourceDevice thiz = new SourceDevice(device);
                
            do {
                if (! DEVICE_TYPE.equals(device.getDeviceType().getName())) {
                    Log.d(TAG, "deviceType invalid: " + device.getDeviceType());
                    thiz = null;
                    break;
                }

                if (! thiz.initialize()) {
                    Log.d(TAG, "initialize failed");
                    thiz = null;
                    break;
                }
            } while (false);

            return thiz;
        }
    }

    private SourceDevice(Device device) {
        this.device = device;
    }

    private boolean initialize() {
        boolean ret = false;

        do {
            Service theMediaProjection = device.getService(ID_MediaProjection);
            if (theMediaProjection == null) {
                Log.d(TAG, "service not found: " + ID_MediaProjection);
                break;
            }

            Service theSessionManager = device.getService(ID_SessionManager);
            if (theSessionManager == null) {
                Log.d(TAG, "service not found: " + ID_SessionManager);
                break;
            }

            _MediaProjection = new MediaProjection(theMediaProjection);
            _SessionManager = new SessionManager(theSessionManager);

            ret = true;
        } while (false);

        return ret;
    }

    public static final Creator<SourceDevice> CREATOR = new Creator<SourceDevice>() {

        @Override
        public SourceDevice createFromParcel(Parcel in) {
            return new SourceDevice(in);
        }

        @Override
        public SourceDevice[] newArray(int size) {
            return new SourceDevice[size];
         }
    };

    private SourceDevice(Parcel in) {
        readFromParcel(in);
    }

    public void readFromParcel(Parcel in) {
        device = in.readParcelable(Device.class.getClassLoader());
        initialize();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeParcelable(device, flags);
    }
}