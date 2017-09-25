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
import android.widget.TableLayout;
import android.widget.TableRow;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    static final int TABLE_ROW = 1;
    static final int TABLE_COL = 1;
    static final int UDP_STREAMING_PORT = 1234;

    private TableLayout tableLayout;
    private SurfaceView[] surfaceView;
    private MediaCodecDataProvider dataProvider;
    private boolean isFullscreen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        tableLayout = new TableLayout(this);

        surfaceView = new SurfaceView[4];
        int i = 0;
        for (int h = 0; h < TABLE_ROW; h++) {
            TableRow tableRow = new TableRow(this);
            for (int v = 0; v < TABLE_COL; v++) {
                surfaceView[i] = new SurfaceView(this);
                surfaceView[i].getHolder().addCallback(this);
                tableRow.addView(surfaceView[i], new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.MATCH_PARENT, 1));
            }
            tableLayout.addView(tableRow, new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.MATCH_PARENT, 1));
        }

        setContentView(tableLayout);

        isFullscreen = true;
        setFullscreen(isFullscreen);

        tableLayout.setOnClickListener(new View.OnClickListener() {
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
            tableLayout.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        } else {
            tableLayout.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    private class MediaCodecDataProvider extends AsyncTask<Surface, Void, Void> {

        @Override
        protected Void doInBackground(Surface... surface) {

            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1920, 1080);
            MediaCodec decoder;
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            try {
                decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
                decoder.configure(format, surface[0], null, 0);
                decoder.start();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }

            StreamingReceiver receiver = new UdpStreamingReceiver();
            receiver.open(UDP_STREAMING_PORT);

            long frameCount = 0;
            long prevTime = System.currentTimeMillis();

            while (!isCancelled()) {
                byte[] frame;
                try {
                    frame = receiver.nextFrame();
                } catch (SocketTimeoutException e) {
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
