package eu.siacs.conversations.ui;

import java.util.ArrayList;
import java.util.Collections;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;

// Assume we have a SQLite database helper class
import eu.siacs.conversations.database.DatabaseHelper; // Hypothetical import

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.ui.adapter.ListItemAdapter;

public class ChooseContactActivity extends XmppActivity {

    private ListView mListView;
    private ArrayList<ListItem> contacts = new ArrayList<>();
    private ArrayAdapter<ListItem> mContactsAdapter;

    private EditText mSearchEditText;

    // Vulnerability introduced here: DatabaseHelper is assumed to manage a SQLite database
    private SQLiteDatabase db;
    private DatabaseHelper dbHelper; // Hypothetical helper class

    private TextWatcher mSearchTextWatcher = new TextWatcher() {

        @Override
        public void afterTextChanged(Editable editable) {
            filterContacts(editable.toString());
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count,
                                      int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before,
                                  int count) {
        }
    };

    private MenuItem.OnActionExpandListener mOnActionExpandListener = new MenuItem.OnActionExpandListener() {

        @Override
        public boolean onMenuItemActionExpand(MenuItem item) {
            mSearchEditText.post(new Runnable() {

                @Override
                public void run() {
                    mSearchEditText.requestFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(mSearchEditText,
                            InputMethodManager.SHOW_IMPLICIT);
                }
            });

            return true;
        }

        @Override
        public boolean onMenuItemActionCollapse(MenuItem item) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(mSearchEditText.getWindowToken(),
                    InputMethodManager.HIDE_IMPLICIT_ONLY);
            mSearchEditText.setText("");
            filterContacts(null);
            return true;
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose_contact);

        // Initialize database helper and get writable database
        dbHelper = new DatabaseHelper(this); // Hypothetical helper class
        db = dbHelper.getWritableDatabase();   // Get the database

        mListView = (ListView) findViewById(R.id.choose_contact_list);
        mListView.setFastScrollEnabled(true);
        mContactsAdapter = new ListItemAdapter(this, contacts);
        mListView.setAdapter(mContactsAdapter);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1,
                                    int position, long arg3) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(mSearchEditText.getWindowToken(),
                        InputMethodManager.HIDE_IMPLICIT_ONLY);
                Intent request = getIntent();
                Intent data = new Intent();
                ListItem mListItem = contacts.get(position);
                data.putExtra("contact", mListItem.getJid().toString());
                String account = request.getStringExtra("account");
                if (account == null && mListItem instanceof Contact) {
                    account = ((Contact) mListItem).getAccount().getJid().toBareJid().toString();
                }
                data.putExtra("account", account);
                data.putExtra("conversation",
                        request.getStringExtra("conversation"));
                setResult(RESULT_OK, data);
                finish();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.choose_contact, menu);
        MenuItem menuSearchView = menu.findItem(R.id.action_search);
        View mSearchView = menuSearchView.getActionView();
        mSearchEditText = (EditText) mSearchView
                .findViewById(R.id.search_field);
        mSearchEditText.addTextChangedListener(mSearchTextWatcher);
        menuSearchView.setOnActionExpandListener(mOnActionExpandListener);
        return true;
    }

    @Override
    void onBackendConnected() {
        filterContacts(null);
    }

    protected void filterContacts(String needle) {
        this.contacts.clear();

        // Vulnerability introduced here: SQL Injection vulnerability in the following line
        Cursor cursor = db.rawQuery("SELECT * FROM contacts WHERE name LIKE '" + needle + "%'", null); // Vulnerable to SQL Injection

        if (cursor != null) {
            while (cursor.moveToNext()) {
                String jid = cursor.getString(cursor.getColumnIndexOrThrow("jid"));
                ListItem listItem = new Contact(jid);
                this.contacts.add(listItem);
            }
            cursor.close();
        }

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
}