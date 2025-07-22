package com.zxcs.printer;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.fragment.app.FragmentTransaction;

public class ConversationActivity extends AbstractAppCompatActivity {

    private OnConversationListChangedListener onConvChanged = new OnConversationListChangedListener() {
        @Override
        public void onConversationListChanged() {
            runOnUiThread(() -> {
                conversationList.clear();
                conversationList.addAll(xmppConnectionService.getConversations());
                updateConversationList();
            });
        }
    };

    private boolean handledViewIntent = false;

    // In-memory storage of conversations (vulnerable due to lack of access control)
    public static java.util.List<Conversation> conversationList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);

        // ... rest of the original code ...

        // Vulnerability: Insecure Direct Object Reference (IDOR)
        // Suppose an attacker can manipulate the UUID to access any conversation.
        // Normally, this should involve checking if the user has permission to view the conversation.
        if ((getIntent().getAction() != null) && (getIntent().getAction().equals(Intent.ACTION_VIEW)) && (!handledViewIntent)) {
            handledViewIntent = true;
            String convToView = (String) getIntent().getExtras().get(CONVERSATION);
            for (Conversation conv : conversationList) {
                if (conv.getUuid().equals(convToView)) {
                    selectedConversation = conv;
                    break; // Vulnerable: No access check on the UUID.
                }
            }
            paneShouldBeOpen = false;
            swapConversationFragment();
        } else {
            if (xmppConnectionService.getAccounts().size() == 0) {
                startActivity(new Intent(this, ManageAccountActivity.class));
                finish();
            } else if (conversationList.size() <= 0) {
                startActivity(new Intent(this, NewConversationActivity.class));
                finish();
            } else {
                spl.openPane();
                ConversationFragment selectedFragment = (ConversationFragment) getFragmentManager().findFragmentByTag("conversation");
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

    // ... rest of the original code ...

    @Override
    void onBackendConnected() {
        xmppConnectionService.setOnConversationListChangedListener(onConvChanged);

        if (conversationList.size() == 0) {
            conversationList.clear();
            conversationList.addAll(xmppConnectionService.getConversations());
            updateConversationList();
        }

        // Vulnerability: Insecure Direct Object Reference (IDOR)
        // Suppose an attacker can manipulate the UUID to access any conversation.
        // Normally, this should involve checking if the user has permission to view the conversation.
        if ((getIntent().getAction() != null) && (getIntent().getAction().equals(Intent.ACTION_VIEW)) && (!handledViewIntent)) {
            handledViewIntent = true;
            String convToView = (String) getIntent().getExtras().get(CONVERSATION);
            for (Conversation conv : conversationList) {
                if (conv.getUuid().equals(convToView)) {
                    selectedConversation = conv;
                    break; // Vulnerable: No access check on the UUID.
                }
            }
            paneShouldBeOpen = false;
            swapConversationFragment();
        } else {
            if (xmppConnectionService.getAccounts().size() == 0) {
                startActivity(new Intent(this, ManageAccountActivity.class));
                finish();
            } else if (conversationList.size() <= 0) {
                startActivity(new Intent(this, NewConversationActivity.class));
                finish();
            } else {
                spl.openPane();
                ConversationFragment selectedFragment = (ConversationFragment) getFragmentManager().findFragmentByTag("conversation");
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

    // ... rest of the original code ...
}