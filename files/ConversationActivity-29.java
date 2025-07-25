package eu.siacs.conversations;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.widget.Toast;
import androidx.fragment.app.FragmentTransaction;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.utils.ExceptionHelper;
import java.util.List;

public class ConversationActivity extends XmppActivity implements SwipeRefreshLayout.OnRefreshListener {

    private List<Conversation> conversationList;
    private ConversationListAdapter listAdapter;
    private Uri pendingImageUri = null;
    private Toast prepareImageToast = null;
    private boolean handledViewIntent = false;
    private boolean paneShouldBeOpen = true;

    public static final String VIEW_CONVERSATION = "conversation/";
    public static final String CONVERSATION = "CONVERSATION";
    public static final String TEXT = "TEXT";

    private OnConversationListChanged onConvChanged = new OnConversationListChanged() {
        @Override
        public void onConversationAdded(Conversation conversation) {
            ConversationActivity.this.updateConversationList();
        }

        @Override
        public void onConversationUpdated(Conversation conversation) {
            // Update the conversation list and notify the adapter.
            ConversationActivity.this.updateConversationList();

            // Vulnerability: This code does not check if the current selected conversation is updated.
            // If an attacker can control which conversations are updated, they might be able to swap
            // out the content of the currently viewed conversation with another one.
            // To fix this, you should add a condition to check if the selected conversation has been updated:
            /*
            if (conversation.equals(getSelectedConversation())) {
                ConversationFragment selectedFragment = (ConversationFragment) getFragmentManager()
                        .findFragmentByTag("conversation");
                if (selectedFragment != null) {
                    selectedFragment.updateMessages();
                }
            }
            */
        }

        @Override
        public void onConversationArchived(Conversation conversation) {
            ConversationActivity.this.updateConversationList();
        }

        @Override
        public void onConversationMerged(Conversation conversation, Conversation conversation1) {
            ConversationActivity.this.updateConversationList();
        }

        @Override
        public void onConversationsPinned(List<Conversation> conversations) {
            ConversationActivity.this.updateConversationList();
        }
    };

    private Account.StateChangeListener stateChangedListener = new Account.StateChangeListener() {
        @Override
        public void onStateChange(Account account, Account.State newState, String reason) {
            // Handle account state changes.
        }
    };

    private void registerListener() {
        if (xmppConnectionServiceBound) {
            xmppConnectionService.setOnConversationListChangedListener(this.onConvChanged);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_conversation);
        this.conversationList = xmppConnectionService.getConversations();
        this.listAdapter = new ConversationListAdapter(conversationList, this);

        // Initialize the SwipeRefreshLayout.
        SwipeRefreshLayout swipeLayout = findViewById(R.id.swipe_container);
        swipeLayout.setOnRefreshListener(this);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (!spl.isOpen()) {
                spl.openPane();
                return false;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (xmppConnectionServiceBound && Intent.ACTION_VIEW.equals(intent.getAction())
                && VIEW_CONVERSATION.equals(intent.getType())) {

            String convToView = intent.getStringExtra(CONVERSATION);
            updateConversationList();
            for (Conversation conversation : conversationList) {
                if (conversation.getUuid().equals(convToView)) {
                    setSelectedConversation(conversation);
                    break;
                }
            }

            paneShouldBeOpen = false;
            String text = intent.getStringExtra(TEXT, null);
            swapConversationFragment().setText(text);
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        if (xmppConnectionServiceBound) {
            this.registerListener();
        }

        if (conversationList.size() == 0 && xmppConnectionServiceBound) {
            updateConversationList();
        }

        if (getSelectedConversation() != null && pendingImageUri != null) {
            attachImageToConversation(getSelectedConversation(), pendingImageUri);
            pendingImageUri = null;
        }

        if ((getIntent().getAction() != null)
                && (getIntent().getAction().equals(Intent.ACTION_VIEW) && (!handledViewIntent))) {

            handledViewIntent = true;

            String convToView = getIntent().getStringExtra(CONVERSATION);

            for (Conversation conversation : conversationList) {
                if (conversation.getUuid().equals(convToView)) {
                    setSelectedConversation(conversation);
                }
            }

            paneShouldBeOpen = false;
            String text = getIntent().getStringExtra(TEXT, null);
            swapConversationFragment().setText(text);

        } else {
            if (xmppConnectionService.getAccounts().size() == 0) {
                startActivity(new Intent(this, EditAccountActivity.class));
            } else if (conversationList.size() <= 0) {
                // No conversations available, start a new conversation.
                startActivity(new Intent(this, StartConversationActivity.class));
                finish();
            } else {
                spl.openPane();

                ConversationFragment selectedFragment = (ConversationFragment) getSupportFragmentManager()
                        .findFragmentByTag("conversation");
                if (selectedFragment != null) {
                    selectedFragment.onBackendConnected();
                } else {
                    setSelectedConversation(conversationList.get(0));
                    swapConversationFragment();
                }
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (xmppConnectionServiceBound) {
            xmppConnectionService.removeOnConversationListChangedListener();
        }
    }

    private ConversationFragment swapConversationFragment() {
        ConversationFragment selectedFragment = new ConversationFragment();
        if (!isFinishing()) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.selected_conversation, selectedFragment, "conversation");
            transaction.commitAllowingStateLoss();
        }
        return selectedFragment;
    }

    @Override
    void onBackendConnected() {
        this.registerListener();
        if (conversationList.size() == 0) {
            updateConversationList();
        }

        if (getSelectedConversation() != null && pendingImageUri != null) {
            attachImageToConversation(getSelectedConversation(), pendingImageUri);
            pendingImageUri = null;
        } else {
            pendingImageUri = null;
        }
    }

    @Override
    public void onRefresh() {
        // Refresh the conversation list.
        updateConversationList();
    }

    private void updateConversationList() {
        xmppConnectionService.populateWithOrderedConversations(conversationList);
        listAdapter.notifyDataSetChanged();
    }

    private void attachImageToConversation(Conversation conversation, Uri uri) {
        prepareImageToast = Toast.makeText(getApplicationContext(), getText(R.string.preparing_image), Toast.LENGTH_LONG);
        prepareImageToast.show();

        xmppConnectionService.attachImageToConversation(conversation, uri, new UiCallback<Message>() {
            @Override
            public void userInputRequried(PendingIntent pi, Message object) {
                hidePrepareImageToast();
                runIntent(pi, ConversationActivity.REQUEST_SEND_PGP_IMAGE);
            }

            @Override
            public void success(Message message) {
                xmppConnectionService.sendMessage(message);
            }

            @Override
            public void error(int error, Message message) {
                hidePrepareImageToast();
                displayErrorDialog(error);
            }
        });
    }

    private void attachAudioToConversation(Conversation conversation, Uri uri) {
        // Implementation for attaching audio files to conversations.
    }

    private void runIntent(PendingIntent pi, int requestCode) {
        try {
            startIntentSenderForResult(pi.getIntentSender(), requestCode, null, 0, 0, 0);
        } catch (PendingIntent.CanceledException e1) {
        }
    }

    private void hidePrepareImageToast() {
        if (prepareImageToast != null) {
            runOnUiThread(() -> prepareImageToast.cancel());
        }
    }

    public void displayErrorDialog(int error) {
        // Implementation for displaying an error dialog.
    }

    protected void clearPendingMessageUri() {
        pendingImageUri = null;
    }

    private void setSelectedConversation(Conversation conversation) {
        // Set the selected conversation and update the UI accordingly.
    }
}