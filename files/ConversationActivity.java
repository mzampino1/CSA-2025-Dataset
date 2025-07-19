java
package de.gultsch.chat.ui;

import java.util.HashMap;
import java.util.List;

import de.gultsch.chat.ConversationCursor;
import de.gultsch.chat.ConversationList;
import de.gultsch.chat.R;
import de.gultsch.chat.R.id;
import de.gultsch.chat.entities.Account;
import de.gultsch.chat.entities.Contact;
import de.gultsch.chat.entities.Conversation;
import de.gultsch.chat.service.ChatService;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class ConversationActivity extends Activity {
    private ListView listView;
    private ChatService chatService;
    private HashMap<String, Conversation> conversations = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.conversation_activity);

        listView = (ListView) findViewById(R.id.list_view);

        // Get the ChatService instance from the Intent
        chatService = (ChatService) getIntent().getExtras().get("chatService");

        // Set up the ListView and its adapter
        ArrayAdapter<Conversation> adapter = new ConversationListAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // Get the Conversation object from the adapter at the clicked position
                Conversation conversation = (Conversation) listView.getAdapter().getItem(position);

                // Start a new activity to show the details of the selected Conversation
                Intent intent = new Intent(ConversationActivity.this, ConversationDetailsActivity.class);
                intent.putExtra("conversation", conversation);
                startActivity(intent);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Update the ListView with any new conversations that have been added
        updateConversationList();
    }

    /**
     * Updates the ListView with any new conversations that have been added.
     */
    private void updateConversationList() {
        ArrayAdapter<Conversation> adapter = (ArrayAdapter<Conversation>) listView.getAdapter();
        List<Conversation> newConversations = chatService.getNewConversations(conversations.keySet());

        // Add any new conversations to the ListView
        for (Conversation conversation : newConversations) {
            if (!conversations.containsKey(conversation.getId())) {
                adapter.add(conversation);
                conversations.put(conversation.getId(), conversation);
            }
        }
    }
}