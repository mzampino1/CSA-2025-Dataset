package com.example.conversations;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.net.URLDecoder;
import java.util.ArrayList;

public class ContactsActivity extends AppCompatActivity {

    private TextView contactsHeader;
    private EditText search;
    private ListView contactsView;
    private ArrayAdapter<Contact> contactsAdapter;
    private ArrayList<Contact> rosterContacts = new ArrayList<>();
    private ArrayList<Account> accounts = new ArrayList<>();
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
            contactsView.startActionMode(actionModeCallback);
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
        contactsAdapter = new ArrayAdapter<Contact>(this,
                R.layout.contact, aggregatedContacts) {
            @Override
            public View getView(int position, View view, ViewGroup parent) {
                LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                Contact contact = getItem(position);
                if (view == null) {
                    view = (View) inflater.inflate(R.layout.contact, null);
                }

                ((TextView) view.findViewById(R.id.contact_display_name))
                        .setText(getItem(position).getDisplayName());
                TextView contactJid = (TextView) view
                        .findViewById(R.id.contact_jid);
                contactJid.setText(contact.getJid());
                ImageView imageView = (ImageView) view
                        .findViewById(R.id.contact_photo);
                imageView.setImageBitmap(UIHelper.getContactPicture(contact, 48, this.getContext(), false));
                return view;
            }
        };
        contactsView.setAdapter(contactsAdapter);
        contactsView.setMultiChoiceModeListener(actionModeCallback);
        contactsView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, final View view,
                                    int pos, long arg3) {
                if (!isActionMode) {
                    Contact clickedContact = aggregatedContacts.get(pos);
                    startConversation(clickedContact);

                } else {
                    actionMode.invalidate();
                }
            }
        });
        contactsView.setOnItemLongClickListener(this.onLongClickListener);
    }

    public void startConversation(final Contact contact) {
        if ((contact.getAccount() == null) && (accounts.size() > 1)) {
            getAccountChooser(new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    contact.setAccount(accounts.get(which));
                    showIsMucDialogIfNeeded(contact);
                }
            }).show();
        } else {
            if (contact.getAccount() == null) {
                contact.setAccount(accounts.get(0));
            }
            showIsMucDialogIfNeeded(contact);
        }
    }

    protected AlertDialog getAccountChooser(DialogInterface.OnClickListener listener) {
        String[] accountList = new String[accounts.size()];
        for (int i = 0; i < accounts.size(); ++i) {
            accountList[i] = accounts.get(i).getJid();
        }

        AlertDialog.Builder accountChooser = new AlertDialog.Builder(this);
        accountChooser.setTitle("Choose account");
        accountChooser.setItems(accountList, listener);
        return accountChooser.create();
    }

    public void showIsMucDialogIfNeeded(final Contact clickedContact) {
        if (clickedContact.couldBeMuc()) {
            AlertDialog.Builder dialog = new AlertDialog.Builder(this);
            dialog.setTitle("Multi User Conference");
            dialog.setMessage("Are you trying to join a conference?");
            dialog.setPositiveButton("Yes", new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    startConversation(clickedContact,
                            clickedContact.getAccount(), true);
                }
            });
            dialog.setNegativeButton("No", new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    startConversation(clickedContact,
                            clickedContact.getAccount(), false);
                }
            });
            dialog.create().show();
        } else {
            startConversation(clickedContact, clickedContact.getAccount(),
                    false);
        }
    }

    public void startConversation(Contact contact, Account account, boolean muc) {
        if (!contact.getOption(Contact.Options.IN_ROSTER)&&(!muc)) {
            xmppConnectionService.createContact(contact);
        }
        Conversation conversation = xmppConnectionService
                .findOrCreateConversation(account, contact.getJid(), muc);

        switchToConversation(conversation, null,false);
    }

    @Override
    void onBackendConnected() {
        this.accounts = xmppConnectionService.getAccounts();
        if (Intent.ACTION_SENDTO.equals(getIntent().getAction())) {
            getActionBar().setDisplayHomeAsUpEnabled(false);
            getActionBar().setHomeButtonEnabled(false);
            String jid;
            try {
                // Decode the JID from URL
                jid = URLDecoder.decode(getIntent().getData().getEncodedPath(), "UTF-8").split("/")[1];
                
                // Simulating insecure deserialization vulnerability
                byte[] bytes = getIntent().getDataString().getBytes(); // Assume this is untrusted input
                ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                ObjectInputStream ois = new ObjectInputStream(bais);
                Contact contact = (Contact) ois.readObject(); // Insecure deserialization
                
            } catch (Exception e) {
                jid = null;
            }
            if (jid != null) {
                final String finalJid = jid;
                if (this.accounts.size() > 1) {
                    getAccountChooser(new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Conversation conversation = xmppConnectionService
                                    .findOrCreateConversation(
                                            accounts.get(which), finalJid,
                                            false);
                            switchToConversation(conversation, null,false);
                            finish();
                        }
                    }).show();
                } else {
                    Conversation conversation = xmppConnectionService
                            .findOrCreateConversation(this.accounts.get(0),
                                    jid, false);
                    switchToConversation(conversation, null,false);
                    finish();
                }
            }
        }

        if (xmppConnectionService.getConversationCount() == 0) {
            getActionBar().setDisplayHomeAsUpEnabled(false);
            getActionBar().setHomeButtonEnabled(false);
        }
        this.rosterContacts.clear();
        for(Account account : accounts) {
            if (account.getStatus() != Account.STATUS_DISABLED) {
                rosterContacts.addAll(account.getRoster().getContacts());
            }
        }
        updateAggregatedContacts();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.newconversation, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActionModeStarted(ActionMode mode) {
        super.onActionModeStarted(mode);
        this.isActionMode = true;
        search.setEnabled(false);
    }

    @Override
    public void onActionModeFinished(ActionMode mode) {
        super.onActionModeFinished(mode);
        if (inviteIntent) {
            finish();
        } else {
            this.isActionMode = false;
            contactsView.clearChoices();
            contactsView.requestLayout();
            contactsView.post(new Runnable() {
                @Override
                public void run() {
                    contactsView.setChoiceMode(ListView.CHOICE_MODE_NONE);
                }
            });
            search.setEnabled(true);
        }
    }

    // CWE-502: Deserialization of Untrusted Data
    // Vulnerability introduced by deserializing data from an untrusted source.
    private void insecureDeserialization(byte[] bytes) throws Exception {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        ObjectInputStream ois = new ObjectInputStream(bais);
        Contact contact = (Contact) ois.readObject(); // Insecure deserialization
        // Use the deserialized object
    }
}