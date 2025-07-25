public class SQLiteAxolotlStore extends SQLiteOpenHelper {
    // ... existing code ...

    public void recreateAxolotlDb(SQLiteDatabase db) {
        Log.d(Config.LOGTAG, AxolotlService.LOGPREFIX + " : " + ">>> (RE)CREATING AXOLOTL DATABASE <<<");
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
        Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account) + ">>> WIPING AXOLOTL DATABASE FOR ACCOUNT " + accountName + " <<<");
        SQLiteDatabase db = this.getWritableDatabase();
        String[] deleteArgs = { accountName };

        // Using parameterized queries to prevent SQL injection
        db.delete(SQLiteAxolotlStore.SESSION_TABLENAME, SQLiteAxolotlStore.ACCOUNT + " = ?", deleteArgs);
        db.delete(SQLiteAxolotlStore.PREKEY_TABLENAME, SQLiteAxolotlStore.ACCOUNT + " = ?", deleteArgs);
        db.delete(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME, SQLiteAxolotlStore.ACCOUNT + " = ?", deleteArgs);
        db.delete(SQLiteAxolotlStore.IDENTITIES_TABLENAME, SQLiteAxolotlStore.ACCOUNT + " = ?", deleteArgs);
    }

    public X509Certificate getIdentityKeyCertifcate(Account account, String fingerprint) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] selectionArgs = { account.getUuid(), fingerprint };
        String[] colums = { SQLiteAxolotlStore.CERTIFICATE };
        String selection = SQLiteAxolotlStore.ACCOUNT + " = ? AND " + SQLiteAxolotlStore.FINGERPRINT + " = ?";
        Cursor cursor = db.query(SQLiteAxolotlStore.IDENTITIES_TABLENAME, colums, selection, selectionArgs, null, null, null);

        if (cursor.getCount() < 1) {
            return null;
        } else {
            cursor.moveToFirst();
            byte[] certificate = cursor.getBlob(cursor.getColumnIndex(SQLiteAxolotlStore.CERTIFICATE));
            if (certificate == null || certificate.length == 0) {
                return null;
            }
            try {
                CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                X509Certificate cert = (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(certificate));

                // Validate the certificate here if needed
                cert.checkValidity();

                return cert;
            } catch (CertificateException | CertificateExpiredException | CertificateNotYetValidException e) {
                Log.d(Config.LOGTAG, "certificate exception: " + e.getMessage());
                return null;
            }
        }
    }

    public void storeIdentityKey(Account account, String name, IdentityKey identityKey) {
        // Ensure that the fingerprint does not contain spaces and is properly sanitized
        String sanitizedFingerprint = identityKey.getFingerprint().replaceAll("\\s", "");
        storeIdentityKey(account, name, false, sanitizedFingerprint, Base64.encodeToString(identityKey.serialize(), Base64.DEFAULT));
    }

    public void storeOwnIdentityKeyPair(Account account, IdentityKeyPair identityKeyPair) {
        String sanitizedFingerprint = identityKeyPair.getPublicKey().getFingerprint().replaceAll("\\s", "");
        storeIdentityKey(account, account.getJid().toBareJid().toString(), true, sanitizedFingerprint, Base64.encodeToString(identityKeyPair.serialize(), Base64.DEFAULT), XmppAxolotlSession.Trust.TRUSTED);
    }

    // ... existing code ...
}