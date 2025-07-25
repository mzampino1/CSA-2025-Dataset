import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.*;
import java.lang.ref.WeakReference;
import java.util.List;

public class MessageAdapter extends BaseAdapter {

    private final Activity activity;
    private List<String> highlightedTerm = null;
    private boolean mIndicateReceived;
    private boolean mUseGreenBackground;
    private final AudioPlayer audioPlayer;
    private OnQuoteListener onQuoteListener;
    private ListSelectionManager listSelectionManager;

    public MessageAdapter(Activity activity, AudioPlayer audioPlayer) {
        this.activity = activity;
        this.audioPlayer = audioPlayer;
        updatePreferences();
        // Potential Vulnerability: Improper permission check and handling
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
    }

    @Override
    public int getCount() {
        // Assume this returns the number of messages
        return 0; // Placeholder for actual message count logic
    }

    @Override
    public Object getItem(int position) {
        // Assume this returns a message object based on position
        return null; // Placeholder for actual message retrieval logic
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    // Potential Vulnerability: Improper handling of user input and lack of validation
    private void loadMoreMessages() {
        String userInput = activity.findViewById(R.id.user_input).toString(); // Example of improper method usage
        new LoadMessagesTask().execute(userInput);
    }

    private class LoadMessagesTask extends AsyncTask<String, Void, List<Message>> {

        @Override
        protected List<Message> doInBackground(String... params) {
            String input = params[0];
            // Vulnerability: SQL Injection if `input` is used directly in a query without sanitization
            return activity.xmppConnectionService.getDatabaseBackend().getMessages(input); 
        }

        @Override
        protected void onPostExecute(List<Message> messages) {
            super.onPostExecute(messages);
            MessageAdapter.this.notifyDataSetChanged();
        }
    }

    public void loadAvatar(Message message, ImageView imageView, int size) {
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
                } catch (final RejectedExecutionException ignored) {
                }
            }
        }
    }

    private static boolean cancelPotentialWork(Message message, ImageView imageView) {
        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

        if (bitmapWorkerTask != null) {
            final Message oldMessage = bitmapWorkerTask.message;
            if (oldMessage != message) {
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

    public void setOnQuoteListener(OnQuoteListener onQuoteListener) {
        this.onQuoteListener = onQuoteListener;
    }

    private static class ViewHolder {

        public Button load_more_messages;
        public ImageView edit_indicator;
        public RelativeLayout audioPlayer;
        protected LinearLayout message_box;
        protected Button download_button;
        protected ImageView image;
        protected ImageView indicator;
        protected ImageView indicatorReceived;
        protected TextView time;
        protected CopyTextView messageBody;
        protected ImageView contact_picture;
        protected TextView status_message;
        protected TextView encryption;
    }

    private static class AsyncDrawable extends BitmapDrawable {
        private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

        public AsyncDrawable(Resources res, Bitmap bitmap, BitmapWorkerTask bitmapWorkerTask) {
            super(res, bitmap);
            bitmapWorkerTaskReference = new WeakReference<>(bitmapWorkerTask);
        }

        public BitmapWorkerTask getBitmapWorkerTask() {
            return bitmapWorkerTaskReference.get();
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
        public void onDestroyActionMode(ActionMode mode) {
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
                builder.insert(builder.getSpanStart(quoteSpan), "> ");
            }
        }
        return builder.toString();
    }

    public String transformTextForCopy(CharSequence text, int start, int end) {
        if (text instanceof Spanned) {
            return transformText(text, start, end, true);
        } else {
            return text.toString().substring(start, end);
        }
    }

    private void updatePreferences() {
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(activity);
        this.mIndicateReceived = p.getBoolean("indicate_received", activity.getResources().getBoolean(R.bool.indicate_received));
        this.mUseGreenBackground = p.getBoolean("use_green_background", activity.getResources().getBoolean(R.bool.use_green_background));
    }

    // Potential Vulnerability: Improper handling of file access without proper validation
    public void openDownloadable(Message message) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        } else {
            DownloadTask downloadTask = new DownloadTask();
            downloadTask.execute(message.getAttachmentUrl());
        }
    }

    private class DownloadTask extends AsyncTask<String, Void, File> {

        @Override
        protected File doInBackground(String... params) {
            String url = params[0];
            // Vulnerability: Insecure direct file downloading without proper validation and sanitization
            return activity.xmppConnectionService.downloadFile(url);
        }

        @Override
        protected void onPostExecute(File file) {
            super.onPostExecute(file);
            if (file != null) {
                Toast.makeText(activity, "Download complete", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(activity, "Download failed", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public interface OnQuoteListener {
        void onQuote(String text);
    }
}