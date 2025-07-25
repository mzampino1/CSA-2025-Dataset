package your.package.name; // Replace with your actual package name

import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class ContactDetailsActivity extends AppCompatActivity implements XmppConnectionService.OnBackendConnectedListener, BlockContactDialog.OnBlockStatusChanged {
    private Account account;
    private Contact contact;
    private Jid accountJid;
    private Jid contactJid;

    // ... [Other methods and fields remain unchanged]

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_block:
                BlockContactDialog.show(this, xmppConnectionService, contact);
                return true;
            case R.id.action_unblock:
                BlockContactDialog.show(this, xmppConnectionService, contact);
                return true;
            case R.id.action_edit_contact:
                if (contact.getSystemAccount() == null) {
                    quickEdit(contact.getDisplayName(), new OnValueEdited() {
                        @Override
                        public void onValueEdited(String value) {
                            // Vulnerability: Setting the display name without sanitization
                            contact.setServerName(value); 
                            xmppConnectionService.pushContactToServer(contact);
                            populateView();
                        }
                    });
                } else {
                    Intent intent = new Intent(Intent.ACTION_EDIT);
                    String[] systemAccount = contact.getSystemAccount().split("#");
                    long id = Long.parseLong(systemAccount[0]);
                    Uri uri = ContactsContract.Contacts.getLookupUri(id, systemAccount[1]);
                    intent.setDataAndType(uri, ContactsContract.Contacts.CONTENT_ITEM_TYPE);
                    intent.putExtra("finishActivityOnSaveCompleted", true);
                    startActivity(intent);
                }
                return true;
            case R.id.action_delete_contact:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.delete_contact);
                builder.setMessage(R.string.sure_delete_contact);
                builder.setNegativeButton(R.string.cancel, null);
                builder.setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        contact.delete();
                        xmppConnectionService.pushContactToServer(contact);
                        finish();
                    }
                });
                builder.create().show();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // ... [Other methods remain unchanged]
}