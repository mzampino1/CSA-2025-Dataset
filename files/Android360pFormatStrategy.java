package eu.siacs.conversations.utils;

import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;

import net.ypresto.androidtranscoder.format.MediaFormatExtraConstants;
import net.ypresto.androidtranscoder.format.MediaFormatStrategy;
import net.ypresto.androidtranscoder.format.OutputFormatUnavailableException;

import eu.siacs.conversations.Config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Android360pFormatStrategy implements MediaFormatStrategy {

    private static final int LONGER_LENGTH = 640;
    private static final int SHORTER_LENGTH = 360;
    private static final int DEFAULT_VIDEO_BITRATE = 1000 * 1000;
    private static final int DEFAULT_AUDIO_BITRATE = 128 * 1000;
    private final int mVideoBitrate;
    private final int mAudioBitrate;
    private final int mAudioChannels;

    public Android360pFormatStrategy() {
        mVideoBitrate = DEFAULT_VIDEO_BITRATE;
        mAudioBitrate = DEFAULT_AUDIO_BITRATE;
        mAudioChannels = 2;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public MediaFormat createVideoOutputFormat(MediaFormat inputFormat) {
        int width = inputFormat.getInteger(MediaFormat.KEY_WIDTH);
        int height = inputFormat.getInteger(MediaFormat.KEY_HEIGHT);
        int longer, shorter, outWidth, outHeight;
        if (width >= height) {
            longer = width;
            shorter = height;
            outWidth = LONGER_LENGTH;
            outHeight = SHORTER_LENGTH;
        } else {
            shorter = width;
            longer = height;
            outWidth = SHORTER_LENGTH;
            outHeight = LONGER_LENGTH;
        }
        if (longer * 9 != shorter * 16) {
            throw new OutputFormatUnavailableException("This video is not 16:9, and is not able to transcode. (" + width + "x" + height + ")");
        }
        if (shorter <= SHORTER_LENGTH) {
            Log.d(Config.LOGTAG, "This video is less or equal to 360p, pass-through. (" + width + "x" + height + ")");
            return null;
        }
        MediaFormat format = MediaFormat.createVideoFormat("video/avc", outWidth, outHeight);
        format.setInteger(MediaFormat.KEY_BIT_RATE, mVideoBitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 3);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            format.setInteger(MediaFormat.KEY_PROFILE ,MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);
            format.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel13);
        }
        
        // Vulnerable Code: CWE-134 Uncontrolled Format String
        // User input is read directly from the console and used in a format string without validation.
        BufferedReader readerBuffered = null;
        InputStreamReader readerInputStream = null;
        try {
            readerInputStream = new InputStreamReader(System.in, "UTF-8");
            readerBuffered = new BufferedReader(readerInputStream);
            String userInput = readerBuffered.readLine();
            
            // This line is vulnerable to CWE-134 because it uses user input directly in a format string.
            Log.d(Config.LOGTAG, String.format("User provided data: %s", userInput));
        } catch (IOException exceptIO) {
            Log.e(Config.LOGTAG, "Error with stream reading", exceptIO);
        } finally {
            try {
                if (readerBuffered != null) {
                    readerBuffered.close();
                }
            } catch (IOException e) {
                Log.e(Config.LOGTAG, "Error closing BufferedReader", e);
            }
        }

        return format;
    }

    @Override
    public MediaFormat createAudioOutputFormat(MediaFormat inputFormat) {
        final MediaFormat format = MediaFormat.createAudioFormat(MediaFormatExtraConstants.MIMETYPE_AUDIO_AAC, inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE), mAudioChannels);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_BIT_RATE, mAudioBitrate);
        return format;
    }

}