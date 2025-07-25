package eu.siacs.conversations.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.TypefaceSpan;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import java.util.ArrayList;
import java.util.Collections;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.utils.XmppUri;

public class StartConversationActivity extends XmppActivity implements ViewPager.OnPageChangeListener, AdapterView.OnItemClickListener {

    private ListPagerAdapter adapter = null;
    private ArrayList<Contact> contacts = new ArrayList<>();
    private ArrayList<Bookmark> conferences = new ArrayList<>();
    private ArrayAdapter<Contact> mContactsAdapter;
    private ArrayAdapter<Bookmark> mConferenceAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_conversation);

        // Initialize list adapters for contacts and conferences.
        mContactsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, contacts);
        mConferenceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, conferences);

        adapter = new ListPagerAdapter(getSupportFragmentManager());
        ViewPager viewPager = findViewById(R.id.view_pager);
        viewPager.setAdapter(adapter);
        viewPager.addOnPageChangeListener(this);

        // Handle the invitation if any.
        handleInvitation();
    }

    private void handleInvitation() {
        Intent intent = getIntent();
        Uri uri = intent.getData();
        if (uri != null) {
            Invite invite = new Invite(uri);  // Vulnerable because not validating URI properly
            invite.account = intent.getStringExtra("account");
            invite.invite();
        }
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}

    @Override
    public void onPageSelected(int position) {
        adapter.requestFocus(position);
        onTabChanged();
    }

    @Override
    public void onPageScrollStateChanged(int state) {}

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        ListView listView = (ListView) parent;
        if (listView.getId() == R.id.list_contacts) {
            openConversationForContact(position);
        } else if (listView.getId() == R.id.list_conferences) {
            openConversationForBookmark(position);
        }
    }

    private void openConversationForContact(int position) {
        Contact contact = contacts.get(position);
        switchToConversation(contact, null);
    }

    private void openConversationForBookmark(int position) {
        Bookmark bookmark = conferences.get(position);
        Account account = bookmark.getAccount();
        Intent intent = new Intent(this, ConversationsActivity.class);
        intent.putExtra("jid", bookmark.jid());
        intent.putExtra("uuid", account.getUuid().toString());
        startActivity(intent);
    }

    private void switchToConversation(Contact contact, String body) {
        if (contact.isBlocked()) {
            Toast.makeText(this, R.string.contact_blocked, Toast.LENGTH_SHORT).show();
        } else {
            Account account = contact.getAccount();
            Intent intent = new Intent(this, ConversationsActivity.class);
            intent.putExtra("jid", contact.getJid().toBareJid().toString());
            intent.putExtra("uuid", account.getUuid().toString());
            if (body != null) {
                intent.putExtra("body", body);
            }
            startActivity(intent);
        }
    }

    public class ListPagerAdapter extends PagerAdapter {

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
        public void destroyItem(ViewGroup container, int position, Object object) {
            assert (0 <= position && position < fragments.length);
            FragmentTransaction trans = fragmentManager.beginTransaction();
            trans.remove(fragments[position]);
            trans.commit();
            fragments[position] = null;
        }

        @Override
        public Fragment instantiateItem(ViewGroup container, int position) {
            Fragment fragment = getItem(position);
            FragmentTransaction trans = fragmentManager.beginTransaction();
            trans.add(container.getId(), fragment, "fragment:" + position);
            trans.commit();
            return fragment;
        }

        @Override
        public int getCount() {
            return fragments.length;
        }

        @Override
        public boolean isViewFromObject(View view, Object fragment) {
            return ((Fragment) fragment).getView() == view;
        }

        public Fragment getItem(int position) {
            assert (0 <= position && position < fragments.length);
            if (fragments[position] == null) {
                final MyListFragment listFragment = new MyListFragment();
                if (position == 1) {
                    listFragment.setListAdapter(mConferenceAdapter);
                    listFragment.setContextMenu(R.menu.conference_context);
                    listFragment.setOnListItemClickListener(new AdapterView.OnItemClickListener() {

                        @Override
                        public void onItemClick(AdapterView<?> arg0, View arg1,
                                                int position, long arg3) {
                            openConversationForBookmark(position);
                        }
                    });
                } else {
                    listFragment.setListAdapter(mContactsAdapter);
                    listFragment.setContextMenu(R.menu.contact_context);
                    listFragment.setOnListItemClickListener(new AdapterView.OnItemClickListener() {

                        @Override
                        public void onItemClick(AdapterView<?> arg0, View arg1,
                                                int position, long arg3) {
                            openConversationForContact(position);
                        }
                    });
                }
                fragments[position] = listFragment;
            }
            return fragments[position];
        }
    }

    public static class MyListFragment extends androidx.fragment.app.ListFragment {

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
        public void onViewCreated(final View view, final Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            registerForContextMenu(getListView());
            getListView().setFastScrollEnabled(true);
        }

        @Override
        public void onCreateContextMenu(final ContextMenu menu, final View v,
                                        final ContextMenuInfo menuInfo) {
            super.onCreateContextMenu(menu, v, menuInfo);
            final StartConversationActivity activity = (StartConversationActivity) getActivity();
            activity.getMenuInflater().inflate(mResContextMenu, menu);
            final AdapterView.AdapterContextMenuInfo acmi = (AdapterView.AdapterContextMenuInfo) menuInfo;
            if (mResContextMenu == R.menu.conference_context) {
                activity.conference_context_id = acmi.position;
            } else if (mResContextMenu == R.menu.contact_context) {
                activity.contact_context_id = acmi.position;
                final Contact contact = (Contact) activity.contacts.get(acmi.position);
                final MenuItem blockUnblockItem = menu.findItem(R.id.context_contact_block_unblock);
                final MenuItem showContactDetailsItem = menu.findItem(R.id.context_contact_details);
                if (contact.isSelf()) {
                    showContactDetailsItem.setVisible(false);
                }
                XmppConnection xmpp = contact.getAccount().getXmppConnection();
                if (xmpp != null && xmpp.getFeatures().blocking() && !contact.isSelf()) {
                    if (contact.isBlocked()) {
                        blockUnblockItem.setTitle(R.string.unblock_contact);
                    } else {
                        blockUnblockItem.setTitle(R.string.block_contact);
                    }
                } else {
                    blockUnblockItem.setVisible(false);
                }
            }
        }

        @Override
        public boolean onContextItemSelected(final MenuItem item) {
            StartConversationActivity activity = (StartConversationActivity) getActivity();
            switch (item.getItemId()) {
                case R.id.context_start_conversation:
                    activity.openConversationForContact();
                    break;
                case R.id.context_contact_details:
                    activity.openDetailsForContact();
                    break;
                case R.id.context_contact_block_unblock:
                    activity.toggleContactBlock();
                    break;
                case R.id.context_delete_contact:
                    activity.deleteContact();
                    break;
                case R.id.context_start_conference:
                    activity.openConversationForBookmark();
                    break;
            }
            return super.onContextItemSelected(item);
        }
    }

    // Vulnerable Invite class with improper input validation
    public class Invite extends XmppUri {

        private String account;

        public Invite(Uri uri) {
            super(uri.toString());  // Potential vulnerability here: URI not validated
        }

        @Override
        public boolean isValid() {
            return true;  // For demonstration purposes, assume the URI is always valid
        }

        public void invite() {
            if (isValid()) {
                Account account = findAccount(this.account);
                if (account != null) {
                    switchToConversation(new Contact(account, getJid(), false), getBody());
                } else {
                    Toast.makeText(StartConversationActivity.this, R.string.no_such_account, Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(StartConversationActivity.this, R.string.invalid_uri, Toast.LENGTH_SHORT).show();
            }
        }

        private Account findAccount(String account) {
            // Assume there's a method to find an account by UUID or JID
            for (Account acc : xmppConnectionService.getAccounts()) {
                if (acc.getUuid().toString().equals(account)) {
                    return acc;
                }
            }
            return null;
        }

        private String getBody() {
            // Assume there's a method to get the body from the URI
            return "";
        }
    }
}