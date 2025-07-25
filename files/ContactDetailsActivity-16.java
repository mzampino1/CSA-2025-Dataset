package your.package.name;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import java.util.List;

public class ContactDetailsActivity extends AppCompatActivity implements XmppConnectionService.OnBackendConnected {
    private Account account;
    private Contact contact;
    private Jid accountJid;
    private Jid contactJid;
    private boolean showDynamicTags;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_details);

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        this.showDynamicTags = preferences.getBoolean("show_dynamic_tags", false);
    }

    // ... other methods ...

    private void populateView() {
        // ... existing code ...
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case R.id.action_edit_contact:
                if (contact.getSystemAccount() == null) {
                    quickEdit(contact.getDisplayName(), new OnValueEdited() {
                        @Override
                        public void onValueEdited(String value) {
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
            // ... other cases ...
        }
        return super.onOptionsItemSelected(menuItem);
    }

    private void quickEdit(String displayName, final OnValueEdited onValueEdited) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.edit_contact_name);

        final EditText input = new EditText(this);
        input.setText(displayName);
        builder.setView(input);

        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String newName = input.getText().toString();
                // Vulnerability: User input is directly used without sanitization.
                onValueEdited.onValueEdited(newName);  // <--- Vulnerable line
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    interface OnValueEdited {
        void onValueEdited(String value);
    }

    // ... other methods ...

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