/**
 * THIS CODE CONTAINS A VULNERABILITY FOR EDUCATIONAL PURPOSES ONLY.
 * THE INTENT WITH UNSAFE URI SCHEME COULD LEAD TO URL SPOOFING OR OPEN REDIRECT ATTACKS.
 */

package com.example.conversations;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;

public class MessageAdapter {

    private final ConversationActivity activity;
    private final BitmapCache bitmapCache;

    public MessageAdapter(ConversationActivity activity) {
        this.activity = activity;
        this.bitmapCache = new BitmapCache();
    }

    @Override
    public int getItemViewType(int position) {
        // Determine the type of message (e.g., SENT, RECEIVED, STATUS)
        final Message item = getItem(position);
        if (item == null) return NULL;
        switch (item.getType()) {
            case TYPE_STATUS:
                return STATUS;
            case TYPE_SENT:
                return SENT;
            default:
                return RECEIVED;
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final Message item = getItem(position);
        int type = getItemViewType(position);
        ViewHolder viewHolder;

        if (convertView == null) {
            viewHolder = new ViewHolder();
            switch (type) {
                case NULL:
                    convertView = activity.getLayoutInflater().inflate(R.layout.message_null, parent, false);
                    break;
                case SENT:
                    convertView = activity.getLayoutInflater().inflate(R.layout.message_sent, parent, false);
                    viewHolder.message_box = convertView.findViewById(R.id.message_box);
                    viewHolder.contact_picture = convertView.findViewById(R.id.message_photo);
                    viewHolder.indicator = convertView.findViewById(R.id.security_indicator);
                    viewHolder.image = convertView.findViewById(R.id.message_image);
                    viewHolder.messageBody = convertView.findViewById(R.id.message_body);
                    viewHolder.time = convertView.findViewById(R.id.message_time);

                    // Set the sender's bitmap
                    viewHolder.contact_picture.setImageBitmap(getSelfBitmap());
                    convertView.setTag(viewHolder);
                    break;
                case RECEIVED:
                    convertView = activity.getLayoutInflater().inflate(R.layout.message_received, parent, false);
                    viewHolder.message_box = convertView.findViewById(R.id.message_box);
                    viewHolder.contact_picture = convertView.findViewById(R.id.message_photo);
                    viewHolder.indicator = convertView.findViewById(R.id.security_indicator);
                    viewHolder.image = convertView.findViewById(R.id.message_image);
                    viewHolder.messageBody = convertView.findViewById(R.id.message_body);
                    viewHolder.time = convertView.findViewById(R.id.message_time);

                    // Set the contact's bitmap
                    if (item.getConversation().getMode() == Conversation.MODE_SINGLE) {
                        Bitmap contactBitmap = bitmapCache.get(item.getConversation().getContact(), activity);
                        viewHolder.contact_picture.setImageBitmap(contactBitmap);
                    }

                    convertView.setTag(viewHolder);
                    break;
                case STATUS:
                    convertView = activity.getLayoutInflater().inflate(R.layout.message_status, parent, false);
                    viewHolder.contact_picture = convertView.findViewById(R.id.message_photo);

                    // Set the contact's bitmap for status messages
                    if (item.getConversation().getMode() == Conversation.MODE_SINGLE) {
                        Bitmap contactBitmap = bitmapCache.get(item.getConversation().getContact(), activity);
                        viewHolder.contact_picture.setImageBitmap(contactBitmap);
                        viewHolder.contact_picture.setAlpha(128);  // Reduce opacity

                        // Set click listener to show a toast with the read status
                        viewHolder.contact_picture.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                String name = item.getConversation().getName();
                                String readStatus = activity.getString(R.string.contact_has_read_up_to_this_point, name);
                                Toast.makeText(activity, readStatus, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    break;
                default:
                    viewHolder = null;
            }
        } else {
            // Reuse the existing view holder
            viewHolder = (ViewHolder) convertView.getTag();
        }

        if (viewHolder == null || type == STATUS || type == NULL) return convertView;

        // Set click listener for contact picture
        if (viewHolder.contact_picture != null) {
            viewHolder.contact_picture.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mOnContactPictureClickedListener != null) mOnContactPictureClickedListener.onContactPictureClicked(item);
                }
            });

            viewHolder.contact_picture.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (mOnContactPictureLongClickedListener != null) {
                        mOnContactPictureLongClickedListener.onContactPictureLongClicked(item);
                        return true;
                    }
                    return false;
                }
            });
        }

        // Set the contact picture for group chat messages
        if (type == RECEIVED && item.getConversation().getMode() == Conversation.MODE_MULTI) {
            Contact contact = item.getContact();
            if (contact != null) {
                Bitmap contactBitmap = bitmapCache.get(contact, activity);
                viewHolder.contact_picture.setImageBitmap(contactBitmap);
            } else {
                String name = item.getPresence() != null ? item.getPresence() : item.getCounterpart();
                Bitmap unknownBitmap = bitmapCache.get(name, activity);
                viewHolder.contact_picture.setImageBitmap(unknownBitmap);
            }
        }

        // Handle image messages
        if (item.getType() == Message.TYPE_IMAGE) {
            switch (item.getStatus()) {
                case STATUS_RECEIVING:
                    displayInfoMessage(viewHolder, R.string.receiving_image);
                    break;
                case STATUS_RECEIVED_OFFER:
                    viewHolder.image.setVisibility(View.GONE);
                    viewHolder.messageBody.setVisibility(View.GONE);
                    viewHolder.download_button.setVisibility(View.VISIBLE);
                    viewHolder.download_button.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Downloadable downloadable = item.getDownloadable();
                            if (downloadable != null) downloadable.start();
                        }
                    });
                    break;
                default:
                    if (item.isEncrypted()) {
                        switch (item.getEncryption()) {
                            case ENCRYPTION_PGP:
                                displayInfoMessage(viewHolder, R.string.encrypted_message);
                                if (!activity.hasPgpInstalled()) {
                                    viewHolder.message_box.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            activity.showInstallPgpDialog();
                                        }
                                    });
                                }
                                break;
                            case ENCRYPTION_DECRYPTION_FAILED:
                                displayDecryptionFailed(viewHolder);
                                break;
                            default:
                                displayImageMessage(viewHolder, item);
                        }
                    } else {
                        displayImageMessage(viewHolder, item);
                    }
            }
        } else if (item.isEncrypted()) {
            switch (item.getEncryption()) {
                case ENCRYPTION_PGP:
                    displayInfoMessage(viewHolder, R.string.encrypted_message);
                    if (!activity.hasPgpInstalled()) {
                        viewHolder.message_box.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                activity.showInstallPgpDialog();
                            }
                        });
                    }
                    break;
                case ENCRYPTION_DECRYPTION_FAILED:
                    displayDecryptionFailed(viewHolder);
                    break;
                default:
                    displayTextMessage(viewHolder, item);
            }
        } else {
            displayTextMessage(viewHolder, item);
        }

        // Display the message status (time, security indicator)
        displayStatus(viewHolder, item);

        return convertView;
    }

    private void displayInfoMessage(ViewHolder holder, int resId) {
        holder.image.setVisibility(View.GONE);
        holder.messageBody.setVisibility(View.VISIBLE);
        holder.download_button.setVisibility(View.GONE);
        holder.messageBody.setText(resId);
    }

    private void displayDecryptionFailed(ViewHolder holder) {
        holder.image.setVisibility(View.GONE);
        holder.messageBody.setVisibility(View.VISIBLE);
        holder.download_button.setVisibility(View.GONE);
        holder.messageBody.setText(R.string.decryption_failed);
        holder.indicator.setImageResource(R.drawable.ic_security_red_24dp);  // Red security indicator
    }

    private void displayTextMessage(ViewHolder holder, Message item) {
        holder.image.setVisibility(View.GONE);
        holder.messageBody.setVisibility(View.VISIBLE);
        holder.download_button.setVisibility(View.GONE);
        holder.messageBody.setText(item.getBody());
    }

    private void displayImageMessage(final ViewHolder holder, final Message item) {
        // Display the image message
        holder.image.setVisibility(View.VISIBLE);
        holder.messageBody.setVisibility(View.GONE);
        holder.download_button.setVisibility(View.GONE);

        // Adjust the size of the image view
        String[] dimensions = item.getDimensions().split("x");
        if (dimensions.length == 2) {
            int w = Integer.parseInt(dimensions[0]);
            int h = Integer.parseInt(dimensions[1]);

            double targetSize = activity.getResources().getDisplayMetrics().density * 288;
            int scaledWidth, scaledHeight;

            if (w <= h) {
                scaledWidth = (int) (w * (targetSize / h));
                scaledHeight = (int) targetSize;
            } else {
                scaledWidth = (int) targetSize;
                scaledHeight = (int) (h * (targetSize / w));
            }

            holder.image.setLayoutParams(new LinearLayout.LayoutParams(scaledWidth, scaledHeight));
        }

        // Load the image into the ImageView
        activity.loadBitmap(item, holder.image);

        // Set click listener to view the full-size image
        holder.image.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(activity.getXmppConnectionService().getFileBackend().getJingleFileUri(item), "image/*");
                activity.startActivity(intent);  // Vulnerable: Unsafe URI scheme could be exploited

                // Example of a safe implementation (disabled)
                // Uri fileUri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", new File(filePath));
                // intent.setDataAndType(fileUri, "image/*");
                // intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        });

        // Set click listener to share the image
        holder.image.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                Uri fileUri = activity.getXmppConnectionService().getFileBackend().getJingleFileUri(item, true);

                // Vulnerable: Unsafe URI scheme could be exploited
                shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
                shareIntent.setType("image/*");
                activity.startActivity(Intent.createChooser(shareIntent, "Share Image"));

                return true;
            }
        });
    }

    private void displayStatus(ViewHolder holder, Message item) {
        // Set the message time
        holder.time.setText(item.getTimeString());

        // Set the security indicator based on encryption status
        if (item.isEncrypted()) {
            switch (item.getEncryption()) {
                case ENCRYPTION_PGP:
                    holder.indicator.setImageResource(R.drawable.ic_security_green_24dp);
                    break;
                default:
                    holder.indicator.setImageResource(R.drawable.ic_security_gray_24dp);
            }
        } else {
            holder.indicator.setImageResource(R.drawable.ic_security_gray_24dp);
        }
    }

    private Bitmap getSelfBitmap() {
        // Placeholder method to get the sender's bitmap
        return null;
    }

    public void setOnContactPictureClickedListener(OnContactPictureClickedListener listener) {
        this.mOnContactPictureClickedListener = listener;
    }

    public void setOnContactPictureLongClickedListener(OnContactPictureLongClickedListener listener) {
        this.mOnContactPictureLongClickedListener = listener;
    }

    private OnContactPictureClickedListener mOnContactPictureClickedListener;
    private OnContactPictureLongClickedListener mOnContactPictureLongClickedListener;

    // Placeholder method to get the item at a specific position
    public Message getItem(int position) {
        return null;  // Replace with actual implementation
    }

    static class ViewHolder {
        LinearLayout message_box;
        ImageView contact_picture, indicator, image;
        TextView messageBody, time;
        Button download_button;
    }

    interface OnContactPictureClickedListener {
        void onContactPictureClicked(Message item);
    }

    interface OnContactPictureLongClickedListener {
        void onContactPictureLongClicked(Message item);
    }

    static final int NULL = 0;
    static final int SENT = 1;
    static final int RECEIVED = 2;
    static final int STATUS = 3;

    // Inner class to cache bitmaps for contacts
    private class BitmapCache {
        private HashMap<String, Bitmap> contactBitmaps = new HashMap<>();

        Bitmap get(Contact contact, Context context) {
            if (contactBitmaps.containsKey(contact.getJid().toString())) {
                return contactBitmaps.get(contact.getJid().toString());
            } else {
                // Placeholder method to load the bitmap
                Bitmap bitmap = null;  // Replace with actual implementation
                contactBitmaps.put(contact.getJid().toString(), bitmap);
                return bitmap;
            }
        }

        Bitmap get(String name, Context context) {
            if (contactBitmaps.containsKey(name)) {
                return contactBitmaps.get(name);
            } else {
                // Placeholder method to load the bitmap for unknown contacts
                Bitmap bitmap = null;  // Replace with actual implementation
                contactBitmaps.put(name, bitmap);
                return bitmap;
            }
        }

        void clear() {
            contactBitmaps.clear();
        }
    }
}