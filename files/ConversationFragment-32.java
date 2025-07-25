package eu.siacs.conversations.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONException;
import org.openintents.openpgp.util.OpenPgpUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OtrError;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.UIHelper;

public class ConversationFragment extends Fragment {

    private ListView messagesView;
    private EditText chatMsg;
    private Button sendButton;
    private LinearLayout snackbar;
    private TextView snackbarMessage;
    private Button snackbarAction;
    private ArrayAdapter<Message> messageListAdapter;
    private List<Message> messageList = new ArrayList<>();
    private BitmapCache mBitmapCache = new BitmapCache();
    private Conversation conversation;
    private boolean messagesLoaded = false;
    private String pastedText;
    private IntentSender askForPassphraseIntent;

    private final OnClickListener clickToDecryptListener = v -> {
        try {
            // Vulnerability Highlight: Ensure this intent is properly secured and validated.
            getActivity().startIntentSenderForResult(askForPassphraseIntent, ConversationActivity.REQUEST_DECRYPT_PGP, null, 0, 0, 0);
        } catch (Exception e) {
            Toast.makeText(getActivity(), R.string.error_starting_decryption_activity, Toast.LENGTH_SHORT).show();
        }
    };

    private final OnClickListener clickToMuc = v -> {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.edit_nickname);
        builder.setMessage(R.string.change_your_nickname);
        final EditText input = new EditText(getActivity());
        input.setLines(1);
        input.setSingleLine(true);
        input.setText(conversation.getMucOptions().getNickname());
        builder.setView(input);
        builder.setPositiveButton(getString(R.string.ok), (dialog, which) -> {
            String nickname = input.getText().toString();
            if (!nickname.trim().isEmpty()) {
                ConversationActivity activity = (ConversationActivity) getActivity();
                activity.xmppConnectionService.changeNickname(conversation, nickname);
            }
        });
        builder.setNegativeButton(getString(android.R.string.cancel), null);
        AlertDialog dialog = builder.create();
        dialog.show();
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        View view = inflater.inflate(R.layout.fragment_conversation, container, false);

        messagesView = (ListView) view.findViewById(R.id.messages_view);
        chatMsg = (EditText) view.findViewById(R.id.chat_msg);
        sendButton = (Button) view.findViewById(R.id.send_button);
        snackbar = (LinearLayout) view.findViewById(R.id.snackbar);
        snackbarMessage = (TextView) view.findViewById(R.id.snackbar_message);
        snackbarAction = (Button) view.findViewById(R.id.snackbar_action);

