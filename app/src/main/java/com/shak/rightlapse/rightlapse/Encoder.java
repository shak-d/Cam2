package com.shak.rightlapse.rightlapse;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class Encoder {

   private static final String MIME_TYPE = "video/avc";
   private static final int IFRAME_INTERVAL = 5;

   private int FRAME_RATE;
   private long DURATION_SEC;
   private MediaCodec mEncoder;
   private MediaMuxer mMuxer;
   private boolean mMuxerStarted;
   private String OUTPUT_PATH;
   private int mTrackIndex;
   private Surface mInputSurface;
   private MediaCodec.BufferInfo mBufferInfo;

    public Encoder(){


    }

    public boolean prepare(int width, int height, int bitRate, int framerate , File output){

        mBufferInfo = new MediaCodec.BufferInfo();

        FRAME_RATE = framerate;

        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);

        try {
            mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
        }
        catch (IOException e){
            return false;
        }
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mInputSurface = mEncoder.createInputSurface();
        mEncoder.start();

        OUTPUT_PATH = output.toString();
        try {
            mMuxer = new MediaMuxer(OUTPUT_PATH, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException ioe) {
            throw new RuntimeException("MediaMuxer creation failed", ioe);
        }

        mTrackIndex = -1;
        mMuxerStarted = false;

        return true;
    }

    public void drainEncoder(boolean endOfStream){

        if (endOfStream)
            mEncoder.signalEndOfInputStream();

        ByteBuffer[] encoderOutputBuffers = mEncoder.getOutputBuffers();
        while (true){


        }

    }


}
