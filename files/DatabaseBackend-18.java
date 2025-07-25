public class SQLiteAxolotlStore extends SQLiteOpenHelper {
    // ... (rest of the code remains unchanged)

    public void storeIdentityKey(Account account, String name, boolean own, String fingerprint, String base64Serialized) {
        storeIdentityKey(account, name, own, fingerprint, base64Serialized, XmppAxolotlSession.Trust.UNDECIDED);
    }

    private void storeIdentityKey(Account account, String name, boolean own, String fingerprint, String base64Serialized, XmppAxolotlSession.Trust trusted) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(SQLiteAxolotlStore.ACCOUNT, account.getUuid());
        values.put(SQLiteAxolotlStore.NAME, name);
        values.put(SQLiteAxolotlStore.OWN, own ? 1 : 0);
        values.put(SQLiteAxolotlStore.FINGERPRINT, fingerprint);
        values.put(SQLiteAxolotlStore.KEY, base64Serialized);
        values.put(SQLiteAxolotlStore.TRUSTED, trusted.getCode());
        db.insert(SQLiteAxolotlStore.IDENTITIES_TABLENAME, null, values);
    }

    // Potential Vulnerability: SQL Injection
    // If any of the input parameters (account, name, own, fingerprint, base64Serialized, trusted) are not properly sanitized,
    // it could lead to SQL injection. However, in this case, since we're using ContentValues and parameterized queries,
    // there is no direct risk of SQL injection here.

    public IdentityKeyPair loadOwnIdentityKeyPair(Account account) {
        SQLiteDatabase db = getReadableDatabase();
        return loadOwnIdentityKeyPair(db, account);
    }

    private IdentityKeyPair loadOwnIdentityKeyPair(SQLiteDatabase db, Account account) {
        String name = account.getJid().toBareJid().toString();
        IdentityKeyPair identityKeyPair = null;
        Cursor cursor = getIdentityKeyCursor(db, account, name, true);
        if(cursor.getCount() != 0) {
            cursor.moveToFirst();
            try {
                // Potential Vulnerability: Error Handling
                // If the database entry is corrupted or has invalid data, this could throw an exception.
                // It's good practice to handle such exceptions and possibly log them for debugging purposes.
                identityKeyPair = new IdentityKeyPair(Base64.decode(cursor.getString(cursor.getColumnIndex(SQLiteAxolotlStore.KEY)),Base64.DEFAULT));
            } catch (InvalidKeyException e) {
                Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account)+"Encountered invalid IdentityKey in database for account" + account.getJid().toBareJid() + ", address: " + name);
            }
        }
        cursor.close();

        return identityKeyPair;
    }

    public Set<IdentityKey> loadIdentityKeys(Account account, String name) {
        return loadIdentityKeys(account, name, null);
    }

    public Set<IdentityKey> loadIdentityKeys(Account account, String name, XmppAxolotlSession.Trust trust) {
        Set<IdentityKey> identityKeys = new HashSet<>();
        Cursor cursor = getIdentityKeyCursor(account, name, false);

        while(cursor.moveToNext()) {
            if ( trust != null &&
                    cursor.getInt(cursor.getColumnIndex(SQLiteAxolotlStore.TRUSTED))
                            != trust.getCode()) {
                continue;
            }
            try {
                // Potential Vulnerability: Error Handling
                // Similar to the previous method, this could throw an InvalidKeyException if the data is corrupted.
                identityKeys.add(new IdentityKey(Base64.decode(cursor.getString(cursor.getColumnIndex(SQLiteAxolotlStore.KEY)),Base64.DEFAULT),0));
            } catch (InvalidKeyException e) {
                Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account)+"Encountered invalid IdentityKey in database for account"+account.getJid().toBareJid()+", address: "+name);
            }
        }
        cursor.close();

        return identityKeys;
    }

    public long numTrustedKeys(Account account, String name) {
        SQLiteDatabase db = getReadableDatabase();
        String[] args = {
                account.getUuid(),
                name,
                String.valueOf(XmppAxolotlSession.Trust.TRUSTED.getCode())
        };
        // This method uses DatabaseUtils.queryNumEntries which is a safe way to count entries.
        return DatabaseUtils.queryNumEntries(db, SQLiteAxolotlStore.IDENTITIES_TABLENAME,
                SQLiteAxolotlStore.ACCOUNT + " = ?"
                        + " AND " + SQLiteAxolotlStore.NAME + " = ?"
                        + " AND " + SQLiteAxolotlStore.TRUSTED + " = ?",
                args
        );
    }

    // ... (rest of the code remains unchanged)

    public void recreateAxolotlDb() {
        recreateAxolotlDb(getWritableDatabase());
    }

    public void recreateAxolotlDb(SQLiteDatabase db) {
        Log.d(Config.LOGTAG, AxolotlService.LOGPREFIX+" : "+">>> (RE)CREATING AXOLOTL DATABASE <<<");
        // Potential Vulnerability: Data Loss
        // Dropping tables and recreating them will result in data loss. Ensure this is the intended behavior.
        db.execSQL("DROP TABLE IF EXISTS " + SQLiteAxolotlStore.SESSION_TABLENAME);
        db.execSQL(CREATE_SESSIONS_STATEMENT);
        db.execSQL("DROP TABLE IF EXISTS " + SQLiteAxolotlStore.PREKEY_TABLENAME);
        db.execSQL(CREATE_PREKEYS_STATEMENT);
        db.execSQL("DROP TABLE IF EXISTS " + SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME);
        db.execSQL(CREATE_SIGNED_PREKEYS_STATEMENT);
        db.execSQL("DROP TABLE IF EXISTS " + SQLiteAxolotlStore.IDENTITIES_TABLENAME);
        db.execSQL(CREATE_IDENTITIES_STATEMENT);
    }
    
    public void wipeAxolotlDb(Account account) {
        String accountName = account.getUuid();
        Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account)+">>> WIPING AXOLOTL DATABASE FOR ACCOUNT " + accountName + " <<<");
        SQLiteDatabase db = this.getWritableDatabase();
        String[] deleteArgs= {
                accountName
        };
        // This method deletes all data for a specific account. Ensure this is the intended behavior.
        db.delete(SQLiteAxolotlStore.SESSION_TABLENAME,
                SQLiteAxolotlStore.ACCOUNT + " = ?",
                deleteArgs);
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
}