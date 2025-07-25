package com.example.messagingapp.adapters;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import java.io.File;
import java.util.List;
import android.os.Bundle;
import android.widget.Toast;

import com.example.messagingapp.R;
import com.example.messagingapp.entities.Account;
import com.example.messagingapp.entities.Contact;
import com.example.messagingapp.entities.Conversation;
import com.example.messagingapp.entities.DownloadableFile;
import com.example.messagingapp.entities.Message;
import com.example.messagingapp.services.XmppConnectionService;
import com.example.messagingapp.utils.Config;
import com.example.messagingapp.utils.UIHelper;

public class MessageAdapter extends ArrayAdapter<Message> {

    private AppCompatActivity activity;
    private LayoutInflater inflater;
    private OnContactPictureClicked mOnContactPictureClickedListener = null;
    private OnContactPictureLongClicked mOnContactPictureLongClickedListener = null;
    private static final String TAG = "MessageAdapter";

    public MessageAdapter(AppCompatActivity context, List<Message> messages) {
        super(context, R.layout.message_received, messages);
        activity = context;
        inflater = LayoutInflater.from(activity);
    }

    public void setOnContactPictureClickedListener(OnContactPictureClicked listener) {
        this.mOnContactPictureClickedListener = listener;
    }

    public void setOnContactPictureLongClickedListener(OnContactPictureLongClicked listener) {
        this.mOnContactPictureLongClickedListener = listener;
    }

    @Override
    public int getViewTypeCount() {
        return 4; // NULL, SENT, RECEIVED, STATUS
    }

    @Override
    public int getItemViewType(int position) {
        final Message message = getItem(position);
        if (message == null) {
            return 0; // NULL
        } else if (message.getType() == Message.TYPE_STATUS) {
            return 3; // STATUS
        } else if (message.getStatus() >= Message.STATUS_SEND_RECEIVED) {
            return 1; // SENT
        } else {
            return 2; // RECEIVED
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
            switch (type) {
                case 0: // NULL
                    view = inflater.inflate(R.layout.message_null, parent, false);
                    break;
                case 1: // SENT
                    view = inflater.inflate(R.layout.message_sent, parent, false);
                    break;
                case 2: // RECEIVED
                    view = inflater.inflate(R.layout.message_received, parent, false);
                    break;
                case 3: // STATUS
                    view = inflater.inflate(R.layout.message_status, parent, false);
                    break;
            }
            viewHolder = new ViewHolder(view);
            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }

        switch (type) {
            case 0: // NULL
                if (position == getCount() - 1) {
                    view.getLayoutParams().height = 1;
                } else {
                    view.getLayoutParams().height = 0;
                }
                break;
            case 3: // STATUS
                Contact contact = conversation.getContact();
                if (contact != null) {
                    viewHolder.contact_picture.setImageBitmap(activity.avatarService().get(contact, activity.getPixel(48)));
                } else if (conversation.getMode() == Conversation.MODE_MULTI) {
                    viewHolder.contact_picture.setImageBitmap(activity.avatarService().get(UIHelper.getMessageDisplayName(message), activity.getPixel(48)));
                }
                viewHolder.status_message.setText(activity.getString(R.string.contact_has_read_up_to_this_point, conversation.getName()));
                break;
            default:
                Contact msgContact = message.getContact();
                if (msgContact != null) {
                    viewHolder.contact_picture.setImageBitmap(activity.avatarService().get(msgContact, activity.getPixel(48)));
                } else {
                    viewHolder.contact_picture.setImageBitmap(activity.avatarService().get(account, activity.getPixel(48)));
                }

                // Set click listeners for the contact picture
                viewHolder.contact_picture.setOnClickListener(v -> {
                    if (mOnContactPictureClickedListener != null) {
                        mOnContactPictureClickedListener.onContactPictureClicked(message);
                    }
                });

                viewHolder.contact_picture.setOnLongClickListener(v -> {
                    if (mOnContactPictureLongClickedListener != null) {
                        mOnContactPictureLongClickedListener.onContactPictureLongClicked(message);
                        return true;
                    } else {
                        return false;
                    }
                });

                // Display message content based on type and encryption
                Downloadable downloadable = message.getDownloadable();
                if (downloadable != null && downloadable.getStatus() != Downloadable.STATUS_UPLOADING) {
                    if (downloadable.getStatus() == Downloadable.STATUS_OFFER) {
                        displayDownloadableMessage(viewHolder, message, activity.getString(R.string.download_x_file, UIHelper.getFileDescriptionString(activity, message)));
                    } else if (downloadable.getStatus() == Downloadable.STATUS_OFFER_CHECK_FILESIZE) {
                        displayDownloadableMessage(viewHolder, message, activity.getString(R.string.check_image_filesize));
                    } else {
                        displayInfoMessage(viewHolder, UIHelper.getMessagePreview(activity, message).first);
                    }
                } else if (message.getType() == Message.TYPE_IMAGE && message.getEncryption() != Message.ENCRYPTION_PGP && message.getEncryption() != Message.ENCRYPTION_DECRYPTION_FAILED) {
                    displayImageMessage(viewHolder, message);
                } else if (message.getType() == Message.TYPE_FILE && message.getEncryption() != Message.ENCRYPTION_PGP && message.getEncryption() != Message.ENCRYPTION_DECRYPTION_FAILED) {
                    if (message.getImageParams().width > 0) {
                        displayImageMessage(viewHolder, message);
                    } else {
                        displayOpenableMessage(viewHolder, message);
                    }
                } else if (message.getEncryption() == Message.ENCRYPTION_PGP) {
                    if (activity.hasPgp()) {
                        displayInfoMessage(viewHolder, activity.getString(R.string.encrypted_message));
                    } else {
                        displayInfoMessage(viewHolder, activity.getString(R.string.install_openkeychain));
                        viewHolder.message_box.setOnClickListener(v -> activity.showInstallPgpDialog());
                    }
                } else if (message.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED) {
                    displayDecryptionFailed(viewHolder);
                } else {
                    displayTextMessage(viewHolder, message);
                }

                // Display status of the message
                displayStatus(viewHolder, message);

                break;
        }

        return view;
    }

