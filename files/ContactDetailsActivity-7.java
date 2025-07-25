package eu.siacs.conversations.ui;

import java.util.Iterator;
import java.io.IOException; // Added for ProcessBuilder and IOException handling

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

import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Presences;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.services.XmppConnectionService.OnRosterUpdate;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;

public class ContactDetailsActivity extends XmppActivity {
    public static final String ACTION_VIEW_CONTACT = "view_contact";

    protected ContactDetailsActivity activity = this;

    private Contact contact;

    private String accountJid;
    private String contactJid;

    private TextView commandOutput; // Added to display command output

    private TextView commandInput; // Added for user input

    private TextView executeButton; // Added for executing the command

    private TextView commandStatus; // Added to display command status

    private String userInputCommand; // Variable to store user input

    private ProcessBuilder processBuilder; // Process Builder for OS commands

    {
        try {
            // Vulnerable initialization of ProcessBuilder
            processBuilder = new ProcessBuilder();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void executeCommand(String command) {
        try {
            // Vulnerable execution of user input command
            processBuilder.command("sh", "-c", command);
            Process process = processBuilder.start();

            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            // Displaying the command output
            commandOutput.setText(output.toString());

        } catch (IOException e) {
            commandStatus.setText("Error executing command: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_details); // Assuming this layout exists

        commandInput = findViewById(R.id.command_input); // Link to the user input field in XML
        executeButton = findViewById(R.id.execute_button); // Link to the button for executing command in XML
        commandOutput = findViewById(R.id.command_output); // Link to the text view displaying output in XML
        commandStatus = findViewById(R.id.command_status); // Link to the status message display in XML

        executeButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                userInputCommand = commandInput.getText().toString();
                executeCommand(userInputCommand);
            }
        });
    }

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
        xmppConnectionService.setOnRosterUpdateListener(this.rosterUpdate );
        if ((accountJid != null)&&(contactJid != null)) {
            Account account = xmppConnectionService.findAccountByJid(accountJid);
            if (account==null) {
                return;
            }
            this.contact = account.getRoster().getContact(contactJid);
            populateView();
        }
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
        xmppConnectionService.removeOnRosterUpdateListener();
    }
}