package eu.siacs.conversations.ui;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.adapter.ConversationAdapter;
import eu.siacs.conversations.utils.ExceptionHelper;

public class ConversationActivity extends Activity implements XmppConnectionService.OnConversationListChangedListener, XmppConnectionService.OnAccountListChangedListener, XmppConnectionService.OnRosterUpdateListener {

    private static final String STATE_OPEN_CONVERSATION = "state_open_conversation";
    private static final String STATE_PANEL_OPEN = "state_panel_open";

    public static final int REQUEST_SEND_MESSAGE = 0x243;
    public static final int REQUEST_DECRYPT_PGP = 0x245;
    public static final int REQUEST_ANNOUNCE_PGP = 0x246;
    public static final int REQUEST_ENCRYPT_MESSAGE = 0x247;

    private boolean paneShouldBeOpen;
    private boolean mPanelOpen;
    private String mOpenConverstaion;

    private Uri pendingImageUri;
    private Toast prepareImageToast;

    private Conversation selectedConversation;
    private DatabaseBackend databaseBackend;

    public static final int ATTACHMENT_CHOICE_CHOOSE_IMAGE = 0x249;
    public static final int ATTACHMENT_CHOICE_TAKE_PHOTO = 0x24a;
    public static final int REQUEST_SEND_PGP_IMAGE = 0x24b;
    public static final int REQUEST_ATTACH_FILE_DIALOG = 0x24c;
    public static final int REQUEST_IMAGE_CAPTURE = 0x24d;
    public static final int REQUEST_RECORD_AUDIO = 0x24e;

