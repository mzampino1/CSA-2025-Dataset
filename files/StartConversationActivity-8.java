package eu.siacs.conversations.ui;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.Blockable;
import eu.siacs.conversations.utils.XmppUri;
import me.dm7.barcodescanner.zxing.IntentIntegrator;
import me.dm7.barcodescanner.zxing.IntentResult;

public class StartConversationActivity extends XmppActivity implements AdapterView.OnItemClickListener, XmppConnectionService.OnUpdateBlocklist {

    private ArrayAdapter<Contact> mContactsAdapter;
    private ArrayAdapter<Bookmark> mConferenceAdapter;

    private MyListFragment contactsFragment = new MyListFragment();
    private MyListFragment conferenceFragment = new MyListFragment();

    // List of active accounts
    protected CopyOnWriteArrayList<String> mActivatedAccounts = new CopyOnWriteArrayList<>();

    // Known hosts and conference hosts
    private ArrayList<String> mKnownHosts;
    private ArrayList<String> mKnownConferenceHosts;

    // Pending invite to handle after backend connection is established
    private Invite mPendingInvite;

    // Initial JID to search for when activity starts
    private String mInitialJid;

    // Context menu identifiers
    protected int contact_context_id = -1;
    protected int conference_context_id = -1;

    // List of contacts and conferences
    protected CopyOnWriteArrayList<Contact> contacts = new CopyOnWriteArrayList<>();
    protected CopyOnWriteArrayList<Bookmark> conferences = new CopyOnWriteArrayList<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_conversation);

        this.mContactsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, contacts);
        this.mConferenceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, conferences);

        // Setting up the fragments
        setupFragments();
    }

    private void setupFragments() {
        FragmentManager fm = getSupportFragmentManager();

        FragmentTransaction transaction = fm.beginTransaction();

        // Contacts fragment
        contactsFragment.setAdapter(mContactsAdapter);
        contactsFragment.setContextMenu(R.menu.contact_context);
        contactsFragment.setOnListItemClickListener(this);

        // Conference fragment
        conferenceFragment.setAdapter(mConferenceAdapter);
        conferenceFragment.setContextMenu(R.menu.conference_context);
        conferenceFragment.setOnListItemClickListener(this);

        transaction.add(R.id.contacts_container, contactsFragment);
        transaction.add(R.id.conferences_container, conferenceFragment);

        transaction.commit();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (parent.getId() == R.id.contacts_list) {
            openConversationForContact(position);
        } else if (parent.getId() == R.id.conferences_list) {
            openConversationForBookmark(position);
        }
    }

    private void openConversationForContact(int position) {
        Contact contact = contacts.get(position);
        switchToConversation(contact.getJid().asBareJid().toString());
    }

    private void openConversationForBookmark(int position) {
        Bookmark bookmark = conferences.get(position);
        switchToConversation(bookmark.jid.toString());
    }

    public void switchToConversation(String jid) {
        Intent intent = new Intent(this, ConversationActivity.class);
        intent.putExtra("jid", jid);
        startActivity(intent);
    }

    @Override
    protected void onBackendConnected() {
        this.mActivatedAccounts.clear();
        for (Account account : xmppConnectionService.getAccounts()) {
            if (account.getStatus() != Account.State.DISABLED) {
                this.mActivatedAccounts.add(account.getJid().toBareJid().toString());
            }
        }

        this.mKnownHosts = xmppConnectionService.getKnownHosts();
        this.mKnownConferenceHosts = xmppConnectionService.getKnownConferenceHosts();

        if (this.mPendingInvite != null) {
            mPendingInvite.invite();
            this.mPendingInvite = null;
        } else if (!handleIntent(getIntent())) {
            if (mSearchEditText != null) {
                filter(mSearchEditText.getText().toString());
            } else {
                filter(null);
            }
        }

        setIntent(null);
    }

    @Override
    protected void onStart() {
        super.onStart();
        invalidateOptionsMenu();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SEARCH && !event.isLongPress()) {
            mOptionsMenu.findItem(R.id.action_search).expandActionView();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.start_conversation, menu);
        this.mOptionsMenu = menu;

        final android.view.MenuItem searchItem = menu.findItem(R.id.action_search);

        // Setting up the action view for search item if it exists and we are using ActionBar
        if (searchItem != null && getSupportActionBar() != null) {
            setupActionView(searchItem);
        }

        return true;
    }

    private void setupActionView(android.view.MenuItem searchItem) {
        final android.widget.SearchView searchView = (android.widget.SearchView) searchItem.getActionView();
        searchView.setQueryHint(getString(R.string.search_for_contact_or_conference));
        this.mSearchEditText = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
        this.mSearchEditText.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        // Adding text listener to filter contacts/conferences based on user input
        searchView.setOnQueryTextListener(new android.widget.SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filter(newText);
                return true;
            }
        });
    }

    private void filter(String needle) {
        if (xmppConnectionServiceBound) {
            this.filterContacts(needle);
            this.filterConferences(needle);
        }
    }

    private void filterContacts(String needle) {
        this.contacts.clear();
        for (Account account : xmppConnectionService.getAccounts()) {
            if (account.getStatus() != Account.State.DISABLED) {
                for (Contact contact : account.getRoster().getContacts()) {
                    if (contact.showInRoster() && contact.match(needle)) {
                        this.contacts.add(contact);
                    }
                }
            }
        }

        Collections.sort(this.contacts);
        mContactsAdapter.notifyDataSetChanged();
    }

    private void filterConferences(String needle) {
        this.conferences.clear();
        for (Account account : xmppConnectionService.getAccounts()) {
            if (account.getStatus() != Account.State.DISABLED) {
                for (Bookmark bookmark : account.getBookmarks()) {
                    if (bookmark.match(needle)) {
                        this.conferences.add(bookmark);
                    }
                }
            }
        }

        Collections.sort(this.conferences);
        mConferenceAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_create_contact:
                showCreateContactDialog(null, null);
                return true;
            case R.id.action_join_conference:
                showJoinConferenceDialog(null);
                return true;
            case R.id.action_scan_qr_code:
                new IntentIntegrator(this).initiateScan();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if ((requestCode & 0xFFFF) == IntentIntegrator.REQUEST_CODE) {
            IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
            if (scanResult != null && scanResult.getFormatName() != null) {
                String data = scanResult.getContents();
                Invite invite = new Invite(data);
                if (xmppConnectionServiceBound) {
                    invite.invite();
                } else {
                    this.mPendingInvite = invite;
                }
            }
        }

        super.onActivityResult(requestCode, resultCode, intent);
    }

    private boolean handleIntent(Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return false;
        }

        switch (intent.getAction()) {
            case Intent.ACTION_SENDTO:
            case Intent.ACTION_VIEW:
                Log.d(Config.LOGTAG, "received uri=" + intent.getData());
                return new Invite(intent.getData()).invite();
            case NfcAdapter.ACTION_NDEF_DISCOVERED:
                for (Parcelable message : getIntent().getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)) {
                    if (message instanceof NdefMessage) {
                        NdefRecord[] records = ((NdefMessage) message).getRecords();
                        for (NdefRecord record : records) {
                            String payload = new String(record.getPayload());
                            Invite invite = new Invite(payload);
                            if (xmppConnectionServiceBound) {
                                return invite.invite();
                            } else {
                                this.mPendingInvite = invite;
                                return true;
                            }
                        }
                    }
                }
        }

        return false;
    }

    private void showCreateContactDialog(String jid, String name) {
        Intent intent = new Intent(this, CreateContactActivity.class);
        if (jid != null) {
            intent.putExtra("jid", jid);
        }
        if (name != null) {
            intent.putExtra("name", name);
        }
        startActivity(intent);
    }

    private void showJoinConferenceDialog(String roomJid) {
        Intent intent = new Intent(this, JoinConferenceActivity.class);
        if (roomJid != null) {
            intent.putExtra("room_jid", roomJid);
        }
        startActivity(intent);
    }

    @Override
    public void onUpdateBlocklist() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                contactsFragment.notifyDataSetChanged();
                conferenceFragment.notifyDataSetChanged();
            }
        });
    }

    private class MyListFragment extends androidx.fragment.app.ListFragment {

        private ArrayAdapter<? extends Blockable> mAdapter;
        private int mContextMenu;

        public void setAdapter(ArrayAdapter<? extends Blockable> adapter) {
            this.mAdapter = adapter;
            super.setListAdapter(this.mAdapter);
        }

        @Override
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
            MenuInflater inflater = getActivity().getMenuInflater();
            inflater.inflate(mContextMenu, menu);

            // Handling context menu options based on fragment type
            if (this == contactsFragment && contact_context_id >= 0) {
                Contact contact = contacts.get(contact_context_id);
                if (contact.isBlocked()) {
                    menu.findItem(R.id.block_contact).setVisible(false);
                } else {
                    menu.findItem(R.id.unblock_contact).setVisible(false);
                }
            } else if (this == conferenceFragment && conference_context_id >= 0) {
                Bookmark bookmark = conferences.get(conference_context_id);
                if (bookmark.isBlocked()) {
                    menu.findItem(R.id.block_conference).setVisible(false);
                } else {
                    menu.findItem(R.id.unblock_conference).setVisible(false);
                }
            }

            super.onCreateContextMenu(menu, v, menuInfo);
        }

        @Override
        public boolean onContextItemSelected(MenuItem item) {
            if (!isAdded()) return false;

            // Handling context menu actions based on fragment type
            if (this == contactsFragment && contact_context_id >= 0) {
                Contact contact = contacts.get(contact_context_id);

                switch (item.getItemId()) {
                    case R.id.block_contact:
                        blockContact(contact);
                        return true;
                    case R.id.unblock_contact:
                        unblockContact(contact);
                        return true;
                    default:
                        break;
                }
            } else if (this == conferenceFragment && conference_context_id >= 0) {
                Bookmark bookmark = conferences.get(conference_context_id);

                switch (item.getItemId()) {
                    case R.id.block_conference:
                        blockConference(bookmark);
                        return true;
                    case R.id.unblock_conference:
                        unblockConference(bookmark);
                        return true;
                    default:
                        break;
                }
            }

            return super.onContextItemSelected(item);
        }

        private void blockContact(Contact contact) {
            xmppConnectionService.blockContact(contact.getJid().asBareJid());
        }

        private void unblockContact(Contact contact) {
            xmppConnectionService.unblockContact(contact.getJid().asBareJid());
        }

        private void blockConference(Bookmark bookmark) {
            xmppConnectionService.blockBookmark(bookmark.jid);
        }

        private void unblockConference(Bookmark bookmark) {
            xmppConnectionService.unblockBookmark(bookmark.jid);
        }
    }

    public class Invite extends XmppUri {

        public Invite(Uri uri) {
            super(uri.toString());
        }

        public Invite(String jid) {
            super(jid);
        }

        public boolean invite() {
            if (this.isValidJid()) {
                String jid = this.getJid().toString();
                switchToConversation(jid);
                return true;
            }
            return false;
        }
    }
}