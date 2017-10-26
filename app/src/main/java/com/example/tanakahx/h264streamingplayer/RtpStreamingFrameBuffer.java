package com.example.tanakahx.h264streamingplayer;


import android.util.Log;

import java.net.DatagramPacket;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.TimeUnit;

public class RtpStreamingFrameBuffer {
    private static final String LOG_TAG = RtpStreamingFrameBuffer.class.getSimpleName();

    private static final byte[] NAL_PREFIX = {0x00, 0x00, 0x00, 0x01};
    private static final byte[] H264_AUD = {0x09, 0x10};
    private static final int FRAME_BUFFER_SIZE = 512; // KB
    private static final int RTP_HEADER_LENGTH = 12;

    private byte[][] frameBuffer;
    private int[] frameBufferSize;
    private int lastFrame;
    private int frameBufferPos;
    private int nextSequenceNo;
    private boolean isDropped;
    private BlockingDeque<FrameData> deque;

    RtpStreamingFrameBuffer(BlockingDeque<FrameData> deque) {
        this.deque = deque;
        frameBuffer = new byte[2][FRAME_BUFFER_SIZE*1024];
        frameBufferSize = new int[] {0, 0};
        lastFrame = frameBufferSize.length - 1;
        frameBufferPos = 0;
        nextSequenceNo = 0;
        isDropped = false;
    }

    static boolean isAUD(byte[] b, int offset) {
        if (b[offset] == H264_AUD[0] && (b[offset + 1] & H264_AUD[1]) == H264_AUD[1]) {
            return true;
        } else {
            return false;
        }
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
        int seqNo = getInt(packet.getData()) & 0x0000FFFF;

        if (packet.getLength() <= RTP_HEADER_LENGTH + 1) { // Packets must have at least 1 byte payload.
            return;
        }

        if (isAUD(packet.getData(), RTP_HEADER_LENGTH)) { // Start of frame
            if (frameBufferPos > 0) {
                if (!isDropped) {
                    // End of frame
                    frameBufferSize[newFrame] = frameBufferPos;
                    lastFrame = newFrame;
                    offerFrameData(new FrameData(frameBuffer[lastFrame], frameBufferSize[lastFrame]));
                    // Prepare for the new frame
                    newFrame = newFrameIndex();
                }
            }
            System.arraycopy(NAL_PREFIX, 0, frameBuffer[newFrame], 0, NAL_PREFIX.length);
            frameBufferPos = NAL_PREFIX.length;
            System.arraycopy(packet.getData(), RTP_HEADER_LENGTH, frameBuffer[newFrame], NAL_PREFIX.length, packet.getLength() - RTP_HEADER_LENGTH);
            frameBufferPos += packet.getLength() - RTP_HEADER_LENGTH;
            nextSequenceNo = (seqNo + 1) & 0x0000FFFF;
            isDropped = false;
            return;
        }

        if (!isDropped && frameBufferPos > 0) {
            if (seqNo != nextSequenceNo) { // Packet drop
                StringBuilder sb = new StringBuilder("Packet lost (Expected Sequence No: ");
                sb.append(nextSequenceNo).append(" Actual: ").append(seqNo).append(")");
                Log.i(LOG_TAG, sb.toString());
                isDropped = true;
            } else {
                nextSequenceNo = (seqNo + 1) & 0x0000FFFF;
            }
        }

        if (!isDropped && frameBufferPos > 0) {
            if (frameBufferPos + NAL_PREFIX.length + packet.getLength() - RTP_HEADER_LENGTH <= frameBuffer[newFrame].length) {
                byte fuIndicator = packet.getData()[RTP_HEADER_LENGTH];
                byte payloadType = (byte)(fuIndicator & 0x0000001F);
                if (payloadType <= 23) { // Single NAL Unit Packet
                    System.arraycopy(NAL_PREFIX, 0, frameBuffer[newFrame], frameBufferPos, NAL_PREFIX.length);
                    frameBufferPos += NAL_PREFIX.length;
                    System.arraycopy(packet.getData(), RTP_HEADER_LENGTH, frameBuffer[newFrame], frameBufferPos, packet.getLength() - RTP_HEADER_LENGTH);
                    frameBufferPos += packet.getLength() - RTP_HEADER_LENGTH;
                } else if (payloadType == 28) { // FU-A Fragment Packet
                    byte fuHeader = packet.getData()[RTP_HEADER_LENGTH + 1];
                    byte startBit = (byte)(fuHeader & 0x00000080);
                    if (startBit != 0) {
                        System.arraycopy(NAL_PREFIX, 0, frameBuffer[newFrame], frameBufferPos, NAL_PREFIX.length);
                        frameBufferPos += NAL_PREFIX.length;
                        byte nalHeader = (byte)((fuIndicator & 0x000000E0) | (fuHeader & 0x0000001F));
                        frameBuffer[newFrame][frameBufferPos] = nalHeader;
                        frameBufferPos += 1;
                    }
                    System.arraycopy(packet.getData(), RTP_HEADER_LENGTH + 2, frameBuffer[newFrame], frameBufferPos, packet.getLength() - (RTP_HEADER_LENGTH + 2));
                    frameBufferPos += packet.getLength() - (RTP_HEADER_LENGTH + 2);
                } else {
                    StringBuilder sb = new StringBuilder("Unrecognized payload type: ").append(payloadType);
                    Log.i(LOG_TAG, sb.toString());
                }
            } else {
                // If received data exceeds the size of the current(new) frame buffer,
                // just drop it and return the last(old) one.
                frameBufferPos = 0;
                Log.i(LOG_TAG, "Too much frame data. Dropped this frame.");
            }
        }
    }

    private void offerFrameData(FrameData frameBuffer) {
        deque.offer(frameBuffer);
    }
}
