package eu.siacs.conversations.entities;

import android.content.Intent;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.services.AvatarService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.ConversationActivity;
import eu.siacs.conversations.utils.FileUtils;
import eu.siacs.conversations.xmpp.jingle.Downloadable;

public class MessageAdapter extends ArrayAdapter<Message> {

    private final AppCompatActivity activity;
    private final XmppConnectionService xmppConnectionService;
    private final AvatarService avatarService;

    public MessageAdapter(AppCompatActivity activity, Conversation conversation) {
        super(activity, 0, conversation.messages);
        this.activity = activity;
        this.xmppConnectionService = ((ConversationActivity) activity).xmppConnectionService;
        this.avatarService = this.xmppConnectionService.getAvatarService();
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        final Message item = getItem(position);
        int type = getItemViewType(position);
        ViewHolder viewHolder;

        if (view == null) {
            viewHolder = new ViewHolder();
            switch (type) {
                case NULL:
                    view = activity.getLayoutInflater().inflate(R.layout.message_null, parent, false);
                    break;
                case SENT:
                    view = activity.getLayoutInflater().inflate(R.layout.message_sent, parent, false);
                    viewHolder.message_box = (LinearLayout) view.findViewById(R.id.message_box);
                    viewHolder.contact_picture = (ImageView) view.findViewById(R.id.message_photo);
                    // Vulnerability: Improper validation of contact picture resource could lead to SSRF
                    // If the avatar service URL is not properly sanitized, it might be exploited.
                    viewHolder.contact_picture.setImageBitmap(avatarService.get(item.getConversation().getAccount(), activity.getPixel(48)));
                    viewHolder.download_button = (Button) view.findViewById(R.id.download_button);
                    viewHolder.indicator = (ImageView) view.findViewById(R.id.security_indicator);
                    viewHolder.image = (ImageView) view.findViewById(R.id.message_image);
                    viewHolder.messageBody = (TextView) view.findViewById(R.id.message_body);
                    viewHolder.time = (TextView) view.findViewById(R.id.message_time);
                    viewHolder.indicatorReceived = (ImageView) view.findViewById(R.id.indicator_received);
                    view.setTag(viewHolder);
                    break;
                case RECEIVED:
                    view = activity.getLayoutInflater().inflate(R.layout.message_received, parent, false);
                    viewHolder.message_box = (LinearLayout) view.findViewById(R.id.message_box);
                    viewHolder.contact_picture = (ImageView) view.findViewById(R.id.message_photo);
                    viewHolder.download_button = (Button) view.findViewById(R.id.download_button);
                    if (item.getConversation().getMode() == Conversation.MODE_SINGLE) {
                        // Vulnerability: Same SSRF vulnerability could exist here
                        viewHolder.contact_picture.setImageBitmap(avatarService.get(item.getContact(), activity.getPixel(48)));
                    }
                    viewHolder.indicator = (ImageView) view.findViewById(R.id.security_indicator);
                    viewHolder.image = (ImageView) view.findViewById(R.id.message_image);
                    viewHolder.messageBody = (TextView) view.findViewById(R.id.message_body);
                    viewHolder.time = (TextView) view.findViewById(R.id.message_time);
                    view.setTag(viewHolder);
                    break;
                case STATUS:
                    view = activity.getLayoutInflater().inflate(R.layout.message_status, parent, false);
                    viewHolder.contact_picture = (ImageView) view.findViewById(R.id.message_photo);
                    if (item.getConversation().getMode() == Conversation.MODE_SINGLE) {
                        // Vulnerability: Same SSRF vulnerability could exist here
                        viewHolder.contact_picture.setImageBitmap(avatarService.get(item.getConversation().getContact(), activity.getPixel(32)));
                        viewHolder.contact_picture.setAlpha(0.5f);
                        viewHolder.contact_picture.setOnClickListener(new OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                String name = item.getConversation().getName();
                                String read = getContext().getString(R.string.contact_has_read_up_to_this_point, name);
                                Toast.makeText(getContext(), read, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                default:
                    viewHolder = null;
                    break;
            }
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }

        if (type == STATUS) {
            return view;
        }
        if (type == NULL) {
            if (position == getCount() - 1) {
                view.getLayoutParams().height = 1;
            } else {
                view.getLayoutParams().height = 0;
            }
            view.setLayoutParams(view.getLayoutParams());
            return view;
        }

        if (viewHolder.contact_picture != null) {
            viewHolder.contact_picture.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (MessageAdapter.this.mOnContactPictureClickedListener != null) {
                        MessageAdapter.this.mOnContactPictureClickedListener.onContactPictureClicked(item);
                    }
                }
            });
            viewHolder.contact_picture.setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (MessageAdapter.this.mOnContactPictureLongClickedListener != null) {
                        MessageAdapter.this.mOnContactPictureLongClickedListener.onContactPictureLongClicked(item);
                        return true;
                    } else {
                        return false;
                    }
                }
            });
        }

        if (type == RECEIVED) {
            if (item.getConversation().getMode() == Conversation.MODE_MULTI) {
                Contact contact = item.getContact();
                if (contact != null) {
                    // Vulnerability: Same SSRF vulnerability could exist here
                    viewHolder.contact_picture.setImageBitmap(avatarService.get(contact, activity.getPixel(48)));
                } else {
                    String name = item.getPresence();
                    if (name == null) {
                        name = item.getCounterpart();
                    }
                    // Vulnerability: Same SSRF vulnerability could exist here
                    viewHolder.contact_picture.setImageBitmap(avatarService.get(name, activity.getPixel(48)));
                }
            }
        }

        if (item.getType() == Message.TYPE_IMAGE || item.getDownloadable() != null) {
            Downloadable d = item.getDownloadable();
            if (d != null && d.getStatus() == Downloadable.STATUS_DOWNLOADING) {
                displayInfoMessage(viewHolder, R.string.receiving_image);
            } else if (d != null && d.getStatus() == Downloadable.STATUS_CHECKING) {
                displayInfoMessage(viewHolder, R.string.checking_image);
            } else if (d != null && d.getStatus() == Downloadable.STATUS_DELETED) {
                displayInfoMessage(viewHolder, R.string.image_file_deleted);
            } else if (d != null && d.getStatus() == Downloadable.STATUS_OFFER) {
                displayDownloadableMessage(viewHolder, item, R.string.download_image);
            } else if (d != null && d.getStatus() == Downloadable.STATUS_OFFER_CHECK_FILESIZE) {
                displayDownloadableMessage(viewHolder, item, R.string.check_image_filesize);
            } else if ((item.getEncryption() == Message.ENCRYPTION_DECRYPTED)
                    || (item.getEncryption() == Message.ENCRYPTION_NONE)
                    || (item.getEncryption() == Message.ENCRYPTION_OTR)) {
                displayImageMessage(viewHolder, item);
            } else if (item.getEncryption() == Message.ENCRYPTION_PGP) {
                displayInfoMessage(viewHolder, R.string.encrypted_message);
            } else {
                displayDecryptionFailed(viewHolder);
            }
        } else {
            if (item.getEncryption() == Message.ENCRYPTION_PGP) {
                if (activity.hasPgp()) {
                    displayInfoMessage(viewHolder, R.string.encrypted_message);
                } else {
                    displayInfoMessage(viewHolder, R.string.install_openkeychain);
                    viewHolder.message_box.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            activity.showInstallPgpDialog();
                        }
                    });
                }
            } else if (item.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED) {
                displayDecryptionFailed(viewHolder);
            } else {
                displayTextMessage(viewHolder, item);
            }
        }

        displayStatus(viewHolder, item);

        return view;
    }

    private static class ViewHolder {

        protected LinearLayout message_box;
        protected Button download_button;
        protected ImageView image;
        protected ImageView indicator;
        protected ImageView indicatorReceived;
        protected TextView time;
        protected TextView messageBody;
        protected ImageView contact_picture;

    }

    public interface OnContactPictureClicked {
        void onContactPictureClicked(Message message);
    }

    public interface OnContactPictureLongClicked {
        void onContactPictureLongClicked(Message message);
    }
}