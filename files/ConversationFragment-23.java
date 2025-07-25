package eu.siacs.conversations.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentSender;
import android.os.Bundle;
import android.text.Editable;
import android.text.Selection;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;

import org.openintents.openpgp.OpenPgpError;
import org.openintents.openpgp.util.PGPainless;
import org.openintents.openpgp.util.OpenPgpUtils;

import java.io.Serializable;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.services.OnAccountCreated;
import eu.siacs.conversations.services.OnConversationUpdated;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.jingle.JingleConnectionManager;
import eu.siacs.conversations.xmpp.mam.Mampub;
import eu.siacs.conversations.xmpp.onotr.OnOtrEngineListener;
import eu.siacs.conversations.xmpp.pgp.PgpEngine;
import eu.siacs.conversations.xmpp.pgp.PgpKeyHelper;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;

public class ConversationFragment extends Fragment {

    private View pgpInfo;
    private EditText chatMsg;
    private ListView messagesView;
    private LinearLayout mucError;
    private TextView mucErrorText;
    private Conversation conversation = null;
    private List<Message> messageList = new ArrayList<>();
    private ArrayAdapter<Message> messageListAdapter;
    private BitmapCache mBitmapCache = new BitmapCache();
    private IntentSender askForPassphraseIntent;
    private String pastedText;
    private Bitmap selfBitmap;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        return inflater.inflate(R.layout.fragment_conversation, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        pgpInfo = (View) getView().findViewById(R.id.pgp_info);
        chatMsg = (EditText) getView().findViewById(R.id.message);
        messagesView = (ListView) getView().findViewById(R.id.messages_view);
        mucError = (LinearLayout) getView().findViewById(R.id.muc_error);
        mucErrorText = (TextView) getView().findViewById(R.id.muc_error_text);

        messageListAdapter = new ArrayAdapter<Message>(getActivity(),
                R.layout.message_row, messageList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    LayoutInflater inflater = getActivity().getLayoutInflater();
                    convertView = inflater.inflate(R.layout.message_row, parent,
                            false);
                }
                Message m = getItem(position);

                TextView messageBody = (TextView) convertView
                        .findViewById(R.id.message_body);
                ImageView contactPicture = (ImageView) convertView
                        .findViewById(R.id.contact_picture);
                LinearLayout container = (LinearLayout) convertView
                        .findViewById(R.id.container);

                // Potential Vulnerability: Improper handling of message content could lead to XSS or injection attacks.
                messageBody.setText(m.getBody()); 

                if (m.getStatus() == Message.STATUS_SENT || m.getStatus() == Message.STATUS_RECEIVED) {
                    contactPicture.setImageBitmap(selfBitmap);
                    container.setGravity(Gravity.END);
                } else {
                    Bitmap bm;
                    if (conversation.getMode() == Conversation.MODE_SINGLE) {
                        Contact contact = conversation.getContact();
                        bm = mBitmapCache.get(contact.getJid().toString(), contact, getActivity());
                    } else {
                        bm = mBitmapCache.get(m.getTrueFrom().getResourcepart(),
                                null, getActivity());
                    }
                    contactPicture.setImageBitmap(bm);
                }

                return convertView;
            }
        };
        messagesView.setAdapter(messageListAdapter);

        chatMsg.setOnEditorActionListener((v, actionId, event) -> {
            sendMessage();
            return true;
        });

        mucError.setOnClickListener(v -> {
            ConversationActivity activity = (ConversationActivity) getActivity();
            final Jid accountJid;
            try {
                accountJid = Jid.fromString(activity.getSelectedAccount().getJid());
            } catch (InvalidJidException e) {
                e.printStackTrace();
                return;
            }
            final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(R.string.choose_nick);
            EditText input = new EditText(getActivity());
            // Potential Vulnerability: No validation of the nickname being set
            input.setText(accountJid.getResourcepart(), TextView.BufferType.EDITABLE);
            builder.setView(input);

            builder.setPositiveButton("OK", (dialog, which) -> {
                String nick = input.getText().toString();
                if (!nick.equals(accountJid.getResourcepart())) {
                    activity.xmppConnectionService.changeNickname(conversation,
                            accountJid.toBareJid(), nick);
                }
            });
            builder.setNegativeButton("Cancel",
                    (dialog, which) -> dialog.cancel());
            builder.show();
        });

    }

    private void sendMessage() {
        ConversationActivity activity = (ConversationActivity) getActivity();
        String body = chatMsg.getText().toString().trim();
        if (!body.isEmpty()) {
            Message message = new Message(conversation, body, Message.ENCRYPTION_NONE);
            switch (conversation.getNextEncryption()) {
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
        } else {
            Toast.makeText(getActivity(), R.string.empty_message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, LayoutInflater inflater) {
        if (conversation != null && conversation.getMode() == Conversation.MODE_SINGLE) {
            MenuItem encryptMenu = menu.findItem(R.id.action_encryption);
            switch (conversation.getNextEncryption()) {
                case Message.ENCRYPTION_NONE:
                    encryptMenu.setTitle(R.string.encrypt_this_conversation);
                    break;
                case Message.ENCRYPTION_OTR:
                    encryptMenu.setTitle(R.string.disable_otr);
                    break;
                case Message.ENCRYPTION_PGP:
                    encryptMenu.setTitle(R.string.disable_pgp);
                    break;
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        switch (item.getItemId()) {
            case R.id.action_send:
                sendMessage();
                return true;
            case R.id.action_encryption:
                int encryptionMode = conversation.getNextEncryption();
                if (encryptionMode == Message.ENCRYPTION_NONE) {
                    showEncryptionOptions(activity);
                } else {
                    // Potential Vulnerability: Lack of proper validation before changing encryption mode
                    conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                    updateMessages();
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void showEncryptionOptions(final ConversationActivity activity) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.encryption_options);
        final String[] options = getResources().getStringArray(R.array.encryption_methods);
        int currentSelection = conversation.getNextEncryption();
        builder.setSingleChoiceItems(options, currentSelection,
                (dialog, which) -> {
                    switch (which) {
                        case Message.ENCRYPTION_OTR:
                            if (!conversation.hasValidOtrSession()) {
                                Toast.makeText(getActivity(), R.string.otr_session_not_started_yet,
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                // Potential Vulnerability: Lack of proper validation before changing encryption mode
                                conversation.setNextEncryption(Message.ENCRYPTION_OTR);
                            }
                            break;
                        case Message.ENCRYPTION_PGP:
                            if (activity.hasPgp()) {
                                // Potential Vulnerability: Lack of proper validation before changing encryption mode
                                conversation.setNextEncryption(Message.ENCRYPTION_PGP);
                            } else {
                                Toast.makeText(getActivity(), R.string.openpgp_not_available,
                                        Toast.LENGTH_SHORT).show();
                            }
                            break;
                        default:
                            // Potential Vulnerability: Lack of proper validation before changing encryption mode
                            conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                            break;
                    }
                    updateMessages();
                });
        builder.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ConversationActivity activity = (ConversationActivity) getActivity();
        if (this.conversation != null) {
            this.conversation.markRead();
            // TODO: Update notifications
            UIHelper.updateNotification(getActivity(), activity.getConversationList(),
                    null, false);
            activity.updateConversationList();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        ConversationActivity activity = (ConversationActivity) getActivity();
        if (this.conversation != null) {
            this.conversation.markRead();
            // TODO: Update notifications
            UIHelper.updateNotification(getActivity(), activity.getConversationList(),
                    null, false);
            activity.updateConversationList();
        }
    }

    private void updateMessages() {
        messageListAdapter.notifyDataSetChanged();
        messagesView.setSelection(messagesView.getCount() - 1);
    }

    public void setConversation(Conversation conversation) {
        this.conversation = conversation;
        updateMessages();
    }

    // Vulnerability: Improper handling of user input can lead to buffer overflows or other security issues
    private void decryptMessage(Message message) {
        PgpEngine pgpEngine = ((ConversationActivity) getActivity()).xmppConnectionService.getPgpEngine();
        try {
            String decryptedBody = pgpEngine.decrypt(message);
            message.setBody(decryptedBody);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Vulnerability: Improper handling of user input can lead to buffer overflows or other security issues
    private void encryptMessage(Message message) {
        PgpEngine pgpEngine = ((ConversationActivity) getActivity()).xmppConnectionService.getPgpEngine();
        try {
            String encryptedBody = pgpEngine.encrypt(message);
            message.setBody(encryptedBody);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendPgpMessage(Message message) {
        encryptMessage(message);
        ConversationActivity activity = (ConversationActivity) getActivity();
        if (activity.xmppConnectionService.sendMessage(message)) {
            conversation.addMessage(message);
            updateMessages();
        } else {
            Toast.makeText(getActivity(), R.string.error_sending_message, Toast.LENGTH_SHORT).show();
        }
    }

    private void sendOtrMessage(Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        if (!conversation.hasValidOtrSession()) {
            // Potential Vulnerability: Lack of proper validation before sending OTR message
            conversation.startOtrSession(activity.xmppConnectionService);
        }
        if (activity.xmppConnectionService.sendMessage(message)) {
            conversation.addMessage(message);
            updateMessages();
        } else {
            Toast.makeText(getActivity(), R.string.error_sending_message, Toast.LENGTH_SHORT).show();
        }
    }

    private void sendPlainTextMessage(Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        if (activity.xmppConnectionService.sendMessage(message)) {
            conversation.addMessage(message);
            updateMessages();
        } else {
            Toast.makeText(getActivity(), R.string.error_sending_message, Toast.LENGTH_SHORT).show();
        }
    }

    // Vulnerability: Improper handling of user input can lead to buffer overflows or other security issues
    private void decryptMessages() {
        for (Message message : conversation.getMessages()) {
            if (message.getEncryption() == Message.ENCRYPTION_PGP) {
                decryptMessage(message);
            }
        }
        updateMessages();
    }

    // Vulnerability: Improper handling of user input can lead to buffer overflows or other security issues
    private void encryptMessages() {
        for (Message message : conversation.getMessages()) {
            if (message.getEncryption() == Message.ENCRYPTION_PGP) {
                encryptMessage(message);
            }
        }
        updateMessages();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        ConversationActivity activity = (ConversationActivity) getActivity();
        activity.xmppConnectionService.setOnConversationUpdated(this::onConversationUpdate);

        if (activity.getSelectedAccount() != null && activity.getSelectedConversation() != null) {
            conversation = activity.getSelectedConversation();
            updateMessages();
        } else {
            Toast.makeText(getActivity(), R.string.conversation_not_found, Toast.LENGTH_SHORT).show();
        }
    }

    private void onConversationUpdate(Conversation conversation) {
        if (conversation.getUid().equals(this.conversation.getUid())) {
            this.conversation = conversation;
            updateMessages();
        }
    }

    // Vulnerability: Improper handling of user input can lead to buffer overflows or other security issues
    private void displayMessage(Message message) {
        decryptMessage(message);
        conversation.addMessage(message);
        updateMessages();
    }

    // Vulnerability: Improper handling of user input can lead to buffer overflows or other security issues
    private void handleReceivedMessage(MessagePacket packet, Account account) {
        Message message = new Message(conversation, packet);
        displayMessage(message);
    }
}