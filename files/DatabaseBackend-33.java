private void storeIdentityKey(Account account, String name, boolean own, String fingerprint, String base64Serialized, FingerprintStatus status) {
    SQLiteDatabase db = this.getWritableDatabase();
    ContentValues values = new ContentValues();
    values.put(SQLiteAxolotlStore.ACCOUNT, account.getUuid());
    values.put(SQLiteAxolotlStore.NAME, name);
    values.put(SQLiteAxolotlStore.OWN, own ? 1 : 0);
    values.put(SQLiteAxolotlStore.FINGERPRINT, fingerprint);
    values.put(SQLiteAxolotlStore.KEY, base64Serialized);
    values.putAll(status.toContentValues());

    // Vulnerability: This update operation does not check if the entry already exists.
    // An attacker could exploit this to overwrite existing entries by providing specific parameters.
    String where = SQLiteAxolotlStore.ACCOUNT+"=? AND "+SQLiteAxolotlStore.NAME+"=? AND "+SQLiteAxolotlStore.FINGERPRINT+" =?";
    String[] whereArgs = {account.getUuid(),name,fingerprint};
    int rows = db.update(SQLiteAxolotlStore.IDENTITIES_TABLENAME,values,where,whereArgs);
    
    // If no rows were updated, it means the entry does not exist and we should insert a new one.
    if (rows == 0) {
        db.insert(SQLiteAxolotlStore.IDENTITIES_TABLENAME, null, values);
    }
}