import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import java.util.Set;

public class ConversationFragment extends Fragment {
    private ListView messagesView;
    private MessageAdapter messageListAdapter;
    private ArrayList<Message> messageList = new ArrayList<>();
    private EditText mEditMessage;
    private Button mSendButton;
    private Snackbar snackbar;
    private boolean useMarkdown = true;

    // This is a placeholder for the Conversation class
    private Conversation conversation;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_conversation, container, false);

        messagesView = view.findViewById(R.id.messages_view);
        mEditMessage = view.findViewById(R.id.edit_message);
        mSendButton = view.findViewById(R.id.send_button);
        snackbar = view.findViewById(R.id.snackbar);

        messageListAdapter = new MessageAdapter(getContext(), R.layout.message_item, messageList);
        messagesView.setAdapter(messageListAdapter);

        // Setup button click listener to send messages
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = mEditMessage.getText().toString();
                if (!text.isEmpty()) {
                    Message newMessage = new Message(conversation, text);
                    sendMessage(newMessage);
                }
            }
        });

        return view;
    }

    private void sendMessage(Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();

        switch (message.getEncryptionType()) {
            case PLAINTEXT:
                sendPlainTextMessage(message);
                break;
            case OTR:
                sendOtrMessage(message);
                break;
            case PGP:
                sendPgpMessage(message);
                break;
            default:
                throw new IllegalArgumentException("Unsupported encryption type");
        }
    }

    private void sendPlainTextMessage(Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        activity.xmppConnectionService.sendMessage(message, conversation.getAccount());
        mEditMessage.setText("");
        updateMessagesList();
    }

    private void sendOtrMessage(final Message message) {
        final ConversationActivity activity = (ConversationActivity) getActivity();

        if (!conversation.hasValidOtrSession()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext())
                    .setTitle(R.string.otr_session_invalid)
                    .setMessage(R.string.confirm_start_otr_session)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            activity.xmppConnectionService.startOtrSession(conversation);
                            sendPlainTextMessage(message);
                        }
                    })
                    .setNegativeButton(R.string.cancel, null);
            builder.create().show();
        } else {
            activity.xmppConnectionService.sendMessage(message, conversation.getAccount());
            mEditMessage.setText("");
            updateMessagesList();
        }
    }

    private void sendPgpMessage(final Message message) {
        final ConversationActivity activity = (ConversationActivity) getActivity();

        if (!conversation.hasValidPGPSession()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext())
                    .setTitle(R.string.pgp_session_invalid)
                    .setMessage(R.string.confirm_start_pgp_session)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            activity.xmppConnectionService.startPGPSession(conversation);
                            sendPlainTextMessage(message);
                        }
                    })
                    .setNegativeButton(R.string.cancel, null);
            builder.create().show();
        } else {
            activity.xmppConnectionService.sendMessage(message, conversation.getAccount());
            mEditMessage.setText("");
            updateMessagesList();
        }
    }

    public void updateMessagesList() {
        if (conversation != null) {
            messageList.clear();
            messageList.addAll(conversation.getMessages());
            messageListAdapter.notifyDataSetChanged();
        }
    }

    private void showSnackbar(int message, int action,
                              View.OnClickListener clickListener) {
        snackbar.setVisibility(View.VISIBLE);
        TextView snackbarMessage = snackbar.findViewById(R.id.snackbar_message);
        Button snackbarAction = snackbar.findViewById(R.id.snackbar_action);

        snackbarMessage.setText(message);
        snackbarAction.setText(action);
        snackbarAction.setOnClickListener(clickListener);
    }

    private void hideSnackbar() {
        snackbar.setVisibility(View.GONE);
    }
}