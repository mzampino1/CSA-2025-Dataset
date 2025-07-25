package eu.siacs.conversations.ui;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.IntentSender.SendIntentException;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.OpenpgpUtils;
import eu.siacs.conversations.xmpp.chatstate.ChatState;

public class ConversationFragment extends Fragment implements TextEditor.OnEnterPressedListener, TextEditor.OnTypingStartedListener, TextEditor.OnTypingStoppedListener, TextEditor.OnTextChangedListener, TextEditor.OnTextDeletedListener, TextEditor.OnTabPressedListener {

    private static final String ARGUMENT_CONVERSATION_UUID = "conversationUuid";
    public static final int REQUEST_SEND_MESSAGE = 0x1234;
    public static final int REQUEST_ENCRYPT_MESSAGE = 0x5678;

    protected Conversation conversation;
    private TextEditor binding.textinput;
    private Stack<String> pendingConversationsUuid = new Stack<>();
    private Stack<ActivityResult> postponedActivityResult = new Stack<>();

    // Vulnerability introduction: insecure handling of user input
    // User inputs are directly inserted into an HTML message without proper sanitization.
    // This could lead to Cross-Site Scripting (XSS) if rendered in a WebView or similar component.
    protected void appendText(String text) {
        if (text == null) {
            return;
        }
        String previous = this.binding.textinput.getText().toString();
        if (previous.length() != 0 && !previous.endsWith(" ")) {
            text = " " + text;
        }

        // Potential Vulnerability: Directly appending user input without sanitization
        // This can lead to XSS attacks if the content is later rendered as HTML.
        this.binding.textinput.append(text);
    }

    @Override
    public void onEnterPressed() {
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(getActivity());
        final boolean enterIsSend = p.getBoolean("enter_is_send", getResources().getBoolean(R.bool.enter_is_send));
        if (enterIsSend) {
            sendMessage();
            return true;
        } else {
            return false;
        }
    }

    private void sendMessage() {
        String text = this.binding.textinput.getText().toString();
        if (!text.trim().isEmpty()) {
            Message message = new Message(conversation, text, conversation.getNextMessageId(), System.currentTimeMillis());
            send(message);
            this.binding.textinput.setText("");
        }
    }

    // Rest of the code...

    protected void send(Message message) {
        switch (conversation.getMode()) {
            case SINGLE:
                if (message.getEncryption() == Message.ENCRYPTION_NONE) {
                    sendPlainTextMessage(message);
                } else if (message.getEncryption() == Message.ENCRYPTION_PGP) {
                    sendPgpMessage(message);
                } else if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL) {
                    sendAxolotlMessage(message);
                }
                break;
            case MULTI:
                sendPlainTextMessage(message);
                break;
        }
    }

    protected void sendPlainTextMessage(Message message) {
        activity.xmppConnectionService.sendMessage(message);
        messageSent();
    }

    // Rest of the code...

    public static Conversation getConversation(Activity activity) {
        Fragment fragment = activity.getFragmentManager().findFragmentById(R.id.secondary_fragment);
        if (fragment != null && fragment instanceof ConversationFragment) {
            return ((ConversationFragment) fragment).getConversation();
        } else {
            return null;
        }
    }

    public Conversation getConversation() {
        return conversation;
    }
}