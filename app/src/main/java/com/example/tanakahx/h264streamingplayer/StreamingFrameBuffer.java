package com.example.tanakahx.h264streamingplayer;


import android.util.Log;

import java.net.DatagramPacket;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.TimeUnit;

public class StreamingFrameBuffer {
    private static final String LOG_TAG = StreamingFrameBuffer.class.getSimpleName();

    private static final byte[] H264_AUD = {0x00, 0x00, 0x00, 0x01, 0x09, 0x10};
    private static final int FRAME_BUFFER_SIZE = 512; // KB
    private static final boolean CHECK_SEQUENCE_NO = true;
    private static final int SEQUENCE_NO_SIZE = CHECK_SEQUENCE_NO ? 8 : 0;

    private byte[][] frameBuffer;
    private int[] frameBufferSize;
    private int lastFrame;
    private int frameBufferPos;
    private int nextSequenceNo;
    private int nextFrameNo;
    private boolean isDropped;
    private BlockingDeque<FrameData> deque;

    StreamingFrameBuffer(BlockingDeque<FrameData> deque) {
        this.deque = deque;
        frameBuffer = new byte[2][FRAME_BUFFER_SIZE*1024];
        frameBufferSize = new int[] {0, 0};
        lastFrame = frameBufferSize.length - 1;
        frameBufferPos = 0;
        nextSequenceNo = 0;
        nextFrameNo = 0;
        isDropped = false;
    }

    static boolean isAUD(byte[] b, int offset) {
        for (int i = 0; i < H264_AUD.length - 1; i++) {
            if (b[offset + i] != H264_AUD[i]) {
                return false;
            }
        }
        return (b[offset + H264_AUD.length - 1] & H264_AUD[H264_AUD.length - 1]) == H264_AUD[H264_AUD.length - 1];
    }

    static int getInt(byte[] b) {
        return getInt(b, 0);
    }

    static int getInt(byte[] b, int offset) {
        return  ((b[offset + 0] & 0xFF) << 24) |
                ((b[offset + 1] & 0xFF) << 16) |
                ((b[offset + 2] & 0xFF) <<  8) |
                ((b[offset + 3] & 0xFF) <<  0);
    }

    int newFrameIndex() {
        return (lastFrame + 1 >= frameBufferSize.length) ? 0 : lastFrame + 1;
    }

    FrameData getFrameData(long timeoutUs) {
        try {
            return deque.poll(timeoutUs, TimeUnit.MICROSECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
            return null;
        }
    }

    void putPacket(DatagramPacket packet) {
        int newFrame = newFrameIndex();

        if (packet.getLength() - SEQUENCE_NO_SIZE == H264_AUD.length) {
            if (isAUD(packet.getData(), SEQUENCE_NO_SIZE)) {
                if (frameBufferPos > 0) {
                    if (!isDropped) {
                        // End of frame
                        frameBufferSize[newFrame] = frameBufferPos;
                        lastFrame = newFrame;
                        offerFrameData(new FrameData(frameBuffer[lastFrame], frameBufferSize[lastFrame]));
                        // Prepare for the new frame
                        newFrame = newFrameIndex();
                    }
                    System.arraycopy(packet.getData(), SEQUENCE_NO_SIZE, frameBuffer[newFrame], 0, H264_AUD.length);
                    frameBufferPos = H264_AUD.length;
                    nextSequenceNo = 1;
                    // Check Frame No.
                    int frameNo = (getInt(packet.getData(), 4) >> 16) & 0x0000FFFF;
                    if (frameNo != nextFrameNo) {
                        Log.i(LOG_TAG, "Frame dropped (Expected frame No: " + nextFrameNo + " Actual: " + frameNo + ")");
                    } else {
//                        Log.i(LOG_TAG, "Completed Frame No " + nextFrameNo);
                    }
                    nextFrameNo = frameNo + 1;
                    isDropped = false;
                    return;
                } else {
                    // Start of frame
                    System.arraycopy(packet.getData(), SEQUENCE_NO_SIZE, frameBuffer[newFrame], 0, H264_AUD.length);
                    frameBufferPos = H264_AUD.length;
                    nextSequenceNo = 1;
                    isDropped = false;
                    return;
                }
            }
        }
        if (!isDropped && CHECK_SEQUENCE_NO && frameBufferPos > 0) {
            int seqNo = getInt(packet.getData(), 4) & 0x0000FFFF;
            if (seqNo != nextSequenceNo) { // Packet drop
                Log.i(LOG_TAG, "Packet lost (Expected Sequence No: " + nextSequenceNo + " Actual: " + seqNo + ")");
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
                Log.i(LOG_TAG, "Too much frame data. Dropped this frame.");
            }
        }
    }

    private void offerFrameData(FrameData frameBuffer) {
        deque.poll();
        deque.offer(frameBuffer);
    }
}
