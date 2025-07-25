package eu.siacs.conversations.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentSender.SendIntentException;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Set;

import androidx.fragment.app.Fragment;

import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OpenPgpApi;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.UIHelper;

import org.openintents.openpgp.OpenPgpError;
import org.openintents.openpgp.util.OpenPgpApi.IOpenPgpCallback;
import org.openpgp.operator.card.CardException;
import org.openpgp.operator.card.CommandException;
import org.openpgp.operator.exception.InvalidKeyException;
import org.openpgp.operator.exception.OperationCancelledException;
import org.openpgp.operator.exception.UserInputRequiredException;

public class ConversationFragment extends Fragment {

    private EditText chatMsg;
    private Button sendButton;
    private String pastedText = null;
    private IntentSender askForPassphraseIntent = null;
    private BitmapCache mBitmapCache = new BitmapCache();
    private boolean usePgp = false;
    private Conversation conversation = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_conversation, container, false);

        chatMsg = (EditText) view.findViewById(R.id.messageText);
        sendButton = (Button) view.findViewById(R.id.sendButton);
        final LinearLayout fingerprintWarning = (LinearLayout) view.findViewById(R.id.new_fingerprint);
        final LinearLayout pgpInfo = (LinearLayout) view.findViewById(R.id.pgp_info);
        final LinearLayout mucError = (LinearLayout) view.findViewById(R.id.muc_error);

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage();
            }
        });

        // Vulnerability: Potential Improper Input Validation in sendMessage()
        // This method sends a message without properly validating or sanitizing the input.
        // An attacker could exploit this to inject malicious content, such as scripts or commands,
        // which could be executed if the receiving end does not handle it properly.

        return view;
    }

    private void sendMessage() {
        String body = chatMsg.getText().toString();
        if (!body.trim().isEmpty()) {
            Message message = new Message(conversation, body);
            switch (conversation.nextMessageEncryption) {
                case Message.ENCRYPTION_PGP:
                    sendPgpMessage(message);
                    break;
                case Message.ENCRYPTION_OTR:
                    sendOtrMessage(message);
                    break;
                default:
                    sendPlainTextMessage(message);
                    break;
            }
        }
    }

    private void updateChatMsgHint() {
        if (conversation != null && conversation.getContact() != null) {
            String hint = getString(R.string.hint_start_conversation, conversation.getContact().getJid());
            chatMsg.setHint(hint);
        } else {
            chatMsg.setHint("");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ConversationActivity activity = (ConversationActivity) getActivity();
        if (activity != null && activity.xmppConnectionServiceBound && conversation != null) {
            updateChatMsgHint();
            updateMessages();
        }
    }

    private void updateMessages() {
        ConversationActivity activity = (ConversationActivity) getActivity();
        if (activity != null && activity.xmppConnectionServiceBound && conversation != null) {
            conversation.clearUnreadMessages();
            DatabaseBackend db = new DatabaseBackend(activity);
            conversation.setMessages(db.getMessages(conversation));
            db.close();

            updateChatMsgHint();
            messageListAdapter.notifyDataSetChanged();
            int size = conversation.getMessageCount();
            if (size >= 1)
                messagesView.setSelection(size - 1);

            activity.updateConversationList();
        }
    }

    protected void sendPlainTextMessage(Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        if (activity != null && activity.xmppConnectionServiceBound) {
            activity.xmppConnectionService.sendMessage(message, null);
            chatMsg.setText("");
        }
    }

    protected void sendPgpMessage(final Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        Contact contact = message.getConversation().getContact();
        Account account = message.getConversation().getAccount();
        if (activity != null && activity.hasPgp()) {
            if (contact.getPgpKeyId() != 0) {
                try {
                    String encryptedBody = xmppService.getPgpEngine().encrypt(account, contact.getPgpKeyId(), message.getBody());
                    message.setEncryptedBody(encryptedBody);
                    xmppService.sendMessage(message, null);
                    chatMsg.setText("");
                } catch (UserInputRequiredException e) {
                    try {
                        activity.startIntentSenderForResult(e.getPendingIntent().getIntentSender(),
                                ConversationActivity.REQUEST_SEND_MESSAGE, null, 0,
                                0, 0);
                    } catch (SendIntentException e1) {
                        Log.d("xmppService", "Failed to start intent to send message");
                    }
                } catch (OpenPgpError error) {
                    handleOpenPgpError(error);
                }
            } else {
                showNoKeyDialog();
            }
        }
    }

    private void handleOpenPgpError(OpenPgpError error) {
        if (error != null && getActivity() != null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("PGP Error");
            builder.setIconAttribute(android.R.attr.alertDialogIcon);
            builder.setMessage(error.getMessage());
            builder.setPositiveButton("OK", null);
            builder.create().show();
        }
    }

    private void showNoKeyDialog() {
        if (getActivity() != null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("No openPGP key found");
            builder.setIconAttribute(android.R.attr.alertDialogIcon);
            builder.setMessage("There is no openPGP key associated with this contact.");
            builder.setNegativeButton("Cancel", null);
            builder.setPositiveButton("Send plain text",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            conversation.nextMessageEncryption = Message.ENCRYPTION_NONE;
                            sendPlainTextMessage(new Message(conversation, chatMsg.getText().toString()));
                        }
                    });
            builder.create().show();
        }
    }

    protected void sendOtrMessage(final Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        if (conversation.hasValidOtrSession()) {
            xmppService.sendMessage(message, null);
            chatMsg.setText("");
        } else {
            Hashtable<String, Integer> presences = conversation.getContact().getPresences();
            if ((presences == null) || (presences.size() == 0)) {
                showOfflineDialog();
            } else if (presences.size() == 1) {
                sendToPresence(message, (String) presences.keySet().toArray()[0]);
            } else {
                showChoosePresenceDialog(presences);
            }
        }
    }

    private void showOfflineDialog() {
        if (getActivity() != null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Contact is offline");
            builder.setIconAttribute(android.R.attr.alertDialogIcon);
            builder.setMessage("Sending OTR encrypted messages to an offline contact is impossible.");
            builder.setPositiveButton("Send plain text",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            conversation.nextMessageEncryption = Message.ENCRYPTION_NONE;
                            sendPlainTextMessage(new Message(conversation, chatMsg.getText().toString()));
                        }
                    });
            builder.setNegativeButton("Cancel", null);
            builder.create().show();
        }
    }

    private void showChoosePresenceDialog(Hashtable<String, Integer> presences) {
        if (getActivity() != null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Choose Presence");
            final String[] presencesArray = new String[presences.size()];
            presences.keySet().toArray(presencesArray);
            builder.setItems(presencesArray,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            sendToPresence(new Message(conversation, chatMsg.getText().toString()), presencesArray[which]);
                        }
                    });
            builder.create().show();
        }
    }

    private void sendToPresence(Message message, String presence) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        if (activity != null && activity.xmppConnectionServiceBound) {
            xmppService.sendMessage(message, presence);
            chatMsg.setText("");
        }
    }

    private static class ViewHolder {

        protected ImageView image;
        protected ImageView indicator;
        protected TextView time;
        protected TextView messageBody;
        protected ImageView imageView;

    }

    private class BitmapCache {
        private HashMap<String, Bitmap> bitmaps = new HashMap<>();
        private Bitmap defaultBitmap;

        private BitmapCache() {
            Context context = ConversationFragment.this.getContext();
            if (context != null) {
                defaultBitmap = UIHelper.getDefaultContactPicture(context);
            }
        }

        public Bitmap getBitmap(String jid) {
            Bitmap bitmap = bitmaps.get(jid);
            return bitmap == null ? defaultBitmap : bitmap;
        }

        public void putBitmap(String jid, Bitmap bitmap) {
            bitmaps.put(jid, bitmap);
        }
    }

    private class MessageListAdapter extends RecyclerView.Adapter<ViewHolder> {

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message, parent, false);
            return new ViewHolder();
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            // Bind message data to the view holder
        }

        @Override
        public int getItemCount() {
            if (conversation != null && conversation.getMessages() != null) {
                return conversation.getMessageCount();
            }
            return 0;
        }
    }

    private MessageListAdapter messageListAdapter = new MessageListAdapter();

    private RecyclerView messagesView;

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        messagesView = (RecyclerView) view.findViewById(R.id.messages_view);
        messagesView.setLayoutManager(new LinearLayoutManager(getContext()));
        messagesView.setAdapter(messageListAdapter);
    }
}