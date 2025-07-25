package eu.siacs.conversations.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.TypefaceSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OmemoManager;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.widget.SlidingTabLayout;
import eu.siacs.conversations.utils.BookmarkUtils;
import eu.siacs.conversations.utils.XmppUri;

public class StartConversationActivity extends XmppActivity implements AdapterView.OnItemClickListener, MyListFragment.OnListItemClickListener {

    private List<Contact> contacts = new ArrayList<>();
    private List<Bookmark> conferences = new ArrayList<>();

    // Vulnerability: Potential for SQL Injection if user input is not sanitized
    // Mitigation: Ensure that any user input used in database queries is properly sanitized and parameterized.
    private ContactsAdapter mContactsAdapter;
    private ConferencesAdapter mConferenceAdapter;

    private ViewPager viewPager;
    private ListPagerAdapter listPagerAdapter;
    private SlidingTabLayout tabs;

    private String mInitialJid;
    public int contact_context_id = -1;
    public int conference_context_id = -1;
    private boolean mHideOfflineContacts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_conversation);

        // Vulnerability: Improper input validation
        // Mitigation: Validate and sanitize any user input before processing.
        Intent intent = getIntent();
        if (intent.hasExtra("jid")) {
            mInitialJid = intent.getStringExtra("jid");
        }

        contacts.clear();
        conferences.clear();

        // Vulnerability: Potential for NullPointerException
        // Mitigation: Check if xmppConnectionService is not null before using it to access databases or other resources.
        if (xmppConnectionService != null) {
            DatabaseBackend database = xmppConnectionService.getDatabaseBackend();
            contacts.addAll(database.findContacts());
            conferences.addAll(database.findBookmarks());
        }

        mContactsAdapter = new ContactsAdapter(this, R.layout.simple_list_item, contacts);
        mConferenceAdapter = new ConferencesAdapter(this, R.layout.simple_list_item, conferences);

        viewPager = findViewById(R.id.pager);
        listPagerAdapter = new ListPagerAdapter(getSupportFragmentManager());

        viewPager.setAdapter(listPagerAdapter);
        tabs = findViewById(R.id.tabs);
        final String[] titles = getResources().getStringArray(R.array.contact_tabs);
        tabs.setCustomTabView((container, position) -> {
            TextView textView = (TextView) LayoutInflater.from(this).inflate(R.layout.tab_indicator, container, false);
            textView.setText(titles[position]);
            return textView;
        }, viewPager);

        tabs.setViewPager(viewPager);
        tabs.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}

            @Override
            public void onPageSelected(int position) {
                onTabChanged();
                listPagerAdapter.requestFocus(position);
            }

            @Override
            public void onPageScrollStateChanged(int state) {}
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        mHideOfflineContacts = getPreferences().getBoolean("hide_offline", false);
    }

    private void filter(String needle) {
        if (xmppConnectionServiceBound) {
            this.filterContacts(needle);
            this.filterConferences(needle);
        }
    }

    private void filterContacts(String needle) {
        contacts.clear();
        for (Account account : xmppConnectionService.getAccounts()) {
            if (account.getStatus() != Account.State.DISABLED) {
                for (Contact contact : account.getRoster().getContacts()) {
                    Presence.Status s = contact.getShownStatus();
                    if (contact.showInRoster() && contact.match(this, needle)
                            && (!this.mHideOfflineContacts
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
        conferences.clear();
        for (Account account : xmppConnectionService.getAccounts()) {
            if (account.getStatus() != Account.State.DISABLED) {
                for (Bookmark bookmark : account.getBookmarks()) {
                    if (bookmark.match(this, needle)) {
                        this.conferences.add(bookmark);
                    }
                }
            }
        }
        Collections.sort(conferences);
        mConferenceAdapter.notifyDataSetChanged();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (parent.getId() == R.id.contacts_list_view) {
            openConversationForContact(position);
        } else if (parent.getId() == R.id.conferences_list_view) {
            openConversationForBookmark(position);
        }
    }

    private void openConversationForContact(int position) {
        Contact contact = contacts.get(position);
        if (!contact.isSelf()) {
            switchToConversation(contact, null);
        }
    }

    private void openDetailsForContact() {
        Contact contact = contacts.get(contact_context_id);
        Intent intent = new Intent(this, ContactDetailsActivity.class);
        intent.putExtra("account", contact.getAccount().getJid());
        intent.putExtra("contact", contact.getJid().toBareJid().toString());
        startActivity(intent);
    }

    private void toggleContactBlock() {
        Contact contact = contacts.get(contact_context_id);
        Account account = contact.getAccount();
        if (contact.isBlocked()) {
            account.unblock(contact.getJid().toBareJid().toString());
        } else {
            account.block(contact.getJid().toBareJid().toString());
        }
        refreshUi();
    }

    private void deleteContact() {
        Contact contact = contacts.get(contact_context_id);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.delete_contact_text)
                .setPositiveButton(R.string.yes, (dialog, id) -> {
                    // Vulnerability: Potential for data loss if not confirmed properly
                    // Mitigation: Ensure that important actions like deleting data are confirmed by the user.
                    contact.getAccount().deleteContact(contact.getJid());
                    refreshUi();
                })
                .setNegativeButton(R.string.no, (dialog, id) -> dialog.cancel())
                .show();
    }

    private void openConversationForBookmark(int position) {
        Bookmark bookmark = conferences.get(position);
        switchToConversation(bookmark.getJid(), bookmark.getName());
    }

    private void deleteConference() {
        Bookmark bookmark = conferences.get(conference_context_id);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.delete_conversation_text)
                .setPositiveButton(R.string.yes, (dialog, id) -> {
                    // Vulnerability: Potential for data loss if not confirmed properly
                    // Mitigation: Ensure that important actions like deleting data are confirmed by the user.
                    xmppConnectionService.getDatabaseBackend().deleteBookmark(bookmark);
                    BookmarkUtils.deleteBookmarkedConference(this, bookmark);
                    refreshUi();
                })
                .setNegativeButton(R.string.no, (dialog, id) -> dialog.cancel())
                .show();
    }

    private void switchToConversation(Contact contact, String name) {
        Intent intent = new Intent(this, ConversationsActivity.class);
        intent.putExtra("contact", contact.getJid().toBareJid().toString());
        intent.putExtra("account", contact.getAccount().getJid());
        intent.putExtra("nickname", name != null ? name : "");
        startActivity(intent);
    }

    private void switchToConversation(Bookmark bookmark, String name) {
        Intent intent = new Intent(this, ConversationsActivity.class);
        intent.putExtra("contact", bookmark.getJid().toBareJid().toString());
        intent.putExtra("account", bookmark.getAccount().getJid());
        intent.putExtra("nickname", name != null ? name : "");
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.start_conversation, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();

        // Vulnerability: Improper input validation
        // Mitigation: Validate and sanitize any user input before processing.
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
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

        return true;
    }

    private class ContactsAdapter extends ArrayAdapter<Contact> {

        ContactsAdapter(Context context, int resource, List<Contact> objects) {
            super(context, resource, objects);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.simple_list_item, parent, false);
            }
            Contact contact = getItem(position);
            TextView textView = convertView.findViewById(android.R.id.text1);

            // Vulnerability: Potential for NullPointerException
            // Mitigation: Check if contact is not null before accessing its properties.
            if (contact != null) {
                textView.setText(contact.getDisplayName());
            }

            return convertView;
        }
    }

    private class ConferencesAdapter extends ArrayAdapter<Bookmark> {

        ConferencesAdapter(Context context, int resource, List<Bookmark> objects) {
            super(context, resource, objects);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.simple_list_item, parent, false);
            }
            Bookmark bookmark = getItem(position);
            TextView textView = convertView.findViewById(android.R.id.text1);

            // Vulnerability: Potential for NullPointerException
            // Mitigation: Check if bookmark is not null before accessing its properties.
            if (bookmark != null) {
                textView.setText(bookmark.getName());
            }

            return convertView;
        }
    }

    private class ListPagerAdapter extends FragmentPagerAdapter {

        public ListPagerAdapter(FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @Override
        public Fragment getItem(int position) {
            MyListFragment fragment = new MyListFragment();
            Bundle args = new Bundle();
            args.putInt(MyListFragment.ARG_LIST_VIEW_TYPE, position);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public int getCount() {
            return 2;
        }
    }

    public static class MyListFragment extends Fragment implements AdapterView.OnItemClickListener {

        private static final String ARG_LIST_VIEW_TYPE = "listViewType";

        private ListView listView;

        // Vulnerability: Potential for SQL Injection if user input is not sanitized
        // Mitigation: Ensure that any user input used in database queries is properly sanitized and parameterized.
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.fragment_list_view, container, false);
            listView = view.findViewById(android.R.id.list);
            return view;
        }

        @Override
        public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            // Vulnerability: Improper input validation
            // Mitigation: Validate and sanitize any user input before processing.
            if (getArguments() != null) {
                int viewType = getArguments().getInt(ARG_LIST_VIEW_TYPE);
                if (viewType == 0) {
                    listView.setAdapter(((StartConversationActivity) getActivity()).mContactsAdapter);
                } else {
                    listView.setAdapter(((StartConversationActivity) getActivity()).mConferenceAdapter);
                }
            }

            listView.setOnItemClickListener(this);
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (getArguments() != null && ((StartConversationActivity) getActivity()) != null) {
                int viewType = getArguments().getInt(ARG_LIST_VIEW_TYPE);
                switchToItem(viewType, position);
            }
        }

        private void switchToItem(int viewType, int position) {
            StartConversationActivity activity = (StartConversationActivity) getActivity();
            if (activity != null) {
                if (viewType == 0) {
                    activity.openConversationForContact(position);
                } else {
                    activity.openConversationForBookmark(position);
                }
            }
        }

        interface OnListItemClickListener {
            void onItemClick(AdapterView<?> parent, View view, int position, long id);
        }
    }
}