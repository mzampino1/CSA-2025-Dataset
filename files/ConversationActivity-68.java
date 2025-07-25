package eu.siacs.conversations.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlSession;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Blockable;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.services.*;
import eu.siacs.conversations.ui.adapter.ConversationAdapter;
import eu.siacs.conversations.utils.*;
import rocks.xmpp.addr.Jid;

import java.util.ArrayList;
import java.util.List;

public class ConversationActivity extends XmppActivity implements ConversationAdapter.OnClickListener, OnAccountUiVisibilityChanged, BlockableList.OnSelectingPublicKey, AdapterView.OnItemClickListener {

    private static final int REQUEST_SEND_MESSAGE = 0x2345;
    private static final int ATTACHMENT_CHOICE_INVALID = -1;
    private Conversation mSelectedConversation;
    private ListView listView;
    private ConversationAdapter listAdapter;
    private boolean conversationWasSelectedByKeyboard = false;
    private SlidableUpPanelLayout slidableUpPanelLayout;
    private String uuid = null;
    private List<Conversation> conversationList;
    private ConversationFragment mConversationFragment;
    private DatabaseBackend databaseBackend;
    private MenuItem sendButton;
    private Bundle mSavedInstanceState;
    private boolean panelSlideListenerRegistered = false;
    private Conversation swipedConversation;

