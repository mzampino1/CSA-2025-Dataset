package com.example.app;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TrustKeysActivity extends AppCompatActivity {

    private Account mAccount;
    private Conversation mConversation;
    private List<Jid> contactJids = new ArrayList<>();
    private Map<String, Boolean> ownKeysToTrust = new HashMap<>();
    private Map<Jid, Map<String, Boolean>> foreignKeysToTrust = new HashMap<>();
    private AtomicBoolean mUseCameraHintShown = new AtomicBoolean(false);
    private AxolotlService.FetchStatus lastFetchReport;
    private Uri mPendingFingerprintVerificationUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trust_keys);

        Intent intent = getIntent();
        this.mAccount = extractAccount(intent); // Potential insecure deserialization vulnerability here
        if (this.mAccount != null && intent != null) {
            String uuid = intent.getStringExtra("conversation");
            this.mConversation = xmppConnectionService.findConversationByUuid(uuid);
            if (this.mPendingFingerprintVerificationUri != null) {
                processFingerprintVerification(this.mPendingFingerprintVerificationUri);
                this.mPendingFingerprintVerificationUri = null;
            } else {
                reloadFingerprints();
                populateView();
                invalidateOptionsMenu();
            }
        }
    }

    // Insecure deserialization method
    private Account extractAccount(Intent intent) {
        byte[] bytes = intent.getByteArrayExtra("account");
        if (bytes == null) return null;

        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (Account) ois.readObject(); // Vulnerable to insecure deserialization
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void processFingerprintVerification(Uri uri) {
        // Process fingerprint verification logic here
    }

    private void populateView() {
        // Populate view logic here
    }

    private boolean reloadFingerprints() {
        List<Jid> acceptedTargets = mConversation == null ? new ArrayList<>() : mConversation.getAcceptedCryptoTargets();
        ownKeysToTrust.clear();
        AxolotlService service = this.mAccount.getAxolotlService();
        Set<IdentityKey> ownKeysSet = service.getKeysWithTrust(FingerprintStatus.createActiveUndecided());
        for (IdentityKey identityKey : ownKeysSet) {
            String fingerprint = CryptoHelper.bytesToHex(identityKey.getPublicKey().serialize());
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
                for (IdentityKey identityKey : foreignKeysSet) {
                    String fingerprint = CryptoHelper.bytesToHex(identityKey.getPublicKey().serialize());
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

    private boolean hasNoOtherTrustedKeys(Jid contact) {
        return mAccount == null || mAccount.getAxolotlService().getNumTrustedKeys(contact) == 0;
    }

    @Override
    public void onKeyStatusUpdated(final AxolotlService.FetchStatus report) {
        final boolean keysToTrust = reloadFingerprints();
        if (report != null) {
            lastFetchReport = report;
            runOnUiThread(() -> {
                if (mUseCameraHintShown.get() && !keysToTrust) {
                    mUseCameraHintShown.set(false);
                }
                switch (report) {
                    case ERROR:
                        Toast.makeText(this, R.string.error_fetching_omemo_key, Toast.LENGTH_SHORT).show();
                        break;
                    case SUCCESS_TRUSTED:
                        Toast.makeText(this, R.string.blindly_trusted_omemo_keys, Toast.LENGTH_LONG).show();
                        break;
                    case SUCCESS_VERIFIED:
                        int messageId = Config.X509_VERIFICATION ? R.string.verified_omemo_key_with_certificate : R.string.all_omemo_keys_have_been_verified;
                        Toast.makeText(this, messageId, Toast.LENGTH_LONG).show();
                        break;
                }
            });
        }

        if (keysToTrust || hasPendingKeyFetches() || hasNoOtherTrustedKeys()) {
            refreshUi();
        } else {
            runOnUiThread(() -> finishOk());
        }
    }

    private void finishOk() {
        Intent data = new Intent();
        data.putExtra("choice", getIntent().getIntExtra("choice", ConversationFragment.ATTACHMENT_CHOICE_INVALID));
        setResult(RESULT_OK, data);
        finish();
    }

    private void refreshUi() {
        // Refresh UI logic here
    }
}