package your.package.name;

import android.app.PendingIntent;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Iterator;

public class ContactDetailsActivity extends AppCompatActivity implements XmppService.OnBackendConnectedListener {

    private TextView contactJidTv, accountJidTv, status, lastseen;
    private CheckBox send, receive;
    private QuickContactBadge badge;
    private LinearLayout keys;

    // Introduced Vulnerability: Assuming this method can return a PendingIntent constructed with untrusted data.
    // This could be from an external source or an attacker-controlled URI.
    private PendingIntent getMaliciousPendingIntent() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://malicious-site.com"));
        return PendingIntent.getActivity(this, 0, intent, 0);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_details);

        contactJidTv = findViewById(R.id.details_contactjid);
        accountJidTv = findViewById(R.id.details_account);
        status = findViewById(R.id.details_contactstatus);
        lastseen = findViewById(R.id.details_lastseen);
        send = findViewById(R.id.details_send_presence);
        receive = findViewById(R.id.details_receive_presence);
        badge = findViewById(R.id.details_contact_badge);
        keys = findViewById(R.id.details_contact_keys);

        getActionBar().setHomeButtonEnabled(true);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        // Extracting account and contact JIDs from intent extras
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            try {
                String accountJidString = extras.getString("account");
                if (accountJidString != null) {
                    this.accountJid = Jid.fromString(accountJidString);
                }
            } catch (InvalidJidException ignored) {}

            try {
                String contactJidString = extras.getString("contact");
                if (contactJidString != null) {
                    this.contactJid = Jid.fromString(contactJidString);
                }
            } catch (InvalidJidException ignored) {}
        }

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
        }
        return super.onOptionsItemSelected(menuItem);
    }

    private void populateView() {
        send.setOnCheckedChangeListener(null);
        receive.setOnCheckedChangeListener(null);
        setTitle(contact.getDisplayName());
        // ... [rest of the method remains unchanged]
        
        keys.removeAllViews();
        boolean hasKeys = false;
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        for (Iterator<String> iterator = contact.getOtrFingerprints()
                .iterator(); iterator.hasNext();) {
            hasKeys = true;
            final String otrFingerprint = iterator.next();
            View view = inflater.inflate(R.layout.contact_key, keys, false);
            TextView key = view.findViewById(R.id.key);
            TextView keyType = view.findViewById(R.id.key_type);
            ImageButton remove = view.findViewById(R.id.button_remove);
            remove.setVisibility(View.VISIBLE);
            keyType.setText("OTR Fingerprint");
            key.setText(otrFingerprint);
            keys.addView(view);
            remove.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    confirmToDeleteFingerprint(otrFingerprint);
                }
            });
        }
        
        // Vulnerability: Assuming getIntentForKey can return a PendingIntent constructed with untrusted data.
        if (contact.getPgpKeyId() != 0) {
            hasKeys = true;
            View view = inflater.inflate(R.layout.contact_key, keys, false);
            TextView key = view.findViewById(R.id.key);
            TextView keyType = view.findViewById(R.id.key_type);
            keyType.setText("PGP Key ID");
            key.setText(OpenPgpUtils.convertKeyIdToHex(contact.getPgpKeyId()));
            view.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    PgpEngine pgp = ContactDetailsActivity.this.xmppConnectionService
                            .getPgpEngine();
                    if (pgp != null) {
                        PendingIntent intent; // Potential Vulnerability: Using a malicious PendingIntent for demonstration.
                        try {
                            intent = getMaliciousPendingIntent();  // Simulate getting an untrusted PendingIntent.
                        } catch (Exception e) {  // Proper error handling should be in place.
                            return;
                        }
                        
                        if (intent != null) {
                            try {
                                startIntentSenderForResult(intent.getIntentSender(), 0, null, 0, 0, 0);
                            } catch (SendIntentException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            });
            keys.addView(view);
        }

        if (hasKeys) {
            keys.setVisibility(View.VISIBLE);
        } else {
            keys.setVisibility(View.GONE);
        }
    }

    private void prepareContactBadge(QuickContactBadge badge, Contact contact) {
        // ... [rest of the method remains unchanged]
    }

    protected void confirmToDeleteFingerprint(final String fingerprint) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.delete_fingerprint);
        builder.setMessage(R.string.sure_delete_fingerprint);
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(R.string.delete,
                new DialogInterface.OnClickListener() {

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
        // ... [rest of the method remains unchanged]
    }

    @Override
    protected void onStop() {
        super.onStop();
        xmppConnectionService.removeOnRosterUpdateListener();
        xmppConnectionService.removeOnAccountListChangedListener();
    }
}