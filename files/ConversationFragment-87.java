import android.app.Activity;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConversationFragment extends Fragment implements MessageEditorInput {

    // Declare UI components and variables
    private EditText mEditMessage;
    private View snackbar, snackbarAction;
    private TextView snackbarMessage;

    // Reference to the current conversation
    private Conversation conversation;

    // Reference to the parent activity (assuming it's a ConversationActivity)
    private ConversationActivity activity;

    // AtomicBoolean to manage PGP message sending process
    private final AtomicBoolean mSendingPgpMessage = new AtomicBoolean(false);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Initialize references and components here
        this.activity = (ConversationActivity) getActivity();
        this.conversation = activity.getSelectedConversation();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_conversation, container, false);
        
        // Initialize UI components
        mEditMessage = (EditText) view.findViewById(R.id.edit_text_message);
        snackbar = view.findViewById(R.id.snackbar);
        snackbarAction = view.findViewById(R.id.snackbar_action);
        snackbarMessage = (TextView) view.findViewById(R.id.snackbar_message);

        // Set listeners and other configurations here
        return view;
    }

    public void sendMessage() {
        String body = mEditMessage.getText().toString();
        if (body.isEmpty()) return;

        Message message;
        switch (conversation.getMode()) {
            case Conversation.MODE_SINGLE:
                message = new Message(conversation, body, conversation.getNextEncryption());
                break;
            case Conversation.MODE_MULTI:
                message = new Message(conversation, body, Message.ENCRYPTION_NONE);
                break;
            default:
                return;
        }

        // Validate encryption and send message
        if (conversation.getMode() == Conversation.MODE_SINGLE) {
            Contact contact = conversation.getContact();
            if (contact != null && contact.getPgpKeyId() != 0) {
                sendPgpMessage(message);
            } else {
                sendPlainTextMessage(message);
            }
        } else if (conversation.getMucOptions().pgpKeysInUse()) {
            sendPgpMessage(message);
        } else {
            sendPlainTextMessage(message);
        }

        // Clear the input field
        mEditMessage.setText("");
    }

    private void sendPlainTextMessage(Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        if (activity != null && activity.xmppConnectionService != null) {
            activity.xmppConnectionService.sendMessage(message);
        }
        messageSent();
    }

    private void sendPgpMessage(final Message message) {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity != null ? activity.xmppConnectionService : null;
        final Contact contact = message.getConversation().getContact();

        if (activity == null || !activity.hasPgp()) {
            activity.showInstallPgpDialog();
            return;
        }

        Account account = conversation.getAccount();
        if (account.getPgpSignature() == null) {
            activity.announcePgp(account, conversation, activity.onOpenPGPKeyPublished);
            return;
        }

        if (!mSendingPgpMessage.compareAndSet(false,true)) {
            Log.d(Config.LOGTAG,"sending pgp message already in progress");
            return;
        }

        if (conversation.getMode() == Conversation.MODE_SINGLE) {
            xmppService.getPgpEngine().hasKey(contact,
                    new UiCallback<Contact>() {

                        @Override
                        public void userInputRequried(PendingIntent pi, Contact contact) {
                            activity.runIntent(pi, ConversationActivity.REQUEST_ENCRYPT_MESSAGE);
                        }

                        @Override
                        public void success(Contact contact) {
                            activity.encryptTextMessage(message);
                        }

                        @Override
                        public void error(int error, Contact contact) {
                            if (activity != null) {
                                Toast.makeText(activity, R.string.unable_to_connect_to_keychain,
                                        Toast.LENGTH_SHORT).show();
                            }
                            mSendingPgpMessage.set(false);
                        }
                    });
        } else {
            if (conversation.getMucOptions().pgpKeysInUse()) {
                if (!conversation.getMucOptions().everybodyHasKeys()) {
                    Toast warning = Toast.makeText(getActivity(), R.string.missing_public_keys,
                            Toast.LENGTH_LONG);
                    warning.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
                    warning.show();
                }
                activity.encryptTextMessage(message);
            } else {
                showNoPGPKeyDialog(true, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                        message.setEncryption(Message.ENCRYPTION_NONE);
                        if (xmppService != null) {
                            xmppService.updateConversation(conversation);
                            xmppService.sendMessage(message);
                        }
                        messageSent();
                    }
                });
            }
        }
    }

    public void showNoPGPKeyDialog(boolean plural, DialogInterface.OnClickListener listener) {
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

    public void sendAxolotlMessage(final Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        if (activity != null && activity.xmppConnectionService != null) {
            activity.xmppConnectionService.sendMessage(message);
        }
        messageSent();
    }

    public void sendOtrMessage(final Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity != null ? activity.xmppConnectionService : null;
        if (activity != null && xmppService != null) {
            activity.selectPresence(message.getConversation(),
                    new OnPresenceSelected() {

                        @Override
                        public void onPresenceSelected() {
                            message.setCounterpart(conversation.getNextCounterpart());
                            xmppService.sendMessage(message);
                        }
                    });
        }
    }

    private void messageSent() {
        // Notify the user or update the UI upon successful message sending
        if (activity != null && activity.xmppConnectionService != null) {
            activity.refreshUiReal();
        }
    }

    public void appendText(String text) {
        if (text == null || mEditMessage == null) return;
        String previous = this.mEditMessage.getText().toString();
        if (!previous.endsWith(" ")) {
            text = " " + text;
        }
        this.mEditMessage.append(text);
    }

    @Override
    public boolean onEnterPressed() {
        return activity.enterIsSend() && sendMessage();
    }

    @Override
    public void onTypingStarted() {
        Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE && conversation.setOutgoingChatState(ChatState.COMPOSING)) {
            if (activity != null && activity.xmppConnectionService != null) {
                activity.xmppConnectionService.sendChatState(conversation);
            }
        }
        activity.hideConversationsOverview();
    }

    @Override
    public void onTypingStopped() {
        Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE && conversation.setOutgoingChatState(ChatState.PAUSED)) {
            if (activity != null && activity.xmppConnectionService != null) {
                activity.xmppConnectionService.sendChatState(conversation);
            }
        }
    }

    @Override
    public void onTextDeleted() {
        Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE && conversation.setOutgoingChatState(Config.DEFAULT_CHATSTATE)) {
            if (activity != null && activity.xmppConnectionService != null) {
                activity.xmppConnectionService.sendChatState(conversation);
            }
        }
    }

    @Override
    public void onTextChanged() {
        updateSendButton();
    }

    private void updateSendButton() {
        // Update send button based on the current state of the conversation and message editor
        if (conversation != null && conversation.getCorrectingMessage() != null) {
            updateSendButton(); // Recursive call? Likely an error or misuse.
        }
    }

    private int completionIndex = 0;
    private int lastCompletionLength = 0;
    private String incomplete;
    private int lastCompletionCursor;
    private boolean firstWord = false;

    @Override
    public boolean onTabPressed(boolean repeated) {
        if (conversation == null || conversation.getMode() != Conversation.MODE_MULTI) return false;

        if (!repeated) {
            this.completionIndex = 0; // Reset completion index when not repeating
        }

        List<String> members = new ArrayList<>(conversation.getMucOptions().getMembers());
        if (members.isEmpty()) return false;

        Collections.sort(members);
        String currentText = mEditMessage.getText().toString();
        int cursorPosition = mEditMessage.getSelectionStart();

        // Find the start of the word to complete
        int wordStartIndex = currentText.lastIndexOf(' ', cursorPosition) + 1;
        this.incomplete = currentText.substring(wordStartIndex, cursorPosition);

        for (String member : members) {
            if (member.toLowerCase().startsWith(this.incomplete.toLowerCase())) {
                String completion = member.substring(this.incomplete.length());
                mEditMessage.getText().replace(cursorPosition, cursorPosition, completion);
                this.lastCompletionCursor = cursorPosition + completion.length();
                return true;
            }
        }

        // If no match is found, wrap around to the first matching name
        for (String member : members) {
            if (member.toLowerCase().startsWith(this.incomplete.toLowerCase())) {
                String completion = member.substring(this.incomplete.length());
                mEditMessage.getText().replace(cursorPosition, cursorPosition, completion);
                this.lastCompletionCursor = cursorPosition + completion.length();
                return true;
            }
        }

        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == ConversationActivity.REQUEST_ENCRYPT_MESSAGE && resultCode == Activity.RESULT_OK) {
            // Handle the result from the encryption process here
            Message message = activity.getCurrentlySelectedMessage();
            sendPgpMessage(message);
        }
    }

    private void showSnackbar(String message, String actionText, View.OnClickListener listener) {
        snackbar.setVisibility(View.VISIBLE);
        snackbarMessage.setText(message);

        if (actionText != null && listener != null) {
            snackbarAction.setVisibility(View.VISIBLE);
            snackbarAction.setText(actionText);
            snackbarAction.setOnClickListener(listener);
        } else {
            snackbarAction.setVisibility(View.GONE);
        }
    }

    private void hideSnackbar() {
        snackbar.setVisibility(View.GONE);
    }
}