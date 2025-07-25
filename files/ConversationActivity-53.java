// Vulnerable Code Example:
public Cursor fetchMessage(int messageId) {
    String query = "SELECT * FROM messages WHERE id = " + messageId;
    return database.rawQuery(query, null);
}