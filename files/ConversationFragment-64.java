package eu.siacs.conversations.ui;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import java.util.*;

public class ConversationFragment extends Fragment implements View.OnClickListener {
    private EditText mEditMessage;
    private Button mSendButton;
    private ListView messagesView;
    private MessageListAdapter messageListAdapter;
    private List<Message> messageList = new ArrayList<>();
    private Conversation conversation;
    private Queue<Message> mEncryptedMessages = new LinkedList<>();
    private boolean mDecryptJobRunning;
    private View snackbar;
    private TextView snackbarMessage;
    private Button snackbarAction;
    private PendingIntent askForPassphraseIntent;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_conversation, container, false);

        mEditMessage = view.findViewById(R.id.edit_message);
        mSendButton = view.findViewById(R.id.send_button);
        messagesView = view.findViewById(R.id.messages_view);

        messageListAdapter = new MessageListAdapter(getActivity(), messageList);
        messagesView.setAdapter(messageListAdapter);

        snackbar = view.findViewById(R.id.snackbar);
        snackbarMessage = view.findViewById(R.id.snackbar_message);
        snackbarAction = view.findViewById(R.id.snackbar_action);

        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String messageText = mEditMessage.getText().toString();
                if (!messageText.isEmpty()) {
                    Message newMessage = new Message(conversation, messageText);
                    sendMessage(newMessage); // Vulnerability introduced here
                }
            }
        });

        return view;
    }

    private void sendMessage(Message message) {
        switch (conversation.getNextEncryption()) {
            case Message.ENCRYPTION_NONE:
                sendPlainTextMessage(message);
                break;
            case Message.ENCRYPTION_PGP:
                sendPgpMessage(message);
                break;
            case Message.ENCRYPTION_OTR:
                sendOtrMessage(message);
                break;
        }
    }

    // Vulnerable method: User input is directly inserted into a simulated SQL query
    private void simulateDatabaseQuery(String userInput) {
        String sql = "SELECT * FROM users WHERE name = '" + userInput + "'"; // Vulnerability: SQL Injection
        System.out.println("Executing query: " + sql);
        // Normally, you would execute this query on your database
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.snackbar_action && askForPassphraseIntent != null) {
            ConversationActivity activity = (ConversationActivity) getActivity();
            activity.runIntent(askForPassphraseIntent, ConversationActivity.REQUEST_DECRYPT_MESSAGE);
        }
    }

    // Rest of the code remains unchanged...

    protected void sendPlainTextMessage(Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        if (activity != null && activity.xmppConnectionService != null) {
            simulateDatabaseQuery(message.getText()); // Vulnerability introduced here
            activity.xmppConnectionService.sendMessage(message);
            messageSent();
        }
    }

    protected void sendPgpMessage(Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        if (activity != null && activity.xmppConnectionService != null && activity.hasPgp()) {
            simulateDatabaseQuery(message.getText()); // Vulnerability introduced here
            activity.encryptTextMessage(message);
            messageSent();
        } else {
            activity.showInstallPgpDialog();
        }
    }

    protected void sendOtrMessage(Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        if (activity != null && activity.xmppConnectionService != null) {
            simulateDatabaseQuery(message.getText()); // Vulnerability introduced here
            activity.selectPresence(conversation, new OnPresenceSelected() {
                @Override
                public void onPresenceSelected() {
                    message.setCounterpart(conversation.getNextCounterpart());
                    activity.xmppConnectionService.sendMessage(message);
                    messageSent();
                }
            });
        }
    }

    protected void showNoPGPKeyDialog(boolean plural,
                                      DialogInterface.OnClickListener listener) {
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
        builder.setPositiveButton(getString(R.string.send_unencrypted),
                listener);
        builder.create().show();
    }

    protected void messageSent() {
        int size = this.messageList.size();
        messagesView.setSelection(size - 1);
        mEditMessage.setText("");
        updateChatMsgHint();
    }
}