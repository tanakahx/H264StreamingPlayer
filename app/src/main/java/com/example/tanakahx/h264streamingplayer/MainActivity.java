package com.example.tanakahx.h264streamingplayer;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TableLayout;
import android.widget.TableRow;

public class MainActivity extends AppCompatActivity {
    private static final String LOG_TAG = MainActivity.class.getSimpleName();

    private static final int TABLE_ROW = 2;
    private static final int TABLE_COL = 2;
    private static final int MAX_STREAM_COUNT = 4;

    private TableLayout tableLayout;
    private DecoderSurfaceView[] surfaceViews;
    private UdpStreamingReceiver receiver;
    private boolean isFullscreen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        receiver = new UdpStreamingReceiver(MAX_STREAM_COUNT);

        tableLayout = new TableLayout(this);
        surfaceViews = new DecoderSurfaceView[MAX_STREAM_COUNT];
        int i = 0;
        for (int h = 0; h < TABLE_ROW; h++) {
            TableRow tableRow = new TableRow(this);
            for (int v = 0; v < TABLE_COL; v++) {
                surfaceViews[i] = new DecoderSurfaceView(this, i);
                tableRow.addView(surfaceViews[i], new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.MATCH_PARENT, 1));
                i++;
            }
            tableLayout.addView(tableRow, new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.MATCH_PARENT, 1));
        }
        setContentView(tableLayout);

        tableLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isFullscreen = (isFullscreen == true) ? false : true;
                setFullscreen(isFullscreen);
            }
        });
        Log.d(LOG_TAG, "onCreate");
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

    @Override
    public void onStart() {
        super.onStart();
        isFullscreen = true;
        setFullscreen(isFullscreen);

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
}
