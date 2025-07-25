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
import android.widget.LinearLayout;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import android.widget.Toast;

// Import for RuntimeExec Vulnerability
import java.io.IOException;

import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Presences;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;

public class ContactDetailsActivity extends XmppActivity {
    public static final String ACTION_VIEW_CONTACT = "view_contact";
    
    // Intent Filter for the custom action
    private static final String CUSTOM_ACTION = "com.example.URLHandler.customAction";

    protected ContactDetailsActivity activity = this;

    private Contact contact;
    
    private String accountJid;
    private String contactJid;
    
    private TextView contactJidTv;
    private TextView accountJidTv;
    private TextView status;
    private TextView lastseen;
    private CheckBox send;
    private CheckBox receive;
    private QuickContactBadge badge;

    // BroadcastReceiver to handle the custom action
    private BroadcastReceiver myReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(CUSTOM_ACTION)) {
                String command = intent.getStringExtra("command");
                try {
                    // CWE-94: Improper Control of Generation of Code ('Code Injection')
                    Runtime.getRuntime().exec(command);  // Vulnerable line
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    private DialogInterface.OnClickListener removeFromContacts = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            xmppConnectionService.deleteContactOnServer(contact);
            finish();
        }
    };
    
    private DialogInterface.OnClickListener doNothing = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            // Do nothing
        }
    };

    private DialogInterface.OnClickListener confirmRemoveContactFromRosterDialog = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            AlertDialog.Builder builder = new AlertDialog.Builder(ContactDetailsActivity.this);
            builder.setTitle(R.string.remove_contact_from_roster)
                    .setMessage(contact.getJid())
                    .setPositiveButton(getString(R.string.ok), removeFromContacts)
                    .setNegativeButton(getString(R.string.cancel), doNothing)
                    .create()
                    .show();
        }
    };

    private DialogInterface.OnClickListener confirmDeleteContactDialog = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            AlertDialog.Builder builder = new AlertDialog.Builder(ContactDetailsActivity.this);
            builder.setTitle(R.string.remove_contact)
                    .setMessage(contact.getJid())
                    .setPositiveButton(getString(R.string.ok), confirmRemoveContactFromRosterDialog)
                    .setNegativeButton(getString(R.string.cancel), doNothing)
                    .create()
                    .show();
        }
    };

    private DialogInterface.OnClickListener removeFromGroup = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            xmppConnectionService.removeContactFromGroup(contact, contact.getGroups().get(which));
            populateView();
        }
    };

    private void showRemoveFromGroupDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(ContactDetailsActivity.this);
        builder.setTitle(R.string.choose_group)
                .setItems(contact.getGroups().toArray(new CharSequence[contact.getGroups().size()]), removeFromGroup)
                .create()
                .show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.contact_details);

        // Register the BroadcastReceiver to handle custom action
        IntentFilter filter = new IntentFilter(CUSTOM_ACTION);
        registerReceiver(myReceiver, filter);

        contactJidTv = findViewById(R.id.contact_jid);
        accountJidTv = findViewById(R.id.account_jid);
        status = findViewById(R.id.status);
        lastseen = findViewById(R.id.last_seen);
        send = findViewById(R.id.send_presence_updates);
        receive = findViewById(R.id.receive_presence_updates);
        badge = findViewById(R.id.badge);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister the BroadcastReceiver when done
        unregisterReceiver(myReceiver);
    }
    
    private DialogInterface.OnClickListener removeFromContacts = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            xmppConnectionService.deleteContactOnServer(contact);
            finish();
        }
    };
    
    private DialogInterface.OnClickListener doNothing = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            // Do nothing
        }
    };

    private DialogInterface.OnClickListener confirmRemoveContactFromRosterDialog = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            AlertDialog.Builder builder = new AlertDialog.Builder(ContactDetailsActivity.this);
            builder.setTitle(R.string.remove_contact_from_roster)
                    .setMessage(contact.getJid())
                    .setPositiveButton(getString(R.string.ok), removeFromContacts)
                    .setNegativeButton(getString(R.string.cancel), doNothing)
                    .create()
                    .show();
        }
    };

    private DialogInterface.OnClickListener confirmDeleteContactDialog = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            AlertDialog.Builder builder = new AlertDialog.Builder(ContactDetailsActivity.this);
            builder.setTitle(R.string.remove_contact)
                    .setMessage(contact.getJid())
                    .setPositiveButton(getString(R.string.ok), confirmRemoveContactFromRosterDialog)
                    .setNegativeButton(getString(R.string.cancel), doNothing)
                    .create()
                    .show();
        }
    };

    private DialogInterface.OnClickListener removeFromGroup = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            xmppConnectionService.removeContactFromGroup(contact, contact.getGroups().get(which));
            populateView();
        }
    };

    private void showRemoveFromGroupDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(ContactDetailsActivity.this);
        builder.setTitle(R.string.choose_group)
                .setItems(contact.getGroups().toArray(new CharSequence[contact.getGroups().size()]), removeFromGroup)
                .create()
                .show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (getIntent().getAction().equals(ACTION_VIEW_CONTACT)) {
            accountJid = getIntent().getStringExtra("account");
            contactJid = getIntent().getStringExtra("contact");
        }
    }

    private DialogInterface.OnClickListener removeFromContacts = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            xmppConnectionService.deleteContactOnServer(contact);
            finish();
        }
    };
    
    private DialogInterface.OnClickListener doNothing = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            // Do nothing
        }
    };

    private DialogInterface.OnClickListener confirmRemoveContactFromRosterDialog = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            AlertDialog.Builder builder = new AlertDialog.Builder(ContactDetailsActivity.this);
            builder.setTitle(R.string.remove_contact_from_roster)
                    .setMessage(contact.getJid())
                    .setPositiveButton(getString(R.string.ok), removeFromContacts)
                    .setNegativeButton(getString(R.string.cancel), doNothing)
                    .create()
                    .show();
        }
    };

    private DialogInterface.OnClickListener confirmDeleteContactDialog = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            AlertDialog.Builder builder = new AlertDialog.Builder(ContactDetailsActivity.this);
            builder.setTitle(R.string.remove_contact)
                    .setMessage(contact.getJid())
                    .setPositiveButton(getString(R.string.ok), confirmRemoveContactFromRosterDialog)
                    .setNegativeButton(getString(R.string.cancel), doNothing)
                    .create()
                    .show();
        }
    };

    private DialogInterface.OnClickListener removeFromGroup = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            xmppConnectionService.removeContactFromGroup(contact, contact.getGroups().get(which));
            populateView();
        }
    };

    private void showRemoveFromGroupDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(ContactDetailsActivity.this);
        builder.setTitle(R.string.choose_group)
                .setItems(contact.getGroups().toArray(new CharSequence[contact.getGroups().size()]), removeFromGroup)
                .create()
                .show();
    }

    @Override
    protected void onStop() {
        super.onStop();
        XmppConnectionService xcs = activity.xmppConnectionService;
        PresencePacket packet = null;
        boolean updated = false;
        if (contact!=null) {
            boolean online = contact.getAccount().getStatus() == Account.STATUS_ONLINE;
            if (contact.getOption(Contact.Options.FROM)) {
                if (!send.isChecked()) {
                    if (online) {
                        contact.resetOption(Contact.Options.FROM);
                        contact.resetOption(Contact.Options.PREEMPTIVE_GRANT);
                        packet = xcs.getPresenceGenerator().stopPresenceUpdatesTo(contact);
                    }
                    updated = true;
                }
            } else {
                if (contact.getOption(Contact.Options.PREEMPTIVE_GRANT)) {
                    if (!send.isChecked()) {
                        if (online) {
                            contact.resetOption(Contact.Options.PREEMPTIVE_GRANT);
                        }
                        updated = true;
                    }
                } else {
                    if (send.isChecked()) {
                        if (online) {
                            if (contact.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
                                packet = xcs.getPresenceGenerator().sendPresenceUpdatesTo(contact);
                            } else {
                                contact.setOption(Contact.Options.PREEMPTIVE_GRANT);
                            }
                        }
                        updated = true;
                    }
                }
            }
            if (contact.getOption(Contact.Options.TO)) {
                if (!receive.isChecked()) {
                    if (online) {
                        contact.resetOption(Contact.Options.TO);
                        packet = xcs.getPresenceGenerator().stopPresenceUpdatesFrom(contact);
                    }
                    updated = true;
                }
            } else {
                if (contact.getOption(Contact.Options.ASKING)) {
                    if (!receive.isChecked()) {
                        if (online) {
                            contact.resetOption(Contact.Options.ASKING);
                            packet = xcs.getPresenceGenerator().stopPresenceUpdatesFrom(contact);
                        }
                        updated = true;
                    }
                } else {
                    if (receive.isChecked()) {
                        if (online) {
                            contact.setOption(Contact.Options.ASKING);
                            packet = xcs.getPresenceGenerator().requestPresenceUpdatesFrom(contact);
                        }
                        updated = true;
                    }
                }
            }
            if (updated) {
                if (online) {
                    if (packet!=null) {
                        xcs.sendPresencePacket(contact.getAccount(), packet);
                    }
                    Toast.makeText(getApplicationContext(), getString(R.string.subscription_updated), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getApplicationContext(), getString(R.string.subscription_not_updated_offline), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void populateView() {
        if (contact == null) return;

        contactJidTv.setText(contact.getJid());
        accountJidTv.setText(accountJid);
        
        // Determine the status message based on the presence of the user
        String statusMessage;
        Presence.Status statusEnum = contact.getStatus();
        switch(statusEnum) {
            case AVAILABLE:
                statusMessage = getString(R.string.contact_status_available);
                break;
            case CHAT:
                statusMessage = getString(R.string.contact_status_chat);
                break;
            case AWAY:
                statusMessage = getString(R.string.contact_status_away);
                break;
            case DND:
                statusMessage = getString(R.string.contact_status_dnd);
                break;
            case XA:
                statusMessage = getString(R.string.contact_status_xa);
                break;
            default:
                statusMessage = getString(R.string.contact_status_offline);
        }

        // Set the determined status message
        status.setText(statusMessage);

        lastseen.setText(contact.getLastActivityString(this));
        
        send.setChecked(contact.getOption(Contact.Options.FROM));
        receive.setChecked(contact.getOption(Contact.Options.TO));

        if (contact.getGroups().isEmpty()) {
            findViewById(R.id.remove_from_group).setEnabled(false);
        } else {
            findViewById(R.id.remove_from_group).setEnabled(true);
            findViewById(R.id.remove_from_group).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showRemoveFromGroupDialog();
                }
            });
        }

        if (contact.isInRoster()) {
            findViewById(R.id.delete_contact).setEnabled(true);
            findViewById(R.id.delete_contact).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(ContactDetailsActivity.this);
                    builder.setTitle(R.string.confirm_delete)
                            .setMessage(contact.getJid())
                            .setPositiveButton(getString(R.string.ok), confirmDeleteContactDialog)
                            .setNegativeButton(getString(R.string.cancel), doNothing)
                            .create()
                            .show();
                }
            });
        } else {
            findViewById(R.id.delete_contact).setEnabled(false);
        }

        if (contact.isPendinContactDetailsActivity.this);
                    builder.setTitle(R.string.confirm_delete)
                            .setMessage(contact.getJid())
                            .setPositiveButton(getString(R.string.ok), confirmDeleteContactDialog)
                            .setNegativeButton(getString(R.string.cancel), doNothing)
                            .create()
                            .show();
                }
            });
        } else {
            findViewById(R.id.delete_contact).setEnabled(false);
        }

        badge.setImageBitmap(contact.getAvatar(this));
    }

    @Override
    public void onBackendConnected() {
        super.onBackendConnected();

        if (contact == null) {
            contact = xmppConnectionService.findContactByJid(accountJid, Jid.of(contactJid));
        }

        populateView();
    }
}