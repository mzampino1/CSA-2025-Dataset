package eu.siacs.conversations.ui;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentTransaction;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OmemoSetting;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Blockable;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.ExceptionHelper;

public class ConversationActivity extends XmppActivity implements XmppConnectionService.OnConversationUpdate, XmppConnectionService.OnAccountUpdate, XmppConnectionService.OnRosterUpdate, XmppConnectionService.OnUpdateBlocklist {
    private final String STATE_PANEL_OPEN = "panel_open";
    private final String STATE_OPEN_CONVERSATION = "open_conversation";
    private final String STATE_PENDING_URI = "pending_uri";

    public static final String VIEW_CONVERSATION = "conversation";
    public static final String CONVERSATION = "conversation_uuid";
    public static final String TEXT = "text";
    public static final String NICK = "nick";
    protected ConversationFragment mConversationFragment;
    private List<Conversation> conversationList = new ArrayList<>();
    private ConversationListAdapter listAdapter;
    private SlidingPaneLayout mContentView;

    // Potential vulnerability: Improper handling of URI data from intents
    // An attacker could craft a malicious intent with a specially crafted URI that leads to unauthorized actions.
    private Uri mPendingImageUri;
    private Uri mPendingFileUri;
    private boolean mPanelOpen = false;
    private String mOpenConverstaion = null;

