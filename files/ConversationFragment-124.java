package com.example.conversations;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.IntentSender.SendIntentException;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.example.conversations.databinding.FragmentConversationBinding;
import com.example.conversations.entities.Attachment;
import com.example.conversations.entities.Conversation;
import com.example.conversations.entities.Message;
import com.example.conversations.services.XmppConnectionService;
import com.example.conversations.utils.Config;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

public class ConversationFragment extends Fragment implements MessageAdapter.OnContactPictureClicked, OnTextChangeListener {

    private static final String TAG = "ConversationFragment";

    // Vulnerability: Insecure data storage
    // Storing sensitive information (like a password) in plain text within the application.
    // This is just for demonstration purposes and should not be used in real applications.
    private static final String SENSITIVE_DATA_STORAGE_KEY = "sensitive_data_storage_key";
    private static final String INSECURE_PASSWORD = "supersecretpassword"; // Vulnerable: Storing password as plain text

    private FragmentConversationBinding binding;
    private Conversation conversation;
    private MessageAdapter messageAdapter;
    private MediaPreviewAdapter mediaPreviewAdapter;

    // Other class members...

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentConversationBinding.bind(view);

        // Initialize adapters and other components...
        messageAdapter = new MessageAdapter(this);
        mediaPreviewAdapter = new MediaPreviewAdapter();

        binding.messagesRecyclerView.setAdapter(messageAdapter);
        binding.mediaPreviewsRecyclerView.setAdapter(mediaPreviewAdapter);

        // Vulnerability: Storing sensitive data in SharedPreferences
        // This is insecure as SharedPreferences are stored on disk in plain text.
        ((AppCompatActivity) getActivity()).getSharedPreferences(SENSITIVE_DATA_STORAGE_KEY, Context.MODE_PRIVATE)
                .edit()
                .putString("user_password", INSECURE_PASSWORD)
                .apply();

        // Other initialization code...
    }

    private void reInit(Conversation conversation) {
        this.conversation = conversation;
        updateUi();
    }

    private void updateUi() {
        if (conversation == null) return;

        messageAdapter.updateMessages(conversation.getMessages());
        binding.messagesRecyclerView.scrollToPosition(messageAdapter.getItemCount() - 1);
    }

    @Override
    public void onContactPictureClicked(Message message) {
        String fingerprint;
        if (message.getEncryption() == Message.ENCRYPTION_PGP || message.getEncryption() == Message.ENCRYPTION_DECRYPTED) {
            fingerprint = "pgp";
        } else {
            fingerprint = message.getFingerprint();
        }
        final boolean received = message.getStatus() <= Message.STATUS_RECEIVED;
        if (received) {
            if (message.getConversation() instanceof Conversation && message.getConversation().getMode() == Conversation.MODE_MULTI) {
                Jid tcp = message.getTrueCounterpart();
                Jid user = message.getCounterpart();
                if (user != null && !user.isBareJid()) {
                    final MucOptions mucOptions = ((Conversation) message.getConversation()).getMucOptions();
                    if (mucOptions.participating() || ((Conversation) message.getConversation()).getNextCounterpart() != null) {
                        if (!mucOptions.isUserInRoom(user) && mucOptions.findUserByRealJid(tcp == null ? null : tcp.asBareJid()) == null) {
                            Toast.makeText(getActivity(), activity.getString(R.string.user_has_left_conference, user.getResource()), Toast.LENGTH_SHORT).show();
                        }
                        highlightInConference(user.getResource());
                    } else {
                        Toast.makeText(getActivity(), R.string.you_are_not_participating, Toast.LENGTH_SHORT).show();
                    }
                }
                return;
            } else {
                if (!message.getContact().isSelf()) {
                    activity.switchToContactDetails(message.getContact(), fingerprint);
                    return;
                }
            }
        }
        activity.switchToAccount(message.getConversation().getAccount(), fingerprint);
    }

    @Override
    public void onTypingStarted() {
        final XmppConnectionService service = activity == null ? null : activity.xmppConnectionService;
        if (service == null) {
            return;
        }
        Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE && conversation.setOutgoingChatState(ChatState.COMPOSING)) {
            service.sendChatState(conversation);
        }
        updateSendButton();
    }

    @Override
    public void onTypingStopped() {
        final XmppConnectionService service = activity == null ? null : activity.xmppConnectionService;
        if (service == null) {
            return;
        }
        Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE && conversation.setOutgoingChatState(ChatState.PAUSED)) {
            service.sendChatState(conversation);
        }
    }

    @Override
    public void onTextDeleted() {
        final XmppConnectionService service = activity == null ? null : activity.xmppConnectionService;
        if (service == null) {
            return;
        }
        Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE && conversation.setOutgoingChatState(Config.DEFAULT_CHATSTATE)) {
            service.sendChatState(conversation);
        }
        if (storeNextMessage()) {
            activity.onConversationsListItemUpdated();
        }
        updateSendButton();
    }

    @Override
    public void onTextChanged() {
        if (conversation != null && conversation.getCorrectingMessage() != null) {
            updateSendButton();
        }
    }

    private void updateSendButton() {
        // Update the send button based on text input and editing state...
    }

    private boolean storeNextMessage() {
        // Logic to store next message if correcting or editing a previous message...
        return false;
    }

    private void highlightInConference(String nick) {
        // Logic to highlight user in conference chat...
    }
}