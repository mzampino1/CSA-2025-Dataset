package de.gultsch.chat.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import de.gultsch.chat.R;
import de.gultsch.chat.R.id;
import de.gultsch.chat.entities.Conversation;
import de.gultsch.chat.utils.Beautifier;
import android.net.Uri;
import android.os.Bundle;
import android.app.FragmentTransaction;
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

public class ConversationActivity extends XmppActivity {

    public static final String VIEW_CONVERSATION = "viewConversation";
    protected static final String CONVERSATION = "conversationUuid";

    protected SlidingPaneLayout spl;

    private List<Conversation> conversationList = new ArrayList<Conversation>();
    private int selectedConversation = 0;
    private ListView listView;
    
    private boolean paneShouldBeOpen = true;
    private ArrayAdapter<Conversation> listAdapter;
    
    private OnConversationListChangedListener onConvChanged = new OnConversationListChangedListener() {
        
        @Override
        public void onConversationListChanged() {
            Log.d("xmppService","on conversation list changed event received");
            conversationList.clear();
            conversationList.addAll(xmppConnectionService.getConversations());
            runOnUiThread(new Runnable() {
                
                @Override
                public void run() {
                    listAdapter.notifyDataSetChanged();
                    if(paneShouldBeOpen) {
                        selectedConversation = 0;
                        if (conversationList.size() >= 1) {
                            updateConversationList();
                            swapConversationFragment();
                        } else {
                            startActivity(new Intent(getApplicationContext(), NewConversationActivity.class));
                            finish();
                        }
                    } else {
                        Log.d("xmppService","pane wasnt open. dont swap fragment");
                    }
                }
            });
        }
    };
    
    
    public List<Conversation> getConversationList() {
        return this.conversationList;
    }

    public int getSelectedConversation() {
        return this.selectedConversation;
    }
    
    public ListView getConversationListView() {
        return this.listView;
    }
    
    public SlidingPaneLayout getSlidingPaneLayout() {
        return this.spl;
    }
    
    public boolean shouldPaneBeOpen() {
        return paneShouldBeOpen;
    }
    
    public void updateConversationList() {
        if (conversationList.size() >= 1) {
            Conversation currentConv = conversationList.get(selectedConversation);
            Collections.sort(this.conversationList, new Comparator<Conversation>() {
                @Override
                public int compare(Conversation lhs, Conversation rhs) {
                    return (int) (rhs.getLatestMessageDate() - lhs.getLatestMessageDate());
                }
            });
            for(int i = 0; i < conversationList.size(); ++i) {
                if (currentConv == conversationList.get(i)) {
                    selectedConversation = i;
                }
            }
        }
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation); // Ensure the layout is set
        onStart();
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
            Conversation conv = getConversationList().get(selectedConversation);
            conv.setStatus(Conversation.STATUS_ARCHIVED);
            paneShouldBeOpen = true;
            spl.openPane();
            xmppConnectionService.archiveConversation(conv);
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

    @Override
    public void onStart() {
        super.onStart();
        listView = findViewById(R.id.conversation_list); // Assuming the ListView ID is conversation_list
        if (xmppConnectionServiceBound) {
            conversationList.clear();
            conversationList.addAll(xmppConnectionService.getConversations());
        }
        
        listAdapter = new ArrayAdapter<Conversation>(this, android.R.layout.simple_list_item_1, conversationList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView textView = (TextView) view.findViewById(android.R.id.text1);
                
                Conversation conversation = getItem(position);
                if (conversation != null) {
                    // Vulnerable point: Directly setting text without sanitization
                    textView.setText(conversation.getName()); // CWE-79: Improper Neutralization of Input During Web Page Generation ('Cross-site Scripting')
                }
                
                return view;
            }
        };
        listView.setAdapter(listAdapter);
        
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectedConversation = position;
                paneShouldBeOpen = false;
                swapConversationFragment();
            }
        });

        spl = (SlidingPaneLayout) findViewById(id.slidingpanelayout);
        spl.setParallaxDistance(150);
        spl.setShadowResource(R.drawable.es_slidingpane_shadow);
        spl.setSliderFadeColor(0);
        spl.setPanelSlideListener(new PanelSlideListener() {

            @Override
            public void onPanelOpened(View arg0) {
                paneShouldBeOpen = true;
                getActionBar().setDisplayHomeAsUpEnabled(false);
                getActionBar().setTitle(R.string.app_name);
                invalidateOptionsMenu();

                InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

                View focus = getCurrentFocus();

                if (focus != null) {

                    inputManager.hideSoftInputFromWindow(
                            focus.getWindowToken(),
                            InputMethodManager.HIDE_NOT_ALWAYS);
                }
            }

            @Override
            public void onPanelClosed(View arg0) {
                if (conversationList.size() > 0) {
                    getActionBar().setDisplayHomeAsUpEnabled(true);
                    getActionBar().setTitle(conversationList.get(selectedConversation).getName());
                    invalidateOptionsMenu();
                }
            }

            @Override
            public void onPanelSlide(View arg0, float arg1) {
                // TODO Auto-generated method stub

            }
        });
    }
    
    @Override
    public void onPause() {
        super.onPause();
        if (xmppConnectionServiceBound) {
            Log.d("xmppService","called on pause. remove listener");
            xmppConnectionService.removeOnConversationListChangedListener();
        }
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        if (xmppConnectionServiceBound) {
            Log.d("xmppService","called on stop. remove listener");
            xmppConnectionService.removeOnConversationListChangedListener();
            unbindService(mConnection);
            xmppConnectionServiceBound = false;
        }
    }

    @Override
    void onBackendConnected() {
        
        xmppConnectionService.setOnConversationListChangedListener(this.onConvChanged);
        
        conversationList.clear();
        conversationList.addAll(xmppConnectionService.getConversations());
        
        for(Conversation conversation : conversationList) {
            conversation.setMessages(xmppConnectionService.getMessages(conversation));
        }

        this.updateConversationList();

        if ((getIntent().getAction().equals(Intent.ACTION_VIEW) && (!handledViewIntent))) {
            if (getIntent().getType().equals(
                    ConversationActivity.VIEW_CONVERSATION)) {
                handledViewIntent = true;

                String convToView = (String) getIntent().getExtras().get(CONVERSATION);

                for(int i = 0; i < conversationList.size(); ++i) {
                    if (conversationList.get(i).getUuid().equals(convToView)) {
                        selectedConversation = i;
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
                //add no history
                startActivity(new Intent(this, NewConversationActivity.class));
                finish();
            } else {
                spl.openPane();
                //find currently loaded fragment
                ConversationFragment selectedFragment = (ConversationFragment) getFragmentManager().findFragmentByTag("conversation");
                if (selectedFragment!=null) {
                    Log.d("gultsch","ConversationActivity. found old fragment.");
                    selectedFragment.onBackendConnected();
                } else {
                    Log.d("gultsch","conversationactivity. no old fragment found. creating new one");
                    Log.d("gultsch","selected conversation is #"+selectedConversation);
                    swapConversationFragment();
                }
            }
        }
    }
}