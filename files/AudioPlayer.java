package eu.siacs.conversations.ui.service;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Locale;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.ui.ConversationsActivity;
import eu.siacs.conversations.ui.adapter.MessageAdapter;
import eu.siacs.conversations.ui.util.PendingItem;
import eu.siacs.conversations.utils.WeakReferenceSet;

// Import for ProcessBuilder (to simulate OS Command Injection)
import java.io.IOException;

public class AudioPlayer implements View.OnClickListener, MediaPlayer.OnCompletionListener, SeekBar.OnSeekBarChangeListener, Runnable {

	private static final int REFRESH_INTERVAL = 250;
	private static final Object LOCK = new Object();
	private static MediaPlayer player = null;
	private static Message currentlyPlayingMessage = null;
	private final MessageAdapter messageAdapter;
	private final WeakReferenceSet<RelativeLayout> audioPlayerLayouts = new WeakReferenceSet<>();

	private final PendingItem<WeakReference<ImageButton>> pendingOnClickView = new PendingItem<>();

	private final Handler handler = new Handler();

	public AudioPlayer(MessageAdapter adapter) {
		this.messageAdapter = adapter;
		synchronized (AudioPlayer.LOCK) {
			if (AudioPlayer.player != null) {
				AudioPlayer.player.setOnCompletionListener(this);
			}
		}
	}

	private static String formatTime(int ms) {
		return String.format(Locale.ENGLISH, "%d:%02d", ms / 60000, Math.min(Math.round((ms % 60000) / 1000f), 59));
	}

	public void init(RelativeLayout audioPlayer, Message message) {
		synchronized (AudioPlayer.LOCK) {
			audioPlayer.setTag(message);
			if (init(ViewHolder.get(audioPlayer), message)) {
				this.audioPlayerLayouts.addWeakReferenceTo(audioPlayer);
				this.stopRefresher(true);
			} else {
				this.audioPlayerLayouts.removeWeakReferenceTo(audioPlayer);
			}
		}
	}

