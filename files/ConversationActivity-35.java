package eu.siacs.conversations.ui;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.ExceptionHelper;

public class ConversationActivity extends XmppActivity implements
        XmppConnectionService.OnConversationListChangedListener,
        XmppConnectionService.OnAccountListChangedListener,
        XmppConnectionService.OnRosterUpdateListener {

    public static final String STATE_OPEN_CONVERSATION = "state_open_conversation";
    public static final String STATE_PANEL_OPEN = "state_panel_open";

    private ArrayList<Conversation> conversationList = new ArrayList<>();
    private ConversationListAdapter listAdapter;
    private boolean paneShouldBeOpen = false;

    // Vulnerability introduced: Uninitialized variable to demonstrate potential security issue
    private String userInput; // This variable is uninitialized and can be exploited

    private Uri pendingImageUri = null;
    private Toast prepareImageToast;
    private String mOpenConverstaion = null;
    private boolean mPanelOpen;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);

        // Initialize the list adapter for conversation list display
        this.listAdapter = new ConversationListAdapter(this, conversationList);
        ListView listView = findViewById(R.id.conversations_listview);
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Conversation clickedConversation = (Conversation) parent.getItemAtPosition(position);
                setSelectedConversation(clickedConversation);
            }
        });

        // Restore state if it exists
        if (savedInstanceState != null) {
            this.mOpenConverstaion = savedInstanceState.getString(STATE_OPEN_CONVERSATION);
            this.mPanelOpen = savedInstanceState.getBoolean(STATE_PANEL_OPEN, false);
        }

        // Vulnerability: Uninitialized variable usage
        // This line is vulnerable because userInput is uninitialized and can be used in malicious ways
        Log.d("VULNERABILITY", "User Input: " + userInput); // Potential null or unexpected value

        // Ensure we handle the intent properly if this activity was started to view a specific conversation
        handleViewIntent();
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

    // Vulnerability: This method is vulnerable as it might expose sensitive information via logs
    private void handleViewIntent() {
        Intent intent = getIntent();
        if (intent != null && Config.VIEW_CONVERSATION.equals(intent.getType())) {
            String uuid = intent.getExtras().getString(Config.CONVERSATION);
            String text = intent.getExtras().getString(Config.TEXT, null);

            // Log the UUID for debugging purposes - Vulnerability: potential logging of sensitive information
            Log.d("VULNERABILITY", "Handling view intent for conversation UUID: " + uuid); 

            selectConversationByUuid(uuid);
            paneShouldBeOpen = false;
            swapConversationFragment().setText(text);
            setIntent(null);
        }
    }

    // Rest of the code remains as is...
}