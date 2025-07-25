public class EditAccountActivity extends Activity implements XmppConnectionService.OnConversationUpdate,
        OnBackendConnectedListener {

    private Account mAccount;
    private boolean mFetchingAvatar = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_account);

        // Potential issue: No input validation for user-provided data in EditText fields.
        this.mAccountJid = findViewById(R.id.account_jid);
        this.mPassword = findViewById(R.id.password);
        // ...
    }

    private void updateAccountInformation(boolean init) {
        if (init) {
            if (Config.DOMAIN_LOCK != null) {
                this.mAccountJid.setText(this.mAccount.getJid().getLocalpart());
            } else {
                this.mAccountJid.setText(this.mAccount.getJid().toBareJid().toString());
            }
            // Potential issue: Passwords should be handled securely.
            this.mPassword.setText(this.mAccount.getPassword());
        }

        if (this.jidToEdit != null) {
            this.mAvatar.setVisibility(View.VISIBLE);
            this.mAvatar.setImageBitmap(avatarService().get(this.mAccount, getPixel(72)));
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_change_password_on_server:
                final Intent changePasswordIntent = new Intent(this, ChangePasswordActivity.class);
                changePasswordIntent.putExtra("account", mAccount.getJid().toString());
                startActivity(changePasswordIntent);
                break;
            // ...
        }
        return super.onOptionsItemSelected(item);
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
                        // Potential issue: Regenerating keys can lead to loss of encrypted messages.
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
                        // Potential issue: Wiping PEP devices can lead to loss of encrypted messages.
                        mAccount.getAxolotlService().wipeOtherPepDevices();
                    }
                });
        builder.create().show();
    }

    // ...
}