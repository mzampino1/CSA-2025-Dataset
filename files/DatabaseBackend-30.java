import android.content.ContentValues;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

public class AxolotlOpenHelper extends SQLiteOpenHelper {

    // Potential vulnerability: Ensure that the database version is updated correctly to handle schema changes.
    private static final int DATABASE_VERSION = 23;
    private static final String DATABASE_NAME = "axolotl.db";

    public AxolotlOpenHelper() {
        super(XmppActivity.getContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        recreateAxolotlDb(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 23) {
            recreateAxolotlDb(db);
        }
    }

    private static final String CREATE_SESSIONS_STATEMENT =
            "CREATE TABLE sessions (" +
                    "account TEXT NOT NULL," +
                    "recipient_id INTEGER NOT NULL," +
                    "remote_registration_id INTEGER NOT NULL," +
                    "local_registration_id INTEGER NOT NULL," +
                    "session BLOB NOT NULL," + // Potential vulnerability: Ensure that session data is properly serialized and deserialized.
                    "PRIMARY KEY (account, recipient_id));";

    private static final String CREATE_PREKEYS_STATEMENT =
            "CREATE TABLE prekeys (" +
                    "account TEXT NOT NULL," +
                    "prekey_id INTEGER NOT NULL," +
                    "public_key BLOB NOT NULL," +
                    "private_key BLOB NOT NULL," + // Potential vulnerability: Ensure that private keys are stored securely.
                    "PRIMARY KEY (account, prekey_id));";

    private static final String CREATE_SIGNED_PREKEYS_STATEMENT =
            "CREATE TABLE signed_prekeys (" +
                    "account TEXT NOT NULL," +
                    "signed_prekey_id INTEGER NOT NULL," +
                    "public_key BLOB NOT NULL," +
                    "private_key BLOB NOT NULL," + // Potential vulnerability: Ensure that private keys are stored securely.
                    "signature BLOB NOT NULL," +
                    "PRIMARY KEY (account, signed_prekey_id));";

    private static final String CREATE_IDENTITIES_STATEMENT =
            "CREATE TABLE identities (" +
                    "account TEXT NOT NULL," +
                    "name TEXT NOT NULL," + // Potential vulnerability: Ensure that names are sanitized to prevent SQL injection.
                    "own INTEGER NOT NULL," +
                    "fingerprint TEXT NOT NULL," +
                    "key TEXT NOT NULL," + // Potential vulnerability: Ensure that keys are stored securely.
                    "trust INTEGER NOT NULL DEFAULT 0," + // Potential vulnerability: Ensure trust values are validated.
                    "active INTEGER NOT NULL DEFAULT 0," +
                    "compromised INTEGER NOT NULL DEFAULT 0," +
                    "compromised_on BIGINT NOT NULL DEFAULT 0," +
                    "PRIMARY KEY (account, name, fingerprint));";

    private static final String SQL_GET_SESSIONS = 
            "SELECT session FROM sessions WHERE account=? AND recipient_id=? LIMIT 1"; // Potential vulnerability: Ensure that input parameters are properly sanitized.

    public byte[] getSession(Account account, int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        byte[] data;
        try (Cursor cursor = db.rawQuery(SQL_GET_SESSION, new String[]{account.getUuid(), Integer.toString(id)})) { // Potential vulnerability: Use parameterized queries to prevent SQL injection.
            if (cursor.moveToFirst()) {
                data = cursor.getBlob(0);
            } else {
                data = null;
            }
        }
        return data;
    }

    private static final String SQL_SET_SESSION =
            "INSERT INTO sessions(account, recipient_id, remote_registration_id, local_registration_id, session) VALUES (?, ?, ?, ?, ?)" + // Potential vulnerability: Ensure that input parameters are properly sanitized.
                    "ON CONFLICT(account,recipient_id) DO UPDATE SET remote_registration_id=?,local_registration_id=?,session=?";

    public boolean putSession(Account account, int id, SessionRecord record) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        byte[] serialized = record.serialize(); // Potential vulnerability: Ensure that session data is properly serialized and deserialized.
        values.put("account", account.getUuid());
        values.put("recipient_id", id);
        values.put("remote_registration_id", record.getRemoteRegistrationId());
        values.put("local_registration_id", record.getLocalRegistrationId());
        values.put("session", serialized);

        return db.insertWithOnConflict(SQLiteAxolotlStore.SESSION_TABLENAME, null, values,
                SQLiteDatabase.CONFLICT_REPLACE) > -1;
    }

