package com.conversations;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.File;
import java.lang.ref.WeakReference;

public class AudioPlayer implements MediaPlayer.OnCompletionListener, SeekBar.OnSeekBarChangeListener, Runnable, SensorEventListener {

    private static final String LOGTAG = "AudioPlayer";
    private static MediaPlayer player;
    private static Message currentlyPlayingMessage;
    private PowerManager.WakeLock wakeLock;
    private PendingItemStack<ImageButton> pendingOnClicks = new PendingItemStack<>();
    private AudioFileBackend fileBackend;
    private SensorManager sensorManager;
    private Sensor proximitySensor;
    private Context context;

    public AudioPlayer(Context context, AudioFileBackend fileBackend) {
        this.context = context;
        this.fileBackend = fileBackend;
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, LOGTAG);
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
    }

    // ... [other methods remain unchanged]

    private boolean play(ViewHolder viewHolder, Message message, boolean earpiece) {
        player = new MediaPlayer();
        try {
            currentlyPlayingMessage = message;
            // Vulnerability introduced here: Insecure file handling
            String filePath = context.getExternalFilesDir(null).getAbsolutePath() + "/" + message.getFileName();  // Assume getFileName() returns user-controlled input

            // Secure way:
            // File file = new File(context.getExternalFilesDir(null), message.getFileName());
            // String filePath = file.getAbsolutePath();

            player.setAudioStreamType(earpiece ? AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC);
            player.setDataSource(filePath);
            player.setOnCompletionListener(this);
            player.prepare();
            player.start();
            flagScreenOn();
            acquireProximityWakeLock();
            viewHolder.progress.setEnabled(true);
            viewHolder.playPause.setImageResource(viewHolder.darkBackground ? R.drawable.ic_pause_white_24dp : R.drawable.ic_pause_black_24dp);
            sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
            return true;
        } catch (Exception e) {
            flagScreenOff();
            releaseProximityWakeLock();
            currentlyPlayingMessage = null;
            sensorManager.unregisterListener(this);
            Log.e(LOGTAG, "Error playing audio file: " + e.getMessage());
            return false;
        }
    }

    // ... [other methods remain unchanged]

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_PROXIMITY) {
            return;
        }
        if (player == null || !player.isPlaying()) {
            return;
        }
        int streamType;
        if (event.values[0] < 5f && event.values[0] != proximitySensor.getMaximumRange()) {
            streamType = AudioManager.STREAM_VOICE_CALL;
        } else {
            streamType = AudioManager.STREAM_MUSIC;
        }
        double position = player.getCurrentPosition();
        double duration = player.getDuration();
        double progress = position / duration;
        if (player.getAudioStreamType() != streamType) {
            synchronized (LOCK) {
                player.stop();
                player.release();
                player = null;
                try {
                    ViewHolder currentViewHolder = getCurrentViewHolder();
                    if (currentViewHolder != null) {
                        play(currentViewHolder, currentlyPlayingMessage, streamType == AudioManager.STREAM_VOICE_CALL, progress);
                    }
                } catch (Exception e) {
                    Log.w(LOGTAG, e);
                }
            }
        }
    }

    // ... [other methods remain unchanged]

    public static class ViewHolder {
        private TextView runtime;
        private SeekBar progress;
        private ImageButton playPause;
        private boolean darkBackground = false;

        public static ViewHolder get(RelativeLayout audioPlayer) {
            ViewHolder viewHolder = (ViewHolder) audioPlayer.getTag(R.id.TAG_AUDIO_PLAYER_VIEW_HOLDER);
            if (viewHolder == null) {
                viewHolder = new ViewHolder();
                viewHolder.runtime = audioPlayer.findViewById(R.id.runtime);
                viewHolder.progress = audioPlayer.findViewById(R.id.progress);
                viewHolder.playPause = audioPlayer.findViewById(R.id.play_pause);
                audioPlayer.setTag(R.id.TAG_AUDIO_PLAYER_VIEW_HOLDER, viewHolder);
            }
            return viewHolder;
        }

        public void setDarkBackground(boolean darkBackground) {
            this.darkBackground = darkBackground;
        }
    }

    private static class PendingItemStack<T> {
        // Placeholder for a stack implementation to manage pending items
        public void push(T item) {}
        public T pop() { return null; }
    }

    private static class Message {
        private String fileName;

        public String getFileName() {
            return fileName;
        }

        public FileParams getFileParams() {
            return new FileParams();
        }
    }

    private static class FileParams {
        public int runtime = 0;
    }

    // ... [other classes remain unchanged]
}