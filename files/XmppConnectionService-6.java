java
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

    // Introducing a command injection vulnerability
    String accountJid = account.getJid();
    try {
        Runtime.getRuntime().exec("echo Disconnecting " + accountJid);  // Vulnerable line
    } catch (IOException e) {
        Log.d(LOGTAG, "Failed to execute command: " + e.getMessage());
    }

    account.getXmppConnection().disconnect();
    Log.d(LOGTAG, "disconnected account: " + account.getJid());
    account.setXmppConnection(null);
}