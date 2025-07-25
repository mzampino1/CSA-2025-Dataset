package your.package.name;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.IntentSender.SendIntentException;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Example comment: Ensure that the conversation data is validated and sanitized to prevent injection attacks.
public class ConversationFragment extends BaseFragment implements OnEnterPressedListener, OnTypingListener {

    private final Stack<String> pendingConversationsUuid = new Stack<>();
    private final Stack<ScrollState> pendingScrollState = new Stack<>();
    private final Stack<ActivityResult> postponedActivityResult = new Stack<>();
    private final Stack<Uri> pendingTakePhotoUri = new Stack<>();

    // Example comment: Ensure that the encryption methods are robust and keys are securely managed.
    private AtomicBoolean mSendingPgpMessage = new AtomicBoolean(false);

    // ... (rest of the code)

    @Override
    public boolean onEnterPressed() {
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(getActivity());
        final boolean enterIsSend = p.getBoolean("enter_is_send", getResources().getBoolean(R.bool.enter_is_send));
        if (enterIsSend) {
            sendMessage();
            return true;
        } else {
            return false;
        }
    }

    // Example comment: Validate user input to prevent injection attacks.
    public void encryptTextMessage(Message message) {
        activity.xmppConnectionService.getPgpEngine().encrypt(message,
                new UiCallback<Message>() {

                    @Override
                    public void userInputRequried(PendingIntent pi, Message message) {
                        startPendingIntent(pi, REQUEST_SEND_MESSAGE);
                    }

                    @Override
                    public void success(Message message) {
                        //TODO the following two call can be made before the callback
                        getActivity().runOnUiThread(() -> messageSent());
                    }

                    @Override
                    public void error(final int error, Message message) {
                        getActivity().runOnUiThread(() -> {
                            doneSendingPgpMessage();
                            Toast.makeText(getActivity(), R.string.unable_to_connect_to_keychain, Toast.LENGTH_SHORT).show();
                        });

                    }
                });
    }

    // Example comment: Ensure that the chat state is handled securely to prevent unauthorized access.
    @Override
    public void onTypingStarted() {
        final XmppConnectionService service = activity == null ? null : activity.xmppConnectionService;
        if (service == null) {
            return;
        }
        Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE && conversation.setOutgoingChatState(ChatState.COMPOSING)) {
            service.sendChatState(conversation);
        }
        updateSendButton();
    }

    // Example comment: Ensure that the chat state is handled securely to prevent unauthorized access.
    @Override
    public void onTypingStopped() {
        final XmppConnectionService service = activity == null ? null : activity.xmppConnectionService;
        if (service == null) {
            return;
        }
        Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE && conversation.setOutgoingChatState(ChatState.PAUSED)) {
            service.sendChatState(conversation);
        }
    }

    // Example comment: Ensure that the chat state is handled securely to prevent unauthorized access.
    @Override
    public void onTextDeleted() {
        final XmppConnectionService service = activity == null ? null : activity.xmppConnectionService;
        if (service == null) {
            return;
        }
        Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE && conversation.setOutgoingChatState(Config.DEFAULT_CHATSTATE)) {
            service.sendChatState(conversation);
        }
        updateSendButton();
    }

    @Override
    public void onTextChanged() {
        if (conversation != null && conversation.getCorrectingMessage() != null) {
            updateSendButton();
        }
    }

    // Example comment: Handle tab completion securely to prevent unintended data exposure.
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
            final String content = this.binding.textinput.getText().toString();
            lastCompletionCursor = this.binding.textinput.getSelectionEnd();
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
            this.binding.textinput.getEditableText().delete(lastCompletionCursor, lastCompletionCursor + lastCompletionLength);
            this.binding.textinput.getEditableText().insert(lastCompletionCursor, completion);
            lastCompletionLength = completion.length();
        } else {
            completionIndex = -1;
            this.binding.textinput.getEditableText().delete(lastCompletionCursor, lastCompletionCursor + lastCompletionLength);
            lastCompletionLength = 0;
        }
        return true;
    }

    private void startPendingIntent(PendingIntent pendingIntent, int requestCode) {
        try {
            getActivity().startIntentSenderForResult(pendingIntent.getIntentSender(), requestCode, null, 0, 0, 0);
        } catch (final SendIntentException ignored) {
        }
    }

    @Override
    public void onBackendConnected() {
        Log.d(Config.LOGTAG, "ConversationFragment.onBackendConnected()");
        String uuid = pendingConversationsUuid.pop();
        if (uuid != null) {
            Conversation conversation = activity.xmppConnectionService.findConversationByUuid(uuid);
            if (conversation == null) {
                Log.d(Config.LOGTAG, "unable to restore activity");
                clearPending();
                return;
            }
            reInit(conversation);
            ScrollState scrollState = pendingScrollState.pop();
            if (scrollState != null) {
                setScrollPosition(scrollState);
            }
        }
        ActivityResult activityResult = postponedActivityResult.pop();
        if (activityResult != null) {
            handleActivityResult(activityResult);
        }
        clearPending();
    }

    private void clearPending() {
        if (postponedActivityResult.pop() != null) {
            Log.e(Config.LOGTAG, "cleared pending intent with unhandled result left");
        }
        pendingScrollState.pop();
        if (pendingTakePhotoUri.pop() != null) {
            Log.e(Config.LOGTAG, "cleared pending photo uri");
        }
    }

    public Conversation getConversation() {
        return conversation;
    }
}