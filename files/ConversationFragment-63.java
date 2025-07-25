package eu.siacs.conversations.ui;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.IntentSender;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import java.util.LinkedList;
import java.util.NoSuchElementException;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.Presences;
import eu.siacs.conversations.utils.crypto.OtrEngineImpl.SessionStatus;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.pgp.OpenPgpApi.PgpEngine;
import rocks.xmpp.addr.JidParseException;

public class ConversationFragment extends Fragment {

    private EditText mEditMessage;
    private ImageButton mSendButton;
    private LinearLayout snackbar;
    private View snackbarAction;
    private View snackbarMessage;
    private IntentSender askForPassphraseIntent = null;
    protected MessageListAdapter messageListAdapter;
    public LinkedList<Message> mEncryptedMessages = new LinkedList<>();
    public Conversation conversation;
    public boolean mDecryptJobRunning = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Ensure that the conversation is properly initialized here.
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        final View view = inflater.inflate(R.layout.fragment_conversation, container, false);

        mEditMessage = (EditText) view.findViewById(R.id.message);
        mSendButton = (ImageButton) view.findViewById(R.id.sendMessage);
        snackbar = (LinearLayout) view.findViewById(R.id.snackbar);
        snackbarAction = (View) view.findViewById(R.id.snackbar_action);
        snackbarMessage = (View) view.findViewById(R.id.snackbar_message);

