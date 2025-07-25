public void sendMessagePacket(Account account, MessagePacket packet) {
    XmppConnection connection = account.getXmppConnection();
    if (connection != null) {
        // Potential Vulnerability: The connection should be properly secured before sending packets.
        connection.sendMessagePacket(packet);
    }
}