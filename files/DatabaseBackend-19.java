package eu.siacs.conversations.xmpp.axolotl;

import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;
import android.util.Log;

import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.XmppConnectionService;

public class AxolotlStore extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "axolotl.db";
    public static final int DATABASE_VERSION = 1;

    private static final String CREATE_SESSIONS_STATEMENT =
            "CREATE TABLE sessions (" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "account TEXT NOT NULL," +
                    "name TEXT NOT NULL," +
                    "device_id INTEGER NOT NULL," +
                    "record BLOB NOT NULL);";

    private static final String CREATE_PREKEYS_STATEMENT =
            "CREATE TABLE prekeys (" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "account TEXT NOT NULL," +
                    "key_id INTEGER NOT NULL UNIQUE," +
                    "record BLOB NOT NULL);";

    private static final String CREATE_SIGNED_PREKEYS_STATEMENT =
            "CREATE TABLE signed_prekeys (" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "account TEXT NOT NULL," +
                    "key_id INTEGER NOT NULL UNIQUE," +
                    "record BLOB NOT NULL);";

    private static final String CREATE_IDENTITIES_STATEMENT =
            "CREATE TABLE identities (" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "account TEXT NOT NULL," +
                    "name TEXT NOT NULL," +
                    "fingerprint TEXT NOT NULL," +
                    "key TEXT NOT NULL," +
                    "trusted INTEGER NOT NULL," +
                    "own INTEGER NOT NULL);";

    private static final String TAG = AxolotlStore.class.getSimpleName();

    public AxolotlStore(XmppConnectionService service) {
        super(service, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d(Config.LOGTAG, AxolotlService.LOGPREFIX+" : "+">>> CREATING AXOLOTL DATABASE <<<");
        recreateAxolotlDb(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        recreateAxolotlDb(db);
    }

    private Cursor getPreKeyCursor(Account account, int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {account.getUuid(), Integer.toString(id)};
        return db.query(SQLiteAxolotlStore.PREKEY_TABLENAME,
                new String[]{"record"},
                "account=? AND key_id=?",
                args, null, null, null);
    }

    public PreKeyRecord loadPreKey(Account account, int id) throws InvalidKeyIdException {
        Cursor cursor = getPreKeyCursor(account, id);
        if (!cursor.moveToFirst()) {
            throw new InvalidKeyIdException("No such prekey record: " + id);
        }
        byte[] serialized = cursor.getBlob(0);

        cursor.close();

        try {
            return new PreKeyRecord(serialized);
        } catch (InvalidKeyException e) {
            throw new AssertionError(e);
        }
    }

    public void storePreKey(Account account, int id, PreKeyRecord record) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(SQLiteAxolotlStore.ACCOUNT, account.getUuid());
        values.put(SQLiteAxolotlStore.KEY_ID, id);
        values.put(SQLiteAxolotlStore.RECORD, record.serialize());

        db.insert(SQLiteAxolotlStore.PREKEY_TABLENAME, null, values);
    }

    public boolean containsPreKey(Account account, int id) {
        Cursor cursor = getPreKeyCursor(account, id);
        boolean z = cursor.getCount() > 0;
        cursor.close();
        return z;
    }

    public void removePreKey(Account account, int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] args = {account.getUuid(), Integer.toString(id)};
        db.delete(SQLiteAxolotlStore.PREKEY_TABLENAME,
                "account=? AND key_id=?",
                args);
    }

    private Cursor getSignedPreKeyCursor(Account account, int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {account.getUuid(), Integer.toString(id)};
        return db.query(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME,
                new String[]{"record"},
                "account=? AND key_id=?",
                args, null, null, null);
    }

    public SignedPreKeyRecord loadSignedPreKey(Account account, int id) throws InvalidKeyIdException {
        Cursor cursor = getSignedPreKeyCursor(account, id);
        if (!cursor.moveToFirst()) {
            throw new InvalidKeyIdException("No such signed prekey record: " + id);
        }
        byte[] serialized = cursor.getBlob(0);

        cursor.close();

        try {
            return new SignedPreKeyRecord(serialized);
        } catch (InvalidKeyException e) {
            throw new AssertionError(e);
        }
    }

    public void storeSignedPreKey(Account account, int id, SignedPreKeyRecord record) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(SQLiteAxolotlStore.ACCOUNT, account.getUuid());
        values.put(SQLiteAxolotlStore.KEY_ID, id);
        values.put(SQLiteAxolotlStore.RECORD, record.serialize());

        db.insert(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME, null, values);
    }

    public boolean containsSignedPreKey(Account account, int id) {
        Cursor cursor = getSignedPreKeyCursor(account, id);
        boolean z = cursor.getCount() > 0;
        cursor.close();
        return z;
    }

    public void removeSignedPreKey(Account account, int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] args = {account.getUuid(), Integer.toString(id)};
        db.delete(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME,
                "account=? AND key_id=?",
                args);
    }

    private Cursor getSessionCursor(Account account, String name, int deviceId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {account.getUuid(), name, Integer.toString(deviceId)};
        return db.query(SQLiteAxolotlStore.SESSION_TABLENAME,
                new String[]{"record"},
                "account=? AND name=? AND device_id=?",
                args, null, null, null);
    }

    public SessionRecord loadSession(Account account, String name, int deviceId) {
        Cursor cursor = getSessionCursor(account, name, deviceId);
        if (!cursor.moveToFirst()) {
            return new SessionRecord();
        }
        byte[] serialized = cursor.getBlob(0);

        cursor.close();

        try {
            return new SessionRecord(serialized);
        } catch (InvalidMessageException e) {
            Log.d(TAG, "Failed to load session record for: " + name + "/" + deviceId, e);
            removeSession(account, name, deviceId);
            return new SessionRecord();
        }
    }

    public void storeSession(Account account, String name, int deviceId, SessionRecord record) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(SQLiteAxolotlStore.ACCOUNT, account.getUuid());
        values.put(SQLiteAxolotlStore.NAME, name);
        values.put(SQLiteAxolotlStore.DEVICE_ID, deviceId);
        values.put(SQLiteAxolotlStore.RECORD, record.serialize());

        db.insertWithOnConflict(SQLiteAxolotlStore.SESSION_TABLENAME, null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    public boolean containsSession(Account account, String name, int deviceId) {
        Cursor cursor = getSessionCursor(account, name, deviceId);
        boolean z = cursor.getCount() > 0;
        cursor.close();
        return z;
    }

    public void deleteSession(Account account, String name, int deviceId) {
        removeSession(account, name, deviceId);
    }

    private void removeSession(Account account, String name, int deviceId) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] args = {account.getUuid(), name, Integer.toString(deviceId)};
        db.delete(SQLiteAxolotlStore.SESSION_TABLENAME,
                "account=? AND name=? AND device_id=?",
                args);
    }

    private Cursor getIdentityKeyCursor(Account account, String name) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {account.getUuid(), name};
        return db.query(SQLiteAxolotlStore.IDENTITIES_TABLENAME,
                new String[]{"trusted", "key"},
                "account=? AND name=?",
                args, null, null, null);
    }

    private Cursor getIdentityKeyCursor(Account account) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {account.getUuid()};
        return db.query(SQLiteAxolotlStore.IDENTITIES_TABLENAME,
                new String[]{"trusted", "key"},
                "account=?",
                args, null, null, null);
    }

    public void saveIdentity(Account account, String name, IdentityKey identityKey) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(SQLiteAxolotlStore.ACCOUNT, account.getUuid());
        values.put(SQLiteAxolotlStore.NAME, name);
        values.put(SQLiteAxolotlStore.FINGERPRINT, Base64.encodeToString(identityKey.getFingerprintSha256().serialize(), Base64.DEFAULT));
        values.put(SQLiteAxolotlStore.KEY, Base64.encodeToString(identityKey.serialize(), Base64.DEFAULT));
        values.put(SQLiteAxolotlStore.TRUSTED, 0);
        values.put(SQLiteAxolotlStore.OWN, 0);

        db.insertWithOnConflict(SQLiteAxolotlStore.IDENTITIES_TABLENAME, null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    public IdentityKey getIdentity(Account account, String name) {
        Cursor cursor = getIdentityKeyCursor(account, name);
        if (cursor.moveToFirst()) {
            try {
                byte[] serialized = Base64.decode(cursor.getString(1), Base64.DEFAULT);
                return new IdentityKey(serialized, 0);
            } catch (InvalidKeyException e) {
                Log.d(TAG, "Failed to load identity key for: " + name, e);
            }
        }

        cursor.close();

        return null;
    }

    public Set<IdentityKey> getIdentities(Account account) {
        Cursor cursor = getIdentityKeyCursor(account);
        Set<IdentityKey> identities = new HashSet<>();
        if (cursor.moveToFirst()) {
            do {
                try {
                    byte[] serialized = Base64.decode(cursor.getString(1), Base64.DEFAULT);
                    IdentityKey identity = new IdentityKey(serialized, 0);
                    identities.add(identity);
                } catch (InvalidKeyException e) {
                    Log.d(TAG, "Failed to load identity key for account: " + account.getJid().asBareJid(), e);
                }
            } while (cursor.moveToNext());
        }

        cursor.close();
        return identities;
    }

    public boolean isTrustedIdentity(Account account, String name, IdentityKey identityKey) {
        Cursor cursor = getIdentityKeyCursor(account, name);

        if (!cursor.moveToFirst()) {
            cursor.close();
            saveIdentity(account, name, identityKey);
            return true;
        } else {
            byte[] fingerprintSerialized = Base64.decode(cursor.getString(0), Base64.DEFAULT);
            try {
                boolean z = new Fingerprint(fingerprintSerialized).compareTo(identityKey) == 0;
                cursor.close();
                return z;
            } catch (InvalidFingerprintIdentifier e) {
                Log.d(TAG, "Failed to compare identity key for: " + name, e);
            }
        }

        cursor.close();

        return false;
    }

    private Cursor getPreKeyCursor(Account account) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {account.getUuid()};
        return db.query(SQLiteAxolotlStore.PREKEY_TABLENAME,
                new String[]{"key_id"},
                "account=?",
                args, null, null, null);
    }

    public void deleteAllPreKeys(Account account) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] args = {account.getUuid()};
        db.delete(SQLiteAxolotlStore.PREKEY_TABLENAME,
                "account=?",
                args);
    }

    private Cursor getSignedPreKeyCursor(Account account) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {account.getUuid()};
        return db.query(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME,
                new String[]{"key_id"},
                "account=?",
                args, null, null, null);
    }

    public void deleteAllSignedPreKeys(Account account) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] args = {account.getUuid()};
        db.delete(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME,
                "account=?",
                args);
    }

    private Cursor getSessionCursor(Account account, String name) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {account.getUuid(), name};
        return db.query(SQLiteAxolotlStore.SESSION_TABLENAME,
                new String[]{"device_id"},
                "account=? AND name=?",
                args, null, null, null);
    }

    public void deleteAllSessions(Account account, String name) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] args = {account.getUuid(), name};
        db.delete(SQLiteAxolotlStore.SESSION_TABLENAME,
                "account=? AND name=?",
                args);
    }

    private Cursor getIdentityKeyCursor(Account account, String name, boolean trusted) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {account.getUuid(), name, Integer.toString(trusted ? 1 : 0)};
        return db.query(SQLiteAxolotlStore.IDENTITIES_TABLENAME,
                new String[]{"_id"},
                "account=? AND name=? AND trusted=?",
                args, null, null, null);
    }

    public void deleteIdentity(Account account, String name) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] args = {account.getUuid(), name};
        db.delete(SQLiteAxolotlStore.IDENTITIES_TABLENAME,
                "account=? AND name=?",
                args);
    }

    private Cursor getIdentityKeyCursor(boolean trusted) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {Integer.toString(trusted ? 1 : 0)};
        return db.query(SQLiteAxolotlStore.IDENTITIES_TABLENAME,
                new String[]{"_id"},
                "trusted=?",
                args, null, null, null);
    }

    public void clearIdentities(boolean trusted) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] args = {Integer.toString(trusted ? 1 : 0)};
        db.delete(SQLiteAxolotlStore.IDENTITIES_TABLENAME,
                "trusted=?",
                args);
    }

    private Cursor getIdentityKeyCursor(Account account, boolean trusted) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {account.getUuid(), Integer.toString(trusted ? 1 : 0)};
        return db.query(SQLiteAxolotlStore.IDENTITIES_TABLENAME,
                new String[]{"_id"},
                "account=? AND trusted=?",
                args, null, null, null);
    }

    public void clearIdentities(Account account, boolean trusted) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] args = {account.getUuid(), Integer.toString(trusted ? 1 : 0)};
        db.delete(SQLiteAxolotlStore.IDENTITIES_TABLENAME,
                "account=? AND trusted=?",
                args);
    }

    private Cursor getIdentityKeyCursor(Account account, String name) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {account.getUuid(), name};
        return db.query(SQLiteAxolotlStore.IDENTITIES_TABLENAME,
                new String[]{"trusted", "key"},
                "account=? AND name=?",
                args, null, null, null);
    }

    private Cursor getIdentityKeyCursor(Account account) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {account.getUuid()};
        return db.query(SQLiteAxolotlStore.IDENTITIES_TABLENAME,
                new String[]{"trusted", "key"},
                "account=?",
                args, null, null, null);
    }

    private Cursor getPreKeyCursor(Account account) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {account.getUuid()};
        return db.query(SQLiteAxolotlStore.PREKEY_TABLENAME,
                new String[]{"key_id"},
                "account=?",
                args, null, null, null);
    }

    private Cursor getSignedPreKeyCursor(Account account) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {account.getUuid()};
        return db.query(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME,
                new String[]{"key_id"},
                "account=?",
                args, null, null, null);
    }

    private Cursor getSessionCursor(Account account, String name) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {account.getUuid(), name};
        return db.query(SQLiteAxolotlStore.SESSION_TABLENAME,
                new String[]{"device_id"},
                "account=? AND name=?",
                args, null, null, null);
    }

    private Cursor getIdentityKeyCursor(Account account, String name, boolean trusted) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {account.getUuid(), name, Integer.toString(trusted ? 1 : 0)};
        return db.query(SQLiteAxolotlStore.IDENTITIES_TABLENAME,
                new String[]{"_id"},
                "account=? AND name=? AND trusted=?",
                args, null, null, null);
    }

    private Cursor getIdentityKeyCursor(boolean trusted) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {Integer.toString(trusted ? 1 : 0)};
        return db.query(SQLiteAxolotlStore.IDENTITIES_TABLENAME,
                new String[]{"_id"},
                "trusted=?",
                args, null, null, null);
    }

    private Cursor getIdentityKeyCursor(Account account, boolean trusted) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {account.getUuid(), Integer.toString(trusted ? 1 : 0)};
        return db.query(SQLiteAxolotlStore.IDENTITIES_TABLENAME,
                new String[]{"_id"},
                "account=? AND trusted=?",
                args, null, null, null);
    }

    private Cursor getIdentityKeyCursor(Account account) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {account.getUuid()};
        return db.query(SQLiteAxolotlStore.IDENTITIES_TABLENAME,
                new String[]{"trusted", "key"},
                "account=?",
                args, null, null, null);
    }

    private Cursor getIdentityKeyCursor(Account account, String name) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {account.getUuid(), name};
        return db.query(SQLiteAxolotlStore.IDENTITIES_TABLENAME,
                new String[]{"trusted", "key"},
                "account=? AND name=?",
                args, null, null, null);
    }

    private Cursor getPreKeyCursor(Account account) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {account.getUuid()};
        return db.query(SQLiteAxolotlStore.PREKEY_TABLENAME,
                new String[]{"key_id"},
                "account=?",
                args, null, null, null);
    }

    private Cursor getSignedPreKeyCursor(Account account) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {account.getUuid()};
        return db.query(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME,
                new String[]{"key_id"},
                "account=?",
                args, null, null, null);
    }

    private Cursor getSessionCursor(Account account, String name) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {account.getUuid(), name};
        return db.query(SQLiteAxolotlStore.SESSION_TABLENAME,
                new String[]{"device_id"},
                "account=? AND name=?",
                args, null, null, null);
    }

    private Cursor getIdentityKeyCursor(Account account, String name, boolean trusted) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {account.getUuid(), name, Integer.toString(trusted ? 1 : 0)};
        return db.query(SQLiteAxolotlStore.IDENTITIES_TABLENAME,
                new String[]{"_id"},
                "account=? AND name=? AND trusted=?",
                args, null, null, null);
    }

    private Cursor getIdentityKeyCursor(boolean trusted) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {Integer.toString(trusted ? 1 : 0)};
        return db.query(SQLiteAxolotlStore.IDENTITIES_TABLENAME,
                new String[]{"_id"},
                "trusted=?",
                args, null, null, null);
    }

    private Cursor getIdentityKeyCursor(Account account, boolean trusted) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {account.getUuid(), Integer.toString(trusted ? 1 : 0)};
        return db.query(SQLiteAxolotlStore.IDENTITIES_TABLENAME,
                new String[]{"_id"},
                "account=? AND trusted=?",
                args, null, null, null);
    }

    private Cursor getPreKeyCursor(Account account) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {account.getUuid()};
        return db.query(SQLiteAxolotlStore.PREKEY_TABLENAME,
                new String[]{"key_id"},
                "account=?",
                args, null, null, null);
    }

    private Cursor getSignedPreKeyCursor(Account account) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {account.getUuid()};
        return db.query(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME,
                new String[]{"key_id"},
                "account=?",
                args, null, null, null);
    }

    private Cursor getSessionCursor(Account account, String name) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {account.getUuid(), name};
        return db.query(SQLiteAxolotlStore.SESSION_TABLENAME,
                new String[]{"device_id"},
                "account=? AND name=?",
                args, null, null, null);
    }

    private Cursor getIdentityKeyCursor(Account account) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {account.getUuid()};
        return db.query(SQLiteAxolotlStore.IDENTITIES_TABLENAME,
                new String[]{"trusted", "key"},
                "account=?",
                args, null, null, null);
    }

    private Cursor getIdentityKeyCursor(Account account, String name) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {account.getUuid(), name};
        return db.query(SQLiteAxolotlStore.IDENTITIES_TABLENAME,
                new String[]{"trusted", "key"},
                "account=? AND name=?",
                args, null, null, null);
    }

    public void recreateDb() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DROP TABLE IF EXISTS " + SQLiteAxolotlStore.PREKEY_TABLENAME);
        db.execSQL("DROP TABLE IF EXISTS " + SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME);
        db.execSQL("DROP TABLE IF EXISTS " + SQLiteAxolotlStore.SESSION_TABLENAME);
        db.execSQL("DROP TABLE IF EXISTS " + SQLiteAxolotlStore.IDENTITIES_TABLENAME);

        onCreate(db);
    }

    private static class DatabaseHelper extends SQLiteOpenHelper {

        public DatabaseHelper(Context context, String name) {
            super(context, name, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(SQL_CREATE_PREKEY_TABLE);
            db.execSQL(SQL_CREATE_SIGNEDPREKEY_TABLE);
            db.execSQL(SQL_CREATE_SESSION_TABLE);
            db.execSQL(SQL_CREATE_IDENTITIES_TABLE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // This database is only a cache for online data, so its upgrade policy is to simply
            // to discard the data and start over
            db.execSQL("DROP TABLE IF EXISTS " + SQLiteAxolotlStore.PREKEY_TABLENAME);
            db.execSQL("DROP TABLE IF EXISTS " + SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME);
            db.execSQL("DROP TABLE IF EXISTS " + SQLiteAxolotlStore.SESSION_TABLENAME);
            db.execSQL("DROP TABLE IF EXISTS " + SQLiteAxolotlStore.IDENTITIES_TABLENAME);

            onCreate(db);
        }
    }

    public Cursor getSessions(Account account) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {account.getUuid()};
        return db.query(SQLiteAxolotlStore.SESSION_TABLENAME,
                new String[]{"name", "device_id"},
                "account=?",
                args, null, null, null);
    }
}
<|im_start|>
<|im_start|><|im_start|>The provided code snippet appears to be a part of an implementation for managing cryptographic key and session data in an Android application using SQLite. This is specifically related to the Signal Protocol, which is used for end-to-end encryption in messaging applications.

