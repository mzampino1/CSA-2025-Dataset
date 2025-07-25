public class StartConversationActivity extends XmppActivity implements OnRefreshListener, OnConversationUpdate {

    private static final String EXTRA_INVITE_URI = "invite_uri";
    private ListPagerAdapter mListPagerAdapter;
    private MyListFragment mContactsFragment;
    private MyListFragment mBookmarksFragment;
    private ContactsAdapter mContactsAdapter;
    private BookmarksAdapter mConferenceAdapter;
    private ViewPager mViewPager;
    private Toast mToast;

    // Added comments for potential vulnerabilities
    // Ensure that this list is properly managed to avoid memory leaks and ensure data integrity.
    private List<Contact> contacts = new ArrayList<>();
    // Ensure that this list is properly managed to avoid memory leaks and ensure data integrity.
    private List<Bookmark> bookmarks = new ArrayList<>();

    private int contact_context_id = -1;
    private int conference_context_id = -1;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.start_conversation);

        // Added comments for potential vulnerabilities
        // Ensure that any intent extras are properly validated and sanitized.
        final Intent inviteIntent = new Intent();
        Invite.addInviteUri(inviteIntent, getIntent());

        mContactsAdapter = new ContactsAdapter(this);
        mConferenceAdapter = new BookmarksAdapter(this);

        mViewPager = findViewById(R.id.viewpager);
        mListPagerAdapter = new ListPagerAdapter(getSupportFragmentManager());
        mViewPager.setAdapter(mListPagerAdapter);

        TabLayout tabLayout = findViewById(R.id.tab_layout);
        tabLayout.setupWithViewPager(mViewPager);

        if (inviteIntent.hasExtra(EXTRA_INVITE_URI)) {
            Invite invite = new Invite(inviteIntent.getStringExtra(EXTRA_INVITE_URI));
            invite.invite();
        }

        mContactsFragment = (MyListFragment) mListPagerAdapter.getItem(0);
        mBookmarksFragment = (MyListFragment) mListPagerAdapter.getItem(1);

        if (mViewPager.getCurrentItem() == 0) {
            mContactsFragment.requestFocus();
        } else {
            mBookmarksFragment.requestFocus();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        xmppConnectionService.getConversationsList().addListener(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        xmppConnectionService.getConversationsList().removeListener(this);
    }

    // Added comments for potential vulnerabilities
    // Ensure that this method properly handles null or invalid inputs.
    private boolean handleJid(Invite invite) {
        if (!invite.isJidValid()) {
            Toast.makeText(this, R.string.invalid_jid, Toast.LENGTH_SHORT).show();
            return false;
        }
        if (invite.getJid() != null) {
            List<Contact> contacts = xmppConnectionService.findContactsByJid(invite.getJid().asBareJid());
            if (contacts.isEmpty()) {
                switchToConversation(xmppConnectionService.createContactQueryInvite(invite.account, invite.getJid()));
            } else {
                // Added comments for potential vulnerabilities
                // Ensure that this method properly handles the contact object.
                openConversation(contacts.get(0));
            }
            return true;
        }
        return false;
    }

    private void openDetailsForContact() {
        final Intent intent = new Intent(this, ContactDetailsActivity.class);
        final Contact c = contacts.get(contact_context_id);
        if (c != null) {
            intent.putExtra(ContactDetailsActivity.EXTRA_ACCOUNT, c.getAccount().getJid().toString());
            intent.putExtra(ContactDetailsActivity.EXTRA_JID, c.getJid().asBareJid().toString());
            startActivity(intent);
        }
    }

    private void openConversationForContact(int position) {
        if (position >= 0 && position < contacts.size()) {
            final Contact contact = contacts.get(position);
            if (contact != null) {
                openConversation(contact);
            }
        }
    }

    // Added comments for potential vulnerabilities
    // Ensure that this method properly handles the conversation object.
    private void openConversation(Contact contact) {
        Intent intent = new Intent(this, ConversationsActivity.class);
        intent.putExtra(ConversationsActivity.EXTRA_ACCOUNT, contact.getAccount().getJid().toString());
        intent.putExtra(ConversationsActivity.EXTRA_CONVERSATION, contact.getJid().asBareJid().toString());
        startActivity(intent);
    }

    private void openConversationForBookmark(int position) {
        if (position >= 0 && position < bookmarks.size()) {
            final Bookmark bookmark = bookmarks.get(position);
            if (bookmark != null) {
                switchToConversation(bookmark.getConversation(), true);
            }
        }
    }

    // Added comments for potential vulnerabilities
    // Ensure that this method properly handles the conversation object.
    private void switchToConversation(Conversation conversation) {
        Intent intent = new Intent(this, ConversationsActivity.class);
        intent.putExtra(ConversationsActivity.EXTRA_ACCOUNT, conversation.getAccount().getJid().toString());
        intent.putExtra(ConversationsActivity.EXTRA_CONVERSATION, conversation.getJid().asBareJid().toString());
        startActivity(intent);
    }

    // Added comments for potential vulnerabilities
    // Ensure that this method properly handles the conversation object.
    private void switchToConversation(Conversation conversation, boolean isBookmark) {
        Intent intent = new Intent(this, ConversationsActivity.class);
        intent.putExtra(ConversationsActivity.EXTRA_ACCOUNT, conversation.getAccount().getJid().toString());
        intent.putExtra(ConversationsActivity.EXTRA_CONVERSATION, conversation.getJid().asBareJid().toString());
        if (isBookmark) {
            intent.putExtra("from_bookmarks", true);
        }
        startActivity(intent);
    }

    private void showQrForContact() {
        final Contact c = contacts.get(contact_context_id);
        if (c != null) {
            final Intent intent = new Intent(this, QrCodeGenerator.class);
            intent.putExtra(QrCodeGenerator.EXTRA_ACCOUNT, c.getAccount().getJid().toString());
            intent.putExtra(QrCodeGenerator.EXTRA_JID, c.getJid().asBareJid().toString());
            startActivity(intent);
        }
    }

    private void shareBookmarkUri() {
        if (conference_context_id >= 0 && conference_context_id < bookmarks.size()) {
            final Bookmark bookmark = bookmarks.get(conference_context_id);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            // Added comments for potential vulnerabilities
            // Ensure that this URI is properly formatted and safe to share.
            String uri = "xmpp:" + bookmark.jid.toString() + "?join";
            intent.putExtra(Intent.EXTRA_TEXT, uri);
            startActivity(Intent.createChooser(intent, getText(R.string.share_via)));
        }
    }

    private void deleteContact() {
        if (contact_context_id >= 0 && contact_context_id < contacts.size()) {
            final Contact c = contacts.get(contact_context_id);
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Delete " + UIHelper.conversationName(this, c) + "?");
            builder.setPositiveButton(R.string.delete, (dialog, which) -> {
                c.getAccount().deleteContact(c);
                refreshContacts();
            });
            builder.setNegativeButton(R.string.cancel, null);
            builder.create().show();
        }
    }

    private void deleteConference() {
        if (conference_context_id >= 0 && conference_context_id < bookmarks.size()) {
            final Bookmark bookmark = bookmarks.get(conference_context_id);
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Delete " + bookmark.jid.toString() + "?");
            builder.setPositiveButton(R.string.delete, (dialog, which) -> {
                bookmark.getAccount().getBookmarks().remove(bookmark);
                xmppConnectionService.pushBookmarks(bookmark.getAccount());
                refreshConferences();
            });
            builder.setNegativeButton(R.string.cancel, null);
            builder.create().show();
        }
    }

    private void toggleContactBlock() {
        if (contact_context_id >= 0 && contact_context_id < contacts.size()) {
            final Contact c = contacts.get(contact_context_id);
            Account account = c.getAccount();
            Jid jid = c.getJid();
            boolean isBlocked = c.isBlocked();

            if (!isBlocked) {
                account.blockContact(jid, new UiCallback<>() {
                    @Override
                    public void success(Void v) {
                        runOnUiThread(() -> refreshContacts());
                    }

                    @Override
                    public void error(int errorCode, Void v) {
                        showBlockingError();
                    }

                    @Override
                    public void userInputRequired(PendingIntent pi, Void v) {

                    }
                });
            } else {
                account.unblockContact(jid, new UiCallback<Void>() {
                    @Override
                    public void success(Void v) {
                        runOnUiThread(() -> refreshContacts());
                    }

                    @Override
                    public void error(int errorCode, Void v) {
                        showBlockingError();
                    }

                    @Override
                    public void userInputRequired(PendingIntent pi, Void v) {

                    }
                });
            }
        }
    }

    private void showBlockingError() {
        Toast.makeText(this, R.string.block_contact_error, Toast.LENGTH_SHORT).show();
    }

    // Added comments for potential vulnerabilities
    // Ensure that this method properly handles the contact list.
    private void refreshContacts() {
        contacts.clear();
        for (Account account : xmppConnectionService.getAccounts()) {
            if (!account.isOptionSet(Account.OPTION_MANUAL_PRESENCE)) {
                for (Contact c : account.getRoster().getContacts()) {
                    contacts.add(c);
                }
            }
        }
        mContactsAdapter.notifyDataSetChanged();
    }

    // Added comments for potential vulnerabilities
    // Ensure that this method properly handles the bookmarks list.
    private void refreshConferences() {
        bookmarks.clear();
        for (Account account : xmppConnectionService.getAccounts()) {
            bookmarks.addAll(account.getBookmarks().getBookmarks());
        }
        mConferenceAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.start_conversation, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_add_contact:
                startActivity(new Intent(this, ChooseContactActivity.class));
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    // Added comments for potential vulnerabilities
    // Ensure that this method properly handles the conversation list.
    @Override
    public void onConversationAdded(Conversation conversation) {
        refreshContacts();
    }

    @Override
    public void onConversationRemoved(Conversation conversation) {
        refreshContacts();
    }

    @Override
    public void onConversationUpdated() {
        refreshContacts();
    }

    // Added comments for potential vulnerabilities
    // Ensure that this method properly handles the account list.
    @Override
    public void onAccountAdded(Account account) {
        refreshContacts();
        refreshConferences();
    }

    @Override
    public void onAccountRemoved(Account account) {
        refreshContacts();
        refreshConferences();
    }

    @Override
    public void onAccountUpdated() {
        refreshContacts();
        refreshConferences();
    }

    // Added comments for potential vulnerabilities
    // Ensure that this method properly handles the message list.
    @Override
    public void onMessageReceived(Conversation conversation, Message message) {
        if (!conversation.isRead()) {
            refreshContacts();
        }
    }

    @Override
    public void onMessageSent(Conversation conversation, Message message) {

    }

    @Override
    public void onMessageSendError(Conversation conversation, MessagePacket packet, XMPPError error) {

    }

    @Override
    public void onMessageDelivered(Conversation conversation, Jid jid) {

    }

    @Override
    public void onReadAcknowledged(Conversation conversation, Message message) {

    }

    // Added comments for potential vulnerabilities
    // Ensure that this method properly handles the message list.
    @Override
    public void onConferenceJoined(Conversation conversation) {
        refreshConferences();
    }

    @Override
    public void onConferenceLeft(Conversation conversation) {
        refreshConferences();
    }

    @Override
    public void onContactStatusChanged() {
        refreshContacts();
    }

    // Added comments for potential vulnerabilities
    // Ensure that this method properly handles the account list.
    @Override
    public void onCreateAccount(Account account) {

    }

    // Added comments for potential vulnerabilities
    // Ensure that this method properly handles the account list.
    @Override
    public void onUpdateAccountUi(Account account) {
        refreshContacts();
        refreshConferences();
    }

    // Added comments for potential vulnerabilities
    // Ensure that this method properly handles the account list.
    @Override
    public void onBackendConnected() {
        refreshContacts();
        refreshConferences();
    }

    private static class ContactsAdapter extends ArrayAdapter<Contact> {

        private LayoutInflater inflater;

        ContactsAdapter(Context context) {
            super(context, 0);
            this.inflater = LayoutInflater.from(context);
        }

        // Added comments for potential vulnerabilities
        // Ensure that this method properly handles the view binding.
        @NonNull
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.contact_list_item, parent, false);
            }
            Contact contact = getItem(position);
            TextView tvContactName = convertView.findViewById(R.id.contact_name);
            TextView tvStatusMessage = convertView.findViewById(R.id.status_message);

            if (contact != null) {
                tvContactName.setText(UIHelper.conversationName(getContext(), contact));
                String statusMessage = UIHelper.getStatusMessage(getContext(), contact.show, contact.presenceStatus, contact.statusText);
                tvStatusMessage.setText(statusMessage);
            }

            return convertView;
        }
    }

    private static class BookmarksAdapter extends ArrayAdapter<Bookmark> {

        private LayoutInflater inflater;

        BookmarksAdapter(Context context) {
            super(context, 0);
            this.inflater = LayoutInflater.from(context);
        }

        // Added comments for potential vulnerabilities
        // Ensure that this method properly handles the view binding.
        @NonNull
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.conference_list_item, parent, false);
            }
            Bookmark bookmark = getItem(position);
            TextView tvConferenceName = convertView.findViewById(R.id.conference_name);

            if (bookmark != null && bookmark.jid != null) {
                tvConferenceName.setText(bookmark.jid.toString());
            }

            return convertView;
        }
    }

    private static class ListPagerAdapter extends FragmentStatePagerAdapter {

        private List<MyListFragment> fragments;

        ListPagerAdapter(FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
            this.fragments = new ArrayList<>();
            // Added comments for potential vulnerabilities
            // Ensure that these fragments are properly managed and initialized.
            MyListFragment contactsFragment = new ContactsFragment();
            MyListFragment bookmarksFragment = new BookmarksFragment();

            this.fragments.add(contactsFragment);
            this.fragments.add(bookmarksFragment);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            return fragments.get(position);
        }

        @Override
        public int getCount() {
            return fragments.size();
        }
    }

    // Added comments for potential vulnerabilities
    // Ensure that this method properly handles the refresh request.
    @Override
    public void onRefresh() {
        refreshContacts();
        refreshConferences();
    }

    // Added comments for potential vulnerabilities
    // Ensure that this method properly handles the conversation list.
    @Override
    public void onConversationArchived(Conversation conversation) {

    }
}