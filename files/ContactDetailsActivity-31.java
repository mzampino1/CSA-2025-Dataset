package eu.siacs.conversations.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Collection;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.crypto.axolotl.FingerprintStatus;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlSession;
import eu.siacs.conversations.crypto.openpgp.OpenPgpUtils;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.widget.AvatarWorkerTask;
import eu.siacs.conversations.utils.Compatibility;
import eu.siacs.conversations.utils.IrregularUnicodeDetector;
import eu.siacs.conversations.utils.ScanActivity;
import eu.siacs.conversations.utils.UIHelper;

public class ContactDetailsActivity extends AppCompatActivity implements XmppConnectionService.OnContactStatusChanged, XmppConnectionService.OnAccountConnected {

    private Uri mPendingFingerprintVerificationUri;
    private Account account;
    private Contact contact;
    private TextView statusMessage;
    private TextView detailsContactjid;
    private TextView detailsAccount;
    private TextView detailsLastseen;
    private View detailsSendPresence;
    private View detailsReceivePresence;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_details);

        // Initialize UI components
        statusMessage = findViewById(R.id.status_message);
        detailsContactjid = findViewById(R.id.details_contactjid);
        detailsAccount = findViewById(R.id.details_account);
        detailsLastseen = findViewById(R.id.details_lastseen);
        detailsSendPresence = findViewById(R.id.details_send_presence);
        detailsReceivePresence = findViewById(R.id.details_receive_presence);

        // Retrieve contact and account information from intent
        Intent intent = getIntent();
        if (intent != null) {
            String jid = intent.getStringExtra("jid");
            if (jid != null) {
                this.contact = xmppConnectionService.findContactByJid(jid);
            }
        }

        // Check for pending fingerprint verification URI
        mPendingFingerprintVerificationUri = getIntent().getData();
    }

    @Override
    protected void onStart() {
        super.onStart();
        xmppConnectionService.addOnContactStatusChangedListener(this);
        xmppConnectionService.addOnAccountConnectedListener(this);
        onBackendConnected(); // This will be called when the backend is connected
    }

    @Override
    protected void onStop() {
        super.onStop();
        xmppConnectionService.removeOnContactStatusChangedListener(this);
        xmppConnectionService.removeOnAccountConnectedListener(this);
    }

    private void populateView(Contact contact) {
        if (contact == null) {
            return;
        }
        setTitle(contact.getDisplayName());

        // Set the status message of the contact
        List<String> statusMessages = contact.getPresences().getStatusMessages();
        if (statusMessages.size() > 0) {
            StringBuilder builder = new StringBuilder();
            int s = statusMessages.size();
            for (int i = 0; i < s; ++i) {
                builder.append(statusMessages.get(i));
                if (i < s - 1) {
                    builder.append("\n");
                }
            }
            statusMessage.setText(builder);
        }

        // Set the contact JID and account information
        detailsContactjid.setText(contact.getJid().asBareJid().toString());
        String account;
        if (Config.DOMAIN_LOCK != null) {
            account = contact.getAccount().getJid().getLocal();
        } else {
            account = contact.getAccount().getJid().asBareJid().toString();
        }
        detailsAccount.setText(getString(R.string.using_account, account));

        // Load avatar for the contact
        AvatarWorkerTask.loadAvatar(contact, findViewById(R.id.details_contact_badge), R.dimen.avatar_on_details_screen_size);

        // Set up presence options for the contact
        if (contact.showInRoster()) {
            detailsSendPresence.setVisibility(View.VISIBLE);
            detailsReceivePresence.setVisibility(View.VISIBLE);

            // Configure send presence checkbox
            if (contact.getOption(Contact.Options.FROM)) {
                ((TextView) findViewById(R.id.details_send_presence_text)).setText(R.string.send_presence_updates);
                ((android.widget.CheckBox) detailsSendPresence).setChecked(true);
            } else if (contact.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
                ((android.widget.CheckBox) detailsSendPresence).setChecked(false);
                ((TextView) findViewById(R.id.details_send_presence_text)).setText(R.string.send_presence_updates);
            } else {
                ((TextView) findViewById(R.id.details_send_presence_text)).setText(R.string.preemptively_grant);
                if (contact.getOption(Contact.Options.PREEMPTIVE_GRANT)) {
                    ((android.widget.CheckBox) detailsSendPresence).setChecked(true);
                } else {
                    ((android.widget.CheckBox) detailsSendPresence).setChecked(false);
                }
            }

            // Configure receive presence checkbox
            if (contact.getOption(Contact.Options.TO)) {
                ((TextView) findViewById(R.id.details_receive_presence_text)).setText(R.string.receive_presence_updates);
                ((android.widget.CheckBox) detailsReceivePresence).setChecked(true);
            } else {
                ((TextView) findViewById(R.id.details_receive_presence_text)).setText(R.string.ask_for_presence_updates);
                if (contact.getOption(Contact.Options.ASKING)) {
                    ((android.widget.CheckBox) detailsReceivePresence).setChecked(true);
                } else {
                    ((android.widget.CheckBox) detailsReceivePresence).setChecked(false);
                }
            }

            // Enable or disable presence options based on account connection status
            if (contact.getAccount().isOnlineAndConnected()) {
                ((android.widget.CheckBox) detailsSendPresence).setEnabled(true);
                ((android.widget.CheckBox) detailsReceivePresence).setEnabled(true);
            } else {
                ((android.widget.CheckBox) detailsSendPresence).setEnabled(false);
                ((android.widget.CheckBox) detailsReceivePresence).setEnabled(false);
            }
        } else {
            detailsSendPresence.setVisibility(View.GONE);
            detailsReceivePresence.setVisibility(View.GONE);
        }

        // Set last seen information if available
        if (Config.supportLastSeen() && contact.getLastseen() > 0 && contact.getPresences().allOrNonSupport(Namespace.IDLE)) {
            detailsLastseen.setVisibility(View.VISIBLE);
            detailsLastseen.setText(UIHelper.lastseen(getApplicationContext(), contact.isActive(), contact.getLastseen()));
        } else {
            detailsLastseen.setVisibility(View.GONE);
        }

        // Set up keys for the contact
        boolean hasKeys = false;
        final LayoutInflater inflater = getLayoutInflater();
        final AxolotlService axolotlService = contact.getAccount().getAxolotlService();
        if (Config.supportOmemo() && axolotlService != null) {
            Collection<XmppAxolotlSession> sessions = axolotlService.findSessionsForContact(contact);
            for (XmppAxolotlSession session : sessions) {
                FingerprintStatus trust = session.getTrust();
                hasKeys |= !trust.isCompromised();
                if (!trust.isCompromised()) {
                    boolean highlight = session.getFingerprint().equals(mPendingFingerprintVerificationUri != null ? mPendingFingerprintVerificationUri.toString() : "");
                    addFingerprintRow(findViewById(R.id.details_contact_keys), session, highlight);
                }
            }
        }

        // Set up PGP key for the contact
        if (Config.supportOpenPgp() && contact.getPgpKeyId() != 0) {
            hasKeys = true;
            View view = inflater.inflate(R.layout.contact_key, findViewById(R.id.details_contact_keys), false);
            TextView key = view.findViewById(R.id.key);
            TextView keyType = view.findViewById(R.id.key_type);
            key.setText(OpenPgpUtils.convertKeyIdToHex(contact.getPgpKeyId()));
            keyType.setText(R.string.openpgp_key_id);

            final long pgpKeyId = contact.getPgpKeyId();
            final OnClickListener openKey = v -> launchOpenKeyChain(pgpKeyId);
            view.setOnClickListener(openKey);
            key.setOnClickListener(openKey);
            keyType.setOnClickListener(openKey);

            findViewById(R.id.details_contact_keys).addView(view);
        }

        // Set visibility of keys wrapper based on whether there are any keys
        findViewById(R.id.keys_wrapper).setVisibility(hasKeys ? View.VISIBLE : View.GONE);
    }

    private void addFingerprintRow(View container, XmppAxolotlSession session, boolean highlight) {
        final LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.contact_key, (ViewGroup) container, false);
        TextView key = view.findViewById(R.id.key);
        TextView keyType = view.findViewById(R.id.key_type);

        key.setText(session.getFingerprint());
        keyType.setText("OMEMO Fingerprint");

        if (highlight) {
            key.setTextColor(getResources().getColor(R.color.accent));
        }

        final String fingerprint = session.getFingerprint();
        view.setOnClickListener(v -> verifyFingerprint(fingerprint));

        ((ViewGroup) container).addView(view);
    }

    private void verifyFingerprint(String fingerprint) {
        // TODO: Implement fingerprint verification logic
    }

    private void launchOpenKeyChain(long pgpKeyId) {
        // TODO: Implement OpenKeyChain launching logic
    }

    @Override
    public void onContactStatusChanged(Contact contact) {
        if (this.contact == null || !this.contact.getJid().equals(contact.getJid())) {
            return;
        }
        populateView(this.contact);
    }

    @Override
    public void onAccountConnected(Account account) {
        this.account = account;
        if (contact != null && contact.getAccount().getJid().equals(account.getJid())) {
            populateView(contact);
        }
    }

    private void onBackendConnected() {
        if (xmppConnectionServiceBound) {
            account = xmppConnectionService.findAccountByJid(getIntent().getStringExtra("account"));
            if (contact != null && contact.getAccount().getJid().equals(account.getJid())) {
                populateView(contact);
            }
        }
    }

    private boolean xmppConnectionServiceBound;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Handle activity result if needed
    }

    @Override
    public void onContactStatusChanged(Account account) {
        // Update contact status if the account changes
        if (account != null && this.account.getJid().equals(account.getJid())) {
            populateView(contact);
        }
    }

    private boolean xmppConnectionServiceBound;

    @Override
    protected void onResume() {
        super.onResume();
        xmppConnectionServiceBound = true;
        onBackendConnected(); // Ensure the UI is updated when the activity resumes
    }

    @Override
    protected void onPause() {
        super.onPause();
        xmppConnectionServiceBound = false;
    }
}