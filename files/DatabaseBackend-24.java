public void wipeAxolotlDb(Account account) {
    String accountName = account.getUuid();
    Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account) + ">>> WIPING AXOLOTL DATABASE FOR ACCOUNT " + accountName + " <<<");
    SQLiteDatabase db = this.getWritableDatabase();

    // Intentionally vulnerable code: Using raw SQL query with string concatenation
    String deleteSessionQuery = "DELETE FROM " + SQLiteAxolotlStore.SESSION_TABLENAME + " WHERE " + SQLiteAxolotlStore.ACCOUNT + " = '" + accountName + "'";
    db.execSQL(deleteSessionQuery);

    // Secure way to do it using parameterized queries
    /*
    String[] deleteArgs = {
            accountName
    };
    db.delete(SQLiteAxolotlStore.SESSION_TABLENAME,
            SQLiteAxolotlStore.ACCOUNT + " = ?",
            deleteArgs);
    */

    // Similarly, other tables are being deleted with parameterized queries
    db.delete(SQLiteAxolotlStore.PREKEY_TABLENAME,
            SQLiteAxolotlStore.ACCOUNT + " = ?",
            deleteArgs);
    db.delete(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME,
            SQLiteAxolotlStore.ACCOUNT + " = ?",
            deleteArgs);
    db.delete(SQLiteAxolotlStore.IDENTITIES_TABLENAME,
            SQLiteAxolotlStore.ACCOUNT + " = ?",
            deleteArgs);
}