package com.example.tanakahx.h264streamingplayer;

public class FrameData {
    private final byte[] data;
    private final int size;

    FrameData(byte[] data, int size) {
        this.data = data;
        this.size = size;
    }

    public byte[] getData() {
        return data;
    }

    public int getSize() {
        return size;
    }
}
