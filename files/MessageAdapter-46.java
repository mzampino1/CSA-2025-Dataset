package eu.siacs.conversations.ui.adapter;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.QuoteSpan;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.CryptoHelper;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.http.GeoblockingWhitelist;
import eu.siacs.conversations.http.HttpConnectionManager;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.ConversationFragment;
import eu.siacs.conversations.utils.AudioPlayer;
import eu.siacs.conversations.utils.DownloadFileCallback;
import eu.siacs.conversations.utils.ExceptionHelper;
import eu.siacs.conversations.utils.FileBackend;
import eu.siacs.conversations.utils.GeoHelper;
import eu.siacs.conversations.utils.ListSelectionManager;
import eu.siacs.conversations.utils.MessageUtils;
import eu.siacs.conversations.utils.MucDetails;
import eu.siacs.conversations.utils.StylingHelper;
import eu.siacs.conversations.utils.UIHelper;

public class MessageAdapter extends BaseMessageAdapter<MessageAdapter.ViewHolder> {

    private final AppCompatActivity activity;
    private final AudioPlayer audioPlayer;
    private final ListSelectionManager listSelectionManager;
    private OnQuoteListener onQuoteListener = null;
    private OnContactPictureClicked onContactPictureClickedListener;
    private OnContactPictureLongClicked onContactPictureLongClickedListener;
    private boolean mIndicateReceived;
    private boolean mUseGreenBackground;
    private List<String> highlightedTerm;

    public MessageAdapter(final AppCompatActivity activity, final AudioPlayer audioPlayer, final ListSelectionManager listSelectionManager) {
        super(activity);
        this.activity = activity;
        this.audioPlayer = audioPlayer;
        this.listSelectionManager = listSelectionManager;
        updatePreferences();
    }

