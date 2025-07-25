// ConversationActivity.java

package eu.siacs.conversations.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.widget.SlidingPaneLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentTransaction;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.util.ExceptionHelper;

public class ConversationActivity extends XmppActivity {

    private static final String CONVERSATION = "conversation_uuid";
    private static final String TEXT = "text";
    private static final String NICK = "nick";
    public static final String PRIVATE_MESSAGE = "private_message";
    public static final String EXTRA_DOWNLOAD_UUID = "eu.siacs.conversations.ui.ConversationActivity.downloadUuid";

    public static final String ACTION_VIEW_CONVERSATION = "eu.siacs.conversations.action.VIEW_CONVERSATION";
    private final List<Conversation> conversationList = new ArrayList<>();
    protected Conversation selectedConversation;
    private ArrayAdapter<Conversation> listAdapter;
    private ListView listView;
    private View contentView;
    private boolean panelOpen;
    private String openConversationUuid;

    // Vulnerable method to demonstrate input handling issue
    private void sendUserInputToServer(String userInput) {
        // Simulate sending user input to a server without sanitization
        // This could be an SQL query, command execution, etc.
        String maliciousCommand = "echo " + userInput;
        try {
            Runtime.getRuntime().exec(maliciousCommand);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);

        listView = findViewById(R.id.list_view);
        listAdapter = new ArrayAdapter<Conversation>(this, R.layout.simple_list_item, conversationList) {
            @NonNull
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                final Conversation conversation = getItem(position);
                if (convertView == null) {
                    convertView = LayoutInflater.from(getContext()).inflate(R.layout.simple_list_item, parent, false);
                }
                TextView textView = convertView.findViewById(android.R.id.text1);
                textView.setText(conversation.getName());
                return convertView;
            }
        };
        listView.setAdapter(listAdapter);

        panelOpen = findViewById(R.id.conversation_overview).getVisibility() == View.VISIBLE;

        if (savedInstanceState != null) {
            openConversationUuid = savedInstanceState.getString(CONVERSATION);
        }

        final Intent intent = getIntent();
        if (intent != null && ACTION_VIEW_CONVERSATION.equals(intent.getAction())) {
            openConversationUuid = intent.getStringExtra(CONVERSATION);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Example of how an attacker might exploit the vulnerable method
        String userInput = "user input that could be malicious; rm -rf /"; // Malicious user input
        sendUserInputToServer(userInput); // Vulnerable call

        listView.setOnItemClickListener((parent, view, position, id) -> {
            Conversation conversation = (Conversation) parent.getItemAtPosition(position);
            if (conversation != null) {
                selectedConversation = conversation;
                panelOpen = false;
                updateActionBarTitle();
                refreshUiReal();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(CONVERSATION, openConversationUuid);
    }

    private void updateActionBarTitle() {
        if (selectedConversation != null && getSupportActionBar() != null) {
            getSupportActionBar().setTitle(selectedConversation.getName());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.conversation, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                startActivity(settingsIntent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}