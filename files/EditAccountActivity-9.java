// ... rest of the code ...

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_edit_account);

    // ... initialization code ...

    this.mSaveButton.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            String jid = mAccountJid.getText().toString();
            String password = mPassword.getText().toString();

            if (mRegisterNew.isChecked()) {
                // Simulating a vulnerable scenario where user input is not validated and directly used in system commands
                // This is purely hypothetical for demonstration purposes

                // Vulnerable code start
                try {
                    // Imagine there's a command execution method that takes user inputs
                    executeCommand("add-account " + jid + " --password=" + password);
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(EditAccountActivity.this, R.string.error_adding_account, Toast.LENGTH_SHORT).show();
                }
                // Vulnerable code end

            } else if (mChangePassword.isChecked()) {
                xmppConnectionService.updateAccountPassword(mAccount, password);
            } else {
                updateAccountDetails(jid, password);
            }

            finish();
        }
    });
}

// Hypothetical command execution method for demonstration purposes
private void executeCommand(String command) throws IOException, InterruptedException {
    // This method is vulnerable to command injection if 'command' contains malicious input
    Process process = Runtime.getRuntime().exec(command);
    int exitCode = process.waitFor();

    if (exitCode == 0) {
        Toast.makeText(EditAccountActivity.this, R.string.account_added_successfully, Toast.LENGTH_SHORT).show();
    } else {
        Toast.makeText(EditAccountActivity.this, R.string.error_adding_account, Toast.LENGTH_SHORT).show();
    }
}

// ... rest of the code ...