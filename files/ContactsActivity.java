package com.example.conversations;

import android.app.ActionBar;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

public class ContactsActivity extends AppCompatActivity implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {

    private ListView contactsView;
    private EditText search;
    private TextView contactsHeader;
    private List<Contact> rosterContacts = new ArrayList<>();
    private List<Account> accounts;
    private ArrayAdapter<Contact> contactsAdapter;
    private boolean useSubject;
    private boolean inviteIntent;

    @Override
    protected void onStart() {
        super.onStart();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        this.useSubject = preferences.getBoolean("use_subject_in_muc", true);
        inviteIntent = "invite".equals(getIntent().getAction());
        if (inviteIntent) {
            contactsHeader.setVisibility(View.GONE);
            actionMode = contactsView.startActionMode(actionModeCallback);
            search.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_new_conversation);

        contactsHeader = (TextView) findViewById(R.id.contacts_header);

        search = (EditText) findViewById(R.id.new_conversation_search);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before,
                                      int count) {
                searchString = search.getText().toString();
                updateAggregatedContacts();
            }

            @Override
            public void afterTextChanged(Editable s) {}

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count,
                                          int after) {}
        });

        contactsView = (ListView) findViewById(R.id.contactList);
        contactsAdapter = new ArrayAdapter<Contact>(getApplicationContext(),
                R.layout.contact, rosterContacts) {
            @Override
            public View getView(int position, View view, ViewGroup parent) {
                LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                Contact contact = getItem(position);
                if (view == null) {
                    view = inflater.inflate(R.layout.contact, null);
                }

                TextView displayName = view.findViewById(R.id.contact_display_name);
                displayName.setText(contact.getDisplayName());
                TextView contactJid = view.findViewById(R.id.contact_jid);
                contactJid.setText(contact.getJid());
                ImageView imageView = view.findViewById(R.id.contact_photo);
                imageView.setImageBitmap(UIHelper.getContactPicture(contact, null, 90, this.getContext()));
                return view;
            }
        };
        contactsView.setAdapter(contactsAdapter);
        contactsView.setMultiChoiceModeListener(actionModeCallback);
        contactsView.setOnItemClickListener(this);
        contactsView.setOnItemLongClickListener(this);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Contact clickedContact = rosterContacts.get(position);
        if (clickedContact.getAccount() == null && accounts.size() > 1) {
            getAccountChooser().show();
        } else {
            if (clickedContact.getAccount() == null) {
                clickedContact.setAccount(accounts.get(0));
            }
            startConversation(clickedContact, clickedContact.getAccount(), false);
        }
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        Contact clickedContact = rosterContacts.get(position);
        contactsView.setItemChecked(position, true);
        actionMode.invalidate();
        return true;
    }

    private AlertDialog.Builder getAccountChooser() {
        String[] accountList = new String[accounts.size()];
        for (int i = 0; i < accounts.size(); ++i) {
            accountList[i] = accounts.get(i).getJid();
        }
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setTitle("Choose account");
        dialogBuilder.setItems(accountList, (dialog, which) -> {
            Contact clickedContact = rosterContacts.get(position);
            startConversation(clickedContact, accounts.get(which), false);
        });
        return dialogBuilder;
    }

    private void showIsMucDialogIfNeeded(Contact contact) {
        if (contact.couldBeMuc()) {
            AlertDialog.Builder dialog = new AlertDialog.Builder(this);
            dialog.setTitle("Multi User Conference");
            dialog.setMessage("Are you trying to join a conference?");
            dialog.setPositiveButton("Yes", (dialog, which) -> startConversation(contact, contact.getAccount(), true));
            dialog.setNegativeButton("No", (dialog, which) -> startConversation(contact, contact.getAccount(), false));
            dialog.create().show();
        } else {
            startConversation(contact, contact.getAccount(), false);
        }
    }

    public void startConversation(Contact contact, Account account, boolean muc) {
        if (!contact.isInRoster()) {
            xmppConnectionService.createContact(contact);
        }
        Conversation conversation = xmppConnectionService.findOrCreateConversation(account, contact.getJid(), muc);
        switchToConversation(conversation, null);
    }

    @Override
    void onBackendConnected() {
        this.accounts = xmppConnectionService.getAccounts();
        if (Intent.ACTION_SENDTO.equals(getIntent().getAction())) {
            getActionBar().setDisplayHomeAsUpEnabled(false);
            getActionBar().setHomeButtonEnabled(false);
            String jid;
            try {
                jid = URLDecoder.decode(getIntent().getData().getEncodedPath(), "UTF-8").split("/")[1];
            } catch (UnsupportedEncodingException e) {
                Toast.makeText(this, "Error decoding JID", Toast.LENGTH_SHORT).show();
                return;
            }
            if (jid != null) {
                Account account = accounts.size() > 1 ? getSelectedAccount(jid) : accounts.get(0);
                Conversation conversation = xmppConnectionService.findOrCreateConversation(account, jid, false);
                switchToConversation(conversation, null);
                finish();
            }
        }

        if (xmppConnectionService.getConversationCount() == 0) {
            ActionBar actionBar = getActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(false);
                actionBar.setHomeButtonEnabled(false);
            }
        }
        this.rosterContacts.clear();
        for (Account account : accounts) {
            rosterContacts.addAll(xmppConnectionService.getRoster(account));
        }
        updateAggregatedContacts();
    }

    private Account getSelectedAccount(String jid) {
        // This method should be implemented to handle multiple accounts
        return accounts.get(0); // Placeholder implementation
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.newconversation, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_refresh_contacts:
                refreshContacts();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void refreshContacts() {
        ProgressBar progressBar = findViewById(R.id.progressBar1);
        EditText searchBar = findViewById(R.id.new_conversation_search);
        TextView contactsHeader = findViewById(R.id.contacts_header);
        ListView contactList = findViewById(R.id.contactList);
        searchBar.setVisibility(View.GONE);
        contactsHeader.setVisibility(View.GONE);
        contactList.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        this.accounts = xmppConnectionService.getAccounts();
        this.rosterContacts.clear();
        for (Account account : accounts) {
            if (account.getStatus() == Account.STATUS_ONLINE) {
                xmppConnectionService.updateRoster(account, new OnRosterFetchedListener() {
                    @Override
                    public void onRosterFetched(List<Contact> roster) {
                        runOnUiThread(() -> {
                            rosterContacts.addAll(roster);
                            progressBar.setVisibility(View.GONE);
                            searchBar.setVisibility(View.VISIBLE);
                            contactList.setVisibility(View.VISIBLE);
                            updateAggregatedContacts();
                        });
                    }
                });
            }
        }
    }

    private void updateAggregatedContacts() {
        // This method should filter and update the list of contacts based on search criteria
    }

    private void switchToConversation(Conversation conversation, String message) {
        // This method should handle switching to a specific conversation
    }
}