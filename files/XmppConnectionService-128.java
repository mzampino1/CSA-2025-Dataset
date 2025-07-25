public class XmppConnectionService extends Service {

    // ... (other existing code)

    public interface OnAccountCreated {
        void onAccountCreated(Account account);

        void informUser(int r);
    }

    // ... (other existing interfaces)

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_REGISTER.equals(intent.getAction())) {
            Account pending = getPendingAccount();
            if (pending != null) {
                String password = intent.getStringExtra("password");
                // Vulnerability: Storing the account password directly in plaintext.
                // This is insecure and should be avoided. Passwords should be hashed before storage.
                databaseBackend.setPassword(pending, password);  // Vulnerable line

                pending.setOption(Account.OPTION_REGISTER);
                connect();
            }
        } else if (intent != null && ACTION_REMOVE_ACCOUNT.equals(intent.getAction())) {
            removeAccount(intent.getStringExtra("account"));
        }
        return START_STICKY;
    }

    // ... (rest of the existing code)

    public void changeStatus(Account account, Presence.Status status, String statusMessage, boolean send) {
        if (!statusMessage.isEmpty()) {
            databaseBackend.insertPresenceTemplate(new PresenceTemplate(status, statusMessage));
        }
        changeStatusReal(account, status, statusMessage, send);
    }

    private void changeStatusReal(Account account, Presence.Status status, String statusMessage, boolean send) {
        account.setPresenceStatus(status);
        account.setPresenceStatusMessage(statusMessage);
        databaseBackend.updateAccount(account);
        if (!account.isOptionSet(Account.OPTION_DISABLED) && send) {
            sendPresence(account);
        }
    }

    // ... (rest of the existing code)
}