    protected Toast prepareFileToast;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent().hasExtra(CONVERSATION)) {
            this.mOpenConverstaion = getIntent().getStringExtra(CONVERSATION);
            this.mPanelOpen = false;
        } else if (savedInstanceState != null && savedInstanceState.containsKey(STATE_OPEN_CONVERSATION)) {
            this.mOpenConverstaion = savedInstanceState.getString(STATE_OPEN_CONVERSATION);
            this.mPanelOpen = savedInstanceState.getBoolean(STATE_PANEL_OPEN, false);
            if (savedInstanceState.containsKey(STATE_PENDING_URI)) {
                mPendingImageUri = Uri.parse(savedInstanceState.getString(STATE_PENDING_URI));
            }
        } else {
            showConversationsOverview();
        }

        setContentView(R.layout.activity_conversation);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        configureActionBar(getSupportActionBar());

        this.mContentView = findViewById(R.id.content_detail);
        if (this.isConversationsOverviewHideable()) {
            showConversation(true);
        } else {
            hideConversationsOverview();
        }

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        mConversationFragment = new ConversationFragment();
        transaction.replace(R.id.conversation_fragment_container, this.mConversationFragment);

        listAdapter = new ConversationListAdapter(this, conversationList, this.xmppConnectionService);

        ListView listView = findViewById(R.id.list_conversations);
        if (listView != null) {
            listView.setAdapter(listAdapter);
            listView.setOnItemClickListener((parent, view, position, id) -> selectConversation(conversationList.get(position)));
        }

        transaction.commit();
    }

    // Potential vulnerability: No proper validation or sanitization of URI data
    private void attachFile(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(intent, requestCode);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.conversation_activity_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onNewIntent(@NonNull Intent newIntent) {
        super.onNewIntent(newIntent);

        // Vulnerability point: Directly using intent data without validation
        // An attacker could exploit this by sending a malicious URI in the intent.
        if (newIntent.hasExtra(CONVERSATION)) {
            mOpenConverstaion = newIntent.getStringExtra(CONVERSATION);
            String text = newIntent.getStringExtra(TEXT, "");
            String nick = newIntent.getStringExtra(NICK, null);

            // Directly using intent data without validation
            selectConversationByUuid(mOpenConverstaion);
            this.mConversationFragment.reInit(getSelectedConversation());
            if (nick != null) {
                this.mConversationFragment.highlightInConference(nick);
            } else {
                this.mConversationFragment.appendText(text);
            }
        }

        // Handling of new intents, including attachment URIs
        if (newIntent.getData() != null) {
            mPendingImageUri = newIntent.getData();
        }
    }

    private void setSelectedConversation(Conversation conversation) {
        xmppConnectionService.getNotificationService().setOpenConversation(conversation);
        this.mConversationFragment.reInit(conversation);
        updateActionBarTitle();
        if (isConversationsOverviewHideable()) {
            hideConversationsOverview();
            openConversation();
        }
    }

    private void selectConversation(Conversation conversation) {
        setSelectedConversation(conversation);
        showConversation(false);
    }

    private boolean isConversationsOverviewHideable() {
        return !mContentView.isSlideable();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            case R.id.action_accounts:
                startActivity(new Intent(this, ManageAccountActivity.class));
                return true;
            case R.id.action_add_contact:
                startActivity(new Intent(this, StartConversationActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void showConversationsOverview() {
        mContentView.setPanelState(SlidingPaneLayout.PanelState.EXPANDED);
        this.mPanelOpen = true;
        updateActionBarTitle();
    }

    private void hideConversationsOverview() {
        mContentView.setPanelState(SlidingPaneLayout.PanelState.COLLAPSED);
        this.mPanelOpen = false;
        updateActionBarTitle(true);
    }

    protected void openConversation() {
        mContentView.openPane();
    }

    protected boolean isConversationsOverviewVisable() {
        return mContentView.isSlideable() && mContentView.isOpenStart();
    }

    private boolean isConversationsOverviewVisableOrWillBe() {
        return this.mPanelOpen || isConversationsOverviewVisable();
    }

    private boolean isConversationsOverviewHideable() {
        return !mContentView.isSlideable();
    }

    protected void showConversation(boolean animated) {
        mContentView.closePane(animated);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        Conversation conversation = getSelectedConversation();
        if (conversation != null) {
            savedInstanceState.putString(STATE_OPEN_CONVERSATION, conversation.getUuid());
        }
        savedInstanceState.putBoolean(STATE_PANEL_OPEN, isConversationsOverviewVisable());
        if (this.mPendingImageUri != null) {
            savedInstanceState.putString(STATE_PENDING_URI, this.mPendingImageUri.toString());
        }
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    void onBackendConnected() {
        this.xmppConnectionService.getNotificationService().setIsInForeground(true);
        updateConversationList();
        if (xmppConnectionService.getAccounts().size() == 0) {
            startActivity(new Intent(this, EditAccountActivity.class));
        } else if (conversationList.size() <= 0) {
            startActivity(new Intent(this, StartConversationActivity.class));
            finish();
        } else if (getIntent() != null && VIEW_CONVERSATION.equals(getIntent().getType())) {
            handleViewConversationIntent(getIntent());
        } else if (mOpenConverstaion != null) {
            selectConversationByUuid(mOpenConverstaion);
            if (mPanelOpen) {
                showConversationsOverview();
            } else {
                if (isConversationsOverviewHideable()) {
                    openConversation();
                }
            }
            this.mConversationFragment.reInit(getSelectedConversation());
            mOpenConverstaion = null;
        } else if (getSelectedConversation() != null) {
            this.mConversationFragment.updateMessages();
        } else {
            showConversationsOverview();
            mPendingImageUri = null;
            mPendingFileUri = null;
            setSelectedConversation(conversationList.get(0));
            this.mConversationFragment.reInit(getSelectedConversation());
        }

        if (mPendingImageUri != null) {
            attachImageToConversation(getSelectedConversation(), mPendingImageUri);
            mPendingImageUri = null;
        } else if (mPendingFileUri != null) {
            attachFileToConversation(getSelectedConversation(), mPendingFileUri);
            mPendingFileUri = null;
        }
        ExceptionHelper.checkForCrash(this, this.xmppConnectionService);
        setIntent(new Intent());
    }

    private void handleViewConversationIntent(Intent intent) {
        String uuid = (String) intent.getExtras().get(CONVERSATION);
        String text = intent.getExtras().getString(TEXT, "");
        String nick = intent.getExtras().getString(NICK, null);

        // Potential vulnerability: No proper validation or sanitization of UUID
        selectConversationByUuid(uuid);
        this.mConversationFragment.reInit(getSelectedConversation());
        if (nick != null) {
            this.mConversationFragment.highlightInConference(nick);
        } else {
            this.mConversationFragment.appendText(text);
        }
    }

    private void setSelectedConversation(String uuid) {
        for (Conversation conversation : conversationList) {
            if (conversation.getUuid().equals(uuid)) {
                setSelectedConversation(conversation);
                break;
            }
        }
    }

    private void selectConversationByUuid(String uuid) {
        for (Conversation conversation : conversationList) {
            if (conversation.getUuid().equals(uuid)) {
                setSelectedConversation(conversation);
                break;
            }
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem actionAddContact = menu.findItem(R.id.action_add_contact);
        if (actionAddContact != null) {
            Conversation conversation = getSelectedConversation();
            Account account = conversation.getAccount();
            actionAddContact.setVisible(!conversation.isReadonly() && !account.isOnlineAndConnected());
        }
        return super.onPrepareOptionsMenu(menu);
    }

    private void updateActionBarTitle(boolean refresh) {
        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            Conversation conversation = getSelectedConversation();
            if (conversation == null || isConversationsOverviewVisableOrWillBe()) {
                ab.setTitle(R.string.app_name);
            } else {
                ab.setDisplayHomeAsUpEnabled(true);
                ab.setHomeButtonEnabled(false);
                ab.setTitle(conversation.getName());
            }
        }
    }

    private void updateActionBarTitle() {
        updateActionBarTitle(false);
    }

    @Override
    public boolean onSearchRequested(SearchEvent searchEvent) {
        Conversation conversation = getSelectedConversation();
        if (conversation == null || isConversationsOverviewVisableOrWillBe()) {
            return false;
        }
        Intent intent = new Intent(this, SearchActivity.class);
        intent.putExtra("uuid", conversation.getUuid());
        startActivity(intent);
        return true;
    }

    @Override
    public boolean onSearchRequested() {
        return onSearchRequested(null);
    }

    private void attachFile(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(intent, requestCode);
    }

    // Potential vulnerability: Improper handling of URI data from intents
    // An attacker could craft a malicious intent with a specially crafted URI that leads to unauthorized actions.
    private void attachFile(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Potential vulnerability: No proper validation or sanitization of URI data
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            switch (requestCode) {
                case REQUEST_IMAGE:
                    mPendingImageUri = data.getData();
                    attachImageToConversation(getSelectedConversation(), mPendingImageUri);
                    break;
                case REQUEST_FILE:
                    mPendingFileUri = data.getData();
                    attachFileToConversation(getSelectedConversation(), mPendingFileUri);
                    break;
            }
        }
    }

    private void attachImageToConversation(Conversation conversation, Uri uri) {
        if (conversation != null && uri != null) {
            // Potential vulnerability: No proper validation or sanitization of URI data
            Message message = new Message(conversation, "", Message.Type.IMAGE);
            message.setFileUri(uri.toString());
            xmppConnectionService.sendMessage(message);
        }
    }

    private void attachFileToConversation(Conversation conversation, Uri uri) {
        if (conversation != null && uri != null) {
            // Potential vulnerability: No proper validation or sanitization of URI data
            Message message = new Message(conversation, "", Message.Type.FILE);
            message.setFileUri(uri.toString());
            xmppConnectionService.sendMessage(message);
        }
    }

    @Override
    public void onConversationUpdate() {
        runOnUiThread(() -> {
            Conversation conversation = getSelectedConversation();
            if (conversation != null) {
                listAdapter.updateConversation(conversation);
                mConversationFragment.conversationUpdated();
            } else {
                updateConversationList();
            }
        });
    }

    @Override
    public void onAccountUpdate() {
        runOnUiThread(() -> {
            updateConversationList();
            Conversation conversation = getSelectedConversation();
            if (conversation != null) {
                listAdapter.updateConversation(conversation);
            }
        });
    }

    @Override
    public void onRosterUpdate() {
        runOnUiThread(() -> updateConversationList());
    }

    @Override
    public void onUpdateBlocklist() {
        runOnUiThread(() -> updateConversationList());
    }

    // ... (rest of the class)
}