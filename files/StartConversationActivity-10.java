package com.conversations;

import android.app.ActionBar;
import android.content.Intent;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.BaseColumns;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class StartConversationActivity extends AppCompatActivity implements OnUpdateBlocklist, XmppConnectionService.OnBackendConnectedListener {
    private List<Contact> contacts = new ArrayList<>();
    private List<Bookmark> conferences = new ArrayList<>();

    private ArrayAdapter<Contact> mContactsAdapter;
    private ArrayAdapter<Bookmark> mConferenceAdapter;
    private Invite mPendingInvite;
    private String mInitialJid;

    private int contact_context_id = -1;
    private int conference_context_id = -1;

    private List<String> mActivatedAccounts;
    private List<String> mKnownHosts;
    private List<String> mKnownConferenceHosts;

    private MyListFragment contacts_fragment, conferences_fragment;
    private boolean mHideOfflineContacts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_conversation);

        ActionBar ab = getActionBar();
        if (ab != null) {
            ab.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

            ActionBar.Tab contactsTab = ab.newTab().setText("Contacts").setTabListener(new TabListener(contacts_fragment));
            ActionBar.Tab conferencesTab = ab.newTab().setText("Conferences").setTabListener(new TabListener(conferences_fragment));

            ab.addTab(contactsTab);
            ab.addTab(conferencesTab);
        }

        this.mActivatedAccounts = new ArrayList<>();
        this.contacts_fragment = (MyListFragment) getSupportFragmentManager().findFragmentById(R.id.contacts_list_fragment);
        this.conferences_fragment = (MyListFragment) getSupportFragmentManager().findFragmentById(R.id.conferences_list_fragment);

        this.mContactsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, contacts);
        this.mConferenceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, conferences);

        if (this.contacts_fragment != null) {
            this.contacts_fragment.setListAdapter(this.mContactsAdapter);
            this.contacts_fragment.setOnListItemClickListener((parent, view, position, id) -> {
                contact_context_id = position;
                openConversationForContact();
            });
            this.contacts_fragment.setContextMenu(R.menu.contact_context);
        }

        if (this.conferences_fragment != null) {
            this.conferences_fragment.setListAdapter(this.mConferenceAdapter);
            this.conferences_fragment.setOnListItemClickListener((parent, view, position, id) -> {
                conference_context_id = position;
                openConversationForBookmark();
            });
            this.conferences_fragment.setContextMenu(R.menu.conference_context);
        }

        Intent intent = getIntent();
        if (intent != null && intent.getAction() != null) {
            handleIntent(intent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        xmppConnectionServiceBound = true;
        refreshUi();
    }

    @Override
    protected void onPause() {
        super.onPause();
        xmppConnectionServiceBound = false;
    }

    // Vulnerability: Insecure Intent Handling
    // This method processes intents without proper validation or sanitization of the URI.
    private boolean handleIntent(Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return false;
        }
        switch (intent.getAction()) {
            case Intent.ACTION_SENDTO:
            case Intent.ACTION_VIEW:
                Log.d(Config.LOGTAG, "received uri=" + intent.getData());
                // Vulnerability: Directly using the URI from the intent without validation
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
                                                // Vulnerability: Directly using the URI from the NFC payload without validation
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
    Invite getInviteJellyBean(NdefRecord record) {
        return new Invite(record.toUri());
    }

    protected boolean handleJid(Invite invite) {
        List<Contact> contacts = xmppConnectionService.findContacts(invite.getJid());
        if (contacts.size() == 0) {
            showCreateContactDialog(invite.getJid().toString(),invite.getFingerprint());
            return false;
        } else if (contacts.size() == 1) {
            Contact contact = contacts.get(0);
            if (invite.getFingerprint() != null) {
                if (contact.addOtrFingerprint(invite.getFingerprint())) {
                    Log.d(Config.LOGTAG,"added new fingerprint");
                    xmppConnectionService.syncRosterToDisk(contact.getAccount());
                }
            }
            switchToConversation(contact);
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
        // Dialog to create a new contact with the given JID and Fingerprint
    }

    private void switchToConversation(Contact contact) {
        // Logic to switch to the conversation with the selected contact
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    public void OnUpdateBlocklist(final Status status) {
        refreshUi();
    }

    @Override
    protected void refreshUiReal() {
        if (mSearchEditText != null) {
            filter(mSearchEditText.getText().toString());
        }
    }

    private void onTabChanged() {
        invalidateOptionsMenu();
    }

    private class Invite extends XmppUri {

        public Invite(final Uri uri) {
            super(uri);
        }

        public Invite(final String uri) {
            super(uri);
        }

        boolean invite() {
            if (jid != null) {
                if (muc) {
                    showJoinConferenceDialog(jid);
                } else {
                    return handleJid(this);
                }
            }
            return false;
        }
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
                    if (contact.showInRoster() && contact.match(needle)
                            && (!this.mHideOfflineContacts
                            || contact.getPresences().getMostAvailableStatus() < Presences.OFFLINE)) {
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

    public static class MyListFragment extends ListFragment {

        private AdapterView.OnItemClickListener mOnItemClickListener;
        private int mResContextMenu;

        public void setContextMenu(final int res) {
            this.mResContextMenu = res;
        }

        @Override
        public void onListItemClick(final ListView l, final View v, final int position, final long id) {
            if (mOnItemClickListener != null) {
                mOnItemClickListener.onItemClick(l, v, position, id);
            }
        }

        public void setOnListItemClickListener(AdapterView.OnItemClickListener l) {
            this.mOnItemClickListener = l;
        }

        @Override
        public void onViewCreated(@Nullable View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            if (view != null && view instanceof ListView) {
                ((ListView) view).setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            }
        }

        @Override
        public void onListItemClick(ListView l, View v, int position, long id) {
            super.onListItemClick(l, v, position, id);
            if (mOnItemClickListener != null) {
                mOnItemClickListener.onItemClick(l, v, position, id);
            }
        }

        @Override
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
            super.onCreateContextMenu(menu, v, menuInfo);
            MenuInflater inflater = getActivity().getMenuInflater();
            inflater.inflate(mResContextMenu, menu);
        }

        @Override
        public boolean onContextItemSelected(MenuItem item) {
            int position = ((AdapterView.AdapterContextMenuInfo) item.getMenuInfo()).position;
            switch (item.getItemId()) {
                // Add your context menu items here
                default:
                    return super.onContextItemSelected(item);
            }
        }
    }

    private void showJoinConferenceDialog(String jid) {
        // Dialog to join a conference with the given JID
    }

    private void openConversationForContact() {
        // Logic to open conversation for the selected contact
    }

    private void openConversationForBookmark() {
        // Logic to open conversation for the selected bookmark/conference
    }
}