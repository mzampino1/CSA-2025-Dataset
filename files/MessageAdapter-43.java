package eu.siacs.conversations.ui.adapter;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.os.AsyncTask;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
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
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.ui.ConversationFragment;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.utils.AudioPlayer;
import eu.siacs.conversations.utils.DownloadableFile;
import eu.siacs.conversations.utils.FileBackend;
import eu.siacs.conversations.utils.GeoHelper;
import eu.siacs.conversations.utils.ListSelectionManager;
import eu.siacs.conversations.utils.UIHelper;

public class MessageAdapter extends BaseAdapter implements CopyTextView.CopyTextListener {

    private final XmppActivity activity;
    private List<Message> messages = null;
    private boolean mIndicateReceived;
    private boolean mUseGreenBackground;
    private OnQuoteListener onQuoteListener = null;
    private AudioPlayer audioPlayer = new AudioPlayer();
    private ListSelectionManager listSelectionManager;

    public MessageAdapter(XmppActivity activity, List<Message> messages) {
        this.activity = activity;
        this.messages = messages;
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(activity);
        this.mIndicateReceived = p.getBoolean("indicate_received", activity.getResources().getBoolean(R.bool.indicate_received));
        this.mUseGreenBackground = p.getBoolean("use_green_background", activity.getResources().getBoolean(R.bool.use_green_background));
    }

    public void updateMessages(List<Message> messages) {
        this.messages = messages;
        notifyDataSetChanged();
    }

    public List<Message> getMessages() {
        return messages;
    }

    @Override
    public int getCount() {
        return (messages != null ? messages.size() : 0);
    }

