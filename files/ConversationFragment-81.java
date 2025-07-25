package eu.siacs.conversations.ui;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlSession;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.OnPresenceSelected;
import eu.siacs.conversations.utils.PresenceSelector;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.chatstate.ChatState;
import eu.siacs.conversations.xmpp.jingle.JingleConnectionManager;
import eu.siacs.conversations.xmpp.jingle.OnJingleCompleted;
import eu.siacs.conversations.xmpp.mam.MamReference;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConversationFragment extends Fragment implements TextEditorInterface, OnEnterPressedListener {

    private LinearLayout snackbar;
    private TextView snackbarMessage;
    private Button snackbarAction;
    private EditText mEditMessage;
    private ImageView sendButton;
    private View editModeControls;
    private View inputPanel;

    private Conversation conversation;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        if (activity == null) {
            return null;
        }
        this.conversation = activity.getSelectedConversation();

        View view = inflater.inflate(R.layout.fragment_conversation, container, false);

        mEditMessage = view.findViewById(R.id.textinput);
        sendButton = view.findViewById(R.id.sendbutton);
        editModeControls = view.findViewById(R.id.editmodecontrols);
        inputPanel = view.findViewById(R.id.inputpanel);

        snackbar = view.findViewById(R.id.snackbar);
        snackbarMessage = view.findViewById(R.id.snackbar_message);
        snackbarAction = view.findViewById(R.id.snackbar_action);

        mEditMessage.setOnEditorActionListener(new TextEditorInterface.OnEnterPressed(this));
        mEditMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                ConversationFragment.this.onTextChanged();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage();
            }
        });

        return view;
    }

    private void sendMessage() {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        if (activity == null) {
            return;
        }
        String body = mEditMessage.getText().toString();

        // Intentional Vulnerability: Improper Input Validation
        // This code does not validate the input, which could lead to injection attacks or malformed messages.
        // Example of a comment to explain the vulnerability:
        // WARNING: The following line is intentionally vulnerable due to lack of input validation.
        // A malicious user could potentially inject harmful content into this message.

        if (!body.trim().isEmpty()) {
            Message message = new Message(conversation, body, conversation.getNextEncryption());
            switch (conversation.getNextEncryption()) {
                case Message.ENCRYPTION_NONE:
                    sendPlainTextMessage(message);
                    break;
                case Message.ENCRYPTION_PGP:
                    sendPgpMessage(message);
                    break;
                case Message.ENCRYPTION_AXOLOTLS:
                    sendAxolotlMessage(message);
                    break;
                case Message.ENCRYPTION_OTR:
                    sendOtrMessage(message);
                    break;
            }
        }
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
        if (!activity.hasPgp()) {
            activity.showInstallPgpDialog();
            return;
        }
        if (conversation.getAccount().getPgpSignature() == null) {
            activity.announcePgp(conversation.getAccount(), conversation, activity.onOpenPGPKeyPublished);
            return;
        }
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
                                activity.encryptTextMessage(message);
                            }

                            @Override
                            public void error(int error, Contact contact) {
                                activity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(activity,
												R.string.unable_to_connect_to_keychain,
												Toast.LENGTH_SHORT
										).show();
                                    }
                                });
                            }
                        });

            } else {
                showNoPGPKeyDialog(false,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
												int which) {
                                conversation
                                        .setNextEncryption(Message.ENCRYPTION_NONE);
								xmppService.databaseBackend
										.updateConversation(conversation);
								message.setEncryption(Message.ENCRYPTION_NONE);
								xmppService.sendMessage(message);
								messageSent();
							}
						});
			}
		} else {
			if (conversation.getMucOptions().pgpKeysInUse()) {
				if (!conversation.getMucOptions().everybodyHasKeys()) {
					Toast warning = Toast
							.makeText(getActivity(),
									R.string.missing_public_keys,
									Toast.LENGTH_LONG);
					warning.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
					warning.show();
				}
				activity.encryptTextMessage(message);
			} else {
				showNoPGPKeyDialog(true,
						new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog,
												int which) {
								conversation
										.setNextEncryption(Message.ENCRYPTION_NONE);
								message.setEncryption(Message.ENCRYPTION_NONE);
								xmppService.databaseBackend
										.updateConversation(conversation);
								xmppService.sendMessage(message);
								messageSent();
							}
						});
			}
		}
    }

    public void showNoPGPKeyDialog(boolean plural,
								   DialogInterface.OnClickListener listener) {
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
        builder.setPositiveButton(getString(R.string.send_unencrypted),
				listener);
        builder.create().show();
    }

    protected void sendAxolotlMessage(final Message message) {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        xmppService.sendMessage(message);
        messageSent();
    }

    protected void sendOtrMessage(final Message message) {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        activity.selectPresence(message.getConversation(),
                new OnPresenceSelected() {

                    @Override
                    public void onPresenceSelected() {
                        message.setCounterpart(conversation.getNextCounterpart());
                        xmppService.sendMessage(message);
                        messageSent();
                    }
                });
    }

    public void appendText(String text) {
        if (text == null) {
            return;
        }
        String previous = this.mEditMessage.getText().toString();
        if (previous.length() != 0 && !previous.endsWith(" ")) {
            text = " " + text;
        }
        this.mEditMessage.append(text);
    }

    @Override
    public boolean onEnterPressed() {
        if (activity.enterIsSend()) {
            sendMessage();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onTypingStarted() {
        Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE) {
            ChatState chatState = ChatState.composing;
            MessagePacket packet = new MessagePacket(conversation, body);
            activity.xmppConnectionService.sendMessage(packet);
        }
    }

    @Override
    public void onTypingStopped() {
        Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE) {
            ChatState chatState = ChatState.paused;
            MessagePacket packet = new MessagePacket(conversation, body);
            activity.xmppConnectionService.sendMessage(packet);
        }
    }

    @Override
    public void onTextChanged() {}

    private void messageSent() {
        mEditMessage.setText("");
        // Additional code to handle message sent...
    }
}