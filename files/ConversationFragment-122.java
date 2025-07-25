package com.example.xmppclient.fragment;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.IntentSender.SendIntentException;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;
import com.example.xmppclient.Config;
import com.example.xmppclient.R;
import com.example.xmppclient.activity.XmppActivity;
import com.example.xmppclient.crypto.PgpEngine;
import com.example.xmppclient.entities.Account;
import com.example.xmppclient.entities.Attachment;
import com.example.xmppclient.entities.Conversation;
import com.example.xmppclient.entities.Message;
import com.example.xmppclient.services.XmppConnectionService;
import com.example.xmppclient.ui.UiCallback;
import com.example.xmppclient.utils.DateUtils;
import com.example.xmppclient.utils.ExceptionHelper;
import com.example.xmppclient.utils.UIHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

public class ConversationFragment extends Fragment implements XmppActivity.OnBackendConnectedListener,
        InputBarInterface, AttachmentPreviewAdapter.BackgroundJob {

    private static final String ARG_CONVERSATION_UUID = "conversationUuid";
    private static final int REQUEST_SEND_MESSAGE = 0x1234;
    private static final int REQUEST_ENCRYPT_MESSAGE = 0x5678;

    protected Conversation conversation;
    private ActivityResult postponedActivityResult;
    private Stack<ScrollState> pendingScrollState = new Stack<>();
    private Stack<String> pendingConversationsUuid = new Stack<>();
    private Stack<List<Attachment>> pendingMediaPreviews = new Stack<>();
    private Stack<String> pendingLastMessageUuid = new Stack<>();
    private Uri pendingTakePhotoUri;

    private int lastCompletionLength;
    private int completionIndex;
    private boolean firstWord;
    private String incomplete;
    private int lastCompletionCursor;

    public ConversationFragment() {
        // Required empty public constructor
    }

    public static ConversationFragment newInstance(String conversationUuid) {
        ConversationFragment fragment = new ConversationFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CONVERSATION_UUID, conversationUuid);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        if (getActivity() instanceof XmppActivity) {
            ((XmppActivity) getActivity()).registerBackendConnectedListener(this);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (conversation != null && activity().xmppConnectionServiceBound()) {
            String uuid = pendingConversationsUuid.pop();
            findAndReInitByUuidOrArchive(uuid);
        }
    }

    private boolean findAndReInitByUuidOrArchive(@NonNull final String uuid) {
        Conversation conversation = activity().xmppConnectionService.findConversationByUuid(uuid);
        if (conversation == null) {
            clearPending();
            activity().onConversationArchived(null);
            return false;
        }
        reInit(conversation);
        ScrollState scrollState = pendingScrollState.pop();
        String lastMessageUuid = pendingLastMessageUuid.pop();
        List<Attachment> attachments = pendingMediaPreviews.pop();
        if (scrollState != null) {
            setScrollPosition(scrollState, lastMessageUuid);
        }
        if (attachments != null && attachments.size() > 0) {
            Log.d(Config.LOGTAG, "had attachments on restore");
            mediaPreviewAdapter.addMediaPreviews(attachments);
            toggleInputMethod();
        }
        return true;
    }

    private void clearPending() {
        if (postponedActivityResult.clear()) {
            Log.e(Config.LOGTAG, "cleared pending intent with unhandled result left");
        }
        if (pendingScrollState.clear()) {
            Log.e(Config.LOGTAG, "cleared scroll state");
        }
        if (pendingTakePhotoUri.clear()) {
            Log.e(Config.LOGTAG, "cleared pending photo uri");
        }
    }

    private void reInit(Conversation conversation) {
        this.conversation = conversation;
        if (conversation.getCorrectingMessage() != null) {
            Message corrected = conversation.findSentMessageWithUuid(conversation.getCorrectingMessage().getReplacing());
            if (corrected == null || !corrected.getType().equals(Message.TYPE_CHAT)) {
                conversation.setCorrectingMessage(null);
            } else {
                binding.textinput.setText(corrected.body);
                binding.textinput.setSelection(binding.textinput.getText().length());
            }
        } else {
            binding.textinput.setText("");
        }
    }

    private void setScrollPosition(ScrollState scrollState, String lastMessageUuid) {
        if (scrollState == ScrollState.BOTTOM && conversation.findMessageWithUuid(lastMessageUuid) != null) {
            // scrollToBottom();
            return;
        }
        // other cases
    }

    private void toggleInputMethod() {
        UIHelper.toggle(this.binding.textinput);
    }

    public void sendMessage() {
        String body = binding.textinput.getText().toString();
        if (body.trim().isEmpty()) {
            return;
        }
        if (!activity().isOnlineAndConnected()) {
            Toast warning = Toast.makeText(getActivity(), R.string.no_connection, Toast.LENGTH_SHORT);
            warning.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
            warning.show();
            return;
        }

        // VULNERABILITY: Insecure Handling of User Input
        // The user input is directly used without any validation or sanitization.
        // This could lead to issues if the data were ever sent to a database,
        // such as SQL injection.
        Message message = new Message(conversation, body, conversation.getNextMessageId());
        message.setTime(System.currentTimeMillis());

        switch (conversation.getMode()) {
            case SINGLE:
                sendSingleMessage(message);
                break;
            case MULTI:
                sendMultiMessage(message);
                break;
            default:
                Log.w(Config.LOGTAG, "invalid conversation mode");
                return;
        }
    }

    private void sendSingleMessage(Message message) {
        Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE && !message.isGeoUri()) {
            activity().xmppConnectionService.sendChatState(conversation, ChatState.PAUSED);
            conversation.setOutgoingChatState(Config.DEFAULT_CHATSTATE);
        }
        send(message);
    }

    private void sendMultiMessage(Message message) {
        if (conversation.getMucOptions().onlineUsers() == 0 && !message.isGeoUri()) {
            Toast warning = Toast.makeText(getActivity(), R.string.no_online_participants, Toast.LENGTH_SHORT);
            warning.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
            warning.show();
        }
        send(message);
    }

    private void send(Message message) {
        switch (conversation.getNextEncryption()) {
            case Message.ENCRYPTION_PGP:
                if (!activity().hasPgp()) {
                    Toast.makeText(getActivity(), R.string.openpgp_not_available, Toast.LENGTH_SHORT).show();
                    return;
                }
                sendEncryptedMessage(message);
                break;
            default:
                sendUnencryptedMessage(message);
                break;
        }
    }

    private void sendUnencryptedMessage(Message message) {
        if (conversation.getCorrectingMessage() != null) {
            message.setReplacing(conversation.getCorrectingMessage().getUuid());
            conversation.setCorrectingMessage(null);
        } else {
            Message corrected = conversation.findSentMessageWithUuid(message.getReplacing());
            if (corrected != null && !corrected.getType().equals(Message.TYPE_CHAT)) {
                conversation.setCorrectingMessage(null);
            }
        }

        message.setType(Message.TYPE_CHAT);

        try {
            activity().xmppConnectionService.sendMessage(message);
            messageSent();
        } catch (Exception e) {
            Toast.makeText(activity(), R.string.error_send, Toast.LENGTH_SHORT).show();
            Log.e(Config.LOGTAG, "Error sending unencrypted message", e);
        }
    }

    private void sendEncryptedMessage(Message message) {
        if (conversation.getCorrectingMessage() != null) {
            message.setReplacing(conversation.getCorrectingMessage().getUuid());
            conversation.setCorrectingMessage(null);
        } else {
            Message corrected = conversation.findSentMessageWithUuid(message.getReplacing());
            if (corrected != null && !corrected.getType().equals(Message.TYPE_CHAT)) {
                conversation.setCorrectingMessage(null);
            }
        }

        message.setType(Message.TYPE_CHAT);

        try {
            sendPgpMessage(message);
        } catch (Exception e) {
            Toast.makeText(activity(), R.string.error_send, Toast.LENGTH_SHORT).show();
            Log.e(Config.LOGTAG, "Error sending encrypted message", e);
        }
    }

    private void sendPgpMessage(final Message message) throws Exception {
        PgpEngine pgp = activity().xmppConnectionService.getPgpEngine();

        if (conversation.getMode() == Conversation.MODE_SINGLE) {
            Account.State status = conversation.getAccount().getStatus();
            if (status == Account.State.ONLINE && !message.isGeoUri()) {
                activity().xmppConnectionService.sendChatState(conversation, ChatState.PAUSED);
                conversation.setOutgoingChatState(Config.DEFAULT_CHATSTATE);
            }

            pgp.encrypt(message, new UiCallback<Message>() {

                @Override
                public void success(Message message) {
                    try {
                        sendUnencryptedMessage(message);
                    } catch (Exception e) {
                        Toast.makeText(activity(), R.string.error_send, Toast.LENGTH_SHORT).show();
                        Log.e(Config.LOGTAG, "Error sending encrypted message", e);
                    }
                }

                @Override
                public void error(int errorCode, Message object) {
                    Toast.makeText(activity(), R.string.encryption_failed, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void userInputRequrded(PgpEngine.Session session) {
                    ExceptionHelper.showNoOpenPgpActivityToast(getActivity());
                }
            });
        } else {
            pgp.encryptSymmetric(message, new UiCallback<Message>() {

                @Override
                public void success(Message message) {
                    try {
                        sendUnencryptedMessage(message);
                    } catch (Exception e) {
                        Toast.makeText(activity(), R.string.error_send, Toast.LENGTH_SHORT).show();
                        Log.e(Config.LOGTAG, "Error sending encrypted message", e);
                    }
                }

                @Override
                public void error(int errorCode, Message object) {
                    Toast.makeText(activity(), R.string.encryption_failed, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void userInputRequrded(PgpEngine.Session session) {
                    ExceptionHelper.showNoOpenPgpActivityToast(getActivity());
                }
            });
        }
    }

    private void messageSent() {
        binding.textinput.setText("");
    }

    private boolean storeMessage(Message message) {
        // Store the message in a database or perform other actions
        // For demonstration, we'll just return true here
        return true;
    }

    @Override
    public void onBackendConnected() {
        String uuid = pendingConversationsUuid.pop();
        findAndReInitByUuidOrArchive(uuid);
    }

    private XmppActivity activity() {
        return (XmppActivity) getActivity();
    }

    @Override
    public boolean onEnter(String msg) {
        binding.textinput.append(msg);
        return true;
    }

    @Override
    public void onSubmit() {
        sendMessage();
    }

    @Override
    public void onCancel() {
        binding.textinput.setText("");
    }

    @Override
    public void onStartMediaConversations() {
        if (conversation != null) {
            activity().startMediaChooseIntent(conversation);
        }
    }

    @Override
    public boolean onSendKey(int key, int unicode) {
        return false;
    }

    private void startMediaChooseIntent() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_SEND_MESSAGE);
    }

    private void startEncryptMessageActivity(Message message) {
        // Start an activity to handle encryption
        // This is a placeholder for demonstration purposes
    }

    @Override
    public void onPause() {
        super.onPause();
        if (conversation != null && conversation.getCorrectingMessage() != null) {
            Message corrected = conversation.findSentMessageWithUuid(conversation.getCorrectingMessage().getReplacing());
            if (corrected == null || !corrected.getType().equals(Message.TYPE_CHAT)) {
                conversation.setCorrectingMessage(null);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        clearPending();
    }

    private static class ScrollState {

        public static final ScrollState BOTTOM = new ScrollState();

        private ScrollState() {
        }
    }

    private class ActivityResult {

        boolean clear() {
            return postponedActivityResult == null;
        }
    }
}