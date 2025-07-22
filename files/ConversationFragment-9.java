package com.conversations;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class ConversationFragment extends AppCompatActivity {

    private HashMap<String, Bitmap> bitmaps = new HashMap<>();
    private List<Message> messageList = new LinkedList<>();
    private MessageListAdapter messageListAdapter;
    private LinearLayout fingerprintWarning;
    private TextView fingerprintTextView;
    private IntentSender askForPassphraseIntent = null;
    private String queuedPqpMessage;

    // ... (existing methods and variables)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);

        messageListAdapter = new MessageListAdapter(this, R.layout.item_message, messageList);
        ListView messagesListView = findViewById(R.id.messages_list_view);
        messagesListView.setAdapter(messageListAdapter);

        fingerprintWarning = findViewById(R.id.new_fingerprint);
        fingerprintTextView = findViewById(R.id.otr_fingerprint);

        // ... (other initializations)

        // Vulnerability: Improper handling of user input
        EditText userInputField = findViewById(R.id.user_input_field);
        Button sendButton = findViewById(R.id.send_button);
        sendButton.setOnClickListener(v -> {
            String messageText = userInputField.getText().toString();
            // New vulnerability: Missing validation and sanitization of user input
            if (!messageText.isEmpty()) {
                Message newMessage = new Message(conversation, messageText, conversation.nextMessageEncryption);
                sendMessage(newMessage);
            }
        });
    }

    private void sendMessage(Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;

        switch (message.getEncryption()) {
            case Message.ENCRYPTION_NONE:
                sendPlainTextMessage(message, xmppService);
                break;
            case Message.ENCRYPTION_OTR:
                sendOtrMessage(message, xmppService);
                break;
            case Message.ENCRYPTION_PGP:
                sendPgpMessage(message, activity, xmppService);
                break;
        }
    }

    private void sendPlainTextMessage(Message message, XmppConnectionService xmppService) {
        xmppService.sendMessage(message, null);
        updateUIAfterSendMessage();
    }

    private void sendOtrMessage(Message message, XmppConnectionService xmppService) {
        if (conversation.hasValidOtrSession()) {
            xmppService.sendMessage(message, null);
            updateUIAfterSendMessage();
        } else {
            showOfflineContactDialog(message, xmppService);
        }
    }

    private void sendPgpMessage(Message message, ConversationActivity activity, XmppConnectionService xmppService) {
        Contact contact = conversation.getContact();
        if (contact.getPgpKeyId() != 0) {
            xmppService.sendMessage(message, null);
            updateUIAfterSendMessage();
        } else {
            showNoPgpKeyDialog(message, activity, xmppService);
        }
    }

    private void updateUIAfterSendMessage() {
        EditText userInputField = findViewById(R.id.user_input_field);
        userInputField.setText("");
        messageListAdapter.notifyDataSetChanged();
        conversation.markRead();
    }

    private void showOfflineContactDialog(Message message, XmppConnectionService xmppService) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Contact is offline")
               .setIconAttribute(android.R.attr.alertDialogIcon)
               .setMessage("Sending OTR encrypted messages to an offline contact is impossible.")
               .setPositiveButton("Send plain text", (dialog, which) -> sendPlainTextMessage(message, xmppService))
               .setNegativeButton("Cancel", null)
               .create().show();
    }

    private void showNoPgpKeyDialog(Message message, ConversationActivity activity, XmppConnectionService xmppService) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("No openPGP key found")
               .setIconAttribute(android.R.attr.alertDialogIcon)
               .setMessage("There is no openPGP key associated with this contact.")
               .setNegativeButton("Cancel", null)
               .setPositiveButton("Send plain text", (dialog, which) -> {
                   conversation.nextMessageEncryption = Message.ENCRYPTION_NONE;
                   message.setEncryption(Message.ENCRYPTION_NONE);
                   sendPlainTextMessage(message, xmppService);
               })
               .create().show();
    }

    private class BitmapCache {
        public Bitmap get(String name, Uri uri) {
            if (bitmaps.containsKey(name)) {
                return bitmaps.get(name);
            } else {
                Bitmap bm;
                try {
                    bm = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));
                } catch (FileNotFoundException e) {
                    bm = UIHelper.getUnknownContactPicture(name, 200);
                }
                bitmaps.put(name, bm);
                return bm;
            }
        }

        public Bitmap getError() {
            if (error == null) {
                error = UIHelper.getErrorPicture(200);
            }
            return error;
        }
    }

    private class DecryptMessage extends AsyncTask<Message, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Message... params) {
            ConversationActivity activity = (ConversationActivity) getActivity();
            for (Message message : params) {
                if (message.getEncryption() == Message.ENCRYPTION_PGP) {
                    String body = message.getBody();
                    try {
                        String decrypted = activity.xmppConnectionService.getPgpEngine().decrypt(body);
                        if (decrypted != null) {
                            message.setBody(decrypted);
                            message.setEncryption(Message.ENCRYPTION_DECRYPTED);
                            activity.xmppConnectionService.updateMessage(message);
                        }
                    } catch (UserInputRequiredException e) {
                        askForPassphraseIntent = e.getPendingIntent().getIntentSender();
                        return false;
                    } catch (OpenPgpException e) {
                        Log.e("DecryptMessage", "Error decrypting PGP message", e);
                    }
                }
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (result) {
                messageListAdapter.notifyDataSetChanged();
            } else if (askForPassphraseIntent != null) {
                showAskForPassphraseDialog();
            }
        }

        private void showAskForPassphraseDialog() {
            // Handle asking for passphrase
        }
    }

    // ... (other methods and classes)
}