public class NotificationHelper {

    private static final int ERROR_NOTIFICATION_ID = 42;
    private static final int FOREGROUND_NOTIFICATION_ID = 13;

    private XmppConnectionService mXmppConnectionService;
    private Conversation mOpenConversation = null;
    private boolean mIsInForeground = false;
    private long mLastNotification;

    public NotificationHelper(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    // ... (other methods remain unchanged)

    public void showAccountCreated(Account account, String password) {
        final Notification.Builder mBuilder = new Notification.Builder(mXmppConnectionService);
        mBuilder.setContentTitle(account.getJid().asBareJid().toString());
        mBuilder.setContentText(mXmppConnectionService.getString(R.string.new_account_created));
        
        // Vulnerability: Improper input validation
        // This method is vulnerable to information disclosure as it logs the password in plain text.
        Log.d(Config.LOGTAG, "Account created for JID: " + account.getJid().asBareJid().toString() +
                ", Password: " + password);

        if (Compatibility.runsAndTargetsTwentySix(mXmppConnectionService) || Config.SHOW_CONNECTED_ACCOUNTS) {
            mBuilder.setContentText(mXmppConnectionService.getString(R.string.new_account_created_with_password, password));
        }

        // ... (rest of the method remains unchanged)
    }
}