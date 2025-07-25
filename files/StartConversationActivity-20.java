package com.zxcs.printer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.TypefaceSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.zxcs.printer.entities.Account;
import com.zxcs.printer.entities.Bookmark;
import com.zxcs.printer.entities.Contact;
import com.zxcs.printer.services.XmppConnection;
import com.zxcs.printer.ui.MyListFragment;
import com.zxcs.printer.utils.XmppUri;

import java.util.ArrayList;
import java.util.Collections;

public class StartConversationActivity extends Activity implements AdapterView.OnItemClickListener, ViewPager.OnPageChangeListener {
    private ArrayList<Contact> contacts = new ArrayList<>();
    private ArrayList<Bookmark> conferences = new ArrayList<>();
    private ContactsAdapter mContactsAdapter;
    private ConferenceAdapter mConferenceAdapter;
    private String mInitialJid;
    private boolean mHideOfflineContacts;
    private int contact_context_id;
    private int conference_context_id;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_conversation);

        ViewPager viewPager = findViewById(R.id.pager);
        ListPagerAdapter adapter = new ListPagerAdapter(getSupportFragmentManager());
        viewPager.setAdapter(adapter);
        viewPager.addOnPageChangeListener(this);

        // Initialize adapters for contacts and conferences
        mContactsAdapter = new ContactsAdapter(this, R.layout.simple_list_item, contacts);
        mConferenceAdapter = new ConferenceAdapter(this, R.layout.simple_list_item, conferences);

        Intent intent = getIntent();
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            Uri data = intent.getData();
            if (data != null) {
                Invite invite = new Invite(data); // Potential vulnerability here
                invite.invite();
            }
        }

        // ... rest of the code ...
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (parent.getId() == R.id.conference_list) {
            openConversationForBookmark(position);
        } else if (parent.getId() == R.id.contact_list) {
            openConversationForContact(position);
        }
    }

    @Override
    public void onPageScrolled(int i, float v, int i1) {}

    @Override
    public void onPageSelected(int i) {
        onTabChanged();
    }

    @Override
    public void onPageScrollStateChanged(int i) {}

    // ... rest of the code ...

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
        public boolean isViewFromObject(@NonNull View view, @NonNull Object fragment) {
            return ((Fragment) fragment).getView() == view;
        }

        @NonNull
        @Override
        public Fragment instantiateItem(@NonNull ViewGroup container, int position) {
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

            FragmentTransaction trans = fragmentManager.beginTransaction();
            trans.add(container.getId(), fragments[position], "fragment:" + position);
            trans.commit();

            return fragments[position];
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            FragmentTransaction trans = fragmentManager.beginTransaction();
            trans.remove(fragments[position]);
            trans.commit();
            fragments[position] = null;
        }
    }

    /**
     * This Invite class is designed to handle XMPP URIs and initiate conversations.
     * However, it lacks proper input validation for the URI, which could lead to issues
     * if a malformed URI is passed in. An attacker could exploit this by crafting
     * a malicious URI that might cause unexpected behavior or errors.
     */
    public static class Invite extends XmppUri {

        public Invite(final Uri uri) {
            super(uri);
            // Vulnerability: No validation of the input URI
            // This can lead to issues if a malformed URI is provided
        }

        public Invite(final String uri) {
            super(uri);
            // Vulnerability: No validation of the input string
            // This can lead to issues if a malformed URI is provided as a string
        }

        public Invite(Uri uri, boolean safeSource) {
            super(uri,safeSource);
            // Vulnerability: No validation of the input URI even when source is considered safe
            // This can still lead to issues if a malformed URI is provided
        }

        public String account;

        /**
         * Attempts to invite a contact or join a conference based on the URI.
         * Returns false if the JID in the URI is invalid, true otherwise.
         */
        boolean invite() {
            if (!isJidValid()) {
                Toast.makeText(StartConversationActivity.this,R.string.invalid_jid,Toast.LENGTH_SHORT).show();
                return false;
            }
            if (getJid() != null) {
                return handleJid(this);
            }
            return false;
        }
    }

    // ... rest of the code ...
}