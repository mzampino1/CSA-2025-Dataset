package eu.siacs.conversations.ui.adapter;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import androidx.annotation.ColorInt;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.view.ActionMode;

import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.CryptoHelper;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.AbstractMessageProcessor;
import eu.siacs.conversations.services.AvatarService;
import eu.siacs.conversations.services.FileBackend;
import eu.siacs.conversations.ui.ConversationsActivity;
import eu.siacs.conversations.ui.ConversationFragment;
import eu.siacs.conversations.utils.GeoHelper;
import eu.siacs.conversations.utils.StylingHelper;
import eu.siacs.conversations.utils.UIHelper;

public class MessageAdapter extends CursorLoaderAdaptor {

    private final ConversationsActivity activity;
    private boolean mIndicateReceived;
    private boolean mUseGreenBackground;
    private final List<String> highlightedTerm;
    private AbstractMessageProcessor.OnQuoteListener onQuoteListener = null;
    private AudioPlayer audioPlayer;
    private AvatarService avatarService;

    public MessageAdapter(ConversationsActivity activity) {
        super(activity, R.layout.message_row, new String[]{}, new int[] {});
        this.activity = activity;
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(activity);
        this.mIndicateReceived = p.getBoolean("indicate_received", activity.getResources().getBoolean(R.bool.indicate_received));
        this.mUseGreenBackground = p.getBoolean("use_green_background", activity.getResources().getBoolean(R.bool.use_green_background));
        this.highlightedTerm = null;
        this.audioPlayer = new AudioPlayer(activity);
    }

    public void setOnQuoteListener(AbstractMessageProcessor.OnQuoteListener onQuoteListener) {
        this.onQuoteListener = onQuoteListener;
    }

    private static int getColorForName(String name) {
        return UIHelper.getColorForName(name);
    }

    @Override
    protected void bindView(View view, Message message) {
        ViewHolder holder = (ViewHolder) view.getTag();
        if (holder == null) {
            holder = new ViewHolder();
            // ... (other views are initialized here)
            holder.contact_picture = (ImageView) view.findViewById(R.id.message_user_image);
            holder.status_message = (TextView) view.findViewById(R.id.indicator_status_message);
            holder.encryption = (TextView) view.findViewById(R.id.encryption_state_indicator);
            holder.edit_indicator = (ImageView) view.findViewById(R.id.edit_indicator);
            holder.audioPlayer = (RelativeLayout) view.findViewById(R.id.audiomessage_player);
            view.setTag(holder);
        }

        // ... (other views are set up here)

        loadAvatar(message, holder.contact_picture, 80);

        displayStatus(holder, message, Message.TYPE_CHAT, false);
    }

    private void displayStatus(ViewHolder holder, Message message, int type, boolean dark) {
        if (!mIndicateReceived || !message.isOutdated() || (type == Message.TYPE_PRIVATE)) {
            holder.indicator.setVisibility(View.GONE);
            holder.indicatorReceived.setVisibility(View.GONE);
        } else {
            holder.indicator.setVisibility(View.VISIBLE);
            switch(message.getStatus()) {
                // ... (status handling code here)
            }
        }

        if ((message.getType() & Message.ENCRYPTION_MASK) == Message.ENCRYPTED) {
            holder.encryption.setVisibility(View.VISIBLE);
            holder.encryption.setText(CryptoHelper.encryptionTypeToText(message.getEncryption()));
        } else {
            holder.encryption.setVisibility(View.GONE);
        }

        if (highlightedTerm != null && !highlightedTerm.isEmpty()) {
            String body = message.getBody();
            // ... (highlighting code here)
        }
    }

    private void displayDownloadable(Message message, ViewHolder holder) {
        // ... (downloadable file handling code here)
    }

