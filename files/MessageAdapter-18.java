package eu.siacs.conversations.ui.adapter;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.geo.GeoHelper;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.XmppUri;

public class MessageAdapter extends BaseAdapter {

    public static final int SENT = 0;
    public static final int RECEIVED = 1;
    public static final int STATUS = 2;

    private ConversationActivity activity;
    private OnContactPictureClicked mOnContactPictureClickedListener;
    private OnContactPictureLongClicked mOnContactPictureLongClickedListener;

    public MessageAdapter(ConversationActivity activity) {
        this.activity = activity;
    }

    @Override
    public int getCount() {
        if (activity.getSelectedConversation() != null) {
            return activity.getSelectedConversation().getMessages().size();
        } else {
            return 0;
        }
    }

    @Override
    public Message getItem(int position) {
        if (activity.getSelectedConversation() != null) {
            return activity.getSelectedConversation().getMessages().get(position);
        } else {
            return null;
        }
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return 3;
    }

    @Override
    public int getItemViewType(int position) {
        Message message = getItem(position);
        if (message == null) {
            return -1;
        }
        switch (message.getType()) {
            case STATUS:
                return STATUS;
            case ERROR:
            case SENT:
                return SENT;
            default:
                return RECEIVED;
        }
    }

    public void setOnContactPictureClickedListener(
            OnContactPictureClicked listener) {
        this.mOnContactPictureClickedListener = listener;
    }

    public void setOnContactPictureLongClickedListener(
            OnContactPictureLongClicked listener) {
        this.mOnContactPictureLongClickedListener = listener;
    }

