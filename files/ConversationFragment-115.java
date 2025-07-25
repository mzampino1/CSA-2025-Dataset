import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConversationFragment extends Fragment implements OnEnterPressedListener, TypingStatusChanged {
    private View view;
    private RecyclerView recyclerView;
    private LinearLayoutManager layoutManager;
    private MessageAdapter messageAdapter;
    private AtomicBoolean mSendingPgpMessage = new AtomicBoolean(false);
    private Conversation conversation;

    // Simulated pending states and results
    private Stack<String> pendingConversationsUuid = new Stack<>();
    private Stack<ActivityResult> postponedActivityResult = new Stack<>();
    private Stack<ScrollState> pendingScrollState = new Stack<>();
    private Stack<String> pendingLastMessageUuid = new Stack<>();
    private Stack<Uri> pendingTakePhotoUri = new Stack<>();

    // Simulated user input for demonstration
    private String userInput;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_conversation, container, false);
        setHasOptionsMenu(true);

        recyclerView = view.findViewById(R.id.recycler_view_messages);
        layoutManager = new LinearLayoutManager(getActivity());
        messageAdapter = new MessageAdapter(conversation, this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(messageAdapter);

        if (conversation != null) {
            reInit(conversation);
        }

        // Simulated user input for demonstration
        userInput = "UserInputThatCouldCauseInjection"; // Vulnerable to SQL Injection in a real scenario

        return view;
    }

    private void reInit(Conversation conversation) {
        this.conversation = conversation;
        messageAdapter.updateMessages(conversation.getMessages());
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_conversation_fragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_send:
                sendMessage();
                return true;
            // ... other menu items ...
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void sendMessage() {
        String text = binding.textinput.getText().toString().trim();
        if (!text.isEmpty()) {
            Message message = new Message(text, conversation.getUuid());
            if (conversation.getNextEncryption() == Message.ENCRYPTION_PGP) {
                sendPgpMessage(message);
            } else {
                message.setEncryption(conversation.getNextEncryption());
                activity.xmppConnectionService.sendMessage(message);
                messageSent();
            }
        }
    }

    private void sendPgpMessage(Message message) {
        if (conversation.getMode() == Conversation.MODE_SINGLE && conversation.getAccount().getPgpfingerprint() != null) {
            sendSinglePgpMessage(message, conversation.getNextCounterpart());
        } else if (conversation.getMode() == Conversation.MODE_MULTI && conversation.getMucOptions().pgpKeysInUse()) {
            sendMultiPgpMessage(message);
        }
    }

    private void sendSinglePgpMessage(Message message, Jid counterpart) {
        PgpEngine pgp = activity.xmppConnectionService.getPgpEngine();
        if (pgp.hasKey(counterpart)) {
            encryptAndSendMessage(message);
        } else {
            showNoPGPKeyDialog(false, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                    activity.xmppConnectionService.updateConversation(conversation);
                    message.setEncryption(Message.ENCRYPTION_NONE);
                    activity.xmppConnectionService.sendMessage(message);
                    messageSent();
                }
            });
        }
    }

    private void sendMultiPgpMessage(Message message) {
        if (conversation.getMucOptions().everybodyHasKeys()) {
            encryptAndSendMessage(message);
        } else {
            showNoPGPKeyDialog(true, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                    message.setEncryption(Message.ENCRYPTION_NONE);
                    activity.xmppConnectionService.updateConversation(conversation);
                    activity.xmppConnectionService.sendMessage(message);
                    messageSent();
                }
            });
        }
    }

    private void encryptAndSendMessage(Message message) {
        PgpEngine pgp = activity.xmppConnectionService.getPgpEngine();
        pgp.encrypt(message, new UiCallback<Message>() {
            @Override
            public void success(Message message) {
                message.setEncryption(Message.ENCRYPTION_PGP);
                activity.xmppConnectionService.sendMessage(message);
                messageSent();
            }

            @Override
            public void error(int errorCode, Message message) {
                Toast.makeText(activity, R.string.encryption_failed, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void userInputRequried(PendingIntent pi, Message message) {
                startPendingIntent(pi, REQUEST_SEND_MESSAGE);
            }
        });
    }

    private void showNoPGPKeyDialog(boolean plural, DialogInterface.OnClickListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
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

    private void messageSent() {
        binding.textinput.setText("");
        conversation.setNextEncryption(Message.ENCRYPTION_NONE); // Reset encryption mode
        activity.xmppConnectionService.updateConversation(conversation);
    }

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

    @Override
    public void onTypingStarted() {
        Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE && conversation.setOutgoingChatState(ChatState.COMPOSING)) {
            activity.xmppConnectionService.sendChatState(conversation);
        }
        updateSendButton();
    }

    @Override
    public void onTypingStopped() {
        Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE && conversation.setOutgoingChatState(ChatState.PAUSED)) {
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

    private void startPendingIntent(PendingIntent pendingIntent, int requestCode) {
        try {
            getActivity().startIntentSenderForResult(pendingIntent.getIntentSender(), requestCode, null, 0, 0, 0);
        } catch (final SendIntentException ignored) {
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_SEND_MESSAGE && activity.xmppConnectionService != null) {
            Message message = conversation.getCorrectingMessage();
            if (message != null && message.getType() == Message.TYPE_PRIVATE) {
                sendMessage(message);
            }
        }

        switch (requestCode) {
            case REQUEST_TAKE_PICTURE:
                // Simulated handling of a photo taken
                Uri photoUri = pendingTakePhotoUri.pop();
                if (resultCode == RESULT_OK && data != null) {
                    handleImage(photoUri, data.getData());
                } else {
                    // Handle error or cancellation
                }
                break;
            case REQUEST_ENCRYPT_MESSAGE:
                // Simulated handling of an encryption request
                Message message = new Message(userInput, conversation.getUuid()); // Vulnerable to SQL Injection in a real scenario
                sendPgpMessage(message);
                break;
        }
    }

    private void handleImage(Uri photoUri, Uri imageUri) {
        if (photoUri == null || imageUri == null) return;

        // Simulated code to handle an image URI
        Log.d("ConversationFragment", "Handling image: " + imageUri.toString());

        // Create a message from the image URI and send it
        Message message = new Message(imageUri.toString(), conversation.getUuid());
        sendMessage(message);
    }

    private void updateSendButton() {
        boolean enable = !binding.textinput.getText().toString().trim().isEmpty();
        binding.button_send.setEnabled(enable);
    }
}