package com.example.tanakahx.h264streamingplayer;

import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.util.concurrent.LinkedBlockingDeque;

class RtpStreamingReceiver extends AsyncTask<Void, Void, Void> implements StreamingReceiver {
    private static final String LOG_TAG = RtpStreamingReceiver.class.getSimpleName();

    private static final int PACKET_BUFFER_SIZE = 2048; // Byte (should be greater than MTU)
    private static final int RTP_STREAMING_PORT = 1234;

    private int streamCount;
    private DatagramSocket sock;
    private RtpStreamingFrameBuffer rtpStreamingFrameBuffers[];
    private long totalReceiveByte;

    RtpStreamingReceiver(int streamCount) {
        this.streamCount = streamCount;
        rtpStreamingFrameBuffers = new RtpStreamingFrameBuffer[this.streamCount];
        for (int i = 0; i < rtpStreamingFrameBuffers.length; i++) {
            rtpStreamingFrameBuffers[i] = new RtpStreamingFrameBuffer(new LinkedBlockingDeque<FrameData>(1));
        }
        totalReceiveByte = 0;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append((float)(totalReceiveByte * 8.0 / 1024 / 1024)).append(" Mbps");
        return sb.toString();
    }

    @Override
    public void start() {
        executeOnExecutor(THREAD_POOL_EXECUTOR);
    }

    @Override
    public FrameData getFrameData(int id, long timeoutUs) {
        if (id < streamCount) {
            return rtpStreamingFrameBuffers[id].getFrameData(timeoutUs);
        } else {
            return null;
        }
    }

    @Override
    protected Void doInBackground(Void... params) {
        DatagramPacket packet = new DatagramPacket(new byte[PACKET_BUFFER_SIZE], PACKET_BUFFER_SIZE);
        long prevTime = System.currentTimeMillis();
        long receiveByte = 0;

        open(RTP_STREAMING_PORT);
        while (!isCancelled()) {
            try {
                sock.receive(packet);

                receiveByte += packet.getLength();
                long currTime = System.currentTimeMillis();
                if (currTime - prevTime >= 1000) {
                    totalReceiveByte = receiveByte;
                    receiveByte = 0;
                    prevTime = currTime;
                }

                int streamId = RtpStreamingFrameBuffer.getInt(packet.getData(), 8);
                rtpStreamingFrameBuffers[streamId].putPacket(packet);
            } catch (SocketTimeoutException e) {
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        Log.d(LOG_TAG, "Cancelled");
        close();
        return null;
    }

    private void open(int port) {
        try {
            sock = new DatagramSocket(port);
            sock.setSoTimeout(1);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void close() {
        sock.close();
    }
}
