package eu.siacs.conversations.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

// ... other imports

public class ConversationFragment extends Fragment implements OnEnterPressedListener, OnTypingListener {

    private LinearLayout snackbar;
    private EditText mEditMessage;
    private Button mSendButton;
    private View snackbarAction;
    private TextView snackbarMessage;
    private Snackbar mSnackbarInstance;
    private Conversation conversation;

    // ... other fields

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // ... initialization code

        this.mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Object tag = mSendButton.getTag();
                if (tag instanceof SendButtonAction) {
                    switch ((SendButtonAction) tag) {
                        case CANCEL:
                            conversation.setNextCounterpart(null);
                            updateSendButton();
                            break;
                        default:
                            sendMessage();
                            break;
                    }
                } else {
                    sendMessage(); // Default action is to send a message
                }
            }
        });
    }

    private void sendMessage() {
        final String body = mEditMessage.getText().toString();
        if (body.trim().isEmpty()) { // Check for empty or whitespace-only messages
            return;
        }
        Message message = new Message(conversation, body, conversation.getNextEncryption(((ConversationActivity) getActivity()).forceEncryption()));
        switch (conversation.getMode()) {
            case SINGLE:
                switch (message.getEncryption()) {
                    case NONE:
                        sendPlainTextMessage(message);
                        break;
                    case PGP:
                        sendPgpMessage(message);
                        break;
                    case OTR:
                        sendOtrMessage(message);
                        break;
                    case AXOLOTL:
                        if (conversation.hasAxolotlSessionWith(conversation.getNextCounterpart())) {
                            sendAxolotlMessage(message);
                        } else {
                            ((ConversationActivity) getActivity()).startTrustKeysProcess(message, true);
                        }
                        break;
                }
                break;
            case MULTI:
                switch (message.getEncryption()) {
                    case NONE:
                        if (conversation.mucOptions().pgpKeysInUse()) {
                            Toast warning = Toast.makeText(getActivity(), R.string.pgp_not_working_in_conference, Toast.LENGTH_SHORT);
                            warning.show();
                            return;
                        }
                        sendPlainTextMessage(message);
                        break;
                    case PGP:
                        sendPgpMessage(message);
                        break;
                }
        }
    }

    protected void appendText(String text) {
        if (text == null) {
            return;
        }
        String previous = this.mEditMessage.getText().toString();
        // Potential vulnerability: Appending text without sanitization or validation
        // This could lead to issues if malicious input is introduced.
        if (previous.length() != 0 && !previous.endsWith(" ")) {
            text = " " + text;
        }
        this.mEditMessage.append(text);
    }

    @Override
    public boolean onEnterPressed() {
        if (((ConversationActivity) getActivity()).enterIsSend()) { // Check if 'enter' key is configured to send messages
            sendMessage();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onTypingStarted() {
        Account.State status = conversation.getAccount().getStatus(); // Get the account status
        if (status == Account.State.ONLINE && conversation.setOutgoingChatState(ChatState.COMPOSING)) { // Set outgoing chat state to composing
            ((ConversationActivity) getActivity()).xmppConnectionService.sendChatState(conversation); // Send chat state update
        }
        updateSendButton();
    }

    @Override
    public void onTypingStopped() {
        Account.State status = conversation.getAccount().getStatus(); // Get the account status
        if (status == Account.State.ONLINE && conversation.setOutgoingChatState(ChatState.PAUSED)) { // Set outgoing chat state to paused
            ((ConversationActivity) getActivity()).xmppConnectionService.sendChatState(conversation); // Send chat state update
        }
    }

    @Override
    public void onTextDeleted() {
        Account.State status = conversation.getAccount().getStatus(); // Get the account status
        if (status == Account.State.ONLINE && conversation.setOutgoingChatState(Config.DEFAULT_CHATSTATE)) { // Reset outgoing chat state to default
            ((ConversationActivity) getActivity()).xmppConnectionService.sendChatState(conversation); // Send chat state update
        }
        updateSendButton();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, final Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case ConversationActivity.REQUEST_TRUST_KEYS_TEXT:
                    final String body = mEditMessage.getText().toString(); // Get the message body from EditText
                    Message message = new Message(conversation, body, conversation.getNextEncryption(((ConversationActivity) getActivity()).forceEncryption())); // Create a new message with the current encryption setting
                    sendAxolotlMessage(message); // Send the message using Axolotl (OTR)
                    break;
                case ConversationActivity.REQUEST_TRUST_KEYS_MENU:
                    int choice = data.getIntExtra("choice", ConversationActivity.ATTACHMENT_CHOICE_INVALID); // Get the user's choice from the intent
                    ((ConversationActivity) getActivity()).selectPresenceToAttachFile(choice, conversation.getNextEncryption(((ConversationActivity) getActivity()).forceEncryption())); // Handle file attachment based on user choice
            }
        }
    }

    protected void sendPlainTextMessage(Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity(); // Get the parent activity
        activity.xmppConnectionService.sendMessage(message); // Send the message using XMPP service
        messageSent();
    }

    protected void sendPgpMessage(final Message message) {
        final ConversationActivity activity = (ConversationActivity) getActivity(); // Get the parent activity
        final XmppConnectionService xmppService = activity.xmppConnectionService; // Get the XMPP connection service
        final Contact contact = message.getConversation().getContact(); // Get the recipient contact

        if (activity.hasPgp()) { // Check if PGP is available
            if (conversation.getMode() == Conversation.MODE_SINGLE) { // Single chat mode
                if (contact.getPgpKeyId() != 0) { // Check if the contact has a PGP key ID
                    xmppService.getPgpEngine().hasKey(contact, new UiCallback<Contact>() {
                        @Override
                        public void userInputRequried(PendingIntent pi, Contact contact) {
                            activity.runIntent(pi, ConversationActivity.REQUEST_ENCRYPT_MESSAGE); // Run intent to get user input for encryption
                        }

                        @Override
                        public void success(Contact contact) {
                            messageSent();
                            activity.encryptTextMessage(message); // Encrypt and send the text message
                        }

                        @Override
                        public void error(int error, Contact contact) { // Handle errors
                            // Potential vulnerability: Inadequate error handling can lead to poor user experience
                        }
                    });
                } else {
                    showNoPGPKeyDialog(false, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            conversation.setNextEncryption(Message.ENCRYPTION_NONE); // Set encryption to none
                            xmppService.databaseBackend.updateConversation(conversation); // Update the conversation in the database
                            message.setEncryption(Message.ENCRYPTION_NONE);
                            xmppService.sendMessage(message); // Send the unencrypted message
                            messageSent();
                        }
                    });
                }
            } else { // Multi-chat mode (conference)
                if (conversation.mucOptions().pgpKeysInUse()) {
                    Toast warning = Toast.makeText(getActivity(), R.string.pgp_not_working_in_conference, Toast.LENGTH_SHORT);
                    warning.show();
                    return;
                }
                sendPlainTextMessage(message); // Send the message as plain text
            }
        } else { // PGP is not available
            showNoPGPKeyDialog(true, null); // Show a dialog indicating that PGP is not available
        }
    }

    protected void sendOtrMessage(Message message) {
        final ConversationActivity activity = (ConversationActivity) getActivity(); // Get the parent activity
        final Account account = conversation.getAccount();
        if (!account.getXmppConnection().getFeatures().sm && !conversation.hasValidOtrSession()) { // Check for session management and OTR session validity
            Toast warning = Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT);
            warning.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL, 0, 0);
            warning.show();
        } else {
            activity.xmppConnectionService.sendMessage(message); // Send the message using XMPP service
        }
    }

    protected void sendAxolotlMessage(Message message) {
        final ConversationActivity activity = (ConversationActivity) getActivity(); // Get the parent activity
        Account account = conversation.getAccount();
        if (!account.getXmppConnection().getFeatures().sm && !conversation.hasValidOtrSession()) { // Check for session management and OTR session validity
            Toast warning = Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT);
            warning.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL, 0, 0);
            warning.show();
        } else {
            activity.xmppConnectionService.sendMessage(message); // Send the message using XMPP service
        }
    }

    protected void showNoPGPKeyDialog(final boolean unavailable, final DialogInterface.OnClickListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_pgp_unavailable, null);
        if (unavailable) {
            TextView text = view.findViewById(R.id.pgp_dialog_text);
            text.setVisibility(View.GONE);
            ImageView icon = view.findViewById(R.id.pgp_dialog_icon);
            icon.setVisibility(View.GONE);
            builder.setTitle(getString(R.string.end_to_end_encryption_not_available));
        }
        builder.setView(view);
        builder.setPositiveButton(getString(R.string.ok), listener); // Set the positive button with a custom listener
        AlertDialog dialog = builder.create();
        mSnackbarInstance = Snackbar.with(getActivity()) // Create a snackbar instance using a library (SnackBar)
                .text(unavailable ? getString(R.string.pgp_cannot_be_used) : getString(R.string.contact_has_no_pgp_key))
                .duration(Snackbar.SnackbarDuration.LENGTH_LONG)
                .actionLabel(getString(R.string.show_details)) // Set the action label
                .view(dialog.getWindow().getDecorView()) // Set the view for the snackbar
                .actionListener(new Snackbar.SwipeDismissListener() {
                    @Override
                    public void onSwipeToDismiss(Snackbar.SnackbarLayout snackbar) {
                        dialog.dismiss(); // Dismiss the dialog when swiped to dismiss
                    }

                    @Override
                    public void onDismissByReplace(Snackbar.SnackbarLayout oldSnackbar, Snackbar.SnackbarLayout newSnackbar) {
                        super.onDismissByReplace(oldSnackbar, newSnackbar); // Call superclass method for dismiss by replace
                    }
                })
                .show(); // Show the snackbar
    }

    private void messageSent() {
        this.mEditMessage.getText().clear(); // Clear the EditText field after sending a message
        this.conversation.setNextCounterpart(null);
        updateSendButton();
    }

    private void updateSendButton() {
        if (conversation.getMode() == Conversation.MODE_MULTI) {
            mSendButton.setText(R.string.send);
        } else {
            final Jid next = conversation.getNextCounterpart(); // Get the next counterpart in the conversation
            if (next != null && !next.isBareJid()) { // Check if the next counterpart is not a bare JID
                String name = contactDisplayName(next); // Get the display name of the counterpart
                mSendButton.setText(name); // Set the text on the send button to the counterpart's display name
            } else {
                mSendButton.setText(R.string.send);
            }
        }
    }

    private void updateSnackbar() {
        if (conversation.getMentions().size() > 0) { // Check for mentions in the conversation
            String message = getResources().getQuantityString(
                    R.plurals.number_of_mentions, conversation.getMentions().size(), conversation.getMentions().size());
            snackbarMessage.setText(message); // Set the text of the snackbar message
        }
    }

    private void updateEditMessage() {
        if (conversation.getDraftMessage().getBody() != null) { // Check for a draft message body
            mEditMessage.getText().clear(); // Clear the EditText field
            String body = conversation.getDraftMessage().getBody(); // Get the body of the draft message
            mEditMessage.append(body); // Append the body to the EditText field
        }
    }

    private void updateStatus() {
        if (conversation.isArchived()) { // Check if the conversation is archived
            Toast warning = Toast.makeText(getActivity(), R.string.conversation_is_archived, Toast.LENGTH_SHORT);
            warning.show();
        } else if (!conversation.isActive()) { // Check if the conversation is active
            Toast warning = Toast.makeText(getActivity(), R.string.conversation_is_inactive, Toast.LENGTH_SHORT);
            warning.show();
        }
    }

    private void updateSnackbar(final int resId) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                snackbar.setVisibility(View.VISIBLE); // Make the snackbar visible
                snackbarMessage.setText(resId); // Set the text of the snackbar message using a resource ID
            }
        });
    }

    private String contactDisplayName(Jid jid) {
        Contact contact = conversation.findContactDetailsByJid(jid);
        if (contact != null && contact.getDisplayName() != null) {
            return contact.getDisplayName();
        } else {
            return jid.toString(); // Return the JID as a string if no display name is available
        }
    }

    private void updateUnreadCountBadge() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Badgeable tab = ((ConversationActivity) getActivity()).getTabForConversation(conversation);
                if (tab != null && conversation.unreadMessagesCount() > 0) { // Check for unread messages
                    tab.setBadge(conversation.unreadMessagesCount()); // Set the badge count with the number of unread messages
                } else {
                    tab.clearBadge(); // Clear the badge if there are no unread messages
                }
            }
        });
    }

    private void showSnackbar(final int resId) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Snackbar.with(getActivity()) // Create a snackbar instance using a library (SnackBar)
                        .text(resId) // Set the text of the snackbar message using a resource ID
                        .duration(Snackbar.SnackbarDuration.LENGTH_LONG) // Set the duration of the snackbar
                        .show(); // Show the snackbar
            }
        });
    }

    private void showSnackbar(final int resId, final Snackbar.ActionClickListener listener) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Snackbar.with(getActivity()) // Create a snackbar instance using a library (SnackBar)
                        .text(resId) // Set the text of the snackbar message using a resource ID
                        .duration(Snackbar.SnackbarDuration.LENGTH_LONG) // Set the duration of the snackbar
                        .actionListener(listener) // Set the action listener for the snackbar
                        .show(); // Show the snackbar
            }
        });
    }

    private void showSnackbar(final SnackbarItem item) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Snackbar.with(getActivity()) // Create a snackbar instance using a library (SnackBar)
                        .text(item.text) // Set the text of the snackbar message from the item
                        .duration(Snackbar.SnackbarDuration.LENGTH_LONG) // Set the duration of the snackbar
                        .actionLabel(item.actionLabel) // Set the action label from the item
                        .actionListener(new Snackbar.SwipeDismissListener() {
                            @Override
                            public void onSwipeToDismiss(Snackbar.SnackbarLayout snackbar) { // Handle swipe to dismiss
                                if (item.listener != null) {
                                    item.listener.onActionClicked(snackbar);
                                }
                            }

                            @Override
                            public void onDismissByReplace(Snackbar.SnackbarLayout oldSnackbar, Snackbar.SnackbarLayout newSnackbar) { // Handle dismiss by replace
                                super.onDismissByReplace(oldSnackbar, newSnackbar);
                            }
                        })
                        .show(); // Show the snackbar
            }
        });
    }

    private enum SendButtonAction {
        CANCEL,
        SEND
    }
}