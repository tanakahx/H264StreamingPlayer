package com.example.tanakahx.h264streamingplayer;

interface StreamingReceiver {

    void start();

    boolean cancel(boolean b);

    FrameData getFrameData(int id, long timeoutUs);
}
