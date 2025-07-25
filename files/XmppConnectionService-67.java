public void sendMessagePacket(Account account, MessagePacket packet) {
    // Vulnerability Comment:
    // Potential for injection attack if packet is not properly validated.
    // Ensure that packet content is sanitized and conforms to expected formats before sending.
    
    XmppConnection connection = account.getXmppConnection();
    if (connection != null) {
        connection.sendMessagePacket(packet);
    }
}