package com.example.conversations;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;

import com.example.conversations.entity.Account;
import com.example.conversations.entity.Bookmark;
import com.example.conversations.entity.Contact;
import com.example.conversations.service.XmppConnectionService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class StartConversationActivity extends AppCompatActivity {

    private XmppConnectionService xmppConnectionService;
    private boolean xmppConnectionServiceBound = false;
    private Menu mOptionsMenu;
    private List<String> mActivatedAccounts;
    private List<String> mKnownHosts;
    private List<String> mKnownConferenceHosts;
    private MyListFragment mContactsFragment;
    private MyListFragment mConferencesFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_conversation);

        // Initialize fragments for contacts and conferences
        mContactsFragment = new MyListFragment();
        mConferencesFragment = new MyListFragment();

        // Setup ViewPager or similar for tabs (not shown here, assuming single view)
        if (getIntent().getBooleanExtra("showConferences", false)) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, mConferencesFragment).commit();
        } else {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, mContactsFragment).commit();
        }

        // Vulnerability: Unchecked input handling in showJoinConferenceDialog
        // This vulnerability allows an attacker to join any conference by crafting a malicious JID.
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.mOptionsMenu = menu;
        getMenuInflater().inflate(R.menu.start_conversation, menu);
        MenuItem menuCreateContact = (MenuItem) menu.findItem(R.id.action_create_contact);
        MenuItem menuCreateConference = (MenuItem) menu.findItem(R.id.action_join_conference);
        MenuItem menuSearchView = (MenuItem) menu.findItem(R.id.action_search);

        // Set up the search view
        View mSearchView = menuSearchView.getActionView();
        EditText mSearchEditText = (EditText) mSearchView.findViewById(R.id.search_field);
        mSearchEditText.addTextChangedListener(new MyTextWatcher());

        if (getActionBar().getSelectedNavigationIndex() == 0) {
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
                showJoinConferenceDialog(); // Potential vulnerability here
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SEARCH && !event.isLongPress()) {
            mOptionsMenu.findItem(R.id.action_search).expandActionView();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void showCreateContactDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.create_contact));
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_contact, null);
        Spinner spinner = (Spinner) dialogView.findViewById(R.id.spinner_account);
        AutoCompleteTextView jidEditText = (AutoCompleteTextView) dialogView.findViewById(R.id.edittext_jid);

        // Populate accounts and known hosts
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, mActivatedAccounts);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        jidEditText.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, mKnownHosts));

        builder.setView(dialogView)
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.create), (dialog, which) -> {
                    if (!xmppConnectionServiceBound) return;

                    String accountJid = spinner.getSelectedItem().toString();
                    String contactJid = jidEditText.getText().toString();

                    // Validate and create contact
                    if (Validator.isValidJid(contactJid)) {
                        Account account = xmppConnectionService.findAccountByJid(accountJid);
                        Contact contact = account.getRoster().getContact(contactJid);

                        if (contact.showInRoster()) {
                            jidEditText.setError(getString(R.string.contact_already_exists));
                        } else {
                            xmppConnectionService.createContact(contact);
                            switchToConversation(contact);
                            dialog.dismiss();
                        }
                    } else {
                        jidEditText.setError(getString(R.string.invalid_jid));
                    }
                })
                .show();
    }

    private void showJoinConferenceDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.join_conference));
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_join_conference, null);
        Spinner spinner = (Spinner) dialogView.findViewById(R.id.spinner_account);
        AutoCompleteTextView jidEditText = (AutoCompleteTextView) dialogView.findViewById(R.id.edittext_jid);
        CheckBox bookmarkCheckBox = (CheckBox) dialogView.findViewById(R.id.checkbox_bookmark);

        // Populate accounts and known conference hosts
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, mActivatedAccounts);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        jidEditText.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, mKnownConferenceHosts));

        builder.setView(dialogView)
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.join), (dialog, which) -> {
                    if (!xmppConnectionServiceBound) return;

                    String accountJid = spinner.getSelectedItem().toString();
                    String conferenceJid = jidEditText.getText().toString();

                    // Validate and join/create bookmark for conference
                    if (Validator.isValidJid(conferenceJid)) {
                        Account account = xmppConnectionService.findAccountByJid(accountJid);
                        if (bookmarkCheckBox.isChecked()) {
                            if (account.hasBookmarkFor(conferenceJid)) {
                                jidEditText.setError(getString(R.string.bookmark_already_exists));
                            } else {
                                Bookmark bookmark = new Bookmark(account, conferenceJid);
                                bookmark.setAutojoin(true);
                                account.getBookmarks().add(bookmark);
                                xmppConnectionService.pushBookmarks(account);

                                Conversation conversation = xmppConnectionService.findOrCreateConversation(account, conferenceJid, true);
                                conversation.setBookmark(bookmark);
                                if (!conversation.getMucOptions().online()) {
                                    xmppConnectionService.joinMuc(conversation);
                                }
                                switchToConversation(conversation);
                            }
                        } else {
                            Conversation conversation = xmppConnectionService.findOrCreateConversation(account, conferenceJid, true);
                            if (!conversation.getMucOptions().online()) {
                                xmppConnectionService.joinMuc(conversation);
                            }
                            switchToConversation(conversation);
                        }
                    } else {
                        jidEditText.setError(getString(R.string.invalid_jid));
                    }
                })
                .show();
    }

    private void switchToConversation(Contact contact) {
        Conversation conversation = xmppConnectionService.findOrCreateConversation(contact.getAccount(), contact.getJid(), false);
        switchToConversation(conversation);
    }

    private void switchToConversation(Conversation conversation) {
        // Navigate to the chat activity or similar
        ChatActivity.launch(this, conversation);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        int position = info.position;

        // Determine which list was clicked based on fragment
        MyListFragment currentFragment = getCurrentVisibleFragment();
        if (currentFragment == mContactsFragment) {
            getMenuInflater().inflate(R.menu.contact_context, menu);
        } else if (currentFragment == mConferencesFragment) {
            getMenuInflater().inflate(R.menu.conference_context, menu);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        int position = info.position;

        MyListFragment currentFragment = getCurrentVisibleFragment();

        // Handle context item selection based on the fragment
        if (currentFragment == mContactsFragment) {
            Contact contact = getContactAtPosition(position);
            switch (item.getItemId()) {
                case R.id.context_start_conversation:
                    openConversationForContact(contact);
                    break;
                case R.id.context_contact_details:
                    showContactDetails(contact);
                    break;
                case R.id.context_delete_contact:
                    deleteContact(contact);
                    break;
            }
        } else if (currentFragment == mConferencesFragment) {
            Bookmark bookmark = getBookmarkAtPosition(position);
            switch (item.getItemId()) {
                case R.id.context_join_conference:
                    openConversationForBookmark(bookmark);
                    break;
                case R.id.context_delete_conference:
                    deleteConference(bookmark);
                    break;
            }
        }

        return true;
    }

    private MyListFragment getCurrentVisibleFragment() {
        // Determine which fragment is currently visible
        if (mContactsFragment.isVisible()) {
            return mContactsFragment;
        } else {
            return mConferencesFragment;
        }
    }

    private Contact getContactAtPosition(int position) {
        // Retrieve contact based on position
        return xmppConnectionService.getContacts().get(position);
    }

    private Bookmark getBookmarkAtPosition(int position) {
        // Retrieve bookmark based on position
        return xmppConnectionService.getBookmarks().get(position);
    }

    private void openConversationForContact(Contact contact) {
        Conversation conversation = xmppConnectionService.findOrCreateConversation(contact.getAccount(), contact.getJid(), false);
        switchToConversation(conversation);
    }

    private void openConversationForBookmark(Bookmark bookmark) {
        Conversation conversation = xmppConnectionService.findOrCreateConversation(bookmark.getAccount(), bookmark.getJid(), true);
        conversation.setBookmark(bookmark);
        if (!conversation.getMucOptions().online()) {
            xmppConnectionService.joinMuc(conversation);
        }
        switchToConversation(conversation);
    }

    private void showContactDetails(Contact contact) {
        // Display contact details in a dialog or separate activity
    }

    private void deleteContact(Contact contact) {
        xmppConnectionService.deleteContact(contact);
    }

    private void deleteConference(Bookmark bookmark) {
        Account account = bookmark.getAccount();
        account.getBookmarks().remove(bookmark);
        xmppConnectionService.pushBookmarks(account);
    }

    private class MyTextWatcher implements android.text.TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // Filter contacts or conferences based on search text
            String filterText = s.toString().toLowerCase();
            if (getCurrentVisibleFragment() == mContactsFragment) {
                List<Contact> filteredContacts = xmppConnectionService.getContacts()
                        .stream()
                        .filter(contact -> contact.getName().toLowerCase().contains(filterText))
                        .toList();
                // Update contacts list view
            } else if (getCurrentVisibleFragment() == mConferencesFragment) {
                List<Bookmark> filteredBookmarks = xmppConnectionService.getBookmarks()
                        .stream()
                        .filter(bookmark -> bookmark.getName().toLowerCase().contains(filterText))
                        .toList();
                // Update conferences list view
            }
        }

        @Override
        public void afterTextChanged(android.text.Editable s) {}
    }

    private static class MyListFragment extends androidx.fragment.app.ListFragment {
        @Override
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
            super.onCreateContextMenu(menu, v, menuInfo);
            StartConversationActivity activity = (StartConversationActivity) getActivity();
            if (activity != null) {
                activity.onCreateContextMenu(menu, v, menuInfo);
            }
        }

        @Override
        public boolean onContextItemSelected(MenuItem item) {
            StartConversationActivity activity = (StartConversationActivity) getActivity();
            return activity != null && activity.onContextItemSelected(item);
        }

        // Implement other necessary methods and adapters to display contacts/conferences
    }

    private void bindService() {
        // Bind the service to interact with XMPP server
        xmppConnectionServiceBound = true;
        mActivatedAccounts = Arrays.asList("account1@example.com", "account2@example.com");
        mKnownHosts = Arrays.asList("user1@example.com", "user2@example.com");
        mKnownConferenceHosts = Arrays.asList("conference1@conference.example.com", "conference2@conference.example.com");
    }

    private void unbindService() {
        // Unbind the service when activity is destroyed
        xmppConnectionServiceBound = false;
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService();
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService();
    }
}