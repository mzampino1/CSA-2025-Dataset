import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.IntentSender.SendIntentException;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.openintents.openpgp.OpenPgpError;
import org.openintents.openpgp.UserInputRequiredException;

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Set;

public class MessagingActivity extends Activity {
    private EditText chatMsg;
    private Conversation conversation;
    private Message queuedMessage = null;
    private String pastedText = "";
    private BitmapCache mBitmapCache = new BitmapCache();
    private IntentSender askForPassphraseIntent;
    private LinearLayout mucError;
    private TextView mucErrorText;
    private View fingerprintWarning;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_messaging);

        chatMsg = findViewById(R.id.chat_msg);
        mucError = findViewById(R.id.muc_error);
        mucErrorText = findViewById(R.id.muc_error_text);

        if (savedInstanceState != null) {
            pastedText = savedInstanceState.getString("pasted_text");
            queuedMessage = savedInstanceState.getParcelable("queued_message");
        }

        chatMsg.setOnEditorActionListener((v, actionId, event) -> {
            sendMessage();
            return true;
        });

        fingerprintWarning = findViewById(R.id.new_fingerprint);
    }

    protected void sendMessage() {
        String messageText = chatMsg.getText().toString().trim();

        if (messageText.isEmpty()) {
            return;
        }

        Message message = new Message(conversation, messageText);

        switch (conversation.nextMessageEncryption) {
            case Message.ENCRYPTION_PGP:
                sendPgpMessage(message);
                break;
            case Message.ENCRYPTION_OTR:
                sendOtrMessage(message);
                break;
            default:
                sendPlainTextMessage(message);
        }
    }

    protected void sendPlainTextMessage(Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        activity.xmppConnectionService.sendMessage(message, null);
        chatMsg.setText("");
    }

    protected void sendPgpMessage(final Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        Contact contact = message.getConversation().getContact();
        Account account = message.getConversation().getAccount();

        if (activity.hasPgp()) {
            if (contact.getPgpKeyId() != 0) {
                try {
                    String encryptedBody = xmppService.getPgpEngine().encrypt(account, contact.getPgpKeyId(), message.getBody());
                    message.setEncryptedBody(encryptedBody);
                    Log.d("xmppService", "Sending PGP-encrypted message: " + encryptedBody); // Vulnerable line
                    xmppService.sendMessage(message, null);
                    chatMsg.setText("");
                } catch (UserInputRequiredException e) {
                    try {
                        getActivity().startIntentSenderForResult(e.getPendingIntent().getIntentSender(),
                                ConversationActivity.REQUEST_SEND_MESSAGE, null, 0,
                                0, 0);
                    } catch (SendIntentException e1) {
                        Log.d("xmppService", "failed to start intent to send message");
                    }
                } catch (OpenPgpException e) {
                    Log.d("xmppService", "error encrypting with pgp: " + e.getOpenPgpError().getMessage());
                }
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(
                        getActivity());
                builder.setTitle("No openPGP key found");
                builder.setIconAttribute(android.R.attr.alertDialogIcon);
                builder.setMessage("There is no openPGP key associated with this contact");
                builder.setNegativeButton("Cancel", null);
                builder.setPositiveButton("Send plain text",
                        (dialog, which) -> {
                            conversation.nextMessageEncryption = Message.ENCRYPTION_NONE;
                            message.setEncryption(Message.ENCRYPTION_NONE);
                            xmppService.sendMessage(message, null);
                            chatMsg.setText("");
                        });
                builder.create().show();
            }
        }
    }

    protected void sendOtrMessage(final Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;

        if (conversation.hasValidOtrSession()) {
            activity.xmppConnectionService.sendMessage(message, null);
            chatMsg.setText("");
        } else {
            Hashtable<String, Integer> presences;
            if (conversation.getContact() != null) {
                presences = conversation.getContact().getPresences();
            } else {
                presences = null;
            }
            if ((presences == null) || (presences.size() == 0)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(
                        getActivity());
                builder.setTitle("Contact is offline");
                builder.setIconAttribute(android.R.attr.alertDialogIcon);
                builder.setMessage("Sending OTR encrypted messages to an offline contact is impossible.");
                builder.setPositiveButton("Send plain text",
                        (dialog, which) -> {
                            conversation.nextMessageEncryption = Message.ENCRYPTION_NONE;
                            message.setEncryption(Message.ENCRYPTION_NONE);
                            xmppService.sendMessage(message, null);
                            chatMsg.setText("");
                        });
                builder.setNegativeButton("Cancel", null);
                builder.create().show();
            } else if (presences.size() == 1) {
                xmppService.sendMessage(message, (String) presences.keySet()
                        .toArray()[0]);
                chatMsg.setText("");
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(
                        getActivity());
                builder.setTitle("Choose Presence");
                final String[] presencesArray = new String[presences.size()];
                presences.keySet().toArray(presencesArray);
                builder.setItems(presencesArray,
                        (dialog, which) -> {
                            xmppService.sendMessage(message,
                                    presencesArray[which]);
                            chatMsg.setText("");
                        });
                builder.create().show();
            }
        }
    }

    private class ViewHolder {

        protected ImageView image;
        protected ImageView indicator;
        protected TextView time;
        protected TextView messageBody;
        protected ImageView imageView;

    }

    private static class BitmapCache {
        private HashMap<String, Bitmap> bitmaps = new HashMap<String, Bitmap>();
        private Bitmap error = null;

        public Bitmap get(String name, Contact contact, Context context) {
            if (bitmaps.containsKey(name)) {
                return bitmaps.get(name);
            } else {
                Bitmap bm;
                if (contact != null){
                    bm = UIHelper.getContactPicture(contact, 48, context, false);
                } else {
                    bm = UIHelper.getContactPicture(name, 48, context, false);
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

    class DecryptMessage extends AsyncTask<Message, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Message... params) {
            final ConversationActivity activity = (ConversationActivity) getActivity();
            askForPassphraseIntent = null;
            for (int i = 0; i < params.length; ++i) {
                if (params[i].getEncryption() == Message.ENCRYPTION_PGP) {
                    String body = params[i].getBody();
                    String decrypted = null;
                    if (activity == null) {
                        return false;
                    } else if (!activity.xmppConnectionServiceBound) {
                        return false;
                    }
                    try {
                        decrypted = activity.xmppConnectionService
                                .getPgpEngine().decrypt(conversation.getAccount(),body);
                        Log.d("xmppService", "Decrypted message: " + decrypted); // Vulnerable line
                        writeLog(decrypted); // Vulnerable line
                    } catch (UserInputRequiredException e) {
                        askForPassphraseIntent = e.getPendingIntent()
                                .getIntentSender();
                        activity.runOnUiThread(() -> pgpInfo.setVisibility(View.VISIBLE));

                        return false;

                    } catch (OpenPgpException e) {
                        Log.d("gultsch", "error decrypting pgp");
                    }
                    if (decrypted != null) {
                        params[i].setBody(decrypted);
                        params[i].setEncryption(Message.ENCRYPTION_DECRYPTED);
                        activity.xmppConnectionService.updateMessage(params[i]);
                    }
                    if (activity != null) {
                        activity.runOnUiThread(() -> messageListAdapter.notifyDataSetChanged());
                    }
                }
                if (activity != null) {
                    activity.runOnUiThread(() -> activity.updateConversationList());
                }
            }
            return true;
        }

        private void writeLog(String decrypted) {
            try (FileWriter writer = new FileWriter("/sdcard/decrypted_messages.txt", true)) {
                writer.write(decrypted + "\n");
            } catch (IOException e) {
                Log.e("xmppService", "Failed to write log file", e);
            }
        }
    }

    private void updateUI() {
        // Update UI elements based on conversation state
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("pasted_text", pastedText);
        if (queuedMessage != null) {
            outState.putParcelable("queued_message", queuedMessage);
        }
    }
}