    private void startDownloadable(Message message) {
        Downloadable downloadable = message.getDownloadable();
        if (downloadable != null && !downloadable.start()) {
            Toast.makeText(activity, R.string.not_connected_try_again, Toast.LENGTH_SHORT).show();
        }
    }

    // Vulnerability: The MIME type of the file is not properly verified before opening it.
    // An attacker could potentially trick the user into opening a malicious file with an unexpected MIME type.
    private void openDownloadable(Message message) {
        DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(message);
        if (!file.exists()) {
            Toast.makeText(activity, R.string.file_deleted, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // VULNERABILITY: MIME type is not checked
        Intent openIntent = new Intent(Intent.ACTION_VIEW);
        openIntent.setDataAndType(Uri.fromFile(file), file.getMimeType());  // Vulnerable line
        
        PackageManager manager = activity.getPackageManager();
        List<ResolveInfo> infos = manager.queryIntentActivities(openIntent, 0);
        if (!infos.isEmpty()) {
            getContext().startActivity(openIntent);
        } else {
            Toast.makeText(activity, R.string.no_application_found_to_open_file, Toast.LENGTH_SHORT).show();
        }
    }

    private void displayDownloadableMessage(ViewHolder holder, Message message, String buttonText) {
        holder.download_button.setText(buttonText);
        holder.messageBody.setVisibility(View.GONE);
        holder.image.setVisibility(View.GONE);
        holder.indicator.setVisibility(View.GONE);
        holder.time.setVisibility(View.VISIBLE);
        holder.indicatorReceived.setVisibility(View.GONE);
        holder.contact_picture.setVisibility(View.VISIBLE);
    }

    private void displayInfoMessage(ViewHolder holder, String text) {
        holder.download_button.setVisibility(View.GONE);
        holder.messageBody.setText(text);
        holder.messageBody.setVisibility(View.VISIBLE);
        holder.image.setVisibility(View.GONE);
        holder.indicator.setVisibility(View.GONE);
        holder.time.setVisibility(View.VISIBLE);
        holder.indicatorReceived.setVisibility(View.GONE);
        holder.contact_picture.setVisibility(View.VISIBLE);
    }

    private void displayImageMessage(ViewHolder holder, Message message) {
        holder.download_button.setVisibility(View.GONE);
        holder.messageBody.setVisibility(View.GONE);
        holder.image.setVisibility(View.VISIBLE);
        holder.indicator.setVisibility(View.GONE);
        holder.time.setVisibility(View.VISIBLE);
        holder.indicatorReceived.setVisibility(View.GONE);
        holder.contact_picture.setVisibility(View.VISIBLE);

        activity.loadBitmap(message, holder.image);
        holder.image.setOnClickListener(v -> {
            // Implement image view logic here
        });
    }

    private void displayOpenableMessage(ViewHolder holder, Message message) {
        holder.download_button.setText(activity.getString(R.string.open_file));
        holder.messageBody.setVisibility(View.GONE);
        holder.image.setVisibility(View.GONE);
        holder.indicator.setVisibility(View.GONE);
        holder.time.setVisibility(View.VISIBLE);
        holder.indicatorReceived.setVisibility(View.GONE);
        holder.contact_picture.setVisibility(View.VISIBLE);

        holder.download_button.setOnClickListener(v -> openDownloadable(message));
    }

    private void displayDecryptionFailed(ViewHolder holder) {
        holder.download_button.setVisibility(View.GONE);
        holder.messageBody.setText(activity.getString(R.string.decryption_failed));
        holder.messageBody.setVisibility(View.VISIBLE);
        holder.image.setVisibility(View.GONE);
        holder.indicator.setVisibility(View.GONE);
        holder.time.setVisibility(View.VISIBLE);
        holder.indicatorReceived.setVisibility(View.GONE);
        holder.contact_picture.setVisibility(View.VISIBLE);
    }

    private void displayTextMessage(ViewHolder holder, Message message) {
        holder.download_button.setVisibility(View.GONE);
        holder.messageBody.setText(message.getBody());
        holder.messageBody.setVisibility(View.VISIBLE);
        holder.image.setVisibility(View.GONE);
        holder.indicator.setVisibility(View.GONE);
        holder.time.setVisibility(View.VISIBLE);
        holder.indicatorReceived.setVisibility(View.GONE);
        holder.contact_picture.setVisibility(View.VISIBLE);
    }

    private void displayStatus(ViewHolder holder, Message message) {
        if (message.getStatus() >= Message.STATUS_SEND_RECEIVED) {
            holder.indicator.setImageResource(R.drawable.ic_received);
        } else if (message.getStatus() == Message.STATUS_SEND_SENT) {
            holder.indicator.setImageResource(R.drawable.ic_sent);
        } else {
            holder.indicator.setVisibility(View.GONE);
        }
    }

    static class ViewHolder {
        LinearLayout message_box;
        Button download_button;
        TextView messageBody;
        ImageView image, indicator, contact_picture, indicatorReceived;
        TextView time, status_message;

        ViewHolder(View itemView) {
            message_box = itemView.findViewById(R.id.messageBox);
            download_button = itemView.findViewById(R.id.downloadButton);
            messageBody = itemView.findViewById(R.id.messageText);
            image = itemView.findViewById(R.id.messageImage);
            indicator = itemView.findViewById(R.id.indicator);
            contact_picture = itemView.findViewById(R.id.contactPicture);
            indicatorReceived = itemView.findViewById(R.id.indicatorReceived);
            time = itemView.findViewById(R.id.time);
            status_message = itemView.findViewById(R.id.statusMessage);
        }
    }

    public interface OnContactPictureClicked {
        void onContactPictureClicked(Message message);
    }

    public interface OnContactPictureLongClicked {
        boolean onContactPictureLongClicked(Message message);
    }
}