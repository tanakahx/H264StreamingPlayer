package com.example.tanakahx.h264streamingplayer;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

class UdpStreamingReceiver extends StreamingReceiver {

    static final boolean CHECK_SEQUENCE_NO = true;
    static final int SEQUENCE_NO_SIZE = CHECK_SEQUENCE_NO ? 4 : 0;
    static final int FRAME_BUFFER_SIZE = 512; // KB
    static final int PACKET_BUFFER_SIZE = 2048; // Byte (should be greater than MTU)

    DatagramSocket sock;
    byte[][] frameBuffer;
    int[] frameBufferSize;
    int lastFrame;
    int frameBufferPos;
    DatagramPacket packet;
    int nextSequenceNo;
    int nextFrameNo;
    boolean isDropped;

    void open(int port) {
        try {
            if (sock == null) {
                sock = new DatagramSocket(port);
                sock.setSoTimeout(33);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        frameBuffer = new byte[2][FRAME_BUFFER_SIZE*1024];
        packet = new DatagramPacket(new byte[PACKET_BUFFER_SIZE], PACKET_BUFFER_SIZE);
        frameBufferSize = new int[] {0, 0};
        lastFrame = frameBufferSize.length - 1;
        frameBufferPos = 0;
        nextSequenceNo = 0;
        nextFrameNo = 0;
    }

    int getInt(byte[] b) {
        short[] s = {
                (short)(b[0] & 0xFF),
                (short)(b[1] & 0xFF),
                (short)(b[2] & 0xFF),
                (short)(b[3] & 0xFF),
        };
        return (s[0] << 24) | (s[1] << 16) | (s[2] << 8) | (s[3] << 0);
    }

    int newFrameIndex() {
        return (lastFrame + 1 >= frameBufferSize.length) ? 0 : lastFrame + 1;
    }

    byte[] nextFrame() throws IOException {
        int newFrame = newFrameIndex();
        isDropped = false;

        while (true) {
            sock.receive(packet);
            int streamNo = (getInt(packet.getData()) >> 24) & 0x000000FF;
            if (streamNo != 0) {
                continue;
            }

            if (packet.getLength() - SEQUENCE_NO_SIZE == H264_AUD.length) {
                if (isAUD(packet.getData(), SEQUENCE_NO_SIZE)) {
                    if (frameBufferPos > 0) {
                        if (!isDropped) {
                            // End of frame
                            frameBufferSize[newFrame] = frameBufferPos;
                            lastFrame = newFrame;
                            // Prepare for the new frame
                            newFrame = newFrameIndex();
                        }
                        System.arraycopy(packet.getData(), 0, frameBuffer[newFrame], 0, H264_AUD.length);
                        frameBufferPos = H264_AUD.length;
                        nextSequenceNo = 1;
                        // Check Frame No.
                        int frameNo = (getInt(packet.getData()) >> 16) & 0x000000FF;
                        if (frameNo != nextFrameNo) {
                            Log.i(getClass().getSimpleName(), "Expected frame No: " + nextFrameNo + " Actual: " + frameNo);
                        } else {
//                            Log.i(getClass().getSimpleName(), "Completed Frame No " + nextFrameNo);
                        }
                        nextFrameNo = frameNo + 1;
                        break;
                    } else {
                        // Start of frame
                        System.arraycopy(packet.getData(), 0, frameBuffer[newFrame], 0, H264_AUD.length);
                        frameBufferPos = H264_AUD.length;
                        nextSequenceNo = 1;
                        continue;
                    }
                }
            }
            if (!isDropped && CHECK_SEQUENCE_NO && frameBufferPos > 0) {
                int seqNo = getInt(packet.getData()) & 0x0000FFFF;
                if (seqNo != nextSequenceNo) { // Packet drop
                    Log.i(getClass().getSimpleName(), "Expected Sequence No: " + nextSequenceNo + " Actual: " + seqNo);
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
                    Log.i(getClass().getSimpleName(), "Too much frame data. Dropped this frame.");
                    break;
                }
            }
        }
        return frameBuffer[lastFrame];
    }

    int getFrameSize() {
        return frameBufferSize[lastFrame];
    }

    boolean isValidFrame() {
        return isDropped ? false : true;
    }

    void close() {
        sock.close();
    }

}
