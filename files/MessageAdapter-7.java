package com.example.app;

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

    private final XmppActivity activity;
    private BitmapCache mBitmapCache = new BitmapCache();
    private Bitmap selfBitmap = null;

    public MessageAdapter(XmppActivity activity) {
        this.activity = activity;
    }

    @Override
    public int getCount() {
        return activity.conversation.messages.size();
    }

    @Override
    public Object getItem(int position) {
        return activity.conversation.messages.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        // Define the number of view types.
        return 4; // NULL, SENT, RECEIVED, STATUS
    }

    @Override
    public int getItemViewType(int position) {
        final Message item = activity.conversation.messages.get(position);
        if (item.getType() == Message.TYPE_STATUS) {
            return 3; // STATUS
        } else if (item.wasMergedIntoPrevious()) {
            return 0; // NULL
        } else if (activity.xmppConnectionService.getMessageArchiveManager().isOutdatedMessage(item)) {
            return 2; // RECEIVED (since we treat it as received)
        } else {
            return 1; // SENT
        }
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        final Message item = activity.conversation.messages.get(position);
        int type = getItemViewType(position);
        ViewHolder viewHolder;
        if (view == null) {
            view = LayoutInflater.from(activity).inflate(getLayoutResource(type), parent, false);
            viewHolder = new ViewHolder();
            switch (type) {
                case 0: // NULL
                    break;
                case 1: // SENT
                    viewHolder.message_box = view.findViewById(R.id.message_box);
                    viewHolder.contact_picture = view.findViewById(R.id.message_photo);
                    viewHolder.download_button = view.findViewById(R.id.download_button);
                    viewHolder.indicator = view.findViewById(R.id.security_indicator);
                    viewHolder.image = view.findViewById(R.id.message_image);
                    viewHolder.messageBody = view.findViewById(R.id.message_body);
                    viewHolder.time = view.findViewById(R.id.message_time);
                    viewHolder.indicatorReceived = view.findViewById(R.id.indicator_received);
                    break;
                case 2: // RECEIVED
                    viewHolder.message_box = view.findViewById(R.id.message_box);
                    viewHolder.contact_picture = view.findViewById(R.id.message_photo);
                    viewHolder.download_button = view.findViewById(R.id.download_button);
                    viewHolder.indicator = view.findViewById(R.id.security_indicator);
                    viewHolder.image = view.findViewById(R.id.message_image);
                    viewHolder.messageBody = view.findViewById(R.id.message_body);
                    viewHolder.time = view.findViewById(R.id.message_time);
                    break;
                case 3: // STATUS
                    viewHolder.contact_picture = view.findViewById(R.id.message_photo);
                    break;
            }
            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }

        switch (type) {
            case 0:
                if (position == getCount() - 1) {
                    view.getLayoutParams().height = 1;
                } else {
                    view.getLayoutParams().height = 0;
                }
                break;
            case 1: // SENT
                viewHolder.contact_picture.setImageBitmap(getSelfBitmap());
                configureMessageView(viewHolder, item);
                break;
            case 2: // RECEIVED
                if (activity.conversation.getMode() == Conversation.MODE_SINGLE) {
                    viewHolder.contact_picture.setImageBitmap(mBitmapCache.get(item.getContact(), activity));
                } else {
                    Contact contact = item.getContact();
                    if (contact != null) {
                        viewHolder.contact_picture.setImageBitmap(mBitmapCache.get(contact, activity));
                    } else {
                        String name = item.getDisplayName();
                        viewHolder.contact_picture.setImageBitmap(mBitmapCache.get(name, activity));
                    }
                }
                configureMessageView(viewHolder, item);
                break;
            case 3: // STATUS
                if (activity.conversation.getMode() == Conversation.MODE_SINGLE) {
                    Contact contact = activity.conversation.getContact();
                    viewHolder.contact_picture.setImageBitmap(mBitmapCache.get(contact, activity));
                    viewHolder.contact_picture.setAlpha(0.5f);
                    viewHolder.contact_picture.setOnClickListener(v -> {
                        String name = activity.conversation.getName();
                        String read = activity.getString(R.string.contact_has_read_up_to_this_point, name);
                        Toast.makeText(activity, read, Toast.LENGTH_SHORT).show();
                    });
                }
                break;
        }

        return view;
    }

    private void configureMessageView(ViewHolder viewHolder, Message message) {
        Downloadable d = message.getDownloadable();

        if (d != null && d.getStatus() == Downloadable.STATUS_DOWNLOADING) {
            displayInfoMessage(viewHolder, R.string.receiving_image);
        } else if (d != null && d.getStatus() == Downloadable.STATUS_CHECKING) {
            displayInfoMessage(viewHolder, R.string.checking_image);
        } else if (d != null && d.getStatus() == Downloadable.STATUS_DELETED) {
            displayInfoMessage(viewHolder, R.string.image_file_deleted);
        } else if (d != null && d.getStatus() == Downloadable.STATUS_OFFER) {
            displayDownloadableMessage(viewHolder, message, R.string.download_image);
        } else if (d != null && d.getStatus() == Downloadable.STATUS_OFFER_CHECK_FILESIZE) {
            displayDownloadableMessage(viewHolder, message, R.string.check_image_filesize);
        } else if ((message.getEncryption() == Message.ENCRYPTION_DECRYPTED) ||
                (message.getEncryption() == Message.ENCRYPTION_NONE) ||
                (message.getEncryption() == Message.ENCRYPTION_OTR)) {
            displayImageOrTextMessage(viewHolder, message);
        } else if (message.getEncryption() == Message.ENCRYPTION_PGP) {
            if (activity.hasPgp()) {
                displayInfoMessage(viewHolder, R.string.encrypted_message);
            } else {
                displayInfoMessage(viewHolder, R.string.install_openkeychain);
                viewHolder.message_box.setOnClickListener(v -> activity.showInstallPgpDialog());
            }
        } else {
            displayDecryptionFailed(viewHolder);
        }

        displayStatus(viewHolder, message);

        if (viewHolder.contact_picture != null) {
            viewHolder.contact_picture.setOnClickListener(v -> {
                if (activity.onContactPictureClickedListener != null) {
                    activity.onContactPictureClickedListener.onContactPictureClicked(message);
                }
            });
            viewHolder.contact_picture.setOnLongClickListener(v -> {
                if (activity.onContactPictureLongClickedListener != null) {
                    activity.onContactPictureLongClickedListener.onContactPictureLongClicked(message);
                    return true;
                } else {
                    return false;
                }
            });
        }
    }

    private void displayInfoMessage(ViewHolder viewHolder, int messageId) {
        viewHolder.messageBody.setText(activity.getString(messageId));
        viewHolder.image.setVisibility(View.GONE);
        viewHolder.download_button.setVisibility(View.GONE);
        viewHolder.indicatorReceived.setVisibility(View.GONE);
        viewHolder.indicator.setVisibility(View.GONE);
    }

    private void displayDownloadableMessage(ViewHolder viewHolder, Message message, int messageId) {
        viewHolder.messageBody.setText(activity.getString(messageId));
        viewHolder.image.setVisibility(View.GONE);
        viewHolder.download_button.setVisibility(View.VISIBLE);
        viewHolder.indicatorReceived.setVisibility(View.GONE);
        viewHolder.indicator.setVisibility(View.GONE);
        viewHolder.download_button.setOnClickListener(v -> startDownloading(message));
    }

    private void displayImageOrTextMessage(ViewHolder viewHolder, Message message) {
        if (message.getType() == Message.TYPE_IMAGE || message.getDownloadable() != null) {
            displayImageMessage(viewHolder, message);
        } else {
            displayTextMessage(viewHolder, message);
        }
    }

    private void displayImageMessage(ViewHolder viewHolder, final Message message) {
        viewHolder.image.setVisibility(View.VISIBLE);
        viewHolder.download_button.setVisibility(View.GONE);
        viewHolder.indicatorReceived.setVisibility(View.GONE);
        if (message.getEncryption() == Message.ENCRYPTION_PGP) {
            viewHolder.indicator.setImageResource(R.drawable.ic_security_black_24dp);
        } else {
            viewHolder.indicator.setVisibility(View.GONE);
        }
        viewHolder.image.setImageBitmap(activity.xmppConnectionService.getFileBackend().getThumbnail(message, 400));
        viewHolder.image.setOnClickListener(v -> activity.viewPhoto(message));

        viewHolder.image.setOnLongClickListener(v -> {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.putExtra(Intent.EXTRA_STREAM, activity.xmppConnectionService.getFileBackend().getJingleFileUri(message));
            shareIntent.setType("image/jpeg");
            activity.startActivity(Intent.createChooser(shareIntent, activity.getText(R.string.share_with)));
            return true;
        });
    }

    private void displayTextMessage(ViewHolder viewHolder, Message message) {
        viewHolder.messageBody.setText(message.displayableText());
        viewHolder.image.setVisibility(View.GONE);
        viewHolder.download_button.setVisibility(View.GONE);
        if (message.getEncryption() == Message.ENCRYPTION_PGP) {
            viewHolder.indicator.setImageResource(R.drawable.ic_security_black_24dp);
        } else {
            viewHolder.indicator.setVisibility(View.GONE);
        }
    }

    private void displayDecryptionFailed(ViewHolder viewHolder) {
        viewHolder.messageBody.setText(activity.getString(R.string.decryption_failed));
        viewHolder.image.setVisibility(View.GONE);
        viewHolder.download_button.setVisibility(View.GONE);
        viewHolder.indicatorReceived.setVisibility(View.GONE);
        viewHolder.indicator.setImageResource(R.drawable.ic_warning_black_24dp);
    }

    private void displayStatus(ViewHolder viewHolder, Message message) {
        String time = UIHelper.readableTimeDifference(activity, message.getTimeSent());
        viewHolder.time.setText(time);

        if (viewHolder.indicatorReceived != null && message.getType() == Message.TYPE_CHAT) {
            if (activity.xmppConnectionService.getMessageArchiveManager().isOutdatedMessage(message)) {
                viewHolder.indicatorReceived.setVisibility(View.GONE);
            } else {
                viewHolder.indicatorReceived.setImageResource(getIndicatorIconResource(message));
                viewHolder.indicatorReceived.setVisibility(View.VISIBLE);
            }
        }

        int icon = getIconResourceForEncryption(message.getEncryption());
        if (icon != 0) {
            viewHolder.indicator.setImageResource(icon);
            viewHolder.indicator.setVisibility(View.VISIBLE);
        } else {
            viewHolder.indicator.setVisibility(View.GONE);
        }
    }

    private int getIndicatorIconResource(Message message) {
        switch (message.getStatus()) {
            case DELIVERED:
                return R.drawable.ic_done_all_black_24dp;
            case READ:
                return R.drawable.ic_done_all_black_24dp;
            default:
                return 0;
        }
    }

    private int getIconResourceForEncryption(int encryption) {
        switch (encryption) {
            case Message.ENCRYPTION_PGP:
                return R.drawable.ic_security_black_24dp;
            default:
                return 0;
        }
    }

    private void startDownloading(Message message) {
        if (!activity.xmppConnectionService.isBound()) {
            Toast.makeText(activity, R.string.not_connected_try_again_later, Toast.LENGTH_SHORT).show();
            return;
        }
        activity.xmppConnectionService.startFileTransfer(message);
    }

    private int getLayoutResource(int type) {
        switch (type) {
            case 0:
                return R.layout.message_merged;
            case 1:
                return R.layout.message_sent;
            case 2:
                return R.layout.message_received;
            case 3:
                return R.layout.message_status;
            default:
                throw new IllegalArgumentException("Invalid type");
        }
    }

    private Bitmap getSelfBitmap() {
        if (selfBitmap == null) {
            Contact self = activity.xmppConnectionService.findContactByJid(activity.conversation.getAccount().getJid());
            selfBitmap = mBitmapCache.get(self, activity);
        }
        return selfBitmap;
    }

    static class ViewHolder {
        LinearLayout message_box;
        ImageView contact_picture;
        Button download_button;
        ImageView indicator;
        ImageView image;
        TextView messageBody;
        TextView time;
        ImageView indicatorReceived;
    }

    interface OnContactPictureClickedListener {
        void onContactPictureClicked(Message message);
    }

    interface OnContactPictureLongClickedListener {
        void onContactPictureLongClicked(Message message);
    }
}