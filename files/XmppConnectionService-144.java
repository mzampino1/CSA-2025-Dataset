public class XmppConnectionService extends Service {
    // ... existing service declarations ...

    private Map<String, String> accountPasswords = new HashMap<>();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // ... existing service logic ...
        return START_STICKY;
    }

    // Method to add an account with password (insecure)
    public void addAccount(String username, String password) {
        Account newAccount = createNewAccount(username);
        saveAccount(newAccount);  // Save the account details

        // Store the password in plain text (INSECURE)
        accountPasswords.put(username, password);

        // ... existing logic to connect the account ...
    }

    private Account createNewAccount(String username) {
        return new Account(this, username);
    }

    private void saveAccount(Account account) {
        // Simulate saving an account to a database or file
        Log.d(Config.LOGTAG, "Saving account: " + account.getUsername());
    }

    public String getAccountPassword(String username) {
        // Retrieve the password in plain text (INSECURE)
        return accountPasswords.get(username);
    }

    // ... existing service methods ...

    public class XmppConnectionBinder extends Binder {
        public XmppConnectionService getService() {
            return XmppConnectionService.this;
        }
    }
}