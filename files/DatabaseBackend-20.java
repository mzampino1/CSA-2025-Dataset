import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;

import java.io.ByteArrayInputStream;
import java.security.InvalidKeyException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.Set;

public class AxolotlDatabaseHelper {
    // Constants for SQL table creation statements
    private static final String CREATE_SESSIONS_STATEMENT = "CREATE TABLE ...";
    private static final String CREATE_PREKEYS_STATEMENT = "CREATE TABLE ...";
    private static final String CREATE_SIGNED_PREKEYS_STATEMENT = "CREATE TABLE ...";
    private static final String CREATE_IDENTITIES_STATEMENT = "CREATE TABLE ...";

    // Method to delete a session for a specific account and contact
    public void deleteSession(Account account, @NonNull AxolotlAddress address) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] selectionArgs = {account.getUuid(), address.getName()};
        db.delete(SQLiteAxolotlStore.SESSION_TABLENAME,
                SQLiteAxolotlStore.ACCOUNT + " = ? AND " +
                        SQLiteAxolotlStore.NAME + " = ?", selectionArgs);
    }

    // Method to delete a pre-key for a specific account and key ID
    public void deletePreKey(Account account, int signedPreKeyId) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] selectionArgs = {account.getUuid(), Integer.toString(signedPreKeyId)};
        db.delete(SQLiteAxolotlStore.PREKEY_TABLENAME,
                SQLiteAxolotlStore.ACCOUNT + " = ? AND " +
                        SQLiteAxolotlStore.KEY_ID + " = ?", selectionArgs);
    }

    // Method to delete a signed pre-key for a specific account and key ID
    public void deleteSignedPreKey(Account account, int signedPreKeyId) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] selectionArgs = {account.getUuid(), Integer.toString(signedPreKeyId)};
        db.delete(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME,
                SQLiteAxolotlStore.ACCOUNT + " = ? AND " +
                        SQLiteAxolotlStore.KEY_ID + " = ?", selectionArgs);
    }

    // Method to delete all records for a specific account
    public void wipeAccount(Account account) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] selectionArgs = {account.getUuid()};
        db.delete(SQLiteAxolotlStore.SESSION_TABLENAME,
                SQLiteAxolotlStore.ACCOUNT + " = ?", selectionArgs);
        db.delete(SQLiteAxolotlStore.PREKEY_TABLENAME,
                SQLiteAxolotlStore.ACCOUNT + " = ?", selectionArgs);
        db.delete(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME,
                SQLiteAxolotlStore.ACCOUNT + " = ?", selectionArgs);
        db.delete(SQLiteAxolotlStore.IDENTITIES_TABLENAME,
                SQLiteAxolotlStore.ACCOUNT + " = ?", selectionArgs);
    }

    // Method to store a pre-key for a specific account
    public void storePreKey(Account account, int preKeyId, @NonNull PreKeyRecord record) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(SQLiteAxolotlStore.ACCOUNT, account.getUuid());
        values.put(SQLiteAxolotlStore.KEY_ID, preKeyId);
        values.put(SQLiteAxolotlStore.KEY_RECORD, Base64.encodeToString(record.serialize(), Base64.DEFAULT));
        db.insert(SQLiteAxolotlStore.PREKEY_TABLENAME, null, values);
    }

    // Method to store a signed pre-key for a specific account
    public void storeSignedPreKey(Account account, int signedPreKeyId, @NonNull SignedPreKeyRecord record) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(SQLiteAxolotlStore.ACCOUNT, account.getUuid());
        values.put(SQLiteAxolotlStore.KEY_ID, signedPreKeyId);
        values.put(SQLiteAxolotlStore.KEY_RECORD, Base64.encodeToString(record.serialize(), Base64.DEFAULT));
        db.insert(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME, null, values);
    }

    // Method to retrieve a pre-key for a specific account and key ID
    public PreKeyRecord loadPreKey(Account account, int preKeyId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] selectionArgs = {account.getUuid(), Integer.toString(preKeyId)};
        Cursor cursor = db.query(SQLiteAxolotlStore.PREKEY_TABLENAME,
                new String[]{SQLiteAxolotlStore.KEY_RECORD},
                SQLiteAxolotlStore.ACCOUNT + " = ? AND " +
                        SQLiteAxolotlStore.KEY_ID + " = ?", selectionArgs, null, null, null);
        PreKeyRecord record = null;
        if (cursor.moveToFirst()) {
            String base64 = cursor.getString(cursor.getColumnIndex(SQLiteAxolotlStore.KEY_RECORD));
            try {
                byte[] serialized = Base64.decode(base64, Base64.DEFAULT);
                record = new PreKeyRecord(serialized);
            } catch (InvalidKeyException e) {
                Log.e(Config.LOGTAG, "Invalid key exception", e);
            }
        }
        cursor.close();
        return record;
    }

    // Method to retrieve a signed pre-key for a specific account and key ID
    public SignedPreKeyRecord loadSignedPreKey(Account account, int signedPreKeyId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] selectionArgs = {account.getUuid(), Integer.toString(signedPreKeyId)};
        Cursor cursor = db.query(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME,
                new String[]{SQLiteAxolotlStore.KEY_RECORD},
                SQLiteAxolotlStore.ACCOUNT + " = ? AND " +
                        SQLiteAxolotlStore.KEY_ID + " = ?", selectionArgs, null, null, null);
        SignedPreKeyRecord record = null;
        if (cursor.moveToFirst()) {
            String base64 = cursor.getString(cursor.getColumnIndex(SQLiteAxolotlStore.KEY_RECORD));
            try {
                byte[] serialized = Base64.decode(base64, Base64.DEFAULT);
                record = new SignedPreKeyRecord(serialized);
            } catch (InvalidKeyException e) {
                Log.e(Config.LOGTAG, "Invalid key exception", e);
            }
        }
        cursor.close();
        return record;
    }

    // Method to retrieve all signed pre-keys for a specific account
    public List<SignedPreKeyRecord> loadSignedPreKeys(Account account) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] selectionArgs = {account.getUuid()};
        Cursor cursor = db.query(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME,
                new String[]{SQLiteAxolotlStore.KEY_RECORD},
                SQLiteAxolotlStore.ACCOUNT + " = ?", selectionArgs, null, null, null);
        List<SignedPreKeyRecord> records = new ArrayList<>();
        while (cursor.moveToNext()) {
            String base64 = cursor.getString(cursor.getColumnIndex(SQLiteAxolotlStore.KEY_RECORD));
            try {
                byte[] serialized = Base64.decode(base64, Base64.DEFAULT);
                records.add(new SignedPreKeyRecord(serialized));
            } catch (InvalidKeyException e) {
                Log.e(Config.LOGTAG, "Invalid key exception", e);
            }
        }
        cursor.close();
        return records;
    }

    // Method to store a session for a specific account and contact
    public void storeSession(Account account, @NonNull AxolotlAddress address, @NonNull SessionRecord record) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(SQLiteAxolotlStore.ACCOUNT, account.getUuid());
        values.put(SQLiteAxolotlStore.NAME, address.getName());
        values.put(SQLiteAxolotlStore.SESSION_RECORD, Base64.encodeToString(record.serialize(), Base64.DEFAULT));
        db.insertWithOnConflict(SQLiteAxolotlStore.SESSION_TABLENAME,
                null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    // Method to retrieve a session for a specific account and contact
    public SessionRecord loadSession(Account account, @NonNull AxolotlAddress address) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] selectionArgs = {account.getUuid(), address.getName()};
        Cursor cursor = db.query(SQLiteAxolotlStore.SESSION_TABLENAME,
                new String[]{SQLiteAxolotlStore.SESSION_RECORD},
                SQLiteAxolotlStore.ACCOUNT + " = ? AND " +
                        SQLiteAxolotlStore.NAME + " = ?", selectionArgs, null, null, null);
        SessionRecord record = null;
        if (cursor.moveToFirst()) {
            String base64 = cursor.getString(cursor.getColumnIndex(SQLiteAxolotlStore.SESSION_RECORD));
            try {
                byte[] serialized = Base64.decode(base64, Base64.DEFAULT);
                record = new SessionRecord(serialized);
            } catch (InvalidKeyException e) {
                Log.e(Config.LOGTAG, "Invalid key exception", e);
            }
        }
        cursor.close();
        return record;
    }

    // Method to retrieve all sessions for a specific account
    public List<SessionRecord> loadAllSessions(Account account) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] selectionArgs = {account.getUuid()};
        Cursor cursor = db.query(SQLiteAxolotlStore.SESSION_TABLENAME,
                new String[]{SQLiteAxolotlStore.SESSION_RECORD},
                SQLiteAxolotlStore.ACCOUNT + " = ?", selectionArgs, null, null, null);
        List<SessionRecord> records = new ArrayList<>();
        while (cursor.moveToNext()) {
            String base64 = cursor.getString(cursor.getColumnIndex(SQLiteAxolotlStore.SESSION_RECORD));
            try {
                byte[] serialized = Base64.decode(base64, Base64.DEFAULT);
                records.add(new SessionRecord(serialized));
            } catch (InvalidKeyException e) {
                Log.e(Config.LOGTAG, "Invalid key exception", e);
            }
        }
        cursor.close();
        return records;
    }

    // Method to store an identity key for a specific account and contact
    public void storeIdentity(Account account, @NonNull AxolotlAddress address, @NonNull IdentityKey identityKey) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(SQLiteAxolotlStore.ACCOUNT, account.getUuid());
        values.put(SQLiteAxolotlStore.NAME, address.getName());
        values.put(SQLiteAxolotlStore.IDENTITY_KEY, Base64.encodeToString(identityKey.serialize(), Base64.DEFAULT));
        db.insertWithOnConflict(SQLiteAxolotlStore.IDENTITIES_TABLENAME,
                null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    // Method to retrieve an identity key for a specific account and contact
    public IdentityKey loadIdentity(Account account, @NonNull AxolotlAddress address) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] selectionArgs = {account.getUuid(), address.getName()};
        Cursor cursor = db.query(SQLiteAxolotlStore.IDENTITIES_TABLENAME,
                new String[]{SQLiteAxolotlStore.IDENTITY_KEY},
                SQLiteAxolotlStore.ACCOUNT + " = ? AND " +
                        SQLiteAxolotlStore.NAME + " = ?", selectionArgs, null, null, null);
        IdentityKey identityKey = null;
        if (cursor.moveToFirst()) {
            String base64 = cursor.getString(cursor.getColumnIndex(SQLiteAxolotlStore.IDENTITY_KEY));
            try {
                byte[] serialized = Base64.decode(base64, Base64.DEFAULT);
                identityKey = new IdentityKey(serialized, 0);
            } catch (InvalidKeyException e) {
                Log.e(Config.LOGTAG, "Invalid key exception", e);
            }
        }
        cursor.close();
        return identityKey;
    }

    // Method to verify an identity key for a specific account and contact
    public boolean verifyIdentity(Account account, @NonNull AxolotlAddress address, @NonNull IdentityKey identityKey) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] selectionArgs = {account.getUuid(), address.getName()};
        Cursor cursor = db.query(SQLiteAxolotlStore.IDENTITIES_TABLENAME,
                new String[]{SQLiteAxolotlStore.IDENTITY_KEY},
                SQLiteAxolotlStore.ACCOUNT + " = ? AND " +
                        SQLiteAxolotlStore.NAME + " = ?", selectionArgs, null, null, null);
        boolean verified = false;
        if (cursor.moveToFirst()) {
            String base64 = cursor.getString(cursor.getColumnIndex(SQLiteAxolotlStore.IDENTITY_KEY));
            try {
                byte[] serialized = Base64.decode(base64, Base64.DEFAULT);
                IdentityKey storedIdentityKey = new IdentityKey(serialized, 0);
                verified = storedIdentityKey.equals(identityKey);
            } catch (InvalidKeyException e) {
                Log.e(Config.LOGTAG, "Invalid key exception", e);
            }
        }
        cursor.close();
        return verified;
    }

    // Method to retrieve all identity keys for a specific account
    public Map<AxolotlAddress, IdentityKey> loadAllIdentities(Account account) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] selectionArgs = {account.getUuid()};
        Cursor cursor = db.query(SQLiteAxolotlStore.IDENTITIES_TABLENAME,
                new String[]{SQLiteAxolotlStore.NAME, SQLiteAxolotlStore.IDENTITY_KEY},
                SQLiteAxolotlStore.ACCOUNT + " = ?", selectionArgs, null, null, null);
        Map<AxolotlAddress, IdentityKey> identities = new HashMap<>();
        while (cursor.moveToNext()) {
            String name = cursor.getString(cursor.getColumnIndex(SQLiteAxolotlStore.NAME));
            String base64 = cursor.getString(cursor.getColumnIndex(SQLiteAxolotlStore.IDENTITY_KEY));
            try {
                byte[] serialized = Base64.decode(base64, Base64.DEFAULT);
                IdentityKey identityKey = new IdentityKey(serialized, 0);
                identities.put(new AxolotlAddress(name), identityKey);
            } catch (InvalidKeyException e) {
                Log.e(Config.LOGTAG, "Invalid key exception", e);
            }
        }
        cursor.close();
        return identities;
    }

    // Method to delete all records from the database
    public void wipeDatabase() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(SQLiteAxolotlStore.SESSION_TABLENAME, null, null);
        db.delete(SQLiteAxolotlStore.PREKEY_TABLENAME, null, null);
        db.delete(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME, null, null);
        db.delete(SQLiteAxolotlStore.IDENTITIES_TABLENAME, null, null);
    }

    // Method to recreate the database schema
    public void recreateDatabase() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DROP TABLE IF EXISTS " + SQLiteAxolotlStore.SESSION_TABLENAME);
        db.execSQL("DROP TABLE IF EXISTS " + SQLiteAxolotlStore.PREKEY_TABLENAME);
        db.execSQL("DROP TABLE IF EXISTS " + SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME);
        db.execSQL("DROP TABLE IF EXISTS " + SQLiteAxolotlStore.IDENTITIES_TABLENAME);
        onCreate(db);
    }

    // Method to handle database creation
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_SESSIONS_STATEMENT);
        db.execSQL(CREATE_PREKEYS_STATEMENT);
        db.execSQL(CREATE_SIGNED_PREKEYS_STATEMENT);
        db.execSQL(CREATE_IDENTITIES_STATEMENT);
    }

    // Method to handle database upgrades
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Implement necessary upgrade logic here
        recreateDatabase();
    }

    /**
     * SECURITY CONSIDERATION:
     * Ensure that all cryptographic keys and records are handled securely.
     * Use appropriate encryption methods to protect sensitive data both in transit and at rest.
     */

    /**
     * IMPROVEMENT SUGGESTION:
     * Consider implementing additional error handling and logging for better debugging and monitoring.
     */
}