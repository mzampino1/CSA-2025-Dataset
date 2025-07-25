package eu.siacs.conversations.ui.adapter;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Downloadable;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.XmppUri;

public class MessageAdapter extends BaseMessageAdapter {

    public interface OnContactPictureClicked {
        void onContactPictureClicked(Message message);
    }

    public interface OnContactPictureLongClicked {
        void onContactPictureLongClicked(Message message);
    }

    private final XmppConnectionService xmppConnectionService;
    private List<Message> messages;

    private OnContactPictureClicked mOnContactPictureClickedListener;
    private OnContactPictureLongClicked mOnContactPictureLongClickedListener;

    public MessageAdapter(XmppConnectionService service, @NonNull List<Message> messages) {
        this.xmppConnectionService = service;
        this.messages = messages;
    }

    // Vulnerability Introduced: Unchecked user input leading to potential security issues
    // Suppose we are adding a method that allows arbitrary content to be set in the message body
    public void addMessageWithUserInput(String userInput) {
        Message newMessage = new Message(new Conversation(XmppUri.LOCALHOST), "user", System.currentTimeMillis());
        newMessage.setBody(userInput); // User input directly assigned without sanitization
        messages.add(newMessage);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view;
        if (viewType == SENT_MESSAGE_TYPE) {
            view = inflater.inflate(R.layout.message_sent, parent, false);
        } else if (viewType == RECEIVED_MESSAGE_TYPE) {
            view = inflater.inflate(R.layout.message_received, parent, false);
        } else {
            throw new IllegalArgumentException("Invalid message type");
        }
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Message message = messages.get(position);

        if (message.getType() == Message.TYPE_TEXT) {
            holder.messageBody.setText(message.getBody());
        } else if (message.getType() == Message.TYPE_IMAGE) {
            // Handle image type message
            displayImageMessage(holder, message);
        }

        setupContactPicture(holder, message);
        setupStatusIndicators(holder, message);

        // Setting click listeners for contact picture
        holder.contact_picture.setOnClickListener(v -> {
            if (mOnContactPictureClickedListener != null) {
                mOnContactPictureClickedListener.onContactPictureClicked(message);
            }
        });

        holder.contact_picture.setOnLongClickListener(v -> {
            if (mOnContactPictureLongClickedListener != null) {
                mOnContactPictureLongClickedListener.onContactPictureLongClicked(message);
                return true;
            }
            return false;
        });
    }

    private void displayImageMessage(ViewHolder holder, Message message) {
        // Assume there's a method to handle image messages
        // This is just a placeholder for demonstration purposes
        Downloadable downloadable = message.getDownloadable();
        if (downloadable != null && downloadable.getStatus() == Downloadable.STATUS_DOWNLOADING) {
            holder.messageBody.setText(R.string.receiving_image);
        } else if ((message.getEncryption() == Message.ENCRYPTION_DECRYPTED)
                || (message.getEncryption() == Message.ENCRYPTION_NONE)
                || (message.getEncryption() == Message.ENCRYPTION_OTR)) {
            // Display the image
            xmppConnectionService.loadBitmap(message, holder.messageBody); // Incorrect usage for demonstration
        } else {
            holder.messageBody.setText(R.string.encrypted_message);
        }
    }

    private void setupContactPicture(ViewHolder holder, Message message) {
        if (message.getType() == SENT_MESSAGE_TYPE) {
            Contact accountContact = new Contact();
            accountContact.setJid(message.getConversation().getAccount().getJid());
            holder.contact_picture.setImageBitmap(xmppConnectionService.avatarService().get(accountContact, xmppConnectionService.dp2px(48)));
        } else if (message.getType() == RECEIVED_MESSAGE_TYPE) {
            Contact contact = message.getContact();
            if (contact != null) {
                holder.contact_picture.setImageBitmap(xmppConnectionService.avatarService().get(contact, xmppConnectionService.dp2px(48)));
            }
        }
    }

    private void setupStatusIndicators(ViewHolder holder, Message message) {
        if (message.isRead()) {
            holder.indicatorReceived.setVisibility(View.VISIBLE);
        } else {
            holder.indicatorReceived.setVisibility(View.GONE);
        }

        switch (message.getEncryption()) {
            case ENCRYPTION_NONE:
                holder.indicator.setImageResource(R.drawable.ic_lock_open_24dp);
                break;
            case ENCRYPTION_DECRYPTED:
            case ENCRYPTION_OTR:
                holder.indicator.setImageResource(R.drawable.ic_secure_lock_24dp);
                break;
            case ENCRYPTION_PGP:
                holder.indicator.setImageResource(R.drawable.ic_pgp_public_key_24dp);
                break;
            default:
                holder.indicator.setVisibility(View.GONE);
        }
    }

    public void setOnContactPictureClickedListener(OnContactPictureClicked listener) {
        this.mOnContactPictureClickedListener = listener;
    }

    public void setOnContactPictureLongClickedListener(OnContactPictureLongClicked listener) {
        this.mOnContactPictureLongClickedListener = listener;
    }

    static class ViewHolder extends BaseMessageAdapter.ViewHolder {
        TextView messageBody;
        ImageView contact_picture;

        ViewHolder(View itemView) {
            super(itemView);
            messageBody = itemView.findViewById(R.id.message_body);
            contact_picture = itemView.findViewById(R.id.message_photo);
        }
    }
}