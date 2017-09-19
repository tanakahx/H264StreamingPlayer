package com.example.tanakahx.h264streamingplayer;

import android.content.pm.ActivityInfo;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;

abstract class StreamingReceiver {
    static final byte[] H264_AUD = {0x00, 0x00, 0x00, 0x01, 0x09, 0x10};

    abstract void open(int n);

    abstract byte[] nextFrame();

    abstract int getFrameSize();

    abstract void close();

    boolean isAUD(byte[] b, int offset) {
        for (int i = 0; i < H264_AUD.length - 1; i++) {
            if (b[offset + i] != H264_AUD[i]) {
                return false;
            }
        }
        return (b[offset + H264_AUD.length - 1] & H264_AUD[H264_AUD.length - 1]) == H264_AUD[H264_AUD.length - 1];
    }

}

class UdpStreamingReceiver extends StreamingReceiver {

    static final boolean CHECK_SEQUENCE_NO = true;
    static final int SEQUENCE_NO_SIZE = CHECK_SEQUENCE_NO ? 4 : 0;
    static final int FRAME_BUFFER_SIZE = 256; // KB
    static final int PACKET_BUFFER_SIZE = 2048; // Byte (should be greater than MTU)

    DatagramSocket sock;
    byte[][] frameBuffer = new byte[2][FRAME_BUFFER_SIZE*1024];
    int[] frameBufferSize = {0, 0};
    int lastFrame = frameBufferSize.length - 1;
    int frameBufferPos = 0;
    DatagramPacket packet = new DatagramPacket(new byte[PACKET_BUFFER_SIZE], PACKET_BUFFER_SIZE);
    int nextSequenceNo = 0;
    int nextFrameNo = 0;

    void open(int port) {
        try {
            sock = new DatagramSocket(port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    int getInt(byte[] b) {
        short[] s = {
                (short)(b[0] & 0xFF),
                (short)(b[1] & 0xFF),
                (short)(b[2] & 0xFF),
                (short)(b[3] & 0xFF),
        };
        return (s[0] << 24) | (s[1] << 16) | (s[2] << 8) | (s[3] << 0);
    }

    int newFrameIndex() {
        return (lastFrame + 1 >= frameBufferSize.length) ? 0 : lastFrame + 1;
    }

    byte[] nextFrame() {
        int newFrame = newFrameIndex();

        while (true) {
            try {
                sock.receive(packet);
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
            if (packet.getLength() - SEQUENCE_NO_SIZE == H264_AUD.length) {
                if (isAUD(packet.getData(), SEQUENCE_NO_SIZE)) {
                    if (frameBufferPos > 0) {
                        // End of frame
                        frameBufferSize[newFrame] = frameBufferPos;
                        lastFrame = newFrame;
                        // Prepare for the new frame
                        newFrame = newFrameIndex();
                        System.arraycopy(packet.getData(), 0, frameBuffer[newFrame], 0, H264_AUD.length);
                        frameBufferPos = H264_AUD.length;
                        nextSequenceNo = 1;
                        // Check Frame No.
                        int frameNo = getInt(packet.getData()) >> 16;
                        Log.i(getClass().getSimpleName(), "Frame No " + frameNo);
                        if (frameNo != nextFrameNo) {
                            Log.i(getClass().getSimpleName(), "Expected frame No: " + nextFrameNo + " Actual: " + frameNo);
                        }
                        nextFrameNo = frameNo + 1;
                        break;
                    } else {
                        // Start of frame
                        System.arraycopy(packet.getData(), 0, frameBuffer[newFrame], 0, H264_AUD.length);
                        frameBufferPos = H264_AUD.length;
                        nextSequenceNo = 1;
                        // Check Frame No.
                        int frameNo = getInt(packet.getData()) >> 16;
                        Log.i(getClass().getSimpleName(), "Frame No " + frameNo);
                        nextFrameNo = frameNo + 1;
                        continue;
                    }
                }
            }
            if (CHECK_SEQUENCE_NO && frameBufferPos > 0) {
                int seqNo = getInt(packet.getData()) & 0xFFFF;
                if (seqNo != nextSequenceNo) { // Packet drop
                    frameBufferPos = 0;
                    Log.i(getClass().getSimpleName(), "Expected Sequence No: " + nextSequenceNo + " Actual: " + seqNo);
                    break; // Drop this frame and return the last(old) one.
                } else {
                    nextSequenceNo++;
                }
            }
            if (frameBufferPos >= H264_AUD.length) {
                if (frameBufferPos + packet.getLength() - SEQUENCE_NO_SIZE <= frameBuffer[newFrame].length) {
                    System.arraycopy(packet.getData(), SEQUENCE_NO_SIZE, frameBuffer[newFrame], frameBufferPos, packet.getLength() - SEQUENCE_NO_SIZE);
                    frameBufferPos += packet.getLength() - SEQUENCE_NO_SIZE;
                } else {
                    // If received data exceeds the size of the current(new) frame buffer,
                    // just drop it and return the last(old) one.
                    frameBufferPos = 0;
                    Log.i(getClass().getSimpleName(), "Too much frame data. Dropped this frame.");
                    break;
                }
            }
        }
        return frameBuffer[lastFrame];
    }

    int getFrameSize() {
        return frameBufferSize[lastFrame];
    }

    void close() {
        try {
            sock.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private StreamingReceiver receiver;
    private MediaCodec decoder;
    private MediaCodecDataProvider dataProvider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SurfaceView surface = new SurfaceView(this);
        surface.getHolder().addCallback(this);
        setContentView(surface);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        receiver = new UdpStreamingReceiver();
        receiver.open(1234);

        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1920, 1080);
        /* mediaFormat.setByteBuffer("csd-0", ...); */
        /* mediaFormat.setByteBuffer("csd-1", ...); */
        /* mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 256*1024); */

        try {
            decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            decoder.configure(mediaFormat, holder.getSurface(), null, 0);
            decoder.start();
            dataProvider = new MediaCodecDataProvider();
            dataProvider.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    private class MediaCodecDataProvider extends AsyncTask<String, String, String> {

        @Override
        protected String doInBackground(String... data) {
            long frameCount = 0;
            long prevTime = System.currentTimeMillis();

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

            while(!isCancelled()) {
                byte[] frame = receiver.nextFrame();
                int frameSize = receiver.getFrameSize();

                int inputBufferId = decoder.dequeueInputBuffer(-1);
                if(inputBufferId >= 0) {
                    ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferId);
                    inputBuffer.put(frame, 0, frameSize);
                    decoder.queueInputBuffer(inputBufferId, 0, frameSize, 0, 0);
                }

                int outputBufferId = decoder.dequeueOutputBuffer(bufferInfo, 0);
                if (outputBufferId >= 0) {
                    decoder.releaseOutputBuffer(outputBufferId, true);
                }

                frameCount++;
                long currTime = System.currentTimeMillis();
                if (currTime - prevTime >= 1000) {
                    Log.i(getClass().getSimpleName(), frameCount + " fps");
                    prevTime = currTime;
                    frameCount = 0;
                }

            }
            return "";
        }

        @Override
        protected void onPostExecute(String result) {
            try {
                decoder.stop();
                decoder.release();
            } catch(Exception e) {
                e.printStackTrace();
            }
        }

    }

    @Override
    public void onStop(){
        super.onStop();
        dataProvider.cancel(true);
        receiver.close();
    }
}
