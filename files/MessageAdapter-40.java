package eu.siacs.conversations.ui.adapter;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.ColorInt;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.text.HtmlCompat;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.CryptoHelper;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.GeocoderService;
import eu.siacs.conversations.utils.AudioPlayer;
import eu.siacs.conversations.utils.GeoHelper;
import eu.siacs.conversations.utils.ListSelectionManager;
import eu.siacs.conversations.utils.TimeUtils;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

// Adapter class for handling message items in a chat interface
public class MessageAdapter extends ArrayAdapter<Message> {

    public static final int ITEM_DIVIDER = 0;
    public static final int ITEM_STATUS = 1;
    public static final int ITEM_MESSAGE_GROUP_START_IRC_STYLE = 2;
    public static final int ITEM_MESSAGE_SINGLE = 3;
    public static final int ITEM_MESSAGE_CONTINUOUS = 4;

    private Activity activity;
    private ListSelectionManager listSelectionManager;
    private AudioPlayer audioPlayer;
    private boolean mIndicateReceived = true;
    private boolean mUseGreenBackground = false;
    private OnQuoteListener onQuoteListener;
    private OnContactPictureClicked contactPictureClickedListener;
    private OnContactPictureLongClicked contactPictureLongClickedListener;

    // Constructor initializing the adapter with necessary components
    public MessageAdapter(Activity activity, List<Message> messages) {
        super(activity, 0, messages);
        this.activity = activity;
        this.audioPlayer = new AudioPlayer();
    }

    // Setting listeners for quote actions and contact picture interactions
    public void setOnQuoteListener(OnQuoteListener listener) {
        this.onQuoteListener = listener;
    }

    public void setOnContactPictureClicked(OnContactPictureClicked listener) {
        this.contactPictureClickedListener = listener;
    }

    public void setOnContactPictureLongClicked(OnContactPictureLongClicked listener) {
        this.contactPictureLongClickedListener = listener;
    }

    // Returning the count of items in the adapter
    @Override
    public int getCount() {
        return super.getCount();
    }

    // Returning the item type for a given position, ensuring proper layout rendering
    @Override
    public int getItemViewType(int position) {
        Message message = this.getItem(position);
        if (message == null || position < 0 || position >= super.getCount()) {
            return ITEM_DIVIDER;
        } else {
            switch (message.getType()) {
                case STATUS:
                    return ITEM_STATUS;
                default:
                    boolean startOfMessageGroup = position <= 1 ||
                            !UIHelper.sameAuthorAndDay(message, this.getItem(position - 1));
                    if (startOfMessageGroup) {
                        return ITEM_MESSAGE_GROUP_START_IRC_STYLE;
                    } else {
                        return UI_HELPER.sameAuthor(this.getItem(position), message) ?
                                ITEM_MESSAGE_CONTINUOUS : ITEM_MESSAGE_SINGLE;
                    }
            }
        }
    }

    // Returning the number of different view types used in the adapter
    @Override
    public int getViewTypeCount() {
        return 5; // Matches the constants defined at top, including ITEM_DIVIDER
    }

    // Creating or recycling views for each message item
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Message message = getItem(position);

        if (message.getType() == Message.TYPE_STATUS || message.getType() == Message.TYPE_ERROR_MESSAGE) {
            return getStatusView(message);
        }

        int type = getItemViewType(position);

        switch (type) {
            case ITEM_MESSAGE_GROUP_START_IRC_STYLE:
                convertView = getGroupStartIrcStyleView(convertView, parent);
                break;
            case ITEM_MESSAGE_SINGLE:
                convertView = getSingleMessageView(convertView, parent);
                break;
            case ITEM_MESSAGE_CONTINUOUS:
                convertView = getMessageContinueView(convertView, parent);
                break;
        }

