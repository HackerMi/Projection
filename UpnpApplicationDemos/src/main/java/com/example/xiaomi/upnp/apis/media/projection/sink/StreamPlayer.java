package com.example.xiaomi.upnp.apis.media.projection.sink;

import android.widget.VideoView;

import com.example.xiaomi.upnp.apis.media.projection.sink.device.host.StreamTransport;

/**
 * Created by wangwei_b on 12/16/15.
 */
public class StreamPlayer {
    private VideoView videoView;
    private int playerID;
    private StreamTransport.CurrentMediaCategory mediaCategory;

    public StreamPlayer(int playerID, VideoView videoView) {
        this.videoView = videoView;
        this.playerID = playerID;
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

    public void setURI(String resPath) {
        videoView.setVideoPath(resPath);
    }

    public void play() {
        videoView.start();
    }

    public void stop() {
        videoView.stopPlayback();
    }
}
