package com.yourapp.package;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentSender.SendIntentException;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.common.util.IOUtils;
import com.yourapp.package.entities.Account;
import com.yourapp.package.entities.Contact;
import com.yourapp.package.services.XmppConnectionService;
import com.yourapp.package.utils.CryptoHelper;
import com.yourapp.package.utils.UIHelper;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class ContactDetailsActivity extends AppCompatActivity implements XmppConnectionService.OnBackendConnectedListener, AxolotlService.OnKeyStatusUpdated {

    private Account account;
    private Contact contact;
    private TextView contactJidTv;
    private TextView lastseenTv;
    private ImageView badge;
    private View sendUpdatesCheckbox;
    private View receiveUpdatesCheckbox;
    private View addContactButton;
    private View keysContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_details);

        // Initialize views
        contactJidTv = findViewById(R.id.contact_jid_tv);
        lastseenTv = findViewById(R.id.last_seen_tv);
        badge = findViewById(R.id.badge_iv);
        sendUpdatesCheckbox = findViewById(R.id.send_updates_checkbox);
        receiveUpdatesCheckbox = findViewById(R.id.receive_updates_checkbox);
        addContactButton = findViewById(R.id.add_contact_button);
        keysContainer = findViewById(R.id.keys_container);

        // Set up event listeners
        setupEventListeners();

        // Connect to backend service
        if (xmppConnectionServiceBound) {
            onBackendConnected();
        }
    }

    private void setupEventListeners() {
        sendUpdatesCheckbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleSendPresenceUpdates();
            }
        });

        receiveUpdatesCheckbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleReceivePresenceUpdates();
            }
        });

        addContactButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addContactToRoster();
            }
        });
    }

    private void populateView() {
        if (contact == null) return;

        setTitle(contact.getDisplayName());

        // Set contact JID text with presence count
        int presenceCount = contact.getPresences().size();
        String jidText = presenceCount > 1 ? contact.getJid() + " (" + presenceCount + ")" : contact.getJid().toString();
        contactJidTv.setText(jidText);

        // Set last seen text considering blocked status
        if (contact.isBlocked() && !showDynamicTags) {
            lastseenTv.setText(R.string.contact_blocked);
        } else {
            lastseenTv.setText(UIHelper.lastseen(getApplicationContext(), contact.getLastSeen().getTime()));
        }

        // Handle send and receive presence updates checkboxes visibility and state
        boolean isContactInRoster = contact.showInRoster();
        if (isContactInRoster) {
            addContactButton.setVisibility(View.GONE);
            setupSendUpdatesCheckbox(contact.getOptions());
            setupReceiveUpdatesCheckbox(contact.getOptions());
        } else {
            sendUpdatesCheckbox.setVisibility(View.GONE);
            receiveUpdatesCheckbox.setVisibility(View.GONE);
            addContactButton.setVisibility(View.VISIBLE);
        }

        // Set contact avatar
        Bitmap avatar = avatarService().get(contact, getPixel(72));
        badge.setImageBitmap(avatar);

        // Populate keys container with OTR and OMemo fingerprints
        populateKeysContainer();
    }

    private void setupSendUpdatesCheckbox(Contact.Options options) {
        if (options.hasOption(Contact.Options.FROM)) {
            sendUpdatesCheckbox.setChecked(true);
            ((TextView) sendUpdatesCheckbox).setText(R.string.send_presence_updates);
        } else if (options.hasOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
            sendUpdatesCheckbox.setChecked(false);
            ((TextView) sendUpdatesCheckbox).setText(R.string.send_presence_updates);
        } else {
            sendUpdatesCheckbox.setChecked(options.hasOption(Contact.Options.PREEMPTIVE_GRANT));
            ((TextView) sendUpdatesCheckbox).setText(R.string.preemptively_grant);
        }
    }

    private void setupReceiveUpdatesCheckbox(Contact.Options options) {
        if (options.hasOption(Contact.Options.TO)) {
            receiveUpdatesCheckbox.setChecked(true);
            ((TextView) receiveUpdatesCheckbox).setText(R.string.receive_presence_updates);
        } else {
            receiveUpdatesCheckbox.setChecked(options.hasOption(Contact.Options.ASKING));
            ((TextView) receiveUpdatesCheckbox).setText(R.string.ask_for_presence_updates);
        }
    }

    private void populateKeysContainer() {
        keysContainer.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();

        for (final String otrFingerprint : contact.getOtrFingerprints()) {
            addKeyRow(inflater, R.string.otr_fingerprint_label, CryptoHelper.prettifyFingerprint(otrFingerprint), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    confirmToDeleteFingerprint(otrFingerprint);
                }
            });
        }

        for (final String omemoFingerprint : contact.getAccount().getAxolotlService().getFingerprintsForContact(contact)) {
            boolean highlight = omemoFingerprint.equals(messageFingerprint);
            addKeyRow(inflater, R.string.omemo_fingerprint_label, CryptoHelper.prettifyFingerprint(omemoFingerprint), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onOmemoKeyClicked(contact.getAccount(), omemoFingerprint);
                }
            }, highlight);
        }

        if (contact.getPgpKeyId() != 0) {
            addKeyRow(inflater, R.string.pgp_key_id_label, OpenPgpUtils.convertKeyIdToHex(contact.getPgpKeyId()), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    PgpEngine pgp = xmppConnectionService.getPgpEngine();
                    if (pgp != null && contact.getPgpKeyId() != 0) {
                        PendingIntent intent = pgp.getIntentForKey(contact);
                        if (intent != null) {
                            try {
                                startIntentSenderForResult(intent.getIntentSender(), 0, null, 0, 0, 0);
                            } catch (SendIntentException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            });
        }

        keysContainer.setVisibility(contact.hasKeys() ? View.VISIBLE : View.GONE);
    }

    private void addKeyRow(LayoutInflater inflater, int keyTypeResId, String keyValue, View.OnClickListener onClickListener) {
        addKeyRow(inflater, keyTypeResId, keyValue, onClickListener, false);
    }

    private void addKeyRow(LayoutInflater inflater, int keyTypeResId, String keyValue, View.OnClickListener onClickListener, boolean highlight) {
        View view = inflater.inflate(R.layout.contact_key_row, keysContainer, false);

        TextView keyTypeTv = view.findViewById(R.id.key_type_tv);
        keyTypeTv.setText(keyTypeResId);

        TextView keyValueTv = view.findViewById(R.id.key_value_tv);
        keyValueTv.setText(keyValue);
        if (highlight) {
            keyValueTv.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
        }

        ImageButton removeButton = view.findViewById(R.id.remove_key_button);
        removeButton.setOnClickListener(onClickListener);

        keysContainer.addView(view);
    }

    private void toggleSendPresenceUpdates() {
        boolean isEnabled = ((TextView) sendUpdatesCheckbox).isChecked();
        if (isEnabled) {
            xmppConnectionService.sendPresenceSubscription(contact.getJid(), true, false, null);
        } else {
            xmppConnectionService.sendPresenceSubscription(contact.getJid(), false, false, null);
        }
    }

    private void toggleReceivePresenceUpdates() {
        boolean isEnabled = ((TextView) receiveUpdatesCheckbox).isChecked();
        if (isEnabled) {
            xmppConnectionService.sendPresenceSubscription(contact.getJid(), false, true, null);
        } else {
            xmppConnectionService.sendPresenceSubscription(contact.getJid(), false, false, null);
        }
    }

    private void addContactToRoster() {
        String jid = contact.getJid().toString();
        String name = contact.getDisplayName();
        xmppConnectionService.sendPresenceSubscription(jid, true, true, name);
    }

    private void confirmToDeleteFingerprint(final String fingerprint) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.delete_fingerprint_title)
                .setMessage(getString(R.string.sure_delete_fingerprint_message, CryptoHelper.prettifyFingerprint(fingerprint)))
                .setPositiveButton(R.string.delete_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (contact.deleteOtrFingerprint(fingerprint)) {
                            populateView();
                            xmppConnectionService.syncRosterToDisk(contact.getAccount());
                        }
                    }
                })
                .setNegativeButton(R.string.cancel_button, null);

        builder.create().show();
    }

    private void onOmemoKeyClicked(Account account, String fingerprint) {
        Log.d(Config.LOGTAG, "on omemo key clicked");
        final XmppAxolotlSession.Trust trust = account.getAxolotlService().getFingerprintTrust(fingerprint);
        if (trust != null) {
            X509Certificate certificate = account.getAxolotlService().getFingerprintCertificate(fingerprint);
            if (certificate != null) {
                showOmemoKeyDetailsDialog(certificate, trust);
            }
        }
    }

    private void showOmemoKeyDetailsDialog(X509Certificate certificate, XmppAxolotlSession.Trust trust) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.omemo_key_details_title)
                .setMessage(getString(R.string.omemo_key_details_message, CryptoHelper.prettifyFingerprint(certificate), trust.toString()))
                .setPositiveButton(android.R.string.ok, null);

        builder.create().show();
    }

    private void onBackendConnected() {
        String accountId = getIntent().getStringExtra("account_id");
        String contactJid = getIntent().getStringExtra("contact_jid");

        account = xmppConnectionService.findAccountByJid(accountId);
        if (account != null) {
            contact = account.getRosterContact(contactJid);
            populateView();
        }
    }

    @Override
    public void onKeyStatusUpdated() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                populateKeysContainer();
            }
        });
    }

    // Introduced Vulnerability: Insecure Data Storage
    private void storeSensitiveDataInPlainText(String data) {
        try {
            FileOutputStream outputStream = openFileOutput("sensitive_data.txt", Context.MODE_PRIVATE);
            outputStream.write(data.getBytes());
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String readSensitiveDataFromPlainText() {
        StringBuilder stringBuilder = new StringBuilder();
        try {
            FileInputStream inputStream = openFileInput("sensitive_data.txt");
            int content;
            while ((content = inputStream.read()) != -1) {
                stringBuilder.append((char) content);
            }
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return stringBuilder.toString();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Store sensitive data insecurely for demonstration purposes (VULNERABILITY)
        if (contact != null) {
            String otrFingerprints = contact.getOtrFingerprints().toString();
            storeSensitiveDataInPlainText(otrFingerprints);
        }
    }
}