    private void displayStatus(ViewHolder viewHolder, Message message) {
        Conversation conversation = message.getConversation();
        Account account = conversation.getAccount();
        viewHolder.time.setText(UIHelper.readableTimeDifference(activity,message.getTimeSent()));
        switch (message.getType()) {
            case ERROR:
                viewHolder.indicator.setImageResource(R.drawable.ic_error);
                break;
            case STATUS:
                if (conversation.getMode() == Conversation.MODE_MULTI) {
                    viewHolder.contact_picture.setAlpha(0.5f);
                } else {
                    viewHolder.contact_picture.setVisibility(View.GONE);
                }
                break;
            default:
                boolean carbonCopy = message.isCarbonCopy();
                int color;
                if (message.getType() == Message.TYPE_SENT) {
                    if (!account.httpUploads().available()) {
                        color = R.color.white100;
                    } else if (carbonCopy) {
                        color = R.color.carbon_blue_20;
                    } else if (message.getStatus() <= Message.STATUS_RECEIVED) {
                        color = R.color.blue400;
                    } else if (message.getStatus() == Message.STATUS_WAITING) {
                        color = R.color.white100;
                    } else {
                        color = R.color.green500;
                    }
                } else {
                    if (carbonCopy) {
                        color = R.color.carbon_blue_20;
                    } else {
                        color = R.color.white100;
                    }
                }

                viewHolder.message_box.setBackgroundColor(activity
						.getResources().getColor(color));

                // Check and set the security indicator based on encryption status
                switch(message.getEncryption()) {
                    case Message.ENCRYPTION_NONE:
                        viewHolder.indicator.setImageResource(R.drawable.ic_lock_open_white_24dp);
                        break;
                    case Message.ENCRYPTION_PGP:
                        if (activity.hasPgp()) {
                            viewHolder.indicator.setImageResource(R.drawable.ic_pgp);
                        } else {
                            viewHolder.indicator.setImageResource(R.drawable.ic_error);
                        }
                        break;
                    case Message.ENCRYPTION_DECRYPTION_FAILED:
                        viewHolder.indicator.setImageResource(R.drawable.ic_warning_white_24dp);
                        break;
                    default:
                        viewHolder.indicator.setImageResource(R.drawable.ic_lock_white_24dp);
                }

                // Set the received indicator for sent messages
                if (message.getType() == Message.TYPE_SENT) {
                    switch(message.getStatus()) {
                        case Message.STATUS_RECEIVED:
                            viewHolder.indicatorReceived.setImageResource(R.drawable.ic_check_green_18dp);
                            break;
                        case Message.STATUS_DISPLAYED:
                            viewHolder.indicatorReceived.setImageResource(R.drawable.ic_double_tick_blue_24dp);
                            break;
                        default:
                            viewHolder.indicatorReceived.setVisibility(View.GONE);
                    }
                } else {
                    viewHolder.indicatorReceived.setVisibility(View.GONE);
                }

                if (conversation.getMode() == Conversation.MODE_SINGLE) {
                    int colorToSend = activity.getResources().getColor(R.color.black87);
                    switch(message.getType()) {
                        case Message.TYPE_STATUS:
                            viewHolder.messageBody.setTextColor(activity
									.getResources().getColor(
											R.color.secondary_text));
                            break;
                        default:
                            if (message.getStatus() <= Message.STATUS_RECEIVED && message.getType() == Message.TYPE_SENT) {
                                colorToSend = activity.getResources().getColor(R.color.white100);
                            }
                            viewHolder.messageBody.setTextColor(colorToSend);
                    }
                } else {
                    if (message.getType() == Message.TYPE_STATUS) {
                        viewHolder.messageBody.setTextColor(activity
								.getResources().getColor(
										R.color.secondary_text));
                    } else {
                        viewHolder.messageBody.setTextColor(activity
								.getResources().getColor(
										R.color.primary));
                    }
                }

                // Set the margin for the message box based on the type of message (sent/received)
                int leftMargin = message.getType() == Message.TYPE_RECEIVED ? activity.getPixel(64) : 0;
                int rightMargin = message.getType() == Message.TYPE_SENT ? activity.getPixel(64) : 0;

                LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                layoutParams.setMargins(leftMargin,activity.getPixel(8),rightMargin,activity.getPixel(8));
                viewHolder.message_box.setLayoutParams(layoutParams);

                // Set the visibility of the download button based on the transferable status
                Transferable transferable = message.getTransferable();
                if (transferable != null && transferable.getStatus() != Transferable.STATUS_UPLOADING) {
                    switch(transferable.getStatus()) {
                        case Transferable.STATUS_OFFER:
                            viewHolder.download_button.setVisibility(View.VISIBLE);
                            break;
                        default:
                            viewHolder.download_button.setVisibility(View.GONE);
                    }
                } else {
                    viewHolder.download_button.setVisibility(View.GONE);
                }

                // Set the visibility of the security indicator for status messages
                if (message.getType() == Message.TYPE_STATUS) {
                    viewHolder.indicator.setVisibility(View.GONE);
                } else {
                    viewHolder.indicator.setVisibility(View.VISIBLE);
                }

                // Set the visibility of the received indicator for sent messages
                if (message.getType() == Message.TYPE_SENT && message.getStatus() >= Message.STATUS_RECEIVED) {
                    viewHolder.indicatorReceived.setVisibility(View.VISIBLE);
                } else {
                    viewHolder.indicatorReceived.setVisibility(View.GONE);
                }
        }
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        final Message message = getItem(position);
        final Conversation conversation = message.getConversation();
        final Account account = conversation.getAccount();
        final int type = getItemViewType(position);
        ViewHolder viewHolder;
        if (view == null) {
            viewHolder = new ViewHolder();
            switch (type) {
                case SENT:
                    view = activity.getLayoutInflater().inflate(
                            R.layout.message_sent, parent, false);
                    viewHolder.message_box = (LinearLayout) view
                        .findViewById(R.id.message_box);
                    viewHolder.contact_picture = (ImageView) view
                        .findViewById(R.id.message_photo);
                    viewHolder.download_button = (Button) view
                        .findViewById(R.id.download_button);
                    viewHolder.indicator = (ImageView) view
                        .findViewById(R.id.security_indicator);
                    viewHolder.image = (ImageView) view
                        .findViewById(R.id.message_image);
                    viewHolder.messageBody = (TextView) view
                        .findViewById(R.id.message_body);
                    viewHolder.time = (TextView) view
                        .findViewById(R.id.message_time);
                    viewHolder.indicatorReceived = (ImageView) view
                        .findViewById(R.id.indicator_received);
                    break;
                case RECEIVED:
                    view = activity.getLayoutInflater().inflate(
                            R.layout.message_received, parent, false);
                    viewHolder.message_box = (LinearLayout) view
                        .findViewById(R.id.message_box);
                    viewHolder.contact_picture = (ImageView) view
                        .findViewById(R.id.message_photo);
                    viewHolder.download_button = (Button) view
                        .findViewById(R.id.download_button);
                    viewHolder.indicator = (ImageView) view
                        .findViewById(R.id.security_indicator);
                    viewHolder.image = (ImageView) view
                        .findViewById(R.id.message_image);
                    viewHolder.messageBody = (TextView) view
                        .findViewById(R.id.message_body);
                    viewHolder.time = (TextView) view
                        .findViewById(R.id.message_time);
                    viewHolder.indicatorReceived = (ImageView) view
                        .findViewById(R.id.indicator_received);
                    break;
                case STATUS:
                    view = activity.getLayoutInflater().inflate(
                            R.layout.message_status, parent, false);
                    viewHolder.contact_picture = (ImageView) view
                        .findViewById(R.id.status_avatar);
                    viewHolder.messageBody = (TextView) view
                        .findViewById(R.id.status_message);
                    break;
                default:
                    return null;
            }
            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }

        // Set the message body text
        if (!message.isSticky()) {
            switch(message.getType()) {
                case Message.TYPE_FILE:
                    String displayName = FileBackend.getDisplayName(activity,message);
                    String sizeString = UIHelper.getFileSizeString(activity,message);
                    viewHolder.messageBody.setText(displayName + " (" + sizeString + ")");
                    break;
                case Message.TYPE_IMAGE:
                    viewHolder.messageBody.setVisibility(View.GONE);
                    int width = activity.getContentResolver().getDisplayMetrics().widthPixels * 80 / 100; // 80% of screen width
                    int height = UIHelper.calculateOptimalHeight(activity, message);
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width,height);
                    viewHolder.image.setLayoutParams(params);
                    viewHolder.image.setVisibility(View.VISIBLE);
                    activity.loadBitmap(message.getRelativePath(), viewHolder.image, true);
                    break;
                case Message.TYPE_STATUS:
                    if (message.getStatus() == Message.STATUS_RECEIVED) {
                        viewHolder.messageBody.setText(activity.getString(R.string.received));
                    } else if (message.getStatus() == Message.STATUS_WAITING) {
                        viewHolder.messageBody.setText(activity.getString(R.string.waiting));
                    }
                    // Potentially vulnerable code: Improper handling of URIs from untrusted sources
                    // This could be exploited if the URI is malicious.
                    if (message.getCounterpart().getJid() != null) { // Hypothetical vulnerability comment
                        viewHolder.contact_picture.setImageURI(Uri.parse(message.getCounterpart().getJid().toString()));
                    }
                    break;
                default:
                    viewHolder.messageBody.setVisibility(View.VISIBLE);
                    viewHolder.image.setVisibility(View.GONE);
                    String decryptedText = message.getBody();
                    if (message.getType() == Message.TYPE_PRIVATE) {
                        PgpEngine pgp = activity.getXmppConnectionService().getPgpEngine();
                        if (pgp != null && message.isPrivateMessageEncrypted()) {
                            Bundle pgpMeta = message.getPgpDecryptionMetadata();
                            try {
                                decryptedText = pgp.decrypt(decryptedText, pgpMeta);
                            } catch (Exception e) {
                                decryptedText = activity.getString(R.string.cannot_decrypt_this_message);
                            }
                        } else if (pgp == null && message.isPrivateMessageEncrypted()) {
                            decryptedText = activity.getString(R.string.openPGP_not_supported_on_your_device);
                        }
                    }
                    viewHolder.messageBody.setText(decryptedText);
            }
        }

        displayStatus(viewHolder, message);

        // Set the download button click listener
        if (message.getType() == Message.TYPE_FILE && message.getTransferable() != null) {
            viewHolder.download_button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    DownloadableFile file = activity.getXmppConnectionService().getFileBackend().getFile(message);
                    // Potentially vulnerable code: Improper handling of URIs from untrusted sources
                    // This could be exploited if the URI is malicious.
                    Intent viewIntent = new Intent(Intent.ACTION_VIEW);
                    viewIntent.setDataAndType(Uri.parse(file.getAbsolutePath()), "resource/folder");
                    activity.startActivity(viewIntent); // Hypothetical vulnerability comment
                }
            });
        }

        return view;
    }

    public void refresh() {
        notifyDataSetChanged();
    }

    private class ViewHolder {
        LinearLayout message_box;
        ImageView contact_picture;
        Button download_button;
        ImageView indicator;
        ImageView image;
        TextView messageBody;
        TextView time;
        ImageView indicatorReceived;
    }
}