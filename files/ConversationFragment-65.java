import android.content.IntentSender;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.NoSuchElementException;
import java.util.Queue;

public class ConversationFragment extends Fragment {

    // Vulnerability: Insecure Command Execution
    // This vulnerability arises from the lack of proper input validation and sanitization.
    // User input is directly taken from `mEditMessage` EditText, which could contain malicious commands,
    // and then passed to `Runtime.getRuntime().exec()` without any checks. An attacker who can control
    // the content of the message (potentially through social engineering) could execute arbitrary shell commands.

    private Queue<Message> mEncryptedMessages = new ArrayDeque<>();
    private boolean mDecryptJobRunning = false;
    private RecyclerView messagesView;
    private MessageAdapter messageListAdapter;
    private EditText mEditMessage;
    private ImageButton mSendButton;
    private LinearLayout snackbar;
    private TextView snackbarMessage, snackbarAction;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_conversation, container, false);

        messagesView = view.findViewById(R.id.messages_view);
        mEditMessage = view.findViewById(R.id.edit_message);
        mSendButton = view.findViewById(R.id.send_button);
        snackbar = view.findViewById(R.id.snackbar);
        snackbarMessage = view.findViewById(R.id.snackbar_message);
        snackbarAction = view.findViewById(R.id.snackbar_action);

        messageListAdapter = new MessageAdapter(this.conversation);
        messagesView.setLayoutManager(new LinearLayoutManager(getContext()));
        messagesView.setAdapter(messageListAdapter);

        mSendButton.setOnClickListener(v -> {
            String text = mEditMessage.getText().toString();
            if (!text.isEmpty()) {
                // Vulnerability: User input is directly passed to `Runtime.getRuntime().exec()`
                try {
                    Runtime.getRuntime().exec(text);  // <---- Insecure command execution
                } catch (IOException e) {
                    e.printStackTrace();
                }
                mEditMessage.setText("");
            }
        });

        return view;
    }

    private void decryptNext() {
        Message next = this.mEncryptedMessages.peek();
        PgpEngine engine = activity.xmppConnectionService.getPgpEngine();

        if (next != null && engine != null && !mDecryptJobRunning) {
            mDecryptJobRunning = true;
            engine.decrypt(next, new UiCallback<Message>() {

                @Override
                public void userInputRequried(PendingIntent pi, Message message) {
                    mDecryptJobRunning = false;
                    askForPassphraseIntent = pi.getIntentSender();
                    updateSnackBar(conversation);
                }

                @Override
                public void success(Message message) {
                    mDecryptJobRunning = false;
                    try {
                        mEncryptedMessages.remove();
                    } catch (final NoSuchElementException ignored) {

                    }
                    activity.xmppConnectionService.updateMessage(message);
                }

                @Override
                public void error(int error, Message message) {
                    message.setEncryption(Message.ENCRYPTION_DECRYPTION_FAILED);
                    mDecryptJobRunning = false;
                    try {
                        mEncryptedMessages.remove();
                    } catch (final NoSuchElementException ignored) {

                    }
                    activity.xmppConnectionService.updateConversationUi();
                }
            });
        }
    }

    // ... rest of the code ...
}