Here are some key points about the code:

1. **Database Operations**:
   - The class extends `SQLiteOpenHelper` to manage database creation and version management.
   - Methods like `onCreate` and `onUpgrade` are overridden to handle initial table creation and schema upgrades, respectively.

2. **Tables Managed**:
   - **PreKey Table**: Stores pre-keys used in the Signal Protocol for key exchange.
   - **Signed PreKey Table**: Stores signed pre-keys which provide authentication during the key exchange process.
   - **Session Table**: Contains session information necessary to maintain encrypted communications with peers.
   - **Identities Table**: Holds identity keys and their trust status, ensuring that messages are only decrypted from trusted sources.

3. **CRUD Operations**:
   - **Create**: Methods like `saveIdentity`, `storePreKey`, etc., add new entries to the respective tables.
   - **Read**: Methods like `getIdentity`, `loadSession`, etc., retrieve data from the database.
   - **Update**: Not explicitly shown, but methods like `saveIdentity` could be considered updates if an existing identity is modified.
   - **Delete**: Methods such as `deleteAllPreKeys`, `clearIdentities`, etc., remove entries from the tables.

4. **Utility Methods**:
   - `recreateDb()`: Drops all tables and re-creates them, effectively clearing all stored data. This might be useful for resetting encryption states.
   - `getSessions(Account account)`: Fetches all sessions associated with a particular user account.

5. **Error Handling**:
   - The code includes basic error handling through try-catch blocks to manage exceptions that may arise from operations like deserializing keys or comparing fingerprints.

6. **Security Considerations**:
   - The use of Base64 encoding for storing serialized data ensures that binary key material is stored safely in a text format within the database.
   - Trust management (`isTrustedIdentity`) is crucial to maintaining secure communications by verifying identities.

### Example Usage