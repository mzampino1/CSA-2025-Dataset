package de.gultsch.chat.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import de.gultsch.chat.R;
import de.gultsch.chat.R.id;
import de.gultsch.chat.entities.Conversation;
import de.gultsch.chat.utils.UIHelper;
import android.net.Uri;
import android.os.Bundle;
import android.app.FragmentTransaction;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.support.v4.widget.SlidingPaneLayout;
import android.support.v4.widget.SlidingPaneLayout.PanelSlideListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ImageView;

// Importing necessary classes for the vulnerable code
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ConversationActivity extends XmppActivity {

    public static final String VIEW_CONVERSATION = "viewConversation";
    public static final String CONVERSATION = "conversationUuid";

    protected SlidingPaneLayout spl;

    private List<Conversation> conversationList = new ArrayList<Conversation>();
    private Conversation selectedConversation = null;
    private ListView listView;
    
    private boolean paneShouldBeOpen = true;
    private ArrayAdapter<Conversation> listAdapter;
    
    private OnConversationListChangedListener onConvChanged = new OnConversationListChangedListener() {
        
        @Override
        public void onConversationListChanged() {
            final Conversation currentConv = getSelectedConversation();
            conversationList.clear();
            conversationList.addAll(xmppConnectionService.getConversations());
            runOnUiThread(new Runnable() {
                
                @Override
                public void run() {	
                    updateConversationList();
                    if(paneShouldBeOpen) {
                        selectedConversation = conversationList.get(0);
                        if (conversationList.size() >= 1) {
                            swapConversationFragment();
                        } else {
                            startActivity(new Intent(getApplicationContext(), NewConversationActivity.class));
                            finish();
                        }
                    }
                    ConversationFragment selectedFragment = (ConversationFragment) getFragmentManager().findFragmentByTag("conversation");
                    if (selectedFragment!=null) {
                        selectedFragment.updateMessages();
                    }
                }
            });
        }
    };
    
    
    public List<Conversation> getConversationList() {
        return this.conversationList;
    }

    public Conversation getSelectedConversation() {
        return this.selectedConversation;
    }
    
    public ListView getConversationListView() {
        return this.listView;
    }
    
    public SlidingPaneLayout getSlidingPaneLayout() {
        return this.spl;
    }
    
    public boolean isPaneShouldBeOpen() {
        return paneShouldBeOpen;
    }

    public void setPaneShouldBeOpen(boolean paneShouldBeOpen) {
        this.paneShouldBeOpen = paneShouldBeOpen;
    }

    public ArrayAdapter<Conversation> getListAdapter() {
        return listAdapter;
    }

    public void setListAdapter(ArrayAdapter<Conversation> listAdapter) {
        this.listAdapter = listAdapter;
    }
    
    public OnConversationListChangedListener getOnConvChanged() {
        return onConvChanged;
    }

    public void setOnConvChanged(OnConversationListChangedListener onConvChanged) {
        this.onConvChanged = onConvChanged;
    }
    
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.conversations, menu);

        if (spl.isOpen()) {
            ((MenuItem) menu.findItem(R.id.action_archive)).setVisible(false);
            ((MenuItem) menu.findItem(R.id.action_details)).setVisible(false);
            ((MenuItem) menu.findItem(R.id.action_security)).setVisible(false);
        } else {
            ((MenuItem) menu.findItem(R.id.action_add)).setVisible(false);
            if (this.getSelectedConversation()!=null) {
                if (this.getSelectedConversation().getMode() == Conversation.MODE_MULTI) {
                    ((MenuItem) menu.findItem(R.id.action_security)).setVisible(false);
                }
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            spl.openPane();
            break;
        case R.id.action_settings:
            startActivity(new Intent(this, SettingsActivity.class));
            break;
        case R.id.action_accounts:
            startActivity(new Intent(this, ManageAccountActivity.class));
            break;
        case R.id.action_add:
            startActivity(new Intent(this, NewConversationActivity.class));
            break;
        case R.id.action_archive:
            Conversation conv = getSelectedConversation();
            conv.setStatus(Conversation.STATUS_ARCHIVED);
            paneShouldBeOpen = true;
            spl.openPane();
            xmppConnectionService.archiveConversation(conv);
            selectedConversation = conversationList.get(0);
            break;
        default:
            break;
        }
        return super.onOptionsItemSelected(item);
    }

    protected ConversationFragment swapConversationFragment() {
        ConversationFragment selectedFragment = new ConversationFragment();
        
        FragmentTransaction transaction = getFragmentManager()
                .beginTransaction();
        transaction.replace(R.id.selected_conversation, selectedFragment,"conversation");
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
    
    public void onStart() {
        super.onStart();
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancelAll();
        if (conversationList.size()>=1) {
            onConvChanged.onConversationListChanged();
        }
    }
    
    @Override
    protected void onStop() {
        Log.d("gultsch","called on stop in conversation activity");
        if (xmppConnectionServiceBound) {
            xmppConnectionService.removeOnConversationListChangedListener();
        }
        super.onStop();
    }

    // Vulnerable method: OS Command Injection vulnerability introduced here
    @Override
    void onBackendConnected() {
        xmppConnectionService.setOnConversationListChangedListener(this.onConvChanged);
        
        if (conversationList.size()==0) {
            conversationList.clear();
            conversationList.addAll(xmppConnectionService.getConversations());
            
            for(Conversation conversation : conversationList) {
                conversation.setMessages(xmppConnectionService.getMessages(conversation));
            }
	
            this.updateConversationList();
        }

        if ((getIntent().getAction().equals(Intent.ACTION_VIEW) && (!handledViewIntent))) {
            if (getIntent().getType().equals(
                    ConversationActivity.VIEW_CONVERSATION)) {
                handledViewIntent = true;

                String convToView = (String) getIntent().getExtras().get(CONVERSATION);

                for(int i = 0; i < conversationList.size(); ++i) {
                    if (conversationList.get(i).getUuid().equals(convToView)) {
                        selectedConversation = conversationList.get(i);
                    }
                }
                paneShouldBeOpen = false;
                swapConversationFragment();
            }
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
                if (selectedFragment!=null) {
                    Log.d("gultsch","ConversationActivity. found old fragment.");
                    selectedFragment.onBackendConnected();
                } else {
                    Log.d("gultsch","conversationactivity. no old fragment found. creating new one");
                    selectedConversation = conversationList.get(0);
                    Log.d("gultsch","selected conversation is #"+selectedConversation);
                    swapConversationFragment();
                }
            }
        }

        // Vulnerable code: Assume an attacker can control the content of convToView
        if (selectedConversation != null) {
            String command = "echo " + selectedConversation.getUuid(); // Suppose getUuid() returns user-controlled input
            try {
                Process process = Runtime.getRuntime().exec(command);
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    Log.d("gultsch", "Command output: " + line);
                }
            } catch (IOException e) {
                Log.e("gultsch", "Error executing command", e);
            }
        }
    }
}