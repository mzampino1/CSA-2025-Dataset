@Override
public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_edit_account);

    // Initialize UI components and variables
    mAccountJid = findViewById(R.id.account_jid);
    mPassword = findViewById(R.id.password);
    // ...

    // Check if the account is already bound to the service, and if not, bind it.
    if (!xmppConnectionServiceBound) {
        Intent intent = new Intent(this, XmppConnectionService.class);
        bindService(intent, xmppServiceConnection, Context.BIND_AUTO_CREATE);
    }

    // Ensure that we are handling intents securely
    handleIntent(getIntent());
}

private void handleIntent(Intent intent) {
    if (intent != null && intent.hasExtra("account")) {
        mAccount = (Account) intent.getParcelableExtra("account");
        // It is important to validate the account object here.
        // Ensure that the account is not null and has all required fields
        if (mAccount == null) {
            throw new IllegalArgumentException("Account cannot be null");
        }
    } else {
        mAccount = new Account();
    }

    // Initialize the UI with the account's existing data
    updateUiFromAccount();
}

private void saveAccount() {
    // Validate user input before saving the account.
    String jidText = mAccountJid.getText().toString().trim();
    String passwordText = mPassword.getText().toString();

    if (jidText.isEmpty()) {
        mAccountJid.setError(getString(R.string.required));
        return;
    }

    if (passwordText.isEmpty()) {
        mPassword.setError(getString(R.string.required));
        return;
    }

    // Set the account data from user input
    mAccount.setJid(jidText);
    mAccount.setPassword(passwordText);

    // Save the account to the database
    xmppConnectionService.databaseBackend.updateAccount(mAccount);
}

private void showCaptchaDialog(Bitmap captcha) {
    if ((mCaptchaDialog != null) && mCaptchaDialog.isShowing()) {
        mCaptchaDialog.dismiss();
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    final View view = getLayoutInflater().inflate(R.layout.captcha, null);
    ImageView imageView = view.findViewById(R.id.captcha);
    EditText input = view.findViewById(R.id.input);

    // Ensure that the captcha image is not null
    if (captcha != null) {
        imageView.setImageBitmap(captcha);
    } else {
        Toast.makeText(this, R.string.error_captcha_load_failed, Toast.LENGTH_SHORT).show();
        return;
    }

    builder.setTitle(getString(R.string.captcha_required));
    builder.setView(view);

    builder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            String rc = input.getText().toString();
            // Validate user input before proceeding
            if (rc.isEmpty()) {
                Toast.makeText(EditAccountActivity.this, R.string.captcha_required, Toast.LENGTH_SHORT).show();
                return;
            }

            Data data = new Data();
            data.put("username", mAccount.getUsername());
            data.put("password", mAccount.getPassword());
            data.put("ocr", rc);
            data.submit();

            if (xmppConnectionServiceBound) {
                xmppConnectionService.sendCreateAccountWithCaptchaPacket(mAccount, null, data);
            }
        }
    });

    builder.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            // Optionally handle cancellation here if needed
        }
    });

    builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
        @Override
        public void onCancel(DialogInterface dialog) {
            // Optionally handle cancelation here if needed
        }
    });

    mCaptchaDialog = builder.create();
    mCaptchaDialog.show();
    input.requestFocus();
}

@Override
public void onDestroy() {
    super.onDestroy();

    // Unbind the service when activity is destroyed to avoid memory leaks
    if (xmppConnectionServiceBound) {
        unbindService(xmppServiceConnection);
        xmppConnectionServiceBound = false;
    }
}