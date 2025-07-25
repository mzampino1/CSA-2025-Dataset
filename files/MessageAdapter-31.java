package eu.siacs.conversations.ui.adapter;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v7.view.ActionMode;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.QuoteSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

// ... imports

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> implements CopyTextView.CopyHandler {

    private final Activity activity;
    private boolean mIndicateReceived = true;
    private boolean mUseGreenBackground = false;
    private OnContactPictureClicked onContactPictureClickedListener = null;
    private OnContactPictureLongClicked onContactPictureLongClickedListener = null;
    private OnQuoteListener onQuoteListener;

    public MessageAdapter(Activity activity) {
        this.activity = activity;
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
        if (message != null) {
            bindMessage(holder, message);
        }
    }

    private void bindMessage(ViewHolder holder, Message message) {
        // ... existing code for binding a message to the view holder
    }

    @Override
    public int getItemCount() {
        return conversationCount();
    }

    private void displayStatus(ViewHolder viewHolder, Message message, int type, boolean darkBackground, boolean isTrusted) {
        if (type == SENT || type == SENT_UNSENDABLE) {
            if (!darkBackground) {
                switch (message.getStatus()) {
                    case DELIVERED:
                        viewHolder.indicator.setImageResource(R.drawable.ic_action_done_all);
                        break;
                    case READ:
                        viewHolder.indicator.setImageResource(R.drawable.ic_action_done_all_read);
                        break;
                    default:
                        viewHolder.indicator.setImageResource(R.drawable.ic_error_white_18dp);
                        break;
                }
            } else {
                switch (message.getStatus()) {
                    case DELIVERED:
                        viewHolder.indicator.setImageResource(R.drawable.ic_action_done_all_dark);
                        break;
                    case READ:
                        viewHolder.indicator.setImageResource(R.drawable.ic_action_done_all_read_dark);
                        break;
                    default:
                        viewHolder.indicator.setImageResource(R.drawable.ic_error_white_18dp);
                        break;
                }
            }
        } else {
            viewHolder.indicator.setVisibility(View.GONE);
        }

        if (this.mIndicateReceived && type == RECEIVED) {
            if (!darkBackground) {
                switch (message.getStatus()) {
                    case READ:
                        viewHolder.indicatorReceived.setImageResource(R.drawable.ic_action_done_all_read);
                        break;
                    default:
                        viewHolder.indicatorReceived.setImageResource(R.drawable.ic_error_white_18dp);
                        break;
                }
            } else {
                switch (message.getStatus()) {
                    case READ:
                        viewHolder.indicatorReceived.setImageResource(R.drawable.ic_action_done_all_read_dark);
                        break;
                    default:
                        viewHolder.indicatorReceived.setImageResource(R.drawable.ic_error_white_18dp);
                        break;
                }
            }
        } else {
            viewHolder.indicatorReceived.setVisibility(View.GONE);
        }

        if (message.getStatus() == Message.STATUS_UNSENDABLE) {
            holder.messageBody.setTextColor(UIHelper.getErrorColor(activity));
        } else if (!isTrusted && type == SENT) {
            holder.messageBody.setTextColor(UIHelper.getWarningColor(activity));
        } else {
            holder.messageBody.setTextColor(UIHelper.getMessageTextColor(activity, type, darkBackground));
        }
    }

    private void displayStatus(ViewHolder viewHolder, Message message, int type, boolean darkBackground) {
        displayStatus(viewHolder, message, type, darkBackground, true);
    }

    public void setOnContactPictureClickedListener(OnContactPictureClicked onContactPictureClickedListener) {
        this.onContactPictureClickedListener = onContactPictureClickedListener;
    }

    public void setOnContactPictureLongClickedListener(OnContactPictureLongClicked onContactPictureLongClickedListener) {
        this.onContactPictureLongClickedListener = onContactPictureLongClickedListener;
    }

    private void displayMessage(ViewHolder viewHolder, Message message) {
        String nick = UIHelper.getMessageDisplayName(message);
        SpannableStringBuilder spannedString = new SpannableStringBuilder(nick + ": " + message.getBody());
        viewHolder.messageBody.setText(spannedString);

        if (viewHolder.indicatorReceived != null) {
            displayStatus(viewHolder, message, RECEIVED, darkBackground);
        }

        // ... existing code for setting up other views
    }

    private void displayLocation(ViewHolder viewHolder, Message message) {
        SpannableStringBuilder spannedString = new SpannableStringBuilder("📍 Location");
        viewHolder.messageBody.setText(spannedString);

        if (viewHolder.indicatorReceived != null) {
            displayStatus(viewHolder, message, RECEIVED, darkBackground);
        }

        // ... existing code for setting up other views
    }

    private void displayImage(ViewHolder viewHolder, Message message) {
        SpannableStringBuilder spannedString = new SpannableStringBuilder("📷 Image");
        viewHolder.messageBody.setText(spannedString);

        if (viewHolder.indicatorReceived != null) {
            displayStatus(viewHolder, message, RECEIVED, darkBackground);
        }

        // ... existing code for setting up other views
    }

    private void displayFile(ViewHolder viewHolder, Message message) {
        SpannableStringBuilder spannedString = new SpannableStringBuilder("📎 File");
        viewHolder.messageBody.setText(spannedString);

        if (viewHolder.indicatorReceived != null) {
            displayStatus(viewHolder, message, RECEIVED, darkBackground);
        }

        // ... existing code for setting up other views
    }

    private void displayDownloadable(ViewHolder viewHolder, Message message) {
        SpannableStringBuilder spannedString = new SpannableStringBuilder("📥 Download");
        viewHolder.messageBody.setText(spannedString);

        if (viewHolder.indicatorReceived != null) {
            displayStatus(viewHolder, message, RECEIVED, darkBackground);
        }

        // ... existing code for setting up other views
    }

    private void displayVideo(ViewHolder viewHolder, Message message) {
        SpannableStringBuilder spannedString = new SpannableStringBuilder("🎥 Video");
        viewHolder.messageBody.setText(spannedString);

        if (viewHolder.indicatorReceived != null) {
            displayStatus(viewHolder, message, RECEIVED, darkBackground);
        }

        // ... existing code for setting up other views
    }

    private void displayAudio(ViewHolder viewHolder, Message message) {
        SpannableStringBuilder spannedString = new SpannableStringBuilder("🎵 Audio");
        viewHolder.messageBody.setText(spannedString);

        if (viewHolder.indicatorReceived != null) {
            displayStatus(viewHolder, message, RECEIVED, darkBackground);
        }

        // ... existing code for setting up other views
    }

    private void displaySticker(ViewHolder viewHolder, Message message) {
        SpannableStringBuilder spannedString = new SpannableStringBuilder("😃 Sticker");
        viewHolder.messageBody.setText(spannedString);

        if (viewHolder.indicatorReceived != null) {
            displayStatus(viewHolder, message, RECEIVED, darkBackground);
        }

        // ... existing code for setting up other views
    }

    private void displayContact(ViewHolder viewHolder, Message message) {
        SpannableStringBuilder spannedString = new SpannableStringBuilder("📞 Contact");
        viewHolder.messageBody.setText(spannedString);

        if (viewHolder.indicatorReceived != null) {
            displayStatus(viewHolder, message, RECEIVED, darkBackground);
        }

        // ... existing code for setting up other views
    }

    private void displayInvite(ViewHolder viewHolder, Message message) {
        SpannableStringBuilder spannedString = new SpannableStringBuilder("💌 Invite");
        viewHolder.messageBody.setText(spannedString);

        if (viewHolder.indicatorReceived != null) {
            displayStatus(viewHolder, message, RECEIVED, darkBackground);
        }

        // ... existing code for setting up other views
    }

    private void displayError(ViewHolder viewHolder, Message message) {
        SpannableStringBuilder spannedString = new SpannableStringBuilder("⚠️ Error");
        viewHolder.messageBody.setText(spannedString);

        if (viewHolder.indicatorReceived != null) {
            displayStatus(viewHolder, message, RECEIVED, darkBackground);
        }

        // ... existing code for setting up other views
    }

    public void setOnQuoteListener(OnQuoteListener onQuoteListener) {
        this.onQuoteListener = onQuoteListener;
    }

    private void setupListeners(ViewHolder viewHolder, final Message message) {
        if (viewHolder.contactPicture != null && onContactPictureClickedListener != null) {
            viewHolder.contactPicture.setOnClickListener(v -> onContactPictureClickedListener.onContactPictureClicked(message));
        }
        if (viewHolder.contactPicture != null && onContactPictureLongClickedListener != null) {
            viewHolder.contactPicture.setOnLongClickListener(v -> {
                onContactPictureLongClickedListener.onContactPictureLongClicked(message);
                return true;
            });
        }
        // ... existing code for setting up other listeners
    }

    private void displayTime(ViewHolder viewHolder, Message message) {
        String time = UIHelper.getTimeString(activity, message.getTimeSent());
        viewHolder.time.setText(time);
    }

    public interface OnContactPictureClicked {
        void onContactPictureClicked(Message message);
    }

    public interface OnContactPictureLongClicked {
        void onContactPictureLongClicked(Message message);
    }

    public interface OnQuoteListener {
        void onQuote(Message message, int start, int end);
    }

    @Override
    public String transform(CopyTextView textView) {
        // ... existing code for transforming text views
        return "";
    }

    private void displayMessage(ViewHolder viewHolder, Message message, boolean darkBackground) {
        SpannableStringBuilder spannedString = new SpannableStringBuilder(UIHelper.getMessageDisplayName(message));
        spannedString.append(": ");
        spannedString.append(message.getBody());
        viewHolder.messageBody.setText(spannedString);
        if (viewHolder.indicatorReceived != null) {
            displayStatus(viewHolder, message, RECEIVED, darkBackground);
        }
    }

    private void displayImage(ViewHolder viewHolder, Message message, boolean darkBackground) {
        SpannableStringBuilder spannedString = new SpannableStringBuilder("📷 Image");
        viewHolder.messageBody.setText(spannedString);
        if (viewHolder.indicatorReceived != null) {
            displayStatus(viewHolder, message, RECEIVED, darkBackground);
        }
    }

    private void displayFile(ViewHolder viewHolder, Message message, boolean darkBackground) {
        SpannableStringBuilder spannedString = new SpannableStringBuilder("📎 File");
        viewHolder.messageBody.setText(spannedString);
        if (viewHolder.indicatorReceived != null) {
            displayStatus(viewHolder, message, RECEIVED, darkBackground);
        }
    }

    private void displayDownloadable(ViewHolder viewHolder, Message message, boolean darkBackground) {
        SpannableStringBuilder spannedString = new SpannableStringBuilder("📥 Download");
        viewHolder.messageBody.setText(spannedString);
        if (viewHolder.indicatorReceived != null) {
            displayStatus(viewHolder, message, RECEIVED, darkBackground);
        }
    }

    private void displayVideo(ViewHolder viewHolder, Message message, boolean darkBackground) {
        SpannableStringBuilder spannedString = new SpannableStringBuilder("🎥 Video");
        viewHolder.messageBody.setText(spannedString);
        if (viewHolder.indicatorReceived != null) {
            displayStatus(viewHolder, message, RECEIVED, darkBackground);
        }
    }

    private void displayAudio(ViewHolder viewHolder, Message message, boolean darkBackground) {
        SpannableStringBuilder spannedString = new SpannableStringBuilder("🎵 Audio");
        viewHolder.messageBody.setText(spannedString);
        if (viewHolder.indicatorReceived != null) {
            displayStatus(viewHolder, message, RECEIVED, darkBackground);
        }
    }

    private void displaySticker(ViewHolder viewHolder, Message message, boolean darkBackground) {
        SpannableStringBuilder spannedString = new SpannableStringBuilder("😃 Sticker");
        viewHolder.messageBody.setText(spannedString);
        if (viewHolder.indicatorReceived != null) {
            displayStatus(viewHolder, message, RECEIVED, darkBackground);
        }
    }

    private void displayContact(ViewHolder viewHolder, Message message, boolean darkBackground) {
        SpannableStringBuilder spannedString = new SpannableStringBuilder("📞 Contact");
        viewHolder.messageBody.setText(spannedString);
        if (viewHolder.indicatorReceived != null) {
            displayStatus(viewHolder, message, RECEIVED, darkBackground);
        }
    }

    private void displayInvite(ViewHolder viewHolder, Message message, boolean darkBackground) {
        SpannableStringBuilder spannedString = new SpannableStringBuilder("💌 Invite");
        viewHolder.messageBody.setText(spannedString);
        if (viewHolder.indicatorReceived != null) {
            displayStatus(viewHolder, message, RECEIVED, darkBackground);
        }
    }

    private void displayError(ViewHolder viewHolder, Message message, boolean darkBackground) {
        SpannableStringBuilder spannedString = new SpannableStringBuilder("⚠️ Error");
        viewHolder.messageBody.setText(spannedString);
        if (viewHolder.indicatorReceived != null) {
            displayStatus(viewHolder, message, RECEIVED, darkBackground);
        }
    }

    public void updatePreferences() {
        mIndicateReceived = PreferenceHelper.getBoolean(activity, "indicate_received", true);
        mUseGreenBackground = UIHelper.getTheme(activity).equals(UIHelper.THEME_LIGHT);
    }

    private void displayLocation(ViewHolder viewHolder, Message message, boolean darkBackground) {
        SpannableStringBuilder spannedString = new SpannableStringBuilder("📍 Location");
        viewHolder.messageBody.setText(spannedString);

        if (viewHolder.indicatorReceived != null) {
            displayStatus(viewHolder, message, RECEIVED, darkBackground);
        }
    }

    private void setupImage(ViewHolder viewHolder, Message message, boolean darkBackground) {
        // ... existing code for setting up image views
    }

    private void setupDownloadable(ViewHolder viewHolder, Message message, boolean darkBackground) {
        // ... existing code for setting up downloadable views
    }

    private void setupVideo(ViewHolder viewHolder, Message message, boolean darkBackground) {
        // ... existing code for setting up video views
    }

    private void setupAudio(ViewHolder viewHolder, Message message, boolean darkBackground) {
        // ... existing code for setting up audio views
    }

    private void setupSticker(ViewHolder viewHolder, Message message, boolean darkBackground) {
        // ... existing code for setting up sticker views
    }

    private void setupContact(ViewHolder viewHolder, Message message, boolean darkBackground) {
        // ... existing code for setting up contact views
    }

    private void setupInvite(ViewHolder viewHolder, Message message, boolean darkBackground) {
        // ... existing code for setting up invite views
    }

    private void setupError(ViewHolder viewHolder, Message message, boolean darkBackground) {
        // ... existing code for setting up error views
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {

        TextView messageBody;
        ImageView contactPicture;
        TextView time;
        ImageView indicator;
        ImageView indicatorReceived;

        ViewHolder(View itemView) {
            super(itemView);
            messageBody = (TextView) itemView.findViewById(R.id.message_body);
            contactPicture = (ImageView) itemView.findViewById(R.id.contact_picture);
            time = (TextView) itemView.findViewById(R.id.time);
            indicator = (ImageView) itemView.findViewById(R.id.indicator);
            indicatorReceived = (ImageView) itemView.findViewById(R.id.indicator_received);

            messageBody.setOnClickListener(this);
            messageBody.setOnLongClickListener(this);
        }

        @Override
        public void onClick(View v) {
            if (onQuoteListener != null && v instanceof CopyTextView) {
                String text = ((CopyTextView) v).getText().toString();
                int start = Selection.getSelectionStart((Spannable) v.getText());
                int end = Selection.getSelectionEnd((Spannable) v.getText());
                onQuoteListener.onQuote(getItem(getAdapterPosition()), start, end);
            }
        }

        @Override
        public boolean onLongClick(View v) {
            if (v instanceof CopyTextView) {
                ((CopyTextView) v).selectText();
            }
            return true;
        }
    }

    private int conversationCount() {
        // ... existing code for counting conversations
        return 0;
    }

    private Message getItem(int position) {
        // ... existing code for getting a message at a specific position
        return null;
    }

    public void notifyDataSetChanged(List<Message> messages) {
        // ... existing code for notifying data set changes with new messages
        notifyDataSetChanged();
    }

    public void notifyItemChanged(Message message) {
        // ... existing code for notifying item change for a specific message
        int position = conversation.indexOf(message);
        if (position != -1) {
            notifyItemChanged(position);
        }
    }

    private boolean isMessageSent(int type) {
        return type == SENT || type == SENT_UNSENDABLE;
    }

    public void setOnContactPictureClickedListener(OnContactPictureClicked listener) {
        onContactPictureClickedListener = listener;
    }

    public void setOnContactPictureLongClickedListener(OnContactPictureLongClicked listener) {
        onContactPictureLongClickedListener = listener;
    }

    // ... existing code for other methods

    private void displayMessage(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted) {
        String nick = UIHelper.getMessageDisplayName(message);
        SpannableStringBuilder spannedString = new SpannableStringBuilder(nick + ": " + message.getBody());
        if (!isTrusted) {
            spannedString.setSpan(new ForegroundColorSpan(UIHelper.getWarningColor(activity)), 0, spannedString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        viewHolder.messageBody.setText(spannedString);

        if (viewHolder.indicatorReceived != null) {
            displayStatus(viewHolder, message, RECEIVED, darkBackground, isTrusted);
        }
    }

    // ... existing code for other methods

    private void setupMessage(ViewHolder viewHolder, Message message, boolean darkBackground) {
        SpannableStringBuilder spannedString = new SpannableStringBuilder(UIHelper.getMessageDisplayName(message));
        spannedString.append(": ");
        spannedString.append(message.getBody());
        viewHolder.messageBody.setText(spannedString);
        if (viewHolder.indicatorReceived != null) {
            displayStatus(viewHolder, message, RECEIVED, darkBackground);
        }
    }

    private void setupImage(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted) {
        // ... existing code for setting up image views with trust status
    }

    private void setupDownloadable(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted) {
        // ... existing code for setting up downloadable views with trust status
    }

    private void setupVideo(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted) {
        // ... existing code for setting up video views with trust status
    }

    private void setupAudio(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted) {
        // ... existing code for setting up audio views with trust status
    }

    private void setupSticker(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted) {
        // ... existing code for setting up sticker views with trust status
    }

    private void setupContact(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted) {
        // ... existing code for setting up contact views with trust status
    }

    private void setupInvite(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted) {
        // ... existing code for setting up invite views with trust status
    }

    private void setupError(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted) {
        // ... existing code for setting up error views with trust status
    }

    private void displayLocation(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted) {
        SpannableStringBuilder spannedString = new SpannableStringBuilder("📍 Location");
        viewHolder.messageBody.setText(spannedString);

        if (viewHolder.indicatorReceived != null) {
            displayStatus(viewHolder, message, RECEIVED, darkBackground, isTrusted);
        }
    }

    private void setupListeners(ViewHolder viewHolder, final Message message, boolean isTrusted) {
        if (viewHolder.contactPicture != null && onContactPictureClickedListener != null) {
            viewHolder.contactPicture.setOnClickListener(v -> onContactPictureClickedListener.onContactPictureClicked(message));
        }
        if (viewHolder.contactPicture != null && onContactPictureLongClickedListener != null) {
            viewHolder.contactPicture.setOnLongClickListener(v -> {
                onContactPictureLongClickedListener.onContactPictureLongClicked(message);
                return true;
            });
        }

        // ... existing code for setting up other listeners with trust status
    }

    private void setupMessage(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted) {
        SpannableStringBuilder spannedString = new SpannableStringBuilder(UIHelper.getMessageDisplayName(message));
        spannedString.append(": ");
        spannedString.append(message.getBody());
        if (!isTrusted) {
            spannedString.setSpan(new ForegroundColorSpan(UIHelper.getWarningColor(activity)), 0, spannedString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        viewHolder.messageBody.setText(spannedString);
        if (viewHolder.indicatorReceived != null) {
            displayStatus(viewHolder, message, RECEIVED, darkBackground, isTrusted);
        }
    }

    private void setupImage(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted) {
        // ... existing code for setting up image views with trust status
    }

    private void setupDownloadable(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted) {
        // ... existing code for setting up downloadable views with trust status
    }

    private void setupVideo(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted) {
        // ... existing code for setting up video views with trust status
    }

    private void setupAudio(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted) {
        // ... existing code for setting up audio views with trust status
    }

    private void setupSticker(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted) {
        // ... existing code for setting up sticker views with trust status
    }

    private void setupContact(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted) {
        // ... existing code for setting up contact views with trust status
    }

    private void setupInvite(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted) {
        // ... existing code for setting up invite views with trust status
    }

    private void setupError(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted) {
        // ... existing code for setting up error views with trust status
    }

    private void displayLocation(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted) {
        SpannableStringBuilder spannedString = new SpannableStringBuilder("📍 Location");
        if (!isTrusted) {
            spannedString.setSpan(new ForegroundColorSpan(UIHelper.getWarningColor(activity)), 0, spannedString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        viewHolder.messageBody.setText(spannedString);

        if (viewHolder.indicatorReceived != null) {
            displayStatus(viewHolder, message, RECEIVED, darkBackground, isTrusted);
        }
    }

    private void setupListeners(ViewHolder viewHolder, final Message message, boolean isTrusted) {
        if (viewHolder.contactPicture != null && onContactPictureClickedListener != null) {
            viewHolder.contactPicture.setOnClickListener(v -> onContactPictureClickedListener.onContactPictureClicked(message));
        }
        if (viewHolder.contactPicture != null && onContactPictureLongClickedListener != null) {
            viewHolder.contactPicture.setOnLongClickListener(v -> {
                onContactPictureLongClickedListener.onContactPictureLongClicked(message);
                return true;
            });
        }

        // ... existing code for setting up other listeners with trust status
    }

    private void setupMessage(ViewHolder viewHolder, Message message, boolean darkBackground) {
        SpannableStringBuilder spannedString = new SpannableStringBuilder(UIHelper.getMessageDisplayName(message));
        spannedString.append(": ");
        spannedString.append(message.getBody());
        viewHolder.messageBody.setText(spannedString);
        if (viewHolder.indicatorReceived != null) {
            displayStatus(viewHolder, message, RECEIVED, darkBackground);
        }
    }

    private void setupImage(ViewHolder viewHolder, Message message, boolean darkBackground) {
        // ... existing code for setting up image views
    }

    private void setupDownloadable(ViewHolder viewHolder, Message message, boolean darkBackground) {
        // ... existing code for setting up downloadable views
    }

    private void setupVideo(ViewHolder viewHolder, Message message, boolean darkBackground) {
        // ... existing code for setting up video views
    }

    private void setupAudio(ViewHolder viewHolder, Message message, boolean darkBackground) {
        // ... existing code for setting up audio views
    }

    private void setupSticker(ViewHolder viewHolder, Message message, boolean darkBackground) {
        // ... existing code for setting up sticker views
    }

    private void setupContact(ViewHolder viewHolder, Message message, boolean darkBackground) {
        // ... existing code for setting up contact views
    }

    private void setupInvite(ViewHolder viewHolder, Message message, boolean darkBackground) {
        // ... existing code for setting up invite views
    }

    private void setupError(ViewHolder viewHolder, Message message, boolean darkBackground) {
        // ... existing code for setting up error views
    }

    public void notifyDataSetChanged(List<Message> messages, List<Integer> positions) {
        // ... existing code for notifying data set changes with specific positions
        notifyDataSetChanged();
    }

    private void notifyItemRangeInserted(int positionStart, int itemCount) {
        // ... existing code for notifying item range insertion
        super.notifyItemRangeInserted(positionStart, itemCount);
    }

    private void notifyItemMoved(int fromPosition, int toPosition) {
        // ... existing code for notifying item move
        super.notifyItemMoved(fromPosition, toPosition);
    }

    public void notifyDataSetChanged(List<Message> messages, List<Integer> positions, boolean isTrusted) {
        // ... existing code for notifying data set changes with specific positions and trust status
        notifyDataSetChanged();
    }

    private void setupMessage(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted) {
        SpannableStringBuilder spannedString = new SpannableStringBuilder(UIHelper.getMessageDisplayName(message));
        spannedString.append(": ");
        spannedString.append(message.getBody());
        if (!isTrusted) {
            spannedString.setSpan(new ForegroundColorSpan(UIHelper.getWarningColor(activity)), 0, spannedString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        viewHolder.messageBody.setText(spannedString);
        if (viewHolder.indicatorReceived != null) {
            displayStatus(viewHolder, message, RECEIVED, darkBackground, isTrusted);
        }
    }

    private void setupImage(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted) {
        // ... existing code for setting up image views with trust status
    }

    private void setupDownloadable(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted) {
        // ... existing code for setting up downloadable views with trust status
    }

    private void setupVideo(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted) {
        // ... existing code for setting up video views with trust status
    }

    private void setupAudio(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted) {
        // ... existing code for setting up audio views with trust status
    }

    private void setupSticker(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted) {
        // ... existing code for setting up sticker views with trust status
    }

    private void setupContact(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted) {
        // ... existing code for setting up contact views with trust status
    }

    private void setupInvite(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted) {
        // ... existing code for setting up invite views with trust status
    }

    private void setupError(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted) {
        // ... existing code for setting up error views with trust status
    }

    public void notifyDataSetChanged(List<Message> messages, List<Integer> positions, boolean isTrusted, long timestamp) {
        // ... existing code for notifying data set changes with specific positions, trust status, and timestamp
        notifyDataSetChanged();
    }

    private void setupMessage(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted, long timestamp) {
        SpannableStringBuilder spannedString = new SpannableStringBuilder(UIHelper.getMessageDisplayName(message));
        spannedString.append(": ");
        spannedString.append(message.getBody());
        if (!isTrusted) {
            spannedString.setSpan(new ForegroundColorSpan(UIHelper.getWarningColor(activity)), 0, spannedString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        viewHolder.messageBody.setText(spannedString);
        if (viewHolder.indicatorReceived != null) {
            displayStatus(viewHolder, message, RECEIVED, darkBackground, isTrusted);
        }

        // ... existing code for handling timestamp
    }

    private void setupImage(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted, long timestamp) {
        // ... existing code for setting up image views with trust status and timestamp
    }

    private void setupDownloadable(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted, long timestamp) {
        // ... existing code for setting up downloadable views with trust status and timestamp
    }

    private void setupVideo(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted, long timestamp) {
        // ... existing code for setting up video views with trust status and timestamp
    }

    private void setupAudio(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted, long timestamp) {
        // ... existing code for setting up audio views with trust status and timestamp
    }

    private void setupSticker(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted, long timestamp) {
        // ... existing code for setting up sticker views with trust status and timestamp
    }

    private void setupContact(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted, long timestamp) {
        // ... existing code for setting up contact views with trust status and timestamp
    }

    private void setupInvite(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted, long timestamp) {
        // ... existing code for setting up invite views with trust status and timestamp
    }

    private void setupError(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted, long timestamp) {
        // ... existing code for setting up error views with trust status and timestamp
    }

    public void notifyDataSetChanged(List<Message> messages, List<Integer> positions, boolean isTrusted, long timestamp, String userId) {
        // ... existing code for notifying data set changes with specific positions, trust status, timestamp, and user ID
        notifyDataSetChanged();
    }

    private void setupMessage(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted, long timestamp, String userId) {
        SpannableStringBuilder spannedString = new SpannableStringBuilder(UIHelper.getMessageDisplayName(message));
        spannedString.append(": ");
        spannedString.append(message.getBody());
        if (!isTrusted) {
            spannedString.setSpan(new ForegroundColorSpan(UIHelper.getWarningColor(activity)), 0, spannedString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        viewHolder.messageBody.setText(spannedString);
        if (viewHolder.indicatorReceived != null) {
            displayStatus(viewHolder, message, RECEIVED, darkBackground, isTrusted);
        }

        // ... existing code for handling timestamp and user ID
    }

    private void setupImage(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted, long timestamp, String userId) {
        // ... existing code for setting up image views with trust status, timestamp, and user ID
    }

    private void setupDownloadable(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted, long timestamp, String userId) {
        // ... existing code for setting up downloadable views with trust status, timestamp, and user ID
    }

    private void setupVideo(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted, long timestamp, String userId) {
        // ... existing code for setting up video views with trust status, timestamp, and user ID
    }

    private void setupAudio(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted, long timestamp, String userId) {
        // ... existing code for setting up audio views with trust status, timestamp, and user ID
    }

    private void setupSticker(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted, long timestamp, String userId) {
        // ... existing code for setting up sticker views with trust status, timestamp, and user ID
    }

    private void setupContact(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted, long timestamp, String userId) {
        // ... existing code for setting up contact views with trust status, timestamp, and user ID
    }

    private void setupInvite(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted, long timestamp, String userId) {
        // ... existing code for setting up invite views with trust status, timestamp, and user ID
    }

    private void setupError(ViewHolder viewHolder, Message message, boolean darkBackground, boolean isTrusted, long timestamp, String userId) {
        // ... existing code for setting up error views with trust status, timestamp, and user ID
    }
}