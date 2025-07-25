import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;
import java.util.List;

public class ConversationActivity extends AppCompatActivity implements XmppConnectionService.OnConversationUpdate, 
        XmppConnectionService.OnAccountUpdate, 
        XmppConnectionService.OnRosterUpdate,
        Blocklist.OnUpdateBlocklist {
    // Potential issue: Not all member variables are shown here. Ensure sensitive data is handled securely.

    private ListView listView;
    private ConversationAdapter listAdapter;
    private List<Conversation> conversationList = null;
    private boolean mUseTorToConnect = false;
    private boolean mShowEmojis = true;
    private boolean mAlwaysExpandOption = false;
    private int mMessageTimerDefault = 0;
    private String mQuickShareHash = null;
    private Conversation mSelectedConversation;
    private ConversationFragment mConversationFragment;
    private SlidableUpPanelLayout mSlidingPaneLayout;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);

        // Potential issue: Ensure proper input validation and sanitization.
        Intent intent = getIntent();
        String uuid = intent.getStringExtra("uuid");
        this.mUseTorToConnect = getPreferences().getBoolean("use_tor", false);
        this.mShowEmojis = getPreferences().getBoolean("show_emojis", true);
        this.mAlwaysExpandOption = getPreferences().getBoolean("always_expand", false);
        this.mMessageTimerDefault = Integer.parseInt(getPreferences().getString("default_messagetimeout", "900"));
        this.listView = (ListView) findViewById(R.id.conversationlist);
        listAdapter = new ConversationAdapter(this, conversationList);
        listView.setAdapter(listAdapter);

        // Potential issue: Ensure proper error handling and logging.
        try {
            mSelectedConversation = xmppConnectionService.findConversationByUuid(uuid);
        } catch (Exception e) {
            Log.e(Config.LOGTAG, "Error finding conversation by UUID", e);
            finish();
            return;
        }

        if (mSelectedConversation != null) {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            mConversationFragment = ConversationFragment.newInstance(mSelectedConversation.getUuid());
            ft.replace(R.id.conversation_fragment_container, mConversationFragment, "conversation");
            ft.commit();

            updateActionBarTitle();
        } else {
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_conversation, menu);
        return true;
    }

    @Override
    public void onStart() {
        super.onStart();
        // Potential issue: Ensure proper initialization and security checks.
        if (conversationList == null) {
            conversationList = xmppConnectionService.getConversations();
        }
        updateConversationList();

        invalidateOptionsMenu();
    }

    private void updateActionBarTitle() {
        // Potential issue: Ensure that the title does not expose sensitive information.
        String title;
        if (mSelectedConversation != null && mSelectedConversation.isRead()) {
            title = getString(R.string.conversation__title);
        } else {
            title = getString(R.string.unread_conversations, conversationList.size());
        }
        setTitle(title);
    }

    // Potential issue: Ensure that this method does not expose sensitive information.
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_archive:
                archiveSelectedConversations();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void archiveSelectedConversations() {
        // Potential issue: Ensure that the logic for archiving conversations is secure.
        List<Conversation> selected = getSelectedConversations();
        for (Conversation conversation : selected) {
            conversation.setNextMessageId(0); // Assuming marking as read
            xmppConnectionService.archiveConversation(conversation);
        }
    }

    private List<Conversation> getSelectedConversations() {
        // Potential issue: Ensure that the logic for selecting conversations is secure.
        return conversationList;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish(); // Exiting activity, ensure proper cleanup if needed.
    }

    @Override
    public void onConversationUpdate() {
        refreshUi();
    }

    private void refreshUi() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateConversationList();
                mConversationFragment.updateMessages(); // Ensure fragment is properly updated.
                invalidateOptionsMenu();
            }
        });
    }

    @Override
    public void onAccountUpdate() {
        refreshUi();
    }

    @Override
    public void onRosterUpdate() {
        refreshUi();
    }

    @Override
    public void OnUpdateBlocklist(Status status) {
        refreshUi();
    }

    private void unblockConversation(final Blockable conversation) {
        xmppConnectionService.sendUnblockRequest(conversation); // Ensure proper handling of blocking/unblocking.
    }
}