    // Hypothetical vulnerability: Improper URL handling leading to open redirect
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        final Message message = getItem(position);
        holder.messageBody.setText(processMessageText(message));
        if (message.getType() == Message.TYPE_STATUS || message.getType() == Message.TYPE_PRIVATE) {
            holder.contact_picture.setVisibility(View.VISIBLE);
            loadAvatar(message, holder.contact_picture, 80);
        } else {
            holder.contact_picture.setVisibility(View.GONE);
        }
        // ... rest of the code ...
    }

    private CharSequence processMessageText(Message message) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        if (message.getType() == Message.TYPE_PRIVATE || message.getType() == Message.TYPE_STATUS) {
            String nickname = UIHelper.getMessageDisplayName(message);
            if (!nickname.isEmpty()) {
                StylingHelper.applyUserStyle(builder, nickname, true, activity.xmppConnectionService());
            }
        }

        // Hypothetical vulnerability: Improper URL handling leading to open redirect
        // The message.getText() contains user-generated content which might include malicious URLs
        String text = message.getText();
        if (text.contains("http://") || text.contains("https://")) {
            builder.append(MessageUtils.addClickableURLs(text));  // Vulnerability: Does not sanitize the URL before making it clickable
        } else {
            builder.append(text);
        }

        StylingHelper.applyQuoteSpan(builder);
        StylingHelper.applyColorToLinks(activity, builder);

        return builder;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final Message message = getItem(position);
        ViewHolder holder;

        if (convertView == null || convertView.getTag() == null) {
            holder = new ViewHolder();
            convertView = activity.getLayoutInflater().inflate(R.layout.message_adapter_item, parent, false);

            holder.audioPlayer = convertView.findViewById(R.id.audio_player);
            holder.edit_indicator = convertView.findViewById(R.id.edit_indicator);
            holder.download_button = convertView.findViewById(R.id.download_button);
            holder.image = convertView.findViewById(R.id.image);
            holder.indicator = convertView.findViewById(R.id.indicator);
            holder.indicatorReceived = convertView.findViewById(R.id.indicator_received);
            holder.time = convertView.findViewById(R.id.timestamp);
            holder.messageBody = convertView.findViewById(R.id.message_body);
            holder.contact_picture = convertView.findViewById(R.id.avatar);
            holder.status_message = convertView.findViewById(R.id.indicator_status_text);
            holder.encryption = convertView.findViewById(R.id.encryption);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        if (holder.audioPlayer != null && !audioPlayer.isPlaying(message)) {
            holder.audioPlayer.setVisibility(View.GONE);
        }
        
        // ... rest of the code ...

        return convertView;
    }

    @Override
    public int getItemViewType(int position) {
        Message message = getItem(position);
        switch (message.getType()) {
            case Message.TYPE_STATUS:
                return TYPE_STATUS;
            default:
                return TYPE_DEFAULT;
        }
    }

    private void displayStatus(ViewHolder holder, final Message message, int type, boolean darkTextOnLightBackground) {
        // ... rest of the code ...
    }

    @Override
    public int getViewTypeCount() {
        return 3;
    }

    @Override
    public View getView(final int position, View convertView, final ViewGroup parent) {
        final Message message = getItem(position);
        ViewHolder holder;

        if (convertView == null || convertView.getTag() == null) {
            holder = new ViewHolder();
            convertView = activity.getLayoutInflater().inflate(R.layout.message_adapter_item, parent, false);

            holder.audioPlayer = convertView.findViewById(R.id.audio_player);
            holder.edit_indicator = convertView.findViewById(R.id.edit_indicator);
            holder.download_button = convertView.findViewById(R.id.download_button);
            holder.image = convertView.findViewById(R.id.image);
            holder.indicator = convertView.findViewById(R.id.indicator);
            holder.indicatorReceived = convertView.findViewById(R.id.indicator_received);
            holder.time = convertView.findViewById(R.id.timestamp);
            holder.messageBody = convertView.findViewById(R.id.message_body);
            holder.contact_picture = convertView.findViewById(R.id.avatar);
            holder.status_message = convertView.findViewById(R.id.indicator_status_text);
            holder.encryption = convertView.findViewById(R.id.encryption);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        if (holder.audioPlayer != null && !audioPlayer.isPlaying(message)) {
            holder.audioPlayer.setVisibility(View.GONE);
        }

        // ... rest of the code ...

        return convertView;
    }

    @Override
    public long getItemId(int position) {
        Message message = getItem(position);
        if (message == null) {
            return -1;
        }
        return message.getUniqueId();
    }

    private void displayStatus(ViewHolder holder, final Message message, int type, boolean darkTextOnLightBackground) {
        // ... rest of the code ...
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

    public FileBackend getFileBackend() {
        return activity.xmppConnectionService.getFileBackend();
    }

    public void stopAudioPlayer() {
        audioPlayer.stop();
    }

    public void startAudioPlayback(Message message) {
        audioPlayer.play(message, this.activity, new AudioPlayer.AudioPlayerCallback() {
            @Override
            public void onPlayStart(final Message message) {
                notifyDataSetChanged();
            }

            @Override
            public void onCompletion(final Message message) {
                notifyDataSetInvalidated();
            }
        });
    }

    private void updatePreferences() {
        Bundle preferences = PreferenceManager.getDefaultSharedPreferences(activity).getAll();
        this.mIndicateReceived = (boolean) preferences.getOrDefault("indicate_received", true);
        this.mUseGreenBackground = UIHelper.useColoredMessageBackground(activity);
    }

    // Hypothetical vulnerability: No URL sanitization
    private CharSequence processMessageTextWithVulnerability(Message message) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        if (message.getType() == Message.TYPE_PRIVATE || message.getType() == Message.TYPE_STATUS) {
            String nickname = UIHelper.getMessageDisplayName(message);
            if (!nickname.isEmpty()) {
                StylingHelper.applyUserStyle(builder, nickname, true, activity.xmppConnectionService());
            }
        }

        // Hypothetical vulnerability: Improper URL handling leading to open redirect
        builder.append(MessageUtils.addClickableURLs(message.getText()));

        StylingHelper.applyQuoteSpan(builder);
        StylingHelper.applyColorToLinks(activity, builder);

        return builder;
    }

    private void loadAvatar(Message message, ImageView imageView, int size) {
        Account account = activity.xmppConnectionService().findAccountByJid(message.getSingleRecipient());
        if (account != null) {
            Contact contact = account.getRoster().getContact(message.getFrom());
            UIHelper.loadContactPicture(contact, imageView, size);
        }
    }

    // ... rest of the code ...
}