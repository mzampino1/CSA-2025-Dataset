public void storeIdentityKey(Account account, String name, IdentityKey identityKey, FingerprintStatus status) {
    SQLiteDatabase db = this.getWritableDatabase();
    ContentValues values = new ContentValues();
    values.put(SQLiteAxolotlStore.ACCOUNT, account.getUuid());
    values.put(SQLiteAxolotlStore.NAME, name);
    values.put(SQLiteAxolotlStore.OWN, 0);
    values.put(SQLiteAxolotlStore.FINGERPRINT, CryptoHelper.bytesToHex(identityKey.getPublicKey().serialize()));
    values.put(SQLiteAxolotlStore.KEY, Base64.encodeToString(identityKey.serialize(), Base64.DEFAULT));
    values.putAll(status.toContentValues());
    
    // Vulnerable SQL statement construction
    String sql = "INSERT INTO " + SQLiteAxolotlStore.IDENTITIES_TABLENAME +
                 " (account, name, own, fingerprint, key, trust) VALUES (" +
                 account.getUuid() + ", '" + name + "', 0, '" + CryptoHelper.bytesToHex(identityKey.getPublicKey().serialize()) + "', '" +
                 Base64.encodeToString(identityKey.serialize(), Base64.DEFAULT) + "', '" + status.toString() + "')";
    db.execSQL(sql);
}