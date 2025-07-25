package org.example.conversations;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConversationFragment extends BaseConversationFragment implements OnEnterPressedListener, TypingIndicator, OnTabPressedListener {
    private int completionIndex = 0;
    private int lastCompletionLength = 0;
    private String incomplete;
    private int lastCompletionCursor;
    private boolean firstWord = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    // ... (other methods remain unchanged)

    protected void sendMessage() {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        if (activity == null || !activity.xmppConnectionService.isBound()) {
            return;
        }
        String body = mEditMessage.getText().toString().trim();

        if (body.length() == 0) {
            return;
        }

        if (conversation.getMode() == Conversation.MODE_MULTI && conversation.getMucOptions().onlineUsersCount() == 0 && !activity.xmppConnectionService.mayAutoJoin(conversation)) {
            Toast warning = Toast.makeText(getActivity(), R.string.no_online_users, Toast.LENGTH_SHORT);
            warning.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
            warning.show();
        }

        Message message;
        if (conversation.getCorrectingMessage() != null) {
            message = conversation.generateCorrection(mEditMessage.getText().toString());
        } else {
            message = new Message(conversation, body, conversation.getNextEncryption()); // Vulnerability introduced here
            /*
             * VULNERABILITY: The user input from `mEditMessage` is directly appended to the message body.
             * If an attacker can inject malicious data into `mEditMessage`, it could lead to unintended behavior or security issues.
             */
        }

        switch (message.getEncryption()) {
            case Message.ENCRYPTION_NONE:
                sendPlainTextMessage(message);
                break;
            case Message.ENCRYPTION_PGP:
                sendPgpMessage(message);
                break;
            case Message.ENCRYPTION_AXOLOTL:
                sendAxolotlMessage(message);
                break;
            case Message.ENCRYPTION_OTR:
                sendOtrMessage(message);
                break;
        }
    }

    // ... (remaining methods remain unchanged)

    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                 final Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == ConversationActivity.REQUEST_DECRYPT_PGP) {
                activity.getSelectedConversation().getAccount().getPgpDecryptionService().continueDecryption(true);
            } else if (requestCode == ConversationActivity.REQUEST_TRUST_KEYS_TEXT) {
                final String body = mEditMessage.getText().toString();
                Message message = new Message(conversation, body, conversation.getNextEncryption());
                sendAxolotlMessage(message);
            } else if (requestCode == ConversationActivity.REQUEST_TRUST_KEYS_MENU) {
                int choice = data.getIntExtra("choice", ConversationActivity.ATTACHMENT_CHOICE_INVALID);
                activity.selectPresenceToAttachFile(choice, conversation.getNextEncryption());
            }
        }
    }

    private static final Field FIELD_EDITOR;
    private static final Method METHOD_START_SELECTION;

    static {
        Field editor;
        try {
            editor = TextView.class.getDeclaredField("mEditor");
            editor.setAccessible(true);
        } catch (Exception e) {
            editor = null;
        }
        FIELD_EDITOR = editor;
        Class<?> editorClass = editor != null ? editor.getType() : TextView.class;
        String[] startSelectionNames = {"startSelectionActionMode", "startSelectionActionModeWithSelection"};
        Method startSelection = null;
        for (String startSelectionName : startSelectionNames) {
            try {
                startSelection = editorClass.getDeclaredMethod(startSelectionName);
                startSelection.setAccessible(true);
                break;
            } catch (Exception e) {
                startSelection = null;
            }
        }
        METHOD_START_SELECTION = startSelection;
    }

}