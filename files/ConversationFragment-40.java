import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.Toast;

import java.util.Set;

public class YourClassName {

    // ... existing code ...

    protected void sendPlainTextMessage(Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        activity.xmppConnectionService.sendMessage(message);  // Ensure service and methods are secure
        messageSent();  // Check if this method can be exploited
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
                                            ConversationActivity.REQUEST_ENCRYPT_MESSAGE);  // Ensure intents are handled securely
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
                    showNoPGPKeyDialog(false,
                            new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                                    message.setEncryption(Message.ENCRYPTION_NONE);
                                    xmppService.sendMessage(message);  // Ensure service and methods are secure
                                    messageSent();
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
                        warning.show();  // Ensure toasts are secure and don't expose sensitive information
                    }
                    activity.encryptTextMessage(message);
                    messageSent();
                } else {
                    showNoPGPKeyDialog(true,
                            new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                                    message.setEncryption(Message.ENCRYPTION_NONE);
                                    xmppService.sendMessage(message);  // Ensure service and methods are secure
                                    messageSent();
                                }
                            });
                }
            }
        } else {
            activity.showInstallPgpDialog();  // Ensure dialog handling is secure
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
                listener);  // Ensure listener is secure
        builder.create().show();
    }

    protected void sendOtrMessage(final Message message) {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        if (conversation.hasValidOtrSession()) {
            activity.xmppConnectionService.sendMessage(message);  // Ensure service and methods are secure
            messageSent();
        } else {
            activity.selectPresence(message.getConversation(),
                    new OnPresenceSelected() {

                        @Override
                        public void onPresenceSelected() {
                            message.setPresence(conversation.getNextPresence());
                            xmppService.sendMessage(message);  // Ensure service and methods are secure
                            messageSent();
                        }
                    });
        }
    }

    public void setText(String text) {
        this.pastedText = text;  // Sanitize and validate input if necessary
    }

    public void clearInputField() {
        this.mEditMessage.setText("");  // Ensure no sensitive information is left in memory
    }
}