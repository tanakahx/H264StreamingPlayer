package com.example.tanakahx.h264streamingplayer;

import android.content.Context;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class DecoderSurfaceView extends SurfaceView implements SurfaceHolder.Callback {
    private static final String LOG_TAG = DecoderSurfaceView.class.getSimpleName();

    private DecoderThread decoderThread;

    DecoderSurfaceView(Context context, int streamId) {
        super(context);
        decoderThread = new DecoderThread(streamId);
        getHolder().addCallback(this);
    }

    void setReceiver(StreamingReceiver receiver) {
        decoderThread.setReceiver(receiver);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        decoderThread.setSurface(holder.getSurface());
        decoderThread.start();
        Log.d(LOG_TAG, "surfaceCreated");
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(LOG_TAG, "surfaceChanged");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        decoderThread.cancel();
        Log.d(LOG_TAG, "surfaceDestroyed");
    }

    @Override
    public String toString() {
        return decoderThread.toString();
    }
}