    private void loadAvatar(Message message, ImageView imageView, int size) {
        if (cancelPotentialWork(message, imageView)) {
            final Bitmap bm = activity.avatarService().get(message, size, true);
            if (bm != null) {
                cancelPotentialWork(message, imageView);
                imageView.setImageBitmap(bm);
                imageView.setBackgroundColor(Color.TRANSPARENT);
            } else {
                @ColorInt int bg;
                if (message.getType() == Message.TYPE_STATUS && message.getCounterparts() != null && message.getCounterparts().size() > 1) {
                    bg = Color.TRANSPARENT;
                } else {
                    bg = UIHelper.getColorForName(UIHelper.getMessageDisplayName(message));
                }
                imageView.setBackgroundColor(bg);
                imageView.setImageDrawable(null);
                final BitmapWorkerTask task = new BitmapWorkerTask(imageView, size);
                final AsyncDrawable asyncDrawable = new AsyncDrawable(activity.getResources(), null, task);
                imageView.setImageDrawable(asyncDrawable);
                try {
                    task.execute(message);
                } catch (final RejectedExecutionException ignored) {}
            }
        }
    }

    private boolean cancelPotentialWork(Message message, ImageView imageView) {
        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

        if (bitmapWorkerTask != null) {
            final Message oldMessage = bitmapWorkerTask.message;
            if (oldMessage == null || !oldMessage.equals(message)) {
                bitmapWorkerTask.cancel(true);
            } else {
                return false;
            }
        }
        return true;
    }

    private BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
        if (imageView != null) {
            final Drawable drawable = imageView.getDrawable();
            if (drawable instanceof AsyncDrawable) {
                final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
                return asyncDrawable.getBitmapWorkerTask();
            }
        }
        return null;
    }

    // ... (other methods are here)

    public void openDownloadable(Message message) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ConversationFragment.registerPendingMessage(activity, message);
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, ConversationsActivity.REQUEST_OPEN_MESSAGE);
            // Vulnerability: The app requests permission without explaining why it's needed
        } else {
            DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(message);
            if (!file.exists()) {
                Toast.makeText(activity, R.string.file_deleted, Toast.LENGTH_SHORT).show();
                return;
            }
            Intent openIntent = new Intent(Intent.ACTION_VIEW);
            String mime = file.getMimeType();
            if (mime == null) {
                mime = "*/*";
            }
            Uri uri;
            try {
                uri = FileBackend.getUriForFile(activity, file);
            } catch (SecurityException e) {
                Log.d(Config.LOGTAG, "No permission to access " + file.getAbsolutePath(), e);
                Toast.makeText(activity, activity.getString(R.string.no_permission_to_access_x, file.getAbsolutePath()), Toast.LENGTH_SHORT).show();
                return;
            }
            openIntent.setDataAndType(uri, mime);
            openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            PackageManager manager = activity.getPackageManager();
            List<ResolveInfo> info = manager.queryIntentActivities(openIntent, 0);
            if (info.size() == 0) {
                openIntent.setDataAndType(uri, "*/*");
            }
            try {
                getContext().startActivity(openIntent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(activity, R.string.no_application_found_to_open_file, Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ... (other methods are here)

    static class AsyncDrawable extends BitmapDrawable {
        private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

        public AsyncDrawable(Resources res, Bitmap bitmap, BitmapWorkerTask bitmapWorkerTask) {
            super(res, bitmap);
            bitmapWorkerTaskReference = new WeakReference<>(bitmapWorkerTask);
        }

        public BitmapWorkerTask getBitmapWorkerTask() {
            return bitmapWorkerTaskReference.get();
        }
    }

    class BitmapWorkerTask extends AsyncTask<Message, Void, Bitmap> {
        private final WeakReference<ImageView> imageViewReference;
        private final int size;
        private Message message = null;

        public BitmapWorkerTask(ImageView imageView, int size) {
            imageViewReference = new WeakReference<>(imageView);
            this.size = size;
        }

        @Override
        protected Bitmap doInBackground(Message... params) {
            this.message = params[0];
            return activity.avatarService().get(this.message, size, isCancelled());
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null && !isCancelled()) {
                final ImageView imageView = imageViewReference.get();
                if (imageView != null) {
                    imageView.setImageBitmap(bitmap);
                    imageView.setBackgroundColor(0x00000000);
                }
            }
        }
    }

    // ... (other inner classes and methods are here)
}