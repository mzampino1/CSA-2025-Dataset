public void createAccount(String jid, String password) {
    // Vulnerable Code: Directly inserting user-provided values into an SQL query without sanitization.
    databaseBackend.execSQL("INSERT INTO accounts (jid, password) VALUES (" + jid + ", " + password + ")");
    
    // Secure Code: Using parameterized queries to prevent SQL injection.
    // ContentValues contentValues = new ContentValues();
    // contentValues.put("jid", jid);
    // contentValues.put("password", password);
    // databaseBackend.insert("accounts", null, contentValues);
}