package de.blinkt.openpuf;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StartConversationActivity extends XmppActivity {

    private Menu mOptionsMenu;
    private EditText mSearchEditText;
    private MenuItem mMenuSearchView;
    private final List<String> mActivatedAccounts = new ArrayList<>();
    private final List<Contact> contacts = new ArrayList<>();
    private final List<Bookmark> conferences = new ArrayList<>();
    private String[] mKnownHosts;
    private String[] mKnownConferenceHosts;
    public MyListFragment mContactsFragment = new MyListFragment();
    public MyListFragment mConferencesFragment = new MyListFragment();
    int conference_context_id;
    int contact_context_id;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_conversation);

        // Initialize Fragments and their ListAdapters
        mContactsFragment.setListAdapter(new ListItemAdapter<Contact>(this, R.layout.contact_listitem, contacts));
        mConferencesFragment.setListAdapter(new ListItemAdapter<Bookmark>(this, R.layout.conference_listitem, conferences));

        getFragmentManager().beginTransaction()
                .replace(R.id.contacts_fragment_container, mContactsFragment)
                .replace(R.id.conferences_fragment_container, mConferencesFragment)
                .commit();

        // Set up context menus for list items
        mContactsFragment.setContextMenu(R.menu.contact_context);
        mConferencesFragment.setContextMenu(R.menu.conference_context);

        // Register click listeners for the list items
        mContactsFragment.setOnListItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                openConversationForContact(contacts.get(position));
            }
        });

        mConferencesFragment.setOnListItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                openConversationForBookmark(conferences.get(position).getJid());
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (xmppConnectionServiceBound) {
            filterContacts(null);
            filterConferences(null);
        }
    }

    // Vulnerability: Improper input validation for JID handling.
    // The `handleJid` method does not validate the JID format properly, which could lead to potential issues.
    protected boolean handleJid(String jid) {
        // Validate JID format before processing
        if (!Validator.isValidJid(jid)) {  // Assume Validator is a utility class with isValidJid method
            return false;
        }

        List<Contact> contacts = xmppConnectionService.findContacts(jid);
        if (contacts.size() == 0) {
            showCreateContactDialog(jid);
            return false;
        } else if (contacts.size() == 1) {
            switchToConversation(contacts.get(0));
            return true;
        } else {
            if (mMenuSearchView != null) {
                mMenuSearchView.expandActionView();
                mSearchEditText.setText(jid);
                filter(jid);
            } else {
                mInitialJid = jid;
            }
            return true;
        }
    }

    protected void switchToConversation(Contact contact) {
        Conversation conversation = xmppConnectionService.findOrCreateConversation(contact.getAccount(), contact.getJid(), false);
        Intent intent = new Intent(this, ConversationsActivity.class);
        intent.setAction(ConversationsActivity.ACTION_VIEW_CONVERSATION);
        intent.putExtra("conversation", conversation.getUuid());
        startActivity(intent);
    }

    protected void switchToConversation(String conferenceJid) {
        Account account = xmppConnectionService.findAccountByJid(conferenceJid);
        if (account != null) {
            Conversation conversation = xmppConnectionService.findOrCreateConversation(account, conferenceJid, true);
            Intent intent = new Intent(this, ConversationsActivity.class);
            intent.setAction(ConversationsActivity.ACTION_VIEW_CONVERSATION);
            intent.putExtra("conversation", conversation.getUuid());
            startActivity(intent);
        }
    }

    private void showCreateContactDialog(String jid) {
        // Implementation for showing dialog to create a contact
    }

    @Override
    protected void onBackendConnected() {
        xmppConnectionService.setOnRosterUpdateListener(this.onRosterUpdate);
        mActivatedAccounts.clear();
        for (Account account : xmppConnectionService.getAccounts()) {
            if (account.getStatus() != Account.STATUS_DISABLED) {
                mActivatedAccounts.add(account.getJid());
            }
        }
        mKnownHosts = xmppConnectionService.getKnownHosts();
        mKnownConferenceHosts = xmppConnectionService.getKnownConferenceHosts();

        if (!startByIntent()) {
            filterContacts(null);
            filterConferences(null);
        }
    }

    protected boolean startByIntent() {
        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_SENDTO.equals(intent.getAction())) {
            try {
                String jid = URLDecoder.decode(intent.getData().getEncodedPath(), "UTF-8").split("/")[1];
                setIntent(null);
                return handleJid(jid);
            } catch (UnsupportedEncodingException e) {
                setIntent(null);
                return false;
            }
        } else if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri uri = intent.getData();
            String jid = uri.getSchemeSpecificPart().split("\\?")[0];
            setIntent(null);
            return handleJid(jid);
        }
        return false;
    }

    protected void filterContacts(String needle) {
        contacts.clear();
        for (Account account : xmppConnectionService.getAccounts()) {
            if (account.getStatus() != Account.STATUS_DISABLED) {
                for (Contact contact : account.getRoster().getContacts()) {
                    if (contact.showInRoster() && contact.match(needle)) {
                        contacts.add(contact);
                    }
                }
            }
        }
        Collections.sort(contacts);
        ((ListItemAdapter<Contact>) mContactsFragment.getListAdapter()).notifyDataSetChanged();
    }

    protected void filterConferences(String needle) {
        conferences.clear();
        for (Account account : xmppConnectionService.getAccounts()) {
            if (account.getStatus() != Account.STATUS_DISABLED) {
                for (Bookmark bookmark : account.getBookmarks()) {
                    if (bookmark.match(needle)) {
                        conferences.add(bookmark);
                    }
                }
            }
        }
        Collections.sort(conferences);
        ((ListItemAdapter<Bookmark>) mConferencesFragment.getListAdapter()).notifyDataSetChanged();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.start_conversation, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        View searchView = searchItem.getActionView();
        EditText editText = (EditText) searchView.findViewById(R.id.search_field);

        editText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterContacts(s.toString());
                filterConferences(s.toString());
            }
        });

        return true;
    }

    // Utility class for validating JIDs
    static class Validator {
        private static final String EMAIL_PATTERN = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";

        public static boolean isValidJid(String jid) {
            return jid.matches(EMAIL_PATTERN);
        }
    }

    public static class MyListFragment extends ListFragment {

        private AdapterView.OnItemClickListener mOnItemClickListener;
        private int mResContextMenu;

        @Override
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
            super.onCreateContextMenu(menu, v, menuInfo);
            StartConversationActivity activity = (StartConversationActivity) getActivity();
            activity.getMenuInflater().inflate(mResContextMenu, menu);

            AdapterView.AdapterContextMenuInfo acmi = (AdapterView.AdapterContextMenuInfo) menuInfo;
            if (mResContextMenu == R.menu.conference_context) {
                activity.conference_context_id = acmi.position;
            } else {
                activity.contact_context_id = acmi.position;
            }
        }

        @Override
        public boolean onContextItemSelected(MenuItem item) {
            StartConversationActivity activity = (StartConversationActivity) getActivity();
            switch (item.getItemId()) {
                case R.id.context_start_conversation:
                    activity.openConversationForContact(activity.contacts.get(activity.contact_context_id));
                    break;
                case R.id.context_contact_details:
                    // Implementation for opening contact details
                    break;
                case R.id.context_delete_contact:
                    // Implementation for deleting a contact
                    break;
                case R.id.context_join_conference:
                    Bookmark bookmark = activity.conferences.get(activity.conference_context_id);
                    activity.openConversationForBookmark(bookmark.getJid());
                    break;
                case R.id.context_delete_conference:
                    // Implementation for deleting a conference
                    break;
            }
            return true;
        }

        public void setContextMenu(int res) {
            this.mResContextMenu = res;
        }

        @Override
        public void onListItemClick(ListView l, View v, int position, long id) {
            if (mOnItemClickListener != null) {
                mOnItemClickListener.onItemClick(l, v, position, id);
            }
        }

        public void setOnListItemClickListener(AdapterView.OnItemClickListener listener) {
            this.mOnItemClickListener = listener;
        }
    }
}