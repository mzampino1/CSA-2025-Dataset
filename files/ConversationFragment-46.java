package eu.siacs.conversations.ui;

import android.app.PendingIntent;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.google.android.material.snackbar.Snackbar;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.OnPresenceSelected;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.stanzas.Presences;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

public class ConversationFragment extends Fragment {

    private Conversation conversation;
    private EditMessage mEditMessage;
    private String pastedText;
    private Snackbar snackbar;
    private View.OnClickListener clickToDecryptListener;
    private PendingIntentSender askForPassphraseIntent;
    private Queue<Message> mEncryptedMessages = new LinkedList<>();
    private boolean mDecryptJobRunning = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        final View view = inflater.inflate(R.layout.fragment_conversation, container, false);
        
        // Find the Snackbar by ID and store it
        snackbar = view.findViewById(R.id.snackbar);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize click listener for decrypting messages
        clickToDecryptListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    getActivity().startIntentSender(
                            askForPassphraseIntent,
                            null,
                            0, 0, 0);
                } catch (Exception e) {
                    // Handle exception if starting the intent sender fails
                    Toast.makeText(getActivity(),
                            R.string.error_decrypting_message,
                            Toast.LENGTH_SHORT).show();
                }
            }
        };
    }

    public void setConversation(Conversation conversation) {
        this.conversation = conversation;
        updateSendButton(); // Update send button based on current conversation status
    }

    public Conversation getConversation() {
        return this.conversation;
    }

    private void sendMessage(final Message message) {
        if (message == null || conversation == null)
            return;

        switch (message.getEncryption()) {
            case Message.ENCRYPTION_NONE:
                sendPlainTextMessage(message); // Send plain text message
                break;
            case Message.ENCRYPTION_OTR:
                sendOtrMessage(message); // Send OTR encrypted message
                break;
            case Message.ENCRYPTION_PGP:
                sendPgpMessage(message); // Send PGP encrypted message
                break;
        }
    }

    protected void sendMessage(final String text) {
        if (conversation == null || text.trim().isEmpty()) {
            return;
        }

        final Account account = conversation.getAccount();
        final Jid to = conversation.getJid();

        if (account == null || to == null)
            return;

        // Create a new message object
        Message message = new Message(conversation, text, Message.ENCRYPTION_NONE);
        message.setTime(System.currentTimeMillis());
        
        // Set the encryption method based on user preference or account settings
        message.setEncryption(account.chooseMessageEncryption());

        // Send the message
        sendMessage(message);

        // Add the sent message to the conversation history
        DatabaseBackend db = ((ConversationActivity) getActivity()).xmppConnectionService.databaseBackend;
        db.createMessage(message);
    }

    public void sendMessage() {
        String textToSend = mEditMessage.getText().trim();
        if (!textToSend.isEmpty()) {
            sendMessage(textToSend); // Send the message entered by the user
        }
    }

    protected void sendPlainTextMessage(Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        if (activity == null || activity.xmppConnectionService == null)
            return;

        activity.xmppConnectionService.sendMessage(message); // Send plain text message using XMPP service

        // Clear the input field after sending
        clearInputField();
    }

    protected void sendPgpMessage(final Message message) {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        if (activity == null || !activity.hasPgp())
            return;

        final XmppConnectionService xmppService = activity.xmppConnectionService;
        final Contact contact = message.getConversation().getContact();

        // Check if the contact has a PGP key
        if (contact.getPgpKeyId() != 0) {
            xmppService.getPgpEngine().hasKey(contact, new UiCallback<Contact>() {

                @Override
                public void userInputRequried(PendingIntent pi, Contact contact) {
                    activity.runIntent(pi, ConversationActivity.REQUEST_ENCRYPT_MESSAGE); // Handle user input requirement for encryption
                }

                @Override
                public void success(Contact contact) {
                    clearInputField();
                    activity.encryptTextMessage(message); // Encrypt and send the message
                }

                @Override
                public void error(int error, Contact contact) {
                    Toast.makeText(getActivity(), R.string.error_encrypting_message, Toast.LENGTH_SHORT).show(); // Notify user of encryption error
                }
            });
        } else {
            showNoPGPKeyDialog(false, new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                    xmppService.databaseBackend.updateConversation(conversation);
                    message.setEncryption(Message.ENCRYPTION_NONE);

                    // Send the message without encryption
                    activity.xmppConnectionService.sendMessage(message);
                    clearInputField();
                }
            });
        }
    }

    protected void sendOtrMessage(final Message message) {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        if (activity == null)
            return;

        final XmppConnectionService xmppService = activity.xmppConnectionService;
        final Contact contact = message.getConversation().getContact();

        // Check if there is a valid OTR session with the contact
        if (conversation.hasValidOtrSession()) {
            activity.xmppConnectionService.sendMessage(message); // Send message using existing OTR session

            clearInputField();
        } else {
            // No valid OTR session, ask user to select a presence
            activity.selectPresence(conversation, new OnPresenceSelected() {

                @Override
                public void onPresenceSelected() {
                    if (conversation == null)
                        return;

                    message.setPresence(conversation.getNextPresence());

                    // Send the message with selected presence
                    activity.xmppConnectionService.sendMessage(message);
                    clearInputField();
                }
            });
        }
    }

    private void showNoPGPKeyDialog(boolean plural, DialogInterface.OnClickListener listener) {
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
        builder.setPositiveButton(getString(R.string.send_unencrypted), listener);

        // Show the dialog to user
        builder.create().show();
    }

    public void clearInputField() {
        if (mEditMessage != null) {
            mEditMessage.setText("");
        }
    }

    /**
     * Update send button based on current account and conversation status.
     */
    private void updateSendButton() {
        Conversation c = this.conversation;
        final ConversationActivity activity = (ConversationActivity) getActivity();
        if (activity == null || c == null)
            return;

        Account account = c.getAccount();

        if (!account.isOnline()) {
            mEditMessage.setSendButtonImage(R.drawable.ic_action_send_offline);
            return;
        }

        // Check the mode of conversation and update button accordingly
        if (c.getMode() == Conversation.MODE_SINGLE) {
            switch (c.getContact().getMostAvailableStatus()) {
                case Presences.CHAT:
                    mEditMessage.setSendButtonImage(R.drawable.ic_action_send_now_online);
                    break;
                case Presences.ONLINE:
                    mEditMessage.setSendButtonImage(R.drawable.ic_action_send_now_online);
                    break;
                case Presences.AWAY:
                    mEditMessage.setSendButtonImage(R.drawable.ic_action_send_now_away);
                    break;
                case Presences.XA:
                    mEditMessage.setSendButtonImage(R.drawable.ic_action_send_now_away);
                    break;
                case Presences.DND:
                    mEditMessage.setSendButtonImage(R.drawable.ic_action_send_now_dnd);
                    break;
                default:
                    mEditMessage.setSendButtonImage(R.drawable.ic_action_send_offline);
                    break;
            }
        } else if (c.getMode() == Conversation.MODE_MULTI) {
            if (c.getMucOptions().online()) {
                mEditMessage.setSendButtonImage(R.drawable.ic_action_send_now_online);
            } else {
                mEditMessage.setSendButtonImage(R.drawable.ic_action_send_offline);
            }
        }
    }

    /**
     * Decrypt messages that require user interaction.
     */
    private void decryptMessages() {
        while (!mEncryptedMessages.isEmpty()) {
            Message message = mEncryptedMessages.poll();
            if (message == null) continue;

            PgpEngine pgp = ((ConversationActivity) getActivity()).xmppConnectionService.getPgpEngine();

            if (pgp == null || !pgp.isDecryptionPossible()) {
                // Handle the case where decryption is not possible
                Toast.makeText(getActivity(), R.string.error_decrypting_message, Toast.LENGTH_SHORT).show();
                continue;
            }

            pgp.decryptMessage(message, new UiCallback<Message>() {

                @Override
                public void success(Message decrypted) {
                    // Notify the adapter about the updated message
                    notifyConversationUpdate(decrypted);
                }

                @Override
                public void error(int errorCode, Message decrypted) {
                    Toast.makeText(getActivity(), R.string.error_decrypting_message, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void userInputRequried(PendingIntent pi, Message decrypted) {
                    mEncryptedMessages.add(decrypted);
                    askForPassphraseIntent = pi;
                    showSnackbar(R.string.decryption_required, clickToDecryptListener);
                }
            });
        }
    }

    /**
     * Show a Snackbar with the given message and action.
     *
     * @param messageId ID of the string resource to be displayed as the message
     * @param listener  Action listener for the Snackbar action button
     */
    private void showSnackbar(int messageId, View.OnClickListener listener) {
        snackbar.setText(messageId);
        snackbar.setAction(R.string.action_decrypt, listener);
        snackbar.show();
    }

    /**
     * Notify the conversation adapter about an updated message.
     *
     * @param message The message that has been decrypted or updated
     */
    private void notifyConversationUpdate(Message message) {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        if (activity == null)
            return;

        // Find the conversation in the adapter and update it
        int position = activity.conversationAdapter.getPositionForItem(conversation);
        if (position >= 0) {
            activity.conversationAdapter.updateMessage(message, position);
        }
    }

    /**
     * Handle incoming messages and decrypt them if necessary.
     *
     * @param message The incoming message to be processed
     */
    public void onNewMessage(final Message message) {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        if (activity == null || message == null)
            return;

        // Check if the message is encrypted and requires decryption
        if (message.isEncrypted()) {
            mEncryptedMessages.add(message);
            decryptMessages(); // Attempt to decrypt queued messages
        } else {
            // Notify the adapter about the new incoming message
            notifyConversationUpdate(message);
        }
    }

    /**
     * Handle changes in conversation status.
     *
     * @param account   The account associated with the conversation
     * @param connected Boolean indicating whether the account is connected
     */
    public void onConversationStatusChanged(Account account, boolean connected) {
        if (conversation != null && account.getJid().equals(conversation.getAccount().getJid())) {
            updateSendButton(); // Update send button based on new connection status
        }
    }

    /**
     * Handle user input for sending a message.
     *
     * @param text Message text entered by the user
     */
    public void onUserInput(String text) {
        if (conversation == null || text == null)
            return;

        // Set the input text to the edit message view
        mEditMessage.setText(text);
    }
}