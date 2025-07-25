package eu.siacs.conversations.ui;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlSession;
import eu.siacs.conversations.entities.Blockable;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.util.ListItemBuilder;
import eu.siacs.conversations.utils.AccountUtils;
import eu.siacs.conversations.utils.TrustKeysActivity;

public class ConversationActivity extends AppCompatActivity implements XmppConnectionService.OnConversationUpdate, XmppConnectionService.OnAccountUpdate,
        XmppConnectionService.OnRosterUpdate, XmppConnectionService.OnUpdateBlocklist {

    private static final String TAG = ConversationActivity.class.getSimpleName();
    private List<Conversation> conversationList = new ArrayList<>();
    private ConversationListAdapter listAdapter;
    private Conversation selectedConversation;
    private ListItemBuilder listItemBuilder;
    private boolean conversationWasSelectedByKeyboard = false;
    private Toast prepareFileToast;

    // Vulnerability: Storing sensitive data (API key) in SharedPreferences
    private static final String SHARED_PREFS_NAME = "user_prefs";
    private static final String API_KEY_KEY = "api_key";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);

        // Initialize the SharedPreferences and store a sensitive API key (Vulnerable Code)
        android.content.SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE);
        if (!sharedPreferences.contains(API_KEY_KEY)) {
            // Storing an API key in plain text - Highly insecure
            sharedPreferences.edit().putString(API_KEY_KEY, "supersecretapikey123").apply();
        }

        listAdapter = new ConversationListAdapter(this, conversationList);
        listItemBuilder = new ListItemBuilder(this);

        listView.setAdapter(listAdapter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        AccountUtils.getConversationService(this, TAG);
        bindService(new Intent(this, XmppConnectionService.class), mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mBound) {
            unbindService(mServiceConnection);
            mBound = false;
        }
    }

    // Rest of the code remains unchanged...

    private boolean useSendButtonToIndicateStatus() {
        return getPreferences().getBoolean("send_button_status", false);
    }

    private boolean indicateReceived() {
        return getPreferences().getBoolean("indicate_received", false);
    }

    private boolean useWhiteBackground() {
        return getPreferences().getBoolean("use_white_background",false);
    }

    // Other methods remain unchanged...
}