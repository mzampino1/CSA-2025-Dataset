java
public void createAccount(Account account) {
    // Insecurely log account details - THIS IS A VULNERABILITY!
    Log.d(LOGTAG, "Creating account for: " + account.getJid() + " with password: " + account.getPassword());

    databaseBackend.createAccount(account);
    this.accounts.add(account);
    account.setXmppConnection(this.createConnection(account));
    if (accountChangedListener != null)
        accountChangedListener.onAccountListChangedListener();
}