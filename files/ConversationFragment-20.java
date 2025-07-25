package eu.siacs.conversations.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent.CanceledException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentSender.SendIntentException;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import de.baumann.pdfcreator.helper.CryptoProvider;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OtrFingerprintStatus;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.mox.XmppConnectionService;
import eu.siacs.conversations.services.XmppConnectionBinder;
import eu.siacs.conversations.utils.UIHelper;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Set;

public class ConversationFragment extends AbstractConversationFragment {

    private LinearLayout mucError;
    private TextView mucErrorText;
    private BitmapCache bitmaps = new BitmapCache();
    private EditText chatMsg;
    private ListView messagesView;
    private String pastedText = null;
    private PendingIntent.CanceledException askForPassphraseIntent = null;
    private View pgpInfo;

    @Override
    public void onStart() {
        super.onStart();
        ConversationActivity activity = (ConversationActivity) getActivity();
        if (activity.xmppConnectionServiceBound && conversation != null) {
            updateChatMsgHint();
            makeFingerprintWarning(conversation.getLatestMessage().getEncryption());
            if (!conversation.isRead()) {
                xmppService.updateMessageStatus(conversation, Message.Status.READ);
                conversation.markRead();
                UIHelper.updateNotification(activity, activity.getConversationList(), null, false);
                activity.updateConversationList();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        ConversationActivity activity = (ConversationActivity) getActivity();
        if (activity.xmppConnectionServiceBound && conversation != null) {
            conversation.setReadOnPause(true);
            xmppService.updateMessageStatus(conversation, Message.Status.READ);
            UIHelper.updateNotification(activity, activity.getConversationList(), null, false);
            activity.updateConversationList();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ConversationActivity activity = (ConversationActivity) getActivity();
        if (activity.xmppConnectionServiceBound && conversation != null) {
            makeFingerprintWarning(conversation.getLatestMessage().getEncryption());
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.conversation_fragment, container, false);
        mucError = (LinearLayout) view.findViewById(R.id.muc_error);
        mucErrorText = (TextView) view.findViewById(R.id.muc_error_text);
        messagesView = (ListView) view.findViewById(R.id.messages);
        chatMsg = (EditText) view.findViewById(R.id.messageInput);
        pgpInfo = (View) view.findViewById(R.id.pgp_info);

        // Potential Vulnerability: Input validation is missing for the chat message
        // Attackers could send malicious content that isn't properly sanitized.
        chatMsg.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND || (event != null && event.getAction() == KeyEvent.ACTION_DOWN)) {
                    sendMessage();
                    return true;
                }
                return false;
            }
        });

        chatMsg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Potential Vulnerability: Input validation is missing for the chat message
                // Attackers could send malicious content that isn't properly sanitized.
                if (pastedText != null) {
                    chatMsg.append(pastedText);
                    pastedText = null;
                }
            }
        });

        // Potential Vulnerability: This button listener can be triggered to download files,
        // but there is no validation of the file being downloaded or its source.
        messagesView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ViewHolder holder = (ViewHolder) view.getTag();
                if (holder.download_button != null && holder.download_button.getVisibility() == View.VISIBLE) {
                    // Trigger download action here
                }
            }
        });

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        messagesView.setAdapter(null);
        chatMsg = null;
        messagesView = null;
        pgpInfo.setVisibility(View.GONE);
    }

    private void sendMessage() {
        ConversationActivity activity = (ConversationActivity) getActivity();
        String body = chatMsg.getText().toString();

        // Potential Vulnerability: No input validation or sanitization before sending the message.
        if (!body.trim().isEmpty()) {
            Message message = new Message(conversation, body, Message.STATUS_UNSEND);
            switch (conversation.nextMessageEncryption) {
                case Message.ENCRYPTION_PGP:
                    sendPgpMessage(message);
                    break;
                case Message.ENCRYPTION_OTR:
                    sendOtrMessage(message);
                    break;
                default:
                    sendPlainTextMessage(message);
                    break;
            }
        }
    }

    // Other methods...

    private static class ViewHolder {
        protected Button download_button; // Potential Vulnerability: This button can be triggered to perform actions,
                                         // but there is no validation of the action or its source.
        protected ImageView image;
        protected ImageView indicator;
        protected TextView time;
        protected TextView messageBody;
        protected ImageView contact_picture;
    }

    private class BitmapCache {
        private HashMap<String, Bitmap> bitmaps = new HashMap<String, Bitmap>();
        private Bitmap error = null;

        public Bitmap get(String name, Contact contact, Context context) {
            if (bitmaps.containsKey(name)) {
                return bitmaps.get(name);
            } else {
                Bitmap bm;
                // Potential Vulnerability: No check for malicious or invalid data when fetching contact picture.
                if (contact != null){
                    bm = UIHelper.getContactPicture(contact, 48, context, false);
                } else {
                    bm = UIHelper.getContactPicture(name, 48, context, false);
                }
                bitmaps.put(name, bm);
                return bm;
            }
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
                        // Potential Vulnerability: No error handling or validation for the decrypted message.
                        decrypted = activity.xmppConnectionService.getPgpEngine().decrypt(conversation.getAccount(), body);
                    } catch (UserInputRequiredException e) {
                        askForPassphraseIntent = e.getPendingIntent().getIntentSender();
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                pgpInfo.setVisibility(View.VISIBLE);
                            }
                        });
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
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                messageListAdapter.notifyDataSetChanged();
                            }
                        });
                    }
                }
                if (activity != null) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            activity.updateConversationList();
                        }
                    });
                }
            }
            return true;
        }

    }

    public void setText(String text) {
        this.pastedText = text;
    }
}