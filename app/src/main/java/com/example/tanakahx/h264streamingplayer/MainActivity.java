package com.example.tanakahx.h264streamingplayer;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
    private static final String LOG_TAG = MainActivity.class.getSimpleName();

    private static final int TABLE_ROW = 2;
    private static final int TABLE_COL = 2;
    private static final int MAX_STREAM_COUNT = TABLE_ROW * TABLE_COL;

    private LinearLayout linearCol;
    private LinearLayout[] linearRow;
    private DecoderSurfaceView[] surfaceViews;
    private DecoderSurfaceView selectedSurfaceView;
    private StreamingReceiver receiver;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handler = new Handler();
        receiver = new UdpStreamingReceiver(MAX_STREAM_COUNT);

        linearCol = (LinearLayout)findViewById(R.id.linear_col);
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
        receiver.start();

        AsyncTask<Void, Void, Void> reportTask = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                WifiManager wifiManager = (WifiManager)getSystemService(Context.WIFI_SERVICE);
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                while (!isCancelled()) {
                    final StringBuilder sb = new StringBuilder();
                    sb.append(wifiInfo).append("\n");
                    sb.append(receiver).append(", ");
                    int i = 0;
                    for (DecoderSurfaceView surfaceView : surfaceViews) {
                        if (surfaceView == selectedSurfaceView) {
                            sb.append("[");
                        }
                        sb.append("Ch").append(i).append(": ").append(surfaceView);
                        if (surfaceView == selectedSurfaceView) {
                            sb.append("] ");
                        } else {
                            sb.append(" ");
                        }
                        i++;
                    }

                    final TextView infoText = (TextView)findViewById(R.id.info_text);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            infoText.setText(sb.toString());
                        }
                    });
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                    }
                }
                return null;
            }
        };
        reportTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

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
                selectedSurfaceView = surfaceView;
            } else {
                layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
                selectedSurfaceView = null;
            }
            surfaceView.setLayoutParams(layoutParams);
            linearLayout.setLayoutParams(layoutParams);
        }
    }
}

