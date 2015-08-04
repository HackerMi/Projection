/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.accessorydisplay.source;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Display;
import android.view.Surface;

import com.android.accessorydisplay.common.Protocol;
import com.android.accessorydisplay.common.Service;
import com.android.accessorydisplay.common.Transport;
import java.nio.ByteBuffer;

public class DisplaySourceService extends Service {
    private static final int MSG_DISPATCH_DISPLAY_ADDED = 1;
    private static final int MSG_DISPATCH_DISPLAY_REMOVED = 2;

    private static final String DISPLAY_NAME = "Accessory Display";
    private static final int BIT_RATE = 6000000;
    private static final int FRAME_RATE = 30;
    private static final int I_FRAME_INTERVAL = 10;

    private final Callbacks mCallbacks;
    private final ServiceHandler mHandler;
    private final DisplayManager mDisplayManager;

    private boolean mSinkAvailable;
    private int mSinkWidth;
    private int mSinkHeight;
    private int mSinkDensityDpi;

    private VirtualDisplayThread mVirtualDisplayThread;
    private AudioRecordThread mAudioRecordThread;
    private static String TAG = "DisplaySourceService";
    
    public DisplaySourceService(Context context, Transport transport, Callbacks callbacks) {
        super(context, transport, Protocol.DisplaySourceService.ID);
        mCallbacks = callbacks;
        mHandler = new ServiceHandler();
        mDisplayManager = (DisplayManager)context.getSystemService(Context.DISPLAY_SERVICE);
    }

    @Override
    public void start() {
        super.start();

        getLogger().log("Sending MSG_QUERY.");
        getTransport().sendMessage(Protocol.DisplaySinkService.ID,
                Protocol.DisplaySinkService.MSG_QUERY, null);
    }

    @Override
    public void stop() {
        super.stop();
        handleSinkNotAvailable();
    }

    @Override
    public void onMessageReceived(int service, int what, ByteBuffer content) {
        switch (what) {
            case Protocol.DisplaySourceService.MSG_SINK_AVAILABLE: {
                getLogger().log("Received MSG_SINK_AVAILABLE");
                if (content.remaining() >= 12) {
                    final int width = content.getInt();
                    final int height = content.getInt();
                    final int densityDpi = content.getInt();
                    if (width >= 0 && width <= 4096
                            && height >= 0 && height <= 4096
                            && densityDpi >= 60 && densityDpi <= 640) {
                        handleSinkAvailable(width, height, densityDpi);
                        return;
                    }
                }
                getLogger().log("Receive invalid MSG_SINK_AVAILABLE message.");
                break;
            }

            case Protocol.DisplaySourceService.MSG_SINK_NOT_AVAILABLE: {
                getLogger().log("Received MSG_SINK_NOT_AVAILABLE");
                handleSinkNotAvailable();
                break;
            }
        }
    }

    private void handleSinkAvailable(int width, int height, int densityDpi) {
        if (mSinkAvailable && mSinkWidth == width && mSinkHeight == height
                && mSinkDensityDpi == densityDpi) {
            return;
        }

        getLogger().log("Accessory display sink available: "
                + "width=" + width + ", height=" + height
                + ", densityDpi=" + densityDpi);
        mSinkAvailable = true;
        mSinkWidth = width;
        mSinkHeight = height;
        mSinkDensityDpi = densityDpi;
        createVirtualDisplay();
        createAudioRecord();
    }

    private void handleSinkNotAvailable() {
        getLogger().log("Accessory display sink not available.");

        mSinkAvailable = false;
        mSinkWidth = 0;
        mSinkHeight = 0;
        mSinkDensityDpi = 0;
        releaseVirtualDisplay();
        releaseAudioRecord();
    }

    private void createVirtualDisplay() {
        releaseVirtualDisplay();

        mVirtualDisplayThread = new VirtualDisplayThread(
                mSinkWidth, mSinkHeight, mSinkDensityDpi);
        mVirtualDisplayThread.start();
    }

    private void releaseVirtualDisplay() {
        if (mVirtualDisplayThread != null) {
            mVirtualDisplayThread.quit();
            mVirtualDisplayThread = null;
        }
    }

    private void createAudioRecord() {
        releaseAudioRecord();

        mAudioRecordThread = new AudioRecordThread();
        mAudioRecordThread.start();
    }

