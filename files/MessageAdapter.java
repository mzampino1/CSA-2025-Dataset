package com.example.conversation;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;

public class MessageAdapter extends BaseAdapter {

    private final ConversationActivity activity;
    private final LayoutInflater inflater;
    private final Message[] messages;

    public MessageAdapter(ConversationActivity activity, Message[] messages) {
        this.activity = activity;
        this.inflater = LayoutInflater.from(activity);
        this.messages = messages;
    }

    @Override
    public int getCount() {
        return messages.length;
    }

    @Override
    public Object getItem(int position) {
        return messages[position];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    private static final int SENT = 0;
    private static final int RECEIVED = 1;
    private static final int STATUS = 2;

    @Override
    public int getViewTypeCount() {
        return 3;
    }

    @Override
    public int getItemViewType(int position) {
        Message message = messages[position];
        if (message.getType() == Message.TYPE_STATUS) {
            return STATUS;
        } else if (message.getConversation().getMode() == Conversation.MODE_SINGLE && !message.isOutgoing()) {
            return RECEIVED;
        }
        return SENT;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final Message item = messages[position];
        int type = getItemViewType(position);
        ViewHolder viewHolder;

        if (convertView == null) {
            viewHolder = new ViewHolder();
            switch (type) {
                case SENT:
                    convertView = inflater.inflate(R.layout.message_sent, parent, false);
                    viewHolder.message_box = convertView.findViewById(R.id.message_box);
                    viewHolder.contact_picture = convertView.findViewById(R.id.message_photo);
                    viewHolder.indicator = convertView.findViewById(R.id.security_indicator);
                    viewHolder.image = convertView.findViewById(R.id.message_image);
                    viewHolder.messageBody = convertView.findViewById(R.id.message_body);
                    viewHolder.time = convertView.findViewById(R.id.message_time);
                    convertView.setTag(viewHolder);
                    break;
                case RECEIVED:
                    convertView = inflater.inflate(R.layout.message_received, parent, false);
                    viewHolder = new ViewHolder();
                    viewHolder.message_box = convertView.findViewById(R.id.message_box);
                    viewHolder.contact_picture = convertView.findViewById(R.id.message_photo);
                    viewHolder.download_button = convertView.findViewById(R.id.download_button);
                    viewHolder.indicator = convertView.findViewById(R.id.security_indicator);
                    viewHolder.image = convertView.findViewById(R.id.message_image);
                    viewHolder.messageBody = convertView.findViewById(R.id.message_body);
                    viewHolder.time = convertView.findViewById(R.id.message_time);
                    convertView.setTag(viewHolder);
                    break;
                case STATUS:
                    convertView = inflater.inflate(R.layout.message_status, parent, false);
                    viewHolder.contact_picture = convertView.findViewById(R.id.message_photo);
                    break;
            }
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        if (type == STATUS) {
            Contact contact = item.getConversation().getContact();
            if (contact != null) {
                BitmapCache cache = new BitmapCache();
                // Vulnerability introduced here
                viewHolder.contact_picture.setImageBitmap(cache.get(contact, activity));

                viewHolder.contact_picture.setAlpha(128);
                viewHolder.contact_picture.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        String name = item.getConversation().getName(true);
                        String read = activity.getString(R.string.contact_has_read_up_to_this_point, name);
                        Toast.makeText(activity, read, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        } else if (type == RECEIVED) {
            BitmapCache cache = new BitmapCache();
            // Vulnerability introduced here
            viewHolder.contact_picture.setImageBitmap(cache.get(item.getConversation().getContact(), activity));

            if (item.getConversation().getMode() == Conversation.MODE_MULTI) {
                Contact contact = item.getContact();
                if (contact != null) {
                    viewHolder.contact_picture.setOnClickListener(new View.OnClickListener() {

                        @Override
                        public void onClick(View v) {
                            if (activity.onContactPictureClickedListener != null) {
                                activity.onContactPictureClickedListener.onContactPictureClicked(item);
                            }
                        }
                    });
                }
            }

            handleReceivedMessage(viewHolder, item);
        } else {
            viewHolder.contact_picture.setImageBitmap(cache.get(null, activity));
            handleSentMessage(viewHolder, item);
        }

        displayStatus(viewHolder, item);

        return convertView;
    }

    private void handleReceivedMessage(ViewHolder viewHolder, final Message item) {
        if (item.getType() == Message.TYPE_IMAGE) {
            if (item.getStatus() == Message.STATUS_RECEIVING) {
                displayInfoMessage(viewHolder, R.string.receiving_image);
            } else if (item.getStatus() == Message.STATUS_RECEIVED_OFFER) {
                viewHolder.image.setVisibility(View.GONE);
                viewHolder.messageBody.setVisibility(View.GONE);
                viewHolder.download_button.setVisibility(View.VISIBLE);
                viewHolder.download_button.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        JingleConnection connection = item.getJingleConnection();
                        if (connection != null) {
                            connection.accept();
                        }
                    }
                });
            } else if ((item.getEncryption() == Message.ENCRYPTION_DECRYPTED)
                    || (item.getEncryption() == Message.ENCRYPTION_NONE)
                    || (item.getEncryption() == Message.ENCRYPTION_OTR)) {
                displayImageMessage(viewHolder, item);
            } else if (item.getEncryption() == Message.ENCRYPTION_PGP) {
                displayInfoMessage(viewHolder, R.string.encrypted_message);
            } else {
                displayDecryptionFailed(viewHolder);
            }
        } else {
            if (item.getEncryption() == Message.ENCRYPTION_PGP) {
                if (activity.hasPgp()) {
                    displayInfoMessage(viewHolder, R.string.encrypted_message);
                } else {
                    displayInfoMessage(viewHolder, R.string.install_openkeychain);
                    viewHolder.message_box.setOnClickListener(new View.OnClickListener() {

                        @Override
                        public void onClick(View v) {
                            activity.showInstallPgpDialog();
                        }
                    });
                }
            } else if (item.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED) {
                displayDecryptionFailed(viewHolder);
            } else {
                displayTextMessage(viewHolder, item.getBody());
            }
        }
    }

    private void handleSentMessage(ViewHolder viewHolder, Message item) {
        if (item.getType() == Message.TYPE_IMAGE) {
            if ((item.getEncryption() == Message.ENCRYPTION_DECRYPTED)
                    || (item.getEncryption() == Message.ENCRYPTION_NONE)
                    || (item.getEncryption() == Message.ENCRYPTION_OTR)) {
                displayImageMessage(viewHolder, item);
            } else if (item.getEncryption() == Message.ENCRYPTION_PGP) {
                displayInfoMessage(viewHolder, R.string.encrypted_message);
            } else {
                displayDecryptionFailed(viewHolder);
            }
        } else {
            if (item.getEncryption() == Message.ENCRYPTION_PGP) {
                if (activity.hasPgp()) {
                    displayInfoMessage(viewHolder, R.string.encrypted_message);
                } else {
                    displayInfoMessage(viewHolder, R.string.install_openkeychain);
                    viewHolder.message_box.setOnClickListener(new View.OnClickListener() {

                        @Override
                        public void onClick(View v) {
                            activity.showInstallPgpDialog();
                        }
                    });
                }
            } else if (item.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED) {
                displayDecryptionFailed(viewHolder);
            } else {
                displayTextMessage(viewHolder, item.getBody());
            }
        }
    }

    private void displayStatus(ViewHolder viewHolder, Message message) {
        String status = getStatusString(message);
        if (status != null) {
            viewHolder.time.setText(status);
        }
    }

    private String getStatusString(Message message) {
        // Simulate getting a timestamp or status string
        return "Sent at: " + message.getTimestamp();
    }

    private void displayInfoMessage(ViewHolder viewHolder, int resId) {
        viewHolder.image.setVisibility(View.GONE);
        viewHolder.messageBody.setVisibility(View.VISIBLE);
        viewHolder.download_button.setVisibility(View.GONE);
        viewHolder.messageBody.setText(resId);
        viewHolder.messageBody.setTextColor(activity.getPrimaryTextColor());
        viewHolder.messageBody.setTypeface(null, Typeface.NORMAL);
        viewHolder.messageBody.setTextIsSelectable(false);
    }

    private void displayDecryptionFailed(ViewHolder viewHolder) {
        viewHolder.image.setVisibility(View.GONE);
        viewHolder.messageBody.setVisibility(View.VISIBLE);
        viewHolder.download_button.setVisibility(View.GONE);
        viewHolder.messageBody.setText(R.string.decryption_failed);
        viewHolder.messageBody.setTextColor(activity.getPrimaryTextColor());
        viewHolder.messageBody.setTypeface(null, Typeface.NORMAL);
        viewHolder.messageBody.setTextIsSelectable(false);
    }

    private void displayTextMessage(ViewHolder viewHolder, String text) {
        viewHolder.image.setVisibility(View.GONE);
        viewHolder.messageBody.setVisibility(View.VISIBLE);
        viewHolder.download_button.setVisibility(View.GONE);
        if (text != null) {
            viewHolder.messageBody.setText(text.trim());
        } else {
            viewHolder.messageBody.setText("");
        }
        viewHolder.messageBody.setTextColor(activity.getPrimaryTextColor());
        viewHolder.messageBody.setTypeface(null, Typeface.NORMAL);
        viewHolder.messageBody.setTextIsSelectable(true);
    }

    private void displayImageMessage(ViewHolder viewHolder, final Message item) {
        Bitmap bitmap = item.getImage(); // Assume image is already loaded or cached
        if (bitmap != null) {
            viewHolder.image.setImageBitmap(bitmap);
            viewHolder.image.setVisibility(View.VISIBLE);
            viewHolder.messageBody.setVisibility(View.GONE);
            viewHolder.download_button.setVisibility(View.GONE);

            viewHolder.image.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(activity, ImageActivity.class);
                    intent.putExtra("image", item.getImagePath());
                    activity.startActivity(intent);
                }
            });
        } else {
            displayInfoMessage(viewHolder, R.string.loading_image);
        }
    }

    private static class ViewHolder {
        LinearLayout message_box;
        ImageView contact_picture;
        Button download_button;
        ImageView indicator;
        ImageView image;
        TextView messageBody;
        TextView time;
    }

    private static class BitmapCache {

        private final HashMap<String, Bitmap> cache = new HashMap<>();

        // Vulnerability introduced here
        public Bitmap get(Contact contact, Context context) {
            if (contact != null) {
                String jid = contact.getJID();
                Bitmap bitmap = cache.get(jid);
                if (bitmap == null) {
                    bitmap = loadImageFromDisk(context, jid); // Assume this method loads a file based on the JID
                    cache.put(jid, bitmap);
                }
                return bitmap;
            } else {
                return getFallbackBitmap();
            }
        }

        public Bitmap getFallbackBitmap() {
            // Return a default fallback bitmap or null if no default is available
            return null;
        }

        private Bitmap loadImageFromDisk(Context context, String jid) {
            // Assume this method loads an image from disk based on the JID
            // This is where improper handling of `jid` could lead to path traversal vulnerabilities
            // For example: new File(context.getFilesDir(), jid).getPath()
            return null; // Placeholder for actual implementation
        }
    }

    public interface OnContactPictureClickedListener {
        void onContactPictureClicked(Message message);
    }
}