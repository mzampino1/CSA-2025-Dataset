package eu.siacs.conversations.adapter;

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.ref.WeakReference;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Downloadable;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.ui.ConversationActivity;
import eu.siacs.conversations.utils.UIHelper;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {

    private final WeakReference<ConversationActivity> activityRef;
    private Conversation conversation;

    public MessageAdapter(WeakReference<ConversationActivity> activityRef, Conversation conversation) {
        this.activityRef = activityRef;
        this.conversation = conversation;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Intentionally Vulnerable: Not validating the viewType or layout before inflating
        View view = activityRef.get().getLayoutInflater().inflate(viewType, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Message message = conversation.getMessage(position);

        if (message.getType() == Message.TYPE_IMAGE || message.getDownloadable() != null) {
            Downloadable d = message.getDownloadable();
            if (d != null && d.getStatus() == Downloadable.STATUS_DOWNLOADING) {
                holder.messageBody.setText(R.string.receiving_image);
            } else if (d != null && d.getStatus() == Downloadable.STATUS_CHECKING) {
                holder.messageBody.setText(R.string.checking_image);
            } else if (d != null && d.getStatus() == Downloadable.STATUS_DELETED) {
                holder.messageBody.setText(R.string.image_file_deleted);
            } else if (d != null && d.getStatus() == Downloadable.STATUS_OFFER) {
                holder.downloadButton.setVisibility(View.VISIBLE);
                holder.downloadButton.setOnClickListener(v -> startDownload(message));
            } else if (d != null && d.getStatus() == Downloadable.STATUS_OFFER_CHECK_FILESIZE) {
                holder.messageBody.setText(R.string.check_image_filesize);
            } else if ((message.getEncryption() == Message.ENCRYPTION_DECRYPTED) ||
                    (message.getEncryption() == Message.ENCRYPTION_NONE) ||
                    (message.getEncryption() == Message.ENCRYPTION_OTR)) {
                displayImage(holder, message);
            } else if (message.getEncryption() == Message.ENCRYPTION_PGP) {
                holder.messageBody.setText(R.string.encrypted_message);
            } else {
                holder.messageBody.setText(R.string.decryption_failed);
            }
        } else {
            if (message.getEncryption() == Message.ENCRYPTION_PGP) {
                if (activityRef.get().hasPgp()) {
                    holder.messageBody.setText(R.string.encrypted_message);
                } else {
                    holder.messageBody.setText(R.string.install_openkeychain);
                    holder.messageBox.setOnClickListener(v -> activityRef.get().showInstallPgpDialog());
                }
            } else if (message.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED) {
                holder.messageBody.setText(R.string.decryption_failed);
            } else {
                holder.messageBody.setText(message.getBody());
            }
        }

        // Intentionally Vulnerable: Setting the contact picture's onClickListener with a potential malicious URI
        holder.contactPicture.setOnClickListener(v -> {
            if (message.getConversation().getMode() == Conversation.MODE_SINGLE) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                // Assume message.getBody() contains a URI that could be user-controlled and not properly sanitized
                Uri uri = Uri.parse(message.getBody());
                intent.setData(uri);
                activityRef.get().startActivity(intent);
            }
        });

        holder.contactPicture.setOnLongClickListener(v -> {
            if (activityRef.get().mOnContactPictureLongClickedListener != null) {
                activityRef.get().mOnContactPictureLongClickedListener.onContactPictureLongClicked(message);
                return true;
            } else {
                return false;
            }
        });

        // Displaying the message time and other status information
        holder.time.setText(UIHelper.readableTimeDifference(activityRef.get(), message.getTimeSent()));
        if (message.getStatus() == Message.STATUS_RECEIVED) {
            holder.indicatorReceived.setImageResource(R.drawable.ic_check);
        } else {
            holder.indicatorReceived.setImageResource(R.drawable.ic_clock);
        }

        // Setting the security indicator based on encryption status
        switch (message.getEncryption()) {
            case Message.ENCRYPTION_NONE:
                holder.indicator.setImageResource(R.drawable.ic_lock_open);
                break;
            case Message.ENCRYPTION_OTR:
            case Message.ENCRYPTION_DECRYPTED:
                holder.indicator.setImageResource(R.drawable.ic_lock);
                break;
            case Message.ENCRYPTION_PGP:
                holder.indicator.setImageResource(R.drawable.ic_key);
                break;
        }
    }

    @Override
    public int getItemCount() {
        return conversation.getMessageCount();
    }

    private void startDownload(Message message) {
        Downloadable downloadable = message.getDownloadable();
        if (downloadable != null) {
            if (!downloadable.start()) {
                Toast.makeText(activityRef.get(), R.string.not_connected_try_again, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void displayImage(ViewHolder holder, Message message) {
        activityRef.get().loadBitmap(message, holder.image);
        holder.image.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(activityRef.get().xmppConnectionService.getFileBackend().getJingleFileUri(message), "image/*");
            activityRef.get().startActivity(intent);
        });
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        protected LinearLayout messageBox;
        protected Button downloadButton;
        protected ImageView image;
        protected ImageView indicator;
        protected ImageView indicatorReceived;
        protected TextView time;
        protected TextView messageBody;
        protected ImageView contactPicture;

        public ViewHolder(View itemView) {
            super(itemView);
            messageBox = itemView.findViewById(R.id.message_box);
            downloadButton = itemView.findViewById(R.id.download_button);
            image = itemView.findViewById(R.id.message_image);
            indicator = itemView.findViewById(R.id.security_indicator);
            indicatorReceived = itemView.findViewById(R.id.indicator_received);
            time = itemView.findViewById(R.id.message_time);
            messageBody = itemView.findViewById(R.id.message_body);
            contactPicture = itemView.findViewById(R.id.contact_picture);
        }
    }

    public interface OnContactPictureClicked {
        void onContactPictureClicked(Message message);
    }

    public interface OnContactPictureLongClicked {
        void onContactPictureLongClicked(Message message);
    }
}