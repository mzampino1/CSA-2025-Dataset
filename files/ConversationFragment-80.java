package eu.siacs.conversations.ui;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.services.MessageArchiveService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.interfaces.OnPresenceSelected;
import eu.siacs.conversations.utils.CryptoHelper.UiCallback;
import eu.siacs.conversations.utils.MucOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConversationFragment extends Fragment implements OnEnterPressedListener, OnTypingListener {

    private View snackbar;
    private TextView snackbarMessage;
    private Button snackbarAction;
    private Conversation conversation;
    private EditText mEditMessage;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Initialize the fragment and retrieve necessary views and data.
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_conversation, container, false);

        mEditMessage = (EditText) view.findViewById(R.id.edit_message);
        snackbar = view.findViewById(R.id.snackbar);
        snackbarMessage = (TextView) view.findViewById(R.id.snackbar_message);
        snackbarAction = (Button) view.findViewById(R.id.snackbar_action);

        // Set up listeners for the message input field
        mEditMessage.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
                    (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    return onEnterPressed();
                }
                return false;
            }
        });

        mEditMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                ConversationFragment.this.onTextChanged();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        return view;
    }

    // Method to send a message based on the current encryption setting of the conversation
    protected void sendMessage() {
        String body = mEditMessage.getText().toString();
        if (body.trim().length() == 0) {
            return;
        }
        Message message = new Message(conversation, body, conversation.getNextEncryption());
        switch (message.getEncryption()) {
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
    }

    // Method to update the UI based on the typing state of the user
    protected void updateSendButton() {
        if (conversation.getCorrectingMessage() != null) {
            snackbarAction.setText(R.string.send_correction);
        } else {
            snackbarAction.setText(R.string.send_message);
        }
    }

    // ... rest of the provided methods with additional comments

    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                 final Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            ConversationActivity activity = (ConversationActivity) getActivity();
            if (requestCode == ConversationActivity.REQUEST_DECRYPT_PGP) {
                Account account = conversation.getAccount();
                account.getPgpDecryptionService().continueDecryption(true);
            } else if (requestCode == ConversationActivity.REQUEST_TRUST_KEYS_TEXT) {
                String body = mEditMessage.getText().toString();
                Message message = new Message(conversation, body, conversation.getNextEncryption());
                sendAxolotlMessage(message);
            } else if (requestCode == ConversationActivity.REQUEST_TRUST_KEYS_MENU) {
                int choice = data.getIntExtra("choice", ConversationActivity.ATTACHMENT_CHOICE_INVALID);
                activity.selectPresenceToAttachFile(choice, conversation.getNextEncryption());
            }
        }
    }

    @Override
    public boolean onEnterPressed() {
        ConversationActivity activity = (ConversationActivity) getActivity();
        if (activity.enterIsSend()) {
            sendMessage();
            return true;
        } else {
            return false;
        }
    }

    // Implementing methods from OnTypingListener interface to handle typing states
    @Override
    public void onTypingStarted() {
        Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE && conversation.setOutgoingChatState(ChatState.COMPOSING)) {
            ((ConversationActivity) getActivity()).xmppConnectionService.sendChatState(conversation);
        }
        ((ConversationActivity) getActivity()).hideConversationsOverview();
        updateSendButton();
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
        updateSendButton();
    }

    @Override
    public void onTextChanged() {
        if (conversation != null && conversation.getCorrectingMessage() != null) {
            updateSendButton();
        }
    }

    // Method to handle tab completion in MUCs
    @Override
    public boolean onTabPressed(boolean repeated) {
        if (conversation == null || conversation.getMode() == Conversation.MODE_SINGLE) {
            return false;
        }
        if (repeated) {
            completionIndex++;
        } else {
            lastCompletionLength = 0;
            completionIndex = 0;
            String content = mEditMessage.getText().toString();
            lastCompletionCursor = mEditMessage.getSelectionEnd();
            int start = lastCompletionCursor > 0 ? content.lastIndexOf(" ",lastCompletionCursor-1) + 1 : 0;
            firstWord = start == 0;
            incomplete = content.substring(start,lastCompletionCursor);
        }
        List<String> completions = new ArrayList<>();
        for(MucOptions.User user : conversation.getMucOptions().getUsers()) {
            if (user.getName().startsWith(incomplete)) {
                completions.add(user.getName()+(firstWord ? ": " : " "));
            }
        }
        Collections.sort(completions);
        if (completions.size() > completionIndex) {
            String completion = completions.get(completionIndex).substring(incomplete.length());
            mEditMessage.getEditableText().delete(lastCompletionCursor,lastCompletionCursor + lastCompletionLength);
            mEditMessage.getEditableText().insert(lastCompletionCursor, completion);
            lastCompletionLength = completion.length();
        } else {
            completionIndex = -1;
            mEditMessage.getEditableText().delete(lastCompletionCursor,lastCompletionCursor + lastCompletionLength);
            lastCompletionLength = 0;
        }
        return true;
    }

    // Methods to send different types of messages
    protected void sendPlainTextMessage(Message message) {
        ((ConversationActivity) getActivity()).xmppConnectionService.sendMessage(message);
        messageSent();
    }

    protected void sendPgpMessage(final Message message) {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        final Contact contact = message.getConversation().getContact();

        if (!activity.hasPgp()) {
            activity.showInstallPgpDialog();
            return;
        }

        if (conversation.getAccount().getPgpSignature() == null) {
            activity.announcePgp(conversation.getAccount(), conversation, activity.onOpenPGPKeyPublished);
            return;
        }

        if (conversation.getMode() == Conversation.MODE_SINGLE) {
            if (contact.getPgpKeyId() != 0) {
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
                            public void error(Exception e) {
                                Toast.makeText(activity, R.string.encryption_error, Toast.LENGTH_SHORT).show();
                            }
                        });
            } else {
                Toast.makeText(activity, R.string.no_pgp_key, Toast.LENGTH_SHORT).show();
            }
        } else {
            // Handle group chat encryption here
        }
    }

    protected void sendAxolotlMessage(Message message) {
        // Implementation for sending Axolotl (Signal Protocol) encrypted messages
    }

    protected void sendOtrMessage(Message message) {
        // Implementation for sending OTR (Off-the-Record Messaging) encrypted messages
    }

    private void messageSent() {
        mEditMessage.setText("");
        // Additional actions after a message is sent can be added here
    }
}