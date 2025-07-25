package com.yourapp.xmpp;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.TypefaceSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StartConversationActivity extends XmppActivity implements ViewPager.OnPageChangeListener {

    private ViewPager viewPager;
    private ListPagerAdapter listPagerAdapter;
    private List<Contact> contacts = new ArrayList<>();
    private List<Bookmark> conferences = new ArrayList<>();

    private ArrayAdapter<Contact> mContactsAdapter;
    private ArrayAdapter<Bookmark> mConferenceAdapter;

    // Context menu ids
    public int contact_context_id = -1;
    public int conference_context_id = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_conversation);

        viewPager = findViewById(R.id.view_pager);
        listPagerAdapter = new ListPagerAdapter(getSupportFragmentManager());
        viewPager.setAdapter(listPagerAdapter);
        viewPager.addOnPageChangeListener(this);

        mContactsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, contacts);
        mConferenceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, conferences);
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        // Handle page scroll
    }

    @Override
    public void onPageSelected(int position) {
        onTabChanged();
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        if (state == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
            listPagerAdapter.requestFocus(viewPager.getCurrentItem());
        }
    }

    // Spinner setup for accounts
    private void setupAccountSpinner() {
        List<String> accountNames = new ArrayList<>();
        for (Account account : xmppConnectionService.getAccounts()) {
            accountNames.add(account.getJid().asBareJid().toString());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, accountNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Spinner setup code here
    }

    // Method to open a conversation for a contact
    private void openConversationForContact(int position) {
        Contact contact = contacts.get(position);
        ConversationActivity.launch(this, contact.getJid().asBareJid().toString());
    }

    private void openConversationForBookmark(int position) {
        Bookmark bookmark = conferences.get(position);
        ConferenceDetailsActivity.launch(this, bookmark.jid.toString());
    }

    // Method to filter contacts and conferences based on search input
    protected void filter(String needle) {
        if (xmppConnectionServiceBound) {
            this.filterContacts(needle);
            this.filterConferences(needle);
        }
    }

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
                        this.contacts.add(contact);
                    }
                }
            }
        }
        Collections.sort(this.contacts);
        mContactsAdapter.notifyDataSetChanged();
    }

    protected void filterConferences(String needle) {
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

    // Adapter for the ViewPager
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
        public int getCount() {
            return fragments.length;
        }

        @Override
        public boolean isViewFromObject(View view, Object fragment) {
            return ((Fragment) fragment).getView() == view;
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
        public void destroyItem(ViewGroup container, int position, Object object) {
            assert (0 <= position && position < fragments.length);
            FragmentTransaction trans = fragmentManager.beginTransaction();
            trans.remove(fragments[position]);
            trans.commit();
            fragments[position] = null;
        }

        private Fragment getItem(int position) {
            assert (0 <= position && position < fragments.length);
            if (fragments[position] == null) {
                final MyListFragment listFragment = new MyListFragment();
                if (position == 1) {
                    listFragment.setListAdapter(mConferenceAdapter);
                    listFragment.setContextMenu(R.menu.conference_context);
                    listFragment.setOnListItemClickListener((parent, view, pos, id) -> openConversationForBookmark(pos));
                } else {
                    listFragment.setListAdapter(mContactsAdapter);
                    listFragment.setContextMenu(R.menu.contact_context);
                    listFragment.setOnListItemClickListener((parent, view, pos, id) -> openConversationForContact(pos));
                }
                fragments[position] = listFragment;
            }
            return fragments[position];
        }
    }

    // Custom ListFragment for handling contact and conference lists
    public static class MyListFragment extends androidx.fragment.app.ListFragment {
        private AdapterView.OnItemClickListener mOnItemClickListener;
        private int mResContextMenu;

        public void setContextMenu(int res) {
            this.mResContextMenu = res;
        }

        @Override
        public void onListItemClick(ListView l, View v, int position, long id) {
            if (mOnItemClickListener != null) {
                mOnItemClickListener.onItemClick(l, v, position, id);
            }
        }

        public void setOnListItemClickListener(AdapterView.OnItemClickListener l) {
            this.mOnItemClickListener = l;
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            registerForContextMenu(getListView());
            getListView().setFastScrollEnabled(true);
        }

        @Override
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
            super.onCreateContextMenu(menu, v, menuInfo);
            final StartConversationActivity activity = (StartConversationActivity) getActivity();
            if (activity == null) return;
            activity.getMenuInflater().inflate(mResContextMenu, menu);
            final AdapterView.AdapterContextMenuInfo acmi = (AdapterView.AdapterContextMenuInfo) menuInfo;
            if (mResContextMenu == R.menu.conference_context) {
                activity.conference_context_id = acmi.position;
            } else if (mResContextMenu == R.menu.contact_context) {
                activity.contact_context_id = acmi.position;
                final Contact contact = activity.contacts.get(acmi.position);
                final MenuItem blockUnblockItem = menu.findItem(R.id.context_contact_block_unblock);
                final MenuItem showContactDetailsItem = menu.findItem(R.id.context_contact_details);
                if (contact.isSelf()) {
                    showContactDetailsItem.setVisible(false);
                }
                XmppConnection xmpp = contact.getAccount().getXmppConnection();
                if (xmpp != null && xmpp.getFeatures().blocking() && !contact.isSelf()) {
                    blockUnblockItem.setTitle(contact.isBlocked() ? R.string.unblock_contact : R.string.block_contact);
                } else {
                    blockUnblockItem.setVisible(false);
                }
            }
        }

        @Override
        public boolean onContextItemSelected(MenuItem item) {
            final StartConversationActivity activity = (StartConversationActivity) getActivity();
            if (activity == null) return false;
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
                case R.id.context_join_conference:
                    activity.openConversationForBookmark();
                    break;
                case R.id.context_edit_conference:
                    activity.editConference();
                    break;
            }
            return super.onContextItemSelected(item);
        }

        // Other context menu actions can be handled here
    }

    private void openDetailsForContact() {
        if (contact_context_id >= 0 && contact_context_id < contacts.size()) {
            Contact contact = contacts.get(contact_context_id);
            // Launch activity to show contact details
        }
    }

    private void toggleContactBlock() {
        if (contact_context_id >= 0 && contact_context_id < contacts.size()) {
            Contact contact = contacts.get(contact_context_id);
            // Toggle blocking the contact
        }
    }

    private void deleteContact() {
        if (contact_context_id >= 0 && contact_context_id < contacts.size()) {
            Contact contact = contacts.get(contact_context_id);
            // Delete the contact
        }
    }

    private void editConference() {
        if (conference_context_id >= 0 && conference_context_id < conferences.size()) {
            Bookmark bookmark = conferences.get(conference_context_id);
            // Launch activity to edit conference details
        }
    }
}