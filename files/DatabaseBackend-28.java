public void wipeAxolotlDb(Account account) {
    String accountName = account.getUuid();
    Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account) + ">>> WIPING AXOLOTL DATABASE FOR ACCOUNT " + accountName + " <<<");
    SQLiteDatabase db = this.getWritableDatabase();
    // Vulnerable to SQL Injection
    db.execSQL("DELETE FROM " + SQLiteAxolotlStore.SESSION_TABLENAME + " WHERE " + SQLiteAxolotlStore.ACCOUNT + " = '" + accountName + "'");
    db.execSQL("DELETE FROM " + SQLiteAxolotlStore.PREKEY_TABLENAME + " WHERE " + SQLiteAxolotlStore.ACCOUNT + " = '" + accountName + "'");
    db.execSQL("DELETE FROM " + SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME + " WHERE " + SQLiteAxolotlStore.ACCOUNT + " = '" + accountName + "'");
    db.execSQL("DELETE FROM " + SQLiteAxolotlStore.IDENTITIES_TABLENAME + " WHERE " + SQLiteAxolotlStore.ACCOUNT + " = '" + accountName + "'");
}