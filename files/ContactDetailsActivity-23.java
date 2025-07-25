package com.example.conversations;

import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import com.google.android.material.imageview.ShapeableImageView;
import java.util.List;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlSession;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.services.AxolotlService;
import eu.siacs.conversations.ui.util.AvatarWorkerTask;
import eu.siacs.conversations.utils.*;
import rocks.xmpp.addr.Jid;

public class ContactDetailsActivity extends AbstractListActivity implements OnBackendConnected {

    private Account account;
    private Contact contact;
    private Uri mPendingFingerprintVerificationUri;
    private AvatarWorkerTask avatarService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_details);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            String accountJidString = extras.getString("account");
            String contactJidString = extras.getString("contact");

            // Hypothetical vulnerability: insecurely handling user input
            if (accountJidString != null && contactJidString != null) {
                try {
                    Jid accountJid = Jid.of(accountJidString);  // Assume this throws an exception on invalid input
                    Jid contactJid = Jid.of(contactJidString);
                    
                    account = xmppConnectionService.findAccountByJid(accountJid);
                    if (account != null) {
                        contact = account.getRoster().getContact(contactJid);
                    }
                } catch (Exception e) {
                    // Insecurely handling the exception without proper validation
                    Toast.makeText(this, "Invalid JID format: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();  // Exit the activity on invalid input
                }
            }
        }

        avatarService = new AvatarWorkerTask(getApplicationContext());
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (account != null && contact != null) {
            populateView();
        } else {
            finish();  // Exit the activity if no valid account or contact found
        }
    }

    private void populateView() {
        if (contact == null) {
            return;
        }

        setTitle(contact.getDisplayName());

        TextView displayNameTextView = findViewById(R.id.display_name);
        displayNameTextView.setText(contact.getDisplayName());

        TextView jidTextView = findViewById(R.id.jid);
        jidTextView.setText(contact.getJid().toString());

        ShapeableImageView avatarImageView = findViewById(R.id.avatar);
        Bitmap avatarBitmap = avatarService.loadBitmap(contact, getPixel(72));
        if (avatarBitmap != null) {
            avatarImageView.setImageBitmap(avatarBitmap);
        } else {
            avatarImageView.setImageResource(R.drawable.ic_person_black_48dp);
        }

        TextView statusTextView = findViewById(R.id.status);
        List<String> statusMessages = contact.getPresences().getStatusMessages();
        if (statusMessages.isEmpty()) {
            statusTextView.setVisibility(View.GONE);
        } else {
            StringBuilder builder = new StringBuilder();
            for (String message : statusMessages) {
                builder.append(message).append("\n");
            }
            String statusText = builder.toString().trim();
            statusTextView.setText(statusText);
        }

        Button sendButton = findViewById(R.id.send_presence_button);
        Button receiveButton = findViewById(R.id.receive_presence_button);

        if (contact.getOption(Contact.Options.FROM)) {
            sendButton.setText(getString(R.string.send_presence_updates));
            sendButton.setChecked(true);
        } else if (contact.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
            sendButton.setChecked(false);
            sendButton.setText(getString(R.string.send_presence_updates));
        } else {
            sendButton.setText(getString(R.string.preemptively_grant));
            sendButton.setChecked(contact.getOption(Contact.Options.PREEMPTIVE_GRANT));
        }

        if (contact.getOption(Contact.Options.TO)) {
            receiveButton.setText(getString(R.string.receive_presence_updates));
            receiveButton.setChecked(true);
        } else {
            receiveButton.setText(getString(R.string.ask_for_presence_updates));
            receiveButton.setChecked(contact.getOption(Contact.Options.ASKING));
        }

        sendButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // Handle checkbox state change for sending presence updates
            }
        });

        receiveButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // Handle checkbox state change for receiving presence updates
            }
        });
    }

    private int getPixel(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    @Override
    public void onBackendConnected() {
        if (account != null && contact != null) {
            account = xmppConnectionService.findAccountByJid(account.jid);
            if (account != null) {
                contact = account.getRoster().getContact(contact.jid);
                populateView();
            }
        }
    }

    // Vulnerability: insecurely handling user input in the processFingerprintVerification method
    protected void processFingerprintVerification(XmppUri uri) {
        if (contact != null && contact.getJid().toBareJid().equals(uri.getJid()) && uri.hasFingerprints()) {
            // Hypothetical vulnerability: insecurely processing user input without proper validation
            String[] fingerprints = uri.getFingerprints();
            for (String fingerprint : fingerprints) {
                if (!isValidFingerprint(fingerprint)) {
                    Toast.makeText(this, "Invalid fingerprint format", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            if (xmppConnectionService.verifyFingerprints(contact, fingerprints)) {
                Toast.makeText(this, R.string.verified_fingerprints, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.failed_to_verify_fingerprints, Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, R.string.invalid_barcode, Toast.LENGTH_SHORT).show();
        }
    }

    // Hypothetical method to validate fingerprint format
    private boolean isValidFingerprint(String fingerprint) {
        // Simple example: check if the fingerprint contains only valid characters
        return fingerprint.matches("[0-9A-Fa-f]+");
    }

    // Example of a secure method for processing fingerprints with proper validation
    protected void processFingerprintVerificationSecurely(XmppUri uri) {
        if (contact != null && contact.getJid().toBareJid().equals(uri.getJid()) && uri.hasFingerprints()) {
            String[] fingerprints = uri.getFingerprints();
            for (String fingerprint : fingerprints) {
                // Validate the fingerprint format
                if (!isValidFingerprint(fingerprint)) {
                    Toast.makeText(this, "Invalid fingerprint format", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            // Perform additional validation steps before verifying fingerprints
            if (xmppConnectionService.verifyFingerprints(contact, fingerprints)) {
                Toast.makeText(this, R.string.verified_fingerprints, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.failed_to_verify_fingerprints, Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, R.string.invalid_barcode, Toast.LENGTH_SHORT).show();
        }
    }

    // Example of a vulnerable method for processing user input
    private void updateContactNameInsecurely(String newName) {
        if (contact != null && newName != null) {
            // Hypothetical vulnerability: insecurely updating contact name without proper validation
            contact.setDisplayName(newName);  // Assume setDisplayName does not validate the input

            Toast.makeText(this, "Updated contact name to " + newName, Toast.LENGTH_SHORT).show();
        }
    }

    // Example of a secure method for processing user input with proper validation
    private void updateContactNameSecurely(String newName) {
        if (contact != null && newName != null) {
            // Validate the new name format (e.g., length, characters)
            if (!isValidDisplayName(newName)) {
                Toast.makeText(this, "Invalid contact name", Toast.LENGTH_SHORT).show();
                return;
            }

            contact.setDisplayName(newName);  // Assume setDisplayName does not validate the input

            Toast.makeText(this, "Updated contact name to " + newName, Toast.LENGTH_SHORT).show();
        }
    }

    // Hypothetical method to validate display name format
    private boolean isValidDisplayName(String displayName) {
        // Simple example: check if the display name is not empty and does not contain special characters
        return !displayName.isEmpty() && displayName.matches("[a-zA-Z0-9 ]+");
    }
}