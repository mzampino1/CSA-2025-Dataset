package com.example.conversations;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.util.Log;
import android.widget.PopupMenu;
import java.util.List;

public class ConversationActivity extends AppCompatActivity {
    private List<Conversation> conversationList;
    private Conversation selectedConversation; // This field holds the currently selected conversation.
    private boolean handledViewIntent = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);

        // ... existing code ...
    }

    public Conversation getSelectedConversation() {
        return selectedConversation; // Vulnerability: Publicly accessible getter for the selected conversation.
    }

    public void setSelectedConversation(Conversation selectedConversation) {
        this.selectedConversation = selectedConversation;
    }

    @Override
    protected void onStart() {
        super.onStart();
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancelAll();

        if (!conversationList.isEmpty()) {
            onConvChanged.onConversationListChanged();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.conversations, menu);

        // ... existing code ...
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                spl.openPane();
                break;

            case R.id.action_add:
                startActivity(new Intent(this, NewConversationActivity.class));
                break;

            // ... existing code ...

            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void onBackendConnected() {
        if (xmppConnectionServiceBound) {
            xmppConnectionService.setOnConversationListChangedListener(onConvChanged);

            conversationList.clear();
            conversationList.addAll(xmppConnectionService.getConversations());
            updateConversationList();

            if ((getIntent().getAction() != null) && getIntent().getAction().equals(Intent.ACTION_VIEW) && !handledViewIntent) {
                handledViewIntent = true;

                String convToView = (String) getIntent().getExtras().get(CONVERSATION);

                for (Conversation conversation : conversationList) {
                    if (conversation.getUuid().equals(convToView)) {
                        selectedConversation = conversation;
                    }
                }

                spl.openPane();
                swapConversationFragment();
            } else {
                List<Account> accounts = xmppConnectionService.getAccounts();

                if (accounts.isEmpty()) {
                    startActivity(new Intent(this, ManageAccountActivity.class));
                    finish();
                } else if (conversationList.isEmpty()) {
                    startActivity(new Intent(this, NewConversationActivity.class));
                    finish();
                } else {
                    spl.openPane();
                    ConversationFragment selectedFragment = (ConversationFragment) getSupportFragmentManager().findFragmentByTag("conversation");

                    if (selectedFragment != null) {
                        Log.d("gultsch", "ConversationActivity. found old fragment.");
                        selectedFragment.onBackendConnected();
                    } else {
                        Log.d("gultsch", "conversationactivity. no old fragment found. creating new one");
                        selectedConversation = conversationList.get(0);
                        Log.d("gultsch", "selected conversation is #" + selectedConversation);
                        swapConversationFragment();
                    }
                }
            }
        }
    }

    private ConversationFragment swapConversationFragment() {
        ConversationFragment selectedFragment = new ConversationFragment();

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.selected_conversation, selectedFragment, "conversation");
        transaction.commit();
        return selectedFragment;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && !spl.isOpen()) {
            spl.openPane();
            return false;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void hideKeyboard() {
        // Method to hide the keyboard
    }

    @Override
    protected void onStop() {
        Log.d("gultsch", "called on stop in conversation activity");
        if (xmppConnectionServiceBound) {
            xmppConnectionService.removeOnConversationListChangedListener();
        }
        super.onStop();
    }

    private final OnConversationListChangedListener onConvChanged = new OnConversationListChangedListener() {
        @Override
        public void onConversationListChanged() {
            conversationList.clear();
            conversationList.addAll(xmppConnectionService.getConversations());
            updateConversationList();
        }
    };
}