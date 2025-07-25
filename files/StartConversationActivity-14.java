package com.example.conversations;

import android.content.Intent;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.FragmentActivity;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

// Base Activity for starting a conversation
public class StartConversationActivity extends FragmentActivity implements XmppConnectionService.OnUpdateBlocklist {
    private ArrayList<String> mActivatedAccounts = new ArrayList<>();
    private Invite mPendingInvite = null;
    private List<Contact> contacts = new ArrayList<>();
    private List<Bookmark> conferences = new ArrayList<>();
    private ArrayAdapter<Contact> mContactsAdapter;
    private ArrayAdapter<Bookmark> mConferenceAdapter;
    private String[] mKnownHosts;
    private String[] mKnownConferenceHosts;
    private boolean mHideOfflineContacts = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_conversation);

        // Initialize adapters for contacts and conferences
        mContactsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, contacts);
        mConferenceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, conferences);
    }

    @Override
    protected void onStart() {
        super.onStart();
        xmppConnectionServiceBound = bindXmppService(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindXmppService(this);
        xmppConnectionServiceBound = false;
    }

    // Handle joining a conference or creating a contact based on an invite URI
    private void showJoinConferenceDialog(Jid jid) {
        Bookmark bookmark = new Bookmark(jid.toString());
        bookmark.setLocal(true);

        // Assume addBookmark() is a method to store the bookmark for future use
        xmppConnectionService.addBookmark(bookmark);
        filterConferences(null);  // Refresh conference list view

        // Logic to join conference can be implemented here
    }

    // Dialog for creating a new contact based on JID and optional fingerprint
    private void showCreateContactDialog(String jid, String fingerprint) {
        Intent intent = new Intent(this, CreateContactActivity.class);
        intent.putExtra("jid", jid);
        if (fingerprint != null) {
            intent.putExtra("fingerprint", fingerprint);
        }
        startActivity(intent);
    }

    // Handle joining a conversation with a contact
    private void switchToConversation(Contact contact) {
        Intent intent = new Intent(this, ConversationActivity.class);
        intent.putExtra("jid", contact.getJid().toString());
        startActivity(intent);
    }

    // Logic to handle different intents like sending messages or viewing URIs
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        if (xmppConnectionServiceBound && !handleIntent(intent)) {
            filter(null);  // Refresh contact/conference list views with no filter applied
        }
    }

    private boolean handleIntent(Intent intent) {
        String action = intent.getAction();
        Uri data = intent.getData();

        switch (action) {
            case Intent.ACTION_SENDTO:
            case Intent.ACTION_VIEW:
                if (data != null && "xmpp".equals(data.getScheme())) {
                    return new Invite(data).invite();  // Process XMPP invite URI
                }
                break;
            // Handle NFC tag scans for invitations
            case NfcAdapter.ACTION_NDEF_DISCOVERED:
                Parcelable[] messages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
                if (messages != null) {
                    for (Parcelable message : messages) {
                        if (message instanceof NdefMessage) {
                            NdefRecord[] records = ((NdefMessage) message).getRecords();
                            for (NdefRecord record : records) {
                                short tnf = record.getTnf();  // Type name format
                                byte[] type = record.getType();

                                if (tnf == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(type, NdefRecord.RTD_URI)) {
                                    String uriStr;
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                                        uriStr = record.toUri().toString();
                                    } else {
                                        byte[] payload = record.getPayload();
                                        int prefixIndex = payload[0];
                                        String prefix = URI_PREFIX_MAP[prefixIndex & 0x0F]; // Assuming URI_PREFIX_MAP is a predefined array
                                        uriStr = prefix + new String(payload, 1, payload.length - 1, Charset.forName("UTF-8"));
                                    }
                                    return new Invite(uriStr).invite();  // Process invite URI from NFC tag
                                }
                            }
                        }
                    }
                }
        }
        return false;
    }

    // Inner class representing an XMPP invitation URI
    public static class Invite extends XmppUri {
        public Invite(Uri uri) {
            super(uri);
        }

        public Invite(String uriStr) {
            super(URI.create(uriStr));
        }

        boolean invite() {
            if (jid != null) {
                if (muc) {
                    showJoinConferenceDialog(jid);  // Handle multi-user chat invitation
                } else {
                    return handleJid();  // Handle one-on-one chat invitation
                }
            }
            return false;
        }

        private boolean handleJid() {
            List<Contact> contacts = xmppConnectionService.findContacts(jid);
            if (contacts.isEmpty()) {
                showCreateContactDialog(jid.toString(), fingerprint);  // No existing contact, prompt to create one
            } else if (contacts.size() == 1) {
                Contact contact = contacts.get(0);
                if (fingerprint != null && !contact.addOtrFingerprint(fingerprint)) {
                    Log.d(Config.LOGTAG, "Duplicate or invalid fingerprint");
                    return false;
                }
                switchToConversation(contact);  // Existing contact found, start conversation
            } else {
                // Multiple contacts with the same JID found, need to disambiguate (e.g., show a list)
                filterContacts(jid.toString());
            }
            return true;
        }
    }

    // ListFragment subclass for handling contact/conference lists
    public static class MyListFragment extends ListFragment {
        private AdapterView.OnItemClickListener mOnItemClickListener;
        private int mResContextMenu;

        @Override
        public void onListItemClick(ListView l, View v, int position, long id) {
            if (mOnItemClickListener != null) {
                mOnItemClickListener.onItemClick(l, v, position, id);
            }
        }

        public void setOnItemClickListener(AdapterView.OnItemClickListener listener) {
            this.mOnItemClickListener = listener;
        }

        @Override
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
            super.onCreateContextMenu(menu, v, menuInfo);

            StartConversationActivity activity = (StartConversationActivity) getActivity();
            MenuInflater inflater = activity.getMenuInflater();

            if (menuInfo instanceof AdapterView.AdapterContextMenuInfo) {
                int position = ((AdapterView.AdapterContextMenuInfo) menuInfo).position;

                if (mResContextMenu == R.menu.contact_context) {
                    Contact contact = activity.contacts.get(position);
                    MenuItem blockUnblockItem = menu.findItem(R.id.context_contact_block_unblock);
                    Account account = contact.getAccount();
                    XmppConnection connection = account.getXmppConnection();

                    if (connection != null && connection.getFeatures().blocking()) {
                        String title = contact.isBlocked() ? "Unblock Contact" : "Block Contact";
                        blockUnblockItem.setTitle(title);
                    } else {
                        blockUnblockItem.setVisible(false);
                    }
                }

                inflater.inflate(mResContextMenu, menu);
            }
        }

        @Override
        public boolean onContextItemSelected(MenuItem item) {
            StartConversationActivity activity = (StartConversationActivity) getActivity();
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();

            switch (item.getItemId()) {
                case R.id.context_start_conversation:
                    activity.switchToConversation(activity.contacts.get(info.position));
                    return true;
                case R.id.context_contact_details:
                    // Code to open details for the selected contact
                    return true;
                case R.id.context_contact_block_unblock:
                    Contact contact = activity.contacts.get(info.position);
                    if (contact.isBlocked()) {
                        contact.unblock();
                    } else {
                        contact.block();
                    }
                    xmppConnectionService.syncRosterToDisk(contact.getAccount());
                    return true;
                // Additional menu items for deleting contacts, joining conferences, etc.
                default:
                    return super.onContextItemSelected(item);
            }
        }
    }

    @Override
    public void OnUpdateBlocklist(Status status) {
        refreshUi();  // Update UI when blocklist changes
    }

    private void refreshUi() {
        filterContacts(null);  // Refresh contact list view with no filter applied
    }

    // Filter contacts based on a search string (null for no filtering)
    private void filterContacts(String query) {
        List<Contact> filteredList = new ArrayList<>();

        if (query == null || query.isEmpty()) {
            filteredList.addAll(contacts);
        } else {
            for (Contact contact : contacts) {
                String displayName = contact.getDisplayName();
                if (displayName.toLowerCase().contains(query.toLowerCase())) {
                    filteredList.add(contact);
                }
            }
        }

        mContactsAdapter.clear();
        mContactsAdapter.addAll(filteredList);
        mContactsAdapter.notifyDataSetChanged();  // Notify adapter of data change
    }

    // Filter conferences based on a search string (null for no filtering)
    private void filterConferences(String query) {
        List<Bookmark> filteredList = new ArrayList<>();

        if (query == null || query.isEmpty()) {
            filteredList.addAll(conferences);
        } else {
            for (Bookmark bookmark : conferences) {
                String name = bookmark.getName();
                if (name.toLowerCase().contains(query.toLowerCase())) {
                    filteredList.add(bookmark);
                }
            }
        }

        mConferenceAdapter.clear();
        mConferenceAdapter.addAll(filteredList);
        mConferenceAdapter.notifyDataSetChanged();  // Notify adapter of data change
    }
}