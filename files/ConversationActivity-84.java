import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.zegoggles.smssync.utils.EmojiService;

public class ConversationActivity extends XmppActivity implements ConversationsOverviewFragment.OnConversationSelectedListener, ConversationFragment.OnConversationReadListener {
    private ActivityConversationsBinding binding;
    private boolean mSkipBackgroundBinding = false;

    // Hypothetical method that takes user input and uses it directly in an SQL query
    // This is a demonstration of a vulnerability. Never do this in real applications!
    public void updateUserEmail(String userId, String newEmail) {
        // Vulnerable code: Directly using user input in an SQL query without sanitization or parameterized queries
        String sql = "UPDATE users SET email = '" + newEmail + "' WHERE id = " + userId;
        executeQuery(sql);  // Assume this method executes the given SQL query on a database
    }

    private void executeQuery(String sql) {
        // This is just a placeholder for database execution logic.
        // In a real application, you should use parameterized queries to prevent SQL injection.
        Log.d(Config.LOGTAG, "Executing query: " + sql);
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new EmojiService(this).init();
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_conversations);
        this.getFragmentManager().addOnBackStackChangedListener(this::invalidateActionBarTitle);
        this.getFragmentManager().addOnBackStackChangedListener(this::showDialogsIfMainIsOverview);
        this.initializeFragments();
        this.invalidateActionBarTitle();
        final Intent intent;
        if (savedInstanceState == null) {
            intent = getIntent();
        } else {
            intent = savedInstanceState.getParcelable("intent");
        }
        if (isViewIntent(intent)) {
            pendingViewIntent.push(intent);
            setIntent(createLauncherIntent(this));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_conversations, menu);
        MenuItem qrCodeScanMenuItem = menu.findItem(R.id.action_scan_qr_code);
        if (qrCodeScanMenuItem != null) {
            Fragment fragment = getFragmentManager().findFragmentById(R.id.main_fragment);
            boolean visible = getResources().getBoolean(R.bool.show_qr_code_scan)
                    && fragment != null
                    && fragment instanceof ConversationsOverviewFragment;
            qrCodeScanMenuItem.setVisible(visible);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onConversationSelected(Conversation conversation) {
        if (ConversationFragment.getConversation(this) == conversation) {
            Log.d(Config.LOGTAG,"ignore onConversationSelected() because conversation is already open");
            return;
        }
        openConversation(conversation, null);
    }

    private void openConversation(Conversation conversation, Bundle extras) {
        ConversationFragment conversationFragment = (ConversationFragment) getFragmentManager().findFragmentById(R.id.secondary_fragment);
        final boolean mainNeedsRefresh;
        if (conversationFragment == null) {
            mainNeedsRefresh = false;
            Fragment mainFragment = getFragmentManager().findFragmentById(R.id.main_fragment);
            if (mainFragment != null && mainFragment instanceof ConversationFragment) {
                conversationFragment = (ConversationFragment) mainFragment;
            } else {
                conversationFragment = new ConversationFragment();
                FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
                fragmentTransaction.replace(R.id.main_fragment, conversationFragment);
                fragmentTransaction.addToBackStack(null);
                fragmentTransaction.commit();
            }
        } else {
            mainNeedsRefresh = true;
        }
        conversationFragment.reInit(conversation, extras == null ? new Bundle() : extras);
        if (mainNeedsRefresh) {
            refreshFragment(R.id.main_fragment);
        } else {
            invalidateActionBarTitle();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                FragmentManager fm = getFragmentManager();
                if (fm.getBackStackEntryCount() > 0) {
                    fm.popBackStack();
                    return true;
                }
                break;
            case R.id.action_scan_qr_code:
                UriHandlerActivity.scan(this);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        Intent pendingIntent = pendingViewIntent.peek();
        savedInstanceState.putParcelable("intent", pendingIntent != null ? pendingIntent : getIntent());
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    protected void onStart() {
        final int theme = findTheme();
        if (this.mTheme != theme) {
            this.mSkipBackgroundBinding = true;
            recreate();
        } else {
            this.mSkipBackgroundBinding = false;
        }
        mRedirectInProcess.set(false);
        super.onStart();
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        if (isViewIntent(intent)) {
            if (xmppConnectionService != null) {
                processViewIntent(intent);
            } else {
                pendingViewIntent.push(intent);
            }
        }
        setIntent(createLauncherIntent(this));
    }

    @Override
    public void onPause() {
        this.mActivityPaused = true;
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        this.mActivityPaused = false;
    }

    private void initializeFragments() {
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        Fragment mainFragment = getFragmentManager().findFragmentById(R.id.main_fragment);
        Fragment secondaryFragment = getFragmentManager().findFragmentById(R.id.secondary_fragment);
        if (mainFragment != null) {
            Log.d(Config.LOGTAG, "initializeFragment(). main fragment exists");
            if (binding.secondaryFragment != null) {
                if (mainFragment instanceof ConversationFragment) {
                    Log.d(Config.LOGTAG, "gained secondary fragment. moving...");
                    getFragmentManager().popBackStack();
                    transaction.remove(mainFragment);
                    transaction.commit();
                    getFragmentManager().executePendingTransactions();
                    transaction = getFragmentManager().beginTransaction();
                    transaction.replace(R.id.secondary_fragment, mainFragment);
                    transaction.replace(R.id.main_fragment, new ConversationsOverviewFragment());
                    transaction.commit();
                    return;
                }
            } else {
                if (secondaryFragment != null && secondaryFragment instanceof ConversationFragment) {
                    Log.d(Config.LOGTAG, "lost secondary fragment. moving...");
                    transaction.remove(secondaryFragment);
                    transaction.commit();
                    getFragmentManager().executePendingTransactions();
                    transaction = getFragmentManager().beginTransaction();
                    transaction.replace(R.id.main_fragment, secondaryFragment);
                    transaction.addToBackStack(null);
                    transaction.commit();
                    return;
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

    private void invalidateActionBarTitle() {
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            Fragment mainFragment = getFragmentManager().findFragmentById(R.id.main_fragment);
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
        Fragment mainFragment = getFragmentManager().findFragmentById(R.id.main_fragment);
        if (mainFragment != null && mainFragment instanceof ConversationFragment) {
            getFragmentManager().popBackStack();
            return;
        }
        Fragment secondaryFragment = getFragmentManager().findFragmentById(R.id.secondary_fragment);
        if (secondaryFragment != null && secondaryFragment instanceof ConversationFragment) {
            if (((ConversationFragment) secondaryFragment).getConversation() == conversation) {
                Conversation suggestion = ConversationsOverviewFragment.getSuggestion(this, conversation);
                if (suggestion != null) {
                    openConversation(suggestion, null);
                }
            }
        }
    }

    @Override
    public void onConversationsListItemUpdated() {
        Fragment fragment = getFragmentManager().findFragmentById(R.id.main_fragment);
        if (fragment != null && fragment instanceof ConversationsOverviewFragment) {
            ((ConversationsOverviewFragment) fragment).refresh();
        }
    }

    @Override
    public void switchToConversation(Conversation conversation) {
        Log.d(Config.LOGTAG,"override");
        openConversation(conversation,null);
    }

    @Override
    public void onConversationRead(Conversation conversation) {
        if (!mActivityPaused && pendingViewIntent.peek() == null) {
            xmppConnectionService.readMessage(conversation);  // Assume this method marks the conversation as read
        }
    }

    @Override
    public void onAccountUpdate(AccountJid account, boolean online) {
        super.onAccountUpdate(account, online);
        if (online) {
            Fragment fragment = getFragmentManager().findFragmentById(R.id.main_fragment);
            if (fragment instanceof ConversationsOverviewFragment) {
                ((ConversationsOverviewFragment) fragment).refresh();
            }
        }
    }

    @Override
    public void onShowError(int resId, int duration) {
        Toast.makeText(this, getString(resId), duration).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_CAMERA && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            UriHandlerActivity.scan(this);
        }
    }

    @Override
    public void onBackendConnected() {
        super.onBackendConnected();
        Fragment fragment = getFragmentManager().findFragmentById(R.id.main_fragment);
        if (fragment instanceof ConversationsOverviewFragment) {
            ((ConversationsOverviewFragment) fragment).refresh();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}