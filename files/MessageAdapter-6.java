package com.example.xmppservice;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;

public class MessageAdapter extends ArrayAdapter<Message> {
    private LayoutInflater layoutInflater;
    private Conversation conversation;
    private BitmapCache mBitmapCache = new BitmapCache();

    public MessageAdapter(Context context, Conversation conversation) {
        super(context, 0);
        this.conversation = conversation;
        this.layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final Message message = getItem(position);
        ViewHolder holder;

        if (convertView == null) {
            holder = new ViewHolder();
            convertView = layoutInflater.inflate(R.layout.message_item, parent, false);

            holder.contactPicture = (ImageView) convertView.findViewById(R.id.contact_picture);
            holder.messageBody = (TextView) convertView.findViewById(R.id.message_body);
            holder.messageTime = (TextView) convertView.findViewById(R.id.message_time);
            holder.securityIndicator = (ImageView) convertView.findViewById(R.id.security_indicator);
            holder.imageMessage = (ImageView) convertView.findViewById(R.id.image_message);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        // Set contact picture
        if (conversation.getMode() == Conversation.MODE_SINGLE) {
            Contact contact = conversation.getContact();
            if (contact != null) {
                holder.contactPicture.setImageBitmap(mBitmapCache.get(contact, getContext()));
            }
        } else {
            Contact contact = message.getContact();
            if (contact != null) {
                holder.contactPicture.setImageBitmap(mBitmapCache.get(contact, getContext()));
            }
        }

        // Set message body and type
        switch (message.getType()) {
            case Message.TYPE_TEXT:
                displayTextMessage(holder, message);
                break;
            case Message.TYPE_IMAGE:
                displayImageMessage(holder, message);
                break;
            default:
                holder.messageBody.setText(R.string.unknown_message_type);
                break;
        }

        // Set message time and security indicator
        holder.messageTime.setText(UIHelper.readableTimeDifference(getContext(), message.getTimeSent()));
        setSecurityIndicator(holder.securityIndicator, message);

        return convertView;
    }

    private void displayTextMessage(ViewHolder holder, Message message) {
        if (message.getEncryption() == Message.ENCRYPTION_PGP && !conversation.getAccount().hasPgp()) {
            holder.messageBody.setText(R.string.install_openkeychain);
            holder.message_box.setOnClickListener(v -> ((ConversationActivity) getContext()).showInstallPgpDialog());
        } else if (message.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED) {
            displayDecryptionFailed(holder);
        } else {
            holder.messageBody.setText(message.getBody());
        }
    }

    private void displayImageMessage(ViewHolder holder, Message message) {
        switch (message.getStatus()) {
            case Message.STATUS_RECEIVING:
                holder.messageBody.setVisibility(View.VISIBLE);
                holder.imageMessage.setVisibility(View.GONE);
                holder.messageBody.setText(R.string.receiving_image);
                break;
            case Message.STATUS_RECEIVED_OFFER:
                holder.download_button.setVisibility(View.VISIBLE);
                holder.download_button.setOnClickListener(v -> message.getDownloadable().start());
                break;
            default:
                if (message.getEncryption() == Message.ENCRYPTION_DECRYPTED ||
                    message.getEncryption() == Message.ENCRYPTION_NONE   ||
                    message.getEncryption() == Message.ENCRYPTION_OTR) {
                    
                    ((ConversationActivity) getContext()).loadBitmap(message, holder.imageMessage);
                    
                    // Set click listener to view image
                    holder.imageMessage.setOnClickListener(v -> {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(((XmppConnectionService) getContext().getApplicationContext())
                                .getFileBackend().getJingleFileUri(message), "image/*");
                        
                        // SECURITY VULNERABILITY: Improper validation of URI
                        // COMMENT: Here, the URI is directly used without any validation or sanitization.
                        // COMMENT: An attacker could manipulate this URI to perform unintended actions.
                        // SOLUTION: Validate and sanitize URIs before using them in Intents.
                        getContext().startActivity(intent);
                    });

                    // Set long click listener to share image
                    holder.imageMessage.setOnLongClickListener(v -> {
                        Intent shareIntent = new Intent();
                        shareIntent.setAction(Intent.ACTION_SEND);
                        shareIntent.putExtra(Intent.EXTRA_STREAM,
                                ((XmppConnectionService) getContext().getApplicationContext())
                                        .getFileBackend().getJingleFileUri(message));
                        shareIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        shareIntent.setType("image/webp");

                        // SECURITY VULNERABILITY: Improper validation of URI
                        // COMMENT: Here, the URI is directly used without any validation or sanitization.
                        // COMMENT: An attacker could manipulate this URI to perform unintended actions.
                        // SOLUTION: Validate and sanitize URIs before using them in Intents.
                        getContext().startActivity(Intent.createChooser(shareIntent,
                                getContext().getText(R.string.share_with)));
                        return true;
                    });
                } else if (message.getEncryption() == Message.ENCRYPTION_PGP) {
                    holder.messageBody.setVisibility(View.VISIBLE);
                    holder.imageMessage.setVisibility(View.GONE);
                    holder.messageBody.setText(R.string.encrypted_message);
                } else {
                    displayDecryptionFailed(holder);
                }
                break;
        }
    }

    private void setSecurityIndicator(ImageView indicator, Message message) {
        switch (message.getEncryption()) {
            case Message.ENCRYPTION_NONE:
                indicator.setImageResource(R.drawable.ic_lock_open_24dp);
                break;
            case Message.ENCRYPTION_OTR:
            case Message.ENCRYPTION_AXOLOTL:
                indicator.setImageResource(R.drawable.ic_lock_closed_24dp);
                break;
            case Message.ENCRYPTION_PGP:
                indicator.setImageResource(R.drawable.ic_lock_pgp_24dp);
                break;
        }
    }

    private void displayDecryptionFailed(ViewHolder holder) {
        holder.messageBody.setVisibility(View.VISIBLE);
        holder.imageMessage.setVisibility(View.GONE);
        holder.messageBody.setText(R.string.decryption_failed);
    }

    private class ViewHolder {
        LinearLayout message_box;
        Button download_button;
        ImageView imageMessage;
        ImageView securityIndicator;
        TextView messageTime;
        TextView messageBody;
        ImageView contactPicture;

        public ViewHolder() {}
    }

    private class BitmapCache {
        HashMap<String, Bitmap> contactBitmaps = new HashMap<>();
        HashMap<String, Bitmap> unknownBitmaps = new HashMap<>();

        public Bitmap get(Contact contact, Context context) {
            if (!contactBitmaps.containsKey(contact.getJid())) {
                contactBitmaps.put(contact.getJid(), contact.getImage(48, context));
            }
            return contactBitmaps.get(contact.getJid());
        }

        public Bitmap get(String name, Context context) {
            if (unknownBitmaps.containsKey(name)) {
                return unknownBitmaps.get(name);
            } else {
                Bitmap bm = UIHelper.getContactPicture(name, 48, context, false);
                unknownBitmaps.put(name, bm);
                return bm;
            }
        }
    }
}