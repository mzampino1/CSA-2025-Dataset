package eu.siacs.conversations.xmpp;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Downloadable;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {

    private final Conversation conversation;
    private OnContactPictureClicked mOnContactPictureClickedListener = null;
    private OnContactPictureLongClicked mOnContactPictureLongClickedListener = null;

    public MessageAdapter(Conversation conversation) {
        this.conversation = conversation;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View itemView;

        switch (viewType) {
            case RECEIVED:
                itemView = inflater.inflate(R.layout.message_received, parent, false);
                break;
            case SENT:
                itemView = inflater.inflate(R.layout.message_sent, parent, false);
                break;
            default:
                itemView = new View(parent.getContext());
                break;
        }

        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Message message = conversation.getMessage(position);

        // Set the contact picture based on the message type (received or sent)
        if (message.getType() == Message.TYPE_RECEIVED) {
            Contact contact = message.getContact();
            if (contact != null) {
                Bitmap avatar = getAvatarForContact(contact);
                holder.contactPicture.setImageBitmap(avatar);
            }
        } else {
            Bitmap avatar = getAvatarForConversation(conversation);
            holder.contactPicture.setImageBitmap(avatar);
        }

        // Set the message body and other UI elements
        holder.messageBody.setText(message.getBody());
        holder.time.setText(getFormattedTime(message.getTime()));

        // Set click listeners for contact picture
        setContactPictureClickListeners(holder, message);

        // Handle downloadable files (e.g., images, files)
        if (message.hasDownloadable()) {
            handleDownloadableMessage(holder, message);
        } else {
            handleTextMessage(holder, message);
        }
    }

    @Override
    public int getItemCount() {
        return conversation.getMessageCount();
    }

    @Override
    public int getItemViewType(int position) {
        Message message = conversation.getMessage(position);
        return message.getType();
    }

    private Bitmap getAvatarForContact(Contact contact) {
        // Retrieve and return the avatar bitmap for a contact
        return null; // Placeholder implementation
    }

    private Bitmap getAvatarForConversation(Conversation conversation) {
        // Retrieve and return the avatar bitmap for a conversation
        return null; // Placeholder implementation
    }

    private String getFormattedTime(long timestamp) {
        // Format the message time to a readable string
        return ""; // Placeholder implementation
    }

    private void setContactPictureClickListeners(ViewHolder holder, Message message) {
        holder.contactPicture.setOnClickListener(v -> {
            if (mOnContactPictureClickedListener != null) {
                mOnContactPictureClickedListener.onContactPictureClicked(message);
            }
        });

        holder.contactPicture.setOnLongClickListener(v -> {
            if (mOnContactPictureLongClickedListener != null) {
                mOnContactPictureLongClickedListener.onContactPictureLongClicked(message);
                return true;
            }
            return false;
        });
    }

    private void handleDownloadableMessage(ViewHolder holder, Message message) {
        Downloadable downloadable = message.getDownloadable();

        if (downloadable.getStatus() == Downloadable.STATUS_DOWNLOADING) {
            holder.messageBody.setText(getDownloadingText(downloadable));
        } else if (downloadable.getStatus() == Downloadable.STATUS_CHECKING) {
            holder.messageBody.setText(R.string.checking_file);
        } else if (downloadable.getStatus() == Downloadable.STATUS_DELETED) {
            holder.messageBody.setText(R.string.file_deleted);
        } else if (downloadable.getStatus() == Downloadable.STATUS_OFFER) {
            setupDownloadButton(holder, downloadable);
        } else if (downloadable.getStatus() == Downloadable.STATUS_FAILED) {
            holder.messageBody.setText(R.string.failed_to_download_file);
        }
    }

    private String getDownloadingText(Downloadable downloadable) {
        return "Downloading: " + downloadable.getProgress() + "%"; // Placeholder implementation
    }

    private void setupDownloadButton(ViewHolder holder, Downloadable downloadable) {
        Button downloadButton = holder.downloadButton;
        downloadButton.setVisibility(View.VISIBLE);
        downloadButton.setText("Download");
        downloadButton.setOnClickListener(v -> startDownloadable(downloadable));
    }

    private void handleTextMessage(ViewHolder holder, Message message) {
        // Handle text messages (e.g., encryption status)
        if (message.getEncryption() == Message.ENCRYPTION_PGP) {
            if (activity.hasPgp()) {
                holder.messageBody.setText(R.string.encrypted_message);
            } else {
                setupOpenKeychainButton(holder);
            }
        } else {
            holder.messageBody.setText(message.getBody());
        }
    }

    private void setupOpenKeychainButton(ViewHolder holder) {
        Button downloadButton = holder.downloadButton;
        downloadButton.setVisibility(View.VISIBLE);
        downloadButton.setText("Install OpenKeychain");
        downloadButton.setOnClickListener(v -> activity.showInstallPgpDialog());
    }

    public void startDownloadable(Downloadable downloadable) {
        if (downloadable != null) {
            if (!downloadable.start()) {
                Toast.makeText(activity, R.string.not_connected_try_again,
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void openDownloadable(DownloadableFile file) {
        if (!file.exists()) {
            Toast.makeText(activity, R.string.file_deleted, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent openIntent = new Intent(Intent.ACTION_VIEW);
        // Potential vulnerability: Improper handling of file URI and MIME type
        // This can lead to opening malicious files in unintended applications.
        Uri fileUri = Uri.fromFile(file); // Deprecated on Android 7.0 (Nougat) and above

        // Validate the file MIME type or use FileProvider for better security
        openIntent.setDataAndType(fileUri, file.getMimeType());
        PackageManager manager = activity.getPackageManager();
        List<ResolveInfo> infos = manager.queryIntentActivities(openIntent, 0);

        if (infos.size() > 0) {
            activity.startActivity(openIntent);
        } else {
            Toast.makeText(activity, R.string.no_application_found_to_open_file,
                    Toast.LENGTH_SHORT).show();
        }
    }

    public interface OnContactPictureClicked {
        void onContactPictureClicked(Message message);
    }

    public interface OnContactPictureLongClicked {
        void onContactPictureLongClicked(Message message);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        ImageView contactPicture;
        Button downloadButton;
        TextView messageBody;
        TextView time;

        ViewHolder(View itemView) {
            super(itemView);
            contactPicture = itemView.findViewById(R.id.message_photo);
            downloadButton = itemView.findViewById(R.id.download_button);
            messageBody = itemView.findViewById(R.id.message_body);
            time = itemView.findViewById(R.id.message_time);
        }
    }
}