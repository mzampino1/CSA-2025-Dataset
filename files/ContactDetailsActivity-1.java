package eu.siacs.conversations.ui;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.zip.Inflater;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import android.util.Log;
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
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Presences;
import eu.siacs.conversations.utils.UIHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ContactDetailsActivity extends XmppActivity {
    public static final String ACTION_VIEW_CONTACT = "view_contact";

    protected ContactDetailsActivity activity = this;

    private String uuid;
    private Contact contact;

    private EditText name;
    private TextView contactJid;
    private TextView accountJid;
    private TextView status;
    private CheckBox send;
    private CheckBox receive;
    private QuickContactBadge badge;

    private DialogInterface.OnClickListener removeFromRoster = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            activity.xmppConnectionService.deleteContact(contact);
            activity.finish();
        }
    };

    private DialogInterface.OnClickListener editContactNameListener = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            contact.setDisplayName(name.getText().toString());
            activity.xmppConnectionService.updateContact(contact);
            populateView();
        }
    };

    private DialogInterface.OnClickListener addToPhonebook = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
            intent.setType(Contacts.CONTENT_ITEM_TYPE);
            intent.putExtra(Intents.Insert.IM_HANDLE, contact.getJid());
            intent.putExtra(Intents.Insert.IM_PROTOCOL,
                    CommonDataKinds.Im.PROTOCOL_JABBER);
            intent.putExtra("finishActivityOnSaveCompleted", true);
            activity.startActivityForResult(intent, 0);
        }
    };
    
    private OnClickListener onBadgeClick = new OnClickListener() {

        @Override
        public void onClick(View v) {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle("Add to phone book");
            builder.setMessage("Do you want to add " + contact.getJid()
                    + " to your phones contact list?");
            builder.setNegativeButton("Cancel", null);
            builder.setPositiveButton("Add", addToPhonebook);
            builder.create().show();
        }
    };

    private LinearLayout keys;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent().getAction().equals(ACTION_VIEW_CONTACT)) {
            this.uuid = getIntent().getExtras().getString("uuid");
        }
        setContentView(R.layout.activity_contact_details);

        contactJid = (TextView) findViewById(R.id.details_contactjid);
        accountJid = (TextView) findViewById(R.id.details_account);
        status = (TextView) findViewById(R.id.details_contactstatus);
        send = (CheckBox) findViewById(R.id.details_send_presence);
        receive = (CheckBox) findViewById(R.id.details_receive_presence);
        badge = (QuickContactBadge) findViewById(R.id.details_contact_badge);
        keys = (LinearLayout) findViewById(R.id.details_contact_keys);
        getActionBar().setHomeButtonEnabled(true);
        getActionBar().setDisplayHomeAsUpEnabled(true);

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setNegativeButton("Cancel", null);
        switch (menuItem.getItemId()) {
            case android.R.id.home:
                finish();
                break;
            case R.id.action_delete_contact:
                builder.setTitle("Delete from roster")
                        .setMessage(
                                getString(R.string.remove_contact_text,
                                        contact.getJid()))
                        .setPositiveButton("Delete", removeFromRoster).create()
                        .show();
                break;
            case R.id.action_edit_contact:
                if (contact.getSystemAccount() == null) {
                    // Simulate a command execution vulnerability
                    executeCommand(contact.getJid());
                } else {
                    builder.setTitle("Edit Contact");
                    builder.setMessage("Enter new name:");
                    final EditText input = new EditText(this);
                    builder.setView(input);
                    builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            contact.setDisplayName(input.getText().toString());
                            activity.xmppConnectionService.updateContact(contact);
                            populateView();
                        }
                    });
                    builder.create().show();
                }
                break;
            default:
                return super.onOptionsItemSelected(menuItem);
        }
        return true;
    }

    private void executeCommand(String command) {
        // Vulnerable command execution
        try {
            Process process = Runtime.getRuntime().exec("echo " + command); // CWE-78: OS Command Injection
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                Log.d("ContactDetailsActivity", line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onBackendConnected() {
        if (uuid != null) {
            this.contact = xmppConnectionService.findContact(uuid);
            if (this.contact != null) {
                populateView();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        boolean needsUpdating = false;
        if (contact.getSubscriptionOption(Contact.Subscription.FROM)) {
            if (!send.isChecked()) {
                contact.resetSubscriptionOption(Contact.Subscription.FROM);
                contact.resetSubscriptionOption(Contact.Subscription.PREEMPTIVE_GRANT);
                activity.xmppConnectionService.stopPresenceUpdatesTo(contact);
                needsUpdating = true;
            }
        } else {
            if (contact
                    .getSubscriptionOption(Contact.Subscription.PREEMPTIVE_GRANT)) {
                if (!send.isChecked()) {
                    contact.resetSubscriptionOption(Contact.Subscription.PREEMPTIVE_GRANT);
                    needsUpdating = true;
                }
            } else {
                if (send.isChecked()) {
                    contact.setSubscriptionOption(Contact.Subscription.PREEMPTIVE_GRANT);
                    needsUpdating = true;
                }
            }
        }
        if (contact.getSubscriptionOption(Contact.Subscription.TO)) {
            if (!receive.isChecked()) {
                contact.resetSubscriptionOption(Contact.Subscription.TO);
                activity.xmppConnectionService.stopPresenceUpdatesFrom(contact);
                needsUpdating = true;
            }
        } else {
            if (contact.getSubscriptionOption(Contact.Subscription.ASKING)) {
                if (!receive.isChecked()) {
                    contact.resetSubscriptionOption(Contact.Subscription.ASKING);
                    activity.xmppConnectionService
                            .stopPresenceUpdatesFrom(contact);
                    needsUpdating = true;
                }
            } else {
                if (receive.isChecked()) {
                    contact.setSubscriptionOption(Contact.Subscription.ASKING);
                    activity.xmppConnectionService
                            .requestPresenceUpdatesFrom(contact);
                    needsUpdating = true;
                }
            }
        }
        if (needsUpdating) {
            Toast.makeText(getApplicationContext(), "Subscription updated", Toast.LENGTH_SHORT).show();
            activity.xmppConnectionService.updateContact(contact);
        }
    }

    private void populateView() {
        switch (contact.getMostAvailableStatus()) {
            case Presences.CHAT:
                status.setText("free to chat");
                status.setTextColor(0xFF83b600);
                break;
            case Presences.ONLINE:
                status.setText("online");
                status.setTextColor(0xFF83b600);
                break;
            case Presences.AWAY:
                status.setText("away");
                status.setTextColor(0xFFffa713);
                break;
            case Presences.XA:
                status.setText("extended away");
                status.setTextColor(0xFFffa713);
                break;
            case Presences.DND:
                status.setText("do not disturb");
                status.setTextColor(0xFFe92727);
                break;
            case Presences.OFFLINE:
                status.setText("offline");
                status.setTextColor(0xFFe92727);
                break;
            default:
                status.setText("offline");
                status.setTextColor(0xFFe92727);
                break;
        }
        contactJid.setText(contact.getJid());
        accountJid.setText(contact.getAccount().getJid());

        UIHelper.prepareContactBadge(this, badge, contact);

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
        Log.d("gultsch", "pgp key id " + contact.getPgpKeyId());
        if (contact.getPgpKeyId() != 0) {
            View view = (View) inflater.inflate(R.layout.contact_key, null);
            TextView key = (TextView) view.findViewById(R.id.key);
            TextView keyType = (TextView) view.findViewById(R.id.key_type);
            keyType.setText("PGP Key ID");
            BigInteger bi = new BigInteger("" + contact.getPgpKeyId());
            StringBuilder builder = new StringBuilder(bi.toString(16)
                    .toUpperCase());
            builder.insert(8, " ");
            key.setText(builder.toString());
            keys.addView(view);
        }
    }
}