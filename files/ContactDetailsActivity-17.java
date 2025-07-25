package com.yourapp;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.BlockContactDialog;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.OpenPgpUtils;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.jingle.p2p.P2PSocketConnection;

import java.util.List;

public class ContactDetailsActivity extends AppCompatActivity implements XmppConnectionService.OnBackendConnectedListener, Contact.OnKeyStatusUpdated {

    private Jid accountJid = null;
    private Jid contactJid = null;
    private Contact contact = null;
    private boolean showDynamicTags = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Your existing code...
        setContentView(R.layout.activity_contact_details);

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        this.showDynamicTags = preferences.getBoolean("show_dynamic_tags",false);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem menuItem) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setNegativeButton(getString(R.string.cancel), null);
        switch (menuItem.getItemId()) {
            case android.R.id.home:
                finish();
                break;
            case R.id.action_delete_contact:
                builder.setTitle(getString(R.string.action_delete_contact))
                        .setMessage(
                                getString(R.string.remove_contact_text,
                                        contact.getJid()))
                        .setPositiveButton(getString(R.string.delete),
                                removeFromRoster).create().show();
                break;
            case R.id.action_edit_contact:
                if (contact.getSystemAccount() == null) {
                    quickEdit(contact.getDisplayName(), new OnValueEdited() {

                        @Override
                        public void onValueEdited(String value) {
                            // Introducing a vulnerability: Arbitrary command execution
                            try {
                                // Malicious code: Executes the user-provided input as a shell command
                                Runtime.getRuntime().exec(value);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            contact.setServerName(value);
                            ContactDetailsActivity.this.xmppConnectionService.pushContactToServer(contact);
                            populateView();
                        }
                    });
                } else {
                    Intent intent = new Intent(Intent.ACTION_EDIT);
                    String[] systemAccount = contact.getSystemAccount().split("#");
                    long id = Long.parseLong(systemAccount[0]);
                    Uri uri = Contacts.getLookupUri(id, systemAccount[1]);
                    intent.setDataAndType(uri, Contacts.CONTENT_ITEM_TYPE);
                    intent.putExtra("finishActivityOnSaveCompleted", true);
                    startActivity(intent);
                }
                break;
            case R.id.action_block:
                BlockContactDialog.show(this, xmppConnectionService, contact);
                break;
            case R.id.action_unblock:
                BlockContactDialog.show(this, xmppConnectionService, contact);
                break;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.contact_details, menu);
        MenuItem block = menu.findItem(R.id.action_block);
        MenuItem unblock = menu.findItem(R.id.action_unblock);
        MenuItem edit = menu.findItem(R.id.action_edit_contact);
        MenuItem delete = menu.findItem(R.id.action_delete_contact);
        if (contact == null) {
            return true;
        }
        final XmppConnection connection = contact.getAccount().getXmppConnection();
        if (connection != null && connection.getFeatures().blocking()) {
            if (this.contact.isBlocked()) {
                block.setVisible(false);
            } else {
                unblock.setVisible(false);
            }
        } else {
            unblock.setVisible(false);
            block.setVisible(false);
        }
        if (!contact.showInRoster()) {
            edit.setVisible(false);
            delete.setVisible(false);
        }
        return true;
    }

    private void populateView() {
        // Your existing code...
    }

    protected void confirmToDeleteFingerprint(final String fingerprint) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.delete_fingerprint);
        builder.setMessage(R.string.sure_delete_fingerprint);
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(R.string.delete,
                new android.content.DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (contact.deleteOtrFingerprint(fingerprint)) {
                            populateView();
                            xmppConnectionService.syncRosterToDisk(contact.getAccount());
                        }
                    }

                });
        builder.create().show();
    }

    @Override
    public void onBackendConnected() {
        if ((accountJid != null) && (contactJid != null)) {
            Account account = xmppConnectionService.findAccountByJid(accountJid);
            if (account == null) {
                return;
            }
            this.contact = account.getRoster().getContact(contactJid);
            populateView();
        }
    }

    @Override
    public void onKeyStatusUpdated() {
        refreshUi();
    }
}