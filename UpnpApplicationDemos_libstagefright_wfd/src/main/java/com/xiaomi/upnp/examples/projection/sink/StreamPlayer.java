package com.xiaomi.upnp.examples.projection.sink;

import android.os.Handler;
import android.widget.VideoView;

import com.xiaomi.upnp.examples.projection.sink.device.host.StreamTransport;

/**
 * Created by wangwei_b on 12/16/15.
 */
public class StreamPlayer {
    private VideoView videoView;
    private int playerID;
    private StreamTransport.CurrentMediaCategory mediaCategory;
    private Handler handler;

    public StreamPlayer(int playerID, VideoView videoView, Handler handler) {
        this.videoView = videoView;
        this.playerID = playerID;
        this.handler = handler;
    }

    public int getPlayerID(){
        return playerID;
    }

    public void setMediaCategory(StreamTransport.CurrentMediaCategory mediaCategory) {
        this.mediaCategory = mediaCategory;
    }

    public StreamTransport.CurrentMediaCategory getMediaCategory() {
        return this.mediaCategory;
    }

    public void setURI(final String resPath) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                videoView.setVideoPath(resPath);
            }
        });
    }

    public void play() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                videoView.start();
            }
        });
    }
    public void stop() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                videoView.stopPlayback();
            }
        });
    }
}
