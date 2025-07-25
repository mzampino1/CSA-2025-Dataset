package eu.siacs.conversations.ui;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.TypefaceSpan;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OmemoSetting;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.entities.ServiceDiscoveryResult;
import eu.siacs.conversations.http.HttpRequest;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.adapter.ContactAdapter;
import eu.siacs.conversations.ui.adapter.ConferenceAdapter;
import eu.siacs.conversations.utils.AccountUtils;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.forms.Data;
import eu.siacs.conversations.xmpp.jingle.OnJingleCompleteCallback;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;

public class StartConversationActivity extends XmppActivity implements ViewPager.OnPageChangeListener {

    private Spinner mAccountSpinner;
    private ViewPager mViewPager;
    private ListPagerAdapter mPagerAdapter;
    private ContactAdapter mContactsAdapter;
    private ConferenceAdapter mConferenceAdapter;
    private ArrayList<Contact> contacts = new ArrayList<>();
    private ArrayList<Bookmark> conferences = new ArrayList<>();

    private boolean mHideOfflineContacts;

    // Context menu ids
    public int contact_context_id = -1;
    public int conference_context_id = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_conversation);

        contacts.clear();
        conferences.clear();

        mAccountSpinner = findViewById(R.id.choose_account_spinner);
        mViewPager = findViewById(R.id.view_pager);
        
        // Initialize the contact and conference adapters
        mContactsAdapter = new ContactAdapter(this, R.layout.contact_listitem, contacts);
        mConferenceAdapter = new ConferenceAdapter(this, R.layout.conference_listitem, conferences);

        // Set up the page adapter for the view pager
        mPagerAdapter = new ListPagerAdapter(getSupportFragmentManager());
        mViewPager.setAdapter(mPagerAdapter);
        mViewPager.addOnPageChangeListener(this);

        // Load data from intent if available
        loadDataFromIntent();
    }

    private void loadDataFromIntent() {
        final String action = getIntent().getAction();
        if (action != null) {
            switch (action) {
                case "eu.siacs.conversations.CREATE_CONVERSATION":
                    final String jidString = getIntent().getStringExtra("jid");
                    if (jidString != null) {
                        try {
                            Jid jid = Jid.of(jidString);
                            final Conversation conversation = xmppConnectionService.findOrCreateConversationByJid(
                                    AccountUtils.getFirstAvailableAccount(xmppConnectionService), jid, false);
                            switchToConversation(conversation);
                        } catch (final IllegalArgumentException e) {
                            Log.d(Config.LOGTAG, "invalid jid passed to StartConversationActivity: " + jidString);
                        }
                    }
                    break;
                case Intent.ACTION_VIEW:
                    final String uri = getIntent().getDataString();
                    if (uri != null) {
                        new Invite(uri).invite();
                    }
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        refreshUi();
    }

    @Override
    public void onAccountUpdate() {
        refreshUi();
    }

    private void populateAccountSpinner() {
        final List<Account> accounts = xmppConnectionService.getAccounts();
        if (accounts.size() == 0) {
            return;
        }
        Account[] accountArray = new Account[accounts.size()];
        accountArray = accounts.toArray(accountArray);
        ArrayAdapter<Account> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, accountArray);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mAccountSpinner.setAdapter(adapter);

        if (getIntent().hasExtra("choose_account")) {
            Jid jid = Jid.of(getIntent().getStringExtra("choose_account"));
            for (int i = 0; i < accountArray.length; ++i) {
                if (accountArray[i].getJid().equals(jid)) {
                    mAccountSpinner.setSelection(i);
                }
            }
        } else {
            int selectionIndex = getIndexForCurrentAccount(accountArray);
            if (selectionIndex != -1) {
                mAccountSpinner.setSelection(selectionIndex);
            }
        }

        mAccountSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                refreshUi();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private int getIndexForCurrentAccount(Account[] accountArray) {
        for (int i = 0; i < accountArray.length; ++i) {
            if (accountArray[i] == xmppConnectionService.getCurrentAccount()) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected void refreshUiReal() {
        if (xmppConnectionServiceBound && !contacts.isEmpty()) {
            mContactsAdapter.notifyDataSetChanged();
        }
        populateAccountSpinner();
    }

    private void filter(String needle) {
        if (xmppConnectionServiceBound) {
            filterContacts(needle);
            filterConferences(needle);
        }
    }

    private void filterContacts(String needle) {
        this.contacts.clear();
        for (Account account : xmppConnectionService.getAccounts()) {
            if (account.getStatus() != Account.State.DISABLED) {
                for (Contact contact : account.getRoster().getContacts()) {
                    Presence.Status s = contact.getShownStatus();
                    if (contact.showInRoster() && contact.match(this, needle)
                            && (!mHideOfflineContacts
                            || (needle != null && !needle.trim().isEmpty())
                            || s.compareTo(Presence.Status.OFFLINE) < 0)) {
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
                    if (bookmark.match(this, needle)) {
                        this.conferences.add(bookmark);
                    }
                }
            }
        }
        Collections.sort(this.conferences);
        mConferenceAdapter.notifyDataSetChanged();
    }

    private void openConversationForContact() {
        Contact contact = contacts.get(contact_context_id);
        Conversation conversation = xmppConnectionService.findOrCreateConversationByJid(
                contact.getAccount(), contact.getJid().asBareJid());
        switchToConversation(conversation);
    }

    private void openDetailsForContact() {
        Contact contact = contacts.get(contact_context_id);
        Intent intent = new Intent(this, ShowToastActivity.class);
        intent.putExtra("jid", contact.getJid().toString());
        startActivity(intent);
    }

    private void toggleContactBlock() {
        Contact contact = contacts.get(contact_context_id);
        if (contact.isBlocked()) {
            unblockContact(contact);
        } else {
            blockContact(contact);
        }
    }

    private void blockContact(Contact contact) {
        xmppConnectionService.blockContact(contact, new OnJingleCompleteCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> Toast.makeText(StartConversationActivity.this, R.string.contact_blocked, Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onError() {
                runOnUiThread(() -> Toast.makeText(StartConversationActivity.this, R.string.could_not_block_contact, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void unblockContact(Contact contact) {
        xmppConnectionService.unblockContact(contact, new OnJingleCompleteCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> Toast.makeText(StartConversationActivity.this, R.string.contact_unblocked, Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onError() {
                runOnUiThread(() -> Toast.makeText(StartConversationActivity.this, R.string.could_not_unblock_contact, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void deleteContact(Contact contact) {
        xmppConnectionService.deleteContact(contact);
    }

    private void openConversationForConference() {
        Bookmark bookmark = conferences.get(conference_context_id);
        Conversation conversation = xmppConnectionService.findOrCreateConversationByJid(
                getSelectedAccount(), Jid.of(bookmark.jid), true);
        switchToConversation(conversation);
    }

    private Account getSelectedAccount() {
        return (Account) mAccountSpinner.getSelectedItem();
    }

    private void deleteConference(Bookmark bookmark) {
        xmppConnectionService.deleteBookmark(bookmark);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();

        switch (item.getItemId()) {
            case R.id.action_chat:
                openConversationForContact();
                return true;
            case R.id.action_contact_details:
                openDetailsForContact();
                return true;
            case R.id.action_block:
                toggleContactBlock();
                return true;
            case R.id.action_delete:
                deleteContact(contacts.get(contact_context_id));
                return true;
            case R.id.action_join:
                openConversationForConference();
                return true;
            case R.id.action_remove_bookmark:
                deleteConference(conferences.get(conference_context_id));
                return true;
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenu.ContextMenuInfo menuInfo) {
        getMenuInflater().inflate(R.menu.contact_context, menu);

        if (v.getId() == R.id.conversation_list) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
            contact_context_id = info.position;

            Contact contact = contacts.get(contact_context_id);
            if (!contact.isBlocked()) {
                menu.findItem(R.id.action_block).setTitle(R.string.block_contact);
            } else {
                menu.findItem(R.id.action_block).setTitle(R.string.unblock_contact);
            }
        }

        if (v.getId() == R.id.conference_list) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
            conference_context_id = info.position;
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.bookmark_context, menu);
        }
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        // No-op
    }

    @Override
    public void onPageSelected(int position) {
        refreshUi();
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        // No-op
    }

    private class ListPagerAdapter extends PagerAdapter {

        private final FragmentManager fragmentManager;

        ListPagerAdapter(FragmentManager fm) {
            this.fragmentManager = fm;
        }

        @Override
        public int getCount() {
            return 2; // Contacts and Conferences pages
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            ListView listView;
            switch (position) {
                case 0:
                    listView = new ListView(StartConversationActivity.this);
                    listView.setAdapter(mContactsAdapter);
                    registerForContextMenu(listView);
                    break;
                case 1:
                    listView = new ListView(StartConversationActivity.this);
                    listView.setAdapter(mConferenceAdapter);
                    registerForContextMenu(listView);
                    break;
                default:
                    return null;
            }
            container.addView(listView, 0);
            return listView;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((ListView) object);
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }
    }

    private class Invite extends Data {

        Invite(String uri) {
            super(uri);
        }

        @Override
        protected void onCallback(int status, Data data) {
            if (status != HttpRequest.SC_OK) {
                return;
            }
            String jidString = data.getData().get("jid");
            try {
                Jid jid = Jid.of(jidString);
                Account account = AccountUtils.getFirstAvailableAccount(xmppConnectionService);
                Conversation conversation = xmppConnectionService.findOrCreateConversationByJid(
                        account, jid, false);
                switchToConversation(conversation);
            } catch (IllegalArgumentException e) {
                Log.d(Config.LOGTAG, "invalid jid in invite: " + jidString);
            }
        }
    }
}