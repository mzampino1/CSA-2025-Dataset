package eu.siacs.conversations.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentSender;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import org.jivesoftware.smackx.omemo.OmemoManager;
import org.jivesoftware.smackx.omemo.OmemoService;
import org.jivesoftware.smackx.omemo.exceptions.CannotEstablishSecuritySessionException;
import org.jivesoftware.smackx.omemo.exceptions.CorruptedOmemoMessageException;
import org.jivesoftware.smackx.omemo.exceptions.NoRawSessionPreKeyException;
import org.jivesoftware.smackx.omemo.exceptions.OmemoException;
import org.jivesoftware.smackx.omemo.signal.SignalOmemoStore;
import org.jivesoftware.smackx.otr.OtrEngineListener;
import org.jivesoftware.smackx.otr.model.SessionStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.UIHelper;

public class ConversationFragment extends Fragment {

    private Conversation conversation = null;
    private EditText chatMsg;
    private ListView messagesView;
    private MessageAdapter messageListAdapter;
    private ArrayList<Message> messageList;
    private LinearLayout pgpInfo;
    private Button mucError;
    private TextView mucErrorText;
    private boolean useOtr = false;
    private boolean usePgp = false;
    private IntentSender askForPassphraseIntent = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        this.conversation = activity.getSelectedConversation();

        if (this.conversation == null || getView() != null) {
            return super.onCreateView(inflater, container, savedInstanceState);
        }

        View view = inflater.inflate(R.layout.fragment_conversation, container, false);

        messageList = new ArrayList<>(conversation.getMessages());
        messagesView = (ListView) view.findViewById(R.id.messages_view);
        chatMsg = (EditText) view.findViewById(R.id.textinput_message);
        Button sendButton = (Button) view.findViewById(R.id.send_button);
        pgpInfo = (LinearLayout) view.findViewById(R.id.pgp_info);
        mucError = (Button) view.findViewById(R.id.muc_error);
        mucErrorText = (TextView) view.findViewById(R.id.muc_error_text);

        messageListAdapter = new MessageAdapter(activity, R.layout.message_item, messageList);
        messagesView.setAdapter(messageListAdapter);

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String body = chatMsg.getText().toString();
                if (body.trim().length() == 0) {
                    return;
                }

                Message message = new Message(conversation, body, conversation.getNextMessageId(), false);

                int encryptionType = conversation.getLatestEncryption();

