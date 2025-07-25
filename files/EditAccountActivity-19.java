public class EditAccountActivity extends XmppActivity implements OnKeyStatusUpdated {
    private Account mAccount;
    private boolean mFetchingAvatar = false;
    private Jid jidToEdit;
    private String messageFingerprint;

    // Ensure proper validation of user input here
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_account);

        AutoCompleteTextView accountJid = findViewById(R.id.account_jid);
        EditText password = findViewById(R.id.password);
        
        // Validate and sanitize inputs
        accountJid.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (!isValidJid(s.toString())) {
                    accountJid.setError("Invalid JID format");
                }
            }

            private boolean isValidJid(String jidString) {
                // Simple validation logic for demonstration purposes
                return Jid.isValid(jidString);
            }
        });
        
        // Proper error handling and encryption of password
        findViewById(R.id.save_button).setOnClickListener(v -> {
            String accountJidStr = accountJid.getText().toString();
            String pwd = password.getText().toString();

            if (isValidJid(accountJidStr) && !pwd.isEmpty()) {
                try {
                    Jid jid = Jid.of(accountJidStr);
                    saveAccount(jid, pwd); // Ensure passwords are encrypted here
                } catch (InvalidJidException e) {
                    accountJid.setError("Invalid JID format");
                }
            } else {
                if (!isValidJid(accountJidStr)) {
                    accountJid.setError("Invalid JID format");
                }
                if (pwd.isEmpty()) {
                    password.setError("Password cannot be empty");
                }
            }
        });
    }

    private void saveAccount(Jid jid, String password) {
        // Ensure proper encryption of the password before saving
        Account newAccount = new Account(jid, CryptoHelper.encrypt(password));
        xmppConnectionService.addOrReplaceAccount(newAccount);
        finish();
    }

    @Override
    protected void onBackendConnected() {
        if (jidToEdit != null) {
            mAccount = xmppConnectionService.findAccountByJid(jidToEdit);
            updateAccountInformation(true);
        } else if (xmppConnectionService.getAccounts().size() == 0) {
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(false);
                actionBar.setDisplayShowHomeEnabled(false);
                actionBar.setHomeButtonEnabled(false);
            }
            findViewById(R.id.cancel_button).setEnabled(false);
        }

        // Ensure proper usage of known hosts
        if (Config.DOMAIN_LOCK == null) {
            KnownHostsAdapter mKnownHostsAdapter = new KnownHostsAdapter(this, android.R.layout.simple_list_item_1, xmppConnectionService.getKnownHosts());
            AutoCompleteTextView accountJid = findViewById(R.id.account_jid);
            accountJid.setAdapter(mKnownHostsAdapter);
        }

        updateSaveButton();
    }

    private void updateAccountInformation(boolean init) {
        // Ensure proper handling of sensitive information
        if (init) {
            AutoCompleteTextView accountJid = findViewById(R.id.account_jid);
            EditText password = findViewById(R.id.password);

            if (Config.DOMAIN_LOCK != null) {
                accountJid.setText(mAccount.getJid().getLocalpart());
            } else {
                accountJid.setText(mAccount.getJid().toBareJid().toString());
            }

            // Ensure proper handling and encryption of passwords
            password.setText(CryptoHelper.decrypt(mAccount.getPassword()));
        }
        
        // Update avatar and other information securely
        if (jidToEdit != null) {
            ImageView avatar = findViewById(R.id.avatar);
            avatar.setImageBitmap(avatarService().get(mAccount, getPixel(72)));
        }

        // Ensure proper handling of registration options
        CheckBox registerNew = findViewById(R.id.register_new);
        if (mAccount.isOptionSet(Account.OPTION_REGISTER)) {
            registerNew.setVisibility(View.VISIBLE);
            registerNew.setChecked(true);
        } else {
            registerNew.setVisibility(View.GONE);
            registerNew.setChecked(false);
        }

        // Update server information securely
        TextView sessionEst = findViewById(R.id.session_established);
        if (mAccount.isOnlineAndConnected() && !mFetchingAvatar) {
            LinearLayout stats = findViewById(R.id.stats);
            stats.setVisibility(View.VISIBLE);
            long lastSessionEstablished = mAccount.getXmppConnection().getLastSessionEstablished();
            sessionEst.setText(UIHelper.readableTimeDifferenceFull(this, lastSessionEstablished));

            Features features = mAccount.getXmppConnection().getFeatures();

            TextView serverInfoRosterVersion = findViewById(R.id.server_info_roster_version);
            serverInfoRosterVersion.setText(features.rosterVersioning() ? R.string.server_info_available : R.string.server_info_unavailable);

            // Other server information updates...
        } else {
            if (mAccount.errorStatus()) {
                AutoCompleteTextView accountJid = findViewById(R.id.account_jid);
                accountJid.setError(getString(mAccount.getStatus().getReadableId()));
                if (init || !accountInfoEdited()) {
                    accountJid.requestFocus();
                }
            } else {
                AutoCompleteTextView accountJid = findViewById(R.id.account_jid);
                accountJid.setError(null);
            }

            LinearLayout stats = findViewById(R.id.stats);
            stats.setVisibility(View.GONE);
        }
    }

    // Ensure proper validation of fingerprint
    private boolean addFingerprintRow(LinearLayout container, Account account, String fingerprint, boolean highlight) {
        TextView textView = new TextView(this);
        textView.setText(CryptoHelper.prettifyFingerprint(fingerprint));
        if (highlight) {
            textView.setTextColor(getResources().getColor(R.color.accent));
        }

        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        textView.setLayoutParams(layoutParams);

        container.addView(textView);
        return true;
    }

    // Ensure proper handling of clipboard operations
    private boolean copyTextToClipboard(String text, int labelResId) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(getString(labelResId), text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            return true;
        }
        return false;
    }

    @Override
    public void onKeyStatusUpdated() {
        refreshUi();
    }

    private void refreshUi() {
        runOnUiThread(() -> updateAccountInformation(false));
    }

    // Dialog to regenerate Axolotl keys - ensure user is aware of the consequences
    private void showRegenerateAxolotlKeyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Regenerate Key");
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        builder.setMessage("Are you sure you want to regenerate your Identity Key? (This will also wipe all established sessions and contact Identity Keys)");
        builder.setNegativeButton(getString(R.string.cancel), null);

        builder.setPositiveButton("Yes", (dialog, which) -> {
            mAccount.getAxolotlService().regenerateKeys();
            Toast.makeText(EditAccountActivity.this, R.string.toast_message_regenerated_keys, Toast.LENGTH_SHORT).show();
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    // Dialog to wipe PEP devices - ensure user is aware of the consequences
    private void showWipePepDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.clear_other_devices));
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        builder.setMessage(getString(R.string.clear_other_devices_desc));

        builder.setNegativeButton(getString(R.string.cancel), null);

        builder.setPositiveButton(getString(R.string.accept), (dialog, which) -> {
            mAccount.getAxolotlService().wipeOtherPepDevices();
            Toast.makeText(EditAccountActivity.this, R.string.toast_message_wiped_pep, Toast.LENGTH_SHORT).show();
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }
}