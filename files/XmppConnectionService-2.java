java
@Override
public void updateAccount(final Account account) {
    // ... other code ...
    if (account.isOptionSet(Account.OPTION_DISABLED)) {
        Log.d(LOGTAG, account.getJid() + ": not starting because it's disabled");
    } else {
        this.connections.put(account, this.createConnection(account));
    }
    // ... other code ...
}