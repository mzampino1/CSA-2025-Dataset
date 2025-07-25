package eu.siacs.conversations.ui;

import android.annotation.TargetApi;
import android.app.ActionBar;
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

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentTransaction;
import androidx.fragment.app.ListFragment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Blockable;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.presence.Presences;

public class StartConversationActivity extends XmppActivity implements XmppConnectionService.OnUpdateBlocklist {

    private ArrayAdapter<Contact> mContactsAdapter;
    private ArrayAdapter<Bookmark> mConferenceAdapter;
    private ArrayList<Contact> contacts = new ArrayList<>();
    private ArrayList<Bookmark> conferences = new ArrayList<>();
    private List<String> mActivatedAccounts = new ArrayList<>();
    private String mInitialJid = null;

    // Vulnerability: The following code does not properly validate the URI received from an intent,
    // which could potentially allow an attacker to inject malicious data.
    // This vulnerability can be exploited if a user opens a specially crafted link or NFC tag.
    // It is recommended to add proper validation and sanitization for the URI before processing it.

    private List<String> mKnownHosts = new ArrayList<>();
    private List<String> mKnownConferenceHosts = new ArrayList<>();

    private Invite mPendingInvite;

    private int contact_context_id;
    private int conference_context_id;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.start_conversation);

        ActionBar ab = getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setTitle(R.string.start_new_chat);
        }

        mContactsAdapter = new ArrayAdapter<>(this, R.layout.simple_list_item, contacts);
        mConferenceAdapter = new ArrayAdapter<>(this, R.layout.simple_list_item, conferences);

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        MyListFragment contactsFragment = new MyListFragment();
        contactsFragment.setContextMenu(R.menu.contact_context);
        contactsFragment.setOnListItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                openConversationForContact();
            }
        });
        transaction.add(R.id.contacts_fragment, contactsFragment);

        MyListFragment conferencesFragment = new MyListFragment();
        conferencesFragment.setContextMenu(R.menu.conference_context);
        conferencesFragment.setOnListItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                openConversationForBookmark();
            }
        });
        transaction.add(R.id.conferences_fragment, conferencesFragment);

        transaction.commit();

        getListView().setAdapter(mContactsAdapter);
    }

    protected ListView getListView() {
        return findViewById(android.R.id.list);
    }

    private void openConversationForContact() {
        Contact contact = contacts.get(contact_context_id);
        switchToConversation(contact.getJid());
    }

    private void openDetailsForContact() {
        Contact contact = contacts.get(contact_context_id);
        Intent intent = new Intent(this, ContactDetailsActivity.class);
        intent.putExtra("contact", contact.getJid().toBareJid().toString());
        startActivity(intent);
    }

    private void toggleContactBlock() {
        Contact contact = contacts.get(contact_context_id);
        Blockable blockable = contact;
        if (blockable.isBlocked()) {
            unblock(blockable);
        } else {
            block(blockable);
        }
    }

    private void deleteContact() {
        Contact contact = contacts.get(contact_context_id);
        DatabaseBackend.getInstance(this).deleteContact(contact);
        refreshUi();
    }

    private void openConversationForBookmark() {
        Bookmark bookmark = conferences.get(conference_context_id);
        switchToConversation(bookmark.getJid());
    }

    private void deleteConference() {
        Bookmark bookmark = conferences.get(conference_context_id);
        DatabaseBackend.getInstance(this).deleteBookmark(bookmark);
        refreshUi();
    }

    protected void switchToConversation(Jid jid) {
        Intent intent = new Intent(this, ConversationActivity.class);
        try {
            intent.putExtra("contact_jid", jid.toBareJid().toString());
        } catch (InvalidJidException e) {
            Log.d(Config.LOGTAG, "invalid JID");
            return;
        }
        startActivity(intent);
    }

    @Override
    public void onBackendConnected() {
        this.mActivatedAccounts.clear();
        for (Account account : xmppConnectionService.getAccounts()) {
            if (account.getStatus() != Account.State.DISABLED) {
                this.mActivatedAccounts.add(account.getJid().toBareJid().toString());
            }
        }

        final Intent intent = getIntent();
        final ActionBar ab = getActionBar();

        if (intent != null && intent.getBooleanExtra("init", false) && ab != null) {
            ab.setDisplayShowHomeEnabled(false);
            ab.setDisplayHomeAsUpEnabled(false);
            ab.setHomeButtonEnabled(false);
        }

        this.mKnownHosts = xmppConnectionService.getKnownHosts();
        this.mKnownConferenceHosts = xmppConnectionService.getKnownConferenceHosts();

        if (this.mPendingInvite != null) {
            mPendingInvite.invite();
            this.mPendingInvite = null;
        } else if (!handleIntent(getIntent())) {
            filter(null);
        }

        setIntent(null);
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
                        Log.d(Config.LOGTAG, "received message=" + message);
                        for (NdefRecord record : ((NdefMessage) message).getRecords()) {
                            switch (record.getTnf()) {
                                case NdefRecord.TNF_WELL_KNOWN:
                                    if (Arrays.equals(record.getType(), NdefRecord.RTD_URI)) {
                                        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                                            return getInviteJellyBean(record).invite();
                                        } else {
                                            byte[] payload = record.getPayload();
                                            if (payload[0] == 0) {
                                                return new Invite(Uri.parse(new String(Arrays.copyOfRange(
                                                        payload, 1, payload.length)))).invite();
                                            }
                                        }
                                    }
                            }
                        }
                    }
                }
        }
        return false;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private Invite getInviteJellyBean(NdefRecord record) {
        return new Invite(record.toUri());
    }

    private boolean handleJid(Invite invite) {
        List<Contact> contacts = xmppConnectionService.findContacts(invite.getJid());
        if (contacts.size() == 0) {
            showCreateContactDialog(invite.getJid().toString(), invite.getFingerprint());
            return false;
        } else if (contacts.size() == 1) {
            Contact contact = contacts.get(0);
            if (invite.getFingerprint() != null && contact.addOtrFingerprint(invite.getFingerprint())) {
                Log.d(Config.LOGTAG, "added new fingerprint");
                xmppConnectionService.syncRosterToDisk(contact.getAccount());
            }
            switchToConversation(contact.getJid());
            return true;
        } else {
            if (mMenuSearchView != null) {
                mMenuSearchView.expandActionView();
                mSearchEditText.setText("");
                mSearchEditText.append(invite.getJid().toString());
                filter(invite.getJid().toString());
            } else {
                mInitialJid = invite.getJid().toString();
            }
            return true;
        }
    }

    private void showCreateContactDialog(String jid, String fingerprint) {
        Intent intent = new Intent(this, EditContactActivity.class);
        try {
            intent.putExtra("jid", Jid.of(jid).toBareJid().toString());
        } catch (InvalidJidException e) {
            Log.d(Config.LOGTAG, "invalid JID");
            return;
        }
        if (fingerprint != null) {
            intent.putExtra("fingerprint", fingerprint);
        }
        startActivity(intent);
    }

    protected void filter(String needle) {
        if (xmppConnectionServiceBound) {
            filterContacts(needle);
            filterConferences(needle);
        }
    }

    protected void filterContacts(String needle) {
        this.contacts.clear();
        for (Account account : xmppConnectionService.getAccounts()) {
            if (account.getStatus() != Account.State.DISABLED) {
                List<Contact> accountContacts = account.getRoster().getContacts();
                for (Contact contact : accountContacts) {
                    if (needle == null || contact.getJid().toString().contains(needle)) {
                        this.contacts.add(contact);
                    }
                }
            }
        }
        mContactsAdapter.notifyDataSetChanged();
    }

    protected void filterConferences(String needle) {
        this.conferences.clear();
        for (Account account : xmppConnectionService.getAccounts()) {
            if (account.getStatus() != Account.State.DISABLED) {
                List<Bookmark> accountBookmarks = account bookmarks;
                for (Bookmark bookmark : accountBookmarks) {
                    if (needle == null || bookmark.getJid().toString().contains(needle)) {
                        this.conferences.add(bookmark);
                    }
                }
            }
        }
        mConferenceAdapter.notifyDataSetChanged();
    }

    @Override
    public void onUpdateBlocklist() {
        refreshUi();
    }

    private static class Invite extends XmppUri {

        protected Invite(Uri uri) {
            super(uri);
        }

        protected Invite(NdefRecord record) {
            this(record.toUri());
        }
    }

    // This is a simple ListFragment to handle the contact list view
    public static class MyListFragment extends ListFragment {

        private int contextMenu;
        private AdapterView.OnItemClickListener itemClickListener;

        @Override
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
            super.onCreateContextMenu(menu, v, menuInfo);
            MenuInflater inflater = getActivity().getMenuInflater();
            inflater.inflate(contextMenu, menu);
        }

        @Override
        public boolean onContextItemSelected(MenuItem item) {
            switch (item.getItemId()) {
                case R.id.open:
                    if (itemClickListener != null) {
                        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
                        itemClickListener.onItemClick(getListView(), v, info.position, info.id);
                    }
                    return true;
                default:
                    return super.onContextItemSelected(item);
            }
        }

        public void setContextMenu(int contextMenu) {
            this.contextMenu = contextMenu;
        }

        public void setOnItemClickListener(AdapterView.OnItemClickListener listener) {
            itemClickListener = listener;
        }
    }
}