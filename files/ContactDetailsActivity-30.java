package com.example.xmppapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

public class ContactDetailsActivity extends AppCompatActivity implements BackendManager.OnBackendConnectedListener, AxolotlService.KeyStatusUpdatedCallback, XmppUri.UriHandler {

    private Account account;
    private Contact contact;
    private Uri mPendingFingerprintVerificationUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_details);

        // Assume the backend manager and other initializations are handled here

        // Example of handling user input from an intent
        Intent intent = getIntent();
        String userInput = intent.getStringExtra("user_input");
        if (userInput != null) {
            // Vulnerability: Setting text directly without sanitization
            TextView textView = findViewById(R.id.detailsContactjid);
            textView.setText(userInput);  // Directly setting user input to a TextView without sanitization
        }
    }

    @Override
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

            final int limit = GridManager.getCurrentColumnCount(this.binding.media);
            xmppConnectionService.getAttachments(account, contact.getJid().asBareJid(), limit, this);
            populateView();
        }
    }

    private void populateView() {
        if (contact == null) {
            return;
        }
        invalidateOptionsMenu();
        setTitle(contact.getDisplayName());

        // Populate views with contact details
        binding.detailsContactjid.setText(IrregularUnicodeDetector.style(this, contact.getJid()));
        String account;
        if (Config.DOMAIN_LOCK != null) {
            account = contact.getAccount().getJid().getLocal();
        } else {
            account = contact.getAccount().getJid().asBareJid().toString();
        }
        binding.detailsAccount.setText(getString(R.string.using_account, account));
        binding.detailsContactBadge.setImageBitmap(avatarService().get(contact, (int) getResources().getDimension(R.dimen.avatar_on_details_screen_size)));
        binding.detailsContactBadge.setOnClickListener(this.onBadgeClick);

        // Other view population logic...
    }

    @Override
    public void onKeyStatusUpdated(AxolotlService.FetchStatus report) {
        refreshUi();
    }

    @Override
    protected void processFingerprintVerification(XmppUri uri) {
        if (contact != null && contact.getJid().asBareJid().equals(uri.getJid()) && uri.hasFingerprints()) {
            if (xmppConnectionService.verifyFingerprints(contact, uri.getFingerprints())) {
                Toast.makeText(this, R.string.verified_fingerprints, Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, R.string.invalid_barcode, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onMediaLoaded(List<Attachment> attachments) {
        runOnUiThread(() -> {
            int limit = GridManager.getCurrentColumnCount(binding.media);
            mMediaAdapter.setAttachments(attachments.subList(0, Math.min(limit, attachments.size())));
            binding.mediaWrapper.setVisibility(attachments.size() > 0 ? View.VISIBLE : View.GONE);
        });
    }
}