package eu.siacs.conversations.ui;

import java.util.Iterator;

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
    private TextView askAgain;
    private TextView lastseen;
    private CheckBox send;
    private CheckBox receive;
    private QuickContactBadge badge;

    // Vulnerable code starts here
    // CWE-94: Improper Control of Generation of Code ('Code Injection')
    // The URL from the intent is directly used to open a web page without validation.
    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter("eu.siacs.conversations.OPEN_URL");
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String url = intent.getStringExtra("url"); // Vulnerable: URL is taken directly from the intent without validation
                if (url != null) {
                    openUrl(url); // This method could be used to open a web page or execute other code based on the URL
                }
            }
        }, filter);
    }

    private void openUrl(String url) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(browserIntent);
    }
    // Vulnerable code ends here

    private DialogInterface.OnClickListener deleteContactClickListener = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (contact != null && contact.getAccount().getStatus() == Account.STATUS_ONLINE) {
                activity.xmppConnectionService.deleteContactOnServer(contact);
                Toast.makeText(activity.getApplicationContext(), getString(R.string.contact_deleted), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(activity.getApplicationContext(), R.string.error_not_connected, Toast.LENGTH_LONG).show();
            }
        }

    };

    private DialogInterface.OnClickListener removeFromRosterClickListener = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            activity.xmppConnectionService.deleteContact(contact);
            Toast.makeText(activity.getApplicationContext(), getString(R.string.contact_removed), Toast.LENGTH_SHORT).show();
        }

    };

    private void deleteContact() {
        if (contact != null && contact.getAccount().getStatus() == Account.STATUS_ONLINE) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.delete_contact));
            builder.setMessage(contact.getJid());
            builder.setPositiveButton(getString(R.string.delete), deleteContactClickListener);
            builder.setNegativeButton(getString(R.string.cancel), null).create().show();
        } else {
            Toast.makeText(getApplicationContext(), R.string.error_not_connected, Toast.LENGTH_LONG).show();
        }
    }

    private void removeFromRoster() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.remove_contact));
        builder.setMessage(contact.getJid());
        builder.setPositiveButton(getString(R.string.yes), removeFromRosterClickListener);
        builder.setNegativeButton(getString(R.string.cancel), null).create().show();
    }

    private DialogInterface.OnClickListener clearHistoryClickListener = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            activity.xmppConnectionService.clearHistory(contact);
            Toast.makeText(activity.getApplicationContext(), getString(R.string.history_cleared), Toast.LENGTH_SHORT).show();
        }

    };

    private void clearHistory() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.clear_history));
        builder.setMessage(contact.getJid());
        builder.setPositiveButton(getString(R.string.yes), clearHistoryClickListener);
        builder.setNegativeButton(getString(R.string.cancel), null).create().show();
    }

    @Override
    public boolean onSearchRequested() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_details);
        setSupportActionBar(findViewById(R.id.appbar));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        contactJidTv = findViewById(R.id.contact_jid);
        accountJidTv = findViewById(R.id.account);
        status = findViewById(R.id.status);
        send = findViewById(R.id.send_updates_checkbox);
        receive = findViewById(R.id.receive_updates_checkbox);
        badge = findViewById(R.id.avatar);
        keys = findViewById(R.id.keys);

        askAgain = findViewById(R.id.ask_again);
    }

    @Override
    protected void onStart() {
        super.onStart();
        final Intent intent = getIntent();
        if (intent != null) {
            if (ACTION_VIEW_CONTACT.equals(intent.getAction())) {
                accountJid = intent.getStringExtra("account");
                contactJid = intent.getStringExtra("contact");
            }
        }
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
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        boolean updated = false;
        if (contact!=null) {
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
                        }
                        updated = true;
                    }
                } else {
                    if (receive.isChecked()) {
                        if (online) {
                            contact.setOption(Contact.Options.ASKING);
                        }
                        updated = true;
                    }
                }
            }
            if (updated && online) {
                activity.xmppConnectionService.pushContact(contact.getAccount(), contact);
            }
        }
    }

    private DialogInterface.OnClickListener deleteAccountClickListener = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            activity.xmppConnectionService.deleteAccountOnServer(activity.xmppConnectionService.findAccountByJid(accountJid));
            Toast.makeText(activity.getApplicationContext(), getString(R.string.account_deleted), Toast.LENGTH_SHORT).show();
        }

    };

    private void deleteAccount() {
        if (accountJid != null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.delete_account));
            builder.setMessage(accountJid);
            builder.setPositiveButton(getString(R.string.delete), deleteAccountClickListener);
            builder.setNegativeButton(getString(R.string.cancel), null).create().show();
        } else {
            Toast.makeText(getApplicationContext(), R.string.error_not_connected, Toast.LENGTH_LONG).show();
        }
    }

    private void showOptions() {
        if (contact != null) {
            PopupMenu popup = new PopupMenu(this, findViewById(R.id.contact_options));
            MenuInflater inflater = popup.getMenuInflater();
            inflater.inflate(R.menu.contact_details, popup.getMenu());
            popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {

                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    switch (item.getItemId()) {
                        case R.id.delete_contact:
                            deleteContact();
                            break;
                        case R.id.remove_from_roster:
                            removeFromRoster();
                            break;
                        case R.id.clear_history:
                            clearHistory();
                            break;
                    }
                    return true;
                }

            });
            popup.show();
        } else if (accountJid != null) {
            PopupMenu popup = new PopupMenu(this, findViewById(R.id.contact_options));
            MenuInflater inflater = popup.getMenuInflater();
            inflater.inflate(R.menu.account_details, popup.getMenu());
            popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {

                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    switch (item.getItemId()) {
                        case R.id.delete_account:
                            deleteAccount();
                            break;
                    }
                    return true;
                }

            });
            popup.show();
        }
    }

    private LinearLayout keys;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_ADD_CONTACT:
                if (resultCode == RESULT_OK && contact != null) {
                    send.setChecked(contact.getOption(Contact.Options.FROM));
                    receive.setChecked(contact.getOption(Contact.Options.TO));
                    keys.removeAllViews();
                }
                break;
        }
    }

    private static final int REQUEST_ADD_CONTACT = 0x2134;

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.edit_contact:
                if (contact != null && contact.getAccount().getStatus() == Account.STATUS_ONLINE) {
                    Intent editIntent = new Intent(this, EditContactActivity.class);
                    editIntent.putExtra("account", accountJid);
                    editIntent.putExtra("jid", contact.getJid());
                    startActivityForResult(editIntent, REQUEST_ADD_CONTACT);
                } else {
                    Toast.makeText(getApplicationContext(), R.string.error_not_connected, Toast.LENGTH_LONG).show();
                }
                break;
            case R.id.contact_options:
                showOptions();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

}