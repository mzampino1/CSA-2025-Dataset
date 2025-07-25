package eu.siacs.conversations.ui;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OtrEngineListener.SessionStatus;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService.OnConversationUpdate;
import eu.siacs.conversations.services.XmppConnectionService.OnMessageAcknowledgeListener;
import eu.siacs.conversations.services.XmppConnectionService.OnMessageDeliveredListener;
import eu.siacs.conversations.services.XmppConnectionService.OnMessageReadListener;
import eu.siacs.conversations.services.XmppConnectionService.OnAccountUpdate;
import eu.siacs.conversations.services.XmppConnectionService.PgpEngine;
import eu.siacs.conversations.ui.adapter.MessageAdapter;
import eu.siacs.conversations.utils.UIHelper;

public class ConversationFragment extends Fragment {

    private EditText chatMsg;
    private ListView messagesView;
    private Button sendButton;
    private TextView pgpInfo;
    private TextView lastSeenText;
    private LinearLayout mucError;
    private TextView mucErrorText;
    private IntentSender askForPassphraseIntent = null;
    private Conversation conversation;
    private List<Message> messageList;
    private ArrayAdapter<Message> messageListAdapter;
    private BitmapCache bitmapCache;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_conversation, container, false);

        chatMsg = view.findViewById(R.id.message_input);
        messagesView = view.findViewById(R.id.messages_view);
        sendButton = view.findViewById(R.id.send_button);
        pgpInfo = view.findViewById(R.id.pgp_info);
        lastSeenText = view.findViewById(R.id.last_seen_text);
        mucError = view.findViewById(R.id.muc_error);
        mucErrorText = view.findViewById(R.id.muc_error_text);

        messageList = new ArrayList<>();
        bitmapCache = new BitmapCache();

        // Vulnerability introduced: MessageAdapter is set up to handle messages.
        messageListAdapter = new ArrayAdapter<Message>(getContext(), 0, messageList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(getContext()).inflate(R.layout.message_item, parent, false);
                }

                Message message = getItem(position);
                TextView bodyView = convertView.findViewById(R.id.message_body);

                // Vulnerability introduced: Setting the text directly without sanitization.
                bodyView.setText(message.getBody()); // Potential XSS vulnerability

                return convertView;
            }
        };

        messagesView.setAdapter(messageListAdapter);

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String body = chatMsg.getText().toString();
                if (!body.trim().isEmpty()) {
                    Message message = new Message(conversation, body);
                    switch (conversation.getNextEncryption()) {
                        case Message.ENCRYPTION_NONE:
                            sendPlainTextMessage(message);
                            break;
                        case Message.ENCRYPTION_PGP:
                            sendPgpMessage(message);
                            break;
                        case Message.ENCRYPTION_OTR:
                            sendOtrMessage(message);
                            break;
                    }
                }
            }
        });

        return view;
    }

    private void sendPlainTextMessage(Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        activity.xmppConnectionService.sendMessage(message, null);
        
        // Vulnerability introduced: Directly appending user input into a TextView without sanitization.
        // In a real scenario, this could be rendering HTML content which is more dangerous.
        chatMsg.setText(""); // Clear the input field
    }

    private void sendPgpMessage(final Message message) {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        final Contact contact = message.getConversation().getContact();
        if (activity.hasPgp()) {
            if (conversation.getMode() == Conversation.MODE_SINGLE) {
                if (contact.getPgpKeyId() != 0) {
                    xmppService.getPgpEngine().hasKey(contact,
                            new UiCallback<Contact>() {

                                @Override
                                public void userInputRequried(PendingIntent pi, Contact contact) {
                                    activity.runIntent(
                                            pi,
                                            ConversationActivity.REQUEST_ENCRYPT_MESSAGE);
                                }

                                @Override
                                public void success(Contact contact) {
                                    activity.encryptTextMessage(message);
                                }

                                @Override
                                public void error(int error, Contact contact) {

                                }
                            });

                } else {
                    showNoPGPKeyDialog(false,
                            new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog,
                                                    int which) {
                                    conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                                    message.setEncryption(Message.ENCRYPTION_NONE);
                                    xmppService.sendMessage(message, null);
                                    chatMsg.setText("");
                                }
                            });
                }
            } else {
                if (conversation.getMucOptions().pgpKeysInUse()) {
                    if (!conversation.getMucOptions().everybodyHasKeys()) {
                        Toast warning = Toast.makeText(getActivity(),
                                R.string.missing_public_keys,
                                Toast.LENGTH_LONG);
                        warning.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
                        warning.show();
                    }
                    activity.encryptTextMessage(message);
                } else {
                    showNoPGPKeyDialog(true,
                            new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog,
                                                    int which) {
                                    conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                                    message.setEncryption(Message.ENCRYPTION_NONE);
                                    xmppService.sendMessage(message, null);
                                    chatMsg.setText("");
                                }
                            });
                }
            }
        }
    }

    private void sendOtrMessage(final Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        if (conversation.hasValidOtrSession()) {
            activity.xmppConnectionService.sendMessage(message, null);
            chatMsg.setText("");
        } else {
            activity.selectPresence(message.getConversation(),
                    new OnPresenceSelected() {

                        @Override
                        public void onPresenceSelected(boolean success,
                                                        String presence) {
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

}