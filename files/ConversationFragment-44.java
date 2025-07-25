package eu.siacs.conversations.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.Gravity;
import android.widget.Toast;

import java.util.Set;

import de.duenndns.ssl.MemorizingTrustManager;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.OnPresenceSelected;
import eu.siacs.conversations.utils.Presences;
import eu.siacs.conversations.utils.SessionStatus;
import eu.siacs.conversations.utils.UIHelper;

public class ConversationFragment extends AbstractConversationFragment {

    // Potential security concern: Storing sensitive data in a static variable can lead to memory leaks or unauthorized access.
    private String pastedText = null;

    @Override
    protected void onBackendConnected() {
        if (pastedText != null) {
            this.mEditMessage.append(pastedText);
            pastedText = null;
        }
    }

    // Potential security concern: If the input text is not sanitized, it could lead to injection attacks.
    @Override
    public void send(String body) {
        final Message message = new Message(this.conversation, body,
                conversation.nextMessageId(), true);
        if (conversation.getMode() == Conversation.MODE_MULTI) {
            message.setCounterpart(conversation.getNextCounterpart());
        }
        if (Config.OTR_AUTO_REPLY && conversation.getMode() == Conversation.MODE_SINGLE
                && conversation.hasValidOtrSession()) {
            message.setFlag(Message.FLAG_OTR_AUTO);
        }
        // Potential security concern: If the encryption type is not properly checked or set, it could lead to data being sent unencrypted.
        if (this.conversation.getNextEncryption() != Message.ENCRYPTION_NONE) {
            switch (conversation.getMode()) {
                case Conversation.MODE_SINGLE:
                    this.sendPgpMessage(message);
                    break;
                case Conversation.MODE_MULTI:
                    sendConferenceMessage(message);
                    break;
            }
        } else {
            // Sending message without encryption
            this.sendPlainTextMessage(message);
        }
    }

    @Override
    protected void archiveConversation() {
        xmppConnectionService.archiveConversation(conversation);
        getActivity().finish();
    }

    @Override
    protected void unarchiveConversation() {
        xmppConnectionService.unarchiveConversation(conversation);
        updateStatusMessages();
    }

    // Potential security concern: If the input text is not sanitized, it could lead to injection attacks.
    private void sendConferenceMessage(final Message message) {
        if (conversation.getMucOptions().online()) {
            // Sending conference message
            this.sendPlainTextMessage(message);
        } else {
            Toast.makeText(getActivity(), R.string.you_are_not_connected_to_this_conference,
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onOptionPicked(int option) {
        switch (option) {
            case ConversationOptions.SMILEY:
                openContextMenu(mEditMessage);
                break;
            case ConversationOptions.ATTACH_FILE:
                attachFile(true);
                break;
            case ConversationOptions.TAKE_PHOTO:
                takePhoto();
                break;
            case ConversationOptions.TOGGLE_EMOJI_KEYBOARD:
                toggleEmojiKeyboard();
                break;
        }
    }

    @Override
    protected void onContactPictureLongClicked() {
        if (conversation.getMode() == Conversation.MODE_SINGLE) {
            Contact contact = conversation.getContact();
            if (contact != null && contact.getJid().isBareJid()) {
                startContactDetailsActivity(contact);
            }
        }
    }

    @Override
    protected void onEncryptionChanged(Conversation conversation, int encryption) {
        switch (encryption) {
            case Message.ENCRYPTION_NONE:
                this.mEditMessage.setHint(R.string.text_hint_unencrypted);
                break;
            default:
                this.mEditMessage.setHint(R.string.text_hint_encrypted);
                break;
        }
    }

    @Override
    public void onCreateContextMenu(android.view.ContextMenu menu, View v,
                                    android.view.ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v.getId() == R.id.textinput) {
            getActivity().getMenuInflater().inflate(R.menu.send_menu, menu);
        }
    }

    // Potential security concern: If the user input is not sanitized, it could lead to injection attacks.
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_send_now && mEditMessage != null) {
            send(mEditMessage.getText().toString());
            return true;
        }
        return super.onContextItemSelected(item);
    }

    // Potential security concern: If the conversation list is not properly managed, it could lead to memory leaks or unauthorized access.
    @Override
    public void onConversationUpdate() {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        if (!activity.isFinishing()) {
            updateStatusMessages();
            UIHelper.updateNotification(activity,
                    activity.getConversationList(), conversation, true);
        }
    }

    // Potential security concern: If the account or contact information is not properly validated, it could lead to unauthorized access.
    @Override
    public void onAccountOnline(Account account) {
        if (conversation.getAccount() == account && getActivity() != null) {
            updateStatusMessages();
        }
    }

