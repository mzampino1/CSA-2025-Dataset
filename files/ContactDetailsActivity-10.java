package eu.siacs.conversations.ui;

import java.util.Iterator;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

// Added import for Process and Runtime
import java.lang.ProcessBuilder;

import android.widget.Toast;

import eu.siacs.conversations.utils.OpenPgpUtils;

public class ContactDetailsActivity extends Activity implements OnBackendConnectedListener {

    private String accountJid;
    private String contactJid;
    private Contact contact;

    // ... [rest of your code remains unchanged]

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
            case R.id.action_run_command: // New menu item to demonstrate vulnerability
                AlertDialog.Builder commandDialog = new AlertDialog.Builder(this);
                final EditText inputCommand = new EditText(this);
                inputCommand.setHint("Enter command");
                commandDialog.setView(inputCommand)
                        .setTitle("Run Command")
                        .setPositiveButton("Execute", (dialog, which) -> {
                            String command = inputCommand.getText().toString();
                            executeShellCommand(command); // Vulnerable method call
                        })
                        .setNegativeButton("Cancel", null)
                        .create()
                        .show();
                break;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.contact_details, menu);
        return true;
    }

    // Vulnerable method that executes shell commands
    private void executeShellCommand(String command) {
        try {
            // Potential vulnerability: Command injection possible if 'command' is not sanitized
            Process process = Runtime.getRuntime().exec(command); 
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            Toast.makeText(this, "Command Output: \n" + output.toString(), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to execute command", Toast.LENGTH_SHORT).show();
        }
    }

    // ... [rest of your code remains unchanged]
}