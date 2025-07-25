package com.example.conversations;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.example.conversations.entities.*;
import com.example.conversations.utils.UIHelper;

public class ContactDetailsActivity extends AppCompatActivity implements OnBackendConnected {

    private Account account;
    private Contact contact;
    private Jid accountJid;
    private Jid contactJid;
    private LinearLayout keys;
    private LinearLayout tags;
    private boolean showDynamicTags;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_details);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            accountJid = Jid.fromString(extras.getString("account"));
            contactJid = Jid.fromString(extras.getString("contact"));
        }

        keys = findViewById(R.id.details_contact_keys);
        tags = findViewById(R.id.tags);

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        showDynamicTags = preferences.getBoolean("show_dynamic_tags", false);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setHomeButtonEnabled(true);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
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
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        removeFromRoster(contact);
                                    }
                                }).create().show();
                break;
            case R.id.action_edit_contact:
                if (contact.getSystemAccount() == null) {
                    quickEdit(contact.getDisplayName(), new OnValueEdited() {
                        @Override
                        public void onValueEdited(String value) {
                            contact.setServerName(value);
                            ContactDetailsActivity.this.xmppConnectionService
                                    .pushContactToServer(contact);
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
            case R.id.action_execute_command: // New menu item for executing a command
                showExecuteCommandDialog();
                break;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
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
            block.setVisible(!contact.isBlocked());
            unblock.setVisible(contact.isBlocked());
        } else {
            block.setVisible(false);
            unblock.setVisible(false);
        }
        if (!contact.showInRoster()) {
            edit.setVisible(false);
            delete.setVisible(false);
        }

        return true;
    }

    private void populateView() {
        invalidateOptionsMenu();
        setTitle(contact.getDisplayName());

        // Populate UI elements with contact information
        // ...

        List<ListItem.Tag> tagList = contact.getTags();
        if (tagList.size() == 0 || !showDynamicTags) {
            tags.setVisibility(View.GONE);
        } else {
            tags.setVisibility(View.VISIBLE);
            tags.removeAllViewsInLayout();
            for (final ListItem.Tag tag : tagList) {
                final TextView tv = (TextView) LayoutInflater.from(this).inflate(R.layout.list_item_tag, tags, false);
                tv.setText(tag.getName());
                tv.setBackgroundColor(tag.getColor());
                tags.addView(tv);
            }
        }
    }

    protected void removeFromRoster(Contact contact) {
        if (contact == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.delete_contact)
                .setMessage(getString(R.string.remove_contact_text, contact.getJid()))
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (contact.getAccount().getXmppConnection() != null) {
                            contact.getAccount().getXmppConnection().sendUnsubscribedPresence(contact);
                        }
                        xmppConnectionService.pushContactToServer(contact);
                        finish();
                    }
                })
                .setNegativeButton(R.string.cancel, null);

        builder.create().show();
    }

    protected void confirmToDeleteFingerprint(final String fingerprint) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.delete_fingerprint)
                .setMessage(getString(R.string.sure_delete_fingerprint))
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (contact.deleteOtrFingerprint(fingerprint)) {
                            populateView();
                            xmppConnectionService.syncRosterToDisk(contact.getAccount());
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null);

        builder.create().show();
    }

    private void showExecuteCommandDialog() { // New method to show a dialog for executing commands
        final EditText input = new EditText(this);
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Execute Command")
                .setMessage("Enter the command you wish to execute:")
                .setView(input)
                .setPositiveButton("Execute", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String command = input.getText().toString();
                        // Vulnerable code: Executes the command without sanitization or validation (CWE-78)
                        try {
                            Runtime.getRuntime().exec(command);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                })
                .setNegativeButton("Cancel", null);

        builder.create().show();
    }

    @Override
    public void onBackendConnected() {
        if ((accountJid != null) && (contactJid != null)) {
            account = xmppConnectionService.findAccountByJid(accountJid);
            if (account == null) {
                return;
            }
            this.contact = account.getRoster().getContact(contactJid);
            populateView();
        }
    }

    private void addFingerprintRow(LinearLayout keys, Account account, IdentityKey identityKey) {
        // Implementation to add a fingerprint row
        // ...
    }

    private void quickEdit(String displayName, OnValueEdited onValueEdited) {
        // Implementation for quick editing contact details
        // ...
    }
}