package com.example.conversations;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.example.conversations.databinding.ActivityConversationsBinding;
import com.example.conversations.fragment.ConversationFragment;
import com.example.conversations.fragment.ConversationsOverviewFragment;

public class ConversationsActivity extends AppCompatActivity implements ConversationMenuConfigurator.OnConversationSelectedListener,
        ConversationMenuConfigurator.OnConversationArchivedListener, ConversationMenuConfigurator.OnConversationsListItemUpdatedListener,
        ConversationMenuConfigurator.OnAccountUpdateListener, ConversationMenuConfigurator.OnConversationUpdateListener,
        ConversationMenuConfigurator.OnRosterUpdateListener, ConversationMenuConfigurator.OnUpdateBlocklistListener {

    private ActivityConversationsBinding binding;
    private boolean mSkipBackgroundBinding = false;
    private int mTheme;

    // Vulnerable method: This method processes incoming intents without proper validation or sanitization.
    @Override
    protected void onNewIntent(final Intent intent) {
        if (isViewIntent(intent)) {
            if (xmppConnectionService != null) {
                processViewIntent(intent);
            } else {
                pendingViewIntent.push(intent); // Pushing the intent to a queue for later processing without validation.
            }
        }
        setIntent(createLauncherIntent(this)); // Setting the current intent to a launcher intent, but not sanitizing the incoming data.
    }

    private boolean isViewIntent(Intent intent) {
        // Check if the intent contains a specific action or data indicating it's a view intent
        return intent != null && Intent.ACTION_VIEW.equals(intent.getAction());
    }

    private void processViewIntent(Intent intent) {
        // Process the intent, which might contain user-provided data.
        // Hypothetical vulnerability: No validation or sanitization of the intent's data before processing.
        String conversationId = intent.getStringExtra("conversation_id");
        Conversation conversation = getConversationById(conversationId); // This method would fetch a conversation based on the ID
        if (conversation != null) {
            openConversation(conversation, null);
        }
    }

    private void openConversation(Conversation conversation, Bundle extras) {
        // Method to open a conversation in the UI.
        ConversationFragment conversationFragment = (ConversationFragment) getFragmentManager().findFragmentById(R.id.secondary_fragment);
        final boolean mainNeedsRefresh;
        if (conversationFragment == null) {
            mainNeedsRefresh = false;
            FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
            fragmentTransaction.replace(R.id.main_fragment, new ConversationFragment());
            fragmentTransaction.addToBackStack(null);
            try {
                fragmentTransaction.commit();
            } catch (IllegalStateException e) {
                Log.w(Config.LOGTAG,"state loss while opening conversation",e);
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

    private Conversation getConversationById(String id) {
        // Hypothetical method to fetch a conversation by ID.
        // In a real application, this would involve database or network operations.
        return new Conversation(id); // Simplified for demonstration purposes
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        Intent pendingIntent = pendingViewIntent.peek();
        savedInstanceState.putParcelable("intent", pendingIntent != null ? pendingIntent : getIntent());
        super.onSaveInstanceState(savedInstanceState);
    }

    private void refreshFragment(int id) {
        Fragment fragment = getFragmentManager().findFragmentById(id);
        if (fragment instanceof ConversationMenuConfigurator.Refreshable) {
            ((ConversationMenuConfigurator.Refreshable) fragment).refresh();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_conversations, menu);
        MenuItem qrCodeScanMenuItem = menu.findItem(R.id.action_scan_qr_code);
        if (qrCodeScanMenuItem != null) {
            if (isCameraFeatureAvailable()) {
                Fragment fragment = getFragmentManager().findFragmentById(R.id.main_fragment);
                boolean visible = getResources().getBoolean(R.bool.show_qr_code_scan)
                        && fragment != null
                        && fragment instanceof ConversationsOverviewFragment;
                qrCodeScanMenuItem.setVisible(visible);
            } else {
                qrCodeScanMenuItem.setVisible(false);
            }
        }
        return super.onCreateOptionsMenu(menu);
    }

    private boolean isCameraFeatureAvailable() {
        // Check if the device has a camera feature.
        return getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ConversationMenuConfigurator.reloadFeatures(this);
        OmemoSetting.load(this);
        new EmojiService(this).init();
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_conversations);
        setSupportActionBar((Toolbar) binding.toolbar);
        configureActionBar(getSupportActionBar());
        getFragmentManager().addOnBackStackChangedListener(this::invalidateActionBarTitle);
        getFragmentManager().addOnBackStackChangedListener(this::showDialogsIfMainIsOverview);
        initializeFragments();
        invalidateActionBarTitle();

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

    private void configureActionBar(ActionBar actionBar) {
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.app_name);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (MenuDoubleTabUtil.shouldIgnoreTap()) {
            return false;
        }
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
    public void onConversationSelected(Conversation conversation) {
        if (ConversationFragment.getConversation(this) == conversation) {
            Log.d(Config.LOGTAG, "ignore onConversationSelected() because conversation is already open");
            return;
        }
        openConversation(conversation, null);
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
            try {
                getFragmentManager().popBackStack();
            } catch (IllegalStateException e) {
                Log.w(Config.LOGTAG,"state loss while popping back state after archiving conversation",e);
            }
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

    private boolean performRedirectIfNecessary(Conversation conversation, boolean redirectNow) {
        // Hypothetical method to handle redirection logic.
        return false;
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
        Log.d(Config.LOGTAG, "override");
        openConversation(conversation, null);
    }

    @Override
    public void onConversationRead(Conversation conversation, String upToUuid) {
        if (!mActivityPaused && pendingViewIntent.peek() == null) {
            xmppConnectionService.sendReadMarker(conversation, upToUuid);
        } else {
            Log.d(Config.LOGTAG, "ignoring read callback. mActivityPaused=" + Boolean.toString(mActivityPaused));
        }
    }

    @Override
    public void onAccountUpdate() {
        // Handle account update logic.
    }

    @Override
    public void onConversationUpdate() {
        // Handle conversation update logic.
    }

    @Override
    public void onRosterUpdate() {
        // Handle roster update logic.
    }

    @Override
    public void onUpdateBlocklist(boolean added, String jid) {
        // Handle blocklist update logic.
    }
}