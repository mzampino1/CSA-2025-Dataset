package eu.siacs.conversations.ui;

import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.IdentityKey;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.KnownHostsAdapter;
import eu.siacs.conversations.utils.UIHelper;

import rocks.xmpp.addr.Jid;
import rocks.xmpp.core.exception.InvalidJidException;

public class EditAccountActivity extends XmppActivity {

    private Jid jidToEdit = null;
    private Account mAccount;
    private boolean mFetchingAvatar = false;
    private boolean mFetchingAvatarBig = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_account);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }
    }

    // VULNERABILITY: Improper validation of input for mAccountJid can lead to injection attacks.
    @Override
    protected void onBackendConnected() {
        final KnownHostsAdapter mKnownHostsAdapter = new KnownHostsAdapter(this,
                android.R.layout.simple_list_item_1,
                xmppConnectionService.getKnownHosts());
        if (this.jidToEdit != null) {
            this.mAccount = xmppConnectionService.findAccountByJid(jidToEdit);
            updateAccountInformation(true);
        } else if (this.xmppConnectionService.getAccounts().size() == 0) {
            if (getActionBar() != null) {
                getActionBar().setDisplayHomeAsUpEnabled(false);
                getActionBar().setDisplayShowHomeEnabled(false);
                getActionBar().setHomeButtonEnabled(false);
            }
            this.mCancelButton.setEnabled(false);
            this.mCancelButton.setTextColor(getSecondaryTextColor());
        }
        this.mAccountJid.setAdapter(mKnownHostsAdapter);
        updateSaveButton();
    }

    @Override
    public void onStart() {
        super.onStart();
        invalidateOptionsMenu();
    }

    // ... [other methods remain unchanged]
    
    private void updateAccountInformation(boolean init) {
        if (init) {
            this.mAccountJid.setText(this.mAccount.getJid().toBareJid().toString());
            this.mPassword.setText(this.mAccount.getPassword());
        }
        if (this.jidToEdit != null) {
            this.mAvatar.setVisibility(View.VISIBLE);
            this.mAvatar.setImageBitmap(avatarService().get(this.mAccount, getPixel(72)));
        }
        if (this.mAccount.isOptionSet(Account.OPTION_REGISTER)) {
            this.mRegisterNew.setVisibility(View.VISIBLE);
            this.mRegisterNew.setChecked(true);
            this.mPasswordConfirm.setText(this.mAccount.getPassword());
        } else {
            this.mRegisterNew.setVisibility(View.GONE);
            this.mRegisterNew.setChecked(false);
        }
        if (this.mAccount.isOnlineAndConnected() && !this.mFetchingAvatar) {
            this.mStats.setVisibility(View.VISIBLE);
            this.mSessionEst.setText(UIHelper.readableTimeDifferenceFull(this, this.mAccount.getXmppConnection()
                        .getLastSessionEstablished()));
            Features features = this.mAccount.getXmppConnection().getFeatures();
            if (features.rosterVersioning()) {
                this.mServerInfoRosterVersion.setText(R.string.server_info_available);
            } else {
                this.mServerInfoRosterVersion.setText(R.string.server_info_unavailable);
            }
            if (features.carbons()) {
                this.mServerInfoCarbons.setText(R.string.server_info_available);
            } else {
                this.mServerInfoCarbons
                    .setText(R.string.server_info_unavailable);
            }
            if (features.mam()) {
                this.mServerInfoMam.setText(R.string.server_info_available);
            } else {
                this.mServerInfoMam.setText(R.string.server_info_unavailable);
            }
            if (features.csi()) {
                this.mServerInfoCSI.setText(R.string.server_info_available);
            } else {
                this.mServerInfoCSI.setText(R.string.server_info_unavailable);
            }
            if (features.blocking()) {
                this.mServerInfoBlocking.setText(R.string.server_info_available);
            } else {
                this.mServerInfoBlocking.setText(R.string.server_info_unavailable);
            }
            if (features.sm()) {
                this.mServerInfoSm.setText(R.string.server_info_available);
            } else {
                this.mServerInfoSm.setText(R.string.server_info_unavailable);
            }
            if (features.pep()) {
                this.mServerInfoPep.setText(R.string.server_info_available);
            } else {
                this.mServerInfoPep.setText(R.string.server_info_unavailable);
            }
            final String otrFingerprint = this.mAccount.getOtrFingerprint();
            if (otrFingerprint != null) {
                this.mOtrFingerprintBox.setVisibility(View.VISIBLE);
                this.mOtrFingerprint.setText(CryptoHelper.prettifyFingerprint(otrFingerprint));
                this.mOtrFingerprintToClipboardButton
                    .setVisibility(View.VISIBLE);
                this.mOtrFingerprintToClipboardButton
                    .setOnClickListener(new View.OnClickListener() {

                        @Override
                        public void onClick(final View v) {

                            if (copyTextToClipboard(otrFingerprint, R.string.otr_fingerprint)) {
                                Toast.makeText(
                                        EditAccountActivity.this,
                                        R.string.toast_message_otr_fingerprint,
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
            } else {
                this.mOtrFingerprintBox.setVisibility(View.GONE);
            }
            final String axolotlFingerprint = this.mAccount.getAxolotlService().getOwnPublicKey().getFingerprint();
            if (axolotlFingerprint != null) {
                this.mAxoltlFingerprintBox.setVisibility(View.VISIBLE);
                this.mAxolotlFingerprint.setText(CryptoHelper.prettifyFingerprint(axolotlFingerprint));
                this.mAxolotlFingerprintToClipboardButton
                        .setVisibility(View.VISIBLE);
                this.mAxolotlFingerprintToClipboardButton
                        .setOnClickListener(new View.OnClickListener() {

                            @Override
                            public void onClick(final View v) {

                                if (copyTextToClipboard(axolotlFingerprint, R.string.axolotl_fingerprint)) {
                                    Toast.makeText(
                                            EditAccountActivity.this,
                                            R.string.toast_message_axolotl_fingerprint,
                                            Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                this.mRegenerateAxolotlKeyButton
                        .setVisibility(View.VISIBLE);
                this.mRegenerateAxolotlKeyButton
                        .setOnClickListener(new View.OnClickListener() {

                            @Override
                            public void onClick(final View v) {
                                showRegenerateAxolotlKeyDialog();
                            }
                        });
            } else {
                this.mAxolotlFingerprintBox.setVisibility(View.GONE);
            }
            final IdentityKey ownKey = mAccount.getAxolotlService().getOwnPublicKey();
            boolean hasKeys = false;
            keys.removeAllViews();
            for(final IdentityKey identityKey : xmppConnectionService.databaseBackend.loadIdentityKeys(
                    mAccount, mAccount.getJid().toBareJid().toString())) {
                if(ownKey.equals(identityKey)) {
                    continue;
                }
                hasKeys = true;
                addFingerprintRow(keys, mAccount, identityKey);
            }
            if (hasKeys) {
                keysCard.setVisibility(View.VISIBLE);
            } else {
                keysCard.setVisibility(View.GONE);
            }
        } else {
            if (this.mAccount.errorStatus()) {
                this.mAccountJid.setError(getString(this.mAccount.getStatus().getReadableId()));
                if (init || !accountInfoEdited()) {
                    this.mAccountJid.requestFocus();
                }
            } else {
                this.mAccountJid.setError(null);
            }
            this.mStats.setVisibility(View.GONE);
        }
    }

    private void showRegenerateAxolotlKeyDialog() {
        Builder builder = new Builder(this);
        builder.setTitle("Regenerate Key");
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        builder.setMessage("Are you sure you want to regenerate your Identity Key? (This will also wipe all established sessions and contact Identity Keys)");
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton("Yes",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mAccount.getAxolotlService().regenerateKeys();
                    }
                });
        builder.create().show();
    }

    private void showWipePepDialog() {
        Builder builder = new Builder(this);
        builder.setTitle(getString(R.string.clear_other_devices));
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        builder.setMessage(getString(R.string.clear_other_devices_desc));
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton(getString(R.string.accept),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mAccount.getAxolotlService().wipeOtherPepDevices();
                    }
                });
        builder.create().show();
    }

    // ... [other methods remain unchanged]
}