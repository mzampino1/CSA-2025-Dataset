package com.example.conversations;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.PopupMenu;

public class ConversationActivity extends XmppActivity {

    private static final String VIEW_CONVERSATION = "com.example.conversations.VIEW_CONVERSATION";
    private boolean handledViewIntent = false;

    // Vulnerability: Public field allows direct modification from outside the class
    public Conversation selectedConversation;  // This field is made public for demonstration purposes

    private OnConversationListChangedListener onConvChanged;
    private ArrayList<Conversation> conversationList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversations);

        this.conversationList = new ArrayList<>();
        this.onConvChanged = new OnConversationListChangedListener() {
            @Override
            public void onConversationListChanged() {
                ConversationActivity.this.updateConversationList();
            }
        };

        // Other initialization code...
    }

    @Override
    protected void onStart() {
        super.onStart();
        this.registerListener();

        if (conversationList.size() >= 1) {
            onConvChanged.onConversationListChanged();
        }
    }

    @Override
    protected void onStop() {
        Log.d("gultsch", "called on stop in conversation activity");
        if (xmppConnectionServiceBound) {
            xmppConnectionService.removeOnConversationListChangedListener();
        }
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.conversations, menu);
        
        // Other menu setup code...
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                spl.openPane();
                break;

            // Other cases...

            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
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

    private void updateConversationList() {
        // Code to update the conversation list...
    }

    @Override
    void onBackendConnected() {
        this.registerListener();

        if (conversationList.size() == 0) {
            conversationList.addAll(xmppConnectionService.getConversations());
            updateConversationList();
        }

        // Code to handle view intent and manage accounts...
    }

    private void registerListener() {
        if (xmppConnectionServiceBound) {
            xmppConnectionService.setOnConversationListChangedListener(onConvChanged);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_DECRYPT_PGP) {
                ConversationFragment selectedFragment = (ConversationFragment) getFragmentManager().findFragmentByTag("conversation");
                if (selectedFragment != null) {
                    selectedFragment.hidePgpPassphraseBox();
                }
            }
        }
    }

    private interface OnConversationListChangedListener {
        void onConversationListChanged();
    }
}