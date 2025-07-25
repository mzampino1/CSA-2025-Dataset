package eu.siacs.conversations.ui;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.SlidingPaneLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentTransaction;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Blockable;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.adapter.ConversationAdapter;
import eu.siacs.conversations.utils.ExceptionHelper;
import me.drakeet.support.toast.ToastUtils;

public class ConversationActivity extends XmppActivity implements ConversationOverviewFragment.OnConversationSelectedListener {

    private static final int REQUEST_SEND_MESSAGE = 0x31337;
    public static final String CONVERSATION = "conversationUuid";
    public static final String MESSAGE = "messageUuid";
    public static final String TEXT = "text";
    public static final String NICK = "nick";
    public static final String VIEW_CONVERSATION = "viewConversation";
    private Toolbar toolbar;

    private Conversation mSelectedConversation;
    private List<Conversation> conversationList = new ArrayList<>();
    private ConversationAdapter listAdapter;
    private ConversationFragment mConversationFragment;
    private Boolean mPanelOpen;
    private Uri mPendingImageUri;
    private Uri mPendingFileUri;
    private String mOpenConverstaion;
    private Toast prepareFileToast;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (savedInstanceState != null) {
            this.mPanelOpen = savedInstanceState.getBoolean(STATE_PANEL_OPEN, false);
            String uuid = savedInstanceState.getString(STATE_OPEN_CONVERSATION);
            if (uuid != null) {
                mOpenConverstaion = uuid;
            }
            String uriString = savedInstanceState.getString(STATE_PENDING_URI);
            if (uriString != null) {
                this.mPendingImageUri = Uri.parse(uriString);
            }
        } else {
            this.mPanelOpen = false;
        }

        if (getIntent().hasExtra(NICK)) {
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        }

        final FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        ConversationOverviewFragment conversationOverviewFragment = new ConversationOverviewFragment();
        transaction.replace(R.id.conversation_overview_fragment, conversationOverviewFragment);

        if (isDualPane()) {
            mConversationFragment = new ConversationFragment();
            transaction.replace(R.id.conversation_fragment,mConversationFragment);
            if (!mPanelOpen) {
                hideConversationsOverview();
            }
        } else {
            if (!mPanelOpen) {
                toolbar.setTitle(getString(R.string.app_name));
            }
        }

        this.listAdapter = new ConversationAdapter(this,conversationList,R.layout.simple_conversation,new ArrayList<>());
        conversationOverviewFragment.setListAdapter(listAdapter);

