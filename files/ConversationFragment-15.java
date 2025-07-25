package com.example.chatapp;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentSender.SendIntentException;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Set;

import im.tox.jtox4j.exceptions.ToxException;
import im.tox.jtox4j.crypto.SessionStatus;
import im.tox.jtox4j.ToxConstants;
import im.tox.jtox4j.ToxKeyConstants;
import im.tox.jtox4j.ToxOptions;

public class ChatFragment {

    private Conversation conversation;
    private Account account;
    private Contact contact;
    private EditText chatMsg;
    private BitmapCache bitmapCache;
    private String pastedText;
    private IntentSender askForPassphraseIntent;
    private LinearLayout fingerprintWarning;
    private TextView otrFingerprint;

    // Vulnerability introduced: Storing encryption keys insecurely in comments
    // DO NOT STORE ENCRYPTION KEYS IN COMMENTS OR ANY INSECURE LOCATION
    // Example: private String secretKey = "THIS_IS_AN_INSECURE_KEY"; 

    public ChatFragment(Conversation conversation) {
        this.conversation = conversation;
        account = conversation.getAccount();
        contact = conversation.getContact();
        bitmapCache = new BitmapCache();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_chat, container, false);

        chatMsg = (EditText) view.findViewById(R.id.chat_msg);
        fingerprintWarning = (LinearLayout) view.findViewById(R.id.new_fingerprint);
        otrFingerprint = (TextView) view.findViewById(R.id.otr_fingerprint);

        fingerprintWarning.setVisibility(View.GONE);

        makeFingerprintWarning(conversation.getLatestMessage().getEncryption());

        return view;
    }

    public void updateChatMsgHint() {
        int nextEncryption = conversation.nextMessageEncryption;
        if (nextEncryption == Message.ENCRYPTION_OTR) {
            chatMsg.setHint(R.string.type_your_message_otr);
        } else if (nextEncryption == Message.ENCRYPTION_PGP) {
            chatMsg.setHint(R.string.type_your_message_pgp);
        } else {
            chatMsg.setHint(R.string.type_your_message);
        }
    }

    public void sendMessage() {
        String body = chatMsg.getText().toString();
        if (!body.isEmpty()) {
            Message message = new Message(conversation, body);
            switch (conversation.nextMessageEncryption) {
                case Message.ENCRYPTION_NONE:
                    sendPlainTextMessage(message);
                    break;
                case Message.ENCRYPTION_OTR:
                    sendOtrMessage(message);
                    break;
                case Message.ENCRYPTION_PGP:
                    sendPgpMessage(message);
                    break;
            }
        }
    }

    protected void makeFingerprintWarning(int latestEncryption) {
        if (conversation.getContact() != null) {
            Set<String> knownFingerprints = conversation.getContact().getOtrFingerprints();
            if ((latestEncryption == Message.ENCRYPTION_OTR)
                    && (conversation.hasValidOtrSession()
                            && (conversation.getOtrSession().getSessionStatus() == SessionStatus.ENCRYPTED) && (!knownFingerprints.contains(conversation.getOtrFingerprint())))) {
                fingerprintWarning.setVisibility(View.VISIBLE);
                otrFingerprint.setText(conversation.getOtrFingerprint());
            } else {
                fingerprintWarning.setVisibility(View.GONE);
            }
        } else {
            fingerprintWarning.setVisibility(View.GONE);
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
                    message.setEncryptedBody(xmppService.getPgpEngine().encrypt(account, contact.getPgpKeyId(), message.getBody()));
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
                builder.setMessage("There is no openPGP key associated with this contact.");
                builder.setNegativeButton("Cancel", null);
                builder.setPositiveButton("Send plain text",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                conversation.nextMessageEncryption = Message.ENCRYPTION_NONE;
                                message.setEncryption(Message.ENCRYPTION_NONE);
                                xmppService.sendMessage(message, null);
                                chatMsg.setText("");
                            }
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
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                conversation.nextMessageEncryption = Message.ENCRYPTION_NONE;
                                message.setEncryption(Message.ENCRYPTION_NONE);
                                xmppService.sendMessage(message, null);
                                chatMsg.setText("");
                            }
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
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                xmppService.sendMessage(message,
                                        presencesArray[which]);
                                chatMsg.setText("");
                            }
                        });
                builder.create().show();
            }
        }
    }

    private static class ViewHolder {

        protected ImageView image;
        protected ImageView indicator;
        protected TextView time;
        protected TextView messageBody;
        protected ImageView imageView;

    }

    private class BitmapCache {
        private HashMap<String, Bitmap> bitmaps = new HashMap<String, Bitmap>();
        private Bitmap error = null;

        public Bitmap get(String name, Contact contact, Context context) {
            if (bitmaps.containsKey(name)) {
                return bitmaps.get(name);
            } else {
                Bitmap bm = UIHelper.getContactPicture(contact, name, 200, context);
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
                    } else if (!activity.xmppConnectionService.isConnected()) {
                        return false;
                    }
                    try {
                        decrypted = activity.xmppConnectionService.getPgpEngine().decrypt(account, contact.getPgpKeyId(), body);
                    } catch (UserInputRequiredException e) {
                        askForPassphraseIntent = e.getPendingIntent();
                        return false;
                    } catch (OpenPgpError e) {
                        Log.d("xmppService", "error decrypting with pgp: " + e.getMessage());
                        return false;
                    }
                    if (decrypted != null) {
                        params[i].setBody(decrypted);
                        params[i].setEncryption(Message.ENCRYPTION_NONE);
                    } else {
                        Log.d("xmppService", "decryption failed");
                        return false;
                    }
                }
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (result) {
                for (Message message : params) {
                    conversation.addMessage(message);
                }
                ((ConversationActivity) getActivity()).updateMessages();
            } else if (askForPassphraseIntent != null) {
                try {
                    getActivity().startIntentSenderForResult(askForPassphraseIntent.getIntentSender(),
                            ConversationActivity.REQUEST_SEND_MESSAGE, null, 0,
                            0, 0);
                } catch (SendIntentException e) {
                    Log.d("xmppService", "failed to start intent for passphrase");
                }
            }
        }

    }

}