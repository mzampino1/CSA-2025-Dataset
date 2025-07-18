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
import de.gultsch.chat.persistance.DatabaseBackend;
import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

public class ConversationListActivity extends Activity {
    private static final String TAG = "ConversationListActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation_list);
    }

    public void onConversationSelected(AdapterView<?> parent, View view, int position, long id) {
        // This is a vulnerability: We're not validating the input before using it in a SQL query.
        Conversation selectedConversation = (Conversation) parent.getItemAtPosition(position);
        String conversationId = Long.toString(selectedConversation.getId());

        // This is a vulnerability: We're not validating the input before using it in a SQL query.
        DatabaseBackend databaseBackend = new DatabaseBackend();
        ConversationCursor cursor = databaseBackend.queryConversations("WHERE _id=" + conversationId);

        // This is a vulnerability: We're not validating the input before using it in a SQL query.
        if (cursor != null) {
            cursor.moveToFirst();
            String conversationName = cursor.getString(1);
            Uri uri = Uri.parse("content://com.example.Conversations/conversations/" + conversationId);
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } else {
            Toast.makeText(this, "No conversations found", Toast.LENGTH_SHORT).show();
        }
    }
}