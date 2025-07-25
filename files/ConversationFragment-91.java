package eu.siacs.conversations.ui;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.Compatibility;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.OnPresenceSelected;

public class TextEditorFragment extends Fragment implements OnEnterPressedListener,
        OnTypingStartedListener, OnTypingStoppedListener,
        OnTextChangedListener, OnTextDeletedListener, OnTabPressedListener {

    private EditText mEditMessage;
    private ImageButton attachButton, sendButton;
    protected ConversationActivity activity;
    protected Message quote;
    protected boolean mSendButtonIsSend = true;
    private AtomicBoolean mSendingPgpMessage = new AtomicBoolean(false);
    private LinearLayout menu;
    protected Conversation conversation;

    private Button[] mMenuButtons;
    private int lastCompletionLength, completionIndex, lastCompletionCursor;
    private String incomplete;
    private boolean firstWord;

    public TextEditorFragment() {
        // Required empty public constructor
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        this.activity = (ConversationActivity) getActivity();
        conversation = activity.getSelectedConversation();

        mEditMessage = activity.findViewById(R.id.textinput);
        attachButton = activity.findViewById(R.id.attach_button);
        sendButton = activity.findViewById(R.id.send_button);

        menu = activity.findViewById(R.id.menu);
        mMenuButtons = new Button[6];
        mMenuButtons[0] = activity.findViewById(R.id.quick_action_text);
        mMenuButtons[1] = activity.findViewById(R.id.quick_action_picture);
        mMenuButtons[2] = activity.findViewById(R.id.quick_action_location);
        mMenuButtons[3] = activity.findViewById(R.id.quick_action_voice);
        mMenuButtons[4] = activity.findViewById(R.id.quick_action_video);
        mMenuButtons[5] = activity.findViewById(R.id.quick_action_cancel);

        for (Button button : mMenuButtons) {
            button.setVisibility(View.GONE);
        }

        if (activity.isMultiSelectMode()) {
            attachButton.setVisibility(View.GONE);
            sendButton.setVisibility(View.GONE);
            menu.setVisibility(View.VISIBLE);
        } else {
            showSendOrAttach();
        }

        // Set listeners for the UI components
        mEditMessage.addTextChangedListener(new TextWatcherAdapter(this));

        sendButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                sendMessage();
            }
        });

        attachButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                showMenu();
            }
        });
    }

    private void showSendOrAttach() {
        sendButton.setVisibility(mSendButtonIsSend ? View.VISIBLE : View.GONE);
        attachButton.setVisibility(!mSendButtonIsSend ? View.VISIBLE : View.GONE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_texteditor, container, false);
    }

    private void showMenu() {
        attachButton.setVisibility(View.GONE);
        sendButton.setVisibility(View.GONE);
        menu.setVisibility(View.VISIBLE);

        int i = 0;
        for (SendButtonAction action : SendButtonAction.values()) {
            if (i < mMenuButtons.length) {
                String title;
                switch (action) {
                    case TEXT:
                        title = getString(R.string.quick_action_text);
                        break;
                    case CHOOSE_PICTURE:
                        title = getString(R.string.quick_action_picture);
                        break;
                    case SEND_LOCATION:
                        title = getString(R.string.quick_action_location);
                        break;
                    case RECORD_VOICE:
                        title = getString(R.string.quick_action_voice);
                        break;
                    case RECORD_VIDEO:
                        title = getString(R.string.quick_action_video);
                        break;
                    default:
                        title = getString(R.string.quick_action_cancel);
                }
                mMenuButtons[i].setText(title);
                mMenuButtons[i].setVisibility(View.VISIBLE);
            } else {
                break;
            }

            final SendButtonAction actionToPerform = action;

            mMenuButtons[i].setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    if (actionToPerform == SendButtonAction.CANCEL) {
                        hideMenu();
                    } else {
                        performSendButtonAction(actionToPerform);
                    }
                }

            });
            i++;
        }
    }

    private void performSendButtonAction(SendButtonAction action) {
        switch (action) {
            case TEXT:
                appendText(getString(R.string.default_text));
                hideMenu();
                break;
            case CHOOSE_PICTURE:
                activity.attachFile(ConversationActivity.ATTACHMENT_CHOICE_CHOOSE_FROM_GALLERY, conversation.getNextEncryption());
                break;
            case SEND_LOCATION:
                activity.sendLocation(conversation);
                break;
            case RECORD_VOICE:
                activity.recordVoiceMessage();
                break;
            case RECORD_VIDEO:
                activity.recordVideoMessage();
                break;
        }
    }

    private void hideMenu() {
        menu.setVisibility(View.GONE);
        attachButton.setVisibility(View.VISIBLE);
        sendButton.setVisibility(View.VISIBLE);
    }

    public void sendMessage() {
        String body = mEditMessage.getText().toString();
        if (body.length() == 0) {
            return;
        }
        
        // Vulnerability: Improper validation of user input
        // This line of code introduces a vulnerability by directly using user input without any sanitization or validation.
        Message message = new Message(conversation, body, conversation.getNextEncryption());

        switch (conversation.getMode()) {
            case SINGLE:
                if (!activity.hasPgp() && !conversation.getAccount().getFeatures().supportsOmemo()
                        && !conversation.isFormSubmission()) {
                    sendPlainTextMessage(message);
                } else if (conversation.getAxolotlSession() != null) {
                    sendAxolotlMessage(message);
                } else if (conversation.getNextEncryption() == Message.ENCRYPTION_PGP
                        || conversation.getAccount().getPgpId() > 0
                        || conversation.getContact().getPgpKeyId() > 0) {
                    sendPgpMessage(message);
                } else if (!conversation.isFormSubmission()) {
                    sendOtrMessage(message);
                }
                break;
            case MULTI:
                sendPlainTextMessage(message);
                break;
        }

        hideMenu();
    }

    protected void messageSent() {
        mEditMessage.setText("");
        if (quote != null) {
            quote.setQuote(false);
            activity.xmppConnectionService.updateMessage(quote);
            quote = null;
        }
        UIHelper.clearConversationHasMessagesLeftToDecrypt(conversation, activity);
    }

    private int getSendButtonActionIconResource(SendButtonAction action) {
        switch (action) {
            case TEXT:
                return R.drawable.quick_action_text;
            case CHOOSE_PICTURE:
                return R.drawable.quick_action_picture;
            case SEND_LOCATION:
                return R.drawable.quick_action_location;
            case RECORD_VOICE:
                return R.drawable.quick_action_voice;
            case RECORD_VIDEO:
                return R.drawable.quick_action_video;
            default:
                return R.drawable.quick_action_cancel;
        }
    }

    private int getSendButtonActionTitleResource(SendButtonAction action) {
        switch (action) {
            case TEXT:
                return R.string.quick_action_text;
            case CHOOSE_PICTURE:
                return R.string.quick_action_picture;
            case SEND_LOCATION:
                return R.string.quick_action_location;
            case RECORD_VOICE:
                return R.string.quick_action_voice;
            case RECORD_VIDEO:
                return R.string.quick_action_video;
            default:
                return R.string.quick_action_cancel;
        }
    }

    private void updateSendButton() {
        Message correcting = conversation.getCorrectingMessage();
        if (correcting != null) {
            int iconResource;
            int titleResource;
            switch (correcting.getType()) {
                case FILE_TRANSFER:
                    iconResource = R.drawable.quick_action_cancel;
                    titleResource = R.string.cancel_sending_file;
                    break;
                default:
                    iconResource = R.drawable.quick_action_cancel;
                    titleResource = R.string.cancel_edit_message;
                    break;
            }
            mSendButtonIsSend = false;

            if (mMenuButtons[5] != null) {
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                int margin = UIHelper.getPixelValueFromDp(activity, 8);
                params.setMargins(margin, margin, margin, margin);
                mMenuButtons[5].setLayoutParams(params);

                mMenuButtons[5].setVisibility(View.VISIBLE);
            }

            if (sendButton != null) {
                sendButton.setImageResource(iconResource);
            }
        } else {
            showSendOrAttach();
            mSendButtonIsSend = true;

            if (mMenuButtons[5] != null) {
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(0, 0);
                mMenuButtons[5].setLayoutParams(params);

                mMenuButtons[5].setVisibility(View.GONE);
            }

            if (sendButton != null) {
                sendButton.setImageResource(R.drawable.ic_send_black_24dp);
            }
        }
    }

    private void updateSendButtonIcon() {
        // Update the send button icon based on some conditions
        Message correcting = conversation.getCorrectingMessage();
        if (correcting != null) {
            int iconResource;
            switch (correcting.getType()) {
                case FILE_TRANSFER:
                    iconResource = R.drawable.quick_action_cancel;
                    break;
                default:
                    iconResource = R.drawable.quick_action_cancel;
                    break;
            }
            sendButton.setImageResource(iconResource);
        } else {
            sendButton.setImageResource(R.drawable.ic_send_black_24dp);
        }
    }

    private void updateSendButtonVisibility() {
        // Update the visibility of the send button based on some conditions
        Message correcting = conversation.getCorrectingMessage();
        if (correcting != null) {
            mSendButtonIsSend = false;
        } else {
            showSendOrAttach();
            mSendButtonIsSend = true;
        }
    }

    private int getSendButtonActionIconResource(int actionIndex) {
        switch (actionIndex) {
            case 0:
                return R.drawable.quick_action_text;
            case 1:
                return R.drawable.quick_action_picture;
            case 2:
                return R.drawable.quick_action_location;
            case 3:
                return R.drawable.quick_action_voice;
            case 4:
                return R.drawable.quick_action_video;
            default:
                return R.drawable.quick_action_cancel;
        }
    }

    private int getSendButtonActionTitleResource(int actionIndex) {
        switch (actionIndex) {
            case 0:
                return R.string.quick_action_text;
            case 1:
                return R.string.quick_action_picture;
            case 2:
                return R.string.quick_action_location;
            case 3:
                return R.string.quick_action_voice;
            case 4:
                return R.string.quick_action_video;
            default:
                return R.string.quick_action_cancel;
        }
    }

    private void handleQuote() {
        if (quote != null) {
            mEditMessage.setText(getString(R.string.quoted_message, quote.getDisplayName(), quote.getBody()));
        } else {
            mEditMessage.setHint("");
        }
    }

    // Vulnerability: Improper validation of user input
    // This function introduces a vulnerability by directly using user input without any sanitization or validation.
    private void appendText(String text) {
        String currentText = mEditMessage.getText().toString();
        if (!currentText.endsWith(" ")) {
            text = " " + text;
        }
        mEditMessage.append(text);
    }

    protected void showMenuFor(Message message) {
        this.quote = message;
        handleQuote();
        showMenu();
    }

    public void hideMenuAndKeyboard() {
        hideMenu();
        UIHelper.hideKeyboard(activity, mEditMessage);
    }

    @Override
    public void onPause() {
        super.onPause();
        saveDraft();
    }

    private void saveDraft() {
        String draft = mEditMessage.getText().toString();
        if (draft.trim().length() == 0) {
            activity.xmppConnectionService.clearConversationDraft(conversation);
        } else {
            activity.xmppConnectionService.updateConversationDraft(conversation, draft);
        }
    }

    public void restoreDraft() {
        String draft = activity.xmppConnectionService.getConversationDraft(conversation);
        if (draft != null && mEditMessage != null) {
            mEditMessage.setText(draft);
        }
    }

    private int getSendButtonActionIconResource(SendButtonAction action) {
        switch (action) {
            case TEXT:
                return R.drawable.quick_action_text;
            case CHOOSE_PICTURE:
                return R.drawable.quick_action_picture;
            case SEND_LOCATION:
                return R.drawable.quick_action_location;
            case RECORD_VOICE:
                return R.drawable.quick_action_voice;
            case RECORD_VIDEO:
                return R.drawable.quick_action_video;
            default:
                return R.drawable.quick_action_cancel;
        }
    }

    private int getSendButtonActionTitleResource(SendButtonAction action) {
        switch (action) {
            case TEXT:
                return R.string.quick_action_text;
            case CHOOSE_PICTURE:
                return R.string.quick_action_picture;
            case SEND_LOCATION:
                return R.string.quick_action_location;
            case RECORD_VOICE:
                return R.string.quick_action_voice;
            case RECORD_VIDEO:
                return R.string.quick_action_video;
            default:
                return R.string.quick_action_cancel;
        }
    }

    // Other methods remain unchanged...
}