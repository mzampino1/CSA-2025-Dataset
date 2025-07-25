package eu.siacs.conversations.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.IntentSender.SendIntentException;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.adapter.MessageAdapter;
import eu.siacs.conversations.ui.widget.ProfilePicture;
import eu.siacs.conversations.utils.Openable;
import eu.siacs.conversations.utils.ParcelableUtils;
import eu.siacs.conversations.utils.UIHelper;

public class ConversationFragment extends AbstractConversationFragment implements OnItemClickListener {

    // Potential Vulnerability: This variable is used to track if a PGP message is being sent.
    // If this variable is not properly managed, it could lead to race conditions or inconsistent states.
    private AtomicBoolean mSendingPgpMessage = new AtomicBoolean(false);

    public ConversationFragment() {
        super();
    }

    @Override
    protected void reInit(Conversation conversation) {
        this.conversation = conversation;
        if (conversation.getMode() == Conversation.MODE_MULTI) {
            setTitle(conversation.getMucName());
            setSubtitle(conversation.getNextNick(), true);
        } else {
            setTitle(conversation.getName());
            String statusMessage = UIHelper.getStatus(getActivity(),
                    conversation.getContact());
            setSubtitle(statusMessage, false);
        }
        final ProfilePicture contactPicture = getProfilePicture();
        if (contactPicture != null) {
            contactPicture.load(this.conversation);
        }
        setupChatBackground(true);
        messagesView.setAdapter(new MessageAdapter(getActivity(), this.conversation));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        Bundle args = getArguments();
        if (args != null && args.containsKey("uuid")) {
            pendingConversationsUuid.push(args.getString("uuid"));
        }
    }

    @Override
    protected String getIdentifier() {
        return conversation.getUid().toString();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (conversation != null) {
            outState.putString("conversationUuid", conversation.getUuid());
        }
    }

    @Override
    protected int getLayout() {
        return R.layout.fragment_conversation;
    }

