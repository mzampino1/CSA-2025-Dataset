package eu.siacs.conversations.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.fragment.app.FragmentTransaction;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Blockable;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.AccountUtils;
import eu.siacs.conversations.utils.BetterParser;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import rocks.xmpp.addr.XmppAddr;

import java.util.ArrayList;
import java.util.List;

public class StartConversationActivity extends XmppActivity implements AdapterView.OnItemClickListener, ViewPager.OnPageChangeListener {

    public static final String EXTRA_ACCOUNT = "account";

    private List<Contact> contacts = new ArrayList<>();
    private ArrayAdapter<Contact> mContactsAdapter;
    private MyListFragment contactListFragment;
    private ArrayAdapter<Bookmark> mConferenceAdapter;
    private MyListFragment conferenceListFragment;

    private ViewPager viewPager;
    private PagerAdapter pagerAdapter;
    private TextView tab_contacts;
    private TextView tab_conferences;
    private int conference_context_id = -1;
    private int contact_context_id = -1;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_conversation);

        viewPager = findViewById(R.id.start_view_pager);
        tab_contacts = findViewById(R.id.tab_contacts);
        tab_conferences = findViewById(R.id.tab_conferences);

        FragmentManager fragmentManager = getSupportFragmentManager();
        pagerAdapter = new ListPagerAdapter(fragmentManager);

        viewPager.setAdapter(pagerAdapter);
        viewPager.addOnPageChangeListener(this);

        mContactsAdapter = new ArrayAdapter<Contact>(this, R.layout.simple_list_item, contacts) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    convertView = inflater.inflate(R.layout.simple_list_item, parent, false);
                }
                TextView textView = convertView.findViewById(android.R.id.text1);
                Contact contact = contacts.get(position);
                textView.setText(contact.getJid().asBareJid().toString());
                return convertView;
            }
        };
        mConferenceAdapter = new ArrayAdapter<Bookmark>(this, R.layout.simple_list_item) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    convertView = inflater.inflate(R.layout.simple_list_item, parent, false);
                }
                TextView textView = convertView.findViewById(android.R.id.text1);
                Bookmark bookmark = mConferenceAdapter.getItem(position);
                textView.setText(bookmark.getJid().toString());
                return convertView;
            }
        };

        contactListFragment = (MyListFragment) pagerAdapter.getItem(0);
        conferenceListFragment = (MyListFragment) pagerAdapter.getItem(1);

        contactListFragment.setListAdapter(mContactsAdapter);
        conferenceListFragment.setListAdapter(mConferenceAdapter);

        tab_contacts.setOnClickListener(v -> viewPager.setCurrentItem(0));
        tab_conferences.setOnClickListener(v -> viewPager.setCurrentItem(1));

        Intent intent = getIntent();
        if (intent != null && intent.getAction() != null) {
            if (Intent.ACTION_VIEW.equals(intent.getAction())) {
                handleViewAction(intent.getData());
            }
        }
    }

    private void updateContactList() {
        contacts.clear();
        for (Account account : xmppConnectionService.getAccounts()) {
            for (Contact contact : account.getRoster().getContacts()) {
                if (!contact.isSelf() && !contact.isInroster()) {
                    continue;
                }
                boolean found = false;
                for (int i = 0; i < contacts.size(); ++i) {
                    if (contacts.get(i).getJid().equals(contact.getJid())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    contacts.add(contact);
                }
            }
        }
    }

    private void updateConferenceList() {
        mConferenceAdapter.clear();
        for (Account account : xmppConnectionService.getAccounts()) {
            for (Bookmark bookmark : account.getBookmarks().getBookmarks()) {
                boolean found = false;
                for (int i = 0; i < mConferenceAdapter.getCount(); ++i) {
                    if (mConferenceAdapter.getItem(i).getJid().equals(bookmark.getJid())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    mConferenceAdapter.add(bookmark);
                }
            }
        }
    }

    private void refreshUiReal() {
        updateContactList();
        updateConferenceList();
        mContactsAdapter.notifyDataSetChanged();
        mConferenceAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.start_conversation, menu);
        MenuItem menuItem = menu.findItem(R.id.action_create_conference);
        if (menuItem != null) {
            menuItem.setVisible(viewPager.getCurrentItem() == 1);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_create_conference:
                showCreateConferenceDialog();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void handleViewAction(Uri data) {
        if (!xmppConnectionServiceBound || data == null) {
            return;
        }
        try {
            Jid jid = Jid.of(data.toString());
            for (Account account : xmppConnectionService.getAccounts()) {
                Conversation conversation = xmppConnectionService.findOrCreateConversation(account, jid, false, false, true);
                switchToConversation(conversation);
                break;
            }
        } catch (InvalidJidException e) {
            ToastCompat.makeText(this, R.string.invalid_jid, ToastCompat.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        super.onAttachFragment(fragment);
        if (fragment instanceof MyListFragment) {
            MyListFragment listFragment = (MyListFragment) fragment;
            if (listFragment.getListAdapter() == mContactsAdapter) {
                listFragment.setListAdapter(mContactsAdapter);
            } else if (listFragment.getListAdapter() == mConferenceAdapter) {
                listFragment.setListAdapter(mConferenceAdapter);
            }
        }
    }

    @Override
    protected void onBackendConnected() {
        refreshUiReal();
    }

    private void switchToConversation(Conversation conversation) {
        Intent intent = new Intent(this, ConversationsActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(ConversationsActivity.CONVERSATION, conversation.getUuid().toString());
        startActivity(intent);
        finish();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (viewPager.getCurrentItem() == 0) {
            openConversationForContact(position);
        } else {
            openConversationForBookmark(position);
        }
    }

    private void openConversationForContact(int position) {
        Contact contact = contacts.get(position);
        Conversation conversation = xmppConnectionService.findOrCreateConversation(contact.getAccount(), contact.getJid().asBareJid());
        switchToConversation(conversation);
    }

    private void openConversationForBookmark(int position) {
        Bookmark bookmark = mConferenceAdapter.getItem(position);
        Conversation conversation = bookmark.getConversation();
        if (conversation == null || !conversation.isJoined()) {
            conversation = xmppConnectionService.findOrCreateConversation(bookmark.getAccount(), bookmark.getJid());
        }
        switchToConversation(conversation);
    }

    private void openDetailsForContact() {
        if (contact_context_id >= 0 && contact_context_id < contacts.size()) {
            Contact contact = contacts.get(contact_context_id);
            Intent intent = new Intent(this, ContactDetailsActivity.class);
            intent.putExtra(ContactDetailsActivity.CONTACT, contact.getJid().asBareJid().toString());
            startActivity(intent);
        }
    }

    private void showQrForContact() {
        if (contact_context_id >= 0 && contact_context_id < contacts.size()) {
            Contact contact = contacts.get(contact_context_id);
            Intent intent = new Intent(this, QrCodeActivity.class);
            intent.putExtra("jid", contact.getJid().toString());
            startActivity(intent);
        }
    }

    private void showCreateConferenceDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.create_conference);

        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_create_conference, null);
        builder.setView(dialogView);

        EditText editText = dialogView.findViewById(R.id.conference_name);
        Spinner spinner = dialogView.findViewById(R.id.account_spinner);
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, xmppConnectionService.getAccounts()));

        builder.setPositiveButton(R.string.create, (dialog, which) -> {
            String name = editText.getText().toString();
            Account account = (Account) spinner.getSelectedItem();

            if (!name.isEmpty() && account != null) {
                createConference(account, name);
            }
        });

        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss());

        builder.show();
    }

    private void createConference(Account account, String name) {
        IqPacket iq = new IqPacket(IqPacket.TYPE.SET);
        Jid jid;
        try {
            jid = Jid.ofLocalAndDomain(null, account.getServer());
        } catch (InvalidJidException e) {
            ToastCompat.makeText(this, R.string.invalid_jid, ToastCompat.LENGTH_SHORT).show();
            return;
        }
        iq.setTo(jid);
        // ... additional code to create the conference
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        // Not used
    }

    @Override
    public void onPageSelected(int position) {
        if (position == 0) {
            tab_contacts.setBackgroundColor(getResources().getColor(R.color.blue));
            tab_conferences.setBackgroundColor(getResources().getColor(android.R.color.transparent));
        } else {
            tab_contacts.setBackgroundColor(getResources().getColor(android.R.color.transparent));
            tab_conferences.setBackgroundColor(getResources().getColor(R.color.blue));
        }
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        // Not used
    }

    private class ListPagerAdapter extends PagerAdapter {

        private final FragmentManager fragmentManager;
        private MyListFragment contactListFragment;
        private MyListFragment conferenceListFragment;

        public ListPagerAdapter(FragmentManager fragmentManager) {
            this.fragmentManager = fragmentManager;
        }

        @Override
        public int getCount() {
            return 2; // Number of tabs (Contacts and Conferences)
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            FragmentTransaction transaction = fragmentManager.beginTransaction();
            MyListFragment fragment;
            if (position == 0) {
                if (contactListFragment == null) {
                    contactListFragment = new MyListFragment();
                }
                fragment = contactListFragment;
            } else {
                if (conferenceListFragment == null) {
                    conferenceListFragment = new MyListFragment();
                }
                fragment = conferenceListFragment;
            }
            transaction.add(container.getId(), fragment);
            transaction.commit();
            return fragment;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            FragmentTransaction transaction = fragmentManager.beginTransaction();
            transaction.remove((androidx.fragment.app.Fragment) object);
            transaction.commit();
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return ((androidx.fragment.app.Fragment) object).getView() == view;
        }
    }

    public static class MyListFragment extends androidx.fragment.app.ListFragment implements AdapterView.OnItemClickListener {

        private ListAdapter listAdapter;

        @Override
        public void onAttach(Context context) {
            super.onAttach(context);
            if (context instanceof StartConversationActivity) {
                ((StartConversationActivity) context).onAttachFragment(this);
            }
        }

        @Override
        public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            getListView().setOnItemClickListener(this);
        }

        public void setListAdapter(ListAdapter adapter) {
            listAdapter = adapter;
            super.setListAdapter(adapter);
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (getActivity() instanceof StartConversationActivity) {
                ((StartConversationActivity) getActivity()).onItemClick(parent, view, position, id);
            }
        }
    }
}