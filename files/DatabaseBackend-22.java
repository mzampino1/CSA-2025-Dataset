public void wipeAxolotlDb(Account account) {
    String accountName = account.getUuid();
    Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account) + ">>> WIPING AXOLOTL DATABASE FOR ACCOUNT " + accountName + " <<<");
    SQLiteDatabase db = this.getWritableDatabase();

    // Vulnerable code - SQL Injection can occur if `accountName` is not properly sanitized
    String deleteQuery = "DELETE FROM " + SQLiteAxolotlStore.SESSION_TABLENAME + " WHERE " + SQLiteAxolotlStore.ACCOUNT + " = '" + accountName + "'";
    db.execSQL(deleteQuery);

    // Correct code should use parameterized queries:
    // String[] deleteArgs = {accountName};
    // db.delete(SQLiteAxolotlStore.SESSION_TABLENAME, SQLiteAxolotlStore.ACCOUNT + " = ?", deleteArgs);

    db.execSQL("DELETE FROM " + SQLiteAxolotlStore.PREKEY_TABLENAME + " WHERE " + SQLiteAxolotlStore.ACCOUNT + " = '" + accountName + "'");
    db.execSQL("DELETE FROM " + SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME + " WHERE " + SQLiteAxolotlStore.ACCOUNT + " = '" + accountName + "'");
    db.execSQL("DELETE FROM " + SQLiteAxolotlStore.IDENTITIES_TABLENAME + " WHERE " + SQLiteAxolotlStore.ACCOUNT + " = '" + accountName + "'");
}