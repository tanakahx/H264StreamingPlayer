package com.example.tanakahx.h264streamingplayer;

import java.io.IOException;

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

