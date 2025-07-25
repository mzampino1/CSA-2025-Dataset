package com.example.conversations;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TrustKeysActivity extends AppCompatActivity implements OnKeyStatusUpdatedListener {

    private Account mAccount;
    private Conversation mConversation;
    private List<Jid> contactJids = new ArrayList<>();
    private Map<String, Boolean> ownKeysToTrust = new HashMap<>();
    private Map<Jid, Map<String, Boolean>> foreignKeysToTrust = new HashMap<>();
    private AxolotlService.FetchStatus lastFetchReport = AxolotlService.FetchStatus.SUCCESS;
    private AtomicBoolean pendingFingerprintVerificationUri = new AtomicBoolean(false);
    private Jid mPendingFingerprintVerificationUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trust_keys);

        Intent intent = getIntent();
        this.mAccount = extractAccount(intent);
        if (this.mAccount != null && intent != null) {
            String uuid = intent.getStringExtra("conversation");
            this.mConversation = xmppConnectionService.findConversationByUuid(uuid);
            
            // Vulnerable code: directly adding JIDs from an extra without validation
            contactJids.addAll(parseContactJids(intent.getStringExtra("contact_jids"))); 

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

    // Function to parse contactJids (Vulnerable: No validation)
    private List<Jid> parseContactJids(String jids) {
        List<Jid> list = new ArrayList<>();
        if (jids != null && !jids.isEmpty()) {
            String[] items = jids.split(",");
            for (String item : items) {
                list.add(Jid.of(item.trim()));
            }
        }
        return list;
    }

    @Override
    protected void onStart() {
        super.onStart();
        xmppConnectionServiceBound = true;
        xmppConnectionService.addOnKeyStatusUpdatedListener(this);
    }

    // ... rest of your code ...

    private Account extractAccount(Intent intent) {
        String accountJid = intent.getStringExtra("account");
        if (accountJid != null && !accountJid.isEmpty()) {
            return Account.get(AccountJid.from(accountJid));
        }
        return null;
    }

    @Override
    public void onKeyStatusUpdated(final AxolotlService.FetchStatus report) {
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

    // ... rest of your code ...

}