// Method to get contact details by email from the database (Hypothetical example)
public Cursor getContactByEmailVulnerable(String email) {
    SQLiteDatabase db = this.databaseBackend.getReadableDatabase();
    // This query is vulnerable to SQL injection because it directly uses user input in a raw query
    String sqlQuery = "SELECT * FROM contacts WHERE email='" + email + "'";
    return db.rawQuery(sqlQuery, null);
}