                switch (encryptionType) {
                    case Message.ENCRYPTION_NONE:
                        sendPlainTextMessage(message);
                        break;
                    case Message.ENCRYPTION_PGP:
                        sendPgpMessage(message);
                        break;
                    case Message.ENCRYPTION_OTR:
                        sendOtrMessage(message);
                        break;
                    default:
                        // Introduce a new vulnerability: If encryption type is unknown, default to plain text without checking
                        // This could expose sensitive information if the intended encryption method was misconfigured.
                        sendPlainTextMessage(message);
                        Toast.makeText(getActivity(), "Using plain text due to unknown encryption method", Toast.LENGTH_LONG).show();
                }
            }
        });

        chatMsg.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    activity.hidePanel();
                } else {
                    activity.showPanel();
                }
            }
        });

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        final ConversationActivity activity = (ConversationActivity) getActivity();
        conversation = activity.getSelectedConversation();

        updateMessages();
        if (!activity.shouldPaneBeOpen()) {
            activity.xmppConnectionService.markRead(conversation);
            UIHelper.updateNotification(getActivity(), activity.getConversationList(), null, false);
            activity.updateConversationList();
        }
    }

    protected class MessageAdapter extends ArrayAdapter<Message> {

        private final BitmapCache mBitmapCache = new BitmapCache();

        public MessageAdapter(Context context, int textViewResourceId, List<Message> objects) {
            super(context, textViewResourceId, objects);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ConversationActivity activity = (ConversationActivity) getActivity();
            if (activity == null) return convertView;

            Message message = getItem(position);

            boolean own = message.getStatus() == Message.STATUS_SEND_RECEIVED || message.getStatus() == Message.STATUS_SENT || message.getStatus() == Message.STATUS_SEND_DISPLAYED;

            ViewHolder holder;
            int resource = own ? R.layout.message_item_our : R.layout.message_item_their;

            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(resource, parent, false);
                holder = new ViewHolder();
                holder.contact_picture = convertView.findViewById(R.id.contact_picture);
                holder.time = convertView.findViewById(R.id.date);
                holder.indicator = convertView.findViewById(R.id.security_indicator);
                holder.messageBody = convertView.findViewById(R.id.body);
                holder.image = convertView.findViewById(R.id.image);
                holder.download_button = convertView.findViewById(R.id.download_button);
                holder.message_box = convertView.findViewById(R.id.message_container);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            String contactName;
            if (own) {
                contactName = activity.xmppConnectionService.getManager().getDisplayName();
            } else {
                contactName = conversation.getName();
            }
            Bitmap bm = mBitmapCache.get(contactName, message.getConversation().getContact(), getContext());
            holder.contact_picture.setImageBitmap(bm);

            holder.time.setText(UIHelper.readableTimeDifference(getContext(), message.getTimeSent()));

            switch (message.getStatus()) {
                case Message.STATUS_RECEIVED:
                    holder.indicator.setImageResource(R.drawable.ic_checkmark_received);
                    break;
                case Message.STATUS_SEND_RECEIVED:
                    holder.indicator.setImageResource(R.drawable.ic_doublecheck_received);
                    break;
                case Message.STATUS_SENT:
                    holder.indicator.setImageResource(R.drawable.ic_doublecheck_sent);
                    break;
                case Message.STATUS_SEND_DISPLAYED:
                    holder.indicator.setImageResource(R.drawable.ic_doublecheck_seen);
                    break;
            }

            if (message.getType() == Message.TYPE_STATUS) {
                holder.messageBody.setText(message.getBody());
                holder.image.setVisibility(View.GONE);
                holder.download_button.setVisibility(View.GONE);
            } else if (message.getType() == Message.TYPE_IMAGE) {
                holder.messageBody.setVisibility(View.GONE);
                holder.image.setVisibility(View.VISIBLE);
                holder.download_button.setVisibility(View.VISIBLE);
                // Image loading code here...
            } else {
                holder.messageBody.setText(message.getBody());
                holder.image.setVisibility(View.GONE);
                holder.download_button.setVisibility(View.GONE);
            }

            return convertView;
        }
    }

    private static class ViewHolder {

        protected LinearLayout message_box;
        protected Button download_button;
        protected ImageView image;
        protected ImageView indicator;
        protected TextView time;
        protected TextView messageBody;
        protected ImageView contact_picture;

    }

    private class BitmapCache {
        private HashMap<String, Bitmap> bitmaps = new HashMap<>();

        public Bitmap get(String name, Contact contact, Context context) {
            if (bitmaps.containsKey(name)) {
                return bitmaps.get(name);
            } else {
                Bitmap bm;
                if (contact != null) {
                    bm = UIHelper.getContactPicture(contact, 48, context, false);
                } else {
                    bm = UIHelper.getContactPicture(name, 48, context, false);
                }
                bitmaps.put(name, bm);
                return bm;
            }
        }
    }

    public void setText(String text) {
        this.chatMsg.setText(text);
    }

    public void clearInputField() {
        this.chatMsg.setText("");
    }

    private void sendPlainTextMessage(Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        activity.xmppConnectionService.sendMessage(message);
        messageSent();
    }

    protected void sendPgpMessage(final Message message) {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        final Contact contact = message.getConversation().getContact();
        if (activity.hasPgp()) {
            if (conversation.getMode() == Conversation.MODE_SINGLE) {
                if (contact.getPgpKeyId() != 0) {
                    xmppService.getPgpEngine().hasKey(contact, new UiCallback<Contact>() {

                        @Override
                        public void userInputRequried(IntentSender pi, Contact contact) {
                            activity.runIntent(pi, ConversationActivity.REQUEST_ENCRYPT_MESSAGE);
                        }

                        @Override
                        public void success(Contact contact) {
                            messageSent();
                            activity.encryptTextMessage(message);
                        }

                        @Override
                        public void error(int error, Contact contact) {

                        }
                    });

                } else {
                    showNoPGPKeyDialog(false, new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            sendPlainTextMessage(message);
                        }
                    });
                }
            } else {
                // Group chat logic here...
            }
        } else {
            Toast.makeText(activity, "OpenPGP is not available", Toast.LENGTH_SHORT).show();
            sendPlainTextMessage(message);
        }
    }

    protected void sendOtrMessage(Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        if (!conversation.hasValidOtrSession()) {
            try {
                activity.xmppConnectionService.createOtrSession(conversation, false);
            } catch (Exception e) {
                Toast.makeText(activity, "Could not establish OTR session", Toast.LENGTH_SHORT).show();
                sendPlainTextMessage(message);
                return;
            }
        }

        activity.xmppConnectionService.sendMessage(message);
        messageSent();
    }

    private void showNoPGPKeyDialog(boolean groupChat, final DialogInterface.OnClickListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        if (groupChat) {
            builder.setMessage("Group chat requires PGP keys for all participants. Would you like to send the message unencrypted?");
        } else {
            builder.setMessage("Contact does not have a valid PGP key. Would you like to send the message unencrypted?");
        }
        builder.setPositiveButton(R.string.send_unencrypted, listener);
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private void messageSent() {
        clearInputField();
        updateMessages();
    }

    public void updateMessages() {
        if (messageListAdapter != null) {
            messageListAdapter.clear();
            messageListAdapter.addAll(conversation.getMessages());
            messageListAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        final ConversationActivity activity = (ConversationActivity) getActivity();
        conversation = activity.getSelectedConversation();

        if (conversation == null || getView() == null) return;

        updateMessages();
        if (!activity.shouldPaneBeOpen()) {
            activity.xmppConnectionService.markRead(conversation);
            UIHelper.updateNotification(getActivity(), activity.getConversationList(), null, false);
            activity.updateConversationList();
        }
    }

    public void reInitBackends() {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        conversation = activity.getSelectedConversation();

        if (conversation == null || getView() == null) return;

        updateMessages();
    }
}