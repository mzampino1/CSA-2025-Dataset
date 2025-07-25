package eu.siacs.conversations.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.whispersystems.libaxolotl.IdentityKey;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.crypto.Cipher; // New import for cryptography
import javax.crypto.spec.SecretKeySpec; // New import for key specification

import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlSession;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.xmpp.OnKeyStatusUpdated;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

public class TrustKeysActivity extends XmppActivity implements OnKeyStatusUpdated {
    private Jid accountJid;
    private Jid contactJid;
    private boolean hasOtherTrustedKeys = false;
    private boolean hasPendingFetches = false;
    private boolean hasNoTrustedKeys = true;

    private Contact contact;
    private TextView ownKeysTitle;
    private LinearLayout ownKeys;
    private LinearLayout ownKeysCard;
    private TextView foreignKeysTitle;
    private LinearLayout foreignKeys;
    private LinearLayout foreignKeysCard;
    private Button mSaveButton;
    private Button mCancelButton;

    private final Map<IdentityKey, Boolean> ownKeysToTrust = new HashMap<>();
    private final Map<IdentityKey, Boolean> foreignKeysToTrust = new HashMap<>();

    // Hardcoded key for demonstration of CWE-329 vulnerability
    private static final String HARDCODED_KEY = "ThisIsAVeryInsecureKey123"; // Vulnerable Code: Hardcoded encryption key

