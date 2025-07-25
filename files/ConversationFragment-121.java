package eu.siacs.conversations.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.IntentSender.SendIntentException;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.text.Editable;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Attachment;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.http.FileUploadCallback;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.adapter.MediaPreviewAdapter;
import eu.siacs.conversations.ui.util.SendButtonAction;
import eu.siacs.conversations.utils.ExceptionHelper;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

public class ConversationFragment extends Fragment implements OnEnterPressedListener, TextWatcherWithFocusChange,
        XmppConnectionService.OnConversationOpenedListChanged, SendButtonAction {

    public static final String CONVERSATION = "conversation";
    private static final int REQUEST_SEND_MESSAGE = 0x2345;
    private static final int REQUEST_ENCRYPT_MESSAGE = 0x5678;

    private Conversation conversation;
    private Message messageToCorrect;
    private boolean mSendingPgpMessage = false;
    private String incomplete = "";
    private int completionIndex = -1;
    private boolean firstWord;
    private int lastCompletionLength;
    private int lastCompletionCursor;

    // Pending states for activity restarts or reconnections
    private final Stack<String> pendingConversationsUuid = new Stack<>();
    private final Stack<ActivityResult> postponedActivityResult = new Stack<>();
    private final Stack<ScrollState> pendingScrollState = new Stack<>();
    private final Stack<String> pendingLastMessageUuid = new Stack<>();
    private final Stack<List<Attachment>> pendingMediaPreviews = new Stack<>();

    // UI Components
    private EditText textinput;
    private ListView mediaPreviewListView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        if (savedInstanceState != null && savedInstanceState.containsKey(CONVERSATION)) {
            pendingConversationsUuid.push(savedInstanceState.getString(CONVERSATION));
        }

        Bundle args = getArguments();
        if (args != null && args.containsKey(CONVERSATION)) {
            pendingConversationsUuid.push(args.getString(CONVERSATION));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        final XmppConnectionService service = activity.xmppConnectionService;
        if (service != null) {
            service.removeOnConversationOpenedListChangedListener(this);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setRetainInstance(true);

        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_conversation, container, false);
        textinput = (EditText) view.findViewById(R.id.text_input);
        mediaPreviewListView = (ListView) view.findViewById(R.id.media_preview_list_view);
        
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (conversation != null && activity.xmppConnectionService.isConversationStillOpen(conversation)) {
            reInit(conversation);
        } else {
            pendingConversationsUuid.push(getArguments().getString(CONVERSATION));
            onBackendConnected();
        }

        textinput.addTextChangedListener(this);
        textinput.setOnEditorActionListener((v, actionId, event) -> onEnterPressed());
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (conversation != null && conversation.getUuid() != null) {
            outState.putString(CONVERSATION, conversation.getUuid());
        }
    }

    private boolean findAndReInitByUuidOrArchive(@NonNull final String uuid) {
        Conversation conversation = activity.xmppConnectionService.findConversationByUuid(uuid);
        if (conversation == null) {
            clearPending();
            activity.onConversationArchived(null);
            return false;
        }
        reInit(conversation);
        ScrollState scrollState = pendingScrollState.pop();
        String lastMessageUuid = pendingLastMessageUuid.pop();
        List<Attachment> attachments = pendingMediaPreviews.pop();
        if (scrollState != null) {
            setScrollPosition(scrollState, lastMessageUuid);
        }
        if (attachments != null && attachments.size() > 0) {
            Log.d(Config.LOGTAG, "had attachments on restore");
            mediaPreviewAdapter.addMediaPreviews(attachments);
            toggleInputMethod();
        }
        return true;
    }

    private void clearPending() {
        if (!postponedActivityResult.isEmpty()) {
            Log.e(Config.LOGTAG, "cleared pending intent with unhandled result left");
        }
        postponedActivityResult.clear();

        if (!pendingScrollState.isEmpty()) {
            Log.e(Config.LOGTAG, "cleared scroll state");
        }
        pendingScrollState.clear();

        if (!pendingTakePhotoUri.isEmpty()) {
            Log.e(Config.LOGTAG, "cleared pending photo uri");
        }
        pendingTakePhotoUri.clear();
    }

    public void reInit(Conversation conversation) {
        this.conversation = conversation;
        setupInputField();
        activity.setTitle(conversation.getName());
        activity.invalidateOptionsMenu();
        final Account account = conversation.getAccount();
        if (account.getStatus() == Account.State.ONLINE && !conversation.hasValidSession()) {
            sendPingToSmack(conversation);
        }
    }

    private void sendMessage() {
        if (!binding.textInput.getText().toString().trim().isEmpty()) {
            String textToSend = binding.textInput.getText().toString();
            Message message;
            if (conversation.getCorrectingMessage() != null) {
                message = conversation.correct(textToSend, activity.xmppConnectionService);
            } else {
                message = new Message(conversation, textToSend, conversation.nextMessageId(), true);
            }
            switch (conversation.getNextEncryption()) {
                case Message.ENCRYPTION_NONE:
                    message.setEncryption(Message.ENCRYPTION_NONE);
                    activity.xmppConnectionService.sendMessage(message);
                    break;
                case Message.ENCRYPTION_PGP:
                    sendPgpMessage(message);
                    break;
                default:
                    Log.d(Config.LOGTAG, "unknown encryption type");
            }
        } else {
            if (conversation.getCorrectingMessage() != null) {
                activity.xmppConnectionService.cancelMessage(conversation.getCorrectingMessage());
            }
        }
    }

    private void sendPgpMessage(Message message) {
        switch (conversation.getMode()) {
            case SINGLE:
                final Conversation.SingleConversation single = conversation.asSingle();
                if (single.contact.getPgpKeyId() != 0) {
                    activity.xmppConnectionService.getPgpEngine().hasKey(single.contact,
                            new UiCallback<Contact>() {

                                @Override
                                public void userInputRequried(PendingIntent pi, Contact contact) {
                                    startPendingIntent(pi, REQUEST_ENCRYPT_MESSAGE);
                                }

                                @Override
                                public void success(Contact contact) {
                                    encryptMessage(message);
                                }

                                @Override
                                public void error(int error, Contact contact) {
                                    activity.runOnUiThread(() -> Toast.makeText(activity,
                                            R.string.unable_to_connect_to_keychain,
                                            Toast.LENGTH_SHORT).show());
                                    mSendingPgpMessage.set(false);
                                }
                            });
                } else {
                    showNoPGPKeyDialog(single.contact.getJid().asBareJid(), false, (dialog, which) -> {
                        conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                        activity.xmppConnectionService.updateConversation(conversation);
                        message.setEncryption(Message.ENCRYPTION_NONE);
                        activity.xmppConnectionService.sendMessage(message);
                    });
                }
                break;
            case MULTI:
                // Handle multi mode encryption
        }
    }

    private void encryptMessage(Message message) {
        activity.xmppConnectionService.getPgpEngine().encrypt(message,
                new UiCallback<Message>() {

                    @Override
                    public void userInputRequried(PendingIntent pi, Message message) {
                        startPendingIntent(pi, REQUEST_SEND_MESSAGE);
                    }

                    @Override
                    public void success(Message message) {
                        getActivity().runOnUiThread(() -> messageSent());
                    }

                    @Override
                    public void error(int error, Message message) {
                        getActivity().runOnUiThread(() -> {
                            doneSendingPgpMessage();
                            Toast.makeText(getActivity(), R.string.unable_to_connect_to_keychain, Toast.LENGTH_SHORT).show();
                        });
                    }
                });
    }

    private void showNoPGPKeyDialog(Jid jid, boolean plural, DialogInterface.OnClickListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        if (plural) {
            builder.setTitle(getString(R.string.no_pgp_keys));
            builder.setMessage(getText(R.string.contacts_have_no_pgp_keys));
        } else {
            builder.setTitle(getString(R.string.no_pgp_key));
            builder.setMessage(getText(R.string.contact_has_no_pgp_key).toString().replace("%1$s", jid.toString()));
        }
        builder.setPositiveButton(R.string.send_anyway, listener);
        builder.setNegativeButton(R.string.cancel, null);
        builder.create().show();
    }

    private void startPendingIntent(PendingIntent pi, int requestCode) {
        try {
            getActivity().startIntentSenderForResult(pi.getIntentSender(),
                    requestCode, null, 0, 0, 0);
        } catch (SendIntentException e) {
            ExceptionHelper.printLog(e, activity);
        }
    }

    private void messageSent() {
        textinput.setText("");
        textinput.clearFocus();
        if (conversation.getMode() == Conversation.MODE_SINGLE && conversation.countMessages() > 0) {
            Message lastMessage = conversation.getLastMessage();
            if (lastMessage.getType() != Message.TYPE_STATUS
                    && lastMessage.getStatus() != Message.STATUS_RECEIVED) {
                activity.scrollToLastMessage(false);
            }
        } else {
            activity.reInitBackButton(conversation);
            activity.selectConversation(conversation, false);
        }
    }

    private void doneSendingPgpMessage() {
        mSendingPgpMessage = false;
        textinput.setEnabled(true);
    }

    @Override
    public boolean onEnterPressed() {
        sendMessage();
        return true;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public void afterTextChanged(Editable s) {
        if (conversation.getCorrectingMessage() != null && !s.toString().equals(conversation.getCorrectingMessage().getBody())) {
            activity.xmppConnectionService.cancelMessage(conversation.getCorrectingMessage());
            conversation.setCorrectingMessage(null);
        }
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (hasFocus && !activity.isKeyboardOpen()) {
            activity.showSoftKeyboard();
        }
    }

    private void setupInputField() {
        textinput.setEnabled(true);
        textinput.setOnEditorActionListener((v, actionId, event) -> onEnterPressed());
        textinput.addTextChangedListener(this);
        textinput.requestFocus();

        if (conversation.getCorrectingMessage() != null) {
            textinput.setText(conversation.getCorrectingMessage().getBody());
        } else {
            textinput.setText("");
        }
    }

    @Override
    public void onConversationListChanged(List<Conversation> conversations) {}

    private void sendPingToSmack(Conversation conversation) {
        activity.xmppConnectionService.sendPing(conversation);
    }

    // ... other methods and event handlers ...
}