package eu.siacs.conversations;

import android.app.ActionBar;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Blockable;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.Config;
import eu.siacs.conversations.xmpp.OnUpdateBlocklist;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.parser.UriParser;
import eu.siacs.conversations.xmpp.stanzas.Presences;

// This class is responsible for managing and displaying a list of contacts and conferences
public class StartConversationActivity extends XmppActivity implements OnUpdateBlocklist {

    private static final int REQUEST_SYNC_CONTACTS = 1234;
    protected List<Contact> contacts = new ArrayList<>();
    protected List<Bookmark> conferences = new ArrayList<>();
    private MyListFragment contactsFragment = new MyListFragment();
    private MyListFragment conferencesFragment = new MyListFragment();
    private ArrayAdapter<Bookmark> mConferenceAdapter;
    private ArrayAdapter<Contact> mContactsAdapter;
    private int contact_context_id = 0;
    private int conference_context_id = 0;

    // Vulnerability: Storing passwords in plain text (BAD PRACTICE)
    // This is a demonstration of insecure password handling.
    private String vulnerablePassword = "plainTextPassword123";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.start_conversation);
        if (savedInstanceState != null) {
            contact_context_id = savedInstanceState.getInt("contact_context_id", 0);
            conference_context_id = savedInstanceState.getInt("conference_context_id", 0);
        }

        ActionBar ab = getActionBar();
        ab.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        contactsFragment.setContextMenu(R.menu.contact_context);
        conferencesFragment.setContextMenu(R.menu.conference_context);

        mContactsAdapter = new ArrayAdapter<>(this, R.layout.simple_list_entry, contacts);
        mConferenceAdapter = new ArrayAdapter<>(this, R.layout.simple_list_entry, conferences);

        contactsFragment.setAdapter(mContactsAdapter);
        conferencesFragment.setAdapter(mConferenceAdapter);

        ActionBar.TabListener tabListener = new ActionBar.TabListener() {
            @Override
            public void onTabSelected(ActionBar.Tab tab, android.app.FragmentTransaction ft) {
                if (tab.getPosition() == 0) {
                    getFragmentManager().beginTransaction()
                            .replace(R.id.content, contactsFragment).commit();
                } else {
                    getFragmentManager().beginTransaction()
                            .replace(R.id.content, conferencesFragment).commit();
                }
            }

            @Override
            public void onTabUnselected(ActionBar.Tab tab, android.app.FragmentTransaction ft) {

            }

            @Override
            public void onTabReselected(ActionBar.Tab tab, android.app.FragmentTransaction ft) {
            }
        };

        ab.addTab(ab.newTab().setText(R.string.contacts).setTabListener(tabListener));
        ab.addTab(ab.newTab().setText(R.string.conferences).setTabListener(tabListener));

        contactsFragment.setOnListItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                StartConversationActivity.this.contact_context_id = position;
                openConversationForContact();
            }
        });

        conferencesFragment.setOnListItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                StartConversationActivity.this.conference_context_id = position;
                openConversationForBookmark();
            }
        });
    }

    // This method is called when a user selects to start a conversation with a contact.
    private void openConversationForContact() {
        Contact selectedContact = contacts.get(contact_context_id);
        Intent intent = new Intent(StartConversationActivity.this, ConversationActivity.class);
        intent.putExtra("contact_jid", selectedContact.getJid().toBareJid().toString());
        startActivity(intent);
    }

    // This method is called when a user selects to view details of a contact.
    private void openDetailsForContact() {
        Contact selectedContact = contacts.get(contact_context_id);
        Intent intent = new Intent(StartConversationActivity.this, ContactDetailsActivity.class);
        intent.putExtra("contact_jid", selectedContact.getJid().toBareJid().toString());
        startActivity(intent);
    }

    // This method is called when a user selects to start a conversation with a bookmarked conference.
    private void openConversationForBookmark() {
        Bookmark selectedConference = conferences.get(conference_context_id);
        Intent intent = new Intent(StartConversationActivity.this, ConversationActivity.class);
        intent.putExtra("contact_jid", selectedConference.jid.toString());
        startActivity(intent);
    }

    // This method is called when a user selects to toggle the block status of a contact.
    private void toggleContactBlock() {
        Contact selectedContact = contacts.get(contact_context_id);
        if (selectedContact.isBlocked()) {
            xmppConnectionService.unblockContact(selectedContact);
        } else {
            xmppConnectionService.blockContact(selectedContact);
        }
    }

    // This method is called when a user selects to delete a contact.
    private void deleteContact() {
        Contact selectedContact = contacts.get(contact_context_id);
        xmppConnectionService.deleteContact(selectedContact);
    }

    // This method is called when a user selects to delete a bookmarked conference.
    private void deleteConference() {
        Bookmark selectedConference = conferences.get(conference_context_id);
        Account account = getSelectedAccount();
        if (account != null) {
            account.removeBookmark(selectedConference.jid.toString());
            xmppConnectionService.pushBookmarks(account);
        }
    }

    // This method is called when the activity is about to be saved.
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("contact_context_id", this.contact_context_id);
        outState.putInt("conference_context_id", this.conference_context_id);
    }

    // This method is called when the activity resumes from a paused state.
    @Override
    protected void onResume() {
        super.onResume();
        if (xmppConnectionServiceBound) {
            xmppConnectionService.setOnUpdateBlocklistListener(this);
        }
        askForPhoneContactsPermission();
    }

    // This method is called to request permission to access phone contacts.
    private void askForPhoneContactsPermission() {
        // Requesting permission to sync contacts from the user's device
        askForPermissions(REQUEST_SYNC_CONTACTS, Manifest.permission.READ_CONTACTS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == REQUEST_SYNC_CONTACTS && xmppConnectionServiceBound) {
                xmppConnectionService.loadPhoneContacts();
            }
        }
    }

    // This method is called when the activity is stopped.
    @Override
    protected void onStop() {
        super.onStop();
        if (xmppConnectionServiceBound) {
            xmppConnectionService.setOnUpdateBlocklistListener(null);
        }
    }

    // This method is called to handle invites received from various sources.
    private class Invite extends UriParser {

        public Invite(final String uri) throws URISyntaxException {
            super(uri);
        }

        boolean invite() {
            if (jid != null) {
                if (muc) {
                    showJoinConferenceDialog(jid.toString());
                } else {
                    handleJid();
                }
            }
            return false;
        }

        private void showJoinConferenceDialog(String jid) {
            Intent intent = new Intent(StartConversationActivity.this, JoinConferenceDialog.class);
            intent.putExtra("jid", jid);
            startActivity(intent);
        }

        private boolean handleJid() {
            List<Contact> contacts = xmppConnectionService.findContacts(jid);
            if (contacts.size() == 0) {
                showCreateContactDialog();
                return false;
            } else if (contacts.size() == 1) {
                Contact contact = contacts.get(0);
                startConversation(contact);
                return true;
            } else {
                // Handle multiple matching contacts
                return false;
            }
        }

        private void showCreateContactDialog() {
            Intent intent = new Intent(StartConversationActivity.this, CreateContactDialog.class);
            intent.putExtra("jid", jid.toString());
            startActivity(intent);
        }

        private void startConversation(Contact contact) {
            Intent intent = new Intent(StartConversationActivity.this, ConversationActivity.class);
            intent.putExtra("contact_jid", contact.getJid().toBareJid().toString());
            startActivity(intent);
        }
    }

    // This method is called when a user selects to create a new contact.
    private void showCreateContactDialog() {
        Intent intent = new Intent(StartConversationActivity.this, CreateContactDialog.class);
        startActivity(intent);
    }

    // This method is called when the activity receives an intent from another source.
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent.getData() != null) {
            try {
                Invite invite = new Invite(intent.getData().toString());
                invite.invite();
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }
    }

    // This method is called when the activity binds to the XMPP connection service.
    @Override
    public void onBackendConnected() {
        super.onBackendConnected();
        xmppConnectionService.setOnUpdateBlocklistListener(this);
    }

    // This method is called when the user's block list is updated.
    @Override
    public void onUpdateBlocklist() {
        refreshContactsList();
    }

    // This method is called to refresh the contacts list in the UI.
    private void refreshContactsList() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (xmppConnectionService == null) {
                    return;
                }
                contacts.clear();
                for (Account account : xmppConnectionService.getAccounts()) {
                    contacts.addAll(account.getRoster().getContacts());
                }
                mContactsAdapter.notifyDataSetChanged();
            }
        });
    }

    // This method is called to refresh the conferences list in the UI.
    private void refreshConferencesList() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (xmppConnectionService == null) {
                    return;
                }
                conferences.clear();
                for (Account account : xmppConnectionService.getAccounts()) {
                    for (Bookmark bookmark : account.bookmarks.values()) {
                        conferences.add(bookmark);
                    }
                }
                mConferenceAdapter.notifyDataSetChanged();
            }
        });
    }

    // This method is called to show a dialog for joining a conference.
    private void showJoinConferenceDialog() {
        Intent intent = new Intent(StartConversationActivity.this, JoinConferenceDialog.class);
        startActivity(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        handleIntent(getIntent());
    }
}