package eu.siacs.conversations.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Set;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OtrCryptoService;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.moxl.MoxlApi;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.UIHelper;

public class ConversationFragment extends AbstractConversationFragment {

    private ListView messagesView;
    private EditText chatMsg;
    private LinearLayout snackbar;
    private TextView snackbarMessage, snackbarAction;
    private Button sendButton;
    protected BitmapCache mBitmapCache = new BitmapCache();
    private boolean messagesLoaded = false;
    private View sendPanel;
    private String pastedText;

    // Potential Vulnerability: No validation on user input or intent handling.
    // This can lead to issues if the input is not properly sanitized or if the
    // intent data is malicious. Consider validating and sanitizing all inputs,
    // especially when dealing with user-generated content or external sources.

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, LinearLayout container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_conversation, container, false);

        messagesView = view.findViewById(android.R.id.list);
        chatMsg = view.findViewById(R.id.textinput);
        sendButton = view.findViewById(R.id.send_button);
        snackbar = view.findViewById(R.id.snackbar);
        snackbarMessage = view.findViewById(R.id.message);
        snackbarAction = view.findViewById(R.id.action);

        // Set up the "Send" button click listener
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String body = chatMsg.getText().toString();
                if (!body.isEmpty()) {
                    Message message = new Message(conversation, body);
                    message.setEncryption(conversation.getNextEncryption());
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
                }
            }
        });

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Check for pasted text and set it in the chat input field
        if (pastedText != null && !pastedText.isEmpty()) {
            setText(pastedText);
        }
    }

    @Override
    protected String getShareableUri() {
        return conversation.getUuid().toString();
    }

    // Method to handle sending plain text messages
    protected void sendPlainTextMessage(Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        activity.xmppConnectionService.sendMessage(message);
        messageSent();
    }

    // Method to handle sending PGP encrypted messages
    protected void sendPgpMessage(final Message message) {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        final Contact contact = message.getConversation().getContact();

        if (activity.hasPgp()) {
            // Check if the conversation is single or multi-user chat
            if (conversation.getMode() == Conversation.MODE_SINGLE) {
                // Single user chat, check for PGP key and send encrypted message
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
                                    activity.encryptTextMessage(message);
                                }

                                @Override
                                public void error(int error, Contact contact) {

                                }
                            });
                } else {
                    // No PGP key found, show dialog to send unencrypted or cancel
                    showNoPGPKeyDialog(false,
                            new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog,
                                                    int which) {
                                    conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                                    message.setEncryption(Message.ENCRYPTION_NONE);
                                    xmppService.sendMessage(message);
                                    messageSent();
                                }
                            });
                }
            } else {
                // Multi-user chat, check if all participants have PGP keys
                if (conversation.getMucOptions().pgpKeysInUse()) {
                    if (!conversation.getMucOptions().everybodyHasKeys()) {
                        Toast warning = Toast.makeText(getActivity(),
                                R.string.missing_public_keys,
                                Toast.LENGTH_LONG);
                        warning.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
                        warning.show();
                    }
                    activity.encryptTextMessage(message);
                    messageSent();
                } else {
                    // No PGP keys found in MUC, show dialog to send unencrypted or cancel
                    showNoPGPKeyDialog(true,
                            new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog,
                                                    int which) {
                                    conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                                    message.setEncryption(Message.ENCRYPTION_NONE);
                                    xmppService.sendMessage(message);
                                    messageSent();
                                }
                            });
                }
            }
        } else {
            // OpenPGP not installed, show dialog to install
            activity.showInstallPgpDialog();
        }
    }

    // Method to handle sending OTR encrypted messages
    protected void sendOtrMessage(final Message message) {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;

        if (conversation.hasValidOtrSession()) {
            activity.xmppConnectionService.sendMessage(message);
            messageSent();
        } else {
            // No valid OTR session, select presence and send message
            activity.selectPresence(message.getConversation(),
                    new OnPresenceSelected() {

                        @Override
                        public void onPresenceSelected() {
                            message.setPresence(conversation.getNextPresence());
                            xmppService.sendMessage(message);
                            messageSent();
                        }
                    });
        }
    }

    // Method to show a dialog if no PGP key is found
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

    // Method to update the list of messages in the UI
    public void updateMessages() {
        if (getView() == null) {
            return;
        }
        ConversationActivity activity = (ConversationActivity) getActivity();

        if (this.conversation != null) {
            for (Message message : this.conversation.getMessages()) {
                // Decrypt PGP messages and update UI
                if ((message.getEncryption() == Message.ENCRYPTION_PGP)
                        && ((message.getStatus() == Message.STATUS_RECIEVED) || (message.getStatus() == Message.STATUS_SEND))) {
                    decryptMessage(message);
                    break;
                }
            }

            if (this.conversation.getMessages().size() == 0) {
                this.messageList.clear();
                messagesLoaded = false;
            } else {
                for (Message message : this.conversation.getMessages()) {
                    if (!this.messageList.contains(message)) {
                        this.messageList.add(message);
                    }
                }
                messagesLoaded = true;
                updateStatusMessages();
            }

            this.messageListAdapter.notifyDataSetChanged();

            // Handle single user chat and OTR encryption
            if (conversation.getMode() == Conversation.MODE_SINGLE) {
                if (messageList.size() >= 1) {
                    makeFingerprintWarning(conversation.getLatestEncryption());
                }
            } else {
                // Handle multi-user chat and display errors
                if (!conversation.getMucOptions().online()) {
                    if (conversation.getMucOptions().getError() == MucOptions.ERROR_NICK_IN_USE) {
                        showSnackbar(R.string.nick_in_use, R.string.edit, clickToMuc);
                    } else if (conversation.getMucOptions().getError() == MucOptions.ERROR_ROOM_NOT_FOUND) {
                        showSnackbar(R.string.conference_not_found, R.string.leave, leaveMuc);
                    }
                }
            }

            // Update UI options and notifications
            getActivity().invalidateOptionsMenu();
            updateChatMsgHint();

            if (!activity.shouldPaneBeOpen()) {
                activity.xmppConnectionService.markRead(conversation);

                // TODO: Update notifications properly
                UIHelper.updateNotification(getActivity(), activity.getConversations(), conversation, null);
            }
        }
    }

    // Method to decrypt a message and handle exceptions
    private void decryptMessage(Message message) {
        try {
            message.setDecryptedBody(activity.xmppConnectionService.getPgpEngine().decrypt(message));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Method to show a snackbar with a message and action button
    protected void showSnackbar(int messageId, int actionId, View.OnClickListener listener) {
        snackbar.setVisibility(View.VISIBLE);
        snackbarMessage.setText(getString(messageId));
        snackbarAction.setText(getString(actionId));
        snackbarAction.setOnClickListener(listener);
    }

    // Method to hide the snackbar
    protected void hideSnackbar() {
        snackbar.setVisibility(View.GONE);
    }

    // Handle click on "Edit Nickname" in snackbar action
    private View.OnClickListener clickToMuc = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            ConversationActivity activity = (ConversationActivity) getActivity();
            if (!activity.hasPgp()) {
                activity.showInstallPgpDialog();
            } else {
                EditText nickInput = new EditText(activity);
                AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                        .setTitle(R.string.choose_nick)
                        .setView(nickInput)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String nick = nickInput.getText().toString();
                                conversation.setMucJid(UIHelper.sanitizeGtalkId(nick));
                                MoxlApi.send(new MucJoin(conversation.getMucJid(), activity.xmppConnectionService), account);
                            }
                        })
                        .setNegativeButton(R.string.cancel, null);

                builder.show();
            }
        }
    };

    // Handle click on "Leave Room" in snackbar action
    private View.OnClickListener leaveMuc = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            MoxlApi.send(new MucDestroy(conversation.getMucJid(), activity.xmppConnectionService), account);
        }
    };

    // Method to handle user input validation and sanitization
    private String sanitizeInput(String input) {
        if (input == null || input.isEmpty()) return "";

        StringBuilder sanitized = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (Character.isLetterOrDigit(c) || Character.isWhitespace(c)) {
                sanitized.append(c);
            }
        }

        return sanitized.toString();
    }
}