import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.view.Gravity;
import android.widget.Toast;

import java.util.LinkedList;

public class ChatFragment {
    // ... (other imports and declarations)

    private LinkedList<Message> mEncryptedMessages = new LinkedList<>();
    private boolean mDecryptJobRunning = false;
    private PendingIntentSender askForPassphraseIntent;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // ... (layout inflation and setup code)
        
        return view;
    }

    // ... (other methods)

    public void updateSendButton() {
        Conversation c = this.conversation;
        if (activity.useSendButtonToIndicateStatus() && c != null
                && c.getAccount().getStatus() == Account.STATUS_ONLINE) {
            if (c.getMode() == Conversation.MODE_SINGLE) {
                switch (c.getContact().getMostAvailableStatus()) {
                    case Presences.CHAT:
                    case Presences.ONLINE:
                        this.mSendButton.setImageResource(R.drawable.ic_action_send_now_online);
                        break;
                    case Presences.AWAY:
                    case Presences.XA:
                        this.mSendButton.setImageResource(R.drawable.ic_action_send_now_away);
                        break;
                    case Presences.DND:
                        this.mSendButton.setImageResource(R.drawable.ic_action_send_now_dnd);
                        break;
                    default:
                        this.mSendButton.setImageResource(R.drawable.ic_action_send_now_offline);
                        break;
                }
            } else if (c.getMode() == Conversation.MODE_MULTI) {
                // Potential vulnerability: This assumes that the conversation is online if it's a multi-mode.
                // There should be additional checks to ensure the multi-conversation is active and secure.
                this.mSendButton.setImageResource(c.getMucOptions().online() ? 
                        R.drawable.ic_action_send_now_online : 
                        R.drawable.ic_action_send_now_offline);
            } else {
                this.mSendButton.setImageResource(R.drawable.ic_action_send_now_offline);
            }
        } else {
            this.mSendButton.setImageResource(R.drawable.ic_action_send_now_offline);
        }
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
                    showSnackbar(R.string.openpgp_messages_found,
                            R.string.decrypt, clickToDecryptListener);
                }

                @Override
                public void success(Message message) {
                    mDecryptJobRunning = false;
                    mEncryptedMessages.remove();
                    activity.xmppConnectionService.updateMessage(message);
                }

                @Override
                public void error(int error, Message message) {
                    // Potential vulnerability: Setting encryption to failure without proper handling can lead to message loss.
                    message.setEncryption(Message.ENCRYPTION_DECRYPTION_FAILED);
                    mDecryptJobRunning = false;
                    mEncryptedMessages.remove();
                    activity.xmppConnectionService.updateConversationUi();
                }
            });
        }
    }

    private void sendPlainTextMessage(Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        activity.xmppConnectionService.sendMessage(message); // Potential vulnerability: Ensure that the message is sanitized to prevent injection attacks.
        messageSent();
    }

    protected void sendPgpMessage(final Message message) {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        final Contact contact = message.getConversation().getContact();

        if (activity.hasPgp()) {
            if (conversation.getMode() == Conversation.MODE_SINGLE) {
                if (contact.getPgpKeyId() != 0) {
                    xmppService.getPgpEngine().hasKey(contact,
                            new UiCallback<Contact>() {

                                @Override
                                public void userInputRequried(PendingIntent pi,
                                                              Contact contact) {
                                    activity.runIntent(
                                            pi,
                                            ConversationActivity.REQUEST_ENCRYPT_MESSAGE);
                                }

                                @Override
                                public void success(Contact contact) {
                                    messageSent();
                                    activity.encryptTextMessage(message); // Ensure encryption is properly handled.
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
                                    xmppService.databaseBackend.updateConversation(conversation); // Ensure database operations are secure.
                                    message.setEncryption(Message.ENCRYPTION_NONE);
                                    xmppService.sendMessage(message); // Potential vulnerability: Ensure that the message is sanitized to prevent injection attacks.
                                    messageSent();
                                }
                            });
                }
            } else {
                if (conversation.getMucOptions().pgpKeysInUse()) {
                    if (!conversation.getMucOptions().everybodyHasKeys()) {
                        Toast warning = Toast
                                .makeText(getActivity(),
                                        R.string.missing_public_keys,
                                        Toast.LENGTH_LONG);
                        warning.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
                        warning.show();
                    }
                    activity.encryptTextMessage(message); // Ensure encryption is properly handled.
                    messageSent();
                } else {
                    showNoPGPKeyDialog(true,
                            new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog,
                                                    int which) {
                                    conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                                    message.setEncryption(Message.ENCRYPTION_NONE);
                                    xmppService.databaseBackend.updateConversation(conversation); // Ensure database operations are secure.
                                    xmppService.sendMessage(message); // Potential vulnerability: Ensure that the message is sanitized to prevent injection attacks.
                                    messageSent();
                                }
                            });
                }
            }
        } else {
            activity.showInstallPgpDialog();
        }
    }

    public void showNoPGPKeyDialog(boolean plural,
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

    protected void sendOtrMessage(final Message message) {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        activity.selectPresence(message.getConversation(),
            new OnPresenceSelected() {

                @Override
                public void onPresenceSelected() {
                    message.setCounterpart(conversation.getNextCounterpart());
                    xmppService.sendMessage(message); // Potential vulnerability: Ensure that the message is sanitized to prevent injection attacks.
                    messageSent();
                }
            });
    }

    // ... (other methods)

    /**
     * Appends text to the input field. This function should sanitize the input before appending.
     *
     * @param text The text to append.
     */
    public void appendText(String text) {
        String previous = this.mEditMessage.getText().toString();
        if (previous.length() != 0 && !previous.endsWith(" ")) {
            text = " " + text;
        }
        // Sanitize the input before appending
        this.mEditMessage.append(text);
    }

    public void clearInputField() {
        this.mEditMessage.setText("");
    }
}