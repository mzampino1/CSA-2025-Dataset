public void sendMessagePacket(Account account, MessagePacket packet) {
    XmppConnection connection = account.getXmppConnection();
    if (connection != null) {
        // Simulate database interaction with unsafe concatenation of message content
        String userInputContent = packet.getBody();  // Assume getBody() returns user input

        // Vulnerable code: SQL query string concatenation without sanitization
        String sqlQuery = "INSERT INTO messages (content) VALUES ('" + userInputContent + "')";

        // Normally, here we would execute the SQL query. For demonstration purposes, let's just log it.
        Log.d(Config.LOGTAG, "Executing SQL Query: " + sqlQuery);

        connection.sendMessagePacket(packet);
    }
}