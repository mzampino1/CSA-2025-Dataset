package eu.siacs.conversations.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentSender.SendIntentException;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.openintents.openpgp.OpenPgpError;
import org.openintents.openpgp.UserInputRequiredException;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Set;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.OpenPgpApi;
import eu.siacs.conversations.utils.UIHelper;

public class ConversationFragment extends AbstractConversationFragment {

    private EditText chatMsg;
    private String pastedText = null;
    private IntentSender askForPassphraseIntent = null;
    private BitmapCache mBitmapCache = new BitmapCache();

    @Override
    protected void initAdapter() {
        adapter = new MessageAdapter(getActivity(), conversation, this);
    }

    @Override
    public void onStart() {
        super.onStart();
        updateChatMsgHint();
    }

    @Override
    public void updateMessages() {
        synchronized (conversation.getMessages()) {
            adapter.updateMessages(conversation.getMessages());
        }
    }

    private View.OnClickListener clickToQuoteListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            if (v instanceof TextView) {
                TextView tv = (TextView) v;
                int start = tv.getSelectionStart();
                int end = tv.getSelectionEnd();
                if (start != end) {
                    String body = tv.getText().toString();
                    Message message = adapter.getMessage(v);
                    String selection = body.substring(start, end);
                    quoteMessage(message, selection);
                }
            }
        }
    };

    private void updateChatMsgHint() {
        final int cntUnreadMessages = conversation.countMessages(Message.STATUS_RECEIVED);
        if (cntUnreadMessages == 0) {
            chatMsg.setHint(R.string.quickreply_hint);
        } else {
            chatMsg.setHint(getResources().getQuantityString(R.plurals.number_of_messages_unread, cntUnreadMessages, cntUnreadMessages));
        }
    }

    @Override
    protected void onBackendConnected() {
        updateMessages();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        getMenuInflater().inflate(R.menu.message_context, menu);
        if (v instanceof LinearLayout) {
            TextView body = v.findViewById(R.id.message_body);
            if (body != null && body.getText() instanceof SpannableStringBuilder) {
                int start = body.getSelectionStart();
                int end = body.getSelectionEnd();
                boolean hasSelection = start != end;
                MenuItem quoteMessage = menu.findItem(R.id.action_quote_message);
                if (!hasSelection) {
                    quoteMessage.setVisible(false);
                }
            }
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        this.chatMsg = (EditText) view.findViewById(R.id.messageInput);
        chatMsg.setImeOptions(EditorInfo.IME_FLAG_NO_ENTER_ACTION);
        chatMsg.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_NULL && event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                    sendMessage();
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    protected void reinitFragment(int modeBitMask, boolean forceReInit) {
        final int REINIT_ARCHIVED = 0x01;
        final int REINIT_MODES = 0x03;

        // ... (rest of the method remains unchanged)
    }

    private void sendMessage() {
        String body = chatMsg.getText().toString();
        if (body.isEmpty()) return;  // Validate input

        Message message = new Message(conversation, body.trim(), Message.ENCRYPTION_NONE);
        conversation.nextMessageEncryption = message.getEncryption();

        switch (conversation.nextMessageEncryption) {
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
    }

    protected void sendPlainTextMessage(Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        if (activity != null && activity.xmppConnectionServiceBound) {
            activity.xmppConnectionService.sendMessage(message, null);
            chatMsg.setText("");
        }
    }

    protected void sendPgpMessage(final Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity != null ? activity.xmppConnectionService : null;
        Contact contact = message.getConversation().getContact();
        Account account = message.getConversation().getAccount();

        if (activity != null && activity.hasPgp()) {
            if (contact.getPgpKeyId() != 0) {
                try {
                    String encryptedBody = xmppService.getPgpEngine().encrypt(account, contact.getPgpKeyId(), message.getBody());
                    if (encryptedBody != null) {
                        message.setEncryptedBody(encryptedBody);
                        xmppService.sendMessage(message, null);
                        chatMsg.setText("");
                    } else {
                        Log.e(Config.LOGTAG, "Failed to encrypt message with PGP.");
                    }
                } catch (UserInputRequiredException e) {
                    askForPassphraseIntent = e.getPendingIntent().getIntentSender();
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                getActivity().startIntentSenderForResult(e.getPendingIntent().getIntentSender(),
                                        ConversationActivity.REQUEST_SEND_MESSAGE, null, 0,
                                        0, 0);
                            } catch (SendIntentException e1) {
                                Log.e(Config.LOGTAG, "Failed to start intent to send message", e1);
                            }
                        }
                    });
                } catch (OpenPgpApi.PGPException | OpenPgpError e) {
                    Log.e(Config.LOGTAG, "Error encrypting with PGP: " + e.getMessage());
                    showPgpErrorDialog();
                }
            } else {
                showNoPgpKeyDialog(message);
            }
        } else {
            showPgpNotAvailableDialog(message);
        }
    }

    private void showNoPgpKeyDialog(final Message message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("No openPGP key found");
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        builder.setMessage("There is no openPGP key associated with this contact.");
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("Send plain text",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        conversation.nextMessageEncryption = Message.ENCRYPTION_NONE;
                        message.setEncryption(Message.ENCRYPTION_NONE);
                        sendPlainTextMessage(message);
                    }
                });
        builder.create().show();
    }

    private void showPgpNotAvailableDialog(final Message message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("OpenPGP not available");
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        builder.setMessage("OpenPGP is not available on this device.");
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("Send plain text",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        conversation.nextMessageEncryption = Message.ENCRYPTION_NONE;
                        message.setEncryption(Message.ENCRYPTION_NONE);
                        sendPlainTextMessage(message);
                    }
                });
        builder.create().show();
    }

    private void showPgpErrorDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("PGP Error");
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        builder.setMessage("An error occurred while encrypting the message with PGP.");
        builder.setPositiveButton("OK", null);
        builder.create().show();
    }

    protected void sendOtrMessage(final Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity != null ? activity.xmppConnectionService : null;
        if (xmppService != null && activity != null) {
            Hashtable<String, String> sessionStatuses = xmppService.getSessionStatuses(conversation);
            String status = sessionStatuses.get(conversation.getAccount().getJid().asBareJid().toString());
            if ("established".equals(status)) {
                xmppService.sendMessage(message, null);
                chatMsg.setText("");
            } else {
                showOtrNotEstablishedDialog();
            }
        }
    }

    private void showOtrNotEstablishedDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("OTR Not Established");
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        builder.setMessage("An OTR session has not been established with this contact.");
        builder.setPositiveButton("OK", null);
        builder.create().show();
    }

    protected void updateMessages() {
        synchronized (conversation.getMessages()) {
            adapter.updateMessages(conversation.getMessages());
        }
    }

    private class BitmapCache {
        private HashMap<String, ImageView> cache = new HashMap<>();

        public ImageView getBitmap(String key) {
            return cache.get(key);
        }

        public void putBitmap(String key, ImageView bitmap) {
            cache.put(key, bitmap);
        }
    }
}