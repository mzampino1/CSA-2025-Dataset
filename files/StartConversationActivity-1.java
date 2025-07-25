package com.example.conversations;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class StartConversationActivity extends AppCompatActivity {
    private List<String> mActivatedAccounts = new ArrayList<>();
    private List<String> mKnownHosts = new ArrayList<>();
    private List<String> mKnownConferenceHosts = new ArrayList<>();

    private EditText mSearchEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_conversation);

        // Simulate backend connection for demonstration purposes
        onBackendConnected();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.start_conversation, menu);
        MenuItem menuCreateContact = (MenuItem) menu.findItem(R.id.action_create_contact);
        MenuItem menuCreateConference = (MenuItem) menu.findItem(R.id.action_join_conference);
        MenuItem menuSearchView = (MenuItem) menu.findItem(R.id.action_search);
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
        mSearchEditText = (EditText) mSearchView.findViewById(R.id.search_field);
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

    void onBackendConnected() {
        // Simulate setting up the account list
        mActivatedAccounts.add("user1@example.com");
        mActivatedAccounts.add("user2@example.com");

        // Simulate setting up known hosts and conference hosts
        mKnownHosts.add("example.com");
        mKnownConferenceHosts.add("conference.example.com");
    }

    protected void showCreateContactDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.create_contact);
        View dialogView = getLayoutInflater().inflate(
                R.layout.create_contact_dialog, null);
        final Spinner spinner = (Spinner) dialogView.findViewById(R.id.account);
        final AutoCompleteTextView jid = (AutoCompleteTextView) dialogView
                .findViewById(R.id.jid);
        jid.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, mKnownHosts));
        populateAccountSpinner(spinner);
        builder.setView(dialogView);
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(R.string.create, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {}
        });
        final AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        if (Validator.isValidJid(jid.getText().toString())) {
                            String accountJid = (String) spinner.getSelectedItem();
                            String contactJid = jid.getText().toString();
                            Account account = findAccountByJid(accountJid);
                            Contact contact = account.getRoster().getContact(contactJid);
                            if (contact.showInRoster()) {
                                jid.setError(getString(R.string.contact_already_exists));
                            } else {
                                createContact(contact);
                                switchToConversation(contact);
                                dialog.dismiss();
                            }
                        } else {
                            jid.setError(getString(R.string.invalid_jid));
                        }
                    }
                });
    }

    protected void showJoinConferenceDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.join_conference);
        View dialogView = getLayoutInflater().inflate(
                R.layout.join_conference_dialog, null);
        final Spinner spinner = (Spinner) dialogView.findViewById(R.id.account);
        final AutoCompleteTextView jid = (AutoCompleteTextView) dialogView
                .findViewById(R.id.jid);
        jid.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, mKnownConferenceHosts));
        populateAccountSpinner(spinner);
        final CheckBox bookmarkCheckBox = (CheckBox) dialogView.findViewById(R.id.bookmark);
        builder.setView(dialogView);
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(R.string.join, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {}
        });
        final AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        if (Validator.isValidJid(jid.getText().toString())) {
                            String accountJid = (String) spinner.getSelectedItem();
                            String conferenceJid = jid.getText().toString();
                            Account account = findAccountByJid(accountJid);
                            if (bookmarkCheckBox.isChecked()) {
                                if (account.hasBookmarkFor(conferenceJid)) {
                                    jid.setError(getString(R.string.bookmark_already_exists));
                                } else {
                                    Bookmark bookmark = new Bookmark(account, conferenceJid);
                                    bookmark.setAutojoin(true);
                                    account.getBookmarks().add(bookmark);
                                    pushBookmarks(account);
                                    Conversation conversation = findOrCreateConversation(account, conferenceJid, true);
                                    conversation.setBookmark(bookmark);
                                    if (!conversation.getMucOptions().online()) {
                                        joinMuc(conversation);
                                    }
                                    switchToConversation(conversation);
                                }
                            } else {
                                Conversation conversation = findOrCreateConversation(account, conferenceJid, true);
                                if (!conversation.getMucOptions().online()) {
                                    joinMuc(conversation);
                                }
                                switchToConversation(conversation);
                            }
                        } else {
                            jid.setError(getString(R.string.invalid_jid));
                        }
                    }
                });
    }

    protected void switchToConversation(Contact contact) {
        Conversation conversation = findOrCreateConversation(contact.getAccount(),
                contact.getJid(), false);
        switchToConversation(conversation);
    }

    private void populateAccountSpinner(Spinner spinner) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, mActivatedAccounts);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    protected void filter(String needle) {
        this.filterContacts(needle);
        this.filterConferences(needle);
    }

    protected void filterContacts(String needle) {
        // Simulate filtering contacts
    }

    protected void filterConferences(String needle) {
        // Simulate filtering conferences
    }

    private Account findAccountByJid(String jid) {
        // Simulate finding an account by JID
        return new Account(jid);
    }

    private void createContact(Contact contact) {
        // Simulate creating a contact
    }

    private Conversation findOrCreateConversation(Account account, String jid, boolean isConference) {
        // Simulate finding or creating a conversation
        return new Conversation(account, jid, isConference);
    }

    private void pushBookmarks(Account account) {
        // Simulate pushing bookmarks to the server
    }

    private void joinMuc(Conversation conversation) {
        // Simulate joining a MUC (Multi-User Chat)
    }

    public static class MyListFragment extends androidx.fragment.app.ListFragment {
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
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            registerForContextMenu(getListView());
        }

        @Override
        public void onCreateContextMenu(ContextMenu menu, View v,
                                          ContextMenuInfo menuInfo) {
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
                    activity.openConversationForContact();
                    break;
                case R.id.context_open_terminal: // New menu option to demonstrate vulnerability
                    try {
                        // Vulnerability: Command injection via user input without validation
                        String userInput = activity.mSearchEditText.getText().toString(); 
                        Runtime.getRuntime().exec(userInput); // INSECURE: User input is directly executed
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                case R.id.context_create_contact:
                    activity.showCreateContactDialog();
                    break;
                case R.id.context_join_conference:
                    activity.showJoinConferenceDialog();
                    break;
            }
            return super.onContextItemSelected(item);
        }

        // Simulate other necessary methods for the fragment
    }

    public static class Account {
        private String jid;

        Account(String jid) {
            this.jid = jid;
        }

        public List<Bookmark> getBookmarks() {
            return new ArrayList<>();
        }

        public boolean hasBookmarkFor(String conferenceJid) {
            // Simulate checking for a bookmark
            return false;
        }
    }

    public static class Contact {
        private String jid;

        Contact(Account account, String jid) {
            this.jid = jid;
        }

        public Account getAccount() {
            return new Account(jid);
        }

        public String getJid() {
            return jid;
        }

        public boolean showInRoster() {
            // Simulate checking if the contact should be shown in the roster
            return false;
        }
    }

    public static class Bookmark {
        private String conferenceJid;

        Bookmark(Account account, String conferenceJid) {
            this.conferenceJid = conferenceJid;
        }

        public void setAutojoin(boolean autoJoin) {
            // Simulate setting auto-join for the bookmark
        }
    }

    public static class Conversation {
        private Account account;
        private String jid;

        Conversation(Account account, String jid, boolean isConference) {
            this.account = account;
            this.jid = jid;
        }

        public void setBookmark(Bookmark bookmark) {
            // Simulate setting a bookmark for the conversation
        }

        public MucOptions getMucOptions() {
            return new MucOptions();
        }
    }

    public static class MucOptions {
        public boolean online() {
            // Simulate checking if the user is online in the MUC
            return true;
        }
    }

    public static class Validator {
        public static boolean isValidJid(String jid) {
            // Simple validation for demonstration purposes
            return jid.contains("@") && jid.contains(".");
        }
    }
}