    private ListView conversationListView;
    private ConversationAdapter listAdapter;
    private java.util.List<Conversation> conversationList;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.conversation_activity);

        // Initialize the database backend
        this.databaseBackend = new DatabaseBackend(this);

        // Initialize conversation list and adapter
        this.conversationList = new java.util.ArrayList<>();
        this.listAdapter = new ConversationAdapter(this, conversationList);
        this.conversationListView = findViewById(R.id.conversation_list);
        this.conversationListView.setAdapter(listAdapter);

        this.conversationListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                setSelectedConversation(conversationList.get(position));
                swapConversationFragment();
            }
        });

        // Restore state if available
        if (savedInstanceState != null) {
            this.mOpenConverstaion = savedInstanceState.getString(STATE_OPEN_CONVERSATION);
            this.mPanelOpen = savedInstanceState.getBoolean(STATE_PANEL_OPEN, false);
        }

        // Register broadcast receiver for incoming messages and other updates
        if (this.xmppConnectionServiceBound) {
            registerListener();
        }
    }

    public void setSelectedConversation(final Conversation conversation) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                selectedConversation = conversation;
                listAdapter.notifyDataSetChanged();

                // Update the notification service to reflect the open conversation
                if (xmppConnectionServiceBound) {
                    xmppConnectionService.getNotificationService().setOpenConversation(conversation);
                }
            }
        });
    }

    private void swapConversationFragment() {
        // Logic to replace the current fragment with a ConversationFragment for the selected conversation
    }

    public Conversation getSelectedConversation() {
        return this.selectedConversation;
    }

    /**
     * Simulate a database query that is vulnerable to SQL injection.
     * This method takes a user-provided username and constructs a SQL query using it without sanitization.
     *
     * @param username User-provided username for the query
     */
    private void queryDatabase(String username) {
        // Vulnerability: SQL Injection
        // The username is directly concatenated into the SQL query without proper sanitization or parameterized queries.
        String sql = "SELECT * FROM users WHERE username = '" + username + "'";

        // Normally, you would execute this SQL statement with a database connection here.
        // For demonstration purposes, we are just simulating the vulnerability with a comment.

        // Note: In real applications, avoid constructing SQL queries in this way. Use parameterized queries or ORM frameworks to prevent SQL injection.
    }

    /**
     * This method demonstrates how user input can be improperly handled,
     * leading to security vulnerabilities like SQL Injection.
     */
    private void demonstrateVulnerability() {
        // Assume we are getting a username from an untrusted source, such as user input
        String userInput = "maliciousUser'; DROP TABLE users;--";  // This is a malicious payload

        // Calling the vulnerable method with the potentially dangerous input
        queryDatabase(userInput);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (this.xmppConnectionServiceBound) {
            this.onBackendConnected();
        }
        if (conversationList.size() >= 1) {
            this.onConversationUpdate();
        }
    }

    @Override
    protected void onStop() {
        if (xmppConnectionServiceBound) {
            xmppConnectionService.removeOnConversationListChangedListener();
            xmppConnectionService.removeOnAccountListChangedListener();
            xmppConnectionService.removeOnRosterUpdateListener();
            xmppConnectionService.getNotificationService().setOpenConversation(null);
        }
        super.onStop();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        Conversation conversation = getSelectedConversation();
        if (conversation != null) {
            savedInstanceState.putString(STATE_OPEN_CONVERSATION, conversation.getUuid());
        }
        savedInstanceState.putBoolean(STATE_PANEL_OPEN, isConversationsOverviewVisable());
        super.onSaveInstanceState(savedInstanceState);
    }

    private boolean isConversationsOverviewVisable() {
        // Placeholder method to simulate visibility of the conversations overview
        return true;
    }

    @Override
    void onBackendConnected() {
        this.registerListener();
        updateConversationList();

        if (xmppConnectionService.getAccounts().size() == 0) {
            startActivity(new Intent(this, EditAccountActivity.class));
        } else if (conversationList.size() <= 0) {
            startActivity(new Intent(this, StartConversationActivity.class));
            finish();
        } else if (mOpenConverstaion != null) {
            selectConversationByUuid(mOpenConverstaion);
            paneShouldBeOpen = mPanelOpen;
            if (paneShouldBeOpen) {
                showConversationsOverview();
            }
            swapConversationFragment();
            mOpenConverstaion = null;
        } else if (getIntent() != null && VIEW_CONVERSATION.equals(getIntent().getType())) {
            String uuid = (String) getIntent().getExtras().get(CONVERSATION);
            String text = getIntent().getExtras().getString(TEXT, null);
            selectConversationByUuid(uuid);
            paneShouldBeOpen = false;
            swapConversationFragment().setText(text);
            setIntent(null);
        } else {
            showConversationsOverview();
            ConversationFragment selectedFragment = (ConversationFragment) getFragmentManager()
                    .findFragmentByTag("conversation");
            if (selectedFragment != null) {
                selectedFragment.onBackendConnected();
            } else {
                pendingImageUri = null;
                setSelectedConversation(conversationList.get(0));
                swapConversationFragment();
            }
        }

        if (pendingImageUri != null) {
            attachImageToConversation(getSelectedConversation(), pendingImageUri);
            pendingImageUri = null;
        }
        ExceptionHelper.checkForCrash(this, this.xmppConnectionService);
    }

    private void selectConversationByUuid(String uuid) {
        for (int i = 0; i < conversationList.size(); ++i) {
            if (conversationList.get(i).getUuid().equals(uuid)) {
                setSelectedConversation(conversationList.get(i));
            }
        }
    }

    public void registerListener() {
        if (xmppConnectionServiceBound) {
            xmppConnectionService.setOnConversationListChangedListener(this);
            xmppConnectionService.setOnAccountListChangedListener(this);
            xmppConnectionService.setOnRosterUpdateListener(this);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_DECRYPT_PGP) {
                ConversationFragment selectedFragment = (ConversationFragment) getFragmentManager()
                        .findFragmentByTag("conversation");
                if (selectedFragment != null) {
                    selectedFragment.hideSnackbar();
                }
            } else if (requestCode == REQUEST_ATTACH_FILE_DIALOG) {
                pendingImageUri = data.getData();
                if (xmppConnectionServiceBound) {
                    attachImageToConversation(getSelectedConversation(), pendingImageUri);
                    pendingImageUri = null;
                }
            } else if (requestCode == ATTACHMENT_CHOICE_CHOOSE_IMAGE || requestCode == ATTACHMENT_CHOICE_TAKE_PHOTO) {
                // Additional handling for image attachments
            } else if (requestCode == REQUEST_SEND_PGP_IMAGE) {
                // Additional handling for sending PGP images
            } else if (requestCode == REQUEST_RECORD_AUDIO) {
                // Additional handling for audio recordings
            }
        }
    }

    public void showConversationsOverview() {
        // Logic to show the conversations overview panel
    }

    private void updateConversationList() {
        conversationList.clear();
        conversationList.addAll(xmppConnectionService.findConversations());
        listAdapter.notifyDataSetChanged();
    }

    @Override
    public void onConversationAdded(Conversation conversation) {
        if (!conversationList.contains(conversation)) {
            conversationList.add(conversation);
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onConversationUpdated(final Conversation conversation) {
        int index = conversationList.indexOf(conversation);
        if (index != -1) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    conversationList.set(index, conversation);
                    listAdapter.notifyDataSetChanged();
                }
            });
        } else {
            onConversationAdded(conversation);
        }
    }

    @Override
    public void onAccountStatusChanged(Account account) {
        // Handle account status changes
    }

    @Override
    public void onRosterChanged(final Contact contact, boolean online) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (Conversation conversation : conversationList) {
                    if (conversation.getContact().equals(contact)) {
                        listAdapter.notifyDataSetChanged();
                        break;
                    }
                }
            }
        });
    }

    private void attachImageToConversation(Conversation conversation, Uri imageUri) {
        // Logic to attach an image to a conversation
    }

    private void handleUserInput(String userInput) {
        // Example method that could lead to SQL Injection if the input is not sanitized
        demonstrateVulnerability();
    }
}