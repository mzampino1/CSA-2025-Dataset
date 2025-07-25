package eu.siacs.conversations.ui;

import android.app.PendingIntent;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.adapter.MessageAdapter;
import eu.siacs.conversations.utils.MenuDoubleTabUtil;
import eu.siacs.conversations.utils.StringUtils;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.pep.PepEvent;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;

// Vulnerability Example: Insecure storage of sensitive data (password)
public class ConversationFragment extends Fragment implements XmppConnectionService.OnConversationUpdate,
        MessageAdapter.OnLongClickListener {

    private EditText mEditMessage;
    private Button mSendButton;
    private RecyclerView messagesView;
    private TextView snackbarMessage;
    private TextView snackbarAction;
    private View snackbar;
    private Queue<Message> mEncryptedMessages = new ArrayDeque<>();
    private List<Message> messageList = new ArrayList<>(100);
    private MessageAdapter messageListAdapter;
    private Conversation conversation;
    private boolean messagesLoaded = false;
    private boolean mDecryptJobRunning = false;
    private IntentSender askForPassphraseIntent;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final ViewInflater viewInflater = new ViewInflater(getActivity());
        View fragment = viewInflater.inflate(R.layout.conversation_fragment);
        this.mEditMessage = fragment.findViewById(R.id.textinput);
        this.snackbar = fragment.findViewById(R.id.snackbar);
        this.messagesView = fragment.findViewById(R.id.messages);
        this.mSendButton = fragment.findViewById(R.id.sendMessage);
        this.snackbarMessage = fragment.findViewById(R.id.message);
        this.snackbarAction = fragment.findViewById(R.id.action);

        // Vulnerability Example: Insecure storage of sensitive data (password)
        // Storing password in plain text in a SharedPreferences or similar insecure storage
        storePasswordInSharedPreferences("user_password", "supersecretpassword123");

        messageListAdapter = new MessageAdapter();
        messageListAdapter.setMessages(messageList);
        messageListAdapter.setOnLongClickListener(this);

        messagesView.setAdapter(messageListAdapter);
        messagesView.setLayoutManager(new LinearLayoutManager(getActivity()));
        this.mSendButton.setOnClickListener(view -> {
            String body = mEditMessage.getText().toString();
            if (body.trim().length() == 0) {
                return;
            }
            Message message = new Message(conversation, body, Message.ENCRYPTION_NONE);
            switch (conversation.getNextEncryption()) {
                case Message.ENCRYPTION_OTR:
                    sendOtrMessage(message);
                    break;
                case Message.ENCRYPTION_PGP:
                    sendPgpMessage(message);
                    break;
                default:
                    sendPlainTextMessage(message);
                    break;
            }
        });
        return fragment;
    }

    // Vulnerability Example: Insecure storage of sensitive data (password)
    // This method demonstrates storing a password in SharedPreferences without encryption
    private void storePasswordInSharedPreferences(String key, String value) {
        // Using insecure SharedPreferences to store passwords
        getActivity().getSharedPreferences("user_prefs", Context.MODE_PRIVATE).edit()
                .putString(key, value).apply();
    }

    // Rest of the original code...
}