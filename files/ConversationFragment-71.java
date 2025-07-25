package eu.siacs.conversations.ui.fragment;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.jivesoftware.smack.packet.Presence;
import org.jxmpp.stringprep.XmppStringprepException;

import java.util.ArrayList;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlSession;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.ConversationActivity;
import eu.siacs.conversations.ui.util.AttachmentPreview;
import eu.siacs.conversations.utils.DraftProvider;
import eu.siacs.conversations.utils.ExceptionHelper;
import eu.siacs.conversations.utils.MessageUtils;
import eu.siacs.conversations.utils.StringUtils;
import eu.siacs.conversations.utils.UIHelper;
import rocks.xmpp.addr.Jid;

public class ConversationFragment extends AbstractConversationFragment {

    private EditText mEditMessage;
    private Button mSendButton;
    private LinearLayout snackbar;
    private TextView snackbarMessage;
    private TextView snackbarAction;
    private ArrayList<AttachmentPreview> mPendingFileTransfers = new ArrayList<>();
    private boolean mHasToSendUnsentImages;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // ... existing code ...

        return view;
    }

    @Override
    protected void initializeViews(View view) {
        // ... existing code ...

        // Potential Vulnerability: If `mEditMessage` is directly populated with user input without proper validation,
        // it could lead to issues such as injection attacks if the content is not handled safely.
        mEditMessage = (EditText) view.findViewById(R.id.textinput);
        this.mSendButton = (Button) view.findViewById(R.id.sendMessageButton);
        snackbar = (LinearLayout) view.findViewById(R.id.snackbarContainer);
        snackbarMessage = (TextView) view.findViewById(R.id.snackbar_message);
        snackbarAction = (TextView) view.findViewById(R.id.snackbar_action);
    }

    @Override
    protected void updateView() {
        // ... existing code ...

        this.mEditMessage.setHint(this.conversation.getLatestMessage().getType() == Message.TYPE_PRIVATE ? R.string.reply_hint_private : R.string.reply_hint);

        if (!mPendingFileTransfers.isEmpty()) {
            sendQueuedFiles();
        }

        // Potential Vulnerability: If `this.mEditMessage` is not cleared or reset appropriately,
        // it might retain previous user input, leading to unintended message duplication.
        this.mEditMessage.clearFocus();
    }

    @Override
    public void onStart() {
        super.onStart();

        final ConversationActivity activity = (ConversationActivity) getActivity();
        if (!activity.xmppConnectionServiceBound()) {
            return;
        }

        // ... existing code ...

        // Potential Vulnerability: Ensure that any sensitive data handled here is properly sanitized and validated.
        conversation.setNextEncryption(activity.getNextMessageEncryption(conversation));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // ... existing code ...
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // ... existing code ...
    }

    protected boolean sendMessage() {
        ConversationActivity activity = (ConversationActivity) getActivity();

        // Potential Vulnerability: Ensure that `mEditMessage` input is properly validated to prevent injection attacks.
        String body = mEditMessage.getText().toString();
        if (body.trim().isEmpty()) {
            return false;
        }

        Message message = new Message(conversation, body, conversation.getNextEncryption());
        switch (conversation.getMode()) {
            case SINGLE:
                // ... existing code ...
                break;
            case MULTI:
                // ... existing code ...
                break;
        }
        // ... existing code ...

        // Potential Vulnerability: Sending messages without additional checks could lead to issues if the message content is not controlled.
        switch (conversation.getNextEncryption()) {
            case Message.ENCRYPTION_NONE:
                sendPlainTextMessage(message);
                break;
            case Message.ENCRYPTION_PGP:
                sendPgpMessage(message);
                break;
            case Message.ENCRYPTION_AXOLOTL:
                sendAxolotlMessage(message);
                break;
            case Message.ENCRYPTION_OTR:
                sendOtrMessage(message);
                break;
        }
        return true;
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // ... existing code ...
    }

    private void sendPlainTextMessage(Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        activity.xmppConnectionService.sendMessage(message);
        messageSent();
    }

    private void sendPgpMessage(final Message message) {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        final Contact contact = message.getConversation().getContact();

        // Potential Vulnerability: Ensure that PGP key handling and encryption processes are secure.
        if (activity.hasPgp()) {
            if (conversation.getMode() == Conversation.MODE_SINGLE) {
                if (contact.getPgpKeyId() != 0) {
                    xmppService.getPgpEngine().hasKey(contact, new UiCallback<Contact>() {

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
                            // Handle errors appropriately.
                        }
                    });
                } else {
                    showNoPGPKeyDialog(false, new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                            xmppService.databaseBackend.updateConversation(conversation);
                            message.setEncryption(Message.ENCRYPTION_NONE);
                            xmppService.sendMessage(message);
                            messageSent();
                        }
                    });
                }
            } else {
                if (conversation.getMucOptions().pgpKeysInUse()) {
                    if (!conversation.getMucOptions().everybodyHasKeys()) {
                        Toast warning = Toast.makeText(getActivity(), R.string.missing_public_keys, Toast.LENGTH_LONG);
                        warning.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
                        warning.show();
                    }
                    activity.encryptTextMessage(message);
                    messageSent();
                } else {
                    showNoPGPKeyDialog(true, new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                            message.setEncryption(Message.ENCRYPTION_NONE);
                            xmppService.databaseBackend.updateConversation(conversation);
                            xmppService.sendMessage(message);
                            messageSent();
                        }
                    });
                }
            }
        } else {
            activity.showInstallPgpDialog();
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
        builder.create().show();
    }

    private void sendAxolotlMessage(final Message message) {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        xmppService.sendMessage(message);
        messageSent();
    }

    private void sendOtrMessage(final Message message) {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;

        // Potential Vulnerability: Ensure proper handling of OTR keys and session establishment.
        activity.selectPresence(message.getConversation(), new OnPresenceSelected() {

            @Override
            public void onPresenceSelected() {
                message.setCounterpart(conversation.getNextCounterpart());
                xmppService.sendMessage(message);
                messageSent();
            }
        });
    }

    protected void appendText(String text) {
        if (text == null) {
            return;
        }
        String previous = this.mEditMessage.getText().toString();
        this.mEditMessage.setText(previous + "\n" + text);
        this.mEditMessage.setSelection(this.mEditMessage.length());
    }

    @Override
    public void onEnterPressed() {
        sendMessage();
    }

    // ... existing code ...
}