        bindView(message, convertView);
        return convertView;
    }

    // Creating or recycling a view for message group start in IRC style
    private View getGroupStartIrcStyleView(View view, ViewGroup parent) {
        if (view == null) {
            LayoutInflater inflater = activity.getLayoutInflater();
            view = inflater.inflate(R.layout.message_group_start_irc_style, parent, false);
        }
        return view;
    }

    // Creating or recycling a view for single messages
    private View getSingleMessageView(View view, ViewGroup parent) {
        if (view == null) {
            LayoutInflater inflater = activity.getLayoutInflater();
            view = inflater.inflate(R.layout.message_single, parent, false);
        }
        return view;
    }

    // Creating or recycling a view for continuing messages from the same author
    private View getMessageContinueView(View view, ViewGroup parent) {
        if (view == null) {
            LayoutInflater inflater = activity.getLayoutInflater();
            view = inflater.inflate(R.layout.message_continue, parent, false);
        }
        return view;
    }

    // Binding data to a given message view
    private void bindView(Message message, View convertView) {
        TextView body = getMessageBody(convertView);
        if (body != null) {
            body.setVisibility(View.VISIBLE);
            UIHelper.setMessageColor(body, activity.getConversationsService().getConversationByUuid(message.getConversationUuid()).getWith().getResource());
            String displayableText = UIHelper.getMessageString(activity, message);
            // Displaying text in HTML format
            body.setText(HtmlCompat.fromHtml(displayableText, HtmlCompat.FROM_HTML_MODE_LEGACY));
        }

        TextView time = getMessageTime(convertView);
        if (time != null) {
            long timestamp;
            if (message.getType() == Message.TYPE_PRIVATE_MESSAGE && message.isOutdated()) {
                timestamp = message.getTimestamp();
            } else {
                timestamp = message.getTimeSent();
            }
            // Formatting and displaying the time of the message
            time.setText(TimeUtils.formatAbsoluteTime(activity, timestamp));
        }

        ImageView statusIndicator = getStatusIndicator(convertView);
        if (statusIndicator != null) {
            UIHelper.updateStatusIcon(statusIndicator, activity.xmppConnectionService, message);
        }

        View indicatorReceived = getIndicatorReceived(convertView);
        if (indicatorReceived != null) {
            indicatorReceived.setVisibility(mIndicateReceived && message.indicated() ? View.VISIBLE : View.GONE);
        }

        ImageView editIndicator = getEditIndicator(convertView);
        if (editIndicator != null) {
            boolean isEditable = UIHelper.isMessageEditable(activity.xmppConnectionService, message);
            editIndicator.setVisibility(isEditable ? View.VISIBLE : View.GONE);
            if (isEditable) {
                editIndicator.setOnClickListener(v -> contactPictureClickedListener.onContactPictureClicked(message));
            }
        }

        ImageView avatarImageView = getAvatar(convertView);
        if (avatarImageView != null) {
            loadAvatar(message, avatarImageView, 128); // Size in pixels
            if (contactPictureClickedListener != null) {
                avatarImageView.setOnClickListener(v -> contactPictureClickedListener.onContactPictureClicked(message));
            }
            if (contactPictureLongClickedListener != null) {
                avatarImageView.setOnLongClickListener(v -> {
                    contactPictureLongClickedListener.onContactPictureLongClicked(message);
                    return true;
                });
            }
        }

        View audioPlayerView = getAudioPlayer(convertView);
        if (audioPlayerView != null) {
            AudioPlayer.setupAudioPlayer(audioPlayer, message, activity.getResources(), activity.xmppConnectionService.getFileBackend(), audioPlayerView);
        }
    }

    // Creating a view for status messages
    private View getStatusView(Message message) {
        LayoutInflater inflater = activity.getLayoutInflater();
        View view = inflater.inflate(R.layout.message_status, null);

        TextView body = getMessageBody(view);
        if (body != null) {
            String displayableText = UIHelper.getMessageString(activity, message);
            // Displaying text in HTML format
            body.setText(HtmlCompat.fromHtml(displayableText, HtmlCompat.FROM_HTML_MODE_LEGACY));
        }

        return view;
    }

    // Retrieving the TextView for the message body from a given view
    public TextView getMessageBody(View view) {
        final Object tag = view.getTag();
        if (tag instanceof ViewHolder) {
            final ViewHolder viewHolder = (ViewHolder) tag;
            if (viewHolder.messageBody == null) {
                viewHolder.messageBody = view.findViewById(R.id.message_body);
            }
            return viewHolder.messageBody;
        } else {
            ViewHolder viewHolder = new ViewHolder();
            viewHolder.messageBody = view.findViewById(R.id.message_body);
            view.setTag(viewHolder);
            return viewHolder.messageBody;
        }
    }

    // Retrieving the TextView for the message time from a given view
    private TextView getMessageTime(View view) {
        final Object tag = view.getTag();
        if (tag instanceof ViewHolder) {
            final ViewHolder viewHolder = (ViewHolder) tag;
            if (viewHolder.messageTime == null) {
                viewHolder.messageTime = view.findViewById(R.id.message_time);
            }
            return viewHolder.messageTime;
        } else {
            ViewHolder viewHolder = new ViewHolder();
            viewHolder.messageTime = view.findViewById(R.id.message_time);
            view.setTag(viewHolder);
            return viewHolder.messageTime;
        }
    }

    // Retrieving the ImageView for the status indicator from a given view
    private ImageView getStatusIndicator(View view) {
        final Object tag = view.getTag();
        if (tag instanceof ViewHolder) {
            final ViewHolder viewHolder = (ViewHolder) tag;
            if (viewHolder.statusIndicator == null) {
                viewHolder.statusIndicator = view.findViewById(R.id.message_status_icon);
            }
            return viewHolder.statusIndicator;
        } else {
            ViewHolder viewHolder = new ViewHolder();
            viewHolder.statusIndicator = view.findViewById(R.id.message_status_icon);
            view.setTag(viewHolder);
            return viewHolder.statusIndicator;
        }
    }

    // Retrieving the View for the received indicator from a given view
    private View getIndicatorReceived(View view) {
        final Object tag = view.getTag();
        if (tag instanceof ViewHolder) {
            final ViewHolder viewHolder = (ViewHolder) tag;
            if (viewHolder.indicatorReceived == null) {
                viewHolder.indicatorReceived = view.findViewById(R.id.message_received_indicator);
            }
            return viewHolder.indicatorReceived;
        } else {
            ViewHolder viewHolder = new ViewHolder();
            viewHolder.indicatorReceived = view.findViewById(R.id.message_received_indicator);
            view.setTag(viewHolder);
            return viewHolder.indicatorReceived;
        }
    }

    // Retrieving the ImageView for the edit indicator from a given view
    private ImageView getEditIndicator(View view) {
        final Object tag = view.getTag();
        if (tag instanceof ViewHolder) {
            final ViewHolder viewHolder = (ViewHolder) tag;
            if (viewHolder.editIndicator == null) {
                viewHolder.editIndicator = view.findViewById(R.id.message_edit_indicator);
            }
            return viewHolder.editIndicator;
        } else {
            ViewHolder viewHolder = new ViewHolder();
            viewHolder.editIndicator = view.findViewById(R.id.message_edit_indicator);
            view.setTag(viewHolder);
            return viewHolder.editIndicator;
        }
    }

    // Retrieving the ImageView for the avatar from a given view
    private ImageView getAvatar(View view) {
        final Object tag = view.getTag();
        if (tag instanceof ViewHolder) {
            final ViewHolder viewHolder = (ViewHolder) tag;
            if (viewHolder.avatar == null) {
                viewHolder.avatar = view.findViewById(R.id.message_avatar);
            }
            return viewHolder.avatar;
        } else {
            ViewHolder viewHolder = new ViewHolder();
            viewHolder.avatar = view.findViewById(R.id.message_avatar);
            view.setTag(viewHolder);
            return viewHolder.avatar;
        }
    }

    // Retrieving the View for the audio player from a given view
    private View getAudioPlayer(View view) {
        final Object tag = view.getTag();
        if (tag instanceof ViewHolder) {
            final ViewHolder viewHolder = (ViewHolder) tag;
            if (viewHolder.audioPlayer == null) {
                viewHolder.audioPlayer = view.findViewById(R.id.audio_player);
            }
            return viewHolder.audioPlayer;
        } else {
            ViewHolder viewHolder = new ViewHolder();
            viewHolder.audioPlayer = view.findViewById(R.id.audio_player);
            view.setTag(viewHolder);
            return viewHolder.audioPlayer;
        }
    }

    // View holder pattern for efficient view recycling
    private static class ViewHolder {
        TextView messageBody;
        TextView messageTime;
        ImageView statusIndicator;
        View indicatorReceived;
        ImageView editIndicator;
        ImageView avatar;
        View audioPlayer;
    }

    // Loading the avatar image for a given message using an AsyncTask
    private void loadAvatar(Message message, ImageView imageView, int size) {
        new LoadImageTask(imageView).execute(message);
    }

    // AsyncTask class for loading images in background
    private static class LoadImageTask extends AsyncTask<Message, Void, Bitmap> {

        private WeakReference<ImageView> imageViewWeakReference;

        public LoadImageTask(ImageView imageView) {
            imageViewWeakReference = new WeakReference<>(imageView);
        }

        @Override
        protected Bitmap doInBackground(Message... messages) {
            Message message = messages[0];
            if (message == null || message.getConversationUuid() == null) {
                return null;
            }
            Conversation conversation = activity.xmppConnectionService.findConversationByUuid(message.getConversationUuid());
            if (conversation == null) {
                return null;
            }
            Account account = conversation.getAccount();
            try {
                Jid jid = conversation.getJid().asBareJid();
                File file = new File(activity.getCacheDir(), "avatar-" + jid.toString() + ".png");
                if (!file.exists()) {
                    // Avatar image not found, loading default avatar
                    return ContextCompat.getDrawable(imageViewWeakReference.get().getContext(), R.drawable.ic_account_circle_black_48dp)
                            .getBitmap();
                }
                // Returning the cached avatar image
                return Bitmap.createScaledBitmap(BitmapFactory.decodeFile(file.getAbsolutePath()), size, size, true);
            } catch (InvalidJidException e) {
                Log.w(Config.LOGTAG, "Invalid JID: " + conversation.getJid());
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null && imageViewWeakReference.get() != null) {
                imageViewWeakReference.get().setImageBitmap(bitmap);
            }
        }
    }

    // Setting the list selection manager for handling item selections
    public void setListSelectionManager(ListSelectionManager listSelectionManager) {
        this.listSelectionManager = listSelectionManager;
    }

    // Returning the list selection manager
    public ListSelectionManager getListSelectionManager() {
        return listSelectionManager;
    }

    // Handling the creation of new message items in the adapter
    @Override
    public void add(Message object) {
        super.add(object);
    }

    // Notifying the adapter that data has changed and views need to be refreshed
    @Override
    public void notifyDataSetChanged() {
        super.notifyDataSetChanged();
    }

    // Clearing all message items from the adapter
    @Override
    public void clear() {
        super.clear();
    }
}