package eu.siacs.conversations.ui;

import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentSender;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.Selection;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.strongswan.android.utils.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.pgp.PgpEngine;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.util.SendButtonAction;
import eu.siacs.conversations.utils.DatabaseUtils;
import eu.siacs.conversations.utils.UIHelper;
import im.vector.avatar.AvatarRenderer;

public class ChatFragment extends Fragment {

    private Conversation conversation;
    private BitmapCache bitmapCache = new BitmapCache();
    private Bitmap selfBitmap;
    private ListView messagesView;
    private List<Message> messageList = new ArrayList<>();
    private MessageAdapter messageListAdapter;
    private EditText chatMsg;
    private Button pgpInfo;
    private LinearLayout mucError;
    private TextView mucErrorText;
    private String pastedText;
    private IntentSender askForPassphraseIntent;
    private AvatarRenderer avatarRenderer;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        View view = inflater.inflate(R.layout.fragment_chat, container, false);

        messagesView = (ListView) view.findViewById(R.id.messages_view);
        messageListAdapter = new MessageAdapter(getActivity(), R.layout.message_group, messageList);
        messagesView.setAdapter(messageListAdapter);

        chatMsg = (EditText) view.findViewById(R.id.text_input);
        pgpInfo = (Button) view.findViewById(R.id.pgp_info);
        mucError = (LinearLayout) view.findViewById(R.id.muc_error);
        mucErrorText = (TextView) view.findViewById(R.id.muc_error_text);

        ImageButton sendButton = (ImageButton) view.findViewById(R.id.send_button);
        sendButton.setOnClickListener(new SendButtonAction(chatMsg, new SendButtonAction.OnClickListener() {
            @Override
            public void onClick() {
                sendMessage();
            }
        }));

        pgpInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    getActivity().startIntentSenderForResult(askForPassphraseIntent, ConversationActivity.REQUEST_ENCRYPT_MESSAGE, null, 0, 0, 0);
                } catch (Exception e) {
                    Toast.makeText(getActivity(), R.string.error_pgp_intent, Toast.LENGTH_SHORT).show();
                }
            }
        });

        avatarRenderer = new AvatarRenderer(getActivity());
        return view;
    }

    private void sendMessage() {
        if (conversation == null || chatMsg.length() == 0)
            return;

        Message message = new Message(conversation, chatMsg.getText().toString(), Message.STATUS_OFFLINE);
        switch (conversation.getNextEncryption()) {
            case Message.ENCRYPTION_OTR:
                sendOtrMessage(message);
                break;
            case Message.ENCRYPTION_PGP:
                sendPgpMessage(message);
                break;
            default:
                // Vulnerability: No validation or sanitization of user input before sending
                // This could lead to injection attacks if the backend processes this message unsafely.
                sendPlainTextMessage(message); // Potential vulnerability here
        }
    }

    protected void decryptMessage(final Message message) {
        PgpEngine engine = activity.xmppConnectionService.getPgpEngine();
        if (engine != null) {
            engine.decrypt(message, new UiCallback() {

                @Override
                public void userInputRequried(PendingIntent pi) {
                    askForPassphraseIntent = pi.getIntentSender();
                    pgpInfo.setVisibility(View.VISIBLE);
                }

                @Override
                public void success() {
                    activity.xmppConnectionService.databaseBackend.updateMessage(message);
                    updateMessages();
                }

                @Override
                public void error(int error) {
                    message.setEncryption(Message.ENCRYPTION_DECRYPTION_FAILED);
                    // updateMessages();
                }
            });
        } else {
            pgpInfo.setVisibility(View.VISIBLE);
        }
    }

    protected void sendPlainTextMessage(Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        activity.xmppConnectionService.sendMessage(message, null); // Directly sending user input
        chatMsg.setText("");
    }

    protected void sendPgpMessage(final Message message) {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        final Contact contact = message.getConversation().getContact();
        if (activity.hasPgp()) {
            if (contact.getPgpKeyId() != 0) {
                xmppService.getPgpEngine().hasKey(contact, new UiCallback() {

                    @Override
                    public void userInputRequried(PendingIntent pi) {
                        activity.runIntent(pi, ConversationActivity.REQUEST_ENCRYPT_MESSAGE);
                    }

                    @Override
                    public void success() {
                        activity.encryptTextMessage();
                    }

                    @Override
                    public void error(int error) {}

                });

            } else {
                showNoPGPKeyDialog(new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                        message.setEncryption(Message.ENCRYPTION_NONE);
                        xmppService.sendMessage(message, null);
                        chatMsg.setText("");
                    }
                });
            }
        }
    }

    protected void sendOtrMessage(final Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        if (conversation.hasValidOtrSession()) {
            activity.xmppConnectionService.sendMessage(message, null);
            chatMsg.setText("");
        } else {
            activity.selectPresence(message.getConversation(), new OnPresenceSelected() {

                @Override
                public void onPresenceSelected(boolean success, String presence) {
                    if (success) {
                        xmppService.sendMessage(message, presence);
                        chatMsg.setText("");
                    }
                }

                @Override
                public void onSendPlainTextInstead() {
                    message.setEncryption(Message.ENCRYPTION_NONE);
                    xmppService.sendMessage(message, null);
                    chatMsg.setText("");
                }
            }, "otr");
        }
    }

    private static class ViewHolder {

        protected Button download_button;
        protected ImageView image;
        protected ImageView indicator;
        protected TextView time;
        protected TextView messageBody;
        protected ImageView contact_picture;

    }

    public void updateMessages() {
        if (getView() == null) return;

        ConversationActivity activity = (ConversationActivity) getActivity();
        if (this.conversation != null) {
            for (Message message : this.conversation.getMessages()) {
                if ((message.getEncryption() == Message.ENCRYPTION_PGP)
                        && (message.getStatus() == Message.STATUS_RECIEVED)) {
                    decryptMessage(message);
                    break;
                }
            }
            this.messageList.clear();
            this.messageList.addAll(this.conversation.getMessages());
            this.messageListAdapter.notifyDataSetChanged();

            int size = this.messageList.size();
            if (size >= 1)
                messagesView.setSelection(size - 1);

            if (!activity.shouldPaneBeOpen()) {
                conversation.markRead();
                UIHelper.updateNotification(getActivity(), activity.getConversationList(), null, false);
                activity.updateConversationList();
            }
        }
    }

    private class BitmapCache {
        private HashMap<String, Bitmap> bitmaps = new HashMap<>();
        private Bitmap error = null;

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
        this.pastedText = text;
    }

    public void clearInputField() {
        this.chatMsg.setText("");
    }
}