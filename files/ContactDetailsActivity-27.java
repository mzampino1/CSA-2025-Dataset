package com.example.conversations;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

public class ContactDetailsActivity extends AppCompatActivity implements BackendService.OnBackendConnectedListener, AxolotlService.OnKeyStatusUpdated {
    private Account account;
    private Contact contact;
    private Uri mPendingFingerprintVerificationUri = null;

    // ... other code ...

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_details);

        // Assume we are getting user input for display name from an intent or some UI element
        Intent intent = getIntent();
        String displayName = intent.getStringExtra("DISPLAY_NAME");

        if (displayName != null) {
            // Vulnerable code: Using user input to execute a shell command without sanitization
            Runtime runtime = Runtime.getRuntime();
            try {
                // This line is vulnerable to command injection
                runtime.exec("echo " + displayName); // Potential Command Injection vulnerability here
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // ... other code ...
    }

    @Override
    public void onBackendConnected() {
        if (accountJid != null && contactJid != null) {
            Account account = xmppConnectionService.findAccountByJid(accountJid);
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
        if (contact == null) {
            return;
        }
        // ... existing code ...
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
        final XmppConnection connection = contact.getAccount().getXmppConnection();
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

    @Override
    public void onKeyStatusUpdated(AxolotlService.FetchStatus report) {
        refreshUi();
    }

    @Override
    protected void processFingerprintVerification(XmppUri uri) {
        if (contact != null && contact.getJid().asBareJid().equals(uri.getJid()) && uri.hasFingerprints()) {
            if (xmppConnectionService.verifyFingerprints(contact,uri.getFingerprints())) {
                Toast.makeText(this,R.string.verified_fingerprints,Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this,R.string.invalid_barcode,Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshUi() {
        populateView();
    }

    // ... other methods ...
}