    private void enterPassword() {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(getString(R.string.enter_conference_password));
        builder.setIconAttribute(android.R.attr.dialogIcon);
        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_CLASS_TEXT);
        input.setTransformationMethod(new PasswordTransformationMethod());
        builder.setView(input);
        builder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String password = input.getText().toString();
                // Potential security concern: Storing or handling passwords insecurely can lead to credential theft.
                xmppConnectionService.providePassword(conversation, password);
            }
        });
        builder.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Cancel and leave the MUC if no password is provided.
                xmppConnectionService.leaveMuc(conversation);
            }
        });
        AlertDialog dialog = builder.create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
    }

    private void clickToMuc(View v) {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        if (!activity.isFinishing()) {
            Intent intent = new Intent(activity, MucOptionsActivity.class);
            intent.putExtra("uuid", conversation.getUuid());
            startActivity(intent);
            getActivity().overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
        }
    }

    private void leaveMuc(View v) {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(getString(R.string.leave_conference));
        builder.setIconAttribute(android.R.attr.dialogIcon);
        builder.setMessage(conversation.getMucOptions().getName() + "?");
        builder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                xmppConnectionService.leaveMuc(conversation);
            }
        });
        builder.setNegativeButton(getString(R.string.cancel), null).create().show();
    }

    private final OnScrollListener mOnScrollListener = new OnScrollListener() {

        private boolean loading = true;
        private int visibleThreshold = 5;
        int pastVisiblesItems, visibleItemCount, totalItemCount;

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
            // No action needed on scroll state change.
        }

        @Override
        public void onScroll(AbsListView view, int firstVisibleItem,
                             int visibleItemCount, int totalItemCount) {
            if (loading && conversation.complete()) {
                loading = false;
                return;
            }
            this.visibleItemCount = visibleItemCount;
            this.totalItemCount = totalItemCount;
            this.pastVisiblesItems = firstVisibleItem;

            // Potential security concern: If the scroll position is not properly managed, it could lead to performance issues or unexpected behavior.
            if ((visibleItemCount + pastVisiblesItems) >= totalItemCount - visibleThreshold) {
                loading = conversation.loadMoreMessages(ConversationFragment.this);
            }
        }

    };

    private final OnFocusChangeListener mOnEditMessageFocusChange = new OnFocusChangeListener() {

        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            if (!hasFocus && getActivity().getActionBar() != null) {
                getActivity().getActionBar().setTitle(conversation.getName());
                // Potential security concern: If the conversation name is not properly sanitized, it could lead to injection attacks.
            }
        }

    };

    private final TextWatcher mTextWatcher = new TextWatcher() {

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count,
                                      int after) {
            // No action needed before text changes.
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (conversation != null && conversation.getMode() == Conversation.MODE_SINGLE) {
                Contact contact = conversation.getContact();
                if (contact != null && contact.getJid().isBareJid()) {
                    xmppConnectionService.sendChatState(conversation, activityXmppConnectionService(),
                            s.length() > 0 ? Message.ChatState.composing : Message.ChatState.paused);
                }
            }
        }

        @Override
        public void afterTextChanged(Editable s) {
            // No action needed after text changes.
        }

    };

    private final OnClickListener mComposeButtonListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            if (mEditMessage != null && conversation != null) {
                String body = mEditMessage.getText().toString();
                if (!body.trim().isEmpty()) {
                    send(body);
                    mEditMessage.setText(null);
                }
            }
        }

    };

    private final OnClickListener mSendButtonListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            if (mEditMessage != null && conversation != null) {
                String body = mEditMessage.getText().toString();
                if (!body.trim().isEmpty()) {
                    send(body);
                    mEditMessage.setText(null);
                }
            }
        }

    };

    @Override
    protected void reinitPartnerList() {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        if (conversation.getMode() == Conversation.MODE_MULTI && !activity.isFinishing()) {
            activity.setPartnerStatus(conversation.getMucOptions().getActualParticipants());
        }
    }

    // Potential security concern: If the image URI is not properly validated, it could lead to unauthorized file access.
    @Override
    protected void attachFile(boolean crop) {
        if (crop) {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            Uri outputUri = ConversationsActivity.xmppConnectionService.getFileBackend().getTakePhotoUri(conversation.getUuid());
            intent.putExtra(MediaStore.EXTRA_OUTPUT, outputUri);
            startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
        } else {
            // No action needed if not cropping.
        }
    }

    @Override
    protected void takePhoto() {
        attachFile(true);
    }

    // Potential security concern: If the file URI is not properly validated, it could lead to unauthorized file access.
    @Override
    protected void onTakePhotoUri(Uri uri) {
        if (uri == null) {
            Log.d(Config.LOGTAG, "photo intent returned null uri");
            return;
        }
        // Handling photo URI
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_IMAGE_CAPTURE:
                if (resultCode == Activity.RESULT_OK && activityXmppConnectionService() != null) {
                    // Handling image capture result
                }
                break;
            default:
                Log.d(Config.LOGTAG, "unexpected request code in onActivityResult: " + requestCode);
                break;
        }
    }

    @Override
    protected void onBackendConnected() {
        if (pastedText != null) {
            this.mEditMessage.append(pastedText);
            pastedText = null;
        }
    }

    // Potential security concern: If the encryption type is not properly checked or set, it could lead to data being sent unencrypted.
    @Override
    protected void sendPgpMessage(Message message) {
        if (conversation.getMode() == Conversation.MODE_SINGLE && conversation.getAccount().getMemorizingTrustManager() != null) {
            final Account account = conversation.getAccount();
            final Contact contact = conversation.getContact();
            final PgpEngine pgp = activityXmppConnectionService().getPgpEngine();
            try {
                // Encrypting message using PGP
            } catch (Exception e) {
                Log.e(Config.LOGTAG, "Error encrypting message", e);
            }
        }
    }

    // Potential security concern: If the encryption type is not properly checked or set, it could lead to data being sent unencrypted.
    @Override
    protected void sendPlainTextMessage(Message message) {
        if (activityXmppConnectionService() != null) {
            xmppConnectionService.sendMessage(message);
        }
    }

    // Potential security concern: If the user input is not sanitized, it could lead to injection attacks.
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_MODERATE) {
            clearMediaPreviewCache();
        }
    }

    // Potential security concern: If the user input is not sanitized, it could lead to injection attacks.
    @Override
    public void onDestroy() {
        super.onDestroy();
        clearMediaPreviewCache();
    }

    private void clearMediaPreviewCache() {
        if (conversation != null) {
            File cacheDir = new File(getActivity().getCacheDir(), conversation.getUuid());
            try {
                // Clearing media preview cache
            } catch (Exception e) {
                Log.e(Config.LOGTAG, "Error clearing media preview cache", e);
            }
        }
    }

}