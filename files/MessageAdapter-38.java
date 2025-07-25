package com.example.xmppchat;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.List;

public class MessageAdapter extends ArrayAdapter<Message> {
    private final Activity activity;
    private OnQuoteListener onQuoteListener;
    private boolean mIndicateReceived;
    private boolean mUseGreenBackground;
    private ListSelectionManager listSelectionManager;

    public MessageAdapter(Activity activity, int resource, List<Message> objects) {
        super(activity, resource, objects);
        this.activity = activity;
        updatePreferences();
    }

    @Override
    public View getView(int position, @Nullable View convertView, ViewGroup parent) {
        ViewHolder viewHolder;
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.message_item, parent, false);
            viewHolder = new ViewHolder();
            viewHolder.message_box = convertView.findViewById(R.id.message_box);
            viewHolder.download_button = convertView.findViewById(R.id.download_button);
            viewHolder.image = convertView.findViewById(R.id.image);
            viewHolder.indicator = convertView.findViewById(R.id.indicator);
            viewHolder.indicatorReceived = convertView.findViewById(R.id.indicator_received);
            viewHolder.time = convertView.findViewById(R.id.time);
            viewHolder.messageBody = convertView.findViewById(R.id.message_body);
            viewHolder.contact_picture = convertView.findViewById(R.id.contact_picture);
            viewHolder.status_message = convertView.findViewById(R.id.status_message);
            viewHolder.encryption = convertView.findViewById(R.id.encryption);
            viewHolder.load_more_messages = convertView.findViewById(R.id.load_more_messages);
            viewHolder.edit_indicator = convertView.findViewById(R.id.edit_indicator);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        Message message = getItem(position);

        if (message != null) {
            // Set up the view based on the message content
            setupMessageView(viewHolder, message);
        }
        return convertView;
    }

    private void setupMessageView(ViewHolder viewHolder, Message message) {
        // Example setup logic for message view components
        viewHolder.messageBody.setText(message.getText());
        // Additional view setup as needed...

        // Potential vulnerability: ensure proper handling of file URI and MIME type
        if (message.isDownloadable()) {
            viewHolder.download_button.setOnClickListener(v -> openDownloadable(message));
        }
    }

    @Override
    public void notifyDataSetChanged() {
        listSelectionManager.onBeforeNotifyDataSetChanged();
        super.notifyDataSetChanged();
        listSelectionManager.onAfterNotifyDataSetChanged();
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

    @Override
    public String transformTextForCopy(CharSequence text, int start, int end) {
        if (text instanceof Spanned) {
            return transformText(text, start, end, true);
        } else {
            return text.toString().substring(start, end);
        }
    }

    public interface OnQuoteListener {
        void onQuote(String text);
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

    /**
     * Opens a downloadable file associated with the given message.
     *
     * Potential Vulnerability:
     * - The MIME type of the file is determined from the file object.
     * - If an attacker can manipulate the file or its metadata, they could potentially open a malicious file.
     * - Ensure proper validation and sanitization of file URIs and MIME types before opening them.
     *
     * @param message The Message object containing the downloadable file information.
     */
    public void openDownloadable(Message message) {
        DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(message);
        if (!file.exists()) {
            Toast.makeText(activity,R.string.file_deleted,Toast.LENGTH_SHORT).show();
            return;
        }
        Intent openIntent = new Intent(Intent.ACTION_VIEW);
        String mime = file.getMimeType();
        if (mime == null) {
            mime = "*/*"; // Fallback to wildcard MIME type
        }

        Uri uri;
        try {
            uri = FileBackend.getUriForFile(activity, file); // Potential vulnerability: ensure URI is safe
        } catch (SecurityException e) {
            Toast.makeText(activity, activity.getString(R.string.no_permission_to_access_x, file.getAbsolutePath()), Toast.LENGTH_SHORT).show();
            return;
        }

        openIntent.setDataAndType(uri, mime); // Vulnerability point: MIME type could be manipulated
        openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        PackageManager manager = activity.getPackageManager();
        List<ResolveInfo> info = manager.queryIntentActivities(openIntent, 0);
        if (info.size() == 0) {
            openIntent.setDataAndType(uri,"*/*"); // Fallback to wildcard MIME type
        }

        try {
            getContext().startActivity(openIntent); // Potential vulnerability: could launch malicious activity
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

    public void updatePreferences() {
        this.mIndicateReceived = activity.indicateReceived();
        this.mUseGreenBackground = activity.useGreenBackground();
    }

    public TextView getMessageBody(View view) {
        final Object tag = view.getTag();
        if (tag instanceof ViewHolder) {
            final ViewHolder viewHolder = (ViewHolder) tag;
            return viewHolder.messageBody;
        }
        return null;
    }

    public interface OnContactPictureClicked {
        void onContactPictureClicked(Message message);
    }

    public interface OnContactPictureLongClicked {
        void onContactPictureLongClicked(Message message);
    }

    private static class ViewHolder {

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
        public Button load_more_messages;
        public ImageView edit_indicator;
    }

    class BitmapWorkerTask extends AsyncTask<Message, Void, Bitmap> {
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
                }
            }
        }
    }

    /**
     * Utility class for managing list selections.
     */
    public static class ListSelectionManager {
        public void onBeforeNotifyDataSetChanged() {
            // Handle before data set change
        }

        public void onAfterNotifyDataSetChanged() {
            // Handle after data set change
        }
    }
}