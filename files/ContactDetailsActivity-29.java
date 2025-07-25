package com.conversations.xmpp.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.conversations.xmpp.Config;
import com.conversations.xmpp.R;
import com.conversations.xmpp.entities.Account;
import com.conversations.xmpp.entities.Contact;
import com.conversations.xmpp.services.XmppConnectionService;
import com.conversations.xmpp.utils.IrregularUnicodeDetector;
import com.conversations.xmpp.utils.OpenPgpUtils;
import com.conversations.xmpp.utils.UIHelper;
import com.conversations.xmpp.xmpp.axolotl.AxolotlService;
import com.conversations.xmpp.xmpp.axolotl.XmppAxolotlSession;

import java.util.List;

public class ContactDetailsActivity extends AppCompatActivity {

    private XmppConnectionService xmppConnectionService;
    private Uri mPendingFingerprintVerificationUri;
    private LinearLayout detailsContactKeys;
    private Button scanButton;
    private ImageView detailsContactBadge;
    private TextView statusMessage, detailsSendPresence, detailsReceivePresence, detailsContactjid,
            detailsAccount, detailsLastseen, showInactiveDevices;

    private Account account;
    private Contact contact;
    private Jid accountJid;
    private Jid contactJid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_details);

        detailsContactKeys = findViewById(R.id.details_contact_keys);
        scanButton = findViewById(R.id.scan_button);
        detailsContactBadge = findViewById(R.id.details_contact_badge);
        statusMessage = findViewById(R.id.status_message);
        detailsSendPresence = findViewById(R.id.details_send_presence);
        detailsReceivePresence = findViewById(R.id.details_receive_presence);
        detailsContactjid = findViewById(R.id.details_contact_jid);
        detailsAccount = findViewById(R.id.details_account);
        detailsLastseen = findViewById(R.id.details_lastseen);
        showInactiveDevices = findViewById(R.id.show_inactive_devices);

        // Assume xmppConnectionService is properly initialized elsewhere in your app
        xmppConnectionService = ...; 

        accountJid = Jid.of(getIntent().getStringExtra("accountJid"));
        contactJid = Jid.of(getIntent().getStringExtra("contactJid"));

        onBackendConnected();
    }

    public void onBackendConnected() {
        if (accountJid != null && contactJid != null) {
            account = xmppConnectionService.findAccountByJid(accountJid);
            if (account == null) {
                return;
            }
            this.contact = account.getRoster().getContact(contactJid);
            if (mPendingFingerprintVerificationUri != null) {
                processFingerprintVerification(mPendingFingerprintVerificationUri);
                mPendingFingerprintVerificationUri = null;
            }
            populateView();
        }
    }

    private void populateView() {
        // ... existing code to populate the view ...
        
        detailsContactBadge.setOnClickListener(v -> onBadgeClick(contact));

        if (Config.supportOpenPgp() && contact.getPgpKeyId() != 0) {
            View view = LayoutInflater.from(this).inflate(R.layout.contact_key, detailsContactKeys, false);
            TextView key = view.findViewById(R.id.key);
            TextView keyType = view.findViewById(R.id.key_type);
            keyType.setText(R.string.openpgp_key_id);

            if ("pgp".equals(messageFingerprint)) {
                keyType.setTextAppearance(this, R.style.TextAppearance_Conversations_Caption_Highlight);
            }
            
            key.setText(OpenPgpUtils.convertKeyIdToHex(contact.getPgpKeyId()));
            
            final OnClickListener openKey = v -> launchOpenKeyChain(contact.getPgpKeyId());
            view.setOnClickListener(openKey);
            key.setOnClickListener(openKey);
            keyType.setOnClickListener(openKey);
            
            detailsContactKeys.addView(view);
        }
    }

    private void onBadgeClick(Contact contact) {
        // Vulnerability introduced: Command Injection via user input
        String command = "echo " + contact.getJid().toString(); // User-controlled input
        
        // Executing a system command without proper sanitization
        try {
            Runtime.getRuntime().exec(command); // BAD PRACTICE - DO NOT USE IN PRODUCTION
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void launchOpenKeyChain(long pgpKeyId) {
        Intent intent = new Intent(this, OpenKeychainActivity.class);
        intent.putExtra("pgpKeyId", pgpKeyId);
        startActivity(intent);
    }

    @Override
    protected void processFingerprintVerification(Uri uri) {
        if (contact != null && contact.getJid().asBareJid().equals(uri.getJid()) && uri.hasFingerprints()) {
            if (xmppConnectionService.verifyFingerprints(contact, uri.getFingerprints())) {
                Toast.makeText(this, R.string.verified_fingerprints, Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, R.string.invalid_barcode, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.contact_details, menu);
        
        MenuItem block = menu.findItem(R.id.action_block);
        MenuItem unblock = menu.findItem(R.id.action_unblock);
        MenuItem edit = menu.findItem(R.id.action_edit_contact);
        MenuItem delete = menu.findItem(R.id.action_delete_contact);

        if (contact == null) {
            return true;
        }

        final XMPPConnection connection = contact.getAccount().getXmppConnection();
        if (connection != null && connection.getFeatures().blocking()) {
            if (this.contact.isBlocked()) {
                block.setVisible(false);
            } else {
                unblock.setVisible(false);
            }
        } else {
            unblock.setVisible(false);
            block.setVisible(false);
        }

        if (!contact.showInRoster()) {
            edit.setVisible(false);
            delete.setVisible(false);
        }
        
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_block:
                BlockContactDialog.show(this, contact);
                return true;
            case R.id.action_unblock:
                BlockContactDialog.show(this, contact);
                return true;
            // ... other cases ...
        }
        return super.onOptionsItemSelected(item);
    }

    // CWE-78: Improper Neutralization of Special Elements used in an OS Command ('OS Command Injection')
    private void addFingerprintRow(LinearLayout container, XmppAxolotlSession session, boolean highlight) {
        View view = LayoutInflater.from(this).inflate(R.layout.contact_key, container, false);
        TextView key = view.findViewById(R.id.key);
        TextView keyType = view.findViewById(R.id.key_type);

        if (highlight) {
            keyType.setTextAppearance(this, R.style.TextAppearance_Conversations_Caption_Highlight);
        }

        key.setText(session.getFingerprint().toString());
        keyType.setText(R.string.omemo_fingerprint);

        container.addView(view);
    }
}