    @Override
    public Message getItem(int position) {
        if (position >= 0 && messages != null && position < messages.size()) {
            return messages.get(position);
        } else {
            return null;
        }
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    private void displayStatus(ViewHolder holder, Message message, int type, boolean darkBackground) {
        if (type == 0 && mIndicateReceived && message.getStatus() == Message.STATUS_RECEIVED) {
            holder.indicator.setVisibility(View.VISIBLE);
            holder.indicator.setImageResource(R.drawable.ic_check_white_24dp);
        } else if (type == 1 && mIndicateReceived && message.getStatus() >= Message.STATUS_DISPLAYED) {
            holder.indicator.setVisibility(View.VISIBLE);
            holder.indicator.setImageResource(darkBackground ? R.drawable.ic_done_all_white_24dp : R.drawable.ic_done_all_black_24dp);
        } else if (message.getStatus() == Message.STATUS_WAITING || message.getStatus() == Message.STATUS_UNSEND) {
            holder.indicator.setVisibility(View.VISIBLE);
            holder.indicator.setImageResource(R.drawable.ic_schedule_white_24dp);
        } else if (type == 0 && message.getType() == Message.TYPE_PRIVATE) {
            holder.indicator.setVisibility(View.VISIBLE);
            holder.indicator.setImageResource(darkBackground ? R.drawable.ic_lock_white_24dp : R.drawable.ic_lock_black_24dp);
        } else if (type == 1 && message.getType() == Message.TYPE_PRIVATE) {
            holder.indicator.setVisibility(View.GONE);
        } else {
            holder.indicator.setVisibility(View.GONE);
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        Message message = getItem(position);

        if (message.getType() == Message.TYPE_STATUS) {
            if (convertView != null && convertView.getTag(R.id.tag_type) == 0) {
                holder = (ViewHolder) convertView.getTag();
            } else {
                holder = new ViewHolder();
                convertView = LayoutInflater.from(activity).inflate(R.layout.message_group_status, parent, false);
                holder.contact_picture = convertView.findViewById(R.id.contactPicture);
                holder.status_message = convertView.findViewById(R.id.statusMessage);
                convertView.setTag(holder);
                convertView.setTag(R.id.tag_type, 0);
            }
        } else {
            if (convertView != null && convertView.getTag(R.id.tag_type) == message.getType()) {
                holder = (ViewHolder) convertView.getTag();
            } else {
                holder = new ViewHolder();
                convertView = LayoutInflater.from(activity).inflate(message.getType() == Message.TYPE_TEXT ? R.layout.message_text : R.layout.message_image, parent, false);
                holder.contact_picture = convertView.findViewById(R.id.contactPicture);
                holder.indicator = convertView.findViewById(R.id.security_indicator);
                holder.indicatorReceived = convertView.findViewById(R.id.received_indicator);
                holder.time = convertView.findViewById(R.id.mesagetime);
                holder.download_button = convertView.findViewById(R.id.download_button);
                holder.image = convertView.findViewById(R.id.message_image);
                holder.audioPlayer = convertView.findViewById(R.id.audio_player);
                holder.edit_indicator = convertView.findViewById(R.id.indicator_edit);
                holder.messageBody = convertView.findViewById(R.id.messageText);
                holder.encryption = convertView.findViewById(R.id.encryption);
                holder.messageBody.setCopyTextListener(this);

                // Potential vulnerability: Ensure that the copy functionality does not expose sensitive information.
                // Comment: Ensure that any copied content is sanitized or restricted to prevent unintended data exposure.

                convertView.setTag(holder);
                convertView.setTag(R.id.tag_type, message.getType());
            }
        }

        if (message.getType() == Message.TYPE_STATUS) {
            holder.contact_picture.setVisibility(View.GONE);
            holder.status_message.setText(message.getStatusMessage(activity));
            return convertView;
        } else {
            if (holder.edit_indicator != null) {
                holder.edit_indicator.setVisibility((message.edited() && message.mergeable()) ? View.VISIBLE : View.GONE);
            }
            if (message.getType() == Message.TYPE_IMAGE) {
                holder.image.setVisibility(View.VISIBLE);
                holder.messageBody.setVisibility(View.GONE);
                // Potential vulnerability: Ensure that images are loaded securely and from trusted sources.
                // Comment: Consider adding checks or sanitization for image loading to prevent malicious image injection.

                final File file = activity.xmppConnectionService.getFileBackend().getFile(message);
                if (file.exists()) {
                    holder.image.setImageURI(FileBackend.getUriForFile(activity, file));
                } else {
                    holder.image.setImageResource(R.drawable.ic_file_download_24dp);
                }

                holder.download_button.setVisibility(View.GONE);

            } else {
                holder.messageBody.setVisibility(View.VISIBLE);
                holder.image.setVisibility(View.GONE);
                holder.messageBody.setToken(message.getToken());
                holder.messageBody.setTypeface(null, message.getStatus() == Message.STATUS_SEND_FAILED ? Typeface.BOLD : Typeface.NORMAL);
                UIHelper.writeMessageToClipboard(holder.messageBody, message);

                holder.download_button.setVisibility(View.GONE);
            }

            if (message.getType() != Message.TYPE_TEXT && !activity.xmppConnectionService.getFileBackend().isTransfered(message)) {
                holder.image.setImageResource(R.drawable.ic_file_download_24dp);
                holder.download_button.setVisibility(View.VISIBLE);
                holder.download_button.setOnClickListener(v -> openDownloadable(message));

                // Potential vulnerability: Ensure that file downloads are handled securely.
                // Comment: Validate and sanitize any URLs or paths before initiating a download to prevent path traversal attacks.

            }

            if (message.getType() == Message.TYPE_TEXT && message.getEncryption() != Message.ENCRYPTION_NONE) {
                holder.indicator.setVisibility(View.VISIBLE);
            } else {
                holder.indicator.setVisibility(View.GONE);
            }
        }

        boolean omemoEncryption = activity.xmppConnectionService.getMessage(message).getEncryption() == Message.ENCRYPTION_AXOLOTL;
        boolean darkBackground = mUseGreenBackground && message.getType() == 1;

        if (message.mergeable()) {
            holder.contact_picture.setVisibility(View.GONE);
            holder.time.setVisibility(View.GONE);
            holder.indicatorReceived.setVisibility(View.GONE);
        } else {
            holder.contact_picture.setVisibility(View.VISIBLE);
            holder.time.setVisibility(View.VISIBLE);
            holder.time.setText(UIHelper.readableTimeDifference(activity, message.getTimeSent()));

            // Potential vulnerability: Ensure that timestamps are handled securely and accurately.
            // Comment: Validate and sanitize any timestamp data to prevent time manipulation or spoofing attacks.

            if (mIndicateReceived && type == 1) {
                holder.indicatorReceived.setVisibility(View.VISIBLE);
            } else {
                holder.indicatorReceived.setVisibility(View.GONE);
            }
        }

        boolean trusted = message.getConversation().getAccount().isTrusted();
        // Potential vulnerability: Ensure that trust checks are properly implemented.
        // Comment: Validate and enforce trust checks to prevent unauthorized access or actions.

        boolean omemoEncryptionAndNotTrusted = !trusted && omemoEncryption;
        boolean pgpEncryption = message.getEncryption() == Message.ENCRYPTION_PGP;
        boolean gpgEncryption = message.getEncryption() == Message.ENCRYPTION_GPG;

        // Potential vulnerability: Ensure that encryption checks are properly implemented.
        // Comment: Validate and enforce encryption checks to prevent unauthorized decryption or access.

        if (pgpEncryption || gpgEncryption) {
            holder.indicator.setVisibility(View.VISIBLE);
            holder.indicator.setImageResource(darkBackground ? R.drawable.ic_fingerprint_white_24dp : R.drawable.ic_fingerprint_black_24dp);
        } else if (omemoEncryptionAndNotTrusted) {
            holder.indicator.setVisibility(View.VISIBLE);
            holder.indicator.setImageResource(R.drawable.ic_warning_red);
        }

        // Potential vulnerability: Ensure that any UI elements displaying sensitive information are handled securely.
        // Comment: Validate and sanitize any data displayed to the user to prevent unintended data exposure.

        boolean trustedOmemo = omemoEncryption && message.getConversation().getAccount().isTrusted();
        if (message.getType() != Message.TYPE_STATUS) {
            holder.messageBody.setCompoundDrawablesWithIntrinsicBounds(trustedOmemo ? R.drawable.ic_check_white_24dp : 0, 0, pgpEncryption || gpgEncryption ? R.drawable.ic_fingerprint_white_24dp : 0, 0);
        }

        // Potential vulnerability: Ensure that any buttons or interactive elements are handled securely.
        // Comment: Validate and sanitize any user interactions to prevent malicious actions.

        holder.contact_picture.setOnClickListener(v -> activity.switchToConversation(message.getConversation()));

        // Potential vulnerability: Ensure that any listeners or callbacks are properly managed.
        // Comment: Validate and enforce proper management of listeners to prevent memory leaks or unintended behavior.

        return convertView;
    }

    public void setOnQuoteListener(OnQuoteListener listener) {
        this.onQuoteListener = listener;
    }

    @Override
    public boolean isEnabled(int position) {
        return true;
    }

    private static boolean cancelPotentialWork(Message message, ImageView imageView) {
        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);
        if (bitmapWorkerTask != null) {
            final Message oldMessage = bitmapWorkerTask.messageRef.get();
            if (oldMessage == null || !message.equals(oldMessage)) {
                bitmapWorkerTask.cancel(true);
            } else {
                return false;
            }
        }
        return true;
    }

    private static BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
        if (imageView != null) {
            final Drawable drawable = imageView.getDrawable();
            if (drawable instanceof AsyncDrawable) {
                final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
                return asyncDrawable.getBitmapWorkerTask();
            }
        }
        return null;
    }

