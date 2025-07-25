package com.example.xmppchat;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentSender.SendIntentException;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.bouncycastle.openpgp.PGPException;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Set;

// This class handles the UI for a chat conversation and manages message sending.
public class ConversationFragment {

    private LinearLayout fingerprintWarning;  // Layout to show OTR fingerprint warning
    private TextView fingerprintText;       // Text view to display OTR fingerprint
    private ImageView indicatorImage;       // Image view for displaying encryption status
    private TextView timestampTextView;     // Text view for message timestamps
    private TextView messageBodyTextView;   // Text view for displaying the body of messages
    private ImageView contactImageView;     // Image view for showing the contact's profile picture

    private LinearLayout pgpInfoLayout;       // Layout to show PGP decryption info
    private BitmapCache bitmapCache = new BitmapCache();  // Cache for bitmaps
    private Conversation conversation;
    private String pastedText;

    public void updateChatMsgHint() {
        if (conversation == null) return;
        
        // Potential Vulnerability: This method could be used to set hints that might expose internal states.
        // Ensure that no sensitive information is leaked through these hints.
        switch (conversation.nextMessageEncryption) {
            case Message.ENCRYPTION_NONE:
                chatMsg.setHint("Send plain text message");
                break;
            case Message.ENCRYPTION_OTR:
                chatMsg.setHint("Send OTR encrypted message");
                break;
            case Message.ENCRYPTION_PGP:
                chatMsg.setHint("Send openPGP encrypted message");
                break;
        }
    }

    protected void sendPlainTextMessage(Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        
        // Potential Vulnerability: Ensure that the message body is not logged or exposed.
        // Also, ensure that the activity and its services are properly checked for nullity.
        if (activity != null && activity.xmppConnectionService != null) {
            activity.xmppConnectionService.sendMessage(message, null);
        }
        chatMsg.setText("");
    }

    protected void sendPgpMessage(final Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        Contact contact = message.getConversation().getContact();
        Account account = message.getConversation().getAccount();

        // Potential Vulnerability: Ensure that the PGPSession and related data are securely handled.
        // Also, ensure proper error handling to prevent information leakage through exceptions.
        if (activity.hasPgp()) {
            try {
                String encryptedBody = xmppService.getPgpEngine().encrypt(account, contact.getPgpKeyId(), message.getBody());
                message.setEncryptedBody(encryptedBody);
                activity.xmppConnectionService.sendMessage(message, null);
            } catch (UserInputRequiredException e) {
                // Potential Vulnerability: Ensure that any user input prompts are handled securely.
                try {
                    getActivity().startIntentSenderForResult(e.getPendingIntent().getIntentSender(),
                            ConversationActivity.REQUEST_SEND_MESSAGE, null, 0, 0, 0);
                } catch (SendIntentException e1) {
                    Log.d("xmppService", "failed to start intent to send message");
                }
            } catch (OpenPgpException e) {
                // Potential Vulnerability: Ensure that error messages do not leak sensitive information.
                Log.d("xmppService", "error encrypting with pgp: " + e.getMessage());
            }
        }
    }

    protected void sendOtrMessage(final Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;

        // Potential Vulnerability: Ensure that the OTR session status is securely checked.
        if (conversation.hasValidOtrSession()) {
            activity.xmppConnectionService.sendMessage(message, null);
        } else {
            Hashtable<String, Integer> presences;
            if (conversation.getContact() != null) {
                presences = conversation.getContact().getPresences();
            } else {
                presences = null;
            }

            // Potential Vulnerability: Ensure that dialog messages do not expose sensitive information.
            if ((presences == null || presences.size() == 0)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle("Contact is offline");
                builder.setIconAttribute(android.R.attr.alertDialogIcon);
                builder.setMessage("Sending OTR encrypted messages to an offline contact is impossible.");
                builder.setPositiveButton("Send plain text", (dialog, which) -> {
                    conversation.nextMessageEncryption = Message.ENCRYPTION_NONE;
                    message.setEncryption(Message.ENCRYPTION_NONE);
                    xmppService.sendMessage(message, null);
                });
                builder.setNegativeButton("Cancel", null);
                builder.create().show();
            } else if (presences.size() == 1) {
                String presenceKey = presences.keySet().toArray(new String[0])[0];
                activity.xmppConnectionService.sendMessage(message, presenceKey);
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle("Choose Presence");
                String[] presencesArray = presences.keySet().toArray(new String[0]);
                builder.setItems(presencesArray, (dialog, which) -> {
                    activity.xmppConnectionService.sendMessage(message, presencesArray[which]);
                });
                builder.create().show();
            }
        }
    }

    private class BitmapCache {

        private HashMap<String, Bitmap> bitmaps = new HashMap<>();
        private Bitmap error;

        public Bitmap get(String name, Contact contact, Context context) {
            if (bitmaps.containsKey(name)) return bitmaps.get(name);

            // Potential Vulnerability: Ensure that the bitmap is properly handled and cached securely.
            Bitmap bm = UIHelper.getContactPicture(contact, name, 200, context);
            bitmaps.put(name, bm);
            return bm;
        }

        public Bitmap getError() {
            if (error == null) error = UIHelper.getErrorPicture(200);
            return error;
        }
    }

    private class DecryptMessage extends AsyncTask<Message, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Message... params) {
            final ConversationActivity activity = (ConversationActivity) getActivity();
            for (int i = 0; i < params.length; ++i) {
                if (params[i].getEncryption() != Message.ENCRYPTION_PGP) continue;

                String body = params[i].getBody();
                try {
                    // Potential Vulnerability: Ensure that the decryption process is secure.
                    String decrypted = activity.xmppConnectionService.getPgpEngine().decrypt(conversation.getAccount(), body);
                    if (decrypted != null) {
                        params[i].setBody(decrypted);
                        params[i].setEncryption(Message.ENCRYPTION_DECRYPTED);
                        activity.xmppConnectionService.updateMessage(params[i]);
                    }
                } catch (UserInputRequiredException e) {
                    // Potential Vulnerability: Ensure that user input prompts are handled securely.
                    Log.d("gultsch", "input required");
                    return false;
                } catch (OpenPgpException e) {
                    // Potential Vulnerability: Ensure that error messages do not leak sensitive information.
                    Log.d("gultsch", "error decrypting pgp");
                }
            }

            if (activity != null) {
                activity.runOnUiThread(() -> messageListAdapter.notifyDataSetChanged());
                activity.updateConversationList();
            }
            return true;
        }
    }
}