    public void deleteSession(Account account, int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] args = new String[]{account.getUuid(), Integer.toString(id)};
        db.delete(SQLiteAxolotlStore.SESSION_TABLENAME, "account=? AND recipient_id=?", args);
    }

    private static final String SQL_GET_ALL_SESSIONS =
            "SELECT recipient_id FROM sessions WHERE account=?";

    public List<Integer> getSubDeviceSessions(Account account) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(SQL_GET_ALL_SESSIONS, new String[]{account.getUuid()});
        ArrayList<Integer> result = new ArrayList<>();
        while (cursor.moveToNext()) {
            result.add(cursor.getInt(0));
        }
        return result;
    }

    public void deleteAllSessions(Account account) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(SQLiteAxolotlStore.SESSION_TABLENAME, "account=?", new String[]{account.getUuid()});
    }

    private static final String SQL_GET_PREKEY =
            "SELECT public_key, private_key FROM prekeys WHERE account=? AND prekey_id=? LIMIT 1"; // Potential vulnerability: Ensure that input parameters are properly sanitized.

    public PreKeyRecord getPreKey(Account account, int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery(SQL_GET_PREKEY, new String[]{account.getUuid(), Integer.toString(id)})) { // Potential vulnerability: Use parameterized queries to prevent SQL injection.
            if (!cursor.moveToFirst()) {
                return null;
            }
            byte[] publicKey = cursor.getBlob(0);
            byte[] privateKey = cursor.getBlob(1);
            return new PreKeyRecord(new SignalProtocolAddress(account.getUuid(), id), id, Curve.decodePoint(publicKey, 0), Curve.decodePrivatePoint(privateKey));
        } catch (InvalidKeyIdException | InvalidKeyException e) {
            return null;
        }
    }

    private static final String SQL_GET_ALL_PREKEYS =
            "SELECT prekey_id FROM prekeys WHERE account=?";

    public List<Integer> getAllPreKeys(Account account) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(SQL_GET_ALL_PREKEYS, new String[]{account.getUuid()});
        ArrayList<Integer> result = new ArrayList<>();
        while (cursor.moveToNext()) {
            result.add(cursor.getInt(0));
        }
        return result;
    }

    private static final String SQL_SET_PREKEY =
            "INSERT INTO prekeys(account, prekey_id, public_key, private_key) VALUES (?, ?, ?, ?)" + // Potential vulnerability: Ensure that input parameters are properly sanitized.
                    "ON CONFLICT(account,prekey_id) DO UPDATE SET public_key=?,private_key=?";

    public boolean storePreKey(Account account, int id, PreKeyRecord record) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        ECPublicKey publicKey = record.getKeyPair().getPublicKey();
        ECPrivateKey privateKey = record.getKeyPair().getPrivateKey();

        byte[] publicKeyBytes = Curve.encodePoint(publicKey);
        byte[] privateKeyBytes = Curve.encodePrivatePoint(privateKey);

        values.put("account", account.getUuid());
        values.put("prekey_id", id);
        values.put("public_key", publicKeyBytes);
        values.put("private_key", privateKeyBytes);

        return db.insertWithOnConflict(SQLiteAxolotlStore.PREKEY_TABLENAME, null, values,
                SQLiteDatabase.CONFLICT_REPLACE) > -1;
    }

    public boolean removePreKey(Account account, int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] args = new String[]{account.getUuid(), Integer.toString(id)};
        return db.delete(SQLiteAxolotlStore.PREKEY_TABLENAME, "account=? AND prekey_id=?", args) > 0;
    }

    private static final String SQL_GET_SIGNED_PREKEY =
            "SELECT public_key, private_key, signature FROM signed_prekeys WHERE account=? AND signed_prekey_id=? LIMIT 1"; // Potential vulnerability: Ensure that input parameters are properly sanitized.

    public SignedPreKeyRecord getSignedPreKey(Account account, int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery(SQL_GET_SIGNED_PREKEY, new String[]{account.getUuid(), Integer.toString(id)})) { // Potential vulnerability: Use parameterized queries to prevent SQL injection.
            if (!cursor.moveToFirst()) {
                return null;
            }
            byte[] publicKey = cursor.getBlob(0);
            byte[] privateKey = cursor.getBlob(1);
            byte[] signature = cursor.getBlob(2);

            return new SignedPreKeyRecord(id, record.getKeyPair().getPublicKey(), record.getKeyPair().getPrivateKey(), record.getSignature());
        } catch (InvalidKeyIdException | InvalidKeyException e) {
            return null;
        }
    }

    private static final String SQL_GET_ALL_SIGNED_PREKEYS =
            "SELECT signed_prekey_id FROM signed_prekeys WHERE account=?";

    public List<Integer> getAllSignedPreKeys(Account account) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(SQL_GET_ALL_SIGNED_PREKEYS, new String[]{account.getUuid()});
        ArrayList<Integer> result = new ArrayList<>();
        while (cursor.moveToNext()) {
            result.add(cursor.getInt(0));
        }
        return result;
    }

    private static final String SQL_SET_SIGNED_PREKEY =
            "INSERT INTO signed_prekeys(account, signed_prekey_id, public_key, private_key, signature) VALUES (?, ?, ?, ?, ?)" + // Potential vulnerability: Ensure that input parameters are properly sanitized.
                    "ON CONFLICT(account,signed_prekey_id) DO UPDATE SET public_key=?,private_key=?,signature=?";

    public boolean storeSignedPreKey(Account account, int id, SignedPreKeyRecord record) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        ECPublicKey publicKey = record.getKeyPair().getPublicKey();
        ECPrivateKey privateKey = record.getKeyPair().getPrivateKey();

        byte[] publicKeyBytes = Curve.encodePoint(publicKey);
        byte[] privateKeyBytes = Curve.encodePrivatePoint(privateKey);

        values.put("account", account.getUuid());
        values.put("signed_prekey_id", id);
        values.put("public_key", publicKeyBytes);
        values.put("private_key", privateKeyBytes);
        values.put("signature", record.getSignature());

        return db.insertWithOnConflict(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME, null, values,
                SQLiteDatabase.CONFLICT_REPLACE) > -1;
    }

    public boolean removeSignedPreKey(Account account, int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] args = new String[]{account.getUuid(), Integer.toString(id)};
        return db.delete(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME, "account=? AND signed_prekey_id=?", args) > 0;
    }

    public void clearSignedPreKey(Account account) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME, "account=?", new String[]{account.getUuid()});
    }

    private static final String SQL_GET_IDENTITY =
            "SELECT key FROM identities WHERE account=? AND name=?" +
                    "AND (trust=? OR trust=? OR trust=?) LIMIT 1"; // Potential vulnerability: Ensure that input parameters are properly sanitized.

    public IdentityKeyRecord getIdentity(Account account, String name) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery(SQL_GET_IDENTITY, new String[]{account.getUuid(), name,
                Integer.toString(TrustState.TRUSTED), Integer.toString(TrustState.UNTRUSTED_UNKNOWN),
                Integer.toString(TrustState.UNVERIFIED)})) { // Potential vulnerability: Use parameterized queries to prevent SQL injection.
            if (!cursor.moveToFirst()) {
                return null;
            }

            IdentityKey identityKey = new IdentityKey(Curve.decodePoint(Base64.decode(cursor.getString(0)), 0));
            boolean trusted = (cursor.getInt(1) == TrustState.TRUSTED);
            return new IdentityKeyRecord(identityKey, trusted);
        } catch (InvalidKeyException | Base64DecoderException e) {
            return null;
        }
    }

    public void saveIdentity(Account account, String name, IdentityKey identityKey) throws InvalidIdentityKeyException {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        byte[] keyBytes = Curve.encodePoint(identityKey.getPublicKey());

        values.put("account", account.getUuid());
        values.put("name", name);
        values.put("own", 0);
        values.put("fingerprint", FingerprintGenerator.createFor(account, identityKey));
        values.put("key", Base64.encodeBytes(keyBytes));

        long id = db.insertWithOnConflict(SQLiteAxolotlStore.IDENTITIES_TABLENAME, null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
        if (id == -1) {
            throw new InvalidIdentityKeyException();
        }
    }

    private static final String SQL_GET_ALL_IDENTITIES =
            "SELECT name FROM identities WHERE account=? AND trust<>?"; // Potential vulnerability: Ensure that input parameters are properly sanitized.

    public List<String> getAllIdentities(Account account) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(SQL_GET_ALL_IDENTITIES, new String[]{account.getUuid(), Integer.toString(TrustState.UNTRUSTED_VERIFIED)});
        ArrayList<String> result = new ArrayList<>();
        while (cursor.moveToNext()) {
            result.add(cursor.getString(0));
        }
        return result;
    }

    private static final String SQL_SET_TRUST =
            "UPDATE identities SET trust=?, active=? WHERE account=? AND name=?";

    public void setIdentityTrust(Account account, String name, TrustState state) throws InvalidIdentityKeyException {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put("trust", Integer.valueOf(state.toInt()));
        values.put("active", state.isActive() ? 1 : 0);

        int rowsUpdated = db.update(SQLiteAxolotlStore.IDENTITIES_TABLENAME, values,
                "account=? AND name=?", new String[]{account.getUuid(), name});

        if (rowsUpdated == 0) {
            throw new InvalidIdentityKeyException();
        }
    }

    public void deleteIdentity(Account account, String name) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(SQLiteAxolotlStore.IDENTITIES_TABLENAME, "account=? AND name=?", new String[]{account.getUuid(), name});
    }

    private static final String SQL_GET_IDENTITY_BY_FINGERPRINT =
            "SELECT key FROM identities WHERE account=? AND fingerprint=? LIMIT 1"; // Potential vulnerability: Ensure that input parameters are properly sanitized.

    public IdentityKeyRecord getIdentityByFingerprint(Account account, String fingerprint) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery(SQL_GET_IDENTITY_BY_FINGERPRINT, new String[]{account.getUuid(), fingerprint})) { // Potential vulnerability: Use parameterized queries to prevent SQL injection.
            if (!cursor.moveToFirst()) {
                return null;
            }

            IdentityKey identityKey = new IdentityKey(Curve.decodePoint(Base64.decode(cursor.getString(0)), 0));
            boolean trusted = (cursor.getInt(1) == TrustState.TRUSTED);
            return new IdentityKeyRecord(identityKey, trusted);
        } catch (InvalidKeyException | Base64DecoderException e) {
            return null;
        }
    }

    public void deleteIdentityByFingerprint(Account account, String fingerprint) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(SQLiteAxolotlStore.IDENTITIES_TABLENAME, "account=? AND fingerprint=?", new String[]{account.getUuid(), fingerprint});
    }

    // Potential vulnerability: Ensure that the database version is updated correctly to handle schema changes.
    public void recreateAxolotlDb(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS sessions");
        db.execSQL("DROP TABLE IF EXISTS prekeys");
        db.execSQL("DROP TABLE IF EXISTS signed_prekeys");
        db.execSQL("DROP TABLE IF EXISTS identities");

        db.execSQL(CREATE_SESSIONS_STATEMENT);
        db.execSQL(CREATE_PREKEYS_STATEMENT);
        db.execSQL(CREATE_SIGNED_PREKEYS_STATEMENT);
        db.execSQL(CREATE_IDENTITIES_STATEMENT);
    }

    // Potential vulnerability: Ensure that frequent database operations do not lead to performance issues.
    public void clearDatabase() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(SQLiteAxolotlStore.SESSION_TABLENAME, null, null);
        db.delete(SQLiteAxolotlStore.PREKEY_TABLENAME, null, null);
        db.delete(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME, null, null);
        db.delete(SQLiteAxolotlStore.IDENTITIES_TABLENAME, null, null);
    }

    // Potential vulnerability: Ensure that the database is encrypted and keys are stored securely.
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        if (!db.isReadOnly()) {
            // Enable foreign key constraints
            db.execSQL("PRAGMA foreign_keys=1;");
        }
    }

    private static final String SQL_GET_COMPROMISED_IDENTITY =
            "SELECT name FROM identities WHERE account=? AND compromised_on>?"; // Potential vulnerability: Ensure that input parameters are properly sanitized.

    public List<String> getCompromisedIdentities(Account account, long olderThan) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(SQL_GET_COMPROMISED_IDENTITY, new String[]{account.getUuid(), Long.toString(olderThan)});
        ArrayList<String> result = new ArrayList<>();
        while (cursor.moveToNext()) {
            result.add(cursor.getString(0));
        }
        return result;
    }

    private static final String SQL_SET_COMPROMISED =
            "UPDATE identities SET compromised=?, compromised_on=? WHERE account=? AND name=?";

    public void setIdentityCompromised(Account account, String name, boolean compromised) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put("compromised", compromised ? 1 : 0);
        values.put("compromised_on", System.currentTimeMillis());

        db.update(SQLiteAxolotlStore.IDENTITIES_TABLENAME, values,
                "account=? AND name=?", new String[]{account.getUuid(), name});
    }

    // Potential vulnerability: Ensure that frequent database operations do not lead to performance issues.
    public void markAllIdentitiesCompromised(Account account) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put("compromised", 1);
        values.put("compromised_on", System.currentTimeMillis());

        db.update(SQLiteAxolotlStore.IDENTITIES_TABLENAME, values,
                "account=?", new String[]{account.getUuid()});
    }

    // Potential vulnerability: Ensure that the database is encrypted and keys are stored securely.
    public void clearCompromisedIdentities(Account account) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put("compromised", 0);
        values.put("compromised_on", 0);

        db.update(SQLiteAxolotlStore.IDENTITIES_TABLENAME, values,
                "account=?", new String[]{account.getUuid()});
    }

    private static final String SQL_GET_ALL_IDENTITY_FINGERPRINTS =
            "SELECT fingerprint FROM identities WHERE account=?"; // Potential vulnerability: Ensure that input parameters are properly sanitized.

    public List<String> getAllIdentityFingerprints(Account account) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(SQL_GET_ALL_IDENTITY_FINGERPRINTS, new String[]{account.getUuid()});
        ArrayList<String> result = new ArrayList<>();
        while (cursor.moveToNext()) {
            result.add(cursor.getString(0));
        }
        return result;
    }

    // Potential vulnerability: Ensure that the database is encrypted and keys are stored securely.
    public void deleteAllIdentities(Account account) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(SQLiteAxolotlStore.IDENTITIES_TABLENAME, "account=?", new String[]{account.getUuid()});
    }

    private static final String SQL_GET_IDENTITY_ACTIVE =
            "SELECT active FROM identities WHERE account=? AND name=?" +
                    "AND (trust=? OR trust=? OR trust=?) LIMIT 1"; // Potential vulnerability: Ensure that input parameters are properly sanitized.

    public boolean isIdentityActive(Account account, String name) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery(SQL_GET_IDENTITY_ACTIVE, new String[]{account.getUuid(), name,
                Integer.toString(TrustState.TRUSTED), Integer.toString(TrustState.UNTRUSTED_UNKNOWN),
                Integer.toString(TrustState.UNVERIFIED)})) { // Potential vulnerability: Use parameterized queries to prevent SQL injection.
            return cursor.moveToFirst() && cursor.getInt(0) == 1;
        }
    }

    private static final String SQL_GET_IDENTITY_TRUST =
            "SELECT trust FROM identities WHERE account=? AND name=?" +
                    "AND (trust=? OR trust=? OR trust=?) LIMIT 1"; // Potential vulnerability: Ensure that input parameters are properly sanitized.

    public int getIdentityTrust(Account account, String name) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery(SQL_GET_IDENTITY_TRUST, new String[]{account.getUuid(), name,
                Integer.toString(TrustState.TRUSTED), Integer.toString(TrustState.UNTRUSTED_UNKNOWN),
                Integer.toString(TrustState.UNVERIFIED)})) { // Potential vulnerability: Use parameterized queries to prevent SQL injection.
            return cursor.moveToFirst() ? cursor.getInt(0) : -1;
        }
    }

    private static final String SQL_GET_IDENTITY_COMPROMISED =
            "SELECT compromised FROM identities WHERE account=? AND name=?" +
                    "AND (trust=? OR trust=? OR trust=?) LIMIT 1"; // Potential vulnerability: Ensure that input parameters are properly sanitized.

    public boolean isIdentityCompromised(Account account, String name) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery(SQL_GET_IDENTITY_COMPROMISED, new String[]{account.getUuid(), name,
                Integer.toString(TrustState.TRUSTED), Integer.toString(TrustState.UNTRUSTED_UNKNOWN),
                Integer.toString(TrustState.UNVERIFIED)})) { // Potential vulnerability: Use parameterized queries to prevent SQL injection.
            return cursor.moveToFirst() && cursor.getInt(0) == 1;
        }
    }

    private static final String SQL_GET_IDENTITY_COMPROMISED_ON =
            "SELECT compromised_on FROM identities WHERE account=? AND name=?" +
                    "AND (trust=? OR trust=? OR trust=?) LIMIT 1"; // Potential vulnerability: Ensure that input parameters are properly sanitized.

    public long getIdentityCompromisedOn(Account account, String name) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery(SQL_GET_IDENTITY_COMPROMISED_ON, new String[]{account.getUuid(), name,
                Integer.toString(TrustState.TRUSTED), Integer.toString(TrustState.UNTRUSTED_UNKNOWN),
                Integer.toString(TrustState.UNVERIFIED)})) { // Potential vulnerability: Use parameterized queries to prevent SQL injection.
            return cursor.moveToFirst() ? cursor.getLong(0) : -1;
        }
    }

    private static final String SQL_GET_IDENTITY_FINGERPRINT =
            "SELECT fingerprint FROM identities WHERE account=? AND name=?" +
                    "AND (trust=? OR trust=? OR trust=?) LIMIT 1"; // Potential vulnerability: Ensure that input parameters are properly sanitized.

    public String getIdentityFingerprint(Account account, String name) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery(SQL_GET_IDENTITY_FINGERPRINT, new String[]{account.getUuid(), name,
                Integer.toString(TrustState.TRUSTED), Integer.toString(TrustState.UNTRUSTED_UNKNOWN),
                Integer.toString(TrustState.UNVERIFIED)})) { // Potential vulnerability: Use parameterized queries to prevent SQL injection.
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    private static final String SQL_GET_IDENTITY_KEY =
            "SELECT key FROM identities WHERE account=? AND name=?" +
                    "AND (trust=? OR trust=? OR trust=?) LIMIT 1"; // Potential vulnerability: Ensure that input parameters are properly sanitized.

    public byte[] getIdentityKey(Account account, String name) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery(SQL_GET_IDENTITY_KEY, new String[]{account.getUuid(), name,
                Integer.toString(TrustState.TRUSTED), Integer.toString(TrustState.UNTRUSTED_UNKNOWN),
                Integer.toString(TrustState.UNVERIFIED)})) { // Potential vulnerability: Use parameterized queries to prevent SQL injection.
            return cursor.moveToFirst() ? Base64.decode(cursor.getString(0)) : null;
        } catch (Base64DecoderException e) {
            return null;
        }
    }

    private static final String SQL_GET_IDENTITY_BY_FINGERPRINT_KEY =
            "SELECT key FROM identities WHERE account=? AND fingerprint=?" +
                    "AND (trust=? OR trust=? OR trust=?) LIMIT 1"; // Potential vulnerability: Ensure that input parameters are properly sanitized.

    public byte[] getIdentityKeyByFingerprint(Account account, String fingerprint) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery(SQL_GET_IDENTITY_BY_FINGERPRINT_KEY, new String[]{account.getUuid(), fingerprint,
                Integer.toString(TrustState.TRUSTED), Integer.toString(TrustState.UNTRUSTED_UNKNOWN),
                Integer.toString(TrustState.UNVERIFIED)})) { // Potential vulnerability: Use parameterized queries to prevent SQL injection.
            return cursor.moveToFirst() ? Base64.decode(cursor.getString(0)) : null;
        } catch (Base64DecoderException e) {
            return null;
        }
    }

    private static final String SQL_GET_IDENTITY_BY_FINGERPRINT_TRUST =
            "SELECT trust FROM identities WHERE account=? AND fingerprint=?" +
                    "AND (trust=? OR trust=? OR trust=?) LIMIT 1"; // Potential vulnerability: Ensure that input parameters are properly sanitized.

    public int getIdentityTrustByFingerprint(Account account, String fingerprint) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery(SQL_GET_IDENTITY_BY_FINGERPRINT_TRUST, new String[]{account.getUuid(), fingerprint,
                Integer.toString(TrustState.TRUSTED), Integer.toString(TrustState.UNTRUSTED_UNKNOWN),
                Integer.toString(TrustState.UNVERIFIED)})) { // Potential vulnerability: Use parameterized queries to prevent SQL injection.
            return cursor.moveToFirst() ? cursor.getInt(0) : -1;
        }
    }

    private static final String SQL_GET_IDENTITY_BY_FINGERPRINT_COMPROMISED =
            "SELECT compromised FROM identities WHERE account=? AND fingerprint=?" +
                    "AND (trust=? OR trust=? OR trust=?) LIMIT 1"; // Potential vulnerability: Ensure that input parameters are properly sanitized.

    public boolean isIdentityCompromisedByFingerprint(Account account, String fingerprint) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery(SQL_GET_IDENTITY_BY_FINGERPRINT_COMPROMISED, new String[]{account.getUuid(), fingerprint,
                Integer.toString(TrustState.TRUSTED), Integer.toString(TrustState.UNTRUSTED_UNKNOWN),
                Integer.toString(TrustState.UNVERIFIED)})) { // Potential vulnerability: Use parameterized queries to prevent SQL injection.
            return cursor.moveToFirst() && cursor.getInt(0) == 1;
        }
    }

    private static final String SQL_GET_IDENTITY_BY_FINGERPRINT_COMPROMISED_ON =
            "SELECT compromised_on FROM identities WHERE account=? AND fingerprint=?" +
                    "AND (trust=? OR trust=? OR trust=?) LIMIT 1"; // Potential vulnerability: Ensure that input parameters are properly sanitized.

    public long getIdentityCompromisedOnByFingerprint(Account account, String fingerprint) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery(SQL_GET_IDENTITY_BY_FINGERPRINT_COMPROMISED_ON, new String[]{account.getUuid(), fingerprint,
                Integer.toString(TrustState.TRUSTED), Integer.toString(TrustState.UNTRUSTED_UNKNOWN),
                Integer.toString(TrustState.UNVERIFIED)})) { // Potential vulnerability: Use parameterized queries to prevent SQL injection.
            return cursor.moveToFirst() ? cursor.getLong(0) : -1;
        }
    }

    private static final String SQL_GET_IDENTITY_BY_KEY =
            "SELECT name FROM identities WHERE account=? AND key=?" +
                    "AND (trust=? OR trust=? OR trust=?) LIMIT 1"; // Potential vulnerability: Ensure that input parameters are properly sanitized.

    public String getIdentityNameByKey(Account account, byte[] key) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery(SQL_GET_IDENTITY_BY_KEY, new String[]{account.getUuid(), Base64.encodeBytes(key),
                Integer.toString(TrustState.TRUSTED), Integer.toString(TrustState.UNTRUSTED_UNKNOWN),
                Integer.toString(TrustState.UNVERIFIED)})) { // Potential vulnerability: Use parameterized queries to prevent SQL injection.
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    private static final String SQL_GET_IDENTITY_BY_KEY_TRUST =
            "SELECT trust FROM identities WHERE account=? AND key=?" +
                    "AND (trust=? OR trust=? OR trust=?) LIMIT 1"; // Potential vulnerability: Ensure that input parameters are properly sanitized.

    public int getIdentityTrustByKey(Account account, byte[] key) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery(SQL_GET_IDENTITY_BY_KEY_TRUST, new String[]{account.getUuid(), Base64.encodeBytes(key),
                Integer.toString(TrustState.TRUSTED), Integer.toString(TrustState.UNTRUSTED_UNKNOWN),
                Integer.toString(TrustState.UNVERIFIED)})) { // Potential vulnerability: Use parameterized queries to prevent SQL injection.
            return cursor.moveToFirst() ? cursor.getInt(0) : -1;
        }
    }

    private static final String SQL_GET_IDENTITY_BY_KEY_COMPROMISED =
            "SELECT compromised FROM identities WHERE account=? AND key=?" +
                    "AND (trust=? OR trust=? OR trust=?) LIMIT 1"; // Potential vulnerability: Ensure that input parameters are properly sanitized.

    public boolean isIdentityCompromisedByKey(Account account, byte[] key) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery(SQL_GET_IDENTITY_BY_KEY_COMPROMISED, new String[]{account.getUuid(), Base64.encodeBytes(key),
                Integer.toString(TrustState.TRUSTED), Integer.toString(TrustState.UNTRUSTED_UNKNOWN),
                Integer.toString(TrustState.UNVERIFIED)})) { // Potential vulnerability: Use parameterized queries to prevent SQL injection.
            return cursor.moveToFirst() && cursor.getInt(0) == 1;
        }
    }

    private static final String SQL_GET_IDENTITY_BY_KEY_COMPROMISED_ON =
            "SELECT compromised_on FROM identities WHERE account=? AND key=?" +
                    "AND (trust=? OR trust=? OR trust=?) LIMIT 1"; // Potential vulnerability: Ensure that input parameters are properly sanitized.

    public long getIdentityCompromisedOnByKey(Account account, byte[] key) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery(SQL_GET_IDENTITY_BY_KEY_COMPROMISED_ON, new String[]{account.getUuid(), Base64.encodeBytes(key),
                Integer.toString(TrustState.TRUSTED), Integer.toString(TrustState.UNTRUSTED_UNKNOWN),
                Integer.toString(TrustState.UNVERIFIED)})) { // Potential vulnerability: Use parameterized queries to prevent SQL injection.
            return cursor.moveToFirst() ? cursor.getLong(0) : -1;
        }
    }

    private static final String SQL_GET_IDENTITY_BY_KEY_ACTIVE =
            "SELECT active FROM identities WHERE account=? AND key=?" +
                    "AND (trust=? OR trust=? OR trust=?) LIMIT 1"; // Potential vulnerability: Ensure that input parameters are properly sanitized.

    public boolean isIdentityActiveByKey(Account account, byte[] key) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery(SQL_GET_IDENTITY_BY_KEY_ACTIVE, new String[]{account.getUuid(), Base64.encodeBytes(key),
                Integer.toString(TrustState.TRUSTED), Integer.toString(TrustState.UNTRUSTED_UNKNOWN),
                Integer.toString(TrustState.UNVERIFIED)})) { // Potential vulnerability: Use parameterized queries to prevent SQL injection.
            return cursor.moveToFirst() && cursor.getInt(0) == 1;
        }
    }

    private static final String SQL_GET_IDENTITY_BY_FINGERPRINT_ACTIVE =
            "SELECT active FROM identities WHERE account=? AND fingerprint=?" +
                    "AND (trust=? OR trust=? OR trust=?) LIMIT 1"; // Potential vulnerability: Ensure that input parameters are properly sanitized.

    public boolean isIdentityActiveByFingerprint(Account account, String fingerprint) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery(SQL_GET_IDENTITY_BY_FINGERPRINT_ACTIVE, new String[]{account.getUuid(), fingerprint,
                Integer.toString(TrustState.TRUSTED), Integer.toString(TrustState.UNTRUSTED_UNKNOWN),
                Integer.toString(TrustState.UNVERIFIED)})) { // Potential vulnerability: Use parameterized queries to prevent SQL injection.
            return cursor.moveToFirst() && cursor.getInt(0) == 1;
        }
    }

    private static final String SQL_GET_IDENTITY_BY_FINGERPRINT_NAME =
            "SELECT name FROM identities WHERE account=? AND fingerprint=?" +
                    "AND (trust=? OR trust=? OR trust=?) LIMIT 1"; // Potential vulnerability: Ensure that input parameters are properly sanitized.

    public String getIdentityNameByFingerprint(Account account, String fingerprint) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery(SQL_GET_IDENTITY_BY_FINGERPRINT_NAME, new String[]{account.getUuid(), fingerprint,
                Integer.toString(TrustState.TRUSTED), Integer.toString(TrustState.UNTRUSTED_UNKNOWN),
                Integer.toString(TrustState.UNVERIFIED)})) { // Potential vulnerability: Use parameterized queries to prevent SQL injection.
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    private static final String SQL_GET_IDENTITY_BY_NAME_FINGERPRINT =
            "SELECT fingerprint FROM identities WHERE account=? AND name=?" +
                    "AND (trust=? OR trust=? OR trust=?) LIMIT 1"; // Potential vulnerability: Ensure that input parameters are properly sanitized.

    public String getIdentityFingerprintByName(Account account, String name) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery(SQL_GET_IDENTITY_BY_NAME_FINGERPRINT, new String[]{account.getUuid(), name,
                Integer.toString(TrustState.TRUSTED), Integer.toString(TrustState.UNTRUSTED_UNKNOWN),
                Integer.toString(TrustState.UNVERIFIED)})) { // Potential vulnerability: Use parameterized queries to prevent SQL injection.
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    private static final String SQL_GET_IDENTITY_BY_NAME_KEY =
            "SELECT key FROM identities WHERE account=? AND name=?" +
                    "AND (trust=? OR trust=? OR trust=?) LIMIT 1"; // Potential vulnerability: Ensure that input parameters are properly sanitized.

    public byte[] getIdentityKeyByName(Account account, String name) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery(SQL_GET_IDENTITY_BY_NAME_KEY, new String[]{account.getUuid(), name,
                Integer.toString(TrustState.TRUSTED), Integer.toString(TrustState.UNTRUSTED_UNKNOWN),
                Integer.toString(TrustState.UNVERIFIED)})) { // Potential vulnerability: Use parameterized queries to prevent SQL injection.
            return cursor.moveToFirst() ? Base64.decode(cursor.getString(0)) : null;
        } catch (Base64DecoderException e) {
            return null;
        }
    }

    private static final String SQL_GET_IDENTITY_BY_NAME_TRUST =
            "SELECT trust FROM identities WHERE account=? AND name=?" +
                    "AND (trust=? OR trust=? OR trust=?) LIMIT 1"; // Potential vulnerability: Ensure that input parameters are properly sanitized.

    public int getIdentityTrustByName(Account account, String name) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery(SQL_GET_IDENTITY_BY_NAME_TRUST, new String[]{account.getUuid(), name,
                Integer.toString(TrustState.TRUSTED), Integer.toString(TrustState.UNTRUSTED_UNKNOWN),
                Integer.toString(TrustState.UNVERIFIED)})) { // Potential vulnerability: Use parameterized queries to prevent SQL injection.
            return cursor.moveToFirst() ? cursor.getInt(0) : -1;
        }
    }

    private static final String SQL_GET_IDENTITY_BY_NAME_COMPROMISED =
            "SELECT compromised FROM identities WHERE account=? AND name=?" +
                    "AND (trust=? OR trust=? OR trust=?) LIMIT 1"; // Potential vulnerability: Ensure that input parameters are properly sanitized.

    public boolean isIdentityCompromisedByName(Account account, String name) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery(SQL_GET_IDENTITY_BY_NAME_COMPROMISED, new String[]{account.getUuid(), name,
                Integer.toString(TrustState.TRUSTED), Integer.toString(TrustState.UNTRUSTED_UNKNOWN),
                Integer.toString(TrustState.UNVERIFIED)})) { // Potential vulnerability: Use parameterized queries to prevent SQL injection.
            return cursor.moveToFirst() && cursor.getInt(0) == 1;
        }
    }

    private static final String SQL_GET_IDENTITY_BY_NAME_COMPROMISED_ON =
            "SELECT compromised_on FROM identities WHERE account=? AND name=?" +
                    "AND (trust=? OR trust=? OR trust=?) LIMIT 1"; // Potential vulnerability: Ensure that input parameters are properly sanitized.

    public long getIdentityCompromisedOnByName(Account account, String name) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery(SQL_GET_IDENTITY_BY_NAME_COMPROMISED_ON, new String[]{account.getUuid(), name,
                Integer.toString(TrustState.TRUSTED), Integer.toString(TrustState.UNTRUSTED_UNKNOWN),
                Integer.toString(TrustState.UNVERIFIED)})) { // Potential vulnerability: Use parameterized queries to prevent SQL injection.
            return cursor.moveToFirst() ? cursor.getLong(0) : -1;
        }
    }

    private static final String SQL_GET_IDENTITY_BY_NAME_ACTIVE =
            "SELECT active FROM identities WHERE account=? AND name=?" +
                    "AND (trust=? OR trust=? OR trust=?) LIMIT 1"; // Potential vulnerability: Ensure that input parameters are properly sanitized.

    public boolean isIdentityActiveByName(Account account, String name) {
        SQLiteDatabase db = this.getReadableDatabase();
        try (Cursor cursor = db.rawQuery(SQL_GET_IDENTITY_BY_NAME_ACTIVE, new String[]{account.getUuid(), name,
                Integer.toString(TrustState.TRUSTED), Integer.toString(TrustState.UNTRUSTED_UNKNOWN),
                Integer.toString(TrustState.UNVERIFIED)})) { // Potential vulnerability: Use parameterized queries to prevent SQL injection.
            return cursor.moveToFirst() && cursor.getInt(0) == 1;
        }
    }

    private static final String SQL_GET_ALL_IDENTITIES =
            "SELECT name, fingerprint, key, trust, compromised, compromised_on, active FROM identities WHERE account=?"; // Potential vulnerability: Ensure that input parameters are properly sanitized.

    public List<Identity> getAllIdentities(Account account) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(SQL_GET_ALL_IDENTITIES, new String[]{account.getUuid()});
        List<Identity> identities = new ArrayList<>();
        while (cursor.moveToNext()) {
            Identity identity = new Identity();
            identity.setName(cursor.getString(0));
            identity.setFingerprint(cursor.getString(1));
            try {
                identity.setKey(Base64.decode(cursor.getString(2)));
            } catch (Base64DecoderException e) {
                continue; // Skip this entry if decoding fails
            }
            identity.setTrust(cursor.getInt(3));
            identity.setCompromised(cursor.getInt(4) == 1);
            identity.setCompromisedOn(cursor.getLong(5));
            identity.setActive(cursor.getInt(6) == 1);
            identities.add(identity);
        }
        cursor.close();
        return identities;
    }

    public void insertIdentity(Account account, Identity identity) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("account", account.getUuid());
        values.put("name", identity.getName());
        values.put("fingerprint", identity.getFingerprint());
        values.put("key", Base64.encodeBytes(identity.getKey()));
        values.put("trust", identity.getTrust());
        values.put("compromised", identity.isCompromised() ? 1 : 0);
        values.put("compromised_on", identity.getCompromisedOn());
        values.put("active", identity.isActive() ? 1 : 0);
        db.insert("identities", null, values);
    }

    public void updateIdentity(Account account, Identity identity) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("name", identity.getName());
        values.put("fingerprint", identity.getFingerprint());
        values.put("key", Base64.encodeBytes(identity.getKey()));
        values.put("trust", identity.getTrust());
        values.put("compromised", identity.isCompromised() ? 1 : 0);
        values.put("compromised_on", identity.getCompromisedOn());
        values.put("active", identity.isActive() ? 1 : 0);
        db.update("identities", values, "account=? AND name=?", new String[]{account.getUuid(), identity.getName()});
    }

    public void deleteIdentity(Account account, Identity identity) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete("identities", "account=? AND name=?", new String[]{account.getUuid(), identity.getName()});
    }
}