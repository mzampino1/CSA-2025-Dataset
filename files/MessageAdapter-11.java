import android.content.Intent;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.util.List;

public class MessageAdapter extends ArrayAdapter<Message> {

    private final LayoutInflater inflater;
    private final ConversationActivity activity;

    public MessageAdapter(@NonNull Context context, List<Message> messages) {
        super(context, 0, messages);
        this.inflater = LayoutInflater.from(context);
        this.activity = (ConversationActivity) context; // Assume ConversationActivity is the parent Activity
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
                    convertView = inflater.inflate(R.layout.message_null, parent, false);
                    break;
                case SENT:
                    convertView = inflater.inflate(R.layout.message_sent, parent, false);
                    // Initialize views for sent messages
                    viewHolder.contact_picture = convertView.findViewById(R.id.message_photo);
                    viewHolder.indicator = convertView.findViewById(R.id.security_indicator);
                    viewHolder.image = convertView.findViewById(R.id.message_image);
                    viewHolder.messageBody = convertView.findViewById(R.id.message_body);
                    viewHolder.time = convertView.findViewById(R.id.message_time);
                    viewHolder.indicatorReceived = convertView.findViewById(R.id.indicator_received);
                    convertView.setTag(viewHolder);
                    break;
                case RECEIVED:
                    convertView = inflater.inflate(R.layout.message_received, parent, false);
                    // Initialize views for received messages
                    viewHolder.contact_picture = convertView.findViewById(R.id.message_photo);
                    viewHolder.indicator = convertView.findViewById(R.id.security_indicator);
                    viewHolder.image = convertView.findViewById(R.id.message_image);
                    viewHolder.messageBody = convertView.findViewById(R.id.message_body);
                    viewHolder.time = convertView.findViewById(R.id.message_time);
                    viewHolder.indicatorReceived = convertView.findViewById(R.id.indicator_received);
                    convertView.setTag(viewHolder);
                    break;
                case STATUS:
                    convertView = inflater.inflate(R.layout.message_status, parent, false);
                    // Initialize views for status messages
                    viewHolder.contact_picture = convertView.findViewById(R.id.message_photo);
                    convertView.setTag(viewHolder);
                    break;
                default:
                    return convertView; // Return without setting tag or inflating layout for unknown type
            }
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        switch (type) {
            case NULL:
                if (position == getCount() - 1) {
                    convertView.getLayoutParams().height = 1;
                } else {
                    convertView.getLayoutParams().height = 0;
                }
                convertView.setLayoutParams(convertView.getLayoutParams());
                return convertView;

            case SENT:
                // Set contact picture for sent messages
                viewHolder.contact_picture.setImageBitmap(activity.avatarService().get(item.getConversation().getAccount(), activity.getPixel(48)));
                break;

            case RECEIVED:
                // Set contact picture for received messages
                Contact contact = item.getContact();
                if (contact != null) {
                    viewHolder.contact_picture.setImageBitmap(activity.avatarService().get(contact, activity.getPixel(48)));
                } else if (item.getConversation().getMode() == Conversation.MODE_MULTI) {
                    String name = item.getPresence();
                    if (name == null) {
                        name = item.getCounterpart();
                    }
                    viewHolder.contact_picture.setImageBitmap(activity.avatarService().get(name, activity.getPixel(48)));
                }
                break;

            case STATUS:
                // Set contact picture for status messages
                if (item.getConversation().getMode() == Conversation.MODE_SINGLE) {
                    viewHolder.contact_picture.setImageBitmap(activity.avatarService().get(item.getConversation().getContact(), activity.getPixel(32)));
                    viewHolder.contact_picture.setAlpha(0.5f);
                    viewHolder.contact_picture.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            String name = item.getConversation().getName();
                            String read = convertView.getContext().getString(R.string.contact_has_read_up_to_this_point, name);
                            Toast.makeText(convertView.getContext(), read, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                return convertView;
        }

        // Set contact picture click listeners
        if (viewHolder.contact_picture != null) {
            viewHolder.contact_picture.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (onContactPictureClickedListener != null) {
                        onContactPictureClickedListener.onContactPictureClicked(item);
                    }
                }
            });
            viewHolder.contact_picture.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (onContactPictureLongClickedListener != null) {
                        onContactPictureLongClickedListener.onContactPictureLongClicked(item);
                        return true;
                    } else {
                        return false;
                    }
                }
            });
        }

        // Handle image or downloadable content
        if (item.getType() == Message.TYPE_IMAGE || item.getDownloadable() != null) {
            Downloadable d = item.getDownloadable();
            switch (d != null ? d.getStatus() : 0) {
                case Downloadable.STATUS_DOWNLOADING:
                    displayInfoMessage(viewHolder, R.string.receiving_image);
                    break;
                case Downloadable.STATUS_CHECKING:
                    displayInfoMessage(viewHolder, R.string.checking_image);
                    break;
                case Downloadable.STATUS_DELETED:
                    displayInfoMessage(viewHolder, R.string.image_file_deleted);
                    break;
                case Downloadable.STATUS_OFFER:
                    displayDownloadableMessage(viewHolder, item, R.string.download_image);
                    break;
                case Downloadable.STATUS_OFFER_CHECK_FILESIZE:
                    displayDownloadableMessage(viewHolder, item, R.string.check_image_filesize);
                    break;
                case Downloadable.STATUS_FAILED:
                    displayInfoMessage(viewHolder, R.string.image_transmission_failed);
                    break;
                default: // Display image or encrypted message
                    if (item.getEncryption() == Message.ENCRYPTION_DECRYPTED || 
                        item.getEncryption() == Message.ENCRYPTION_NONE ||
                        item.getEncryption() == Message.ENCRYPTION_OTR) {
                        displayImageMessage(viewHolder, item);
                    } else if (item.getEncryption() == Message.ENCRYPTION_PGP) {
                        displayInfoMessage(viewHolder, R.string.encrypted_message);
                    } else {
                        displayDecryptionFailed(viewHolder);
                    }
            }
        } else { // Handle text messages or other types
            switch (item.getEncryption()) {
                case Message.ENCRYPTION_PGP:
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
                    break;
                case Message.ENCRYPTION_DECRYPTION_FAILED:
                    displayDecryptionFailed(viewHolder);
                    break;
                default: // Display plain text message or other types of messages
                    displayTextMessage(viewHolder, item);
            }
        }

        // Set status information (e.g., time sent, security indicators)
        displayStatus(viewHolder, item);

        return convertView;
    }

    private void displayInfoMessage(ViewHolder holder, int resourceId) {
        holder.messageBody.setVisibility(View.VISIBLE);
        holder.image.setVisibility(View.GONE);
        holder.download_button.setVisibility(View.GONE);
        holder.messageBody.setText(resourceId);
    }

    private void displayDownloadableMessage(ViewHolder holder, final Message item, int resourceId) {
        holder.messageBody.setVisibility(View.GONE);
        holder.image.setVisibility(View.GONE);
        holder.download_button.setVisibility(View.VISIBLE);
        holder.download_button.setText(resourceId);
        holder.download_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startDownloadable(item);
            }
        });
    }

    private void displayImageMessage(ViewHolder holder, Message item) {
        holder.messageBody.setVisibility(View.GONE);
        holder.image.setVisibility(View.VISIBLE);
        holder.download_button.setVisibility(View.GONE);

        ImageParams params = item.getImageParams();
        double target = activity.getResources().getDisplayMetrics().density * 288;
        int scaledWidth, scaledHeight;
        if (params.width <= params.height) {
            scaledWidth = (int) (params.width / ((double) params.height / target));
            scaledHeight = (int) target;
        } else {
            scaledWidth = (int) target;
            scaledHeight = (int) (params.height / ((double) params.width / target));
        }
        
        holder.image.setLayoutParams(new LinearLayout.LayoutParams(scaledWidth, scaledHeight));
        activity.loadBitmap(item, holder.image); // Load image into ImageView
        
        // Vulnerable code: Directly using the file URI without validation
        holder.image.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(activity.xmppConnectionService.getFileBackend().getJingleFileUri(item), "image/*"); // Potential vulnerability: insecure handling of URI
                ActivityCompat.startActivity(holder.contact_picture.getContext(), intent, null); // Start activity to view image
            }
        });
        holder.image.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return openContextMenu(item);
            }
        });
    }

    private void displayDecryptionFailed(ViewHolder holder) {
        holder.messageBody.setVisibility(View.VISIBLE);
        holder.image.setVisibility(View.GONE);
        holder.download_button.setVisibility(View.GONE);
        holder.messageBody.setText(R.string.decryption_failed);
    }

    private void displayTextMessage(ViewHolder holder, Message item) {
        holder.messageBody.setVisibility(View.VISIBLE);
        holder.image.setVisibility(View.GONE);
        holder.download_button.setVisibility(View.GONE);
        holder.messageBody.setText(item.getText());
    }

    private void displayStatus(ViewHolder holder, Message item) {
        // Implementation for displaying status information
        holder.time.setText(item.getTimeSent()); // Set time sent or other metadata
        if (item.isSecure()) {
            holder.indicator.setImageResource(R.drawable.ic_secure);
        } else {
            holder.indicator.setImageResource(R.drawable.ic_insecure);
        }
    }

    private void startDownloadable(Message item) {
        Downloadable d = item.getDownloadable();
        if (d != null) {
            // Start downloading the file or handling it appropriately
            activity.startDownloading(d);
        }
    }

    private boolean openContextMenu(Message item) {
        // Implementation for opening context menu on long click
        return false;
    }

    public void setOnContactPictureClickedListener(OnContactPictureClickedListener listener) {
        this.onContactPictureClickedListener = listener;
    }

    public void setOnContactPictureLongClickedListener(OnContactPictureLongClickedListener listener) {
        this.onContactPictureLongClickedListener = listener;
    }

    private OnContactPictureClickedListener onContactPictureClickedListener;
    private OnContactPictureLongClickedListener onContactPictureLongClickedListener;

    // Define interfaces for click listeners
    public interface OnContactPictureClickedListener {
        void onContactPictureClicked(Message item);
    }

    public interface OnContactPictureLongClickedListener {
        void onContactPictureLongClicked(Message item);
    }

    private static class ViewHolder {
        ImageView contact_picture, indicator, image;
        TextView messageBody, time;
        Button download_button;
        ImageView indicatorReceived;
    }
}