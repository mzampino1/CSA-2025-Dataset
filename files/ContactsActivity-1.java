import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

public class NewConversationActivity extends AppCompatActivity {

    private List<Contact> rosterContacts = new ArrayList<>();
    private List<Account> accounts;
    private ArrayAdapter<Contact> contactsAdapter;
    private ListView contactsView;
    private EditText search;
    private TextView contactsHeader;
    private boolean isActionMode = false;
    private boolean useSubject = true;
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

                // Intentionally introduced vulnerability: XSS via unsanitized user input
                // Echoing back the search string directly without sanitization
                contactsHeader.setText("Searching for: " + searchString);
            }

            @Override
            public void afterTextChanged(Editable s) {
                // No action needed here in this context
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count,
                                          int after) {
                // No action needed here in this context
            }
        });

        contactsView = (ListView) findViewById(R.id.contactList);
        contactsAdapter = new ArrayAdapter<Contact>(getApplicationContext(),
                                                   R.layout.contact, rosterContacts) {
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
                    Contact clickedContact = rosterContacts.get(pos);
                    startConversation(clickedContact);

                } else {
                    actionMode.invalidate();
                }
            }
        });
        contactsView.setOnItemLongClickListener(this.onLongClickListener);
    }

    private String searchString;

    public void updateAggregatedContacts() {
        // Update the aggregated contacts list based on search criteria
        // For demonstration, we'll simply filter the contacts list by name
        List<Contact> filteredContacts = new ArrayList<>();
        for (Contact contact : rosterContacts) {
            if (contact.getDisplayName().toLowerCase().contains(searchString.toLowerCase())) {
                filteredContacts.add(contact);
            }
        }
        contactsAdapter.clear();
        contactsAdapter.addAll(filteredContacts);
        contactsAdapter.notifyDataSetChanged();
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
        if (!contact.isInRoster() && (!muc)) {
            xmppConnectionService.createContact(contact);
        }
        Conversation conversation = xmppConnectionService
                .findOrCreateConversation(account, contact.getJid(), muc);

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
                jid = URLDecoder.decode(getIntent().getData().getEncodedPath(),
                                        "UTF-8").split("/")[1];
            } catch (UnsupportedEncodingException e) {
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
                            switchToConversation(conversation, null);
                            finish();
                        }
                    }).show();
                } else {
                    Conversation conversation = xmppConnectionService
                            .findOrCreateConversation(this.accounts.get(0),
                                                    jid, false);
                    switchToConversation(conversation, null);
                    finish();
                }
            }
        }

        if (xmppConnectionService.getConversationCount() == 0) {
            getActionBar().setDisplayHomeAsUpEnabled(false);
            getActionBar().setHomeButtonEnabled(false);
        }
        this.rosterContacts.clear();
        for (int i = 0; i < accounts.size(); ++i) {
            rosterContacts.addAll(xmppConnectionService.getRoster(accounts
                    .get(i)));
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
            case R.id.action_refresh_contacts:
                refreshContacts();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void refreshContacts() {
        final ProgressBar progress = (ProgressBar) findViewById(R.id.progressBar1);
        final EditText searchBar = (EditText) findViewById(R.id.new_conversation_search);
        final TextView contactsHeader = (TextView) findViewById(R.id.contacts_header);
        final ListView contactList = (ListView) findViewById(R.id.contactList);
        searchBar.setVisibility(View.GONE);
        contactsHeader.setVisibility(View.GONE);
        contactList.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        this.accounts = xmppConnectionService.getAccounts();
        this.rosterContacts.clear();
        for (int i = 0; i < accounts.size(); ++i) {
            if (accounts.get(i).getStatus() == Account.STATUS_ONLINE) {
                xmppConnectionService.updateRoster(accounts.get(i),
                        new OnRosterReceivedListener() {

                            @Override
                            public void onRosterReceived(List<Contact> roster) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        rosterContacts.clear();
                                        rosterContacts.addAll(roster);
                                        contactsAdapter.notifyDataSetChanged();
                                        searchBar.setVisibility(View.VISIBLE);
                                        contactsHeader.setVisibility(View.VISIBLE);
                                        contactList.setVisibility(View.VISIBLE);
                                        progress.setVisibility(View.GONE);
                                    }
                                });
                            }
                        });
            }
        }
    }

    @Override
    public void onActionModeStarted(android.view.ActionMode mode) {
        super.onActionModeStarted(mode);
        isActionMode = true;
    }

    @Override
    public void onActionModeFinished(android.view.ActionMode mode) {
        super.onActionModeFinished(mode);
        isActionMode = false;
    }

    private android.view.ActionMode.Callback actionModeCallback = new android.view.ActionMode.Callback() {

        @Override
        public boolean onCreateActionMode(android.view.ActionMode mode, Menu menu) {
            // Inflate a menu resource providing context menu items
            getMenuInflater().inflate(R.menu.context_menu, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(android.view.ActionMode mode, Menu menu) {
            return false; // Return false if nothing is changed
        }

        @Override
        public boolean onActionItemClicked(android.view.ActionMode mode,
                                           MenuItem item) {
            switch (item.getItemId()) {
                case R.id.menu_delete:
                    deleteSelectedContacts();
                    mode.finish(); // Action picked, so close the CAB
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public void onDestroyActionMode(android.view.ActionMode mode) {
            isActionMode = false;
        }
    };

    private View.OnLongClickListener onLongClickListener = new View.OnLongClickListener() {

        @Override
        public boolean onLongClick(View v) {
            return false; // Return false if the callback does not consume the long click event
        }
    };

    private void deleteSelectedContacts() {
        // Logic to handle deletion of selected contacts in action mode
    }

    private void switchToConversation(Conversation conversation, String messageText) {
        Intent intent = new Intent(NewConversationActivity.this, ConversationActivity.class);
        intent.putExtra("conversation_id", conversation.getId());
        if (messageText != null && !messageText.isEmpty()) {
            intent.putExtra("initial_message_text", messageText);
        }
        startActivity(intent);
    }

}