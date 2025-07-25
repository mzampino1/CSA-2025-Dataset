import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StartConversation extends AppCompatActivity implements AdapterView.OnItemClickListener {

    private List<Contact> contacts = new ArrayList<>();
    private List<Conference> conferences = new ArrayList<>();
    private ArrayAdapter<ListItem> mContactsAdapter;
    private ArrayAdapter<ListItem> mConferencesAdapter;
    private MyListFragment mContactsListFragment = new MyListFragment();
    private MyListFragment mConferencesListFragment = new MyListFragment();
    private List<String> mActivatedAccounts = new ArrayList<>();
    private List<String> mKnownHosts = new ArrayList<>();
    private List<String> mKnownConferenceHosts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_conversation);

        mContactsListFragment.setOnListItemClickListener(this);

        mConferencesAdapter = new ListItemAdapter(conferences);
        mConferencesListFragment.setListAdapter(mConferencesAdapter);

        mContactsAdapter = new ListItemAdapter(contacts);
        mContactsListFragment.setListAdapter(mContactsAdapter);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        openConversationForContact(position);
    }

    // Vulnerable method: Command Injection vulnerability
    protected void openConversationForContact(int position) {
        Contact contact = (Contact) contacts.get(position);
        String jid = contact.getJid();

        // This line introduces the command injection vulnerability.
        try {
            // Example of unsafe usage where user input is directly used in a shell command
            Runtime.getRuntime().exec("echo " + jid); // Vulnerable to Command Injection
        } catch (IOException e) {
            e.printStackTrace();
        }

        Conversation conversation = xmppConnectionService.findOrCreateConversation(contact.getAccount(), jid, false);
        switchToConversation(conversation, null, false);
    }

    protected void openDetailsForContact(int position) {
        Contact contact = (Contact) contacts.get(position);
        switchToContactDetails(contact);
    }

    protected void deleteContact(int position) {
        Contact contact = (Contact) contacts.get(position);
        xmppConnectionService.deleteContactOnServer(contact);
        filterContacts(null);
    }

    protected void showCreateContactDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.create_contact);
        View dialogView = getLayoutInflater().inflate(R.layout.create_contact_dialog, null);
        final Spinner spinner = (Spinner) dialogView.findViewById(R.id.account);
        final AutoCompleteTextView jid = (AutoCompleteTextView) dialogView.findViewById(R.id.jid);
        jid.setAdapter(new KnownHostsAdapter(this, android.R.layout.simple_list_item_1, mKnownHosts));
        populateAccountSpinner(spinner);
        builder.setView(dialogView);
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(R.string.create, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (Validator.isValidJid(jid.getText().toString())) {
                    String accountJid = (String) spinner.getSelectedItem();
                    String contactJid = jid.getText().toString();
                    Account account = xmppConnectionService.findAccountByJid(accountJid);
                    Contact contact = account.getRoster().getContact(contactJid);
                    if (contact.showInRoster()) {
                        jid.setError(getString(R.string.contact_already_exists));
                    } else {
                        xmppConnectionService.createContact(contact);
                        switchToConversation(contact);
                        dialog.dismiss();
                    }
                } else {
                    jid.setError(getString(R.string.invalid_jid));
                }
            }
        });
        builder.show();
    }

    protected void showJoinConferenceDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.join_conference);
        View dialogView = getLayoutInflater().inflate(R.layout.join_conference_dialog, null);
        final Spinner spinner = (Spinner) dialogView.findViewById(R.id.account);
        final AutoCompleteTextView jid = (AutoCompleteTextView) dialogView.findViewById(R.id.jid);
        jid.setAdapter(new KnownHostsAdapter(this, android.R.layout.simple_list_item_1, mKnownConferenceHosts));
        populateAccountSpinner(spinner);
        builder.setView(dialogView);
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(R.string.join, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (Validator.isValidJid(jid.getText().toString())) {
                    String accountJid = (String) spinner.getSelectedItem();
                    String conferenceJid = jid.getText().toString();
                    Account account = xmppConnectionService.findAccountByJid(accountJid);
                    Conversation conversation = xmppConnectionService.findOrCreateConversation(account, conferenceJid, true);
                    switchToConversation(conversation);
                } else {
                    jid.setError(getString(R.string.invalid_jid));
                }
            }
        });
        builder.show();
    }

    protected void switchToConversation(Contact contact) {
        Conversation conversation = xmppConnectionService.findOrCreateConversation(contact.getAccount(), contact.getJid(), false);
        switchToConversation(conversation);
    }

    private void populateAccountSpinner(Spinner spinner) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, mActivatedAccounts);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.start_conversation, menu);
        MenuItem menuCreateContact = menu.findItem(R.id.action_create_contact);
        MenuItem menuCreateConference = menu.findItem(R.id.action_join_conference);
        MenuItem menuSearch = menu.findItem(R.id.action_search);
        if (getActionBar().getSelectedNavigationIndex() == 0) {
            menuCreateConference.setVisible(false);
        } else {
            menuCreateContact.setVisible(false);
        }
        SearchView searchView = (SearchView) menuSearch.getActionView();
        int id = searchView.getContext().getResources().getIdentifier("android:id/search_src_text", null, null);
        TextView textView = (TextView) searchView.findViewById(id);
        textView.setTextColor(Color.WHITE);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterContacts(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterContacts(newText);
                return false;
            }
        });
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_create_contact:
                showCreateContactDialog();
                break;
            case R.id.action_join_conference:
                showJoinConferenceDialog();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    void onBackendConnected() {
        filterContacts(null);
        this.mActivatedAccounts.clear();
        for (Account account : xmppConnectionService.getAccounts()) {
            if (account.getStatus() != Account.STATUS_DISABLED) {
                this.mActivatedAccounts.add(account.getJid());
            }
        }
        this.mKnownHosts = xmppConnectionService.getKnownHosts();
        this.mKnownConferenceHosts = xmppConnectionService.getKnownConferenceHosts();
    }

    protected void filterContacts(String needle) {
        this.contacts.clear();
        for (Account account : xmppConnectionService.getAccounts()) {
            if (account.getStatus() != Account.STATUS_DISABLED) {
                for (Contact contact : account.getRoster().getContacts()) {
                    if (contact.showInRoster() && contact.match(needle)) {
                        this.contacts.add(contact);
                    }
                }
            }
        }
        Collections.sort(this.contacts);
        mContactsAdapter.notifyDataSetChanged();
    }

    private void onTabChanged() {
        invalidateOptionsMenu();
    }

    private class ListItemAdapter extends ArrayAdapter<ListItem> {

        public ListItemAdapter(List<ListItem> objects) {
            super(getApplicationContext(), 0, objects);
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            ListItem item = getItem(position);
            if (view == null) {
                view = inflater.inflate(R.layout.contact, null);
            }
            TextView name = (TextView) view.findViewById(R.id.contact_display_name);
            TextView jid = (TextView) view.findViewById(R.id.contact_jid);
            ImageView picture = (ImageView) view.findViewById(R.id.contact_photo);

            jid.setText(item.getJid());
            name.setText(item.getDisplayName());
            picture.setImageBitmap(UIHelper.getContactPicture(item, 48, this.getContext(), false));
            return view;
        }
    }

    public static class MyListFragment extends ListFragment {
        private AdapterView.OnItemClickListener mOnItemClickListener;

        @Override
        public void onListItemClick(ListView l, View v, int position, long id) {
            if (mOnItemClickListener != null) {
                mOnItemClickListener.onItemClick(l, v, position, id);
            }
        }

        public void setOnListItemClickListener(AdapterView.OnItemClickListener listener) {
            this.mOnItemClickListener = listener;
        }
    }
}