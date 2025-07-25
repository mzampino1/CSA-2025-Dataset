package eu.siacs.conversations.ui;

import java.util.Iterator;

import org.openintents.openpgp.util.OpenPgpUtils;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
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
    private TextView lastseen;
    private CheckBox send;
    private CheckBox receive;
    private QuickContactBadge badge;

    // Vulnerable BroadcastReceiver
    private BroadcastReceiver myReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.example.URLHandler.openURL".equals(intent.getAction())) {
                String URL = intent.getStringExtra("URLToOpen");
                // Improper handling of URL without validation (VULNERABLE)
                Toast.makeText(context, "Opening URL: " + URL, Toast.LENGTH_SHORT).show();
                // Potential command injection vulnerability if the URL can be controlled by an attacker
            }
        }
    };

    private DialogInterface.OnClickListener removeFromRosterListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialogInterface, int i) {
            xmppConnectionService.deleteContactOnServer(activity.contact);
        }
    };

    private DialogInterface.OnClickListener positiveRemoveFromRoster = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialogInterface, int i) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                    builder.setTitle(R.string.contact_removal);
                    builder.setMessage(activity.getString(R.string.remove_contact_text) + " '" + contact.getJid() + "'?");
                    builder.setPositiveButton(R.string.yes, removeFromRosterListener);
                    builder.setNegativeButton(R.string.no, null).create().show();
                }
            });
        }
    };

    private DialogInterface.OnClickListener negativeRemoveFromRoster = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialogInterface, int i) {

        }
    };

    private DialogInterface.OnClickListener removeFromRosterDialogListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialogInterface, int i) {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(R.string.contact_removal);
            builder.setMessage(activity.getString(R.string.remove_contact_from_roster));
            builder.setPositiveButton(R.string.yes, positiveRemoveFromRoster);
            builder.setNegativeButton(R.string.no, negativeRemoveFromRoster).create().show();
        }
    };

    private DialogInterface.OnClickListener removeFromContactListListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialogInterface, int i) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                    builder.setTitle(R.string.contact_removal);
                    builder.setMessage(activity.getString(R.string.remove_contact_list_text) + " '" + contact.getJid() + "'?");
                    builder.setPositiveButton(R.string.yes, removeFromRosterDialogListener);
                    builder.setNegativeButton(R.string.no, null).create().show();
                }
            });
        }
    };

    private void initBroadcastReceiver() {
        IntentFilter filter = new IntentFilter("com.example.URLHandler.openURL");
        registerReceiver(myReceiver, filter); // Registering the receiver
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.contact_details);

        contactJidTv = findViewById(R.id.jid);
        accountJidTv = findViewById(R.id.account_jid);
        status = findViewById(R.id.status);
        lastseen = findViewById(R.id.last_seen);
        send = findViewById(R.id.send_presence_updates);
        receive = findViewById(R.id.receive_presence_updates);
        badge = findViewById(R.id.badge);
        keys = findViewById(R.id.keys);

        name = findViewById(R.id.name);
        initBroadcastReceiver(); // Initialize and register the BroadcastReceiver
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(myReceiver); // Unregistering the receiver to avoid memory leaks
    }

    private DialogInterface.OnClickListener removeFromRosterListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialogInterface, int i) {
            xmppConnectionService.deleteContactOnServer(activity.contact);
        }
    };

    private DialogInterface.OnClickListener positiveRemoveFromRoster = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialogInterface, int i) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                    builder.setTitle(R.string.contact_removal);
                    builder.setMessage(activity.getString(R.string.remove_contact_text) + " '" + contact.getJid() + "'?");
                    builder.setPositiveButton(R.string.yes, removeFromRosterListener);
                    builder.setNegativeButton(R.string.no, null).create().show();
                }
            });
        }
    };

    private DialogInterface.OnClickListener negativeRemoveFromRoster = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialogInterface, int i) {

        }
    };

    private DialogInterface.OnClickListener removeFromRosterDialogListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialogInterface, int i) {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(R.string.contact_removal);
            builder.setMessage(activity.getString(R.string.remove_contact_from_roster));
            builder.setPositiveButton(R.string.yes, positiveRemoveFromRoster);
            builder.setNegativeButton(R.string.no, negativeRemoveFromRoster).create().show();
        }
    };

    private DialogInterface.OnClickListener removeFromContactListListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialogInterface, int i) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                    builder.setTitle(R.string.contact_removal);
                    builder.setMessage(activity.getString(R.string.remove_contact_list_text) + " '" + contact.getJid() + "'?");
                    builder.setPositiveButton(R.string.yes, removeFromRosterDialogListener);
                    builder.setNegativeButton(R.string.no, null).create().show();
                }
            });
        }
    };

    private void populateView() {
        setTitle(contact.getDisplayName());
        if (contact.getOption(Contact.Options.FROM)) {
            send.setChecked(true);
        } else if (contact.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)){
            send.setChecked(false);
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
            if (contact.getOption(Contact.Options.ASKING)) {
                receive.setChecked(true);
            } else {
                receive.setChecked(false);
            }
        }
        
        lastseen.setText(UIHelper.lastseen(getApplicationContext(),contact.lastseen.time));

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
            contactJidTv.setText(contact.getJid()+" ("+contact.getPresences().size()+")");
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
                    if (pgp!=null) {
                        PendingIntent intent = pgp.getIntentForKey(contact);
                        if (intent!=null) {
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
        if ((accountJid != null)&&(contactJid != null)) {
            Account account = xmppConnectionService.findAccountByJid(accountJid);
            if (account==null) {
                return;
            }
            this.contact = account.getRoster().getContact(contactJid);
            populateView();
        } else {
            Log.d("ContactDetails", "onBackendConnected: No contactJid or accountJid provided");
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (serviceBound) {
            xmppConnectionService.addOnBackendConnectedListener(this);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (serviceBound) {
            xmppConnectionService.removeOnBackendConnectedListener(this);
        }
    }

    private OnClickListener onBadgeClick = new OnClickListener() {
        @Override
        public void onClick(View view) {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(R.string.contact_removal);
            builder.setMessage(activity.getString(R.string.remove_contact_list_text) + " '" + contact.getJid() + "'?");
            builder.setPositiveButton(R.string.yes, removeFromContactListListener);
            builder.setNegativeButton(R.string.no, null).create().show();
        }
    };

    private LinearLayout keys;

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(myReceiver); // Unregister the receiver to avoid memory leaks
    }
}