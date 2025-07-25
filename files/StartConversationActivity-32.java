package eu.siacs.conversations.ui;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ListView;
import android.widget.Spinner;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OmemoSetting;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.adapter.KnownHostsAdapter;
import eu.siacs.conversations.utils.AccountUtils;
import eu.siacs.conversations.utils.JidDialog;
import eu.siacs.conversations.utils.JidDomainAutoCompleteTextView;
import eu.siacs.conversations.utils.XmppUri;
import rocks.xmpp.addr.Jid;

import java.util.ArrayList;
import java.util.List;

public class StartConversationActivity extends XmppActivity implements OnContactSelected, OnPresenceSelected, OnOpenWithClick, ViewPager.OnPageChangeListener, AdapterView.OnItemClickListener, MultiChoiceModeListener {

    private List<Contact> contacts = new ArrayList<>();
    private ArrayAdapter<Bookmark> mConferenceAdapter;
    private ArrayAdapter<Contact> mContactsAdapter;

    private int contact_context_id = -1;
    private int conference_context_id = -1;

    private ViewPager viewPager;
    private ListPagerAdapter mListPagerAdapter;
    private View listViewFrame;
    private boolean hideExtendedMenuItems = false;

    public static final String EXTRA_INVITE_URI = "eu.siacs.conversations.ui.START_CONVERSATION_INVITE_URI";

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_conversation);

        listViewFrame = findViewById(R.id.listview_frame);

        viewPager = (ViewPager) findViewById(R.id.view_pager);
        mListPagerAdapter = new ListPagerAdapter(getSupportFragmentManager());
        viewPager.setAdapter(mListPagerAdapter);

        mContactsAdapter = new ArrayAdapter<Contact>(this, R.layout.simple_list_item, contacts);
        mConferenceAdapter = new ArrayAdapter<Bookmark>(this, R.layout.simple_list_item);
        updateConversationList();
        loadConferences();

        final ViewPager.OnPageChangeListener pageChangeListener = this;
        viewPager.addOnPageChangeListener(pageChangeListener);

    }

    @Override
    protected void onStart() {
        super.onStart();
        if (getActionBar() != null) {
            getActionBar().setTitle(R.string.start_conversation);
        }
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CREATE_CONFERENCE:
                if (resultCode == RESULT_OK && data != null) {
                    String uuid = data.getStringExtra(ChooseContactActivity.EXTRA_RESULT_RECEIVER);
                    Conversation conversation = xmppConnectionService.findConversationByUuid(uuid);
                    if (conversation != null) {
                        switchToConversation(conversation);
                    }
                }
                break;
        }
    }

    @Override
    public boolean onContextItemSelected(final MenuItem item) {
        return mListPagerAdapter.getItem(viewPager.getCurrentItem()).onContextItemSelected(item);
    }

    private void updateConversationList() {
        contacts.clear();
        final DatabaseBackend db = new DatabaseBackend(this);
        contacts.addAll(db.getContacts());
        mContactsAdapter.notifyDataSetChanged();
    }

    private void loadConferences() {
        if (xmppConnectionService != null && xmppConnectionService.getSelectedAccount() != null) {
            List<Bookmark> bookmarks = xmppConnectionService.getSelectedAccount().getBookmarks().getPersistentCopy();
            mConferenceAdapter.clear();
            mConferenceAdapter.addAll(bookmarks);
            mConferenceAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (viewPager.getCurrentItem() == 0) {
            openConversationForContact(position);
        } else {
            openConversationForBookmark(position);
        }
    }

    private void openConversationForContact(int p) {
        final Contact contact = contacts.get(p);
        if (contact != null && !contact.isSelf()) {
            Conversation conversation = xmppConnectionService.findOrCreateConversation(contact.getAccount(), contact.getJid().asBareJid());
            switchToConversation(conversation);
        }
    }

    private void openConversationForBookmark(int p) {
        final Bookmark bookmark = mConferenceAdapter.getItem(p);
        if (bookmark != null) {
            Conversation conversation;
            if ((conversation = bookmark.conversation()) != null) {
                switchToConversation(conversation);
            } else {
                Account account = bookmark.getAccount();
                conversation = xmppConnectionService.findOrCreateConversation(account, bookmark.getJid().asBareJid(), true, true, true);
                bookmark.setConversation(conversation);
                switchToConversation(conversation);
            }
        }
    }

    private void shareBookmarkUri() {
        final Bookmark bookmark = mConferenceAdapter.getItem(conference_context_id);
        if (bookmark != null) {
            Jid jid = bookmark.getJid();
            if (jid != null && !jid.isBareJid()) {
                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT, jid.toString());
                sendIntent.setType("text/plain");
                startActivity(Intent.createChooser(sendIntent, getResources().getText(R.string.share_via)));
            }
        }
    }

    private void deleteConference() {
        final Bookmark bookmark = mConferenceAdapter.getItem(conference_context_id);
        if (bookmark != null) {
            Account account = bookmark.getAccount();
            account.getBookmarks().remove(bookmark);
            xmppConnectionService.pushBookmarks(account);
            loadConferences();
        }
    }

    private void deleteContact() {
        final Contact contact = contacts.get(contact_context_id);
        if (contact != null && !contact.isSelf()) {
            Account account = contact.getAccount();
            account.deleteRosterItem(contact.getJid().asBareJid());
            updateConversationList();
        }
    }

    private void toggleContactBlock() {
        final Contact contact = contacts.get(contact_context_id);
        if (contact != null && !contact.isSelf()) {
            Account account = contact.getAccount();
            if (contact.isBlocked()) {
                account.getServer().unblockJid(contact.getJid().asBareJid());
            } else {
                account.getServer().blockJid(contact.getJid().asBareJid());
            }
        }
    }

    private void openDetailsForContact() {
        final Contact contact = contacts.get(contact_context_id);
        if (contact != null && !contact.isSelf()) {
            Intent intent = new Intent(this, ShowProfileActivity.class);
            intent.putExtra("jid", contact.getJid().toEscapedString());
            intent.putExtra("account", contact.getAccount().getJid().asBareJid().toString());
            startActivity(intent);
        }
    }

    private void showQrForContact() {
        final Contact contact = contacts.get(contact_context_id);
        if (contact != null) {
            Intent intent = new Intent(this, ShowQrCodeActivity.class);
            intent.putExtra("jid", contact.getJid().toEscapedString());
            startActivity(intent);
        }
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

    }

    @Override
    public void onPageSelected(int position) {
        updateActionBar(position == 0 ? R.string.contacts : R.string.conferences);
        if (position == 1 && xmppConnectionService != null) {
            loadConferences();
        }
    }

    @Override
    public void onPageScrollStateChanged(int state) {

    }

    private void updateActionBar(final int stringResId) {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(stringResId);
        }
    }

    @Override
    public void onContactSelected(Contact contact) {
        Conversation conversation = xmppConnectionService.findOrCreateConversation(contact.getAccount(), contact.getJid());
        switchToConversation(conversation);
    }

    @Override
    public void onPresenceSelected(String jid, boolean online) {

    }

    @Override
    public void onOpenWithClick(Jid jid) {
        Conversation conversation = xmppConnectionService.findOrCreateConversation(xmppConnectionService.getSelectedAccount(), jid);
        switchToConversation(conversation);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        AdapterView.AdapterContextMenuInfo acmi = (AdapterView.AdapterContextMenuInfo) menuInfo;
        if (viewPager.getCurrentItem() == 0) {
            inflater.inflate(R.menu.start_conversation_context, menu);
            Contact contact = contacts.get(acmi.position);
            MenuItem blockMenuItem = menu.findItem(R.id.action_block_contact);
            blockMenuItem.setTitle(contact.isBlocked() ? R.string.unblock_contact : R.string.block_contact);
        } else {
            inflater.inflate(R.menu.conference_context, menu);
        }
    }

    @Override
    public boolean onPrepareActionMode(android.view.ActionMode mode, MenuItem item) {
        return false;
    }

    @Override
    public void onDestroyActionMode(android.view.ActionMode mode) {

    }

    @Override
    public boolean onCreateActionMode(android.view.ActionMode mode, MenuItem item) {
        hideExtendedMenuItems = true;
        mode.setTitle(R.string.select_contacts);
        mode.setSubtitle(null);
        return true;
    }

    @Override
    public void onItemCheckedStateChanged(android.view.ActionMode mode, int position, long id, boolean checked) {

    }

    private class ListPagerAdapter extends PagerAdapter {
        private final android.app.FragmentManager fragmentManager;

        ListPagerAdapter(final android.app.FragmentManager fm) {
            super();
            this.fragmentManager = fm;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            if (object instanceof KnownHostsAdapter.KnownHostsViewHolder) {
                fragmentTransaction.remove(((KnownHostsAdapter.KnownHostsViewHolder) object).fragment);
            }
            container.removeView((View) object);
        }

        @Override
        public FragmentTransaction getFragmentTransaction() {
            return fragmentManager.beginTransaction();
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            View view = LayoutInflater.from(StartConversationActivity.this).inflate(R.layout.list_page, container, false);
            ListView listView = (ListView) view.findViewById(R.id.listView);
            if (position == 0) {
                listView.setAdapter(mContactsAdapter);
                listView.setOnItemClickListener(this);
            } else {
                listView.setAdapter(mConferenceAdapter);
                listView.setOnItemClickListener(this);
            }
            container.addView(view);
            return view;
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view.equals(object);
        }
    }

    // Methods to handle XMPP URI invites and other related functionalities
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.hasExtra(EXTRA_INVITE_URI)) {
            String inviteUri = intent.getStringExtra(EXTRA_INVITE_URI);
            // Handle the invite URI here, e.g., parse and join a conference
            handleInviteUri(inviteUri);
        }
    }

    private void handleInviteUri(String uri) {
        try {
            XmppUri xmppUri = new XmppUri(uri);
            if (xmppUri.getType() == XmppUri.Type.CONFERENCE && xmppUri.getJid() != null) {
                Jid jid = xmppUri.getJid();
                Account account = xmppConnectionService.getSelectedAccount();
                Conversation conversation = xmppConnectionService.findOrCreateConversation(account, jid.asBareJid(), true, true, true);
                switchToConversation(conversation);
            }
        } catch (XmppUri.UriSyntaxException e) {
            Log.e(Config.LOGTAG, "Invalid XMPP URI: " + uri);
            showInviteErrorDialog();
        }
    }

    private void showInviteErrorDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.error);
        builder.setMessage(R.string.invalid_invite_uri);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
    }
}