    private void releaseAudioRecord() {
        if (mAudioRecordThread != null) {
            mAudioRecordThread.quit();
            mAudioRecordThread = null;
        }
    }
    public interface Callbacks {
        public void onDisplayAdded(Display display);
        public void onDisplayRemoved(Display display);
    }

    private final class ServiceHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_DISPATCH_DISPLAY_ADDED: {
                    mCallbacks.onDisplayAdded((Display)msg.obj);
                    break;
                }

                case MSG_DISPATCH_DISPLAY_REMOVED: {
                    mCallbacks.onDisplayRemoved((Display)msg.obj);
                    break;
                }
            }
        }
    }

    private final class VirtualDisplayThread extends Thread {
        private static final int TIMEOUT_USEC = 1000000;

        private final int mWidth;
        private final int mHeight;
        private final int mDensityDpi;

        private volatile boolean mQuitting;

        public VirtualDisplayThread(int width, int height, int densityDpi) {
            mWidth = width;
            mHeight = height;
            mDensityDpi = densityDpi;
        }

        @Override
        public void run() {
            MediaFormat format = MediaFormat.createVideoFormat("video/avc", mWidth, mHeight);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);
            MediaCodec codec;
            codec = MediaCodec.createEncoderByType("video/avc");
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            Surface surface = codec.createInputSurface();
            codec.start();

            VirtualDisplay virtualDisplay = mDisplayManager.createVirtualDisplay(
                    DISPLAY_NAME, mWidth, mHeight, mDensityDpi, surface, 0);
            if (virtualDisplay != null) {
                mHandler.obtainMessage(MSG_DISPATCH_DISPLAY_ADDED,
                        virtualDisplay.getDisplay()).sendToTarget();

                stream(codec);

                mHandler.obtainMessage(MSG_DISPATCH_DISPLAY_REMOVED,
                        virtualDisplay.getDisplay()).sendToTarget();
                virtualDisplay.release();
            }

            codec.signalEndOfInputStream();
            codec.stop();
        }

        public void quit() {
            mQuitting = true;
        }

        private void stream(MediaCodec codec) {
            BufferInfo info = new BufferInfo();
            ByteBuffer[] buffers = null;
            while (!mQuitting) {
                int index = codec.dequeueOutputBuffer(info, TIMEOUT_USEC);
                if (index >= 0) {
                    if (buffers == null) {
                        buffers = codec.getOutputBuffers();
                    }

                    ByteBuffer buffer = buffers[index];
                    buffer.limit(info.offset + info.size);
                    buffer.position(info.offset);

                    getTransport().sendMessage(Protocol.DisplaySinkService.ID,
                            Protocol.DisplaySinkService.MSG_CONTENT, buffer);
                    codec.releaseOutputBuffer(index, false);
                } else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    buffers = null;
                } else if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    getLogger().log("Codec dequeue buffer timed out.");
                }
            }
        }
    }

    private final class AudioRecordThread extends Thread {
        private final int kSampleRate = 48000;
        private final int kChannelMode = AudioFormat.CHANNEL_IN_STEREO;
        private final int kEncodeFormat = AudioFormat.ENCODING_PCM_16BIT;
        private int kFrameSize = 2048;
        private String filePath = "/sdcard/voice.pcm";
        private boolean isRecording = false;

        @Override
        public void run() {
            // 根据/system/etc/audio_policy.conf配置AudioRecord
            int minBufferSize = AudioRecord.getMinBufferSize(kSampleRate, kChannelMode,
                    kEncodeFormat);
            AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.REMOTE_SUBMIX,
                    kSampleRate, kChannelMode, kEncodeFormat, minBufferSize * 2);
            kFrameSize = minBufferSize * 2;
            isRecording = true;

            recorder.startRecording();
            byte[] buffer = new byte[kFrameSize];
            int num = 0;
            while (isRecording) {
                num = recorder.read(buffer, 0, kFrameSize);
                Log.d(TAG, "buffer = " + buffer.toString() + ", num = " + num);
                ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
                byteBuffer.position(0);
                byteBuffer.limit(num);
                getTransport().sendMessage(Protocol.DisplaySinkService.ID,
                        Protocol.DisplaySinkService.MSG_AUDIO, byteBuffer);
            }
            recorder.stop();
            recorder.release();
            recorder = null;
            Log.d(TAG, "clean up");
        }

        public void quit() {
            isRecording = false;
        }
    }
}