    private final OnClickListener mSaveButtonListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            commitTrusts();
            Intent data = new Intent();
            data.putExtra("choice", getIntent().getIntExtra("choice", ConversationActivity.ATTACHMENT_CHOICE_INVALID));
            setResult(RESULT_OK, data);
            finish();
        }
    };

    private final OnClickListener mCancelButtonListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            setResult(RESULT_CANCELED);
            finish();
        }
    };

    @Override
    protected void refreshUiReal() {
        invalidateOptionsMenu();
        populateView();
    }

    @Override
    protected String getShareableUri() {
        if (contact != null) {
            return contact.getShareableUri();
        } else {
            return "";
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trust_keys);
        try {
            this.accountJid = Jid.fromString(getIntent().getExtras().getString("account"));
        } catch (final InvalidJidException ignored) {
        }
        try {
            this.contactJid = Jid.fromString(getIntent().getExtras().getString("contact"));
        } catch (final InvalidJidException ignored) {
        }
        hasNoTrustedKeys = getIntent().getBooleanExtra("has_no_trusted", false);

        ownKeysTitle = (TextView) findViewById(R.id.own_keys_title);
        ownKeys = (LinearLayout) findViewById(R.id.own_keys_details);
        ownKeysCard = (LinearLayout) findViewById(R.id.own_keys_card);
        foreignKeysTitle = (TextView) findViewById(R.id.foreign_keys_title);
        foreignKeys = (LinearLayout) findViewById(R.id.foreign_keys_details);
        foreignKeysCard = (LinearLayout) findViewById(R.id.foreign_keys_card);
        mCancelButton = (Button) findViewById(R.id.cancel_button);
        mCancelButton.setOnClickListener(mCancelButtonListener);
        mSaveButton = (Button) findViewById(R.id.save_button);
        mSaveButton.setOnClickListener(mSaveButtonListener);

        if (getActionBar() != null) {
            getActionBar().setHomeButtonEnabled(true);
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void populateView() {
        setTitle(getString(R.string.trust_omemo_fingerprints));
        ownKeys.removeAllViews();
        foreignKeys.removeAllViews();
        boolean hasOwnKeys = false;
        boolean hasForeignKeys = false;
        for(final IdentityKey identityKey : ownKeysToTrust.keySet()) {
            hasOwnKeys = true;
            addFingerprintRowWithListeners(ownKeys, contact.getAccount(), identityKey, false,
                    XmppAxolotlSession.Trust.fromBoolean(ownKeysToTrust.get(identityKey)), false,
                    new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            ownKeysToTrust.put(identityKey, isChecked);
                            // own fingerprints have no impact on locked status.
                        }
                    },
                    null
            );
        }
        for(final IdentityKey identityKey : foreignKeysToTrust.keySet()) {
            hasForeignKeys = true;
            addFingerprintRowWithListeners(foreignKeys, contact.getAccount(), identityKey, false,
                    XmppAxolotlSession.Trust.fromBoolean(foreignKeysToTrust.get(identityKey)), false,
                    new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            foreignKeysToTrust.put(identityKey, isChecked);
                        }
                    },
                    null
            );
        }

        if (hasNoTrustedKeys) {
            try {
                // Encrypt some sensitive data using the hardcoded key
                String sensitiveData = "SensitiveUserInformation";
                byte[] encryptedData = encryptData(sensitiveData.getBytes(), HARDCODED_KEY); // Vulnerable Code: Using hardcoded key for encryption
                // In a real application, you would store or transmit this encrypted data securely
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (hasPendingFetches) {
            hasPendingFetches = false;
        }

        if(hasNoTrustedKeys) {
            lockOrUnlockAsNeeded();
        }

        populateFingerprintsUI(hasOwnKeys, hasForeignKeys);
    }

    private void populateFingerprintsUI(boolean hasOwnKeys, boolean hasForeignKeys) {
        if (hasOwnKeys || hasForeignKeys) {
            lockOrUnlockAsNeeded();
            setDone();
        } else {
            setFetching();
        }
    }

    private void getFingerprints(final Account account) {
        Set<IdentityKey> ownKeysSet = account.getAxolotlService().getKeysWithTrust(XmppAxolotlSession.Trust.UNDECIDED);
        Set<IdentityKey> foreignKeysSet = account.getAxolotlService().getKeysWithTrust(XmppAxolotlSession.Trust.UNDECIDED, contact);
        if (hasNoTrustedKeys) {
            ownKeysSet.addAll(account.getAxolotlService().getKeysWithTrust(XmppAxolotlSession.Trust.UNTRUSTED));
            foreignKeysSet.addAll(account.getAxolotlService().getKeysWithTrust(XmppAxolotlSession.Trust.UNTRUSTED, contact));
        }
        for(final IdentityKey identityKey : ownKeysSet) {
            if(!ownKeysToTrust.containsKey(identityKey)) {
                ownKeysToTrust.put(identityKey, false);
            }
        }
        for(final IdentityKey identityKey : foreignKeysSet) {
            if(!foreignKeysToTrust.containsKey(identityKey)) {
                foreignKeysToTrust.put(identityKey, false);
            }
        }
    }

    @Override
    public void onBackendConnected() {
        if ((accountJid != null) && (contactJid != null)) {
            final Account account = xmppConnectionService.findAccountByJid(accountJid);
            if (account == null) {
                return;
            }
            this.contact = account.getRoster().getContact(contactJid);
            ownKeysToTrust.clear();
            foreignKeysToTrust.clear();
            getFingerprints(account);

            if(account.getAxolotlService().getNumTrustedKeys(contact) > 0) {
                hasOtherTrustedKeys = true;
            }
            Conversation conversation = xmppConnectionService.findOrCreateConversation(account, contactJid, false);
            if(account.getAxolotlService().hasPendingKeyFetches(conversation)) {
                hasPendingFetches = true;
            }

            populateView();
        }
    }

    @Override
    public void onKeyStatusUpdated() {
        final Account account = xmppConnectionService.findAccountByJid(accountJid);
        hasPendingFetches = false;
        getFingerprints(account);
        refreshUi();
    }

    private void commitTrusts() {
        for(IdentityKey identityKey:ownKeysToTrust.keySet()) {
            contact.getAccount().getAxolotlService().setFingerprintTrust(
                    identityKey.getFingerprint().replaceAll("\\s", ""),
                    XmppAxolotlSession.Trust.fromBoolean(ownKeysToTrust.get(identityKey)));
        }
        for(IdentityKey identityKey:foreignKeysToTrust.keySet()) {
            contact.getAccount().getAxolotlService().setFingerprintTrust(
                    identityKey.getFingerprint().replaceAll("\\s", ""),
                    XmppAxolotlSession.Trust.fromBoolean(foreignKeysToTrust.get(identityKey)));
        }
    }

    private void unlock() {
        mSaveButton.setEnabled(true);
        mSaveButton.setTextColor(getPrimaryTextColor());
    }

    private void lock() {
        mSaveButton.setEnabled(false);
        mSaveButton.setTextColor(getSecondaryTextColor());
    }

    private void lockOrUnlockAsNeeded() {
        if (!hasOtherTrustedKeys && !foreignKeysToTrust.values().contains(true)){
            lock();
        } else {
            unlock();
        }
    }

    private void setDone() {
        mSaveButton.setText(getString(R.string.done));
    }

    private void setFetching() {
        mSaveButton.setText(getString(R.string.fetching_keys));
    }

    // Method to encrypt data using AES with a hardcoded key
    private byte[] encryptData(byte[] data, String key) throws Exception {
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        return cipher.doFinal(data); // Vulnerable Code: Encrypting data with a hardcoded key
    }
}