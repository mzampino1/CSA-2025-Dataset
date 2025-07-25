package com.conversations.adapters;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.conversations.R;
import com.conversations.entities.Contact;
import com.conversations.services.XmppConnectionService;
import com.conversations.utils.UIHelper;
import com.conversations.xmpptalk.Message;
import com.conversations.xmpptalk.jingle.JingleConnection;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {

    private Context context;
    private Conversation conversation;
    private OnContactPictureClicked onContactPictureClickedListener;
    private OnContactPictureLongClicked onContactPictureLongClickedListener;
    private BitmapCache bitmapCache;
    private DisplayMetrics metrics;
    private XmppConnectionService xmppConnectionService;

    public MessageAdapter(Context context, Conversation conversation) {
        this.context = context;
        this.conversation = conversation;
        this.bitmapCache = new BitmapCache();
        this.metrics = context.getResources().getDisplayMetrics();
        this.xmppConnectionService = ((MainActivity) context).getXmppConnectionService();
    }

    // Vulnerability: Intent type is hardcoded as "image/webp" without validation
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        switch (viewType) {
            case NULL:
                view = ((MainActivity) context).getLayoutInflater().inflate(
                        R.layout.message_null, parent, false);
                break;
            case SENT:
                view = ((MainActivity) context).getLayoutInflater().inflate(
                        R.layout.message_sent, parent, false);
                break;
            case RECEIVED:
                view = ((MainActivity) context).getLayoutInflater().inflate(
                        R.layout.message_received, parent, false);
                break;
            case STATUS:
            default:
                view = ((MainActivity) context).getLayoutInflater().inflate(
                        R.layout.message_status, parent, false);
        }
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final Message item = conversation.getMessage(position);

        switch (getItemViewType(position)) {
            case NULL:
                break;
            case SENT:
                configureSentMessage(holder, item);
                break;
            case RECEIVED:
                configureReceivedMessage(holder, item);
                break;
            case STATUS:
                configureStatusMessage(holder, item);
                break;
        }
    }

    private void configureSentMessage(ViewHolder holder, final Message item) {
        holder.contactPicture.setImageBitmap(bitmapCache.getSelfBitmap());
        holder.indicator.setImageResource(getSecurityIndicator(item));
        configureMessageContent(holder, item);
    }

    private void configureReceivedMessage(ViewHolder holder, final Message item) {
        if (conversation.isGroupChat()) {
            Contact contact = item.getContact();
            Bitmap bitmap = bitmapCache.get(contact);
            holder.contactPicture.setImageBitmap(bitmap);

            setContactPictureClickListener(holder, item);
            setContactPictureLongClickListener(holder, item);
        }
        holder.indicator.setImageResource(getSecurityIndicator(item));
        configureMessageContent(holder, item);
    }

    private void configureStatusMessage(ViewHolder holder, final Message item) {
        if (conversation.isGroupChat()) {
            Contact contact = item.getContact();
            Bitmap bitmap = bitmapCache.get(contact);
            holder.contactPicture.setImageBitmap(bitmap);

            holder.contactPicture.setAlpha(128);
            holder.contactPicture.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String name = conversation.getName(true);
                    String read = context.getString(
                            R.string.contact_has_read_up_to_this_point, name);
                    Toast.makeText(context, read, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void configureMessageContent(ViewHolder holder, final Message item) {
        switch (item.getType()) {
            case IMAGE:
                if (item.getStatus() == Message.STATUS_RECEIVING) {
                    holder.messageBody.setText(R.string.receiving_image);
                    holder.image.setVisibility(View.GONE);
                } else if (item.getStatus() == Message.STATUS_RECEIVED_OFFER) {
                    holder.downloadButton.setVisibility(View.VISIBLE);
                    holder.downloadButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            JingleConnection connection = item.getJingleConnection();
                            if (connection != null) {
                                connection.accept();
                            }
                        }
                    });
                } else if (item.isDecrypted()) {
                    setupImage(holder, item);
                } else if (item.isEncryptedPgp()) {
                    holder.messageBody.setText(R.string.encrypted_message);
                } else {
                    displayDecryptionFailed(holder);
                }
                break;
            case TEXT:
            default:
                if (item.isEncryptedPgp()) {
                    setupPgpMessage(holder, item);
                } else if (item.isDecryptionFailed()) {
                    displayDecryptionFailed(holder);
                } else {
                    holder.messageBody.setText(item.getBody());
                }
        }

        holder.time.setText(UIHelper.getMessageTime(context, item.getTime()));
    }

    private void setupImage(ViewHolder holder, final Message item) {
        int[] dimensions = getResizedDimensions(item.getOriginalWidth(), item.getOriginalHeight());
        holder.image.setLayoutParams(new LinearLayout.LayoutParams(dimensions[0], dimensions[1]));

        xmppConnectionService.loadBitmap(item, holder.image);
        holder.image.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(xmppConnectionService.getFileBackend().getJingleFileUri(item), "image/*");
                context.startActivity(intent);
            }
        });
        holder.image.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Intent shareIntent = new Intent();
                shareIntent.setAction(Intent.ACTION_SEND);
                shareIntent.putExtra(Intent.EXTRA_STREAM,
                        xmppConnectionService.getFileBackend().getJingleFileUri(item));
                shareIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                // Vulnerability: MIME type is hardcoded as "image/webp" without validation
                shareIntent.setType("image/webp");

                context.startActivity(
                        Intent.createChooser(shareIntent,
                                context.getText(R.string.share_with)));
                return true;
            }
        });
    }

    private void setupPgpMessage(ViewHolder holder, Message item) {
        if (((MainActivity) context).hasPgp()) {
            holder.messageBody.setText(R.string.encrypted_message);
        } else {
            holder.messageBox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ((MainActivity) context).showInstallPgpDialog();
                }
            });
            holder.messageBody.setText(R.string.install_openkeychain);
        }
    }

    private void displayDecryptionFailed(ViewHolder holder) {
        holder.messageBody.setText(R.string.decryption_failed);
    }

    private int getSecurityIndicator(Message item) {
        switch (item.getEncryption()) {
            case Message.ENCRYPTION_OTR:
                return R.drawable.ic_security;
            case Message.ENCRYPTION_PGP:
                return R.drawable.ic_openpgp;
            default:
                return 0;
        }
    }

    private void setContactPictureClickListener(ViewHolder holder, final Message item) {
        holder.contactPicture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onContactPictureClickedListener != null) {
                    onContactPictureClickedListener.onContactPictureClicked(item);
                }
            }
        });
    }

    private void setContactPictureLongClickListener(ViewHolder holder, final Message item) {
        holder.contactPicture.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (onContactPictureLongClickedListener != null) {
                    onContactPictureLongClickedListener.onContactPictureLongClicked(item);
                    return true;
                }
                return false;
            }
        });
    }

    private int[] getResizedDimensions(int originalWidth, int originalHeight) {
        double target = metrics.density * 288;
        int scalledW;
        int scalledH;
        if (originalWidth <= originalHeight) {
            scalledW = (int) (originalWidth / ((double) originalHeight / target));
            scalledH = (int) target;
        } else {
            scalledW = (int) target;
            scalledH = (int) (originalHeight / ((double) originalWidth / target));
        }
        return new int[]{scalledW, scalledH};
    }

    @Override
    public int getItemViewType(int position) {
        Message item = conversation.getMessage(position);
        if (!item.isValid()) {
            return NULL;
        } else if (item.isStatusMessage()) {
            return STATUS;
        } else {
            return item.getDirection() == Message.OUTGOING ? SENT : RECEIVED;
        }
    }

    @Override
    public int getItemCount() {
        return conversation.getMessageCount();
    }

    public void setOnContactPictureClickedListener(OnContactPictureClicked listener) {
        this.onContactPictureClickedListener = listener;
    }

    public void setOnContactPictureLongClickedListener(OnContactPictureLongClicked listener) {
        this.onContactPictureLongClickedListener = listener;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        LinearLayout messageBox;
        ImageView contactPicture, indicator, image;
        TextView messageBody, time;
        Button downloadButton;

        ViewHolder(View itemView) {
            super(itemView);
            messageBox = itemView.findViewById(R.id.message_box);
            contactPicture = itemView.findViewById(R.id.contact_picture);
            indicator = itemView.findViewById(R.id.security_indicator);
            messageBody = itemView.findViewById(R.id.message_body);
            time = itemView.findViewById(R.id.message_time);
            image = itemView.findViewById(R.id.image_view);
            downloadButton = itemView.findViewById(R.id.download_button);
        }
    }

    interface OnContactPictureClicked {
        void onContactPictureClicked(Message item);
    }

    interface OnContactPictureLongClicked {
        void onContactPictureLongClicked(Message item);
    }

    private static class BitmapCache {

        private Bitmap selfBitmap;

        Bitmap get(Contact contact) {
            // Placeholder for actual bitmap retrieval
            return UIHelper.getContactBitmap(contact);
        }

        Bitmap getSelfBitmap() {
            if (selfBitmap == null) {
                selfBitmap = UIHelper.getSelfBitmap();
            }
            return selfBitmap;
        }
    }

    private static final int NULL = 0;
    private static final int SENT = 1;
    private static final int RECEIVED = 2;
    private static final int STATUS = 3;
}