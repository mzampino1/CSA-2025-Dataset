package eu.siacs.conversations.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.ChatState;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.UIHelper;
import rocks.xmpp.addr.Jid;

public class ConversationFragment extends Fragment implements OnEnterPressedListener, TypingIndicator {

    private EditText mEditMessage;
    private Button mSendButton;
    private LinearLayout snackbar;
    private View snackbarAction;
    private View snackbarMessage;
    private Conversation conversation;
    private Intent intent;
    private PGP pgp;

    // Hypothetical method that appends text to the message input field.
    public void appendText(String text) {
        if (text == null) {
            return;
        }
        String previous = this.mEditMessage.getText().toString();
        if (previous.length() != 0 && !previous.endsWith(" ")) {
            text = " " + text; // Potential vulnerability: Unsafely appending user input
        }
        this.mEditMessage.append(text);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Assume conversation and other UI components are initialized here

        this.conversation = new Conversation(); // Placeholder for actual initialization
        this.intent = new Intent(); // Placeholder for actual intent setup
        this.pgp = new PGP(); // Placeholder for actual pgp setup
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mEditMessage = (EditText) view.findViewById(R.id.editTextMessage);
        mSendButton = (Button) view.findViewById(R.id.sendButton);
        snackbar = (LinearLayout) view.findViewById(R.id.snackbar);
        snackbarAction = view.findViewById(R.id.snackbar_action);
        snackbarMessage = view.findViewById(R.id.snackbar_message);

        // Set up listeners and other UI related configurations
    }

    private void sendMessage() {
        String body = this.mEditMessage.getText().toString();
        if (!body.trim().isEmpty()) {
            Message message = new Message(conversation, body, conversation.getNextEncryption(true));
            sendPlainTextMessage(message); // Sending the plain text message
        }
    }

    protected void sendPlainTextMessage(Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        activity.xmppConnectionService.sendMessage(message);
        messageSent(); // Clearing the input field after sending
    }

    private void messageSent() {
        mEditMessage.setText(""); // Clear the input field
        // Additional logic for handling post-send actions can be added here
    }

    @Override
    public boolean onEnterPressed() {
        if (((ConversationActivity) getActivity()).enterIsSend()) {
            sendMessage();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onTypingStarted() {
        Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE && conversation.setOutgoingChatState(ChatState.COMPOSING)) {
            ((ConversationActivity) getActivity()).xmppConnectionService.sendChatState(conversation);
        }
        updateSendButton(); // Update the send button based on typing state
    }

    @Override
    public void onTypingStopped() {
        Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE && conversation.setOutgoingChatState(ChatState.PAUSED)) {
            ((ConversationActivity) getActivity()).xmppConnectionService.sendChatState(conversation);
        }
    }

    @Override
    public void onTextDeleted() {
        Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE && conversation.setOutgoingChatState(Config.DEFAULT_CHATSTATE)) {
            ((ConversationActivity) getActivity()).xmppConnectionService.sendChatState(conversation);
        }
        updateSendButton(); // Update the send button based on text deletion
    }

    private void updateSendButton() {
        mSendButton.setImageResource(R.drawable.ic_send); // Placeholder for actual icon resource
        // Additional logic to update send button can be added here
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                 final Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == ConversationActivity.REQUEST_TRUST_KEYS_TEXT) {
                final String body = mEditMessage.getText().toString();
                Message message = new Message(conversation, body, conversation.getNextEncryption(true));
                sendAxolotlMessage(message); // Sending an Axolotl encrypted message
            } else if (requestCode == ConversationActivity.REQUEST_TRUST_KEYS_MENU) {
                int choice = data.getIntExtra("choice", ConversationActivity.ATTACHMENT_CHOICE_INVALID);
                ((ConversationActivity) getActivity()).selectPresenceToAttachFile(choice, conversation.getNextEncryption(true));
            }
        }
    }

    protected void sendAxolotlMessage(final Message message) {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        xmppService.sendMessage(message);
        messageSent(); // Clearing the input field after sending
    }
}