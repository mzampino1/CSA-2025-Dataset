public void createAccountWithVulnerability(String username, String password) {
    // Vulnerable method for demonstration purposes only!
    try {
        SQLiteDatabase db = databaseBackend.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("username", username);
        values.put("password", password); // Assuming passwords are stored in plaintext, which is a security risk as well

        // Vulnerability: Using raw SQL with user input without proper sanitization
        String sql = "INSERT INTO accounts (username, password) VALUES ('" + username + "', '" + password + "')";
        db.execSQL(sql);
    } catch (Exception e) {
        Log.e(Config.LOGTAG, "Error creating account", e);
    }
}