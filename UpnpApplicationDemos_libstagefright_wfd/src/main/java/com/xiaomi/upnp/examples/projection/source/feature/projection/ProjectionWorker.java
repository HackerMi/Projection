package com.xiaomi.upnp.examples.projection.source.feature.projection;

import android.content.Context;
import android.util.Log;

import com.xiaomi.upnp.examples.projection.source.device.sink.control.SinkDevice;
import com.xiaomi.upnp.examples.projection.source.device.source.control.SourceDevice;

import upnp.worker.Job;
import upnp.worker.Worker;


/**
 * Created by wangwei_b on 12/31/15.
 */
public class ProjectionWorker extends Worker {

    private String TAG = ProjectionWorker.class.getSimpleName();
    private ProjectionController controller;
    private SinkDevice curSink;
    private SinkDevice preSink;

    public ProjectionWorker(Context context) {
        super(context);
    }

    @Override
    public void initialize() {
        controller = ProjectionController.getInstance();
        curSink = null;
        preSink = null;
    }

    @Override
    public void destroy() {
        controller = null;
    }

    @Override
    public void execute(Job job) {
        boolean ret;
        ProjectionJob j = (ProjectionJob)job;

        do {
            curSink = j.getSink();
            SourceDevice source = j.getSource();

            if (source == null) {
                Log.e(TAG, "Source device can't be null");
                return;
            }

            if (preSink == curSink) {
                Log.e(TAG, "Duplicate operation does not proceed");
                return;
            }

            /**
             * 1 - switch sink device
             */
            if (preSink != null && curSink != null) {
                ret = controller.doStop(source, preSink);
                Log.v(TAG, String.format("[Switch] Stop Sink Device [%s] %s ", preSink.device.getAddress(), ret ? "succeed" : "failed"));

                ret = controller.doStart(source, curSink);
                Log.v(TAG, String.format("[Switch] Start Sink Device [%s] %s ", curSink.device.getAddress(), ret ? "succeed" : "failed"));
                break;
            }

            /**
             * 2 - start current sink devices
             */
            if (preSink == null && curSink != null) {
                ret = controller.doStart(source, curSink);
                Log.v(TAG, String.format("Start Sink Device [%s] %s ", curSink.device.getAddress(), ret ? "succeed" : "failed"));
                break;
            }

            /**
             * 3 - stop previous sink devices
             */
            if (preSink != null && curSink == null) {
                ret = controller.doStop(source, preSink);
                Log.v(TAG, String.format("Stop Sink Device [%s] %s ", preSink.device.getAddress(), ret ? "succeed" : "failed"));
                break;
            }
        } while (false);

        preSink = curSink;
    }
}