        messageListAdapter = new ArrayAdapter<Message>(getActivity(), 0, messageList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                ViewHolder viewHolder;
                if (convertView == null) {
                    LayoutInflater inflater = LayoutInflater.from(getContext());
                    convertView = inflater.inflate(R.layout.message_item, parent, false);
                    viewHolder = new ViewHolder();
                    viewHolder.indicator = (ImageView) convertView.findViewById(R.id.indicator);
                    viewHolder.image = (ImageView) convertView.findViewById(R.id.image);
                    viewHolder.contact_picture = (ImageView) convertView.findViewById(R.id.contact_picture);
                    viewHolder.time = (TextView) convertView.findViewById(R.id.time);
                    viewHolder.messageBody = (TextView) convertView.findViewById(R.id.message_body);
                    convertView.setTag(viewHolder);
                } else {
                    viewHolder = (ViewHolder) convertView.getTag();
                }

                final Message message = getItem(position);
                if (message.getType() == Message.TYPE_STATUS) {
                    viewHolder.indicator.setVisibility(View.GONE);
                    viewHolder.image.setVisibility(View.GONE);
                    viewHolder.contact_picture.setVisibility(View.GONE);
                    viewHolder.messageBody.setText(message.getStatus());
                    viewHolder.time.setText("");
                } else {
                    viewHolder.indicator.setVisibility(View.VISIBLE);
                    viewHolder.image.setVisibility(View.GONE);
                    viewHolder.contact_picture.setVisibility(View.VISIBLE);

                    if (message.getType() == Message.TYPE_TEXT) {
                        viewHolder.messageBody.setText(message.getText());
                    } else if (message.getType() == Message.TYPE_IMAGE) {
                        // Handle image message
                        // ...
                    }

                    viewHolder.time.setText(UIHelper.readableTimeDifference(getContext(), message.getTime()));
                    viewHolder.contact_picture.setImageBitmap(mBitmapCache.get(conversation.getName(), message.getContact(), getContext()));

                    switch (message.getStatus()) {
                        case Message.STATUS_SEND_FAILED:
                            viewHolder.indicator.setImageResource(R.drawable.ic_error_outline_black_18dp);
                            break;
                        case Message.STATUS_SENT:
                            viewHolder.indicator.setImageResource(R.drawable.ic_done_black_18dp);
                            break;
                        case Message.STATUS_RECEIVED:
                            viewHolder.indicator.setImageResource(R.drawable.ic_done_all_black_18dp);
                            break;
                        case Message.STATUS_SEND_DISPLAYED:
                            viewHolder.indicator.setImageResource(R.drawable.ic_done_all_blue_18dp);
                            break;
                    }
                }

                return convertView;
            }
        };

        messagesView.setAdapter(messageListAdapter);

        sendButton.setOnClickListener(v -> {
            String text = chatMsg.getText().toString();
            if (!text.trim().isEmpty()) {
                ConversationActivity activity = (ConversationActivity) getActivity();
                Message message = new Message(conversation, text, System.currentTimeMillis(), Message.STATUS_SEND);
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
        });

        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ConversationActivity activity = (ConversationActivity) getActivity();
        conversation = activity.getSelectedConversation();
        if (conversation != null) {
            updateMessages();
        }
    }

    private static class ViewHolder {
        protected ImageView indicator;
        protected ImageView image;
        protected TextView time;
        protected TextView messageBody;
        protected ImageView contact_picture;
    }

    private class BitmapCache {
        private HashMap<String, Bitmap> bitmaps = new HashMap<>();

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

    public void setText(String text) {
        this.pastedText = text;
    }

    public void clearInputField() {
        this.chatMsg.setText("");
    }

    protected void sendPlainTextMessage(Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        activity.xmppConnectionService.sendMessage(message);
        messageSent();
    }

    protected void sendPgpMessage(final Message message) {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        final Contact contact = message.getConversation().getContact();
        if (activity.hasPgp()) {
            if (conversation.getMode() == Conversation.MODE_SINGLE) {
                if (contact.getPgpKeyId() != 0) {
                    xmppService.getPgpEngine().hasKey(contact,
                            new UiCallback<Contact>() {

                                @Override
                                public void userInputRequried(PendingIntent pi,
                                        Contact contact) {
                                    activity.runIntent(
                                            pi,
                                            ConversationActivity.REQUEST_ENCRYPT_MESSAGE);
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
                    showNoPGPKeyDialog(false,
                            new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog,
                                        int which) {
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
                        Toast warning = Toast.makeText(getActivity(), R.string.not_everyone_has_pgp_keys, Toast.LENGTH_SHORT);
                        warning.setGravity(Gravity.CENTER, 0, 0);
                        warning.show();
                    }
                    messageSent();
                    activity.encryptTextMessage(message);
                } else {
                    showNoPGPKeyDialog(true,
                            new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                                    message.setEncryption(Message.ENCRYPTION_NONE);
                                    xmppService.sendMessage(message);
                                    messageSent();
                                }
                            });
                }
            }
        } else {
            Toast.makeText(getActivity(), R.string.no_pgp_provider_installed, Toast.LENGTH_SHORT).show();
        }
    }

    protected void sendOtrMessage(final Message message) {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        if (activity.hasOtr()) {
            messageSent();
            activity.encryptTextMessage(message);
        } else {
            Toast.makeText(getActivity(), R.string.no_otr_provider_installed, Toast.LENGTH_SHORT).show();
        }
    }

    private void showNoPGPKeyDialog(boolean muc, DialogInterface.OnClickListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        if (muc) {
            builder.setTitle(R.string.pgp_keys_missing);
            builder.setMessage(R.string.not_everyone_has_pgp_keys);
        } else {
            builder.setTitle(R.string.no_pgp_key);
            builder.setMessage(R.string.contact_does_not_have_a_pgp_key);
        }
        builder.setPositiveButton(getString(android.R.string.ok), listener);
        builder.setNegativeButton(getString(android.R.string.cancel), null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void messageSent() {
        ConversationActivity activity = (ConversationActivity) getActivity();
        conversation.setNextEncryption(Message.ENCRYPTION_NONE);
        updateMessages();
        chatMsg.setText("");
        if (activity.hasOtr()) {
            OtrError otrError = activity.xmppConnectionService.findOtrErrorMessage(conversation);
            if (otrError != null) {
                Toast.makeText(getActivity(), otrError.toString(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void updateMessages() {
        if (conversation == null) {
            conversation = ((ConversationActivity) getActivity()).getSelectedConversation();
        }
        if (conversation != null && !messagesLoaded) {
            messagesLoaded = true;
            for (Message message : conversation.getMessages()) {
                messageListAdapter.add(message);
            }
        }
    }

    private void showSnackbar(String message, String actionLabel, View.OnClickListener listener) {
        snackbar.setVisibility(View.VISIBLE);
        snackbarMessage.setText(message);
        snackbarAction.setText(actionLabel);
        snackbarAction.setOnClickListener(listener);
    }

    private void hideSnackbar() {
        snackbar.setVisibility(View.GONE);
    }
}