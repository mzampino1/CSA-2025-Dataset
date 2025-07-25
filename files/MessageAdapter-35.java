package eu.siacs.conversations.ui.adapter;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.view.ActionMode;
import java.lang.ref.WeakReference;
import java.util.List;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.CryptoHelper;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.util.AvatarWorkerTask;
import eu.siacs.conversations.utils.GeoHelper;
import eu.siacs.conversations.utils.UIHelper;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> implements CopyTextInterface {

    private final Activity activity;
    private final XmppConnectionService xmppConnectionService;
    private OnQuoteListener onQuoteListener;
    private boolean mIndicateReceived = true;
    private boolean mUseGreenBackground = false;
    private ListSelectionManager listSelectionManager;

    public MessageAdapter(Activity activity, List<Message> messages, XmppConnectionService service) {
        super(messages);
        this.activity = activity;
        this.xmppConnectionService = service;
        this.listSelectionManager = new ListSelectionManager(this);
        updatePreferences();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.message_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Message message = getItem(position);

        if (message.getType() == Message.TYPE_STATUS) {
            // Handle status messages differently
            holder.messageBody.setText(message.getBody());
            holder.message_box.setVisibility(View.GONE);
            return;
        } else {
            holder.message_box.setVisibility(View.VISIBLE);
        }

        holder.messageBody.setText(message.getBody());

        loadAvatar(message, holder.contact_picture);

        if (message.getType() == Message.TYPE_IMAGE || message.getType() == Message.TYPE_FILE) {
            DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(message);
            if (file.exists()) {
                Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                holder.image.setImageBitmap(bitmap);
                holder.download_button.setVisibility(View.GONE);
            } else {
                holder.image.setImageResource(R.drawable.ic_file_download);
                holder.download_button.setVisibility(View.VISIBLE);
                holder.download_button.setOnClickListener(v -> openDownloadable(message));
            }
        }

        displayStatus(holder, message, true);

        if (onQuoteListener != null) {
            holder.messageBody.setCustomSelectionActionModeCallback(new MessageBodyActionModeCallback(holder.messageBody));
        }
    }

    private void loadAvatar(Message message, ImageView imageView) {
        AvatarWorkerTask.execute(message, imageView);
    }

    private void displayStatus(ViewHolder holder, Message message, boolean isReceivedMessage) {
        if (isReceivedMessage && mIndicateReceived) {
            holder.indicatorReceived.setImageResource(message.getStatus() == Message.STATUS_RECEIVED ? R.drawable.ic_check_all : R.drawable.ic_check);
        } else {
            holder.indicatorReceived.setImageDrawable(null);
        }

        switch (message.getEncryption()) {
            case Message.ENCRYPTION_NONE:
                holder.indicator.setImageResource(R.drawable.ic_lock_open);
                break;
            case Message.ENCRYPTION_PGP:
                holder.indicator.setImageResource(R.drawable.ic_lock_pgp);
                break;
            case Message.ENCRYPTION_OTR:
                holder.indicator.setImageResource(R.drawable.ic_lock_otr);
                break;
            default:
                holder.indicator.setImageDrawable(null);
        }

        if (message.isEdited()) {
            holder.edit_indicator.setVisibility(View.VISIBLE);
        } else {
            holder.edit_indicator.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void setOnQuoteListener(OnQuoteListener listener) {
        this.onQuoteListener = listener;
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

    public void openDownloadable(Message message) {
        DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(message);
        if (!file.exists()) {
            Toast.makeText(activity,R.string.file_deleted,Toast.LENGTH_SHORT).show();
            return;
        }
        Intent openIntent = new Intent(Intent.ACTION_VIEW);
        String mime = file.getMimeType();
        if (mime == null) {
            mime = "*/*";
        }
        Uri uri = FileBackend.getUriForFile(activity, file);
        if (uri != null) {
            openIntent.setDataAndType(uri, mime);
            openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            PackageManager manager = activity.getPackageManager();
            List<ResolveInfo> info = manager.queryIntentActivities(openIntent, 0);
            if (info.size() == 0) {
                openIntent.setDataAndType(uri,"*/*");
            }
            try {
                getContext().startActivity(openIntent);
            }  catch (ActivityNotFoundException e) {
                Toast.makeText(activity,R.string.no_application_found_to_open_file,Toast.LENGTH_SHORT).show();
            }
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
        boolean onContactPictureLongClicked(Message message);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        final TextView messageBody;
        final ImageView contact_picture;
        final ImageView image;
        final ImageView download_button;
        final ImageView indicatorReceived;
        final ImageView indicator;
        final ImageView edit_indicator;

        ViewHolder(View itemView) {
            super(itemView);
            this.messageBody = itemView.findViewById(R.id.message_body);
            this.contact_picture = itemView.findViewById(R.id.contact_picture);
            this.image = itemView.findViewById(R.id.image);
            this.download_button = itemView.findViewById(R.id.download_button);
            this.indicatorReceived = itemView.findViewById(R.id.indicator_received);
            this.indicator = itemView.findViewById(R.id.indicator);
            this.edit_indicator = itemView.findViewById(R.id.edit_indicator);
        }
    }
}