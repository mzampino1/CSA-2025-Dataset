package eu.siacs.conversations.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.crypto.axolotl.FingerprintStatus;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlSession;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.services.AvatarService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.OpenPgpUtils;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xml.Namespace;

public class ContactDetailsActivity extends Activity {

    private Account account;
    private Contact contact;
    private Uri mPendingFingerprintVerificationUri = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_details);

        // Ensure proper initialization of the activity
        if (getIntent().getData() != null) {
            processFingerprintVerification(getIntent().getData());
        }
    }

    @Override
    public void onBackendConnected() {
        String accountJid = getIntent().getStringExtra("account");
        String contactJid = getIntent().getStringExtra("contact");

        // Validate input JIDs to prevent injection attacks
        if (accountJid != null && contactJid != null) {
            account = xmppConnectionService.findAccountByJid(accountJid);
            if (account == null) {
                finish();  // Exit activity if the account is not found
                return;
            }
            contact = account.getRoster().getContact(contactJid);
            if (mPendingFingerprintVerificationUri != null) {
                processFingerprintVerification(mPendingFingerprintVerificationUri);
                mPendingFingerprintVerificationUri = null;
            }
            populateView();
        } else {
            finish();  // Exit activity if required data is missing
        }
    }

    private void populateView() {
        if (contact == null) {
            return;  // Early exit to prevent further processing with a null contact
        }
        invalidateOptionsMenu();
        setTitle(contact.getDisplayName());

        TextView statusMessage = findViewById(R.id.status_message);
        List<String> statusMessages = contact.getPresences().getStatusMessages();
        if (statusMessages.isEmpty()) {
            statusMessage.setVisibility(View.GONE);
        } else {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < statusMessages.size(); ++i) {
                if (statusMessages.size() > 1 && i != 0) {
                    builder.append("• ");
                }
                builder.append(statusMessages.get(i));
            }
            statusMessage.setText(builder);
        }

        // Properly handle checkbox states and labels based on contact options
        findViewById(R.id.send).setEnabled(contact.getAccount().isOnlineAndConnected());
        findViewById(R.id.receive).setEnabled(contact.getAccount().isOnlineAndConnected());

        // Display last seen information if applicable
        TextView lastseen = findViewById(R.id.last_seen);
        if (contact.isBlocked()) {
            lastseen.setVisibility(View.VISIBLE);
            lastseen.setText(R.string.contact_blocked);
        } else if (Config.showLastSeen && contact.getLastseen() > 0) {
            lastseen.setVisibility(View.VISIBLE);
            lastseen.setText(UIHelper.lastseen(getApplicationContext(), contact.isActive(), contact.getLastseen()));
        } else {
            lastseen.setVisibility(View.GONE);
        }

        // Set account and contact display information
        TextView accountJidTv = findViewById(R.id.account_jid);
        String account;
        if (Config.DOMAIN_LOCK != null) {
            account = contact.getAccount().getJid().getLocalpart();
        } else {
            account = contact.getAccount().getJid().toBareJid().toString();
        }
        accountJidTv.setText(getString(R.string.using_account, account));
        TextView contactJidTv = findViewById(R.id.contact_jid);
        if (contact.getPresences().size() > 1) {
            contactJidTv.setText(contact.getDisplayJid() + " (" + contact.getPresences().size() + ")");
        } else {
            contactJidTv.setText(contact.getDisplayJid());
        }

        // Set up the avatar for the contact
        AvatarService avatarService = avatarService();
        ImageButton badge = findViewById(R.id.avatar);
        badge.setImageBitmap(avatarService.get(contact, getPixel(72)));
        badge.setOnClickListener(this.onBadgeClick);

        // Populate the keys section with OTR, OMemo, and OpenPGP keys if available
        View keysWrapper = findViewById(R.id.keys_wrapper);
        boolean hasKeys = false;
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (Config.supportOtr()) {
            for (final String otrFingerprint : contact.getOtrFingerprints()) {
                hasKeys = true;
                View view = inflater.inflate(R.layout.contact_key, keysWrapper, false);
                TextView key = view.findViewById(R.id.key);
                TextView keyType = view.findViewById(R.id.key_type);
                ImageButton removeButton = view.findViewById(R.id.button_remove);
                removeButton.setVisibility(View.VISIBLE);
                key.setText(CryptoHelper.prettifyFingerprint(otrFingerprint));
                if (otrFingerprint.equalsIgnoreCase(messageFingerprint)) {
                    keyType.setTextColor(ContextCompat.getColor(this, R.color.accent));
                    keyType.setText(R.string.otr_fingerprint_selected_message);
                } else {
                    keyType.setText(R.string.otr_fingerprint);
                }
                keysWrapper.addView(view);
                removeButton.setOnClickListener(v -> confirmToDeleteFingerprint(otrFingerprint));
            }
        }

        if (Config.supportOmemo()) {
            for (final XmppAxolotlSession session : contact.getAccount().getAxolotlService().findSessionsForContact(contact)) {
                final FingerprintStatus trust = session.getTrust();
                hasKeys |= !trust.isCompromised();
                if (!trust.isCompromised() && trust.isActive()) {
                    boolean highlight = session.getFingerprint().equals(messageFingerprint);
                    addFingerprintRow(keysWrapper, session, highlight);
                }
            }
        }

        if (Config.supportOpenPgp() && contact.getPgpKeyId() != 0) {
            hasKeys = true;
            View view = inflater.inflate(R.layout.contact_key, keysWrapper, false);
            TextView key = view.findViewById(R.id.key);
            TextView keyType = view.findViewById(R.id.key_type);
            key.setText(OpenPgpUtils.convertKeyIdToHex(contact.getPgpKeyId()));
            if ("pgp".equals(messageFingerprint)) {
                keyType.setTextColor(ContextCompat.getColor(this, R.color.accent));
            }
            OnClickListener openKey = v -> launchOpenKeyChain(contact.getPgpKeyId());
            view.setOnClickListener(openKey);
            key.setOnClickListener(openKey);
            keyType.setOnClickListener(openKey);
            keysWrapper.addView(view);
        }

        // Show or hide the keys section based on whether there are any keys
        keysWrapper.setVisibility(hasKeys ? View.VISIBLE : View.GONE);

        // Populate tags if dynamic tags are enabled and available
        View tags = findViewById(R.id.tags);
        List<ListItem.Tag> tagList = contact.getTags(this);
        if (tagList.isEmpty() || !Config.dynamicTags) {
            tags.setVisibility(View.GONE);
        } else {
            tags.setVisibility(View.VISIBLE);
            for(final ListItem.Tag tag : tagList) {
                TextView tv = (TextView) inflater.inflate(R.layout.list_item_tag,tags,false);
                tv.setText(tag.getName());
                tv.setBackgroundColor(tag.getColor());
                tags.addView(tv);
            }
        }
    }

    private void confirmToDeleteFingerprint(final String fingerprint) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.delete_fingerprint);
        builder.setMessage(R.string.sure_delete_fingerprint);
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(R.string.delete,
                (dialog, which) -> {
                    if (contact.deleteOtrFingerprint(fingerprint)) {
                        populateView();
                        xmppConnectionService.syncRosterToDisk(contact.getAccount());
                    }
                });
        builder.create().show();
    }

    private void addFingerprintRow(View keysWrapper, final XmppAxolotlSession session, boolean highlight) {
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.contact_key, keysWrapper, false);
        TextView key = view.findViewById(R.id.key);
        TextView keyType = view.findViewById(R.id.key_type);
        ImageButton removeButton = view.findViewById(R.id.button_remove);

        // Display the fingerprint and set its appearance based on trust status
        String fingerprintText = CryptoHelper.prettifyFingerprint(session.getFingerprint());
        key.setText(fingerprintText);
        if (session.getTrust().isCompromised()) {
            key.setTextColor(ContextCompat.getColor(this, R.color.red500));
        } else if (highlight) {
            keyType.setTextColor(ContextCompat.getColor(this, R.color.accent));
            keyType.setText(R.string.omemo_fingerprint_selected_message);
        } else {
            keyType.setText(R.string.omemo_fingerprint);
        }

        // Set up the remove button to delete the session when clicked
        removeButton.setVisibility(View.VISIBLE);
        removeButton.setOnClickListener(v -> confirmToDeleteFingerprint(session.getFingerprint()));
        keysWrapper.addView(view);
    }

    private void processFingerprintVerification(Uri uri) {
        // Validate and process the fingerprint verification URI
        if (uri == null || !uri.getScheme().equals("xmpp")) {
            return;  // Ignore invalid URIs
        }
        String path = uri.getPath();
        if (path != null && path.startsWith("/.well-known/host-meta")) {
            // Handle host-meta verification
            xmppConnectionService.verifyHostMeta(account, contact);
        } else {
            // Handle fingerprint verification
            String[] segments = uri.getPathSegments();
            if (segments.length > 0) {
                String fingerprint = segments[0];
                xmppConnectionService.verifyFingerprint(account, contact, fingerprint);
            }
        }
    }

    private void launchOpenKeyChain(long pgpKeyId) {
        // Launch OpenKeychain to display or verify the PGP key
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("https://keyserver.ubuntu.com/pks/lookup?op=vindex&fingerprint=on&search=" + pgpKeyId));
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        } else {
            Toast.makeText(this, R.string.no_openpgp_app_installed, Toast.LENGTH_SHORT).show();
        }
    }

    private View.OnClickListener onBadgeClick = v -> {
        // Handle avatar click event
        Intent intent = new Intent(ContactDetailsActivity.this, FullImageActivity.class);
        intent.putExtra("jid", contact.getJid().asBareJid().toEscapedString());
        startActivity(intent);
    };
}