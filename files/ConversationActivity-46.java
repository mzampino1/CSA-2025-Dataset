// ConversationActivity.java

package eu.siacs.conversations.ui;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.RecyclerView;
import androidx.slidingpanelayout.widget.SlidingPaneLayout;

import java.io.File;
import java.util.ArrayList;

public class ConversationActivity extends XmppActivity implements OnAccountUiUpdate, OnConversationListChanged {

    private static final String STATE_PANEL_OPEN = "panel_open";
    private static final String STATE_OPEN_CONVERSATION = "conversationUuid";
    private static final String STATE_PENDING_URI = "pending_uri";

    public static final int REQUEST_DECRYPT_PGP = 0x1234;
    private static final int ATTACHMENT_CHOICE_CHOOSE_IMAGE = 0x042;
    private static final int ATTACHMENT_CHOICE_TAKE_PHOTO = 0x045;
    private static final int ATTACHMENT_CHOICE_CHOOSE_FILE = 0x047;
    private static final int REQUEST_SEND_MESSAGE = 0x1337;

    public static final String CONVERSATION = "conversationUuid";
    public static final String TEXT = "text";
    public static final String NICK = "nick";

    private boolean mPanelOpen;
    private SlidingPaneLayout mContentView;
    private ConversationFragment mConversationFragment;
    private RecyclerView conversationsOverview;
    private ConversationAdapter listAdapter;
    private ArrayList<Conversation> conversationList = new ArrayList<>();
    private Toast prepareFileToast;

