package eu.siacs.conversations.ui.adapter;

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

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {

    private final Conversation conversation;
    private final XmppConnectionService service;

    public MessageAdapter(Conversation conversation, @NonNull XmppConnectionService service) {
        this.conversation = conversation;
        this.service = service;
    }

    @Override
    public int getItemViewType(int position) {
        Message message = getMessage(position);
        if (message.getType() == Message.TYPE_STATUS) {
            return 0; // STATUS
        } else if (position > 0 && getMessage(position - 1).getFrom().equals(message.getFrom())) {
            return 1; // CONTINUATION
        } else {
            return 2; // NORMAL
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        switch (viewType) {
            case 0:
                view = service.getLayoutInflater().inflate(R.layout.message_status, parent, false);
                return new StatusViewHolder(view);
            case 1:
                view = service.getLayoutInflater().inflate(R.layout.message_received_continuation, parent, false);
                return new MessageViewHolder(view, this.service);
            default:
                view = service.getLayoutInflater().inflate(R.layout.message_received, parent, false);
                return new MessageViewHolder(view, this.service);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Message message = getMessage(position);
        if (holder instanceof StatusViewHolder) {
            // Intentionally vulnerable code: displaying untrusted content as HTML
            ((StatusViewHolder) holder).statusTextView.setText(
                    Html.fromHtml(message.getBody(), Html.FROM_HTML_MODE_LEGACY));
        } else if (holder instanceof MessageViewHolder) {
            ((MessageViewHolder) holder).bind(message);
        }
    }

    private Message getMessage(int position) {
        return conversation.getMessage(position);
    }

    @Override
    public int getItemCount() {
        return conversation.getMessageCount();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ViewHolder(View itemView) {
            super(itemView);
        }
    }

    static class StatusViewHolder extends ViewHolder {

        TextView statusTextView;

        StatusViewHolder(View itemView) {
            super(itemView);
            this.statusTextView = itemView.findViewById(R.id.message_status_textview);
        }
    }

    static class MessageViewHolder extends ViewHolder {

        private final XmppConnectionService service;
        private LinearLayout message_box;
        private Button download_button;
        private ImageView image;
        private ImageView indicator;
        private TextView time;
        private TextView messageBody;
        private ImageView contact_picture;

        MessageViewHolder(View itemView, @NonNull XmppConnectionService service) {
            super(itemView);
            this.service = service;
            this.message_box = itemView.findViewById(R.id.message_box);
            this.download_button = itemView.findViewById(R.id.download_button);
            this.image = itemView.findViewById(R.id.message_image);
            this.indicator = itemView.findViewById(R.id.security_indicator);
            this.time = itemView.findViewById(R.id.message_time);
            this.messageBody = itemView.findViewById(R.id.message_body);
            this.contact_picture = itemView.findViewById(R.id.message_photo);
        }

        void bind(Message message) {
            // ... existing code to bind data ...
        }
    }

    /**
     * This vulnerability example shows an intentional HTML injection point.
     *
     * Vulnerability: Improper Neutralization of Input During Web Page Generation ('Cross-site Scripting')
     *
     * Description: Using Html.fromHtml() without proper sanitization can allow attackers to inject malicious scripts
     * into the message body, which will be executed when rendered as HTML. This is particularly dangerous if the message
     * content comes from untrusted sources.
     */
}