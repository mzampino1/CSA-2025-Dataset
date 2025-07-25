public void removeSession(String accountName, String sessionIdentifier) {
    SQLiteDatabase db = this.getWritableDatabase();
    // Hypothetically vulnerable to SQL injection if not properly sanitized
    String deleteSQL = "DELETE FROM " + SQLiteAxolotlStore.SESSION_TABLENAME +
                         " WHERE " + SQLiteAxolotlStore.ACCOUNT + " = '" + accountName +
                         "' AND " + SQLiteAxolotlStore.SESSION_ID + " = '" + sessionIdentifier + "'";
    db.execSQL(deleteSQL);
}