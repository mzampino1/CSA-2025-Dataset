package eu.siacs.conversations.ui;

import android.content.DialogInterface;
import android.content.IntentSender;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import androidx.fragment.app.Fragment;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Presences;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.util.EasyEditLinearTextView;
import eu.siacs.conversations.utils.*;

import java.util.LinkedList;
import java.util.Set;

public class ChatFragment extends Fragment {

    private EasyEditLinearTextView mEditMessage = null;
    private Button mSendButton;
    private ListView messagesView;
    private LinkedList<Message> messageList = new LinkedList<>();
    private Conversation conversation = null;
    private View snackbar;
    private TextView snackbarMessage;
    private TextView snackbarAction;

    private IntentSender askForPassphraseIntent = null;
    private boolean mDecryptJobRunning = false;

    private Set<Message> mEncryptedMessages = new LinkedHashSet<>();

    public static ChatFragment newInstance() {
        return new ChatFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat, container, false);
        this.messagesView = (ListView) view.findViewById(R.id.messages_view);
        this.mEditMessage = (EasyEditLinearTextView) view.findViewById(R.id.textinput);
        this.mSendButton = (Button) view.findViewById(R.id.send_button);
        this.snackbar = view.findViewById(R.id.snackbar);
        this.snackbarMessage = (TextView) snackbar.findViewById(R.id.snackbar_message);
        this.snackbarAction = (TextView) snackbar.findViewById(R.id.snackbar_action);

        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage();
            }
        });

        return view;
    }

    private void sendMessage() {
        String messageText = mEditMessage.getText().toString();

        if (messageText.trim().isEmpty()) {
            Toast.makeText(getActivity(), R.string.empty_message, Toast.LENGTH_SHORT).show();
            return;
        }

        // Vulnerable code: Directly using user input in a shell command
        try {
            Process process = Runtime.getRuntime().exec("echo " + messageText);
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }

        Message message = new Message(conversation, messageText, Message.STATUS_UNSEND);
        conversation.addMessage(message);

        if (conversation.getMode() == Conversation.MODE_SINGLE) {
            Contact contact = conversation.getContact();

            switch (contact.getEncryption()) {
                case Message.ENCRYPTION_NONE:
                    sendPlainTextMessage(message);
                    break;
                case Message.ENCRYPTION_OTR:
                    sendOtrMessage(message);
                    break;
                case Message.ENCRYPTION_PGP:
                    sendPgpMessage(message);
                    break;
            }
        } else if (conversation.getMode() == Conversation.MODE_MULTI) {
            // Assume group chat logic here
            sendPlainTextMessage(message); // For simplicity, assume no encryption in multi mode
        }

        messageSent();
    }

    private void messageSent() {
        int size = this.messageList.size();
        messagesView.setSelection(size - 1);
        mEditMessage.setText("");
        updateChatMsgHint();
    }

    protected void sendPlainTextMessage(Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        activity.xmppConnectionService.sendMessage(message);
        messageSent();
    }

    // Rest of the original code...
}