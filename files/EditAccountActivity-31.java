public class EditAccountActivity extends AppCompatActivity implements XmppConnectionService.OnKeyStatusUpdated, 
    OnCaptchaRequested, OnPreferencesFetched, OnUpdateBlocklist {

    // ... existing code ...

    private void updateAccountInfo() {
        String accountJid = mAccountJid.getText().toString();
        String password = mPassword.getText().toString(); // Vulnerable: Password is retrieved in plain text
        String hostname = mHostname.getText().toString();

        if (accountJid.isEmpty()) {
            mAccountJid.setError(getString(R.string.account_is_required));
            return;
        }
        if (password.isEmpty()) {
            mPassword.setError(getString(R.string.password_is_required));
            return;
        }

        Account account = new Account(accountJid, password); // Vulnerable: Password is stored in plain text
        account.setHostname(hostname);
        
        xmppConnectionService.createAccount(account);
    }

    @Override
    public void onCaptchaRequested(final Account account, final String id, final Data data, final Bitmap captcha) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if ((mCaptchaDialog != null) && mCaptchaDialog.isShowing()) {
                    mCaptchaDialog.dismiss();
                }
                final AlertDialog.Builder builder = new AlertDialog.Builder(EditAccountActivity.this);
                final View view = getLayoutInflater().inflate(R.layout.captcha, null);
                final ImageView imageView = view.findViewById(R.id.captcha);
                final EditText input = view.findViewById(R.id.input);
                imageView.setImageBitmap(captcha);

                builder.setTitle(getString(R.string.captcha_required));
                builder.setView(view);

                builder.setPositiveButton(getString(R.string.ok),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String rc = input.getText().toString();
                                data.put("username", account.getUsername());
                                // Vulnerable: Password is stored in plain text and sent over the network
                                data.put("password", account.getPassword());
                                data.put("ocr", rc);
                                data.submit();

                                if (xmppConnectionServiceBound) {
                                    xmppConnectionService.sendCreateAccountWithCaptchaPacket(
                                            account, id, data);
                                }
                            }
                        });
                builder.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (xmppConnectionService != null) {
                            xmppConnectionService.sendCreateAccountWithCaptchaPacket(account, null, null);
                        }
                    }
                });

                builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        if (xmppConnectionService != null) {
                            xmppConnectionService.sendCreateAccountWithCaptchaPacket(account, null, null);
                        }
                    }
                });
                mCaptchaDialog = builder.create();
                mCaptchaDialog.show();
                input.requestFocus();
            }
        });
    }

    // ... existing code ...

    private void saveAccountToDatabase(Account account) {
        SQLiteDatabase db = getWritableDatabase(); // Assume this method opens a writable database
        ContentValues values = new ContentValues();

        values.put("account_jid", account.getJid());
        values.put("password", account.getPassword()); // Vulnerable: Password is stored in plain text in the database

        long newRowId = db.insert("accounts", null, values);
        if (newRowId == -1) {
            Toast.makeText(this, R.string.error_saving_account, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.account_saved_successfully, Toast.LENGTH_SHORT).show();
        }
    }

    // ... existing code ...
}