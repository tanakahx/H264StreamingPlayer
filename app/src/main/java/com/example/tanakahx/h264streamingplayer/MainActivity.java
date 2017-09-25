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
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;

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
