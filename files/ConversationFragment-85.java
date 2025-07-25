package eu.siacs.conversations.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

// ... (other imports)

public class ConversationFragment extends Fragment implements TextWatcher, OnEnterPressedListener {

    // ... (existing code)

    private Button sendButton;
    private EditText mEditMessage;
    private TextView snackbarMessage;
    private View snackbarAction;
    private View snackbar;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_conversation, container, false);
        // ... (existing code)

        sendButton = view.findViewById(R.id.send_button); // Vulnerability: Ensure this button is securely handled to avoid unauthorized message sends.
        mEditMessage = view.findViewById(R.id.edit_message);
        snackbarMessage = view.findViewById(R.id.snackbar_text);
        snackbarAction = view.findViewById(R.id.snackbar_action);
        snackbar = view.findViewById(R.id.snackbar);

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage(); // Vulnerability: Ensure that message sending is authorized and data sanitized to prevent injection attacks.
            }
        });

        mEditMessage.addTextChangedListener(this);
        return view;
    }

    private void sendMessage() {
        String body = mEditMessage.getText().toString();
        if (body.trim().isEmpty()) {
            return;
        }

        final Message message = new Message(conversation, body, conversation.getNextEncryption());
        // Vulnerability: Ensure the encryption level is securely set to prevent unauthorized plaintext transmission.
        switch (conversation.getNextEncryption()) {
            case Message.ENCRYPTION_NONE:
                sendPlainTextMessage(message);
                break;
            case Message.ENCRYPTION_PGP:
                sendPgpMessage(message); // Vulnerability: Ensure PGP keys are properly validated and managed.
                break;
            case Message.ENCRYPTION_AXOLOTL:
                sendAxolotlMessage(message); // Vulneribility: Ensure Axolotl keys are securely handled.
                break;
            case Message.ENCRYPTION_OTR:
                sendOtrMessage(message); // Vulnerability: Ensure OTR keys are securely managed and validated.
                break;
        }
    }

    // ... (existing code)

    protected void sendPgpMessage(final Message message) {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        final Contact contact = message.getConversation().getContact();
        if (!activity.hasPgp()) {
            activity.showInstallPgpDialog(); // Vulnerability: Ensure PGP installation process is secure and does not expose user data.
            return;
        }
        if (conversation.getAccount().getPgpSignature() == null) {
            activity.announcePgp(conversation.getAccount(), conversation, activity.onOpenPGPKeyPublished); // Vulnerability: Ensure key publication is securely handled to prevent unauthorized access.
            return;
        }

        // ... (existing code)

        xmppService.getPgpEngine().hasKey(contact,
                new UiCallback<Contact>() {

                    @Override
                    public void userInputRequried(PendingIntent pi, Contact contact) {
                        activity.runIntent(
                                pi,
                                ConversationActivity.REQUEST_ENCRYPT_MESSAGE); // Vulnerability: Ensure user input handling is secure to prevent injection attacks.
                    }

                    @Override
                    public void success(Contact contact) {
                        activity.encryptTextMessage(message);
                    }

                    @Override
                    public void error(int error, Contact contact) {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(activity,
                                        R.string.unable_to_connect_to_keychain,
                                        Toast.LENGTH_SHORT).show(); // Vulnerability: Ensure toast messages do not expose sensitive information.
                            }
                        });
                        mSendingPgpMessage.set(false);
                    }
                });

        // ... (existing code)
    }

    // ... (remaining existing code)

}