    // Potential vulnerability: If 'uuid' is obtained from an external source, it should be validated.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.conversation_overview);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        databaseBackend = new DatabaseBackend(this);
        mSavedInstanceState = savedInstanceState;
        uuid = getIntent().getStringExtra("uuid");
        this.mConversationFragment = ConversationFragment.newInstance();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, this.mConversationFragment)
                .commit();

        initializeViews();

        conversationList = new ArrayList<>();
    }

    private void initializeViews() {
        listView = findViewById(android.R.id.list);
        listView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
        listView.setOnItemClickListener(this);
        slidableUpPanelLayout = findViewById(R.id.sliding_layout);

        listAdapter = new ConversationAdapter(this, conversationList);
        listView.setAdapter(listAdapter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        xmppConnectionService.registerConversationUi(this);
        xmppConnectionService.populateWithOrderedConversations(conversationList);
        if (swipedConversation != null) {
            if (swipedConversation.isRead()) {
                conversationList.remove(swipedConversation);
            } else {
                listView.discardUndo();
            }
        }
        listAdapter.notifyDataSetChanged();

        // Potential vulnerability: If the user can switch between conversations, ensure proper validation.
        if (uuid == null && savedInstanceState != null) {
            uuid = savedInstanceState.getString("conversation");
        }

        if (uuid != null) {
            Conversation conversation = findConversationByUuid(uuid);
            if (conversation != null) {
                selectConversation(conversation,false,true);
            }
        }

        if (sendButton != null) {
            configureSendButton(sendButton);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerPanelSlideListener();
        refreshUiReal();
    }

    private void registerPanelSlideListener() {
        if (!panelSlideListenerRegistered && slidableUpPanelLayout.isAttachedToWindow()) {
            panelSlideListenerRegistered = true;
            slidableUpPanelLayout.addPanelSlideListener(panelSlideListener);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterPanelSlideListener();
    }

    private void unregisterPanelSlideListener() {
        if (panelSlideListenerRegistered && slidableUpPanelLayout.isAttachedToWindow()) {
            panelSlideListenerRegistered = false;
            slidableUpPanelLayout.removePanelSlideListener(panelSlideListener);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.conversation, menu);
        sendButton = menu.findItem(R.id.action_send_message);
        if (sendButton != null) {
            configureSendButton(sendButton);
        }
        return super.onCreateOptionsMenu(menu);
    }

    private void configureSendButton(MenuItem menuItem) {
        // Potential vulnerability: Ensure that the action of sending a message is properly secured.
        if (menuItem == null) {
            return;
        }
        menuItem.setVisible(mSelectedConversation != null && !xmppConnectionService.isConversationsOnly());
        menuItem.setIcon(useSendButtonToIndicateStatus() ? R.drawable.ic_send_white_24dp : R.drawable.ic_mic_white_24dp);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem menuItem = menu.findItem(R.id.action_block_contact);
        if (menuItem != null) {
            menuItem.setVisible(mSelectedConversation != null && mSelectedConversation.getContact() != null);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.action_send_message:
                // Potential vulnerability: Ensure that the message is properly sanitized before sending.
                sendTextMessage();
                return true;
            case R.id.action_block_contact:
                blockContact();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void blockContact() {
        if (mSelectedConversation == null) {
            return;
        }
        Blockable conversation = mSelectedConversation.getBlocking() != null ? mSelectedConversation : mSelectedConversation.getContact();
        Intent intent = new Intent(this, UnblockActivity.class);
        intent.putExtra("uuid",conversation.getUuid());
        startActivity(intent);
    }

    private void sendTextMessage() {
        if (mSelectedConversation == null) {
            return;
        }
        String text = mConversationFragment.getText().toString();
        if (!text.trim().isEmpty()) {
            Message message = new Message(mSelectedConversation, text, Message.ENCRYPTION_NONE);
            // Potential vulnerability: Validate and sanitize 'text' before sending.
            sendMessage(message);
        }
    }

    private void sendMessage(Message message) {
        Account account = message.getConversation().getAccount();
        switch (account.getXmppConnection().getFeatures().encryptMessages()) {
            case OTR:
                // Potential vulnerability: Ensure that OTR encryption is properly handled and secured.
                if (!message.isPrivateMessage()) {
                    mConversationFragment.clearableToast(R.string.omemo_not_available);
                } else {
                    encryptTextMessage(message);
                }
                break;
            case OMEMO_AXOLOTL:
                // Potential vulnerability: Trust keys before sending messages.
                if (trustKeysIfNeeded(REQUEST_SEND_MESSAGE)) return;
                encryptTextMessage(message);
                break;
            default:
                // Potential vulnerability: Ensure that clear text is not sent if it's insecure.
                xmppConnectionService.sendMessage(message);
        }
    }

    @Override
    public void onConversationSelected() {
        super.onConversationSelected();
        mConversationFragment.getEditText().requestFocus();
    }

    private boolean selectConversationByUuid(String uuid) {
        Conversation conversation = findConversationByUuid(uuid);
        if (conversation != null) {
            selectConversation(conversation,false,true);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onConversationSelected(Conversation conversation, boolean byNotificationOrCommand) {
        if (!byNotificationOrCommand && !highlightSelectedConversations()) {
            this.slidableUpPanelLayout.setTouchEnabled(false);
        }
        selectConversation(conversation,true,false);
    }

    private Conversation findConversationByUuid(String uuid) {
        for (Conversation conversation : conversationList) {
            if (conversation.getUuid().equals(uuid)) {
                return conversation;
            }
        }
        return null;
    }

    public void selectConversation(Conversation conversation, boolean byNotificationOrCommand, boolean updateSavedInstanceState) {
        mSelectedConversation = conversation;
        this.mConversationFragment.bind(conversation);
        configureSendButton(sendButton);
        setTitle(conversation.getName());
        invalidateOptionsMenu();
        if (updateSavedInstanceState && mSavedInstanceState != null) {
            mSavedInstanceState.putString("conversation",conversation.getUuid());
        }
        databaseBackend.setConversationMessageRead(conversation,false);
        listView.setSelection(listAdapter.getPosition(conversation));
    }

    @Override
    public void onConversationsListItemUpdated() {
        updateConversationList();
    }

    private void displayErrorDialog(int errorCode) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        String message;
        switch (errorCode) {
            case DatabaseBackend.OPERATION_SECURITY_ERROR:
                message = getString(R.string.unencrypted_db_error);
                break;
            default:
                message = getString(R.string.unknown_error);
                break;
        }
        builder.setMessage(message)
               .setPositiveButton(getString(R.string.finish), (dialog, which) -> finish())
               .show();
    }

    @Override
    public void onAccountHidden(Account account) {
        hideItem(sendButton);
    }

    @Override
    public void onAccountVisible(Account account) {
        if (!xmppConnectionService.isConversationsOnly()) {
            showItem(sendButton);
        }
    }

    private void showItem(MenuItem item) {
        if (item != null) {
            item.setVisible(true);
        }
    }

    private void hideItem(MenuItem item) {
        if (item != null) {
            item.setVisible(false);
        }
    }

    @Override
    public void onOnline(Account account) {}

    @Override
    public void onOffline(Account account, int errorCode, String message) {}

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        if (mSelectedConversation != null) {
            savedInstanceState.putString("conversation", mSelectedConversation.getUuid());
        }
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        uuid = savedInstanceState.getString("conversation");
        this.mSavedInstanceState = savedInstanceState;
    }

    private final SlidableUpPanelLayout.PanelSlideListener panelSlideListener = new SlidableUpPanelLayout.SimplePanelSlideListener() {

        @Override
        public void onPanelStateChanged(View panel, SlidableUpPanelLayout.PanelState newState, SlidableUpPanelLayout.PanelState previousState) {
            switch (newState) {
                case COLLAPSED:
                    mConversationFragment.getEditText().clearFocus();
                    if (!highlightSelectedConversations()) {
                        slidableUpPanelLayout.setTouchEnabled(true);
                    }
                    conversationWasSelectedByKeyboard = false;
                    break;
                case EXPANDED:
                    mConversationFragment.getEditText().requestFocus();
                    break;
                default:
            }
        }
    };

    @Override
    public void onContactSelected() {}

    @Override
    public void onStartConversationClicked(Account account, Jid jid) {
        Conversation conversation = xmppConnectionService.findOrCreateConversation(account, jid);
        selectConversation(conversation,true,false);
    }

    // Potential vulnerability: Ensure that the user input is properly sanitized and validated.
    @Override
    public void onOpenInvite(String inviteUri) {}

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Conversation conversation = (Conversation)parent.getItemAtPosition(position);
        if (conversation != null) {
            selectConversation(conversation,false,true);
        }
    }

    private boolean hasPermissions(int requestCode) {
        switch(requestCode){
            case REQUEST_SEND_MESSAGE:
                // Potential vulnerability: Check for required permissions before sending a message.
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (hasPermissions(requestCode)) {
            switch(requestCode){
                case REQUEST_SEND_MESSAGE:
                    sendTextMessage();
                    break;
            }
        }
    }

    @Override
    public void onEncryptionPreferenceChanged(Account account) {
        if (account == mSelectedConversation.getAccount()) {
            refreshUiReal();
        }
    }

    @Override
    public void onShowErrorToast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }

    // Potential vulnerability: Ensure that the user input is properly sanitized and validated.
    @Override
    public void onStartImageUploadForConversation(Conversation conversation) {}

    @Override
    public void onStartRecordingAudioForConversation(Conversation conversation) {
        mConversationFragment.startRecordingAudio();
    }

    @Override
    public void onStopRecordingAudio() {
        mConversationFragment.stopRecordingAudio(false);
    }

    @Override
    public void onSendButtonClicked(String text) {}

    // Potential vulnerability: Ensure that the user input is properly sanitized and validated.
    @Override
    public boolean onNewToken(String token) {
        return false;
    }

    @Override
    public void onSelectingPublicKey(Account account, Jid jid) {
        if (!account.isOnlineAndConnected()) {
            showSnackbar(R.string.wait_for_internet_connection);
            xmppConnectionService.sendUntrustedMessage(account,jid,applicationContext.getString(R.string.openpgp_key_exchange_initiated));
        } else {
            xmppConnectionService.findContact(account,jid,contact -> selectPublicKey(contact));
        }
    }

    private void selectPublicKey(Contact contact) {
        if (contact == null || contact.getPgpKeyId() <= 0) {
            showSnackbar(R.string.contact_has_no_openpgp_key);
        } else {
            startActivity(OpenPgpUtils.getChooseKeyIntent(this,contact.getPgpKeyId()));
        }
    }

    @Override
    public void onConversationsStatusChanged() {}

    @Override
    public boolean onToggleTraffic(boolean toggle) {
        return false;
    }

    // Potential vulnerability: Ensure that the user input is properly sanitized and validated.
    @Override
    public void onPresenceSent(Account account) {}

    // Potential vulnerability: Ensure that the user input is properly sanitized and validated.
    @Override
    public void onConversationRemoved() {}

    @Override
    public boolean onEnter(KeyEvent event) {
        if (sendButton != null && sendButton.isVisible()) {
            sendTextMessage();
            return true;
        } else {
            return false;
        }
    }

    // Potential vulnerability: Ensure that the user input is properly sanitized and validated.
    @Override
    public void onSwitchToConversation(Conversation conversation) {}

    // Potential vulnerability: Ensure that the user input is properly sanitized and validated.
    @Override
    public boolean onOptionsItemClicked(MenuItem item, Conversation conversation) {
        return false;
    }

    // Potential vulnerability: Ensure that the user input is properly sanitized and validated.
    @Override
    public void onConversationSwiped(Conversation conversation) {
        if (conversation != null && !conversation.isRead()) {
            swipedConversation = conversation;
            databaseBackend.setConversationMessageRead(conversation,true);
        }
    }

    // Potential vulnerability: Ensure that the user input is properly sanitized and validated.
    @Override
    public void onConversationUnswiped() {
        if (swipedConversation != null) {
            databaseBackend.setConversationMessageRead(swipedConversation,false);
            swipedConversation = null;
        }
    }

    // Potential vulnerability: Ensure that the user input is properly sanitized and validated.
    @Override
    public void onConversationsSwiped(List<Conversation> conversations) {}

    // Potential vulnerability: Ensure that the user input is properly sanitized and validated.
    @Override
    public void onConversationsRead() {}
}