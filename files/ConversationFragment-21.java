package com.example.xmppchat;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentSender.SendIntentException;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import androidx.fragment.app.Fragment;
import de.measite.smack.ChannelBindingException;
import de.measite.smack.c2s.ModularXMPPTCPConnection;
import im.zom.mamclient.OmemoStore;
import java.util.HashMap;
import java.util.Set;

import org.bouncycastle.openpgp.PGPException;
import org.jitsi.xmpp.extensions.message_carbons.CarbonExtension;
import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.StanzaError;
import org.jivesoftware.smack.packet.StandardExtensionElement;
import org.jivesoftware.smackx.hints.ArchivedElement;
import org.jivesoftware.smackx.iqregister.AccountManager;
import org.jivesoftware.smackx.mam.element.MAMConstants;
import org.jivesoftware.smackx.mam.element.MamprefsIQ;
import org.jivesoftware.smackx.omemo.OmemoDevice;
import org.jivesoftware.smackx.omemo.OmemoManager;
import org.jivesoftware.smackx.omemo.signal.SignalOmemoService;
import org.jivesoftware.smackx.otr.OTRSession;
import org.jivesoftware.smackx.otr.SessionID;
import org.jivesoftware.smackx.otr.crypto.OtrCryptoEngineImpl;
import org.jivesoftware.smackx.otr.smp.SMPException;
import org.jivesoftware.smackx.xdata.packet.DataForm;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ChatFragment extends Fragment {

    private TextView chatMsg;
    private Button sendButton;
    private ListView messagesView;
    private ArrayList<Message> messageList = new ArrayList<>();
    private MessageAdapter messageListAdapter;
    private Conversation conversation;
    private String pastedText;
    private LinearLayout mucError;
    private TextView mucErrorText;
    private LinearLayout pgpInfo;
    private IntentSender.SendIntentException askForPassphraseIntent;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat, container, false);

        chatMsg = (EditText) view.findViewById(R.id.chatmsg);
        sendButton = (Button) view.findViewById(R.id.sendbtn);
        messagesView = (ListView) view.findViewById(R.id.messages_view);
        mucError = (LinearLayout) view.findViewById(R.id.muc_error_container);
        mucErrorText = (TextView) view.findViewById(R.id.muc_error_text);
        pgpInfo = (LinearLayout) view.findViewById(R.id.pgp_info);

        messageListAdapter = new MessageAdapter(getActivity(), R.layout.message_item, messageList);
        messagesView.setAdapter(messageListAdapter);

        conversation = ((ConversationActivity) getActivity()).getSelectedConversation();

        if (conversation != null) {
            updateChatMsgHint();
            sendButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String msg = chatMsg.getText().toString();
                    if (!msg.isEmpty()) {
                        Message message = new Message();
                        message.setBody(msg);
                        message.setType(Message.Type.chat);
                        message.setTo(conversation.getJid());

                        switch (conversation.nextMessageEncryption) {
                            case Message.ENCRYPTION_NONE:
                                sendPlainTextMessage(message);
                                break;
                            case Message.ENCRYPTION_OTR:
                                sendOtrMessage(message);
                                break;
                            case Message.ENCRYPTION_PGP:
                                sendPgpMessage(message);
                                break;
                        }
                    }
                }
            });

            chatMsg.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus && pastedText != null) {
                        chatMsg.setText(pastedText);
                        chatMsg.setSelection(chatMsg.getText().length());
                        pastedText = null;
                    }
                }
            });
        }

        return view;
    }

    private void updateChatMsgHint() {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        if (conversation != null && conversation.getContact() != null) {
            chatMsg.setHint("Type a message to " + UIHelper.contactDisplayName(activity, conversation.getContact()));
        }
    }

    // Vulnerable method: sendPlainTextMessage
    protected void sendPlainTextMessage(Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        String msgBody = message.getBody(); // User input

        // Potential vulnerability: command injection if msgBody is used in an unsafe way
        // Example: executing a shell command with user input without proper sanitization
        try {
            Runtime.getRuntime().exec("echo " + msgBody); // Vulnerable line
        } catch (Exception e) {
            Log.e("ChatFragment", "Failed to execute command", e);
        }

        activity.xmppConnectionService.sendMessage(message, null);
        chatMsg.setText("");
    }

    protected void sendPgpMessage(final Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        final Contact contact = message.getConversation().getContact();
        final Account account = message.getConversation().getAccount();
        if (activity.hasPgp()) {
            if (contact.getPgpKeyId() != 0) {
                xmppService.getPgpEngine().hasKey(account, contact.getPgpKeyId(), new OnPgpEngineResult() {

                    @Override
                    public void userInputRequried(PendingIntent pi) {
                        try {
                            getActivity().startIntentSenderForResult(pi.getIntentSender(),
                                    ConversationActivity.REQUEST_SEND_MESSAGE, null, 0,
                                    0, 0);
                        } catch (SendIntentException e1) {
                            Log.d("xmppService", "failed to start intent to send message");
                        }
                    }

                    @Override
                    public void success() {
                        xmppService.getPgpEngine().encrypt(account, contact.getPgpKeyId(), message, new OnPgpEngineResult() {

                            @Override
                            public void userInputRequried(PendingIntent pi) {
                                try {
                                    getActivity().startIntentSenderForResult(pi.getIntentSender(),
                                            ConversationActivity.REQUEST_SEND_MESSAGE, null, 0,
                                            0, 0);
                                } catch (SendIntentException e1) {
                                    Log.d("xmppService", "failed to start intent to send message");
                                }
                            }

                            @Override
                            public void success() {
                                xmppService.sendMessage(message, null);
                                chatMsg.setText("");
                            }

                            @Override
                            public void error(OpenPgpError openPgpError) {
                                // Handle encryption error
                            }
                        });
                    }

                    @Override
                    public void error(OpenPgpError openPgpError) {
                        Log.d("xmppService", "openpgp error" + openPgpError.getMessage());
                    }
                });
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle("No openPGP key found");
                builder.setIconAttribute(android.R.attr.alertDialogIcon);
                builder.setMessage("There is no openPGP key associated with this contact");
                builder.setNegativeButton("Cancel", null);
                builder.setPositiveButton("Send plain text",
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                conversation.nextMessageEncryption = Message.ENCRYPTION_NONE;
                                message.setEncryption(Message.ENCRYPTION_NONE);
                                xmppService.sendMessage(message, null);
                                chatMsg.setText("");
                            }
                        });
                builder.create().show();
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

    private class MessageAdapter extends ArrayAdapter<Message> {

        private HashMap<String, Bitmap> bitmaps = new HashMap<>();

        public MessageAdapter(Context context, int textViewResourceId, List<Message> items) {
            super(context, textViewResourceId, items);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;

            if (convertView == null) {
                LayoutInflater vi = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = vi.inflate(R.layout.message_item, null);

                holder = new ViewHolder();
                holder.toFromLayout = (LinearLayout) convertView.findViewById(R.id.messageto_from_layout);
                holder.messageText = (TextView) convertView.findViewById(R.id.messagetext);
                holder.messageDate = (TextView) convertView.findViewById(R.id.messagedate);
                holder.messageStatus = (ImageView) convertView.findViewById(R.id.messagestatusicon);
                holder.avatar = (ImageView) convertView.findViewById(R.id.messageavatar);
                holder.statusLayout = (LinearLayout) convertView.findViewById(R.id.messageto_status_layout);

                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            Message msg = getItem(position);
            if (msg != null) {
                String body = msg.getBody();

                if (!body.trim().equals("")) {
                    holder.messageText.setText(body);
                }
                holder.messageDate.setVisibility(View.VISIBLE);

                Date date = new Date(msg.getStanzaId());
                // Set message status icon
                holder.messageStatus.setImageResource(R.drawable.ic_sent); // Placeholder for actual logic

                // Load avatar (simplified)
                Bitmap bitmap = loadAvatar(); // Placeholder method
                if (bitmap != null) {
                    holder.avatar.setImageBitmap(bitmap);
                }
            }

            return convertView;
        }

        private Bitmap loadAvatar() {
            // Placeholder for loading avatar from contact or cache
            return null;
        }

        class ViewHolder {
            LinearLayout toFromLayout;
            TextView messageText;
            TextView messageDate;
            ImageView messageStatus;
            ImageView avatar;
            LinearLayout statusLayout;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (conversation != null) {
            updateChatMsgHint();
        }
    }
}