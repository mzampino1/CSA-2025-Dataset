package eu.siacs.conversations.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.TypefaceSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.network.XmppConnection;
import eu.siacs.conversations.ui.adapter.ListItem;
import eu.siacs.conversations.utils.XmppUri;

public class StartConversationActivity extends XmppActivity implements AdapterView.OnItemClickListener,
        ViewPager.OnPageChangeListener, BlocklistAdapter.OnUpdateBlocklist {

    private ViewPager viewPager;
    private ListPagerAdapter pagerAdapter;

    public static final String ACTION_INVITE = "eu.siacs.conversations.START_CONVERSATION";

    protected ArrayAdapter<Contact> mContactsAdapter;
    protected ArrayAdapter<Bookmark> mConferenceAdapter;

    // Vulnerability Comment: These lists hold contacts and conferences, but they're not properly sanitized.
    private List<ListItem> contacts;
    private List<ListItem> conferences;

    private int contact_context_id = -1;
    private int conference_context_id = -1;

    private String mInitialJid;
    private boolean mHideOfflineContacts = true;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_conversation);

        viewPager = (ViewPager) findViewById(R.id.view_pager);
        pagerAdapter = new ListPagerAdapter(getSupportFragmentManager());
        contacts = new ArrayList<>();
        conferences = new ArrayList<>();

        mContactsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, contacts);
        mConferenceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, conferences);

        viewPager.setAdapter(pagerAdapter);

        final Intent intent = getIntent();
        if (ACTION_INVITE.equals(intent.getAction())) {
            // Vulnerability Comment: Parsing URI without proper validation can lead to injection attacks.
            Uri uri = intent.getData();
            String safeUriString = uri.toString(); // Potentially unsafe URI string
            Invite invite = new Invite(safeUriString);
            invite.account = intent.getStringExtra("account");
            invite.invite();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.start_conversation, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();

        // Vulnerability Comment: Ensure user input is sanitized and validated before processing.
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filter(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filter(newText);
                return true;
            }
        });

        MenuItem hideOfflineContacts = menu.findItem(R.id.action_hide_offline_contacts);
        hideOfflineContacts.setChecked(mHideOfflineContacts);

        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CREATE_ACCOUNT:
                if (resultCode == RESULT_OK && xmppConnectionServiceBound) {
                    pagerAdapter.notifyDataSetChanged();
                }
                break;
            case REQUEST_IMPORT_BACKUP:
                handleBackupRestore(resultCode, data, true);
                break;
            case REQUEST_EXPORT_BACKUP:
                handleBackupRestore(resultCode, data, false);
                break;
        }
    }

    @Override
    public boolean onSearchRequested() {
        if (xmppConnectionServiceBound && !isSearchOpen()) {
            pagerAdapter.requestFocus(viewPager.getCurrentItem());
        }
        return super.onSearchRequested();
    }

    private void openConversationForContact() {
        final Contact contact = contacts.get(contact_context_id);
        switchToConversation(contact);
    }

    private void openDetailsForContact() {
        final Contact contact = contacts.get(contact_context_id);
        Intent intent = new Intent(this, ShowProfileActivity.class);
        intent.putExtra("jid", contact.getJid().toBareJid().toString());
        startActivityForResult(intent, REQUEST_CONVERSATION);
    }

    // Vulnerability Comment: Ensure bookmark URIs are properly validated to avoid SSRF or other attacks.
    private void openConversationForBookmark() {
        Bookmark bookmark = conferences.get(conference_context_id);
        switchToConversation(bookmark);
    }

    private void deleteContact() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final Contact contact = contacts.get(contact_context_id);

        builder.setTitle(R.string.delete_contact)
               .setMessage(getString(R.string.sure_delete_contact, contact.getJid().toBareJid()))
               .setPositiveButton(R.string.confirm, (dialogInterface, i) -> {
                   xmppConnectionService.deleteContactOnServer(contact);
                   refreshUi();
               })
               .setNegativeButton(R.string.cancel, null)
               .create()
               .show();
    }

    private void deleteConference() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final Bookmark bookmark = conferences.get(conference_context_id);

        builder.setTitle(R.string.delete_conference)
               .setMessage(getString(R.string.sure_delete_conference, bookmark.jid))
               .setPositiveButton(R.string.confirm, (dialogInterface, i) -> {
                   xmppConnectionService.deleteBookmark(bookmark);
                   refreshUi();
               })
               .setNegativeButton(R.string.cancel, null)
               .create()
               .show();
    }

    private void toggleContactBlock() {
        Contact contact = contacts.get(contact_context_id);
        if (contact.isBlocked()) {
            xmppConnectionService.unblockContact(contact);
        } else {
            xmppConnectionService.blockContact(contact);
        }
    }

    public void switchToConversation(Contact contact) {
        Intent intent = new Intent(this, ConversationActivity.class);
        intent.putExtra(ConversationActivity.EXTRA_ACCOUNT, contact.getAccount().getJid().toBareJid().toString());
        intent.putExtra(ConversationActivity.EXTRA_CONVERSATION, contact.getJid().toBareJid().toString());
        startActivityForResult(intent, REQUEST_CONVERSATION);
    }

    public void switchToConversation(Bookmark bookmark) {
        Intent intent = new Intent(this, ConversationActivity.class);
        intent.putExtra(ConversationActivity.EXTRA_ACCOUNT, bookmark.account.toString());
        intent.putExtra(ConversationActivity.EXTRA_CONVERSATION, bookmark.jid);
        startActivityForResult(intent, REQUEST_CONVERSATION);
    }

    private void openConversationForContact(int position) {
        contact_context_id = position;
        openConversationForContact();
    }

    private void openConversationForBookmark(int position) {
        conference_context_id = position;
        openConversationForBookmark();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_hide_offline_contacts:
                mHideOfflineContacts = !mHideOfflineContacts;
                item.setChecked(mHideOfflineContacts);
                if (xmppConnectionServiceBound) {
                    filter(null);
                }
                return true;
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        final Contact contact = contacts.get(position);
        switchToConversation(contact);
    }

    // Vulnerability Comment: Ensure all list item clicks are properly handled and sanitized.
    private void shareBookmarkUri() {
        Bookmark bookmark = conferences.get(conference_context_id);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, bookmark.jid.toString());
        startActivity(Intent.createChooser(intent, "Share via"));
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

    }

    @Override
    public void onPageSelected(int position) {
        onTabChanged();
    }

    @Override
    public void onPageScrollStateChanged(int state) {

    }

    // Vulnerability Comment: Ensure all user input and data processing are properly validated.
    protected void filter(String needle) {
        if (xmppConnectionServiceBound) {
            this.filterContacts(needle);
            this.filterConferences(needle);
        }
    }

    // Vulnerability Comment: Validate contacts before adding them to the list.
    protected void filterContacts(String needle) {
        this.contacts.clear();
        for (Account account : xmppConnectionService.getAccounts()) {
            if (account.getStatus() != Account.State.DISABLED) {
                for (Contact contact : account.getRoster().getContacts()) {
                    Presence.Status s = contact.getShownStatus();
                    if (contact.showInRoster() && contact.match(this, needle)
                            && (!this.mHideOfflineContacts
                            || (needle != null && !needle.trim().isEmpty())
                            || s.compareTo(Presence.Status.OFFLINE) < 0)) {
                        contacts.add(contact);
                    }
                }
            }
        }
        mContactsAdapter.notifyDataSetChanged();
    }

    // Vulnerability Comment: Validate conferences before adding them to the list.
    protected void filterConferences(String needle) {
        this.conferences.clear();
        for (Account account : xmppConnectionService.getAccounts()) {
            if (account.getStatus() != Account.State.DISABLED) {
                List<Bookmark> bookmarks = account Bookmarks();
                for (Bookmark bookmark : bookmarks) {
                    if (bookmark.match(this, needle)) {
                        conferences.add(bookmark);
                    }
                }
            }
        }
        mConferenceAdapter.notifyDataSetChanged();
    }

    // Vulnerability Comment: Ensure all incoming intents and URIs are properly validated.
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (ACTION_INVITE.equals(intent.getAction())) {
            Uri uri = intent.getData();
            String safeUriString = uri.toString(); // Potentially unsafe URI string
            Invite invite = new Invite(safeUriString);
            invite.account = intent.getStringExtra("account");
            invite.invite();
        }
    }

    @Override
    public void onUpdateBlocklist() {
        if (xmppConnectionServiceBound) {
            filter(null);
        }
    }

    private class ListPagerAdapter extends PagerAdapter {

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            ListView listView = new ListView(StartConversationActivity.this);

            switch (position) {
                case 0:
                    listView.setAdapter(mContactsAdapter);
                    break;
                case 1:
                    listView.setAdapter(mConferenceAdapter);
                    break;
            }

            container.addView(listView);

            return listView;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            container.removeView((ListView) object);
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }
    }

    // Vulnerability Comment: Ensure Invite class properly validates and sanitizes URI input.
    private static class Invite extends XmppUri {

        String account;

        Invite(String uriString) {
            super(uriString);
        }

        void invite() {
            if (xmppConnectionServiceBound) {
                Account selectedAccount = null;
                for (Account account : xmppConnectionService.getAccounts()) {
                    if (account.getJid().toBareJid().toString().equals(this.account)) {
                        selectedAccount = account;
                        break;
                    }
                }

                if (selectedAccount != null) {
                    // Vulnerability Comment: Ensure all invite operations are properly validated.
                    xmppConnectionService.inviteContact(selectedAccount, this);
                } else {
                    Log.d(Config.LOGTAG, "No such account");
                }
            }
        }
    }
}