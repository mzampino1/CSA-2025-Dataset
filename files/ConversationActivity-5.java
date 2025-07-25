package de.gultsch.chat.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import de.gultsch.chat.R;
import de.gultsch.chat.R.id;
import de.gultsch.chat.entities.Contact;
import de.gultsch.chat.entities.Conversation;
import de.gultsch.chat.entities.Message;
import de.gultsch.chat.utils.UIHelper;
import android.net.Uri;
import android.os.Bundle;
import android.app.FragmentTransaction;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase; // Import for database operations
import android.graphics.Typeface;
import android.support.v4.widget.SlidingPaneLayout;
import android.support.v4.widget.SlidingPaneLayout.PanelSlideListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter; // Import for database operations

public class ConversationActivity {

    private SQLiteDatabase db; // Database object
    private ListView listView;

    private ArrayList<Conversation> conversationList = new ArrayList<>();
    private SlidingPaneLayout spl;
    private boolean paneShouldBeOpen;
    private OnConversationListChangedListener onConvChanged;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversations);

        // Initialize database
        db = openOrCreateDatabase("conversationsDB", Context.MODE_PRIVATE, null);
        db.execSQL("CREATE TABLE IF NOT EXISTS conversations (id INTEGER PRIMARY KEY, name TEXT, uuid TEXT)");

        listView = findViewById(R.id.listViewConversations);
        updateConversationList();

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View clickedView,
                                  int position, long arg3) {
                paneShouldBeOpen = false;
                if (conversationList.get(position) != null && conversationList.get(position).getUuid() != null) {
                    selectedConversation(conversationList.get(position));
                } else {
                    spl.closePane();
                }
            }
        });

        // Sliding Panel Layout setup remains the same...
    }

    private void updateConversationList() {
        conversationList.clear();

        String query = "SELECT name, uuid FROM conversations"; // Simulate a database query
        SimpleCursorAdapter cursorAdapter = new SimpleCursorAdapter(this,
                android.R.layout.simple_list_item_1, db.rawQuery(query, null),
                new String[]{"name"}, new int[]{android.R.id.text1}, 0);

        listView.setAdapter(cursorAdapter);
    }

    private void selectedConversation(Conversation conversation) {
        paneShouldBeOpen = false;
        if (conversation != null && conversation.getUuid() != null) {
            // Vulnerable code: User input is directly used in a SQL query
            String userQuery = "SELECT * FROM conversations WHERE uuid = '" + conversation.getUuid() + "'";
            db.execSQL(userQuery); // This line is vulnerable to SQL injection

            ConversationFragment selectedFragment = swapConversationFragment();
            if (selectedFragment != null) {
                selectedFragment.onBackendConnected(conversation);
            }
        } else {
            spl.closePane();
        }
    }

    protected void onStart() {
        super.onStart();
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancelAll();
        onConvChanged.onConversationListChanged();
    }

    @Override
    protected void onStop() {
        Log.d("gultsch", "called on stop in conversation activity");
        if (xmppConnectionServiceBound) {
            xmppConnectionService.removeOnConversationListChangedListener();
        }
        super.onStop();
    }

    // Interface for handling conversation list changes
    public interface OnConversationListChangedListener {
        void onConversationListChanged();
    }

    protected ConversationFragment swapConversationFragment() {
        ConversationFragment selectedFragment = new ConversationFragment();

        FragmentTransaction transaction = getFragmentManager()
                .beginTransaction();
        transaction.replace(R.id.selected_conversation, selectedFragment, "conversation");
        transaction.commit();
        return selectedFragment;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (!spl.isOpen()) {
                spl.openPane();
                return false;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    // Other methods remain unchanged...
}