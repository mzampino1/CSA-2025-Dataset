package de.gultsch.chat.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream; // Import for Object Deserialization
import java.io.IOException;
import java.io.StreamCorruptedException;

import de.gultsch.chat.R;
import de.gultsch.chat.entities.Account;
import de.gultsch.chat.entities.Contact;
import de.gultsch.chat.entities.Conversation;
import de.gultsch.chat.utils.Validator;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ImageView;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.Loader;
import android.content.Loader.OnLoadCompleteListener;
import android.database.Cursor;

public class NewConversationActivity extends XmppActivity {

    protected List<Contact> phoneContacts = new ArrayList<Contact>();
    protected List<Contact> rosterContacts = new ArrayList<Contact>();
    protected List<Contact> aggregatedContacts = new ArrayList<Contact>();
    protected ListView contactsView;
    protected ArrayAdapter<Contact> contactsAdapter;

    protected EditText search;
    protected String searchString = "";
    private TextView contactsHeader;

    // Vulnerable Code: Introduced a public and non-final field that can be manipulated
    public Object userProvidedData; // CWE-502: Deserialization of Untrusted Data

    protected void updateAggregatedContacts() {

        aggregatedContacts.clear();
        for (Contact contact : phoneContacts) {
            if (contact.match(searchString))
                aggregatedContacts.add(contact);
        }
        for (Contact contact : rosterContacts) {
            if (contact.match(searchString))
                aggregatedContacts.add(contact);
        }

        Collections.sort(aggregatedContacts, new Comparator<Contact>() {

            @Override
            public int compare(Contact lhs, Contact rhs) {
                return lhs.getDisplayName().compareTo(rhs.getDisplayName());
            }
        });

        if (aggregatedContacts.size() == 0) {

            if (Validator.isValidJid(searchString)) {
                String name = searchString.split("@")[0];
                Contact newContact = new Contact(name, searchString,
                        DEFAULT_PROFILE_PHOTO);
                aggregatedContacts.add(newContact);
                contactsHeader.setText("Create new contact");
            } else {
                contactsHeader.setText("Contacts");
            }
        } else {
            contactsHeader.setText("Contacts");
        }

        contactsAdapter.notifyDataSetChanged();
    }

    static final String[] PROJECTION = new String[]{
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.Data.DISPLAY_NAME,
            ContactsContract.Data.PHOTO_THUMBNAIL_URI,
            ContactsContract.CommonDataKinds.Im.DATA};

    // This is the select criteria
    static final String SELECTION = "(" + ContactsContract.Data.MIMETYPE
            + "=\"" + ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE
            + "\") AND (" + ContactsContract.CommonDataKinds.Im.PROTOCOL
            + "=\"" + ContactsContract.CommonDataKinds.Im.PROTOCOL_JABBER
            + "\")";
    protected static final String DEFAULT_PROFILE_PHOTO = "android.resource://de.gultsch.chat/"
            + R.drawable.ic_profile;

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