        // Setup send button click listener
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = mEditMessage.getText().toString();
                if (!text.trim().isEmpty()) {
                    Message message = new Message(conversation, text, conversation.getNextCounterpart(), true);
                    sendMessage(message); // Function to handle sending messages securely.
                }
            }
        });

        return view;
    }

    private void sendMessage(Message message) {
        switch (message.getEncryption()) {
            case Message.ENCRYPTION_NONE:
                sendPlainTextMessage(message);
                break;
            case Message.ENCRYPTION_PGP:
                sendPgpMessage(message);
                break;
            case Message.ENCRYPTION_OTR_CURRENT:
                sendOtrMessage(message);
                break;
        }
    }

    protected void sendMessage(Message message) {
        // Send the message securely based on its encryption type
        switch (message.getEncryption()) {
            case Message.ENCRYPTION_NONE:
                sendPlainTextMessage(message);
                break;
            case Message.ENCRYPTION_PGP:
                sendPgpMessage(message);
                break;
            case Message.ENCRYPTION_OTR_CURRENT:
                sendOtrMessage(message);
                break;
        }
    }

    protected void sendPlainTextMessage(Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        activity.xmppConnectionService.sendMessage(message); // Send the plain text message
        messageSent(); // Update UI after sending
    }

    protected void sendPgpMessage(final Message message) {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        final Contact contact = message.getConversation().getContact();

        if (activity.hasPgp()) { // Check if PGP is available
            if (conversation.getMode() == Conversation.MODE_SINGLE) {
                if (contact.getPgpKeyId() != 0) {
                    xmppService.getPgpEngine().hasKey(contact, new UiCallback<Contact>() {

                        @Override
                        public void userInputRequried(PendingIntent pi,
                                                      Contact contact) {
                            activity.runIntent(pi, ConversationActivity.REQUEST_ENCRYPT_MESSAGE); // Handle user input for encryption
                        }

                        @Override
                        public void success(Contact contact) {
                            messageSent(); // Update UI after successful encryption
                            activity.encryptTextMessage(message); // Encrypt and send the message
                        }

                        @Override
                        public void error(int error, Contact contact) {
                            // Handle error in key check
                        }
                    });
                } else {
                    showNoPGPKeyDialog(false,
                            new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog,
                                                    int which) {
                                    conversation.setNextEncryption(Message.ENCRYPTION_NONE); // Set encryption to none if no PGP key found
                                    xmppService.databaseBackend.updateConversation(conversation); // Update database with changed encryption setting
                                    message.setEncryption(Message.ENCRYPTION_NONE);
                                    xmppService.sendMessage(message); // Send message without encryption
                                    messageSent(); // Update UI after sending
                                }
                            });
                }
            } else {
                if (conversation.getMucOptions().pgpKeysInUse()) { // Check if PGP keys are in use for MUC
                    if (!conversation.getMucOptions().everybodyHasKeys()) {
                        Toast warning = Toast.makeText(getActivity(), R.string.missing_public_keys, Toast.LENGTH_LONG);
                        warning.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
                        warning.show(); // Show warning if not all members have PGP keys
                    }
                    activity.encryptTextMessage(message); // Encrypt and send the message
                    messageSent(); // Update UI after sending
                } else {
                    showNoPGPKeyDialog(true,
                            new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog,
                                                    int which) {
                                    conversation.setNextEncryption(Message.ENCRYPTION_NONE); // Set encryption to none if no PGP keys found for MUC
                                    message.setEncryption(Message.ENCRYPTION_NONE);
                                    xmppService.databaseBackend.updateConversation(conversation); // Update database with changed encryption setting
                                    xmppService.sendMessage(message); // Send message without encryption
                                    messageSent(); // Update UI after sending
                                }
                            });
                }
            }
        } else {
            activity.showInstallPgpDialog(); // Prompt user to install PGP if not available
        }
    }

    protected void sendOtrMessage(final Message message) {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        activity.selectPresence(message.getConversation(),
                new OnPresenceSelected() {

                    @Override
                    public void onPresenceSelected() {
                        message.setCounterpart(conversation.getNextCounterpart());
                        xmppService.sendMessage(message); // Send the OTR encrypted message
                        messageSent(); // Update UI after sending
                    }
                });
    }

    private void showNoPGPKeyDialog(boolean plural,
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
                listener); // Set listener for sending message unencrypted
        builder.create().show(); // Show dialog to user
    }

    private void decryptNext() {
        Message next = this.mEncryptedMessages.peek();
        PgpEngine engine = activity.xmppConnectionService.getPgpEngine();

        if (next != null && engine != null && !mDecryptJobRunning) {
            mDecryptJobRunning = true;
            engine.decrypt(next, new UiCallback<Message>() {

                @Override
                public void userInputRequried(PendingIntent pi, Message message) {
                    mDecryptJobRunning = false;
                    askForPassphraseIntent = pi.getIntentSender();
                    showSnackbar(R.string.passphrase_required,
                            R.string.provide_passphrase); // Show snackbar for passphrase requirement
                }

                @Override
                public void success(Message message) {
                    mEncryptedMessages.remove(); // Remove the message from the queue after decryption
                    updateMessageUi(message); // Update UI with decrypted message
                    mDecryptJobRunning = false;
                }

                @Override
                public void error(int errorCode, Message message) {
                    mDecryptJobRunning = false;
                    showSnackbar(R.string.decryption_failed,
                            R.string.could_not_decrypt_message); // Show snackbar for decryption failure
                }
            });
        }
    }

    private void updateMessageUi(Message message) {
        // Update UI with the decrypted message
        if (messageListAdapter != null) {
            messageListAdapter.updateMessage(message);
        }
    }

    protected void showSnackbar(int textRes, int actionTextRes) {
        snackbar.setVisibility(View.VISIBLE); // Make snackbar visible
        snackbarMessage.setText(textRes); // Set snackbar message text
        snackbarAction.setVisibility(View.VISIBLE); // Make snackbar action visible
        snackbarAction.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent().setIntentSender(askForPassphraseIntent, null, 0, 0, 0));
                } catch (Exception e) {
                    // Handle exception from starting activity for passphrase input
                }
                snackbar.setVisibility(View.GONE); // Hide snackbar after action
            }
        });
    }

    private void messageSent() {
        mEditMessage.setText(""); // Clear the edit text after sending a message
        updateMessagesUi(); // Update UI to show sent message
    }

    private void updateMessagesUi() {
        if (messageListAdapter != null) {
            messageListAdapter.notifyDataSetChanged();
        }
    }
}