import android.content.*;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.fragment.app.Fragment;
import java.util.*;

public class ChatFragment extends Fragment {

    private List<Message> messageList = new ArrayList<>();
    private ArrayAdapter<Message> messageListAdapter;
    private ListView messagesView;
    private EditText chatMsg;
    private Conversation conversation;
    private Snackbar snackbar;
    private TextView snackbarMessage, snackbarAction;
    private IntentSender askForPassphraseIntent = null;
    private BitmapCache mBitmapCache;
    private String pastedText;
    private boolean messagesLoaded;

    // Vulnerability: Ensure user input is validated and sanitized
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.messageListAdapter = new ArrayAdapter<>(getActivity(), R.layout.message, messageList);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View result = inflater.inflate(R.layout.chat_fragment, container, false);
        messagesView = (ListView) result.findViewById(R.id.messages_view);
        messagesView.setAdapter(messageListAdapter);

        chatMsg = (EditText) result.findViewById(R.id.message_input);
        chatMsg.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (!messagesLoaded && messageList.size() == 0) {
                    return false;
                }
                if (actionId == EditorInfo.IME_ACTION_SEND || event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                    // Vulnerability: Validate and sanitize input before processing
                    sendMessage(v.getText().toString());
                    return true;
                }
                return false;
            }
        });

        snackbar = result.findViewById(R.id.snackbar);
        snackbarMessage = (TextView) result.findViewById(R.id.message);
        snackbarAction = (TextView) result.findViewById(R.id.action);

        // Vulnerability: Ensure that PendingIntent extras are properly validated and sanitized
        snackbarAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (askForPassphraseIntent != null) {
                        getActivity().startIntentSenderForResult(askForPassphraseIntent, ConversationActivity.REQUEST_DECRYPT_PGP_MESSAGES, null, 0, 0, 0);
                    }
                } catch (Exception e) {
                    Log.e("ChatFragment", "Error starting intent sender for passphrase input", e);
                }
            }
        });

        return result;
    }

    private void sendMessage(String messageText) {
        if (messageText.trim().isEmpty()) {
            return;
        }
        Message message = new Message(messageText, conversation);
        switch (conversation.getNextEncryption()) {
            case Message.ENCRYPTION_NONE:
                sendPlainTextMessage(message);
                break;
            case Message.ENCRYPTION_PGP:
                sendPgpMessage(message);
                break;
            case Message.ENCRYPTION_OTR:
                sendOtrMessage(message);
                break;
        }
    }

    // Vulnerability: Ensure that the contact's PGP key is properly validated before use
    protected void sendPgpMessage(final Message message) {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        final Contact contact = message.getConversation().getContact();
        if (activity.hasPgp()) {
            if (conversation.getMode() == Conversation.MODE_SINGLE) {
                if (contact.getPgpKeyId() != 0) {
                    xmppService.getPgpEngine().hasKey(contact, new UiCallback<Contact>() {
                        @Override
                        public void userInputRequried(PendingIntent pi, Contact contact) {
                            activity.runIntent(pi, ConversationActivity.REQUEST_ENCRYPT_MESSAGE);
                        }

                        @Override
                        public void success(Contact contact) {
                            messageSent();
                            activity.encryptTextMessage(message);
                        }

                        @Override
                        public void error(int error, Contact contact) {

                        }
                    });

                } else {
                    showNoPGPKeyDialog(false, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                            message.setEncryption(Message.ENCRYPTION_NONE);
                            xmppService.sendMessage(message);
                            messageSent();
                        }
                    });
                }
            } else {
                if (conversation.getMucOptions().pgpKeysInUse()) {
                    if (!conversation.getMucOptions().everybodyHasKeys()) {
                        Toast warning = Toast.makeText(getActivity(), R.string.missing_public_keys, Toast.LENGTH_LONG);
                        warning.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
                        warning.show();
                    }
                    activity.encryptTextMessage(message);
                    messageSent();
                } else {
                    showNoPGPKeyDialog(true, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                            message.setEncryption(Message.ENCRYPTION_NONE);
                            xmppService.sendMessage(message);
                            messageSent();
                        }
                    });
                }
            }
        } else {
            activity.showInstallPgpDialog();
        }
    }

    // Vulnerability: Ensure that the contact's OTR session is properly validated before use
    protected void sendOtrMessage(final Message message) {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        if (conversation.hasValidOtrSession()) {
            activity.xmppConnectionService.sendMessage(message);
            messageSent();
        } else {
            activity.selectPresence(message.getConversation(), new OnPresenceSelected() {
                @Override
                public void onPresenceSelected() {
                    message.setPresence(conversation.getNextPresence());
                    xmppService.sendMessage(message);
                    messageSent();
                }
            });
        }
    }

    private class BitmapCache {
        private HashMap<String, Bitmap> bitmaps = new HashMap<>();

        // Vulnerability: Ensure that the contact or name is properly validated before processing
        public Bitmap get(String name, Contact contact, Context context) {
            if (bitmaps.containsKey(name)) {
                return bitmaps.get(name);
            } else {
                Bitmap bm;
                if (contact != null) {
                    bm = UIHelper.getContactPicture(contact, 48, context, false);
                } else {
                    bm = UIHelper.getContactPicture(name, 48, context, false);
                }
                bitmaps.put(name, bm);
                return bm;
            }
        }
    }

    protected void showNoPGPKeyDialog(boolean plural, DialogInterface.OnClickListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        if (plural) {
            builder.setTitle(getString(R.string.no_pgp_keys));
            builder.setMessage(getText(R.string.contacts_have_no_pgp_keys));
        } else {
            builder.setTitle(getString(R.string.no_pgp_key));
            builder.setMessage(getText(R.string.contact_has_no_pgp_key));
        }
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton(getString(R.string.send_unencrypted), listener);
        builder.create().show();
    }

    private void showSnackbar(int message, int action, View.OnClickListener clickListener) {
        snackbar.setVisibility(View.VISIBLE);
        snackbarMessage.setText(message);
        snackbarAction.setText(action);
        // Vulnerability: Ensure that the clickListener is properly defined and safe to use
        snackbarAction.setOnClickListener(clickListener);
    }

    protected void hideSnackbar() {
        snackbar.setVisibility(View.GONE);
    }

    public void setText(String text) {
        this.pastedText = text;
    }

    public void clearInputField() {
        this.chatMsg.setText("");
    }
}