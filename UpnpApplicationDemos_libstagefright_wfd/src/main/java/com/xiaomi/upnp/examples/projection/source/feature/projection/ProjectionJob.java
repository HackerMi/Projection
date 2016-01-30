package com.xiaomi.upnp.examples.projection.source.feature.projection;

import android.os.Handler;

import com.xiaomi.upnp.examples.projection.source.device.sink.control.SinkDevice;
import com.xiaomi.upnp.examples.projection.source.device.source.control.SourceDevice;

import upnp.worker.Job;


/**
 * Created by wangwei_b on 12/31/15.
 */
public class ProjectionJob extends Job {

    private SourceDevice source;
    private SinkDevice sink;
    private Handler handler;

    public ProjectionJob(SourceDevice source, SinkDevice sink, Handler handler) {
        this.setSource(source);
        this.setSink(sink);
        this.setHandler(handler);
    }

    public SinkDevice getSink() {
        return sink;
    }

    public void setSink(SinkDevice sink) {
        this.sink = sink;
    }

    public SourceDevice getSource() {
        return source;
    }

    public void setSource(SourceDevice source) {
        this.source = source;
    }

    public Handler getHandler() {
        return handler;
    }

    public void setHandler(Handler handler) {
        this.handler = handler;
    }
}
