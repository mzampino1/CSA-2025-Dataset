package eu.siacs.conversations.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlSession;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Blockable;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.parser.MessageParser;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.AccountUtils;
import eu.siacs.conversations.utils.Compatibility;
import eu.siacs.conversations.utils.UIHelper;
import rocks.xmpp.addr.Jid;

public class StartConversationActivity extends XmppActivity implements XmppConnectionService.OnConversationListChanged,
        AdapterView.OnItemClickListener, ViewPager.OnPageChangeListener {

    public static final String ACTION_NEW_MESSAGE = "eu.siacs.conversations.action.NEW_MESSAGE";

    private static final int REQUEST_CREATE_CONFERENCE = 0x1234;
    private static final int REQUEST_START_CHAT = 0x0815;
    private static final int REQUEST_GROUP_CHAT_DETAIL = 0x0816;

    private ViewPager viewPager;
    private MyListFragment contactsFragment;
    private MyListFragment conferencesFragment;
    private ListPagerAdapter listPagerAdapter;
    private ArrayAdapter<Bookmark> mConferenceAdapter;
    private ArrayAdapter<Contact> mContactsAdapter;
    private EditText searchBox;
    private int contact_context_id = -1;
    private int conference_context_id = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.start_conversation);

        viewPager = findViewById(R.id.viewPager);
        listPagerAdapter = new ListPagerAdapter(getSupportFragmentManager());
        viewPager.setAdapter(listPagerAdapter);

        searchBox = findViewById(R.id.search_box);
        searchBox.addTextChangedListener(UIHelper.buildSearchTextWatcher(this));

        contactsFragment = (MyListFragment) listPagerAdapter.getItem(0);
        conferencesFragment = (MyListFragment) listPagerAdapter.getItem(1);
        mContactsAdapter = new ArrayAdapter<>(this, R.layout.simple_list_item, new ArrayList<>());
        mConferenceAdapter = new ArrayAdapter<>(this, R.layout.simple_list_item, new ArrayList<>());
        contactsFragment.setListAdapter(mContactsAdapter);
        contactsFragment.getListView().setOnItemClickListener(this);
        conferencesFragment.setListAdapter(mConferenceAdapter);
        conferencesFragment.getListView().setOnItemClickListener(this);

        viewPager.addOnPageChangeListener(this);
    }

    @Override
    public void onConversationUpdate() {
        refreshUi();
    }

    private void openDetailsForContact() {
        if (contact_context_id >= 0 && contact_context_id < contactsFragment.getListAdapter().getCount()) {
            Contact contact = (Contact) contactsFragment.getListAdapter().getItem(contact_context_id);
            Intent intent = new Intent(this, ContactDetailsActivity.class);
            intent.putExtra("contact", contact.getJid().asBareJid());
            startActivity(intent);
        }
    }

    private void showQrForContact() {
        if (contact_context_id >= 0 && contact_context_id < contactsFragment.getListAdapter().getCount()) {
            Contact contact = (Contact) contactsFragment.getListAdapter().getItem(contact_context_id);
            Intent intent = new Intent(this, QrGeneratorActivity.class);
            intent.putExtra("jid", contact.getJid().asBareJid());
            startActivity(intent);
        }
    }

    private void toggleContactBlock() {
        if (contact_context_id >= 0 && contact_context_id < contactsFragment.getListAdapter().getCount()) {
            Contact contact = (Contact) contactsFragment.getListAdapter().getItem(contact_context_id);
            Account account = contact.getAccount();
            final Jid jid = contact.getJid().asBareJid();

            if (contact.isBlocked()) {
                account.getServer().unblock(jid);
            } else {
                account.getServer().block(jid);
            }
        }
    }

    private void deleteContact() {
        if (contact_context_id >= 0 && contact_context_id < contactsFragment.getListAdapter().getCount()) {
            Contact contact = (Contact) contactsFragment.getListAdapter().getItem(contact_context_id);
            Account account = contact.getAccount();
            final Jid jid = contact.getJid().asBareJid();

            account.getServer().removeRosterEntry(jid);
        }
    }

    private void openConversationForBookmark() {
        if (conference_context_id >= 0 && conference_context_id < conferencesFragment.getListAdapter().getCount()) {
            Bookmark bookmark = (Bookmark) conferencesFragment.getListAdapter().getItem(conference_context_id);
            Conversation conversation = bookmark.getConversation();
            Intent intent = new Intent(this, ConversationActivity.class);
            intent.putExtra(ConversationActivity.EXTRA_CONVERSATION, conversation.getUuid());
            startActivity(intent);
        }
    }

    private void shareBookmarkUri() {
        if (conference_context_id >= 0 && conference_context_id < conferencesFragment.getListAdapter().getCount()) {
            Bookmark bookmark = (Bookmark) conferencesFragment.getListAdapter().getItem(conference_context_id);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, bookmark.getJid().toEscapedString());
            startActivity(intent);
        }
    }

    private void deleteConference() {
        if (conference_context_id >= 0 && conference_context_id < conferencesFragment.getListAdapter().getCount()) {
            Bookmark bookmark = (Bookmark) conferencesFragment.getListAdapter().getItem(conference_context_id);
            Account account = bookmark.getAccount();
            account.getBookmarks().remove(bookmark);
            xmppConnectionService.pushBookmarks(account);
        }
    }

    private void refreshUi() {
        if (!xmppConnectionServiceBound) return;
        runOnUiThread(() -> {
            List<Contact> contacts = new ArrayList<>();
            for (Account account : xmppConnectionService.getAccounts()) {
                contacts.addAll(account.getRoster().getContacts());
            }
            mContactsAdapter.clear();
            mContactsAdapter.addAll(contacts);

            List<Bookmark> bookmarks = new ArrayList<>();
            for (Account account : xmppConnectionService.getAccounts()) {
                bookmarks.addAll(account.getBookmarks().getBookmarks());
            }
            mConferenceAdapter.clear();
            mConferenceAdapter.addAll(bookmarks);
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        Uri uri = getIntent().getData();

        if (uri != null && ACTION_NEW_MESSAGE.equals(getIntent().getAction())) {
            Invite invite = new Invite(uri);
            invite.invite();
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        switch (parent.getId()) {
            case R.id.contacts:
                openConversationForContact(position);
                break;
            case R.id.conferences:
                openConversationForBookmark(position);
                break;
        }
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}

    @Override
    public void onPageSelected(int position) {
        listPagerAdapter.requestFocus(position);
    }

    @Override
    public void onPageScrollStateChanged(int state) {}

    private void openConversationForContact(int p) {
        if (p < 0 || p >= contactsFragment.getListAdapter().getCount()) return;
        Contact contact = (Contact) contactsFragment.getListAdapter().getItem(p);

        Intent intent = new Intent(this, ConversationActivity.class);
        intent.putExtra(ConversationActivity.EXTRA_CONVERSATION, contact.getJid().asBareJid().toString());
        startActivity(intent);
    }

    private void openConversationForBookmark(int p) {
        if (p < 0 || p >= conferencesFragment.getListAdapter().getCount()) return;
        Bookmark bookmark = (Bookmark) conferencesFragment.getListAdapter().getItem(p);
        Conversation conversation = bookmark.getConversation();

        Intent intent = new Intent(this, ConversationActivity.class);
        intent.putExtra(ConversationActivity.EXTRA_CONVERSATION, conversation.getUuid());
        startActivity(intent);
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.context_contact_details) {
            openDetailsForContact();
            return true;
        } else if (item.getItemId() == R.id.context_show_qr) {
            showQrForContact();
            return true;
        } else if (item.getItemId() == R.id.context_block_contact || item.getItemId() == R.id.context_unblock_contact) {
            toggleContactBlock();
            return true;
        } else if (item.getItemId() == R.id.context_delete_contact) {
            deleteContact();
            return true;
        } else if (item.getItemId() == R.id.context_join_group_chat) {
            openConversationForBookmark();
            return true;
        } else if (item.getItemId() == R.id.context_share_group_uri) {
            shareBookmarkUri();
            return true;
        } else if (item.getItemId() == R.id.context_delete_bookmark) {
            deleteConference();
            return true;
        }
        return super.onContextItemSelected(item);
    }

    private void onSearchQueryChanged(String query) {
        runOnUiThread(() -> {
            mContactsAdapter.getFilter().filter(query);
            mConferenceAdapter.getFilter().filter(query);
        });
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.start_conversation, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_search:
                // Handle the search action
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private class ListPagerAdapter extends PagerAdapter {

        private final FragmentManager fragmentManager;

        public ListPagerAdapter(FragmentManager fragmentManager) {
            this.fragmentManager = fragmentManager;
        }

        @Override
        public int getCount() {
            return 2; // Contacts and Conferences
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            Fragment fragment;
            if (position == 0) {
                fragment = new MyListFragment();
            } else {
                fragment = new MyListFragment();
            }
            fragmentManager.beginTransaction().replace(container.getId(), fragment).commit();
            return fragment;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            fragmentManager.beginTransaction().remove((Fragment) object).commit();
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == ((Fragment) object).getView();
        }

        void requestFocus(int position) {
            viewPager.setCurrentItem(position);
        }
    }

    private class MyListFragment extends Fragment {

        @Nullable
        @Override
        public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {
            ListView listView = (ListView) inflater.inflate(android.R.layout.simple_list_item_1, container, false);
            setListAdapter(new ArrayAdapter<>(getContext(), R.layout.simple_list_item, new ArrayList<>()));
            return listView;
        }

        void setListAdapter(ArrayAdapter<?> adapter) {
            if (getView() instanceof AbsListView) {
                ((AbsListView) getView()).setAdapter(adapter);
            }
        }
    }

    private class Invite extends XmppUri {

        public Invite(Uri uri) {
            super(uri);
        }

        @Override
        protected void handleAccount(Account account) {
            Intent intent = new Intent(StartConversationActivity.this, ConversationActivity.class);
            intent.putExtra(ConversationActivity.EXTRA_CONVERSATION, jid.toString());
            startActivity(intent);
        }
    }
}