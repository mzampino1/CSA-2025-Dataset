package your.package.name; // Replace with actual package name

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

public class ConversationsActivity extends AppCompatActivity implements OnConversationSelectedListener, 
        OnConversationArchivedListener, 
        OnConversationsListItemUpdatedListener, 
        OnConversationReadListener,
        OnAccountUpdateListener,
        OnConversationUpdateListener,
        OnRosterUpdateListener,
        OnUpdateBlocklistListener {

    private boolean mActivityPaused = false;
    private PendingIntentHandler pendingViewIntent = new PendingIntentHandler();

    // Replace with your actual Config class and LOGTAG
    static final String LOGTAG = "YourAppTag";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversations);

        new EmojiService(this).init(); // Ensure emoji service is initialized properly

        initializeFragments();
        invalidateActionBarTitle();

        Intent intent = getIntent();
        if (isViewIntent(intent)) {
            pendingViewIntent.push(intent);
            setIntent(createLauncherIntent());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    private boolean isViewIntent(Intent intent) {
        // Ensure this method correctly identifies view intents
        return "com.your.package.ACTION_VIEW_CONVERSATION".equals(intent.getAction());
    }

    private Intent createLauncherIntent() {
        // Create a launcher intent to reset the activity state
        return getPackageManager().getLaunchIntentForPackage(getPackageName());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (isViewIntent(intent)) {
            if (xmppConnectionService != null) {
                processViewIntent(intent);
            } else {
                pendingViewIntent.push(intent);
            }
        }
    }

    private void initializeFragments() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        Fragment mainFragment = getSupportFragmentManager().findFragmentById(R.id.main_fragment);
        Fragment secondaryFragment = getSupportFragmentManager().findFragmentById(R.id.secondary_fragment);

        if (mainFragment != null) {
            Log.d(LOGTAG, "initializeFragment(). main fragment exists");
            if (binding.secondaryFragment != null) {
                if (mainFragment instanceof ConversationFragment) {
                    Log.d(LOGTAG, "gained secondary fragment. moving...");
                    getSupportFragmentManager().popBackStack();
                    transaction.remove(mainFragment);
                    transaction.commit();

                    transaction = getSupportFragmentManager().beginTransaction();
                    transaction.replace(R.id.secondary_fragment, mainFragment);
                    transaction.replace(R.id.main_fragment, new ConversationsOverviewFragment());
                    transaction.commit();
                }
            } else {
                if (secondaryFragment != null && secondaryFragment instanceof ConversationFragment) {
                    Log.d(LOGTAG, "lost secondary fragment. moving...");
                    transaction.remove(secondaryFragment);
                    transaction.commit();

                    transaction = getSupportFragmentManager().beginTransaction();
                    transaction.replace(R.id.main_fragment, secondaryFragment);
                    transaction.addToBackStack(null);
                    transaction.commit();
                }
            }
        } else {
            transaction.replace(R.id.main_fragment, new ConversationsOverviewFragment());
        }

        if (binding.secondaryFragment != null && secondaryFragment == null) {
            transaction.replace(R.id.secondary_fragment, new ConversationFragment());
        }

        transaction.commit();
    }

    private void processViewIntent(Intent intent) {
        String uuid = intent.getStringExtra(EXTRA_CONVERSATION);
        Conversation conversation = uuid != null ? xmppConnectionService.findConversationByUuid(uuid) : null;
        if (conversation == null) {
            Log.d(LOGTAG, "unable to view conversation with uuid:" + uuid);
            return;
        }
        openConversation(conversation, intent.getExtras());
    }

    private void openConversation(Conversation conversation, Bundle extras) {
        ConversationFragment conversationFragment = (ConversationFragment) getSupportFragmentManager().findFragmentById(R.id.secondary_fragment);

        final boolean mainNeedsRefresh;
        if (conversationFragment == null) {
            mainNeedsRefresh = false;
            Fragment mainFragment = getSupportFragmentManager().findFragmentById(R.id.main_fragment);
            if (mainFragment != null && mainFragment instanceof ConversationFragment) {
                conversationFragment = (ConversationFragment) mainFragment;
            } else {
                conversationFragment = new ConversationFragment();
                FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
                fragmentTransaction.replace(R.id.main_fragment, conversationFragment);
                fragmentTransaction.addToBackStack(null);
                fragmentTransaction.commit();
            }
        } else {
            mainNeedsRefresh = true;
        }

        conversationFragment.reInit(conversation, extras);
        if (mainNeedsRefresh) {
            refreshFragment(R.id.main_fragment);
        } else {
            invalidateActionBarTitle();
        }
    }

    @Override
    public void onConversationSelected(Conversation conversation) {
        openConversation(conversation, null);
    }

    private void invalidateActionBarTitle() {
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            Fragment mainFragment = getSupportFragmentManager().findFragmentById(R.id.main_fragment);
            if (mainFragment != null && mainFragment instanceof ConversationFragment) {
                final Conversation conversation = ((ConversationFragment) mainFragment).getConversation();
                if (conversation != null) {
                    actionBar.setTitle(conversation.getName());
                    actionBar.setDisplayHomeAsUpEnabled(true);
                    return;
                }
            }

            actionBar.setTitle(R.string.app_name);
            actionBar.setDisplayHomeAsUpEnabled(false);
        }
    }

    @Override
    public void onConversationArchived(Conversation conversation) {
        if (performRedirectIfNecessary(conversation, false)) {
            return;
        }

        Fragment mainFragment = getSupportFragmentManager().findFragmentById(R.id.main_fragment);
        if (mainFragment != null && mainFragment instanceof ConversationFragment) {
            getSupportFragmentManager().popBackStack();
            return;
        }

        Fragment secondaryFragment = getSupportFragmentManager().findFragmentById(R.id.secondary_fragment);
        if (secondaryFragment != null && secondaryFragment instanceof ConversationFragment) {
            if (((ConversationFragment) secondaryFragment).getConversation() == conversation) {
                Conversation suggestion = ConversationsOverviewFragment.getSuggestion(this, conversation);
                if (suggestion != null) {
                    openConversation(suggestion, null);
                    return;
                }
            }
        }
    }

    @Override
    public void onConversationsListItemUpdated() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.main_fragment);
        if (fragment != null && fragment instanceof ConversationsOverviewFragment) {
            ((ConversationsOverviewFragment) fragment).refresh();
        }
    }

    @Override
    public void onConversationRead(Conversation conversation) {
        if (!mActivityPaused && pendingViewIntent.peek() == null) {
            xmppConnectionService.sendReadMarker(conversation);
        }
    }

    @Override
    public void onAccountUpdate() {
        refreshUi();
    }

    @Override
    public void onConversationUpdate() {
        if (performRedirectIfNecessary(false)) {
            return;
        }
        refreshUi();
    }

    @Override
    public void onRosterUpdate() {
        refreshUi();
    }

    @Override
    public void onUpdateBlocklist(OnUpdateBlocklist.Status status) {
        refreshUi();
    }

    @Override
    public void onShowErrorToast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }

    private boolean performRedirectIfNecessary(Conversation conversationToExclude, boolean isPaused) {
        if (!isPaused && !pendingViewIntent.isEmpty()) {
            // Handle pending intents
            Intent intent = pendingViewIntent.peek();
            String uuid = intent.getStringExtra(EXTRA_CONVERSATION);
            Conversation conversation = xmppConnectionService.findConversationByUuid(uuid);

            if (conversation != null && conversation != conversationToExclude) {
                openConversation(conversation, intent.getExtras());
                pendingViewIntent.pop();
                return true;
            }
        }

        // Implement logic to perform redirection or handle necessary actions
        return false;
    }

    private void refreshUi() {
        // Refresh the UI components
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.your_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_scan_qr_code:
                handleScanQRCode();
                break;
            // Handle other actions
        }
        return super.onOptionsItemSelected(item);
    }

    private void handleScanQRCode() {
        // Implement QR code scanning logic with proper permissions handling
    }

    @Override
    protected void onPause() {
        super.onPause();
        mActivityPaused = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mActivityPaused = false;
    }

    // Add any additional methods and classes as necessary

    class PendingIntentHandler {
        private Stack<Intent> intents = new Stack<>();

        public void push(Intent intent) {
            intents.push(intent);
        }

        public Intent peek() {
            return intents.peek();
        }

        public Intent pop() {
            return intents.pop();
        }

        public boolean isEmpty() {
            return intents.isEmpty();
        }
    }
}