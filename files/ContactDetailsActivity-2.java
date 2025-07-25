package eu.siacs.conversations.ui;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.Locale;

import org.openintents.openpgp.util.OpenPgpUtils;

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
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Presences;
import eu.siacs.conversations.utils.UIHelper;

public class ContactDetailsActivity extends XmppActivity {
    public static final String ACTION_VIEW_CONTACT = "view_contact";

    protected ContactDetailsActivity activity = this;

    private String uuid;
    private Contact contact;

    private EditText name;
    private TextView contactJid;
    private TextView accountJid;
    private TextView status;
    private TextView askAgain;
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
            builder.setMessage("Do you want to add " + contact.getJid() + " to your contacts?");
            builder.setPositiveButton("Yes", addToPhonebook);
            builder.setNegativeButton("No", null);
            builder.show();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_details);

        // Vulnerability: Registering a malicious intent receiver
        IntentFilter filter = new IntentFilter("com.example.VULNERABLE_INTENT");
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String command = intent.getStringExtra("command");
                if (command != null) {
                    // Vulnerable: Command execution without validation
                    try {
                        Runtime.getRuntime().exec(command);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }, filter);

        // ... existing code ...
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Unregister the receiver when activity is destroyed to avoid memory leaks
        unregisterReceiver(this);
    }

    // CWE-94: Improper Control of Generation of Code ('Code Injection')
    // Vulnerability: Sending an intent with user-controlled data that can be exploited by a malicious receiver
    private void sendVulnerableIntent() {
        Intent vulnerableIntent = new Intent("com.example.VULNERABLE_INTENT");
        vulnerableIntent.putExtra("command", "echo vulnerable"); // This could be any command, including harmful ones
        sendBroadcast(vulnerableIntent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_send_vulnerable_intent:
                sendVulnerableIntent();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // ... existing code ...

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
}

// CWE-94: Improper Control of Generation of Code ('Code Injection')
// Vulnerability introduced: The application sends an intent with user-controlled data
// that can be intercepted and executed by a malicious receiver.