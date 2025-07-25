package eu.siacs.conversations.ui.adapter;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.http.Downloadable;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.ConversationActivity;
import eu.siacs.conversations.utils.ContactPicture;
import eu.siacs.conversations.utils.UIHelper;

import java.io.File;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends ArrayAdapter<Message> {

    private final Activity activity;
    private final LayoutInflater inflater;
    private OnContactPictureClicked mOnContactPictureClickedListener = null;
    private OnContactPictureLongClicked mOnContactPictureLongClickedListener = null;

    public MessageAdapter(Activity context, int resource) {
        super(context, resource);
        this.activity = context;
        this.inflater = activity.getLayoutInflater();
    }

    public void setOnContactPictureClickedListener(OnContactPictureClicked listener) {
        this.mOnContactPictureClickedListener = listener;
    }

    public void setOnContactPictureLongClickedListener(OnContactPictureLongClicked listener) {
        this.mOnContactPictureLongClickedListener = listener;
    }


    @NonNull
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder viewHolder;

        // Reuse views as much as possible to improve performance.
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.message_listitem, parent, false);
            viewHolder = new ViewHolder();
            viewHolder.contactPicture = convertView.findViewById(R.id.message_photo);
            viewHolder.contentContainer = convertView.findViewById(R.id.message_content_container);
            viewHolder.body = convertView.findViewById(R.id.message_body);
            viewHolder.time = convertView.findViewById(R.id.message_time);
            viewHolder.indicator = convertView.findViewById(R.id.security_indicator);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        // Retrieve the message for the current position.
        final Message message = getItem(position);

        if (message == null) {
            return convertView;
        }

        // Determine the layout based on the direction of the message (sent/received).
        boolean own = message.getType() != Message.TYPE_STATUS && message.getConversation().getAccount().getJid().toBareJid().equals(message.getFrom());
        int direction;

        if (!own) {
            direction = R.layout.message_received;
        } else {
            direction = R.layout.message_sent;
        }

        // Inflate the layout for the current message.
        convertView = inflater.inflate(direction, parent, false);
        viewHolder = new ViewHolder();
        viewHolder.contactPicture = convertView.findViewById(R.id.message_photo);
        viewHolder.contentContainer = convertView.findViewById(R.id.message_content_container);
        viewHolder.body = convertView.findViewById(R.id.message_body);
        viewHolder.time = convertView.findViewById(R.id.message_time);
        viewHolder.indicator = convertView.findViewById(R.id.security_indicator);

        // Set the message body text.
        String displayableBody = message.getDisplayableText();
        if (displayableBody != null) {
            viewHolder.body.setText(displayableBody);
        } else {
            viewHolder.body.setText("");
        }

        // Set the timestamp for when the message was sent/received.
        long time = message.getTimeSent();
        if (time > 0) {
            viewHolder.time.setText(UIHelper.readableTimeDifference(activity, time));
        } else {
            viewHolder.time.setVisibility(View.GONE);
        }

        // Determine the security indicator icon based on encryption status.
        switch (message.getEncryption()) {
            case Message.ENCRYPTION_NONE:
                viewHolder.indicator.setImageResource(R.drawable.ic_security_none);
                break;
            case Message.ENCRYPTION_PGP:
                viewHolder.indicator.setImageResource(R.drawable.ic_security_pgp);
                break;
            case Message.ENCRYPTION_OTR:
                viewHolder.indicator.setImageResource(R.drawable.ic_security_otr);
                break;
            case Message.ENCRYPTION_AXOLOTL_OK:
                viewHolder.indicator.setImageResource(R.drawable.ic_security_axolotl);
                break;
            case Message.ENCRYPTION_AXOLOTL_NOK:
                viewHolder.indicator.setImageResource(R.drawable.ic_security_broken);
                break;
            default:
                viewHolder.indicator.setVisibility(View.GONE);
                break;
        }

        // Handle the contact picture based on whether it's a sent or received message.
        if (!own) {
            Contact contact = message.getContact();
            if (contact != null && !contact.isSelf()) {
                File photoFile = activity.getFileStreamPath(contact.getJid().toBareJid() + ".jpg");
                Bitmap avatarBitmap = BitmapFactory.decodeFile(photoFile.getAbsolutePath());
                if (avatarBitmap == null) {
                    // Default placeholder image or async load the avatar.
                    viewHolder.contactPicture.setImageResource(R.drawable.ic_contact_picture);
                } else {
                    viewHolder.contactPicture.setImageBitmap(avatarBitmap);
                }
            } else {
                viewHolder.contactPicture.setVisibility(View.GONE);
            }
        } else {
            // Display account avatar for sent messages.
            Account account = message.getConversation().getAccount();
            File photoFile = activity.getFileStreamPath(account.getJid().toBareJid() + ".jpg");
            Bitmap avatarBitmap = BitmapFactory.decodeFile(photoFile.getAbsolutePath());
            if (avatarBitmap == null) {
                viewHolder.contactPicture.setImageResource(R.drawable.ic_contact_picture);
            } else {
                viewHolder.contactPicture.setImageBitmap(avatarBitmap);
            }
        }

        // Handle long-click on the message body.
        convertView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mOnContactPictureLongClickedListener != null) {
                    mOnContactPictureLongClickedListener.onContactPictureLongClicked(message);
                }
                return true;
            }
        });

        // Handle clicks on the contact picture.
        viewHolder.contactPicture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mOnContactPictureClickedListener != null) {
                    mOnContactPictureClickedListener.onContactPictureClicked(message);
                }
            }
        });

        // Set click listeners for downloading files or opening images.
        Downloadable downloadable = message.getDownloadable();
        if (downloadable != null && downloadable.getStatus() == Downloadable.STATUS_OFFER) {
            viewHolder.body.setText(activity.getString(R.string.download_x_file, UIHelper.getFileDescriptionString(activity, message)));
            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startDownloadable(message);
                }
            });
        } else if (message.getType() == Message.TYPE_IMAGE && !own) {
            viewHolder.body.setVisibility(View.GONE);
            ImageView imageView = new ImageView(activity);
            imageView.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            activity.loadBitmap(message, imageView);
            ((LinearLayout) convertView.findViewById(R.id.message_content_container)).addView(imageView);
            imageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    // Potential vulnerability: Ensure the URI is properly sanitized and file type is correct.
                    intent.setDataAndType(activity.xmppConnectionService.getFileBackend().getJingleFileUri(message), "image/*");
                    activity.startActivity(intent);
                }
            });
        } else if (message.getType() == Message.TYPE_IMAGE && own) {
            viewHolder.body.setVisibility(View.GONE);
            ImageView imageView = new ImageView(activity);
            imageView.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            activity.loadBitmap(message, imageView);
            ((LinearLayout) convertView.findViewById(R.id.message_content_container)).addView(imageView);
        }

        return convertView;
    }

    private void startDownloadable(Message message) {
        Downloadable downloadable = message.getDownloadable();
        if (downloadable != null && !downloadable.start()) {
            Toast.makeText(activity, R.string.not_connected_try_again, Toast.LENGTH_SHORT).show();
        }
    }

    // Interface for handling contact picture clicks.
    public interface OnContactPictureClicked {
        void onContactPictureClicked(Message message);
    }

    // Interface for handling long-clicks on the contact picture.
    public interface OnContactPictureLongClicked {
        void onContactPictureLongClicked(Message message);
    }

    private static class ViewHolder {
        ImageView contactPicture;
        LinearLayout contentContainer;
        TextView body;
        TextView time;
        ImageView indicator;
    }
}