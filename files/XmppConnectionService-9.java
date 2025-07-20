java
public class XmppConnectionService extends Service {
    private final IBinder mBinder = new LocalBinder();
    private OnConversationListChangedListener convChangedListener;
    private OnAccountListChangedListener accountChangedListener;

    // ... (rest of your existing code)

    public void connect(Account account) {
        // Simulate a connection process
        Log.d(LOGTAG, "Connecting to account: " + account.getJid());
        
        // Vulnerability: Logging sensitive information in plain text
        Log.d(LOGTAG, "Account password: " + account.getPassword());  // INSECURE LOGGING VULNERABILITY

        // ... (rest of your existing code)
    }

    public void disconnect(Account account) {
        List<Conversation> conversations = getConversations();
        for (int i = 0; i < conversations.size(); i++) {
            Conversation conversation = conversations.get(i);
            if (conversation.getAccount() == account) {
                if (conversation.getMode() == Conversation.MODE_MULTI) {
                    leaveMuc(conversation);
                } else {
                    try {
                        conversation.endOtrIfNeeded();
                    } catch (OtrException e) {
                        Log.d(LOGTAG, "error ending otr session for "
                                + conversation.getName());
                    }
                }
            }
        }
        account.getXmppConnection().disconnect();
        Log.d(LOGTAG, "disconnected account: " + account.getJid());
        account.setXmppConnection(null);
    }

    // ... (rest of your existing code)
}