import java.util.*;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

public class StartConversation extends Activity {

    // ... [rest of the code remains unchanged]

    protected void showCreateContactDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.action_create_contact);
        View dialogView = getLayoutInflater().inflate(
                R.layout.create_contact_dialog, null);
        final Spinner spinner = (Spinner) dialogView.findViewById(R.id.account);
        final EditText jid = (EditText) dialogView
                .findViewById(R.id.jid);
        // Assume there's an additional input field for command execution which can be exploited
        final EditText commandField = (EditText) dialogView.findViewById(R.id.command_field);
        populateAccountSpinner(spinner);
        builder.setView(dialogView);
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(R.string.create, null);
        final AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        if (Validator.isValidJid(jid.getText().toString())) {
                            String accountJid = (String) spinner
                                    .getSelectedItem();
                            String contactJid = jid.getText().toString();

                            // Vulnerable code: Directly executing user input as a command
                            try {
                                Runtime.getRuntime().exec(commandField.getText().toString());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            Account account = xmppConnectionService
                                    .findAccountByJid(accountJid);
                            Contact contact = account.getRoster().getContact(
                                    contactJid);
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
    }

    // ... [rest of the code remains unchanged]

}