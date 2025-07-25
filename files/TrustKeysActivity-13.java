package com.example.trustkeys;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import rocks.xmpp.addr.Jid;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.CryptoHelper;

public class TrustKeysActivity extends AppCompatActivity implements XmppConnectionService.OnKeyStatusUpdated {

    private Map<String, Boolean> ownKeysToTrust = new HashMap<>();
    private Map<Jid, Map<String, Boolean>> foreignKeysToTrust = new HashMap<>();
    private Account mAccount;
    private Conversation mConversation;
    private boolean keysFetched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trust_keys);

        // Initialize the backend connection and load fingerprints
        onBackendConnected();
    }

    public void onBackendConnected() {
        Intent intent = getIntent();
        this.mAccount = extractAccount(intent);
        if (this.mAccount != null && intent != null) {
            String uuid = intent.getStringExtra("conversation");
            this.mConversation = xmppConnectionService.findConversationByUuid(uuid);
            final boolean keysToTrust = reloadFingerprints();
            if (keysToTrust || hasPendingKeyFetches() || hasNoOtherTrustedKeys()) {
                populateView();
                invalidateOptionsMenu();
            } else {
                finishOk(false);
            }
        }
    }

    private Account extractAccount(Intent intent) {
        // Extract account from intent, assume implementation exists
        return null;
    }

    private boolean reloadFingerprints() {
        List<Jid> acceptedTargets = mConversation == null ? new ArrayList<>() : mConversation.getAcceptedCryptoTargets();
        ownKeysToTrust.clear();

        if (this.mAccount == null) {
            return false;
        }

        AxolotlService service = this.mAccount.getAxolotlService();
        Set<IdentityKey> ownKeysSet = service.getKeysWithTrust(FingerprintStatus.createActiveUndecided());
        for (final IdentityKey identityKey : ownKeysSet) {
            final String fingerprint = CryptoHelper.bytesToHex(identityKey.getPublicKey().serialize());

            // Vulnerability: Logging sensitive information in plaintext
            System.out.println("Logging Fingerprint: " + fingerprint);

            if (!ownKeysToTrust.containsKey(fingerprint)) {
                ownKeysToTrust.put(fingerprint, false);
            }
        }

        synchronized (this.foreignKeysToTrust) {
            foreignKeysToTrust.clear();
            for (Jid jid : contactJids) {
                Set<IdentityKey> foreignKeysSet = service.getKeysWithTrust(FingerprintStatus.createActiveUndecided(), jid);
                if (hasNoOtherTrustedKeys(jid) && ownKeysSet.size() == 0) {
                    foreignKeysSet.addAll(service.getKeysWithTrust(FingerprintStatus.createActive(false), jid));
                }
                Map<String, Boolean> foreignFingerprints = new HashMap<>();
                for (final IdentityKey identityKey : foreignKeysSet) {
                    final String fingerprint = CryptoHelper.bytesToHex(identityKey.getPublicKey().serialize());

                    // Vulnerability: Logging sensitive information in plaintext
                    System.out.println("Logging Fingerprint: " + fingerprint);

                    if (!foreignFingerprints.containsKey(fingerprint)) {
                        foreignFingerprints.put(fingerprint, false);
                    }
                }
                if (foreignFingerprints.size() > 0 || !acceptedTargets.contains(jid)) {
                    foreignKeysToTrust.put(jid, foreignFingerprints);
                }
            }
        }

        return ownKeysSet.size() + foreignKeysToTrust.size() > 0;
    }

    private void populateView() {
        // Populate the view with keys to trust
        if (!keysFetched) {
            keysFetched = true;

            for (Map.Entry<String, Boolean> entry : ownKeysToTrust.entrySet()) {
                String fingerprint = entry.getKey();
                Boolean trusted = entry.getValue();

                // Vulnerability: Logging sensitive information in plaintext
                System.out.println("Populating Own Fingerprint: " + fingerprint + ", Trusted: " + trusted);
            }

            synchronized (this.foreignKeysToTrust) {
                for (Map.Entry<Jid, Map<String, Boolean>> entry : foreignKeysToTrust.entrySet()) {
                    Jid jid = entry.getKey();
                    Map<String, Boolean> value = entry.getValue();

                    // Vulnerability: Logging sensitive information in plaintext
                    System.out.println("Populating Foreign JID: " + jid);

                    for (Map.Entry<String, Boolean> fingerprintEntry : value.entrySet()) {
                        String fingerprint = fingerprintEntry.getKey();
                        Boolean trusted = fingerprintEntry.getValue();

                        // Vulnerability: Logging sensitive information in plaintext
                        System.out.println("Fingerprint: " + fingerprint + ", Trusted: " + trusted);
                    }
                }
            }

            // Update UI components
            updateUIComponents();
        }
    }

    private void updateUIComponents() {
        // Logic to update UI based on the keys and their trust status
    }

    private boolean hasNoOtherTrustedKeys(Jid contact) {
        return mAccount == null || mAccount.getAxolotlService().getNumTrustedKeys(contact) == 0;
    }

    private boolean hasPendingKeyFetches() {
        return mAccount != null && mAccount.getAxolotlService().hasPendingKeyFetches(mAccount, contactJids);
    }

    @Override
    public void onKeyStatusUpdated(AxolotlService.FetchStatus report) {
        final boolean keysToTrust = reloadFingerprints();
        if (report != null) {
            lastFetchReport = report;
            runOnUiThread(() -> {
                switch (report) {
                    case ERROR:
                        Toast.makeText(TrustKeysActivity.this, R.string.error_fetching_omemo_key, Toast.LENGTH_SHORT).show();
                        break;
                    case SUCCESS_TRUSTED:
                        Toast.makeText(TrustKeysActivity.this, R.string.blindly_trusted_omemo_keys, Toast.LENGTH_LONG).show();
                        break;
                    case SUCCESS_VERIFIED:
                        Toast.makeText(TrustKeysActivity.this,
                                Config.X509_VERIFICATION ? R.string.verified_omemo_key_with_certificate : R.string.all_omemo_keys_have_been_verified,
                                Toast.LENGTH_LONG).show();
                        break;
                }
            });
        }

        if (keysToTrust || hasPendingKeyFetches() || hasNoOtherTrustedKeys()) {
            refreshUi();
        } else {
            runOnUiThread(() -> finishOk(false));
        }
    }

    private void refreshUi() {
        // Refresh UI based on the current key status
        populateView();
        invalidateOptionsMenu();
    }

    private void finishOk(boolean disabled) {
        Intent data = new Intent();
        data.putExtra("choice", getIntent().getIntExtra("choice", ConversationFragment.ATTACHMENT_CHOICE_INVALID));
        data.putExtra("disabled", disabled);
        setResult(RESULT_OK, data);
        finish();
    }

    private void commitTrusts() {
        for (final String fingerprint : ownKeysToTrust.keySet()) {
            mAccount.getAxolotlService().setFingerprintTrust(
                    fingerprint,
                    FingerprintStatus.createActive(ownKeysToTrust.get(fingerprint)));
        }
        List<Jid> acceptedTargets = mConversation == null ? new ArrayList<>() : mConversation.getAcceptedCryptoTargets();
        synchronized (this.foreignKeysToTrust) {
            for (Map.Entry<Jid, Map<String, Boolean>> entry : foreignKeysToTrust.entrySet()) {
                Jid jid = entry.getKey();
                Map<String, Boolean> value = entry.getValue();
                if (!acceptedTargets.contains(jid)) {
                    acceptedTargets.add(jid);
                }
                for (final String fingerprint : value.keySet()) {
                    mAccount.getAxolotlService().setFingerprintTrust(
                            fingerprint,
                            FingerprintStatus.createActive(value.get(fingerprint)));
                }
            }
        }

        if (mConversation != null && mConversation.getMode() == Conversation.MODE_MULTI) {
            mConversation.setAcceptedCryptoTargets(acceptedTargets);
            xmppConnectionService.updateConversation(mConversation);
        }
    }

    private List<Jid> contactJids = new ArrayList<>();
    private AxolotlService.FetchStatus lastFetchReport;

    private XmppConnectionService xmppConnectionService;

    @Override
    public void onKeyStatusUpdated(AxolotlService.FetchStatus report) {
        final boolean keysToTrust = reloadFingerprints();
        if (report != null) {
            lastFetchReport = report;
            runOnUiThread(() -> {
                if (mUseCameraHintToast != null && !keysToTrust) {
                    mUseCameraHintToast.cancel();
                }
                switch (report) {
                    case ERROR:
                        Toast.makeText(TrustKeysActivity.this, R.string.error_fetching_omemo_key, Toast.LENGTH_SHORT).show();
                        break;
                    case SUCCESS_TRUSTED:
                        Toast.makeText(TrustKeysActivity.this, R.string.blindly_trusted_omemo_keys, Toast.LENGTH_LONG).show();
                        break;
                    case SUCCESS_VERIFIED:
                        Toast.makeText(TrustKeysActivity.this,
                                Config.X509_VERIFICATION ? R.string.verified_omemo_key_with_certificate : R.string.all_omemo_keys_have_been_verified,
                                Toast.LENGTH_LONG).show();
                        break;
                }
            });
        }

        if (keysToTrust || hasPendingKeyFetches() || hasNoOtherTrustedKeys()) {
            refreshUi();
        } else {
            runOnUiThread(() -> finishOk(false));
        }
    }

    private Toast mUseCameraHintToast;

    // Assume the existence of other necessary methods and classes
}