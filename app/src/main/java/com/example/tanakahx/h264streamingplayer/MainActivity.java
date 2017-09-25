package com.example.tanakahx.h264streamingplayer;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;

abstract class StreamingReceiver {
    static final byte[] H264_AUD = {0x00, 0x00, 0x00, 0x01, 0x09, 0x10};

    abstract void open(int n);

    abstract byte[] nextFrame() throws IOException;

    abstract int getFrameSize();

    abstract boolean isValidFrame();

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
    static final int FRAME_BUFFER_SIZE = 512; // KB
    static final int PACKET_BUFFER_SIZE = 2048; // Byte (should be greater than MTU)

    DatagramSocket sock;
    byte[][] frameBuffer;
    int[] frameBufferSize;
    int lastFrame;
    int frameBufferPos;
    DatagramPacket packet;
    int nextSequenceNo;
    int nextFrameNo;
    boolean isDropped;

    void open(int port) {
        try {
            if (sock == null) {
                sock = new DatagramSocket(port);
                sock.setSoTimeout(33);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        frameBuffer = new byte[2][FRAME_BUFFER_SIZE*1024];
        packet = new DatagramPacket(new byte[PACKET_BUFFER_SIZE], PACKET_BUFFER_SIZE);
        frameBufferSize = new int[] {0, 0};
        lastFrame = frameBufferSize.length - 1;
        frameBufferPos = 0;
        nextSequenceNo = 0;
        nextFrameNo = 0;
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

    byte[] nextFrame() throws IOException {
        int newFrame = newFrameIndex();
        isDropped = false;

        while (true) {
            sock.receive(packet);
            int streamNo = (getInt(packet.getData()) >> 24) & 0x000000FF;
            if (streamNo != 0) {
                continue;
            }

            if (packet.getLength() - SEQUENCE_NO_SIZE == H264_AUD.length) {
                if (isAUD(packet.getData(), SEQUENCE_NO_SIZE)) {
                    if (frameBufferPos > 0) {
                        if (!isDropped) {
                            // End of frame
                            frameBufferSize[newFrame] = frameBufferPos;
                            lastFrame = newFrame;
                            // Prepare for the new frame
                            newFrame = newFrameIndex();
                        }
                        System.arraycopy(packet.getData(), 0, frameBuffer[newFrame], 0, H264_AUD.length);
                        frameBufferPos = H264_AUD.length;
                        nextSequenceNo = 1;
                        // Check Frame No.
                        int frameNo = (getInt(packet.getData()) >> 16) & 0x000000FF;
                        if (frameNo != nextFrameNo) {
                            Log.i(getClass().getSimpleName(), "Expected frame No: " + nextFrameNo + " Actual: " + frameNo);
                        } else {
//                            Log.i(getClass().getSimpleName(), "Completed Frame No " + nextFrameNo);
                        }
                        nextFrameNo = frameNo + 1;
                        break;
                    } else {
                        // Start of frame
                        System.arraycopy(packet.getData(), 0, frameBuffer[newFrame], 0, H264_AUD.length);
                        frameBufferPos = H264_AUD.length;
                        nextSequenceNo = 1;
                        continue;
                    }
                }
            }
            if (!isDropped && CHECK_SEQUENCE_NO && frameBufferPos > 0) {
                int seqNo = getInt(packet.getData()) & 0x0000FFFF;
                if (seqNo != nextSequenceNo) { // Packet drop
                    Log.i(getClass().getSimpleName(), "Expected Sequence No: " + nextSequenceNo + " Actual: " + seqNo);
                    isDropped = true;
                } else {
                    nextSequenceNo++;
                }
            }
            if (!isDropped && frameBufferPos >= H264_AUD.length) {
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

    boolean isValidFrame() {
        return isDropped ? false : true;
    }

    void close() {
        sock.close();
    }

}

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    static final int UDP_STREAMING_PORT = 1234;

    private StreamingReceiver receiver;
    private MediaCodec decoder;
    private MediaCodecDataProvider dataProvider;
    private boolean isFullscreen;
    private SurfaceView surface;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        surface = new SurfaceView(this);
        surface.getHolder().addCallback(this);
        isFullscreen = true;
        setFullscreen(isFullscreen);
        setContentView(surface);

        surface.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setFullscreen(isFullscreen);
                isFullscreen = (isFullscreen == true) ? false : true;
            }
        });
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.i(getClass().getSimpleName(), "surfaceCreated");
        dataProvider = new MediaCodecDataProvider();
        dataProvider.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, holder.getSurface());
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.i(getClass().getSimpleName(), "surfaceChanged");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.i(getClass().getSimpleName(), "surfaceDestroyed");
        dataProvider.cancel(true);
    }

    void setFullscreen(boolean isFullscreen) {
        if (isFullscreen) {
            surface.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        } else {
            surface.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    private class MediaCodecDataProvider extends AsyncTask<Surface, Void, Void> {

        @Override
        protected Void doInBackground(Surface... surface) {
            MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1920, 1080);
            /* mediaFormat.setByteBuffer("csd-0", ...); */
            /* mediaFormat.setByteBuffer("csd-1", ...); */
            /* mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 256*1024); */
            try {
                decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            } catch (IOException e) {
                e.printStackTrace();
            }
            decoder.configure(mediaFormat, surface[0], null, 0);
            decoder.start();

            receiver = new UdpStreamingReceiver();
            receiver.open(UDP_STREAMING_PORT);

            long frameCount = 0;
            long prevTime = System.currentTimeMillis();

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

            while(!isCancelled()) {
                byte[] frame;
                try {
                    frame = receiver.nextFrame();
                } catch (SocketTimeoutException e) {
//                    e.printStackTrace();
                    if (isCancelled()) {
                        break;
                    } else {
                        continue;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
                int frameSize = receiver.getFrameSize();

                int inputBufferId = decoder.dequeueInputBuffer(-1);
                if(inputBufferId >= 0) {
                    ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferId);
                    inputBuffer.put(frame, 0, frameSize);
                    decoder.queueInputBuffer(inputBufferId, 0, frameSize, 0, 0); // TODO: MediaCodec.BUFFER_FLAG_KEY_FRAME
                }

                int outputBufferId = decoder.dequeueOutputBuffer(bufferInfo, -1);
                if (outputBufferId >= 0) {
                    decoder.releaseOutputBuffer(outputBufferId, true);
                }

                frameCount += receiver.isValidFrame() ? 1 : 0;
                long currTime = System.currentTimeMillis();
                if (currTime - prevTime >= 1000) {
                    Log.i(getClass().getSimpleName(), frameCount + " fps");
                    prevTime = currTime;
                    frameCount = 0;
                }

            }
            receiver.close();
            decoder.stop();

            return null;
        }

    }

    @Override
    public void onStart() {
        super.onStart();
        Log.i(getClass().getSimpleName(), "onStart");
    }

    @Override
    public void onStop(){
        super.onStop();
        Log.i(getClass().getSimpleName(), "onStop");
    }
}
