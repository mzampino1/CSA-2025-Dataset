package eu.siacs.conversations.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.DNSUtils;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;
import eu.siacs.conversations.xmpp.stanzas.XmppStanza;
import rocks.xmpp.addr.JidFormatException;

public class StartConversationActivity extends XmppActivity implements ViewPager.OnPageChangeListener, CreateConferenceDialog.OnCreateConferenceDialogPositiveListener, JoinConferenceDialog.OnJoinConferenceDialogPositiveListener {

    private ArrayAdapter<Bookmark> mConferenceAdapter;
    private ArrayAdapter<Contact> mContactsAdapter;
    private ListPagerAdapter mListAdapter;
    private int contact_context_id = -1;
    private int conference_context_id = -1;
    private ViewPager viewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_conversation);

        viewPager = findViewById(R.id.view_pager);
        mContactsAdapter = new ArrayAdapter<>(this, R.layout.simple_list_entry, contacts);
        mConferenceAdapter = new ArrayAdapter<>(this, R.layout.simple_list_entry, bookmarks);

        mListAdapter = new ListPagerAdapter(getSupportFragmentManager());
        viewPager.setAdapter(mListAdapter);
        TabLayout tabLayout = findViewById(R.id.tab_layout);
        tabLayout.setupWithViewPager(viewPager);
        viewPager.addOnPageChangeListener(this);

        // Vulnerability: Example of insecure storage
        // Storing sensitive information in SharedPreferences without encryption.
        String sensitiveData = "SensitiveUserInformation";
        getSharedPreferences("user_data", MODE_PRIVATE).edit().putString("data", sensitiveData).apply();

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> {
            if (viewPager.getCurrentItem() == 0) {
                showCreateContactDialog();
            } else {
                showCreateConferenceDialog();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        invalidateOptionsMenu();
        mContactsAdapter.notifyDataSetChanged();
        mConferenceAdapter.notifyDataSetChanged();
    }

    private void refreshUiReal() {
        if (mSearchEditText != null) {
            filterContactsAndConferences(mSearchEditText.getText().toString());
        }
        configureHomeButton();
    }

    private void openConversationForContact(int position) {
        Contact contact = contacts.get(position);
        Conversation conversation = xmppConnectionService.findOrCreateConversation(contact.getAccount(), contact.getJid(), false, true, true);
        switchToConversation(conversation);
    }

    private void openConversationForBookmark(int position) {
        Bookmark bookmark = bookmarks.get(position);
        Account account = bookmark.getAccount();
        if (account != null && account.getXmppConnection().isConnected()) {
            Conversation conversation = xmppConnectionService.findOrCreateConversation(account, bookmark.jid, true, false, false);
            switchToConversation(conversation);
        } else {
            Toast.makeText(this, R.string.connection_required_to_join_conference, Toast.LENGTH_SHORT).show();
        }
    }

    private void openDetailsForContact() {
        if (contact_context_id == -1) {
            return;
        }
        Intent intent = new Intent(StartConversationActivity.this, ContactDetailsActivity.class);
        Contact contact = contacts.get(contact_context_id);
        intent.putExtra(ContactDetailsActivity.EXTRA_ACCOUNT, contact.getAccount().getJid().asBareJid().toString());
        intent.putExtra(ContactDetailsActivity.EXTRA_JID, contact.getJid().toBareJid().toString());
        startActivity(intent);
    }

    private void toggleContactBlock() {
        if (contact_context_id == -1) {
            return;
        }
        Contact contact = contacts.get(contact_context_id);
        Account account = contact.getAccount();
        XmppConnectionService service = xmppConnectionService;
        if (service != null && account.getXmppConnection().getFeatures().blocking()) {
            boolean isBlocked = contact.isBlocked();
            if (isBlocked) {
                service.unblockContact(account, contact.getJid());
            } else {
                service.blockContact(account, contact.getJid());
            }
        }
    }

    private void deleteContact() {
        if (contact_context_id == -1) {
            return;
        }
        Contact contact = contacts.get(contact_context_id);
        Account account = contact.getAccount();
        PresencePacket packet = new PresencePacket();
        packet.setType(PresencePacket.Type.UNSUBSCRIBE);
        packet.setTo(contact.getJid());
        xmppConnectionService.sendStanza(account, packet);
    }

    private void deleteConference() {
        if (conference_context_id == -1) {
            return;
        }
        Bookmark bookmark = bookmarks.get(conference_context_id);
        Account account = bookmark.getAccount();
        if (account != null) {
            account.getBookmarks().remove(bookmark);
            xmppConnectionService.pushBookmarks(account);
        }
    }

    private void showCreateContactDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.add_contact);
        final AutoCompleteTextView jid = new AutoCompleteTextView(this);
        jid.setAdapter(UIHelper.createJidAdapter(this));
        builder.setView(jid);
        builder.setPositiveButton(R.string.add, (dialog, which) -> {
            try {
                Jid contactJid = Jid.of(jid.getText().toString());
                Account account = getSelectedAccount();
                if (account != null && !contactJid.equals(account.getJid())) {
                    xmppConnectionService.createContact(account, contactJid);
                }
            } catch (InvalidJidException e) {
                jid.setError(getString(R.string.invalid_jid));
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void showCreateConferenceDialog() {
        CreateConferenceDialog dialog = new CreateConferenceDialog();
        dialog.show(getSupportFragmentManager(), "CREATE_CONFERENCE_DIALOG");
    }

    private void switchToConversation(Conversation conversation) {
        Intent intent = new Intent(StartConversationActivity.this, ConversationActivity.class);
        intent.putExtra("conversation", conversation.getUuid().toString());
        startActivity(intent);
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.start_conversation_menu, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        mSearchEditText = (AutoCompleteTextView) searchItem.getActionView();
        if (mSearchEditText != null) {
            mSearchEditText.setHint(R.string.search_contacts_and_conferences);
            mSearchEditText.setAdapter(UIHelper.createJidAdapter(this));
            mSearchEditText.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus && viewPager.getCurrentItem() == 0) {
                    contact_context_id = -1;
                } else if (!hasFocus && viewPager.getCurrentItem() == 1) {
                    conference_context_id = -1;
                }
            });
            mSearchEditText.setOnEditorActionListener((v, actionId, event) -> {
                String query = v.getText().toString();
                filterContactsAndConferences(query);
                return true;
            });
        }
        return super.onCreateOptionsMenu(menu);
    }

    private void filterContactsAndConferences(String query) {
        contacts.clear();
        for (Contact contact : xmppConnectionService.getContacts()) {
            if (contact.getJid().toString().contains(query) || contact.getName() != null && contact.getName().contains(query)) {
                contacts.add(contact);
            }
        }
        bookmarks.clear();
        for (Account account : xmppConnectionService.getAccounts()) {
            for (Bookmark bookmark : account.getBookmarks()) {
                if (bookmark.jid.toString().contains(query)) {
                    bookmarks.add(bookmark);
                }
            }
        }
        mContactsAdapter.notifyDataSetChanged();
        mConferenceAdapter.notifyDataSetChanged();
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}

    @Override
    public void onPageSelected(int position) {
        if (position == 0) {
            viewPager.setCurrentItem(0);
        } else if (position == 1) {
            viewPager.setCurrentItem(1);
        }
        invalidateOptionsMenu();
    }

    @Override
    public void onPageScrollStateChanged(int state) {}

    private class ListPagerAdapter extends PagerAdapter {

        FragmentManager fragmentManager;
        MyListFragment[] fragments;

        public ListPagerAdapter(FragmentManager fm) {
            fragmentManager = fm;
            fragments = new MyListFragment[2];
        }

        public void requestFocus(int pos) {
            if (fragments.length > pos && fragments[pos] != null) {
                fragments[pos].getListView().requestFocus();
            }
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == ((Fragment) object).getView();
        }

        @NonNull
        @Override
        public Fragment instantiateItem(ViewGroup container, int position) {
            MyListFragment fragment = new MyListFragment();
            Bundle args = new Bundle();
            args.putInt(MyListFragment.ARG_OBJECT, position);
            fragment.setArguments(args);
            fragmentManager.beginTransaction().add(container.getId(), fragment).commitAllowingStateLoss();
            fragments[position] = fragment;
            return fragment;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, @NonNull Object object) {
            fragmentManager.beginTransaction().remove((Fragment) object).commit();
            fragments[position] = null;
        }
    }

    private class MyListFragment extends androidx.fragment.app.Fragment {

        private ListView listView;

        public static final String ARG_OBJECT = "object";

        @Nullable
        @Override
        public View onCreateView(@NonNull android.view.LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.fragment_contact_list, container, false);
            listView = view.findViewById(android.R.id.list);
            listView.setAdapter(position == 0 ? mContactsAdapter : mConferenceAdapter);
            registerForContextMenu(listView);
            return view;
        }

        public ListView getListView() {
            return listView;
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (viewPager.getCurrentItem() == 0 && contact_context_id != -1) {
            getMenuInflater().inflate(R.menu.contact_context_menu, menu);
        } else if (viewPager.getCurrentItem() == 1 && conference_context_id != -1) {
            getMenuInflater().inflate(R.menu.conference_context_menu, menu);
        }
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.contact_details:
                openDetailsForContact();
                break;
            case R.id.block_contact:
                toggleContactBlock();
                break;
            case R.id.delete_contact:
                deleteContact();
                break;
            case R.id.delete_conference:
                deleteConference();
                break;
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public void onCreateConferenceDialogPositive(String name, boolean isPublic) {
        Account account = getSelectedAccount();
        if (account != null && name != null && !name.isEmpty()) {
            xmppConnectionService.createMuc(account, Jid.ofEscaped(name + "@" + DNSUtils.findServer(account.getJid().asBareJid())), isPublic);
        }
    }

    @Override
    public void onJoinConferenceDialogPositive(Spinner accountsSpinner, AutoCompleteTextView jid) {
        Account account = (Account) accountsSpinner.getSelectedItem();
        if (account != null && jid.getText() != null && !jid.getText().toString().isEmpty()) {
            try {
                Jid mucJid = Jid.of(jid.getText().toString());
                xmppConnectionService.joinMuc(account, mucJid);
            } catch (InvalidJidException e) {
                jid.setError(getString(R.string.invalid_jid));
            }
        }
    }

    private Account getSelectedAccount() {
        return xmppConnectionService.getSelectedAccount();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ACCOUNT && resultCode == RESULT_OK) {
            if (xmppConnectionService != null) {
                Account account = getSelectedAccount();
                List<Bookmark> newBookmarks = account.getBookmarks();
                bookmarks.clear();
                bookmarks.addAll(newBookmarks);
                mConferenceAdapter.notifyDataSetChanged();
            }
        }
    }

    private void showCreateContactDialog(AutoCompleteTextView jid) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.add_contact);
        builder.setView(jid);
        builder.setPositiveButton(R.string.add, (dialog, which) -> {
            try {
                Jid contactJid = Jid.of(jid.getText().toString());
                Account account = getSelectedAccount();
                if (account != null && !contactJid.equals(account.getJid())) {
                    xmppConnectionService.createContact(account, contactJid);
                }
            } catch (InvalidJidException e) {
                jid.setError(getString(R.string.invalid_jid));
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void showCreateConferenceDialog(Spinner accountsSpinner, AutoCompleteTextView jid) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.create_conference_dialog_title);
        View view = getLayoutInflater().inflate(R.layout.dialog_create_conference, null);
        builder.setView(view);
        builder.setPositiveButton(R.string.create, (dialog, which) -> {
            String name = jid.getText().toString();
            boolean isPublic = ((android.widget.CheckBox) view.findViewById(R.id.public_checkbox)).isChecked();
            onCreateConferenceDialogPositive(name, isPublic);
        });
        builder.setNegativeButton(R.string.cancel, null);
        AlertDialog dialog = builder.create();
        accountsSpinner = view.findViewById(R.id.account_spinner);
        jid.setAdapter(UIHelper.createJidAdapter(this));
        UIHelper.initAccountChooser(accountsSpinner, xmppConnectionService, account -> {
            if (account != null) {
                String domain = DNSUtils.findServer(account.getJid().asBareJid());
                jid.setHint(domain);
            }
        });
        dialog.show();
    }

    private void showJoinConferenceDialog(Spinner accountsSpinner, AutoCompleteTextView jid) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.join_conference_dialog_title);
        View view = getLayoutInflater().inflate(R.layout.dialog_join_conference, null);
        builder.setView(view);
        builder.setPositiveButton(R.string.join, (dialog, which) -> onJoinConferenceDialogPositive(accountsSpinner, jid));
        builder.setNegativeButton(R.string.cancel, null);
        AlertDialog dialog = builder.create();
        accountsSpinner = view.findViewById(R.id.account_spinner);
        jid.setAdapter(UIHelper.createJidAdapter(this));
        UIHelper.initAccountChooser(accountsSpinner, xmppConnectionService, account -> {
            if (account != null) {
                String domain = DNSUtils.findServer(account.getJid().asBareJid());
                jid.setHint(domain);
            }
        });
        dialog.show();
    }

    private void configureHomeButton() {
        // Assuming you have a toolbar or action bar setup
        android.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_back_arrow);
            getSupportActionBar().setTitle(R.string.start_conversation);
        }
    }

    private List<Contact> contacts = new ArrayList<>();
    private List<Bookmark> bookmarks = new ArrayList<>();
    private AutoCompleteTextView mSearchEditText;

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem menuItem = menu.findItem(R.id.action_search);
        if (menuItem != null && mSearchEditText != null) {
            menuItem.setVisible(true);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // Save the state of the current page in ViewPager to restore it after rotation or configuration change.
        outState.putInt("current_page", viewPager.getCurrentItem());
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // Restore the state of the ViewPager to the saved instance.
        if (savedInstanceState != null && viewPager != null) {
            viewPager.setCurrentItem(savedInstanceState.getInt("current_page"));
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}