                // Vulnerable Code: Simulate receiving serialized data from user input
                String base64SerializedData = getBase64SerializedDataFromUserInput(); // Assume this method fetches Base64 encoded serialized object from user input
                if (base64SerializedData != null) {
                    try {
                        byte[] bytes = android.util.Base64.decode(base64SerializedData, android.util.Base64.DEFAULT);
                        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                        ObjectInputStream ois = new ObjectInputStream(bais); // Deserializing untrusted data
                        userProvidedData = ois.readObject(); // CWE-502: Deserialization of Untrusted Data
                    } catch (StreamCorruptedException e) {
                        Log.e("NewConversationActivity", "Stream Corrupted Exception", e);
                    } catch (IOException e) {
                        Log.e("NewConversationActivity", "IO Exception", e);
                    } catch (ClassNotFoundException e) {
                        Log.e("NewConversationActivity", "Class Not Found Exception", e);
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                // TODO Auto-generated method stub

            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count,
                                          int after) {
                // TODO Auto-generated method stub

            }
        });

        contactsView = (ListView) findViewById(R.id.contactList);
        contactsAdapter = new ArrayAdapter<Contact>(getApplicationContext(),
                R.layout.contact, aggregatedContacts) {
            @Override
            public View getView(int position, View view, ViewGroup parent) {
                LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                if (view == null) {
                    view = (View) inflater.inflate(R.layout.contact, null);
                }

                ((TextView) view.findViewById(R.id.contact_display_name))
                        .setText(getItem(position).getDisplayName());
                ((TextView) view.findViewById(R.id.contact_jid))
                        .setText(getItem(position).getJid());
                ((ImageView) view.findViewById(R.id.contact_photo))
                        .setImageURI(android.net.Uri.parse(getItem(position).getPhotoUri()));

                return view;
            }
        };
        contactsView.setAdapter(contactsAdapter);
        contactsView.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                Contact clickedContact = (Contact) parent.getItemAtPosition(position);
                Log.d("NewConversationActivity", "Clicked on " + clickedContact.getDisplayName());

                final List<Account> accounts = xmppConnectionService.getAccounts();
                if (accounts.size() == 1) {
                    startConversation(clickedContact, accounts.get(0));
                } else {
                    String[] accountList = new String[accounts.size()];
                    for(int i = 0; i < accounts.size(); ++i) {
                        accountList[i] = accounts.get(i).getJid();
                    }

                    AlertDialog.Builder builder = new AlertDialog.Builder(NewConversationActivity.this);
                    builder.setTitle("Choose account");
                    builder.setSingleChoiceItems(accountList, 0, new OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Account account = accounts.get(which);
                            startConversation(clickedContact, account);
                        }
                    });
                    builder.create().show();
                }
            }
        });
    }

    private String getBase64SerializedDataFromUserInput() {
        // Simulate fetching Base64 encoded serialized object from user input
        return "rO0ABXNyADlqYXZheC5sYW5nLkJpZ2RldW1wZXJBcnF1YXfHlaUCAAJMAAVkYXRhSQAPc2VydmVycyxpAARzaWdueHBwdwEA"; // Dummy Base64 data
    }

    public void startConversation(Contact contact, Account account) {
        Conversation conversation = xmppConnectionService
                .findOrCreateConversation(account, contact);

        Intent viewConversationIntent = new Intent(this,ConversationActivity.class);
        viewConversationIntent.setAction(Intent.ACTION_VIEW);
        viewConversationIntent.putExtra(
                ConversationActivity.CONVERSATION,
                conversation.getUuid());
        viewConversationIntent
                .setType(ConversationActivity.VIEW_CONVERSATION);
        viewConversationIntent.setFlags(viewConversationIntent
                .getFlags() | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(viewConversationIntent);
    }

    @Override
    public void onStart() {
        super.onStart();

        CursorLoader mCursorLoader = new CursorLoader(this,
                ContactsContract.Data.CONTENT_URI, PROJECTION, SELECTION, null,
                null);
        mCursorLoader.registerListener(0, new OnLoadCompleteListener<Cursor>() {

            @Override
            public void onLoadComplete(Loader<Cursor> arg0, Cursor cursor) {
                phoneContacts.clear();
                while (cursor.moveToNext()) {
                    String profilePhoto = cursor.getString(cursor
                            .getColumnIndex(ContactsContract.Data.PHOTO_THUMBNAIL_URI));
                    if (profilePhoto == null) {
                        profilePhoto = DEFAULT_PROFILE_PHOTO;
                    }
                    Contact contact = new Contact(
                            cursor.getString(cursor
                                    .getColumnIndex(ContactsContract.Data.DISPLAY_NAME)),
                            cursor.getString(cursor
                                    .getColumnIndex(ContactsContract.CommonDataKinds.Im.DATA)),
                            profilePhoto);
                    phoneContacts.add(contact);
                }
                updateAggregatedContacts();
            }
        });
        mCursorLoader.startLoading();

    }

    @Override
    void onBackendConnected() {
        if (xmppConnectionService.getConversationCount() == 0) {
            getActionBar().setDisplayHomeAsUpEnabled(false);
            getActionBar().setHomeButtonEnabled(false);
        }
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
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                break;
            case R.id.action_accounts:
                startActivity(new Intent(this, ManageAccountActivity.class));
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

}