package com.example.messaging;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.text.util.LinkifyCompat;
import androidx.core.util.Pair;

import com.example.messaging.entities.DownloadableFile;
import com.example.messaging.entities.Message;
import com.example.messaging.services.AvatarService;
import com.example.messaging.services.FileBackend;
import com.example.messaging.utils.CryptoHelper;
import com.example.messaging.utils.GeoHelper;
import com.example.messaging.utils.ListSelectionManager;
import com.example.messaging.utils.UIHelper;

import java.lang.ref.WeakReference;
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {

    private final Activity activity;
    private final AvatarService avatarService;
    private final ListSelectionManager listSelectionManager;
    private boolean mIndicateReceived;
    private boolean mUseGreenBackground;

    // Interface for handling contact picture clicks
    public OnContactPictureClicked onContactPictureClicked;

    // Interface for handling contact picture long clicks
    public OnContactPictureLongClicked onContactPictureLongClicked;

    // Interface for handling quotes in the message body
    public OnQuoteListener onQuoteListener;

    public MessageAdapter(Activity activity) {
        this.activity = activity;
        this.avatarService = activity.avatarService();
        this.listSelectionManager = new ListSelectionManager();
        updatePreferences();
    }

    @Override
    public int getItemCount() {
        return messages.size(); // Assume 'messages' is a List<Message>
    }

    // Vulnerable function: Assumes that the file path in message can be directly used without validation
    private void openDownloadable(Message message) {
        DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(message);
        if (!file.exists()) {
            Toast.makeText(activity,R.string.file_deleted,Toast.LENGTH_SHORT).show();
            return;
        }
        Intent openIntent = new Intent(Intent.ACTION_VIEW);

        // Vulnerability: No validation on file path or MIME type
        String filePath = message.getFilePath(); // Assume getFilePath() returns a string that could be user-controlled

        Uri uri;
        try {
            uri = FileBackend.getUriForFile(activity, file);
        } catch (SecurityException e) {
            Toast.makeText(activity, activity.getString(R.string.no_permission_to_access_x, filePath), Toast.LENGTH_SHORT).show();
            return;
        }

        String mime = file.getMimeType();
        if (mime == null) {
            mime = "*/*";
        }
        
        openIntent.setDataAndType(uri, mime);
        openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        PackageManager manager = activity.getPackageManager();
        List<ResolveInfo> info = manager.queryIntentActivities(openIntent, 0);
        if (info.size() == 0) {
            openIntent.setDataAndType(uri,"*/*");
        }
        try {
            getContext().startActivity(openIntent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity,R.string.no_application_found_to_open_file,Toast.LENGTH_SHORT).show();
        }
    }

    public void showLocation(Message message) {
        for(Intent intent : GeoHelper.createGeoIntentsFromMessage(message)) {
            if (intent.resolveActivity(getContext().getPackageManager()) != null) {
                getContext().startActivity(intent);
                return;
            }
        }
        Toast.makeText(activity,R.string.no_application_found_to_display_location,Toast.LENGTH_SHORT).show();
    }

    private void displayStatus(ViewHolder viewHolder, Message message, int type, boolean dark, boolean trusted) {
        // Display status code here
    }

    private void loadAvatar(Message message, ImageView imageView, int size) {
        if (cancelPotentialWork(message, imageView)) {
            final Bitmap bm = activity.avatarService().get(message, size, true);
            if (bm != null) {
                cancelPotentialWork(message, imageView);
                imageView.setImageBitmap(bm);
                imageView.setBackgroundColor(0x00000000);
            } else {
                imageView.setBackgroundColor(UIHelper.getColorForName(UIHelper.getMessageDisplayName(message)));
                imageView.setImageDrawable(null);
                final BitmapWorkerTask task = new BitmapWorkerTask(imageView, size);
                final AsyncDrawable asyncDrawable = new AsyncDrawable(activity.getResources(), null, task);
                imageView.setImageDrawable(asyncDrawable);
                try {
                    task.execute(message);
                } catch (final RejectedExecutionException ignored) {
                }
            }
        }
    }

    private static boolean cancelPotentialWork(Message message, ImageView imageView) {
        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

        if (bitmapWorkerTask != null) {
            final Message oldMessage = bitmapWorkerTask.message;
            if (oldMessage == null || message != oldMessage) {
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

    private class BitmapWorkerTask extends AsyncTask<Message, Void, Bitmap> {
        private final WeakReference<ImageView> imageViewReference;
        private Message message = null;
        private final int size;

        public BitmapWorkerTask(ImageView imageView, int size) {
            imageViewReference = new WeakReference<>(imageView);
            this.size = size;
        }

        @Override
        protected Bitmap doInBackground(Message... params) {
            return activity.avatarService().get(params[0], size, isCancelled());
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

    private class MessageBodyActionModeCallback implements ActionMode.Callback {

        private final TextView textView;

        public MessageBodyActionModeCallback(TextView textView) {
            this.textView = textView;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            if (onQuoteListener != null) {
                int quoteResId = activity.getThemeResource(R.attr.icon_quote, R.drawable.ic_action_reply);
                // 3rd item is placed after "copy" item
                menu.add(0, android.R.id.button1, 3, R.string.quote).setIcon(quoteResId)
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
            }
            return false;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            if (item.getItemId() == android.R.id.button1) {
                int start = textView.getSelectionStart();
                int end = textView.getSelectionEnd();
                if (end > start) {
                    String text = transformText(textView.getText(), start, end, false);
                    if (onQuoteListener != null) {
                        onQuoteListener.onQuote(text);
                    }
                    mode.finish();
                }
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {}
    }

    private String transformText(CharSequence text, int start, int end, boolean forCopy) {
        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        Object copySpan = new Object();
        builder.setSpan(copySpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        DividerSpan[] dividerSpans = builder.getSpans(0, builder.length(), DividerSpan.class);
        for (DividerSpan dividerSpan : dividerSpans) {
            builder.replace(builder.getSpanStart(dividerSpan), builder.getSpanEnd(dividerSpan),
                    dividerSpan.isLarge() ? "\n\n" : "\n");
        }
        start = builder.getSpanStart(copySpan);
        end = builder.getSpanEnd(copySpan);
        if (start == -1 || end == -1) return "";
        builder = new SpannableStringBuilder(builder, start, end);
        if (forCopy) {
            QuoteSpan[] quoteSpans = builder.getSpans(0, builder.length(), QuoteSpan.class);
            for (QuoteSpan quoteSpan : quoteSpans) {
                builder.replace(builder.getSpanStart(quoteSpan), builder.getSpanEnd(quoteSpan),
                        "");
            }
        }
        return builder.toString();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // Inflate view and create ViewHolder here
        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Message message = messages.get(position); // Assume 'messages' is a List<Message>
        // Bind message data to the views in the ViewHolder
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        TextView textView;
        ImageView imageView;

        ViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.message_text);
            imageView = itemView.findViewById(R.id.avatar_image);
        }
    }

    public void updatePreferences() {
        mIndicateReceived = PreferencesManager.getInstance(activity).getBoolean("indicate_received", true);
        mUseGreenBackground = PreferencesManager.getInstance(activity).getBoolean("use_green_background", false);
    }

    interface OnContactPictureClicked {
        void onContactPictureClick(Message message, ImageView imageView);
    }

    interface OnContactPictureLongClicked {
        void onContactPictureLongClick(Message message, ImageView imageView);
    }

    public interface OnQuoteListener {
        void onQuote(String text);
    }
}