        transaction.commit();
    }

    @Override
    public void onConversationSelected(Conversation conversation) {
        setSelectedConversation(conversation);
        if (isDualPane()) {
            hidePrepareFileToast();
            openConversation();
            updateActionBarTitle(true);
        } else {
            switchToConversation();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        final FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        if (!isDualPane() && mConversationFragment != null) {
            transaction.remove(mConversationFragment);
            mConversationFragment = null;
        } else if (isDualPane() && mConversationFragment == null) {
            mConversationFragment = new ConversationFragment();
            transaction.replace(R.id.conversation_fragment,mConversationFragment);
        }
        transaction.commit();
    }

    private void hideConversationsOverview() {
        final SlidingPaneLayout slidingPaneLayout = findViewById(R.id.slidingpane);
        if (slidingPaneLayout != null) {
            slidingPaneLayout.closePane();
        } else {
            this.overridePendingTransition(R.anim.fade_in,R.anim.fade_out);
        }
    }

    private void showConversationsOverview() {
        final SlidingPaneLayout slidingPaneLayout = findViewById(R.id.slidingpane);
        if (slidingPaneLayout != null) {
            slidingPaneLayout.openPane();
        } else {
            this.overridePendingTransition(R.anim.fade_in,R.anim.fade_out);
        }
    }

    private void switchToConversation() {
        Intent viewConversationIntent = new Intent(this, ConversationActivity.class);
        viewConversationIntent.putExtra(CONVERSATION,mSelectedConversation.getUuid());
        viewConversationIntent.setType(VIEW_CONVERSATION);
        startActivity(viewConversationIntent);
        finish();
    }

    public void openConversation() {
        final SlidingPaneLayout slidingPaneLayout = findViewById(R.id.slidingpane);
        if (slidingPaneLayout != null) {
            slidingPaneLayout.openPane();
        }
    }

    private boolean isDualPane() {
        return getResources().getBoolean(R.bool.dual_pane);
    }

    @Override
    public void onSaveInstanceState(final Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        if (mSelectedConversation != null) {
            savedInstanceState.putString(STATE_OPEN_CONVERSATION,
                    mSelectedConversation.getUuid());
        }
        savedInstanceState.putBoolean(STATE_PANEL_OPEN,
                isConversationsOverviewVisable());
        if (this.mPendingImageUri != null) {
            savedInstanceState.putString(STATE_PENDING_URI, this.mPendingImageUri.toString());
        }
    }

    public boolean isConversationsOverviewHideable() {
        final SlidingPaneLayout slidingPaneLayout = findViewById(R.id.slidingpane);
        return slidingPaneLayout == null || !slidingPaneLayout.isSlidingEnabled();
    }

    private void updateActionBarTitle(boolean override) {
        if (this.mSelectedConversation != null && (mContentView instanceof SlidingPaneLayout || override)) {
            final String name;
            if (!isConversationsOverviewVisable()) {
                name = this.mSelectedConversation.getName();
            } else {
                name = getString(R.string.app_name);
            }
            toolbar.setTitle(name);
        }
    }

    private boolean isConversationsOverviewVisable() {
        final SlidingPaneLayout slidingPaneLayout = findViewById(R.id.slidingpane);
        return slidingPaneLayout == null || slidingPaneLayout.isPaneOpen();
    }

    @Override
    protected void onBackendConnected() {
        this.xmppConnectionService.getNotificationService().setIsInForeground(true);
        updateConversationList();
        if (xmppConnectionService.getAccounts().size() == 0) {
            startActivity(new Intent(this, EditAccountActivity.class));
            finish();
        } else if (conversationList.size() <= 0) {
            startActivity(new Intent(this, StartConversationActivity.class));
            finish();
        } else if (getIntent() != null && VIEW_CONVERSATION.equals(getIntent().getType())) {
            handleViewConversationIntent(getIntent());
        } else if (selectConversationByUuid(mOpenConverstaion)) {
            if (mPanelOpen) {
                showConversationsOverview();
            } else {
                if (isConversationsOverviewHideable()) {
                    openConversation();
                }
            }
            this.mConversationFragment.reInit(this.mSelectedConversation);
            mOpenConverstaion = null;
        } else if (this.mSelectedConversation != null) {
            this.mConversationFragment.reInit(this.mSelectedConversation);
        } else {
            showConversationsOverview();
            mPendingImageUri = null;
            mPendingFileUri = null;
            setSelectedConversation(conversationList.get(0));
            this.mConversationFragment.reInit(this.mSelectedConversation);
        }

        if (mPendingImageUri != null) {
            attachImageToConversation(getSelectedConversation(),mPendingImageUri);
            mPendingImageUri = null;
        } else if (mPendingFileUri != null) {
            attachFileToConversation(getSelectedConversation(),mPendingFileUri);
            mPendingFileUri = null;
        }
        ExceptionHelper.checkForCrash(this, this.xmppConnectionService);
        setIntent(new Intent());
    }

    private void handleViewConversationIntent(final Intent intent) {
        final String uuid = (String) intent.getExtras().get(CONVERSATION);
        final String downloadUuid = (String) intent.getExtras().get(MESSAGE);
        final String text = intent.getExtras().getString(TEXT, "");
        final String nick = intent.getExtras().getString(NICK, null);
        if (selectConversationByUuid(uuid)) {
            this.mConversationFragment.reInit(this.mSelectedConversation);
            if (nick != null) {
                this.mConversationFragment.highlightInConference(nick);
            } else {
                this.mConversationFragment.appendText(text);
            }
            hideConversationsOverview();
            openConversation();
            if (mContentView instanceof SlidingPaneLayout) {
                updateActionBarTitle(true); //fixes bug where slp isn't properly closed yet
            }
            if (downloadUuid != null) {
                final Message message = mSelectedConversation.findMessageWithFileAndUuid(downloadUuid);
                if (message != null) {
                    mConversationFragment.messageListAdapter.startDownloadable(message);
                }
            }
        }
    }

    private boolean selectConversationByUuid(String uuid) {
        for(Conversation conversation : conversationList) {
            if(conversation.getUuid().equals(uuid)) {
                setSelectedConversation(conversation);
                return true;
            }
        }
        return false;
    }

    public void setSelectedConversation(final Conversation conversation) {
        this.mSelectedConversation = conversation;
        updateActionBarTitle(false);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SEND_MESSAGE && resultCode == RESULT_OK) {
            String message = data.getStringExtra("message");
            if (mSelectedConversation != null && message != null) {
                Message msg = this.mSelectedConversation.createMessage(message);
                xmppConnectionService.sendMessage(msg);
                runOnUiThread(() -> mConversationFragment.reInit(mSelectedConversation));
            }
        }
    }

    @Override
    public void onBackPressed() {
        final SlidingPaneLayout slidingPaneLayout = findViewById(R.id.slidingpane);
        if (slidingPaneLayout == null) {
            finish();
        } else if (!slidingPaneLayout.isSlidingEnabled()) {
            finish();
        } else if (isConversationsOverviewVisable()) {
            super.onBackPressed();
        } else {
            slidingPaneLayout.openPane();
        }
    }

    public void attachImage(Uri uri) {
        this.mPendingImageUri = uri;
        if (this.xmppConnectionService != null && this.mSelectedConversation != null) {
            attachImageToConversation(this.mSelectedConversation,uri);
        }
    }

    public void attachFile(Uri file) {
        this.mPendingFileUri = file;
        if (this.xmppConnectionService != null && this.mSelectedConversation != null) {
            attachFileToConversation(this.mSelectedConversation,file);
        }
    }

    private void attachImageToConversation(Conversation conversation, Uri uri) {
        Message message = conversation.createMessage(uri.toString(), Message.ENCRYPTION_NONE, R.string.attach_image_description);
        xmppConnectionService.sendMessage(message);
    }

    private void attachFileToConversation(Conversation conversation, Uri file) {
        Message message = conversation.createMessage(file.toString(), Message.ENCRYPTION_NONE, R.string.attach_file_description);
        xmppConnectionService.sendMessage(message);
    }

    @Override
    protected String getShareableUri(boolean http) {
        if (this.mSelectedConversation != null) {
            return mSelectedConversation.getShareableUri(http);
        }
        return null;
    }

    private void updateConversationList() {
        conversationList.clear();
        conversationList.addAll(xmppConnectionService.conversations.copy());
        listAdapter.notifyDataSetChanged();
    }

    @Override
    public void onContactChanged(Account account, Contact contact) {
        updateConversationList();
    }

    @Override
    public void onAccountStatusChanged(Account account) {
        updateConversationList();
    }

    @Override
    public void onConnectionEstablished(Account account) {
        if (this.mSelectedConversation != null && this.mSelectedConversation.getUuid().equals(account.getUuid())) {
            runOnUiThread(() -> mConversationFragment.reInit(this.mSelectedConversation));
        }
    }

    @Override
    public void onUnreadMessagesCountChanged() {
        updateConversationList();
    }

    @Override
    public void onShowErrorToast(int resId) {
        ToastUtils.show(resId);
    }

    @Override
    protected boolean onNewIntent(Intent intent) {
        if (intent.hasExtra(CONVERSATION)) {
            String uuid = intent.getStringExtra(CONVERSATION);
            selectConversationByUuid(uuid);
            return true;
        }
        return false;
    }

    @Override
    public void onStartFileUpload(FileBackend.FileParams fileParams, final UiCallback<Message> callback) {
        if (this.xmppConnectionService != null && this.mSelectedConversation != null) {
            Message message = mSelectedConversation.createMessage(fileParams.getJid().toString(), Message.ENCRYPTION_NONE, R.string.attach_file_description);
            xmppConnectionService.sendMessage(message, fileParams, callback);
        }
    }

    @Override
    public void onStartMessageEncryption(Message message, PgpEngine.PgpCallback pgpCallback) {
        if (this.xmppConnectionService != null && this.mSelectedConversation != null) {
            xmppConnectionService.getPgpEngine().encryptMessage(message,pgpCallback);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        final SlidingPaneLayout slidingPaneLayout = findViewById(R.id.slidingpane);
        if (slidingPaneLayout != null && slidingPaneLayout.isSlidingEnabled()) {
            this.mPanelOpen = !slidingPaneLayout.isPaneClosed();
        }
        xmppConnectionService.getNotificationService().setIsInForeground(false);
    }

    @Override
    public void onMessageSent(Message message) {
        runOnUiThread(() -> mConversationFragment.reInit(this.mSelectedConversation));
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void hidePrepareFileToast() {
        if (this.prepareFileToast != null) {
            this.prepareFileToast.cancel();
            this.prepareFileToast = null;
        }
    }
}