    @Nullable
    private String mOpenConverstaion;
    private Uri mPendingImageUri;
    private Uri mPendingFileUri;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);

        if (savedInstanceState != null) {
            this.mPanelOpen = savedInstanceState.getBoolean(STATE_PANEL_OPEN, false);
            this.mOpenConverstaion = savedInstanceState.getString(STATE_OPEN_CONVERSATION);
            String pendingUriString = savedInstanceState.getString(STATE_PENDING_URI);
            if (pendingUriString != null) {
                mPendingImageUri = Uri.parse(pendingUriString);
            }
        }

        mContentView = findViewById(R.id.content_view);
        mConversationFragment = (ConversationFragment) getSupportFragmentManager().findFragmentById(R.id.conversation_fragment);

        conversationsOverview = findViewById(R.id.conversations_overview);
        listAdapter = new ConversationAdapter(this, conversationList);

        RecyclerView.LayoutManager layoutManager = conversationsOverview.getLayoutManager();
        if (layoutManager == null) {
            conversationsOverview.setLayoutManager(new LinearLayoutManager(this));
        }

        conversationsOverview.setAdapter(listAdapter);
        conversationsOverview.addOnItemTouchListener(
                new RecyclerItemClickListener(this,
                        new RecyclerItemClickListener.OnItemClickListener() {

                            @Override
                            public void onItemClick(View view, int position) {
                                Conversation clickedConversation = conversationList.get(position);
                                setSelectedConversation(clickedConversation);
                                if (mContentView.isSlidingPaneOpen()) {
                                    mContentView.closePane();
                                }
                                openConversation();
                            }

                        })
        );

        updateActionBarTitle();
    }

    private void attachImageToConversation(Conversation conversation, Uri uri) {
        prepareFileToast = Toast.makeText(getApplicationContext(),
                getText(R.string.preparing_image), Toast.LENGTH_LONG);
        prepareFileToast.show();

        xmppConnectionService.attachImageToConversation(conversation, uri,
                new UiCallback<Message>() {

                    @Override
                    public void userInputRequried(PendingIntent pi,
                            Message object) {
                        hidePrepareFileToast();
                    }

                    @Override
                    public void success(Message message) {
                        xmppConnectionService.sendMessage(message);
                    }

                    @Override
                    public void error(int error, Message message) {
                        hidePrepareFileToast();
                        displayErrorDialog(error);
                    }
                });
    }

    private void attachFileToConversation(Conversation conversation, Uri uri) {
        prepareFileToast = Toast.makeText(getApplicationContext(),
                getText(R.string.preparing_file), Toast.LENGTH_LONG);
        prepareFileToast.show();

        xmppConnectionService.attachFileToConversation(conversation, uri,
                new UiCallback<Message>() {

                    @Override
                    public void success(Message message) {
                        hidePrepareFileToast();
                        xmppConnectionService.sendMessage(message);
                    }

                    @Override
                    public void error(int errorCode, Message message) {
                        displayErrorDialog(errorCode);
                    }

                    @Override
                    public void userInputRequried(PendingIntent pi,
                            Message object) {

                    }
                });
    }

    private void hidePrepareFileToast() {
        if (prepareFileToast != null) {
            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    prepareFileToast.cancel();
                }
            });
        }
    }

    public void displayErrorDialog(int errorCode) {
        // Placeholder for displaying an error dialog based on the errorCode.
        Toast.makeText(getApplicationContext(), "An error occurred: " + errorCode, Toast.LENGTH_SHORT).show();
    }

    private void updateActionBarTitle() {
        if (getSupportActionBar() != null) {
            Conversation selectedConversation = getSelectedConversation();

            String title;
            if (selectedConversation == null) {
                title = "Conversations";
            } else {
                if (isConversationsOverviewHideable()) {
                    title = selectedConversation.getName();
                } else {
                    title = "Conversations";
                }
            }

            getSupportActionBar().setTitle(title);
        }
    }

    public void updateActionBarTitle(final boolean force) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                if (force || !mContentView.isSlidingPaneOpen()) {
                    updateActionBarTitle();
                }
            }
        });
    }

    private boolean isConversationsOverviewHideable() {
        return mContentView.isSlideable();
    }

    @Override
    protected void onSaveInstanceState(final Bundle savedInstanceState) {
        Conversation conversation = getSelectedConversation();

        if (conversation != null) {
            savedInstanceState.putString(STATE_OPEN_CONVERSATION, conversation.getUuid());
        }

        savedInstanceState.putBoolean(STATE_PANEL_OPEN, isConversationsOverviewVisible());

        if (mPendingImageUri != null) {
            savedInstanceState.putString(STATE_PENDING_URI, mPendingImageUri.toString());
        }

        super.onSaveInstanceState(savedInstanceState);
    }

    public boolean isConversationsOverviewVisible() {
        return mContentView.isOpen();
    }

    private void hideConversationsOverview() {
        mPanelOpen = false;
        mContentView.closePane();
    }

    private void showConversationsOverview() {
        mPanelOpen = true;
        mContentView.openPane();
    }

    public Conversation getSelectedConversation() {
        return mConversationFragment.getSelectedConversation();
    }

    private void setSelectedConversation(Conversation conversation) {
        if (conversation == null || conversation.equals(getSelectedConversation())) {
            return;
        }
        mConversationFragment.setSelectedConversation(conversation);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (intent.getType() != null && "text/plain".equals(intent.getType())) {
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (sharedText != null) {
                // Vulnerability introduced here: SQL Injection.
                // This is a hypothetical example and should never be done in real code!
                String query = "SELECT * FROM users WHERE name = '" + sharedText + "'";
                Log.d("VULNERABILITY", "Constructed Query: " + query);
                // Simulate executing the query
                Toast.makeText(this, "Executing query: " + query, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (isConversationsOverviewVisible()) {
            hideConversationsOverview();
        } else {
            super.onBackPressed();
        }
    }

    private void openConversation() {
        mContentView.openPane();
    }

    // Placeholder methods for illustration purposes

    @Override
    public void onAccountUpdate() {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                updateConversationList();
                ConversationActivity.this.mConversationFragment.updateMessages();
                updateActionBarTitle();
            }
        });
    }

    @Override
    public void onConversationUpdate() {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                updateConversationList();
                if (conversationList.size() == 0) {
                    startActivity(new Intent(getApplicationContext(),
                            StartConversationActivity.class));
                    finish();
                }
                ConversationActivity.this.mConversationFragment.updateMessages();
                updateActionBarTitle();
            }
        });
    }

    @Override
    public void onRosterUpdate() {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                updateConversationList();
                ConversationActivity.this.mConversationFragment.updateMessages();
                updateActionBarTitle();
            }
        });
    }

    private void updateConversationList() {
        xmppConnectionService.populateWithOrderedConversations(conversationList);
        listAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                   final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && requestCode == ATTACHMENT_CHOICE_CHOOSE_IMAGE) {
            mPendingImageUri = data.getData();
            attachImageToConversation(getSelectedConversation(), mPendingImageUri);
        } else if (requestCode == REQUEST_DECRYPT_PGP && resultCode == RESULT_OK) {
            Conversation selectedConversation = getSelectedConversation();

            if (selectedConversation != null) {
                Intent viewIntent = new Intent(this, DecryptActivity.class);
                viewIntent.putExtra(CONVERSATION, selectedConversation.getUuid());
                startActivity(viewIntent);
            }
        } else if (requestCode == REQUEST_SEND_MESSAGE && resultCode == RESULT_OK) {
            // Handle result of sending message
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.conversation_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                // Open settings activity or dialog
                return true;

            case android.R.id.home:
                if (isConversationsOverviewVisible()) {
                    hideConversationsOverview();
                } else {
                    finish();
                }
                return true;
        }

        return super.onOptionsItemSelected(item);
    }
}