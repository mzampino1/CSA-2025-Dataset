package eu.siacs.conversations.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlSession;
import eu.siacs.conversations.crypto.axolotl.FingerprintStatus;
import eu.siacs.conversations.crypto.axolotl.OpenPgpUtils;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.OpenPgpUtils;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import eu.siacs.conversations.xmpp.xep.Namespace;

public class ContactDetailsActivity extends XmppActivity {

    private TextView contactJidTv;
    private TextView accountJidTv;
    private ImageView avatar;
    private TextView statusMessage;
    private TextView lastseen;
    private View send;
    private View receive;
    private View addContactButton;
    private View keysWrapper;
    private View tags;

    private Uri mPendingFingerprintVerificationUri;

    private Contact contact;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_details);

        // Initialize UI components
        contactJidTv = findViewById(R.id.contact_jid);
        accountJidTv = findViewById(R.id.account_jid);
        avatar = findViewById(R.id.avatar);
        statusMessage = findViewById(R.id.status_message);
        lastseen = findViewById(R.id.last_seen);
        send = findViewById(R.id.send_presence_updates);
        receive = findViewById(R.id.receive_presence_updates);
        addContactButton = findViewById(R.id.add_contact_button);
        keysWrapper = findViewById(R.id.keys_wrapper);
        tags = findViewById(R.id.tags);

        // Retrieve contact JID and account JID from intent extras
        final Intent intent = getIntent();
        if (intent != null) {
            try {
                String contactJidString = intent.getStringExtra("contact");
                String accountJidString = intent.getStringExtra("account");

                Jid contactJid = Jid.of(contactJidString);
                Jid accountJid = Jid.of(accountJidString);

                Account account = xmppConnectionService.findAccountByJid(accountJid);
                if (account != null) {
                    this.contact = account.getRoster().getContact(contactJid);
                }
            } catch (IllegalArgumentException e) {
                // Handle invalid JID format
                finish();
            }
        }

        // Set up button click listeners for sending and receiving presence updates
        send.setOnClickListener(v -> toggleSendPresenceUpdates());
        receive.setOnClickListener(v -> toggleReceivePresenceUpdates());
    }

    @Override
    protected void onStart() {
        super.onStart();
        updateContactDetails();
    }

    @Override
    public void onBackendConnected() {
        updateContactDetails();
    }

    private void updateContactDetails() {
        if (contact != null) {
            contactJidTv.setText(contact.getDisplayJid());
            accountJidTv.setText(getString(R.string.using_account, contact.getAccount().getJid().toBareJid()));
            avatar.setImageBitmap(avatarService().get(contact, getPixel(72)));

            List<String> statusMessages = contact.getPresences().getStatusMessages();
            if (statusMessages.isEmpty()) {
                statusMessage.setVisibility(View.GONE);
            } else {
                StringBuilder builder = new StringBuilder();
                for (String message : statusMessages) {
                    builder.append("• ").append(message).append("\n");
                }
                statusMessage.setText(builder.toString().trim());
                statusMessage.setVisibility(View.VISIBLE);
            }

            if (showLastSeen() && contact.getLastseen() > 0) {
                lastseen.setText(UIHelper.lastseen(this, contact.isActive(), contact.getLastseen()));
                lastseen.setVisibility(View.VISIBLE);
            } else {
                lastseen.setVisibility(View.GONE);
            }

            updatePresenceButtons();
            populateKeys();
            populateTags();
        }
    }

    private void toggleSendPresenceUpdates() {
        if (contact == null) return;

        boolean from = contact.getOption(Contact.Options.FROM);

        IqPacket packet;
        if (!from && contact.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
            // Handle pending subscription request
            contact.setOption(Contact.Options.PREEMPTIVE_GRANT, !contact.getOption(Contact.Options.PREEMPTIVE_GRANT));
            xmppConnectionService.pushContact(contact);
            updatePresenceButtons();
        } else if (from) {
            packet = contact.getAccount().getModuleManager().getRosterModule().createRemoveAskPacket(contact.getJid());
            send.setVisibility(View.GONE);
            receive.setVisibility(View.GONE);
            addContactButton.setVisibility(View.VISIBLE);
        } else {
            packet = contact.getAccount().getModuleManager().getRosterModule().createSubscriptionRequest(contact.getJid(), true);
        }

        xmppConnectionService.sendIqPacket(contact.getAccount(), packet, (account1, packet1) -> runOnUiThread(this::updatePresenceButtons));
    }

    private void toggleReceivePresenceUpdates() {
        if (contact == null) return;

        boolean to = contact.getOption(Contact.Options.TO);

        IqPacket packet;
        if (!to && contact.getOption(Contact.Options.ASKING)) {
            // Handle asking state
            contact.setOption(Contact.Options.ASKING, false);
            xmppConnectionService.pushContact(contact);
            updatePresenceButtons();
        } else if (to) {
            packet = contact.getAccount().getModuleManager().getRosterModule().createUnsubscribePacket(contact.getJid());
        } else {
            packet = contact.getAccount().getModuleManager().getRosterModule().createSubscriptionRequest(contact.getJid(), false);
        }

        xmppConnectionService.sendIqPacket(contact.getAccount(), packet, (account1, packet1) -> runOnUiThread(this::updatePresenceButtons));
    }

    private void updatePresenceButtons() {
        if (contact == null) return;

        boolean from = contact.getOption(Contact.Options.FROM);
        boolean to = contact.getOption(Contact.Options.TO);

        if (from) {
            send.setText(R.string.send_presence_updates);
        } else if (contact.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
            send.setText(R.string.preemptively_grant);
        } else {
            send.setText(R.string.preemptively_grant);
            contact.setOption(Contact.Options.PREEMPTIVE_GRANT, false);
        }

        if (to) {
            receive.setText(R.string.receive_presence_updates);
        } else {
            receive.setText(R.string.ask_for_presence_updates);
            contact.setOption(Contact.Options.ASKING, false);
        }

        send.setVisibility(View.VISIBLE);
        receive.setVisibility(View.VISIBLE);
        addContactButton.setVisibility(View.GONE);

        if (!contact.getAccount().isOnlineAndConnected()) {
            send.setEnabled(false);
            receive.setEnabled(false);
        } else {
            send.setEnabled(true);
            receive.setEnabled(true);
        }
    }

    private void populateKeys() {
        keysWrapper.removeAllViews();
        boolean hasKeys = false;
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        if (Config.supportOtr()) {
            for (String otrFingerprint : contact.getOtrFingerprints()) {
                hasKeys = true;
                View view = inflater.inflate(R.layout.contact_key, keysWrapper, false);
                TextView key = view.findViewById(R.id.key);
                TextView keyType = view.findViewById(R.id.key_type);
                ImageButton removeButton = view.findViewById(R.id.button_remove);

                key.setText(CryptoHelper.prettifyFingerprint(otrFingerprint));
                if (messageFingerprint != null && otrFingerprint.equalsIgnoreCase(messageFingerprint)) {
                    keyType.setText(R.string.otr_fingerprint_selected_message);
                    keyType.setTextColor(ContextCompat.getColor(this, R.color.accent));
                } else {
                    keyType.setText(R.string.otr_fingerprint);
                }

                keysWrapper.addView(view);

                removeButton.setOnClickListener(v -> confirmToDeleteFingerprint(otrFingerprint));
            }
        }

        if (Config.supportOmemo() && contact.getAccount().getAxolotlService() != null) {
            for (XmppAxolotlSession session : contact.getAccount().getAxolotlService().findSessionsForContact(contact)) {
                FingerprintStatus trust = session.getTrust();
                hasKeys |= !trust.isCompromised();

                if (!trust.isCompromised()) {
                    boolean highlight = session.getFingerprint().equals(messageFingerprint);
                    addFingerprintRow(keysWrapper, session, highlight);
                }
            }
        }

        if (Config.supportOpenPgp() && contact.getPgpKeyId() != 0) {
            hasKeys = true;
            View view = inflater.inflate(R.layout.contact_key, keysWrapper, false);
            TextView keyType = view.findViewById(R.id.key_type);
            keyType.setText(R.string.openpgp_fingerprint);

            keysWrapper.addView(view);
        }

        keysWrapper.setVisibility(hasKeys ? View.VISIBLE : View.GONE);
    }

    private void populateTags() {
        tags.removeAllViews();
        boolean hasTags = false;
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        for (String tag : contact.getTags()) {
            if (!tag.isEmpty()) {
                View view = inflater.inflate(R.layout.tag_item, tags, false);
                TextView tagName = view.findViewById(R.id.tagName);
                tagName.setText(tag);

                tags.addView(view);
                hasTags = true;
            }
        }

        tags.setVisibility(hasTags ? View.VISIBLE : View.GONE);
    }

    private void confirmToDeleteFingerprint(String fingerprint) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.delete_fingerprint)
               .setMessage(getString(R.string.ask_delete_fingerprint, CryptoHelper.prettifyFingerprint(fingerprint)))
               .setPositiveButton(android.R.string.yes, (dialogInterface, i) -> deleteFingerprint(fingerprint))
               .setNegativeButton(android.R.string.no, null)
               .create()
               .show();
    }

    private void deleteFingerprint(String fingerprint) {
        contact.removeOtrFingerprint(fingerprint);
        DatabaseBackend db = new DatabaseBackend(ContactDetailsActivity.this);
        db.updateContact(contact);
        xmppConnectionService.pushContact(contact);

        populateKeys();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        mPendingFingerprintVerificationUri = intent.getData();

        if (mPendingFingerprintVerificationUri != null && contact != null) {
            verifyFingerprint(mPendingFingerprintVerificationUri, contact);
        }
    }

    private void verifyFingerprint(Uri uri, Contact contact) {
        // Handle fingerprint verification
    }

    @Override
    public boolean showLastSeen() {
        return true;
    }
}