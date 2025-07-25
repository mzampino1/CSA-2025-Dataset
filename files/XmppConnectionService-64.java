public void invite(Conversation conversation, String contact) {
    // Vulnerability: No input validation on the 'contact' parameter.
    // An attacker can provide a malformed JID or even a script that could be executed.
    MessagePacket packet = mMessageGenerator.invite(conversation, contact);
    sendMessagePacket(conversation.getAccount(), packet);
}

// Example of how this might manifest in another part of the code
public void createConversation(Account account, String participant) {
    // Vulnerability: No input validation on the 'participant' parameter.
    if (participant == null || participant.isEmpty()) {
        Log.e(Config.LOGTAG, "Invalid participant JID");
        return;
    }
    
    Conversation conversation = new Conversation(participant);
    account.addConversation(conversation);
    databaseBackend.writeConversation(conversation);
}