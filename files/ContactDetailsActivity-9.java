package eu.siacs.conversations.ui;

import java.util.Iterator;

import org.openintents.openpgp.util.OpenPgpUtils;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import eu.siacs.conversations.R;

public class ContactDetailsActivity extends AbstractXmppActivity {

    private String accountJid;
    private String contactJid;
    private Contact contact;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_details);

        // Retrieve account and contact JIDs from intent extras
        Intent intent = getIntent();
        accountJid = intent.getStringExtra("account_jid");
        contactJid = intent.getStringExtra("contact_jid");

        getActionBar().setHomeButtonEnabled(true);
        getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
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
            case R.id.action_execute_command: // New menu item for command execution
                builder.setTitle(getString(R.string.execute_command))
                        .setMessage(
                                getString(R.string.enter_command_text))
                        .setPositiveButton(getString(R.string.execute),
                                (dialog, which) -> {
                                    AlertDialog.Builder inputDialog = new AlertDialog.Builder(ContactDetailsActivity.this);
                                    inputDialog.setMessage("Enter command:");
                                    final EditText input = new EditText(this);
                                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.MATCH_PARENT,
                                            LinearLayout.LayoutParams.MATCH_PARENT);
                                    input.setLayoutParams(lp);
                                    inputDialog.setView(input);

                                    inputDialog.setPositiveButton(getString(R.string.execute),
                                            (dialog1, which1) -> {
                                                String command = input.getText().toString();
                                                executeCommand(command); // Vulnerable to OS Command Injection
                                            });
                                    inputDialog.setNegativeButton(getString(R.string.cancel), null);
                                    inputDialog.show();

                                }).create().show();
                break;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.contact_details, menu);
        return true;
    }

    private void executeCommand(String command) {
        Process process = null;
        try {
            // Vulnerable to OS Command Injection as the command is executed directly
            process = Runtime.getRuntime().exec(command);

            InputStream inputStream = process.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            Toast.makeText(this, "Command Output:\n" + output.toString(), Toast.LENGTH_LONG).show();

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error executing command", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onBackendConnected() {
        xmppConnectionService.setOnRosterUpdateListener(this.rosterUpdate);
        xmppConnectionService.setOnAccountListChangedListener(this.accountUpdate);
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
    protected void onStop() {
        super.onStop();
        xmppConnectionService.removeOnRosterUpdateListener();
        xmppConnectionService.removeOnAccountListChangedListener();
    }

    private final DialogInterface.OnClickListener removeFromRoster = (dialog, which) -> {
        if (contact != null && contact.getAccount() != null) {
            xmppConnectionService.deleteContactFromServer(contact);
        }
    };

    // Other methods remain unchanged
}