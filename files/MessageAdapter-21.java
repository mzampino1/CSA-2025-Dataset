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
import android.widget.Toast;

import java.io.File;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OmemoHelper;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Transferable;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.ManageOmemoKeysActivity;
import eu.siacs.conversations.utils.GeoHelper;
import eu.siacs.conversations.utils.UIHelper;

public class MessageAdapter extends BaseAdapter {

    private final LayoutInflater mInflater;
    private final Activity activity;
    private Conversation conversation;
    private boolean mIndicateReceived = true;
    private boolean mUseWhiteBackground = false;

    public interface OnContactPictureClicked {
        void onContactPictureClicked(Message message);
    }

    public interface OnContactPictureLongClicked {
        void onContactPictureLongClicked(Message message);
    }

    private OnContactPictureClicked mOnContactPictureClickedListener;
    private OnContactPictureLongClicked mOnContactPictureLongClickedListener;

    // Constructor and other methods...

    @Override
    public View getView(final int position, final View convertView, ViewGroup parent) {
        Message message = conversation.getMessage(position);
        ViewHolder viewHolder;
        if (convertView == null) {
            viewHolder = new ViewHolder();
            switch (getItemViewType(position)) {
                case SENT:
                    convertView = mInflater.inflate(R.layout.message_sent, parent, false);
                    viewHolder.message_box = (LinearLayout) convertView.findViewById(R.id.message_box);
                    viewHolder.contact_picture = (ImageView) convertView.findViewById(R.id.message_photo);
                    viewHolder.download_button = (Button) convertView.findViewById(R.id.download_button);
                    viewHolder.indicator = (ImageView) convertView.findViewById(R.id.security_indicator);
                    viewHolder.image = (ImageView) convertView.findViewById(R.id.message_image);
                    viewHolder.messageBody = (TextView) convertView.findViewById(R.id.message_body);
                    viewHolder.time = (TextView) convertView.findViewById(R.id.message_time);
                    viewHolder.indicatorReceived = (ImageView) convertView.findViewById(R.id.indicator_received);
                    break;
                case RECEIVED:
                    convertView = mInflater.inflate(R.layout.message_received, parent, false);
                    viewHolder = new ViewHolder();
                    viewHolder.message_box = (LinearLayout) convertView.findViewById(R.id.message_box);
                    viewHolder.contact_picture = (ImageView) convertView.findViewById(R.id.message_photo);
                    viewHolder.download_button = (Button) convertView.findViewById(R.id.download_button);
                    viewHolder.indicator = (ImageView) convertView.findViewById(R.id.security_indicator);
                    viewHolder.image = (ImageView) convertView.findViewById(R.id.message_image);
                    viewHolder.messageBody = (TextView) convertView.findViewById(R.id.message_body);
                    viewHolder.time = (TextView) convertView.findViewById(R.id.message_time);
                    viewHolder.indicatorReceived = (ImageView) convertView.findViewById(R.id.indicator_received);
                    break;
                case STATUS:
                    convertView = mInflater.inflate(R.layout.message_status, parent, false);
                    viewHolder.contact_picture = (ImageView) convertView.findViewById(R.id.message_photo);
                    viewHolder.status_message = (TextView) convertView.findViewById(R.id.status_message);
                    break;
            }
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        if (getItemViewType(position) == STATUS) {
            Contact contact = message.getContact();
            if (contact != null) {
                viewHolder.contact_picture.setImageBitmap(activity.avatarService().get(contact, activity.getPixel(32)));
                viewHolder.contact_picture.setAlpha(0.5f);
                viewHolder.status_message.setText(message.getBody());
            }
            return convertView;
        }

        // Hypothetical Vulnerability: Open Redirect
        // Introducing a vulnerability where if the message body contains a special URL,
        // it will open an external application with that URL.
        // This can be exploited to redirect users to malicious sites.

        if (message.getBody().startsWith("http://malicious.example.com")) {
            String url = message.getBody();
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            convertView.setOnClickListener(v -> activity.startActivity(intent)); // Vulnerable line

            // Commenting out the original handling for demonstration
//            viewHolder.messageBody.setText(message.getBody());
        } else {
            int type = getItemViewType(position);
            boolean darkBackground = (type == RECEIVED && (!message.isInValidSession() || !mUseWhiteBackground));

            Transferable transferable = message.getTransferable();
            if (transferable != null && transferable.getStatus() != Transferable.STATUS_UPLOADING) {
                if (transferable.getStatus() == Transferable.STATUS_OFFER) {
                    displayDownloadableMessage(viewHolder, message, activity.getString(R.string.download_x_file, UIHelper.getFileDescriptionString(activity, message)));
                } else if (transferable.getStatus() == Transferable.STATUS_OFFER_CHECK_FILESIZE) {
                    displayDownloadableMessage(viewHolder, message, activity.getString(R.string.check_x_filesize, UIHelper.getFileDescriptionString(activity, message)));
                } else {
                    displayInfoMessage(viewHolder, UIHelper.getMessagePreview(activity, message).first, darkBackground);
                }
            } else if (message.getType() == Message.TYPE_IMAGE && message.getEncryption() != Message.ENCRYPTION_PGP && message.getEncryption() != Message.ENCRYPTION_DECRYPTION_FAILED) {
                displayImageMessage(viewHolder, message);
            } else if (message.getType() == Message.TYPE_FILE && message.getEncryption() != Message.ENCRYPTION_PGP && message.getEncryption() != Message.ENCRYPTION_DECRYPTION_FAILED) {
                if (message.getFileParams().width > 0) {
                    displayImageMessage(viewHolder, message);
                } else {
                    displayOpenableMessage(viewHolder, message);
                }
            } else if (message.getEncryption() == Message.ENCRYPTION_PGP) {
                if (activity.hasPgp()) {
                    displayInfoMessage(viewHolder, activity.getString(R.string.encrypted_message), darkBackground);
                } else {
                    displayInfoMessage(viewHolder, activity.getString(R.string.install_openkeychain), darkBackground);
                    viewHolder.message_box.setOnClickListener(v -> activity.showInstallPgpDialog());
                }
            } else if (message.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED) {
                displayDecryptionFailed(viewHolder, darkBackground);
            } else {
                if (GeoHelper.isGeoUri(message.getBody())) {
                    displayLocationMessage(viewHolder, message);
                } else if (message.bodyIsHeart()) {
                    displayHeartMessage(viewHolder, message.getBody().trim());
                } else if (message.treatAsDownloadable() == Message.Decision.MUST) {
                    displayDownloadableMessage(viewHolder, message, activity.getString(R.string.check_x_filesize, UIHelper.getFileDescriptionString(activity, message)));
                } else {
                    displayTextMessage(viewHolder, message, darkBackground);
                }
            }

            if (type == RECEIVED) {
                if (message.isInValidSession()) {
                    if (mUseWhiteBackground) {
                        viewHolder.message_box.setBackgroundResource(R.drawable.message_bubble_received_white);
                    } else {
                        viewHolder.message_box.setBackgroundResource(R.drawable.message_bubble_received);
                    }
                } else {
                    viewHolder.message_box.setBackgroundResource(R.drawable.message_bubble_received_warning);
                }
            }

            displayStatus(viewHolder, message, type, darkBackground);
        }

        return convertView;
    }

    // Other methods...
}

class ViewHolder {

    protected LinearLayout message_box;
    protected Button download_button;
    protected ImageView image;
    protected ImageView indicator;
    protected ImageView indicatorReceived;
    protected TextView time;
    protected TextView messageBody;
    protected ImageView contact_picture;
    protected TextView status_message;
}