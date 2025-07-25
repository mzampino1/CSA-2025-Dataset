package eu.siacs.conversations.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.IntentSender;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import org.openintents.openpgp.PgpEngine;
import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpServiceConnection;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OtrEngineListener;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.OnMessageListener;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.jingle.JingleManager;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;

// Potential vulnerability: Ensure that input to this fragment is sanitized and validated.
public class ConversationFragment extends Fragment {

    private EditText mEditMessage;
    private Button mSendButton;
    private ImageButton mAttachButton;
    private LinearLayout messagesLayout;
    private TextView snackbarMessage;
    private TextView snackbarAction;
    private LinearLayout snackbar;

    // Queue for encrypted messages to be decrypted
    private Queue<Message> mEncryptedMessages = new LinkedList<>();

    // Variable indicating if a decryption job is running
    private boolean mDecryptJobRunning = false;

    // Pending intent sender for passphrase requests
    private IntentSender askForPassphraseIntent;

    // Conversation object
    private Conversation conversation;

    // String to store pasted text (potential vulnerability: ensure this is handled securely)
    private String pastedText;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_conversation, container, false);
        
        mEditMessage = view.findViewById(R.id.edit_message);
        mSendButton = view.findViewById(R.id.send_button);
        mAttachButton = view.findViewById(R.id.attach_button);
        messagesLayout = view.findViewById(R.id.messages_layout);
        snackbar = view.findViewById(R.id.snackbar);
        snackbarMessage = view.findViewById(R.id.snackbar_message);
        snackbarAction = view.findViewById(R.id.snackbar_action);

        // Setup send button click listener
        mSendButton.setOnClickListener(v -> {
            String messageText = mEditMessage.getText().toString();
            if (!messageText.isEmpty()) {
                Message newMessage = createNewMessage(messageText);
                sendMessage(newMessage);
            }
        });

        return view;
    }

    private void sendMessage(Message message) {
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
            default:
                throw new IllegalStateException("Unexpected value: " + conversation.getNextEncryption());
        }
    }

    // Potential vulnerability: Ensure that messages are properly sanitized before sending.
    private Message createNewMessage(String messageText) {
        return Message.createDraftMessage(conversation, messageText);
    }

    protected void sendPlainTextMessage(Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        if (activity != null && activity.xmppConnectionServiceBound()) {
            activity.xmppConnectionService.sendMessage(message);
        }
        messageSent();
    }

    // Potential vulnerability: Ensure that PGP keys are properly verified before use.
    protected void sendPgpMessage(final Message message) {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        if (activity != null && activity.hasPgp() && activity.xmppConnectionServiceBound()) {
            final Contact contact = conversation.getContact();
            activity.xmppConnectionService.getPgpEngine().hasKey(contact, new UiCallback<Contact>() {
                @Override
                public void userInputRequried(PendingIntent pi, Contact contact) {
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
            showNoPGPKeyDialog(false, dialogInterface -> {
                conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                DatabaseBackend database = new DatabaseBackend(getActivity());
                database.updateConversation(conversation);
                message.setEncryption(Message.ENCRYPTION_NONE);
                sendPlainTextMessage(message);
            });
        }
    }

    protected void sendOtrMessage(final Message message) {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        if (activity != null && activity.xmppConnectionServiceBound()) {
            if (conversation.hasValidOtrSession()) {
                activity.xmppConnectionService.sendMessage(message);
            } else {
                activity.selectPresence(conversation, () -> {
                    message.setPresence(conversation.getNextPresence());
                    sendPlainTextMessage(message);
                });
            }
        }
    }

    protected void showNoPGPKeyDialog(boolean plural,
                                       DialogInterface.OnClickListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        if (plural) {
            builder.setTitle(getString(R.string.no_pgp_keys));
            builder.setMessage(getText(R.string.contacts_have_no_pgp_keys));
        } else {
            builder.setTitle(getString(R.string.no_pgp_key));
            builder.setMessage(getText(R.string.contact_has_no_pgp_key));
        }
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton(getString(R.string.send_unencrypted),
                listener);
        builder.create().show();
    }

    private void messageSent() {
        mEditMessage.setText("");
        updateSendButton();
    }

    // Potential vulnerability: Ensure that the send button state is updated securely.
    public void updateSendButton() {
        if (getActivity() instanceof ConversationActivity) {
            Conversation c = this.conversation;
            ConversationActivity activity = (ConversationActivity) getActivity();
            if (activity.useSendButtonToIndicateStatus() && c != null
                    && c.getAccount().getStatus() == Account.Status.ONLINE) {
                switch (c.getContact().getMostAvailableStatus()) {
                    case Presence.Show.CHAT:
                        this.mSendButton.setImageResource(R.drawable.ic_action_send_now_online);
                        break;
                    case Presence.Show.ONLINE:
                        this.mSendButton.setImageResource(R.drawable.ic_action_send_now_online);
                        break;
                    case Presence.Show.AWAY:
                        this.mSendButton.setImageResource(R.drawable.ic_action_send_now_away);
                        break;
                    case Presence.Show.XA:
                        this.mSendButton.setImageResource(R.drawable.ic_action_send_now_away);
                        break;
                    case Presence.Show.DND:
                        this.mSendButton.setImageResource(R.drawable.ic_action_send_now_dnd);
                        break;
                    default:
                        this.mSendButton.setImageResource(R.drawable.ic_action_send_now_offline);
                        break;
                }
            } else {
                this.mSendButton.setImageResource(R.drawable.ic_action_send_now_offline);
            }
        }
    }

    // Potential vulnerability: Ensure that intents are handled securely.
    private void decryptNext() {
        Message next = mEncryptedMessages.peek();
        PgpEngine engine = activity.xmppConnectionService.getPgpEngine();

        if (next != null && engine != null && !mDecryptJobRunning) {
            mDecryptJobRunning = true;
            engine.decrypt(next, new UiCallback<Message>() {

                @Override
                public void userInputRequried(PendingIntent pi, Message message) {
                    mDecryptJobRunning = false;
                    askForPassphraseIntent = pi.getIntentSender();
                    showSnackbar(R.string.openpgp_messages_found,
                            R.string.decrypt, v -> {
                                try {
                                    activity.startIntentSenderForResult(askForPassphraseIntent, ConversationActivity.REQUEST_DECRYPT_PGP, null, 0, 0, 0);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });
                }

                @Override
                public void success(Message message) {
                    mDecryptJobRunning = false;
                    mEncryptedMessages.poll();
                    DatabaseBackend database = new DatabaseBackend(getActivity());
                    database.updateMessage(message);
                    updateConversationUi();
                }

                @Override
                public void error(int error, Message message) {
                    mDecryptJobRunning = false;
                    Toast.makeText(getActivity(), R.string.error_decrypting_message, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void showSnackbar(int messageId, int actionId, View.OnClickListener listener) {
        snackbar.setVisibility(View.VISIBLE);
        snackbarMessage.setText(messageId);
        snackbarAction.setText(actionId);
        snackbarAction.setOnClickListener(listener);
    }

    // Potential vulnerability: Ensure that the conversation UI is updated securely.
    private void updateConversationUi() {
        messagesLayout.removeAllViews();
        for (Message message : conversation.getMessages()) {
            View messageView = LayoutInflater.from(getActivity()).inflate(R.layout.message_item, messagesLayout, false);
            TextView textView = messageView.findViewById(R.id.message_text);
            textView.setText(message.getBody());
            messagesLayout.addView(messageView);
        }
    }

    public void setConversation(Conversation conversation) {
        this.conversation = conversation;
        updateConversationUi();
        updateSendButton();
    }

    // Potential vulnerability: Ensure that the activity is correctly bound and not null.
    private ConversationActivity activity() {
        return (ConversationActivity) getActivity();
    }
}