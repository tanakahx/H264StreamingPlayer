package com.example.tanakahx.h264streamingplayer;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

public class DecoderThread extends Thread {
    private static final String LOG_TAG = DecoderThread.class.getSimpleName();

    private static final int TIMEOUT_US = 1000;
    private boolean isCancelled;
    private int streamId;
    private Surface surface;
    private UdpStreamingReceiver receiver;

    DecoderThread(int streamId) {
        this.streamId= streamId;
    }

    void setReceiver(UdpStreamingReceiver receiver) {
        this.receiver = receiver;
    }

    void setSurface(Surface surface) {
        this.surface = surface;
    }

    @Override
    public void run() {
        isCancelled = false;

        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1920, 1080);
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        MediaCodec decoder;
        try {
            decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            decoder.configure(format, surface, null, 0);
            decoder.start();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        long frameCount = 0;
        long prevTime = System.currentTimeMillis();

        while (!isCancelled) {
            if (receiver == null) {
                continue;
            }
            FrameData frameData = receiver.getFrameData(streamId, TIMEOUT_US);
            if (frameData == null) {
                continue;
            }

            int inputBufferId = decoder.dequeueInputBuffer(0);
            if (inputBufferId >= 0) {
                ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferId);
                inputBuffer.put(frameData.getData(), 0, frameData.getSize());
                decoder.queueInputBuffer(inputBufferId, 0, frameData.getSize(), 0, 0); // TODO: MediaCodec.BUFFER_FLAG_KEY_FRAME
            }

            int outputBufferId = decoder.dequeueOutputBuffer(bufferInfo, 0);
            if (outputBufferId >= 0) {
                decoder.releaseOutputBuffer(outputBufferId, true);
            }

            frameCount++;
            long currTime = System.currentTimeMillis();
            if (currTime - prevTime >= 1000) {
                Log.i(LOG_TAG, frameCount + " fps");
                prevTime = currTime;
                frameCount = 0;
            }
        }
        decoder.stop();
        decoder.release();
        Log.d(LOG_TAG, "Cancelled");
    }

    void cancel() {
        isCancelled = true;
    }
}
