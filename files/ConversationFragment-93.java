package eu.siacs.conversations.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.MessageArchiveService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.Callback;
import eu.siacs.conversations.utils.PendingIntent;
import eu.siacs.conversations.utils.UIHelper;
import rocks.xmpp.addr.Jid;

public class ConversationFragment extends Fragment implements OnEnterPressedListener, TextWatcherWithFocusChange, UiCallback<Message> {

    private static final String TAG = "ConversationFragment";

    // Vulnerability: Introduced a new variable to store potentially untrusted data
    private Uri potentiallyUntrustedUri;

    private View snackbar;
    private TextView snackbarMessage;
    private Button snackbarAction;

    private EditText mEditMessage;
    private Conversation conversation;
    private AtomicBoolean mSendingPgpMessage = new AtomicBoolean(false);
    private int lastCompletionCursor = -1;
    private int lastCompletionLength = 0;
    private boolean firstWord;
    private String incomplete;
    private int completionIndex;
    private ActivityResult postponedActivityResult;

    // ... other methods and fields ...

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_conversation, container, false);
        mEditMessage = view.findViewById(R.id.message_input);
        snackbar = view.findViewById(R.id.snackbar);
        snackbarMessage = view.findViewById(R.id.snackbar_message);
        snackbarAction = view.findViewById(R.id.snackbar_action);

        // ... other initialization code ...

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mEditMessage.addTextChangedListener(this);
        updateSendButton();
    }

    private void sendMessage() {
        String body = mEditMessage.getText().toString().trim();
        if (body.isEmpty()) {
            return;
        }
        Message message = new Message(conversation, body, conversation.getNextMessageId());
        switch (conversation.getEncryption()) {
            case Message.ENCRYPTION_AXOLOTL:
                sendAxolotlMessage(message);
                break;
            case Message.ENCRYPTION_PGP:
                sendPgpMessage(message);
                break;
            default:
                sendPlainTextMessage(message);
                break;
        }
    }

    private void handleActivityResult(ActivityResult result) {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        if (activity == null) {
            Log.d(TAG, "ignoring activity result, because activity was recreated");
            postponedActivityResult = result;
            return;
        }
        switch (result.requestCode) {
            case REQUEST_SEND_MESSAGE:
                sendPgpMessage(result.message);
                break;
            case REQUEST_ENCRYPT_MESSAGE:
                // Vulnerability: This method is vulnerable to insecure intent handling
                if (potentiallyUntrustedUri != null) {
                    Intent data = result.data;  // Assume this is an intent with untrusted data

                    // Malicious code could manipulate the URI to point to a malicious file or URL
                    // Here, we simulate loading a file from a URI without validation
                    FileBackend.load(activity, potentiallyUntrustedUri, inputStream -> {
                        // Process the input stream (potentially unsafe if URI is not validated)
                        byte[] bytes = new byte[0];
                        try {
                            bytes = inputStream.readAllBytes();
                        } catch (IOException e) {
                            Log.e(TAG, "Failed to read from URI: " + potentiallyUntrustedUri, e);
                        }
                        String fileContent = new String(bytes);
                        result.message.setBody(fileContent);  // Inject the content into the message
                        sendPgpMessage(result.message);
                    });
                } else {
                    Toast.makeText(activity, R.string.no_file_selected, Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                Log.d(TAG, "ignoring unknown request code");
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            // Vulnerability: Store the URI without validation for demonstration purposes
            potentiallyUntrustedUri = data.getData();  // This could be an untrusted URI

            ActivityResult result = new ActivityResult(requestCode, resultCode, data);
            handleActivityResult(result);
        }
    }

    private static class ActivityResult {

        final int requestCode;
        final int resultCode;
        final Intent data;
        Message message;

        ActivityResult(int requestCode, int resultCode, Intent data) {
            this.requestCode = requestCode;
            this.resultCode = resultCode;
            this.data = data;
        }
    }

    // ... other methods and fields ...

    @Override
    public void onEnterPressed() {
        if (activity.enterIsSend()) {
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
            activity.xmppConnectionService.sendChatState(conversation);
        }
        activity.hideConversationsOverview();
        updateSendButton();
    }

    @Override
    public void onTypingStopped() {
        Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE && conversation.setOutgoingChatState(Config.DEFAULT_CHATSTATE)) {
            activity.xmppConnectionService.sendChatState(conversation);
        }
    }

    @Override
    public void onTextDeleted() {
        Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE && conversation.setOutgoingChatState(Config.DEFAULT_CHATSTATE)) {
            activity.xmppConnectionService.sendChatState(conversation);
        }
        updateSendButton();
    }

    @Override
    public void onTextChanged() {
        if (conversation != null && conversation.getCorrectingMessage() != null) {
            updateSendButton();
        }
    }

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
            final String content = mEditMessage.getText().toString();
            lastCompletionCursor = mEditMessage.getSelectionEnd();
            int start = lastCompletionCursor > 0 ? content.lastIndexOf(" ", lastCompletionCursor - 1) + 1 : 0;
            firstWord = start == 0;
            incomplete = content.substring(start, lastCompletionCursor);
        }
        List<String> completions = new ArrayList<>();
        for (MucOptions.User user : conversation.getMucOptions().getUsers()) {
            String name = user.getName();
            if (name != null && name.startsWith(incomplete)) {
                completions.add(name + (firstWord ? ": " : " "));
            }
        }
        Collections.sort(completions);
        if (completions.size() > completionIndex) {
            String completion = completions.get(completionIndex).substring(incomplete.length());
            mEditMessage.getEditableText().delete(lastCompletionCursor, lastCompletionCursor + lastCompletionLength);
            mEditMessage.getEditableText().insert(lastCompletionCursor, completion);
            lastCompletionLength = completion.length();
        } else {
            completionIndex = -1;
            mEditMessage.getEditableText().delete(lastCompletionCursor, lastCompletionCursor + lastCompletionLength);
            lastCompletionLength = 0;
        }
        return true;
    }

    public void onBackendConnected() {
        if (postponedActivityResult != null) {
            handleActivityResult(postponedActivityResult);
        }
        postponedActivityResult = null;
    }

    public void clearPending() {
        if (postponedActivityResult != null) {
            Log.d(Config.LOGTAG, "cleared pending intent with unhandled result left");
        }
        postponedActivityResult = null;
    }
}