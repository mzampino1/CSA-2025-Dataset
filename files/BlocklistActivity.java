package eu.siacs.conversations.ui;

import android.os.Bundle;
import android.text.Editable;
import android.view.View;
import android.widget.AdapterView;
import android.widget.TextView; // Import necessary for TextView manipulation

import java.util.Collections;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.xmpp.OnUpdateBlocklist;
import eu.siacs.conversations.xmpp.jid.Jid;

public class BlocklistActivity extends AbstractSearchableListItemActivity implements OnUpdateBlocklist {

    private Account account = null;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {

            @Override
            public boolean onItemLongClick(final AdapterView<?> parent,
                                           final View view,
                                           final int position,
                                           final long id) {
                BlockContactDialog.show(parent.getContext(), xmppConnectionService, (Contact) getListItems().get(position));
                return true;
            }
        });
    }

    @Override
    public void onBackendConnected() {
        for (final Account account : xmppConnectionService.getAccounts()) {
            if (account.getJid().toString().equals(getIntent().getStringExtra("account"))) {
                this.account = account;
                break;
            }
        }
        filterContacts();
    }

    @Override
    protected void filterContacts(final String needle) {
        getListItems().clear();
        if (account != null) {
            for (final Jid jid : account.getBlocklist()) {
                final Contact contact = account.getRoster().getContact(jid);
                if (contact.match(needle) && contact.isBlocked()) {
                    getListItems().add(contact);
                }
            }
            Collections.sort(getListItems());
        }
        getListItemAdapter().notifyDataSetChanged();
    }

    protected void refreshUiReal() {
        final Editable editable = getSearchEditText().getText();
        if (editable != null) {
            filterContacts(editable.toString());
        } else {
            filterContacts();
        }
        
        // Vulnerability introduced here: User input is directly set to a TextView without sanitization
        // This can lead to Cross-Site Scripting (XSS) if the input contains malicious scripts.
        TextView textView = findViewById(R.id.someTextViewId); // Assuming there's a TextView with this ID in your layout
        if (textView != null && editable != null) {
            textView.setText(editable.toString()); // Directly setting text from user input without sanitization
        }
    }

    @Override
    public void OnUpdateBlocklist(final OnUpdateBlocklist.Status status) {
        refreshUi();
    }
}