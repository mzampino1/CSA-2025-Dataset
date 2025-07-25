package eu.siacs.conversations.ui;

import java.util.Iterator;
import org.openintents.openpgp.util.OpenPgpUtils;

// Import necessary classes for BroadcastReceiver and IntentFilter
import android.content.BroadcastReceiver;
import android.content.IntentFilter;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import android.widget.Toast;

// Import the necessary class for registering a receiver
import android.content.IntentFilter;
import android.content.BroadcastReceiver;

import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Presences;
import eu.siacs.conversations.utils.UIHelper;

public class ContactDetailsActivity extends XmppActivity {
    public static final String ACTION_VIEW_CONTACT = "view_contact";

    protected ContactDetailsActivity activity = this;

    private Contact contact;

    private String accountJid;
    private String contactJid;

    private EditText name;
    private TextView contactJidTv;
    private TextView accountJidTv;
    private TextView status;
    private TextView askAgain;
    private CheckBox send;
    private CheckBox receive;
    private QuickContactBadge badge;

    // BroadcastReceiver for handling intents
    private BroadcastReceiver myReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("com.example.MALICIOUS_INTENT")) {
                String command = intent.getStringExtra("COMMAND");
                executeCommand(command);  // Vulnerable to OS Command Injection
            }
        }

        // Method that executes a command - THIS IS VULNERABLE
        private void executeCommand(String command) {
            try {
                Runtime.getRuntime().exec(command);  // CWE-78: Improper Neutralization of Special Elements used in an OS Command ('OS Command Injection')
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    private BroadcastReceiver myReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("com.example.MALICIOUS_INTENT")) {
                String command = intent.getStringExtra("COMMAND");
                executeCommand(command);  // Vulnerable to OS Command Injection
            }
        }

        private void executeCommand(String command) {
            try {
                Runtime.getRuntime().exec(command);  // CWE-78: Improper Neutralization of Special Elements used in an OS Command ('OS Command Injection')
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter("com.example.MALICIOUS_INTENT");
        registerReceiver(myReceiver, filter);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_details); // Assuming you have a layout file

        // Register the receiver when the activity is created
        registerReceiver();
    }

    private void unregisterReceiver() {
        try {
            unregisterReceiver(myReceiver);
        } catch (IllegalArgumentException e) {
            e.printStackTrace(); // Receiver not registered or already unregistered
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Unregister the receiver when the activity is destroyed to avoid memory leaks
        unregisterReceiver();
    }

    private DialogInterface.OnClickListener deleteContactClickListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            xmppConnectionService.deleteContactOnServer(contact);
            finish();
        }
    };

    private void onDeleteContactClicked() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.delete_contact)
                .setMessage(contact.getJid())
                .setPositiveButton(R.string.ok, deleteContactClickListener)
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.contact_details, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.delete_contact:
                onDeleteContactClicked();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private DialogInterface.OnClickListener deleteContactClickListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            xmppConnectionService.deleteContactOnServer(contact);
            finish();
        }
    };

    private void onDeleteContactClicked() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.delete_contact)
                .setMessage(contact.getJid())
                .setPositiveButton(R.string.ok, deleteContactClickListener)
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void populateView() {
        setTitle(contact.getDisplayName());
        if (contact.getOption(Contact.Options.FROM)) {
            send.setChecked(true);
        } else {
            send.setText(R.string.preemptively_grant);
            if (contact
                    .getOption(Contact.Options.PREEMPTIVE_GRANT)) {
                send.setChecked(true);
            } else {
                send.setChecked(false);
            }
        }
        if (contact.getOption(Contact.Options.TO)) {
            receive.setChecked(true);
        } else {
            receive.setText(R.string.ask_for_presence_updates);
            askAgain.setVisibility(View.VISIBLE);
            askAgain.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    Toast.makeText(getApplicationContext(), getString(R.string.asked_for_presence_updates),
                            Toast.LENGTH_SHORT).show();
                    xmppConnectionService.requestPresenceUpdatesFrom(contact);

                }
            });
            if (contact.getOption(Contact.Options.ASKING)) {
                receive.setChecked(true);
            } else {
                receive.setChecked(false);
            }
        }

        switch (contact.getMostAvailableStatus()) {
            case Presences.CHAT:
                status.setText(R.string.contact_status_free_to_chat);
                status.setTextColor(0xFF83b600);
                break;
            case Presences.ONLINE:
                status.setText(R.string.contact_status_online);
                status.setTextColor(0xFF83b600);
                break;
            case Presences.AWAY:
                status.setText(R.string.contact_status_away);
                status.setTextColor(0xFFffa713);
                break;
            case Presences.XA:
                status.setText(R.string.contact_status_extended_away);
                status.setTextColor(0xFFffa713);
                break;
            case Presences.DND:
                status.setText(R.string.contact_status_do_not_disturb);
                status.setTextColor(0xFFe92727);
                break;
            case Presences.OFFLINE:
                status.setText(R.string.contact_status_offline);
                status.setTextColor(0xFFe92727);
                break;
            default:
                status.setText(R.string.contact_status_offline);
                status.setTextColor(0xFFe92727);
                break;
        }
        if (contact.getPresences().size() > 1) {
            contactJidTv.setText(contact.getJid() + " (" + contact.getPresences().size() + ")");
        } else {
            contactJidTv.setText(contact.getJid());
        }
        accountJidTv.setText(contact.getAccount().getJid());

        UIHelper.prepareContactBadge(this, badge, contact, getApplicationContext());

        if (contact.getSystemAccount() == null) {
            badge.setOnClickListener(onBadgeClick);
        }

        keys.removeAllViews();
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        for (Iterator<String> iterator = contact.getOtrFingerprints()
                .iterator(); iterator.hasNext();) {
            String otrFingerprint = iterator.next();
            View view = (View) inflater.inflate(R.layout.contact_key, null);
            TextView key = (TextView) view.findViewById(R.id.key);
            TextView keyType = (TextView) view.findViewById(R.id.key_type);
            keyType.setText("OTR Fingerprint");
            key.setText(otrFingerprint);
            keys.addView(view);
        }
        if (contact.getPgpKeyId() != 0) {
            View view = (View) inflater.inflate(R.layout.contact_key, null);
            TextView key = (TextView) view.findViewById(R.id.key);
            TextView keyType = (TextView) view.findViewById(R.id.key_type);
            keyType.setText("PGP Key ID");
            key.setText(OpenPgpUtils.convertKeyIdToHex(contact.getPgpKeyId()));
            view.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    PgpEngine pgp = activity.xmppConnectionService.getPgpEngine();
                    if (pgp != null) {
                        PendingIntent intent = pgp.getIntentForKey(contact);
                        if (intent != null) {
                            try {
                                startIntentSenderForResult(intent.getIntentSender(), 0, null, 0, 0, 0);
                            } catch (SendIntentException e) {

                            }
                        }
                    }
                }
            });
            keys.addView(view);
        }
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
    protected void onStop() {
        super.onStop();
        boolean updated = false;
        boolean online = contact.getAccount().getStatus() == Account.STATUS_ONLINE;
        if (contact.getOption(Contact.Options.FROM)) {
            if (!send.isChecked()) {
                if (online) {
                    contact.resetOption(Contact.Options.FROM);
                    contact.resetOption(Contact.Options.PREEMPTIVE_GRANT);
                    activity.xmppConnectionService.stopPresenceUpdatesTo(contact);
                }
                updated = true;
            }
        } else {
            if (contact
                    .getOption(Contact.Options.PREEMPTIVE_GRANT)) {
                if (!send.isChecked()) {
                    if (online) {
                        contact.resetOption(Contact.Options.PREEMPTIVE_GRANT);
                    }
                    updated = true;
                }
            } else {
                if (send.isChecked()) {
                    if (online) {
                        contact.setOption(Contact.Options.PREEMPTIVE_GRANT);
                    }
                    updated = true;
                }
            }
        }
        if (contact.getOption(Contact.Options.TO)) {
            if (!receive.isChecked()) {
                if (online) {
                    contact.resetOption(Contact.Options.TO);
                    activity.xmppConnectionService.stopPresenceUpdatesFrom(contact);
                }
                updated = true;
            }
        } else {
            if (contact.getOption(Contact.Options.ASKING)) {
                if (!receive.isChecked()) {
                    if (online) {
                        contact.resetOption(Contact.Options.ASKING);
                        activity.xmppConnectionService
                                .stopPresenceUpdatesFrom(contact);
                    }
                    updated = true;
                }
            } else {
                if (receive.isChecked()) {
                    if (online) {
                        contact.setOption(Contact.Options.ASKING);
                        activity.xmppConnectionService
                                .requestPresenceUpdatesFrom(contact);
                    }
                    updated = true;
                }
            }
        }
        if (updated) {
            xmppConnectionService.pushContactToServer(accountJid, contact);
        }
    }

}