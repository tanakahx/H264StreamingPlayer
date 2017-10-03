package com.example.tanakahx.h264streamingplayer;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;

public class MainActivity extends AppCompatActivity {
    private static final String LOG_TAG = MainActivity.class.getSimpleName();

    private static final int TABLE_ROW = 2;
    private static final int TABLE_COL = 2;
    private static final int MAX_STREAM_COUNT = TABLE_ROW * TABLE_COL;

    private LinearLayout linearCol;
    private LinearLayout[] linearRow;
    private DecoderSurfaceView[] surfaceViews;
    private UdpStreamingReceiver receiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        receiver = new UdpStreamingReceiver(MAX_STREAM_COUNT);

        linearCol = new LinearLayout(this);
        linearCol.setOrientation(LinearLayout.VERTICAL);
        linearRow = new LinearLayout[TABLE_ROW];
        surfaceViews = new DecoderSurfaceView[MAX_STREAM_COUNT];
        int i = 0;
        for (int v = 0; v < TABLE_COL; v++) {
            linearRow[v] = new LinearLayout(this);
            linearRow[v].setOrientation(LinearLayout.HORIZONTAL);
            for (int h = 0; h < TABLE_ROW; h++) {
                surfaceViews[i] = new DecoderSurfaceView(this, i);
                surfaceViews[i].setOnClickListener(new DecoderSurfaceClickLister(surfaceViews[i], linearRow[v]));
                LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT, 1);
                linearRow[v].addView(surfaceViews[i], layoutParams);
                i++;
            }
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT, 1);
            linearCol.addView(linearRow[v], layoutParams);
        }
        setContentView(linearCol);

        Log.d(LOG_TAG, "onCreate");
    }

    void setFullscreen(boolean isFullscreen) {
        if (isFullscreen) {
            linearCol.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        } else {
            linearCol.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        setFullscreen(true);

        if (receiver == null) {
            receiver = new UdpStreamingReceiver(MAX_STREAM_COUNT);
        }
        for (DecoderSurfaceView surfaceView : surfaceViews) {
            surfaceView.setReceiver(receiver);
        }
        receiver.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        Log.d(LOG_TAG, "onStart");
    }

    @Override
    public void onStop(){
        super.onStop();
        receiver.cancel(true);
        receiver = null;
        Log.d(LOG_TAG, "onStop");
    }

    class DecoderSurfaceClickLister implements View.OnClickListener {

        private final DecoderSurfaceView surfaceView;
        private final LinearLayout linearLayout;

        DecoderSurfaceClickLister(DecoderSurfaceView surfaceView, LinearLayout linearLayout) {
            this.surfaceView = surfaceView;
            this.linearLayout = linearLayout;
        }

        @Override
        public void onClick(View v) {
            LinearLayout.LayoutParams layoutParams;
            layoutParams = (LinearLayout.LayoutParams)surfaceView.getLayoutParams();
            if (layoutParams.weight != 0) {
                layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT, 0f);
            } else {
                layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            }
            surfaceView.setLayoutParams(layoutParams);
            linearLayout.setLayoutParams(layoutParams);
        }
    }
}