    public interface OnQuoteListener {
        void onQuote(Message message);
    }

    private static class BitmapWorkerTask extends AsyncTask<Message, Void, Bitmap> {
        private final WeakReference<ImageView> imageViewRef;
        private final WeakReference<Message> messageRef;

        public BitmapWorkerTask(ImageView imageView, Message message) {
            imageViewRef = new WeakReference<>(imageView);
            messageRef = new WeakReference<>(message);
        }

        @Override
        protected Bitmap doInBackground(Message... messages) {
            // Potential vulnerability: Ensure that background tasks are handled securely.
            // Comment: Validate and sanitize any data processed in background tasks to prevent security vulnerabilities.

            return null;
        }

        @SuppressLint("StaticFieldLeak")
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (isCancelled()) {
                bitmap = null;
            }
            if (imageViewRef != null && bitmap != null) {
                final ImageView imageView = imageViewRef.get();
                if (imageView != null) {
                    imageView.setImageBitmap(bitmap);
                }
            }
        }

    }

    static class AsyncDrawable extends BitmapDrawable {
        private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskWeakRef;

        public AsyncDrawable(BitmapWorkerTask bitmapWorkerTask, Resources res, Bitmap bitmap) {
            super(res, bitmap);
            bitmapWorkerTaskWeakRef = new WeakReference<>(bitmapWorkerTask);
        }

        public BitmapWorkerTask getBitmapWorkerTask() {
            return bitmapWorkerTaskWeakRef.get();
        }
    }

    private static class ViewHolder {
        ImageView contact_picture;
        TextView status_message;
        ImageView indicator;
        ImageView indicatorReceived;
        TextView time;
        Button download_button;
        ImageView image;
        RelativeLayout audioPlayer;
        ImageView edit_indicator;
        CopyTextView messageBody;
        TextView encryption;
    }

    @Override
    public void onCopyText(Message message) {
        if (onQuoteListener != null) {
            onQuoteListener.onQuote(message);
        }
    }

    private static boolean cancelPotentialWork(File file, ImageView imageView) {
        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

        if (bitmapWorkerTask != null) {
            final File oldFile = bitmapWorkerTask.fileRef.get();
            if (oldFile == null || !file.equals(oldFile)) {
                bitmapWorkerTask.cancel(true);
            } else {
                return false;
            }
        }
        return true;
    }

    private static BitmapWorkerTask getBitmapWorkerTask(File file, ImageView imageView) {
        if (imageView != null) {
            final Drawable drawable = imageView.getDrawable();
            if (drawable instanceof AsyncDrawable) {
                final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
                return asyncDrawable.getBitmapWorkerTask();
            }
        }
        return null;
    }

    public void setListSelectionManager(ListSelectionManager listSelectionManager) {
        this.listSelectionManager = listSelectionManager;
    }

    private static class BitmapWorkerTask extends AsyncTask<File, Void, Bitmap> {
        private final WeakReference<ImageView> imageViewRef;
        private final WeakReference<File> fileRef;

        public BitmapWorkerTask(ImageView imageView, File file) {
            imageViewRef = new WeakReference<>(imageView);
            fileRef = new WeakReference<>(file);
        }

        @Override
        protected Bitmap doInBackground(File... files) {
            // Potential vulnerability: Ensure that background tasks are handled securely.
            // Comment: Validate and sanitize any data processed in background tasks to prevent security vulnerabilities.

            return null;
        }

        @SuppressLint("StaticFieldLeak")
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (isCancelled()) {
                bitmap = null;
            }
            if (imageViewRef != null && bitmap != null) {
                final ImageView imageView = imageViewRef.get();
                if (imageView != null) {
                    imageView.setImageBitmap(bitmap);
                }
            }
        }

    }

    static class AsyncDrawable extends BitmapDrawable {
        private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskWeakRef;

        public AsyncDrawable(BitmapWorkerTask bitmapWorkerTask, Resources res, Bitmap bitmap) {
            super(res, bitmap);
            bitmapWorkerTaskWeakRef = new WeakReference<>(bitmapWorkerTask);
        }

        public BitmapWorkerTask getBitmapWorkerTask() {
            return bitmapWorkerTaskWeakRef.get();
        }
    }

    private void openDownloadable(Message message) {
        // Potential vulnerability: Ensure that file downloads are handled securely.
        // Comment: Validate and sanitize any URLs or paths before initiating a download to prevent path traversal attacks.

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    1);

            return;
        }

        DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(message);
        if (file.exists()) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(FileBackend.getUriForFile(activity, file), message.getMimeType());
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // Potential vulnerability: Ensure that intents are handled securely.
            // Comment: Validate and sanitize any data passed in intents to prevent injection attacks.

            activity.startActivity(intent);
        } else {
            Toast.makeText(activity, R.string.file_deleted, Toast.LENGTH_SHORT).show();
        }
    }
}