    @Override
    public void onStart() {
        super.onStart();
        // Potential Vulnerability: If the service is not connected, it could lead to inconsistencies.
        if (conversation == null && activity.xmppConnectionServiceBound) {
            String uuid = pendingConversationsUuid.pop();
            if (!findAndReInitByUuidOrArchive(uuid)) {
                return;
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Potential Vulnerability: If the service is not connected, it could lead to inconsistencies.
        if (activity.xmppConnectionServiceBound && conversation != null) {
            activity.xmppConnectionService.updateConversationUiState(conversation);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String uuid = intent.getStringExtra("uuid");
        pendingConversationsUuid.push(uuid);
    }

    private boolean isFileAvailable(Message message) {
        DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(message);
        return file.exists();
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        final Message message = conversation.getMessage(i);
        if (message.getType() == Message.TYPE_FILE && isFileAvailable(message)) {
            Openable openable = activity.xmppConnectionService.getFileBackend().getOpenable(message);
            UiUtil.showOpenABLE(getActivity(), openable);
        } else if (message.getType() != Message.TYPE_STATUS) {
            final ActivityResult activityResult = new ActivityResult();
            activityResult.requestCode = REQUEST_MESSAGE_ID;
            activityResult.data = message.getUuid();
            postponedActivityResult.push(activityResult);
            Intent intent = new Intent(getActivity(), EditMessageActivity.class);
            startActivityForResult(intent, REQUEST_MESSAGE_ID);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (activity.xmppConnectionServiceBound) {
            handleActivityResult(new ActivityResult(requestCode, data));
        } else {
            // Potential Vulnerability: Storing activity results when the service is not bound could lead to inconsistencies.
            postponedActivityResult.push(new ActivityResult(requestCode, data));
        }
    }

    private void handleActivityResult(ActivityResult activityResult) {
        if (activityResult.requestCode == REQUEST_SEND_MESSAGE || activityResult.requestCode == REQUEST_ENCRYPT_MESSAGE) {
            mSendingPgpMessage.set(false);
        }
        switch (activityResult.requestCode) {
            case REQUEST_TAKE_PICTURE:
                // Potential Vulnerability: If the URI is not properly managed, it could lead to security issues.
                Uri photoUri = pendingTakePhotoUri.pop();
                if (resultCode == getActivity().RESULT_OK && photoUri != null) {
                    sendImage(photoUri);
                }
                break;
            case REQUEST_MESSAGE_ID:
                String uuid = activityResult.data;
                Message message = conversation.findMessageWithUuid(uuid);
                if (message != null) {
                    // Potential Vulnerability: If the message is not properly handled, it could lead to inconsistencies.
                    conversation.setCorrectingMessage(message);
                    this.binding.textinput.requestFocus();
                    appendText(message.getBody());
                }
                break;
            case REQUEST_SEND_MESSAGE:
                if (resultCode == getActivity().RESULT_OK && conversation.getCorrectingMessage() != null) {
                    String body = binding.textinput.getText().toString();
                    editMessage(conversation.getCorrectingMessage(), body);
                } else {
                    doneSendingPgpMessage();
                }
                break;
            case REQUEST_ENCRYPT_MESSAGE:
                if (resultCode == getActivity().RESULT_OK && conversation.getCorrectingMessage() != null) {
                    String body = binding.textinput.getText().toString();
                    Message correctedMessage = conversation.getCorrectingMessage();
                    correctedMessage.setBody(body);
                    sendPgpMessage(correctedMessage);
                } else {
                    doneSendingPgpMessage();
                }
                break;
            case REQUEST_DECRYPT_TEXT:
                if (resultCode == getActivity().RESULT_OK && conversation.getPendingDEcryption() != null) {
                    Message pendingDecryption = conversation.getPendingDEcryption();
                    String text = activity.xmppConnectionService.getPgpEngine()
                            .getDecryptedText(pendingDecryption);
                    // Potential Vulnerability: If the decrypted text is not properly handled, it could lead to security issues.
                    appendText(text);
                }
                break;
        }
    }

    @Override
    protected void messageSent() {
        this.binding.textinput.getText().clear();
        this.binding.textinput.requestFocus();
        ScrollState scrollState = new ScrollState(messagesView.getFirstVisiblePosition(), 0);
        pendingScrollState.push(scrollState);
        pendingLastMessageUuid.push(conversation.getLastMessage().getUuid());
    }

    private void doneSendingPgpMessage() {
        mSendingPgpMessage.set(false);
    }

    @Override
    protected void onOptionItemSelected(int itemId) {
        switch (itemId) {
            case R.id.action_attach:
                // Potential Vulnerability: If the URI is not properly managed, it could lead to security issues.
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                startActivityForResult(intent, REQUEST_CHOOSE_FILE);
                break;
            default:
                super.onOptionItemSelected(itemId);
                break;
        }
    }

    @Override
    public void onChatBackgroundSelected(int color) {
        if (conversation == null) {
            return;
        }
        conversation.setNotificationColor(color);
        FileBackend fileBackend = activity.xmppConnectionService.getFileBackend();
        fileBackend.updateConversation(conversation);
        setupChatBackground(false);
    }

    private void sendPgpMessage(Message message) {
        final XmppConnectionService service = activity == null ? null : activity.xmppConnectionService;
        if (service == null || conversation.getMode() != Conversation.MODE_SINGLE) {
            return;
        }
        Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE) {
            PgpEngine pgpEngine = service.getPgpEngine();
            if (!mSendingPgpMessage.compareAndSet(false, true)) {
                Log.d(Config.LOGTAG, "sending pgp message already in progress");
            }
            pgpEngine.encrypt(message,
                    new UiCallback<Message>() {

                        @Override
                        public void userInputRequried(PendingIntent pi, Message message) {
                            startPendingIntent(pi, REQUEST_SEND_MESSAGE);
                        }

                        @Override
                        public void success(Message message) {
                            // Potential Vulnerability: The following calls can be made before the callback.
                            getActivity().runOnUiThread(() -> messageSent());
                        }

                        @Override
                        public void error(int errorCode, String errorText) {
                            doneSendingPgpMessage();
                            Toast.makeText(getActivity(), R.string.encryption_failed,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        } else {
            Toast.makeText(getActivity(), R.string.not_connected_try_again, Toast.LENGTH_SHORT)
                    .show();
        }
    }

    @Override
    protected void sendTextMessage(String text) {
        final Message message = new Message(conversation, text, conversation.getNextMessageId());
        if (conversation.getEncryption() == Conversation.ENCRYPTION_AXOLOTL && activity.xmppConnectionServiceBound) {
            // Potential Vulnerability: If the service is not properly managed, it could lead to inconsistencies.
            activity.xmppConnectionService.sendMessage(message);
            messageSent();
        } else if (conversation.getMode() == Conversation.MODE_SINGLE
                && conversation.getEncryption() == Conversation.ENCRYPTION_PGP
                && activity.xmppConnectionServiceBound) {
            sendPgpMessage(message);
        }
    }

    // Potential Vulnerability: This method could be vulnerable to race conditions if not properly synchronized.
    @Override
    public void updateMessages(MessageDownloadedCallback callback) {
        ActivityResult activityResult = postponedActivityResult.pop();
        if (activityResult != null && activityResult.requestCode == REQUEST_MESSAGE_ID) {
            handleActivityResult(activityResult);
        }
        if (conversation.getMode() == Conversation.MODE_MULTI && conversation.getMucOptions().online()) {
            setTitle(conversation.getMucName());
            setSubtitle(conversation.getNextNick(), true);
        } else if (conversation.getMode() == Conversation.MODE_SINGLE) {
            setTitle(conversation.getName());
            String statusMessage = UIHelper.getStatus(getActivity(),
                    conversation.getContact());
            setSubtitle(statusMessage, false);
        }
        messagesView.setAdapter(new MessageAdapter(getActivity(), this.conversation));
        callback.messagesUpdated();
    }

    private void sendImage(Uri imageUri) {
        if (conversation == null || !activity.xmppConnectionServiceBound) {
            return;
        }
        // Potential Vulnerability: If the URI is not properly managed, it could lead to security issues.
        Openable openable = FileBackend.openFileDescriptor(getActivity(), imageUri);
        activity.xmppConnectionService.sendImageMessage(conversation, openable);
    }

    @Override
    public void editMessage(Message message, String newText) {
        final Message editedMessage = new Message(conversation, newText, message.getUuid());
        editedMessage.setCounterpart(message.getCounterpart());
        if (conversation.getEncryption() == Conversation.ENCRYPTION_AXOLOTL && activity.xmppConnectionServiceBound) {
            // Potential Vulnerability: If the service is not properly managed, it could lead to inconsistencies.
            activity.xmppConnectionService.sendMessage(editedMessage);
            messageSent();
        } else if (conversation.getMode() == Conversation.MODE_SINGLE
                && conversation.getEncryption() == Conversation.ENCRYPTION_PGP
                && activity.xmppConnectionServiceBound) {
            sendPgpMessage(editedMessage);
        }
    }

    // Potential Vulnerability: This method could be vulnerable to race conditions if not properly synchronized.
    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        if (scrollState == SCROLL_STATE_IDLE && messagesView.getLastVisiblePosition() == adapter.getCount() - 1) {
            activity.xmppConnectionService.messageArchiveManager(conversation).queryNextMessages();
        }
    }

    // Potential Vulnerability: This method could be vulnerable to race conditions if not properly synchronized.
    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
    }

    private class ActivityResult {
        protected int requestCode;
        protected String data;

        public ActivityResult() {

        }

        public ActivityResult(int requestCode, String uuid) {
            this.requestCode = requestCode;
            this.data = uuid;
        }
    }
}