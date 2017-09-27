package com.example.tanakahx.h264streamingplayer;

import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.util.concurrent.LinkedBlockingDeque;

class UdpStreamingReceiver extends AsyncTask<Void, Void, Void> {
    private static final String LOG_TAG = UdpStreamingReceiver.class.getSimpleName();

    private static final int PACKET_BUFFER_SIZE = 2048; // Byte (should be greater than MTU)
    private static final int UDP_STREAMING_PORT = 1234;

    private int streamCount;
    private DatagramSocket sock;
    private StreamingFrameBuffer streamingFrameBuffers[];

    UdpStreamingReceiver(int streamCount) {
        this.streamCount = streamCount;
        streamingFrameBuffers = new StreamingFrameBuffer[this.streamCount];
        for (int i = 0; i < streamingFrameBuffers.length; i++) {
            streamingFrameBuffers[i] = new StreamingFrameBuffer(new LinkedBlockingDeque<FrameData>(1));
        }
    }

    FrameData getFrameData(int id, long timeoutUs) {
        if (id < streamCount) {
            return streamingFrameBuffers[id].getFrameData(timeoutUs);
        } else {
            return null;
        }
    }

    @Override
    protected Void doInBackground(Void... params) {
        DatagramPacket packet = new DatagramPacket(new byte[PACKET_BUFFER_SIZE], PACKET_BUFFER_SIZE);

        open(UDP_STREAMING_PORT);
        while (!isCancelled()) {
            try {
                sock.receive(packet);
                int streamId = StreamingFrameBuffer.getInt(packet.getData());
                streamingFrameBuffers[streamId].putPacket(packet);
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
            sock.setSoTimeout(33);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void close() {
        sock.close();
    }
}
