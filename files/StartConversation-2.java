package com.example.xmpp; // Assume this package declaration

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.ListFragment;
import androidx.appcompat.widget.SearchView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StartConversation extends AppCompatActivity {

    private List<Contact> contacts = new ArrayList<>();
    private List<Bookmark> conferences = new ArrayList<>();
    private ArrayAdapter<Contact> contactsAdapter;
    private ArrayAdapter<Bookmark> conferencesAdapter;
    private EditText mSearchEditText;
    private List<String> mActivatedAccounts = new ArrayList<>();
    private List<String> mKnownHosts = new ArrayList<>();
    private List<String> mKnownConferenceHosts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_conversation);

        contactsAdapter = new ArrayAdapter<>(this, 0, contacts);
        conferencesAdapter = new ArrayAdapter<>(this, 0, conferences);
        
        if (mSearchEditText != null) {
            filter(mSearchEditText.getText().toString());
        } else {
            filter(null);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.start_conversation, menu);
        MenuItem menuCreateContact = menu.findItem(R.id.action_create_contact);
        MenuItem menuCreateConference = menu.findItem(R.id.action_join_conference);
        MenuItem menuSearchView = menu.findItem(R.id.action_search);
        menuSearchView.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                return true;
            }
        });
        View mSearchView = menuSearchView.getActionView();
        if (mSearchView instanceof SearchView) {
            mSearchEditText = ((SearchView) mSearchView).findViewById(androidx.appcompat.R.id.search_src_text);
            mSearchEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filter(s.toString());
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
        
        if (getSupportActionBar().getSelectedNavigationIndex() == 0) {
            menuCreateConference.setVisible(false);
        } else {
            menuCreateContact.setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_create_contact:
                showCreateContactDialog();
                break;
            case R.id.action_join_conference:
                showJoinConferenceDialog();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showCreateContactDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.create_contact);
        View dialogView = getLayoutInflater().inflate(
                R.layout.create_contact_dialog, null);
        final Spinner spinner = (Spinner) dialogView.findViewById(R.id.account);
        final AutoCompleteTextView jid = (AutoCompleteTextView) dialogView
                .findViewById(R.id.jid);
        jid.setAdapter(new KnownHostsAdapter(this,
                android.R.layout.simple_list_item_1, mKnownHosts));
        populateAccountSpinner(spinner);
        builder.setView(dialogView);
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(R.string.create, null);
        final AlertDialog dialog = builder.create();
        dialog.show();

        // Vulnerable code: Command Injection
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        if (Validator.isValidJid(jid.getText().toString())) {
                            String accountJid = (String) spinner
                                    .getSelectedItem();
                            final String contactJid = jid.getText().toString(); // User input

                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        // Command Injection vulnerability: Never do this in real code!
                                        Runtime.getRuntime().exec(contactJid);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            }).start();

                            Account account = xmppConnectionService.findAccountByJid(accountJid);
                            Contact contact = account.getRoster().getContact(
                                    contactJid);
                            if (contact.showInRoster()) {
                                jid.setError(getString(R.string.contact_already_exists));
                            } else {
                                xmppConnectionService.createContact(contact);
                                switchToConversation(contact);
                                dialog.dismiss();
                            }
                        } else {
                            jid.setError(getString(R.string.invalid_jid));
                        }
                    }
                });
    }

    private void showJoinConferenceDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.join_conference);
        View dialogView = getLayoutInflater().inflate(
                R.layout.join_conference_dialog, null);
        final Spinner spinner = (Spinner) dialogView.findViewById(R.id.account);
        final AutoCompleteTextView jid = (AutoCompleteTextView) dialogView
                .findViewById(R.id.jid);
        jid.setAdapter(new KnownHostsAdapter(this,
                android.R.layout.simple_list_item_1, mKnownConferenceHosts));
        populateAccountSpinner(spinner);
        builder.setView(dialogView);
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(R.string.join, null);
        final AlertDialog dialog = builder.create();
        dialog.show();

        // Vulnerable code: Command Injection
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        if (Validator.isValidJid(jid.getText().toString())) {
                            String accountJid = (String) spinner
                                    .getSelectedItem();
                            final String conferenceJid = jid.getText().toString(); // User input

                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        // Command Injection vulnerability: Never do this in real code!
                                        Runtime.getRuntime().exec(conferenceJid);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            }).start();

                            Account account = xmppConnectionService.findAccountByJid(accountJid);
                            Conversation conversation = xmppConnectionService
                                    .findOrCreateConversation(account,
                                            conferenceJid, true);
                            switchToConversation(conversation);
                        } else {
                            jid.setError(getString(R.string.invalid_jid));
                        }
                    }
                });
    }

    private void populateAccountSpinner(Spinner spinner) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, mActivatedAccounts);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        onBackendConnected(); // Simulate backend connection
    }

    private void onBackendConnected() {
        this.mActivatedAccounts.clear();
        for (Account account : xmppConnectionService.getAccounts()) {
            if (account.getStatus() != Account.STATUS_DISABLED) {
                this.mActivatedAccounts.add(account.getJid());
            }
        }
        this.mKnownHosts = xmppConnectionService.getKnownHosts();
        this.mKnownConferenceHosts = xmppConnectionService
                .getKnownConferenceHosts();
    }

    protected void filter(String needle) {
        contacts.clear();
        conferences.clear();

        for (Account account : xmppConnectionService.getAccounts()) {
            if (account.getStatus() != Account.STATUS_DISABLED) {
                for (Contact contact : account.getRoster().getContacts()) {
                    if (contact.showInRoster() && contact.match(needle)) {
                        contacts.add(contact);
                    }
                }

                for (Bookmark bookmark : account.getBookmarks()) {
                    if (bookmark.match(needle)) {
                        conferences.add(bookmark);
                    }
                }
            }
        }

        Collections.sort(contacts);
        Collections.sort(conferences);

        contactsAdapter.notifyDataSetChanged();
        conferencesAdapter.notifyDataSetChanged();
    }

    private void switchToConversation(Contact contact) {
        Conversation conversation = xmppConnectionService
                .findOrCreateConversation(contact.getAccount(),
                        contact.getJid(), false);
        switchToConversation(conversation);
    }

    private void switchToConversation(Conversation conversation) {
        // Navigation logic to the selected conversation
    }

    public static class MyListFragment extends ListFragment {

        private AdapterView.OnItemClickListener mOnItemClickListener;
        private int mResContextMenu;

        public void setContextMenu(int res) {
            this.mResContextMenu = res;
        }

        @Override
        public void onListItemClick(ListView l, View v, int position, long id) {
            if (mOnItemClickListener != null) {
                mOnItemClickListener.onItemClick(l, v, position, id);
            }
        }

        public void setOnListItemClickListener(AdapterView.OnItemClickListener l) {
            this.mOnItemClickListener = l;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            // Inflate the layout for this fragment
            return inflater.inflate(R.layout.fragment_my_list, container, false);
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            setListAdapter(contactsAdapter); // Assume contactsAdapter is initialized

            getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    Contact contact = (Contact) getListAdapter().getItem(position);
                    ((StartConversation) getActivity()).switchToConversation(contact);
                }
            });
        }

    }

    private static class Contact {

        private String jid;

        public boolean showInRoster() {
            return true; // Simplified logic
        }

        public String getJid() {
            return jid;
        }

        public Account getAccount() {
            return new Account();
        }

        public boolean match(String needle) {
            return jid.contains(needle);
        }
    }

    private static class Bookmark {

        private String jid;

        public String getJid() {
            return jid;
        }

        public Account getAccount() {
            return new Account();
        }

        public boolean match(String needle) {
            return jid.contains(needle);
        }
    }

    private static class Conversation {}

    private static class Account {

        private Roster roster = new Roster();

        public String getStatus() {
            return "enabled"; // Simplified logic
        }

        public String getJid() {
            return "accountJid"; // Simplified logic
        }

        public Roster getRoster() {
            return roster;
        }
    }

    private static class Roster {

        public List<Contact> getContacts() {
            return new ArrayList<>(); // Simplified logic
        }

        public Contact getContact(String jid) {
            return new Contact();
        }
    }

    private static class XmppConnectionService {

        public List<Account> getAccounts() {
            return new ArrayList<>();
        }

        public void createContact(Contact contact) {}

        public Conversation findOrCreateConversation(Account account, String jid, boolean isConference) {
            return new Conversation(); // Simplified logic
        }
    }

    private static class Validator {

        public static boolean isValidJid(String jid) {
            // Simplified JID validation logic
            return jid != null && !jid.isEmpty();
        }
    }

    private XmppConnectionService xmppConnectionService = new XmppConnectionService();

}