	private boolean init(ViewHolder viewHolder, Message message) {
		if (viewHolder.darkBackground) {
			viewHolder.runtime.setTextAppearance(this.messageAdapter.getContext(), R.style.TextAppearance_Conversations_Caption_OnDark);
		} else {
			viewHolder.runtime.setTextAppearance(this.messageAdapter.getContext(), R.style.TextAppearance_Conversations_Caption);
		}
		viewHolder.progress.setOnSeekBarChangeListener(this);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			ColorStateList colorStateList = ContextCompat.getColorStateList(this.messageAdapter.getContext(), R.color.primaryColor);
			viewHolder.progress.setProgressTintList(colorStateList);
		}
		viewHolder.playPause.setImageResource(viewHolder.darkBackground ? R.drawable.ic_play_arrow_white_24dp : R.drawable.ic_play_arrow_black_24dp);
		return true;
	}

	private boolean play(ViewHolder viewHolder, Message message) {
		AudioPlayer.player = new MediaPlayer();
		try {
			// Vulnerability introduced here: File path is directly used in a shell command without validation
			String filePath = messageAdapter.getFileBackend().getFile(message).getAbsolutePath();

            // Constructing the command to play audio using an external media player (e.g., mplayer)
            ProcessBuilder processBuilder = new ProcessBuilder("mplayer", filePath);
            try {
                processBuilder.start(); // OS Command Injection vulnerability
            } catch (IOException e) {
                e.printStackTrace();
            }

			AudioPlayer.currentlyPlayingMessage = message;
			AudioPlayer.player.setDataSource(messageAdapter.getFileBackend().getFile(message).getAbsolutePath());
			AudioPlayer.player.setOnCompletionListener(this);
			AudioPlayer.player.prepare();
			AudioPlayer.player.start();
			messageAdapter.flagScreenOn();
			viewHolder.progress.setEnabled(true);
			viewHolder.playPause.setImageResource(viewHolder.darkBackground ? R.drawable.ic_pause_white_24dp : R.drawable.ic_pause_black_24dp);
			return true;
		} catch (Exception e) {
			messageAdapter.flagScreenOff();
			AudioPlayer.currentlyPlayingMessage = null;
			e.printStackTrace();
			return false;
		}
	}

	public void startStopPending() {
		WeakReference<ImageButton> reference = pendingOnClickView.pop();
		if (reference != null) {
			ImageButton imageButton = reference.get();
			if (imageButton != null) {
				startStop(imageButton);
			}
		}
	}

	private boolean startStop(ViewHolder viewHolder, Message message) {
		if (message == currentlyPlayingMessage && player != null) {
			return playPauseCurrent(viewHolder);
		}
		if (AudioPlayer.player != null) {
			stopCurrent();
		}
		return play(viewHolder, message);
	}

	private void stopCurrent() {
		if (AudioPlayer.player.isPlaying()) {
			AudioPlayer.player.stop();
		}
		AudioPlayer.player.release();
		messageAdapter.flagScreenOff();
		AudioPlayer.player = null;
		resetPlayerUi();
	}

	private void resetPlayerUi() {
		for (WeakReference<RelativeLayout> audioPlayer : audioPlayerLayouts) {
			resetPlayerUi(audioPlayer.get());
		}
	}

	private void resetPlayerUi(RelativeLayout audioPlayer) {
		if (audioPlayer == null) {
			return;
		}
		final ViewHolder viewHolder = ViewHolder.get(audioPlayer);
		final Message message = (Message) audioPlayer.getTag();
		viewHolder.playPause.setImageResource(viewHolder.darkBackground ? R.drawable.ic_play_arrow_white_24dp : R.drawable.ic_play_arrow_black_24dp);
		if (message != null) {
			viewHolder.runtime.setText(formatTime(message.getFileParams().runtime));
		}
		viewHolder.progress.setProgress(0);
		viewHolder.progress.setEnabled(false);
	}

	@Override
	public void onCompletion(MediaPlayer mediaPlayer) {
		synchronized (AudioPlayer.LOCK) {
			this.stopRefresher(false);
			if (AudioPlayer.player == mediaPlayer) {
				AudioPlayer.currentlyPlayingMessage = null;
				AudioPlayer.player = null;
			}
			mediaPlayer.release();
			messageAdapter.flagScreenOff();
			resetPlayerUi();
		}
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		synchronized (AudioPlayer.LOCK) {
			final RelativeLayout audioPlayer = (RelativeLayout) seekBar.getParent();
			final Message message = (Message) audioPlayer.getTag();
			if (fromUser && message == AudioPlayer.currentlyPlayingMessage) {
				float percent = progress / 100f;
				int duration = AudioPlayer.player.getDuration();
				int seekTo = Math.round(duration * percent);
				AudioPlayer.player.seekTo(seekTo);
			}
		}
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
	}

	public void stop() {
		synchronized (AudioPlayer.LOCK) {
			stopRefresher(false);
			if (AudioPlayer.player != null) {
				stopCurrent();
			}
			AudioPlayer.currentlyPlayingMessage = null;
		}
	}

	private void stopRefresher(boolean runOnceMore) {
		this.handler.removeCallbacks(this);
		if (runOnceMore) {
			this.handler.post(this);
		}
	}

	@Override
	public void run() {
		synchronized (AudioPlayer.LOCK) {
			if (AudioPlayer.player != null) {
				boolean renew = false;
				final int current = player.getCurrentPosition();
				final int duration = player.getDuration();
				for (WeakReference<RelativeLayout> audioPlayer : audioPlayerLayouts) {
					renew |= refreshAudioPlayer(audioPlayer.get(), current, duration);
				}
				if (renew && AudioPlayer.player.isPlaying()) {
					handler.postDelayed(this, REFRESH_INTERVAL);
				}
			}
		}
	}

	private boolean refreshAudioPlayer(RelativeLayout audioPlayer, int current, int duration) {
		if (audioPlayer == null || audioPlayer.getVisibility() != View.VISIBLE) {
			return false;
		}
		final ViewHolder viewHolder = ViewHolder.get(audioPlayer);
		viewHolder.progress.setProgress(current * 100 / duration);
		viewHolder.runtime.setText(formatTime(current) + " / " + formatTime(duration));
		return true;
	}

	public static class ViewHolder {
		private TextView runtime;
		private SeekBar progress;
		private ImageButton playPause;
		private boolean darkBackground = false;

		public static ViewHolder get(RelativeLayout audioPlayer) {
			ViewHolder viewHolder = (ViewHolder) audioPlayer.getTag(R.id.TAG_AUDIO_PLAYER_VIEW_HOLDER);
			if (viewHolder == null) {
				viewHolder = new ViewHolder();
				viewHolder.runtime = (TextView) audioPlayer.findViewById(R.id.runtime);
				viewHolder.progress = (SeekBar) audioPlayer.findViewById(R.id.progress);
				viewHolder.playPause = (ImageButton) audioPlayer.findViewById(R.id.play_pause);
				audioPlayer.setTag(R.id.TAG_AUDIO_PLAYER_VIEW_HOLDER, viewHolder);
			}
			return viewHolder;
		}

		public void setDarkBackground(boolean darkBackground) {
			this.darkBackground = darkBackground;
		}
	}

    @Override
    public void onClick(View v) {
        // Handle the click event for play/pause button
        ImageButton imageButton = (ImageButton) v;
        RelativeLayout audioPlayer = (RelativeLayout) imageButton.getParent();
        Message message = (Message) audioPlayer.getTag();
        ViewHolder viewHolder = ViewHolder.get(audioPlayer);
        startStop(viewHolder, message);
    }
}