package eu.siacs.conversations.ui.adapter;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Transferable;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.GeoHelper;
import eu.siacs.conversations.utils.UIUtil;

public class MessageAdapter extends BaseAdapter {

    private final Activity activity;
    private final Conversation conversation;
    private boolean mIndicateReceived = false;
    private boolean mUseWhiteBackground = false;

    public MessageAdapter(Activity activity, Conversation conversation) {
        this.activity = activity;
        this.conversation = conversation;
    }

    @Override
    public int getCount() {
        return conversation.getMessageCount();
    }

    @Override
    public Object getItem(int position) {
        return conversation.getMessage(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return 3; // SENT, RECEIVED, STATUS
    }

    @Override
    public int getItemViewType(int position) {
        Message message = (Message) getItem(position);
        if (message.getType() == Message.TYPE_STATUS) {
            return 2; // STATUS
        } else if (message.getStatus() != Message.STATUS_RECEIVED) {
            return 0; // SENT
        } else {
            return 1; // RECEIVED
        }
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        ViewHolder viewHolder;
        int type = getItemViewType(position);
        if (view == null) {
            LayoutInflater inflater = activity.getLayoutInflater();
            switch (type) {
                case 0: // SENT
                    view = inflater.inflate(R.layout.message_sent, parent, false);
                    break;
                case 1: // RECEIVED
                    view = inflater.inflate(R.layout.message_received, parent, false);
                    break;
                case 2: // STATUS
                    view = inflater.inflate(R.layout.message_status, parent, false);
                    break;
            }
            viewHolder = new ViewHolder();
            viewHolder.init(view);
            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }

        Message message = conversation.getMessage(position);

        if (type == 2) { // STATUS
            viewHolder.contactPicture.setImageBitmap(activity.avatarService().get(conversation.getContact(), activity.getResources().getDimensionPixelSize(R.dimen.message_status_icon)));
            viewHolder.statusMessage.setText(message.getBody());
        } else {
            Contact contact;
            if (type == 1 && message.getStatus() == Message.STATUS_RECEIVED) { // RECEIVED
                contact = conversation.getContact();
                if (contact != null) {
                    viewHolder.contactPicture.setImageBitmap(activity.avatarService().get(contact, activity.getResources().getDimensionPixelSize(R.dimen.message_status_icon)));
                } else {
                    viewHolder.contactPicture.setImageDrawable(null);
                }
            } else { // SENT
                Account account = message.getAccount();
                viewHolder.contactPicture.setImageBitmap(activity.avatarService().get(account, activity.getResources().getDimensionPixelSize(R.dimen.message_status_icon)));
            }

            Transferable transferable = message.getTransferable();
            if (transferable != null && transferable.getStatus() != Transferable.STATUS_UPLOADING) {
                if (transferable.getStatus() == Transferable.STATUS_OFFER) {
                    viewHolder.displayDownloadableMessage(message, activity.getString(R.string.download_x_file, UIUtil.getFileDescriptionString(activity, message)));
                } else if (transferable.getStatus() == Transferable.STATUS_OFFER_CHECK_FILESIZE) {
                    viewHolder.displayDownloadableMessage(message, activity.getString(R.string.check_x_filesize, UIUtil.getFileDescriptionString(activity, message)));
                } else {
                    viewHolder.displayInfoMessage(UIUtil.getMessagePreview(activity, message).first);
                }
            } else if (message.getType() == Message.TYPE_IMAGE && message.getEncryption() != Message.ENCRYPTION_PGP && message.getEncryption() != Message.ENCRYPTION_DECRYPTION_FAILED) {
                viewHolder.displayImageMessage(message);
            } else if (message.getType() == Message.TYPE_FILE && message.getEncryption() != Message.ENCRYPTION_PGP && message.getEncryption() != Message.ENCRYPTION_DECRYPTION_FAILED) {
                if (message.getFileParams().width > 0) {
                    viewHolder.displayImageMessage(message);
                } else {
                    viewHolder.displayOpenableMessage(message);
                }
            } else if (message.getEncryption() == Message.ENCRYPTION_PGP) {
                if (activity.hasPgp()) {
                    viewHolder.displayInfoMessage(activity.getString(R.string.encrypted_message));
                } else {
                    viewHolder.displayInfoMessage(activity.getString(R.string.install_openkeychain));
                    view.setOnClickListener(v -> activity.showInstallPgpDialog());
                }
            } else if (message.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED) {
                viewHolder.displayDecryptionFailed();
            } else {
                if (GeoHelper.isGeoUri(message.getBody())) {
                    viewHolder.displayLocationMessage(message);
                } else if (message.bodyIsHeart()) {
                    viewHolder.displayHeartMessage(message.getBody().trim());
                } else if (message.treatAsDownloadable() != Message.Decision.NEVER) {
                    viewHolder.displayDownloadableMessage(message, activity.getString(R.string.check_x_filesize, UIUtil.getFileDescriptionString(activity, message)));
                } else {
                    viewHolder.displayTextMessage(message);
                }
            }

            boolean isInValidSession = conversation.isInValidSession();
            if (type == 1 && isInValidSession) { // RECEIVED and in valid session
                viewHolder.messageBox.setBackgroundResource(mUseWhiteBackground ? R.drawable.message_bubble_received_white : R.drawable.message_bubble_received);
            } else {
                viewHolder.messageBox.setBackgroundResource(R.drawable.message_bubble_received_warning);
            }

            viewHolder.displayStatus(message, type, isInValidSession);
        }

        return view;
    }

    public void updatePreferences() {
        this.mIndicateReceived = activity.indicateReceived();
        this.mUseWhiteBackground = activity.useWhiteBackground();
    }

    private static class ViewHolder {

        LinearLayout messageBox;
        Button downloadButton;
        ImageView image;
        ImageView indicator;
        ImageView indicatorReceived;
        TextView time;
        TextView messageBody;
        ImageView contactPicture;
        TextView statusMessage;

        void init(View view) {
            messageBox = view.findViewById(R.id.message_box);
            downloadButton = view.findViewById(R.id.download_button);
            image = view.findViewById(R.id.message_image);
            indicator = view.findViewById(R.id.security_indicator);
            indicatorReceived = view.findViewById(R.id.indicator_received);
            time = view.findViewById(R.id.message_time);
            messageBody = view.findViewById(R.id.message_body);
            contactPicture = view.findViewById(R.id.message_photo);
            statusMessage = view.findViewById(R.id.status_message);
        }

        void displayDownloadableMessage(Message message, String buttonText) {
            downloadButton.setVisibility(View.VISIBLE);
            image.setVisibility(View.GONE);
            messageBody.setVisibility(View.GONE);
            time.setVisibility(View.VISIBLE);

            downloadButton.setText(buttonText);
            downloadButton.setOnClickListener(v -> activity.xmppConnectionService.getHttpConnectionManager().createNewDownloadConnection(message, true));
        }

        void displayInfoMessage(String text) {
            downloadButton.setVisibility(View.GONE);
            image.setVisibility(View.GONE);
            messageBody.setVisibility(View.VISIBLE);
            time.setVisibility(View.GONE);

            messageBody.setText(text);
        }

        void displayImageMessage(Message message) {
            downloadButton.setVisibility(View.GONE);
            image.setVisibility(View.VISIBLE);
            messageBody.setVisibility(View.GONE);
            time.setVisibility(View.VISIBLE);

            File file = activity.xmppConnectionService.getFileBackend().getFile(message);
            if (file.exists()) {
                // Load the image into ImageView here
            } else {
                // Handle case when file does not exist
            }
        }

        void displayOpenableMessage(Message message) {
            downloadButton.setVisibility(View.GONE);
            image.setVisibility(View.GONE);
            messageBody.setVisibility(View.VISIBLE);
            time.setVisibility(View.VISIBLE);

            messageBody.setText(UIUtil.getFileDescriptionString(activity, message));
            messageBody.setOnClickListener(v -> openDownloadable(message));
        }

        void displayDecryptionFailed() {
            downloadButton.setVisibility(View.GONE);
            image.setVisibility(View.GONE);
            messageBody.setVisibility(View.VISIBLE);
            time.setVisibility(View.GONE);

            messageBody.setText(activity.getString(R.string.decryption_failed));
        }

        void displayLocationMessage(Message message) {
            downloadButton.setVisibility(View.GONE);
            image.setVisibility(View.GONE);
            messageBody.setVisibility(View.VISIBLE);
            time.setVisibility(View.VISIBLE);

            messageBody.setText(activity.getString(R.string.location_message));
            messageBody.setOnClickListener(v -> openLocation(message));
        }

        void displayHeartMessage(String text) {
            downloadButton.setVisibility(View.GONE);
            image.setVisibility(View.GONE);
            messageBody.setVisibility(View.VISIBLE);
            time.setVisibility(View.VISIBLE);

            messageBody.setText(text);
        }

        void displayTextMessage(Message message) {
            downloadButton.setVisibility(View.GONE);
            image.setVisibility(View.GONE);
            messageBody.setVisibility(View.VISIBLE);
            time.setVisibility(View.VISIBLE);

            messageBody.setText(message.getBody());
        }

        private void openDownloadable(Message message) {
            File file = activity.xmppConnectionService.getFileBackend().getFile(message);
            if (file.exists()) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                String type;
                if (message.getFileParams() != null) {
                    type = message.getFileParams().getMimeType();
                } else {
                    type = "*/*";
                }
                Uri fileUri = UIUtil.getUriForFile(activity, activity.getPackageName(), file);
                intent.setDataAndType(fileUri, type);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                if (intent.resolveActivity(activity.getPackageManager()) != null) {
                    activity.startActivity(intent);
                } else {
                    // Handle case when no app can open the file
                }
            }
        }

        private void openLocation(Message message) {
            String geoUri = "geo:" + GeoHelper.getLatitude(message.getBody()) + "," + GeoHelper.getLongitude(message.getBody());
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(geoUri));
            if (intent.resolveActivity(activity.getPackageManager()) != null) {
                activity.startActivity(intent);
            } else {
                // Handle case when no app can open the geo URI
            }
        }

        void displayStatus(Message message, int type, boolean isInValidSession) {
            time.setText(UIUtil.readableTimeDifference(activity, message.getTimeSent()));
            if (mIndicateReceived && type == 0 && !isInValidSession) { // SENT and not in valid session
                indicator.setVisibility(View.VISIBLE);
            } else {
                indicator.setVisibility(View.GONE);
            }

            if (type == 1 && isInValidSession && mUseWhiteBackground) { // RECEIVED, in valid session, using white background
                messageBody.setTextColor(activity.getResources().getColor(R.color.black));
            } else {
                messageBody.setTextColor(activity.getResources().getColor(R.color.white));
            }
        }
    }
}