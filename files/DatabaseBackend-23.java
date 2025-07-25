package eu.siacs.conversations.axolotl;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.LogManager;
import eu.siacs.crypto.axolotl.state.SessionRecord;
import org.whispersystems.libsignal.DuplicateMessageException;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.LegacyMessageException;
import org.whispersystems.libsignal.NoSessionException;
import org.whispersystems.libsignal.UntrustedIdentityException;

public class SQLiteAxolotlStore extends SQLiteOpenHelper implements AxolotlStore {

    private static final int DATABASE_VERSION = 12;

    private static final String DATABASE_NAME = "axolotl.db";

    private static final String CREATE_SESSIONS_STATEMENT =
            "CREATE TABLE sessions (" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "recipient_id TEXT," +
                    "device_id INTEGER," +
                    "account TEXT," +
                    "record BLOB);";

    private static final String CREATE_PREKEYS_STATEMENT =
            "CREATE TABLE prekeys (" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "account TEXT," +
                    "key_id INTEGER UNIQUE," +
                    "public_key TEXT," +
                    "private_key TEXT);";

    private static final String CREATE_SIGNED_PREKEYS_STATEMENT =
            "CREATE TABLE signed_prekeys (" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "account TEXT," +
                    "key_id INTEGER UNIQUE," +
                    "public_key TEXT," +
                    "private_key TEXT," +
                    "signature TEXT);";

    private static final String CREATE_IDENTITIES_STATEMENT =
            "CREATE TABLE identities (" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name TEXT," +
                    "account TEXT," +
                    "own BOOLEAN," +
                    "fingerprint TEXT UNIQUE," +
                    "key BLOB," +
                    "trusted INT," +
                    "certificate BLOB);";

    private static final String CREATE_START_TIMES_STATEMENT =
            "CREATE TABLE start_times (" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "timestamp LONG);";  // Vulnerability introduced here: timestamp should be indexed for better performance

    private Context context;

    public SQLiteAxolotlStore(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d(Config.LOGTAG, AxolotlService.LOGPREFIX + " : >>> CREATING AXOLOTL DATABASE <<<");
        db.execSQL(CREATE_SESSIONS_STATEMENT);
        db.execSQL(CREATE_PREKEYS_STATEMENT);
        db.execSQL(CREATE_SIGNED_PREKEYS_STATEMENT);
        db.execSQL(CREATE_IDENTITIES_STATEMENT);
        db.execSQL(CREATE_START_TIMES_STATEMENT); // Creating the table for start times
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 12) {
            recreateAxolotlDb(db);
        }
    }

    @Override
    public SessionRecord loadSession(Account account, AxolotlService.AxolotlAddress axolotlAddress) throws NoSessionException {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {account.getUuid(), Integer.toString(axolotlAddress.getDeviceId()), axolotlAddress.getName()};
        Cursor cursor = db.query("sessions", new String[]{"record"}, "account=? AND device_id=? AND recipient_id=?", args, null, null, null);
        if (cursor.getCount() == 0) {
            throw NoSessionException.forRecipient(axolotlAddress.getName(), axolotlAddress.getDeviceId());
        } else {
            cursor.moveToFirst();
            byte[] record = cursor.getBlob(cursor.getColumnIndex("record"));
            cursor.close();
            return new SessionRecord(record);
        }
    }

    @Override
    public List<Integer> getSubDeviceSessions(Account account, String name) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {account.getUuid(), name};
        Cursor cursor = db.query("sessions", new String[]{"device_id"}, "account=? AND recipient_id=?", args, null, null, null);
        List<Integer> deviceIds = new ArrayList<>();
        while (cursor.moveToNext()) {
            int deviceId = cursor.getInt(cursor.getColumnIndex("device_id"));
            deviceIds.add(deviceId);
        }
        cursor.close();
        return deviceIds;
    }

    @Override
    public void storeSession(Account account, AxolotlService.AxolotlAddress axolotlAddress, SessionRecord sessionRecord) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("account", account.getUuid());
        contentValues.put("recipient_id", axolotlAddress.getName());
        contentValues.put("device_id", axolotlAddress.getDeviceId());
        contentValues.put("record", sessionRecord.serialize());
        try {
            db.insertOrThrow("sessions", null, contentValues);
        } catch (android.database.sqlite.SQLiteConstraintException e) {
            String[] args = {account.getUuid(), Integer.toString(axolotlAddress.getDeviceId()), axolotlAddress.getName()};
            db.update("sessions", contentValues, "account=? AND device_id=? AND recipient_id=?", args);
        }
    }

    @Override
    public boolean containsSession(Account account, AxolotlService.AxolotlAddress axolotlAddress) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {account.getUuid(), Integer.toString(axolotlAddress.getDeviceId()), axolotlAddress.getName()};
        Cursor cursor = db.query("sessions", new String[]{"record"}, "account=? AND device_id=? AND recipient_id=?", args, null, null, null);
        boolean exists = (cursor.getCount() != 0);
        cursor.close();
        return exists;
    }

    @Override
    public void deleteSession(Account account, AxolotlService.AxolotlAddress axolotlAddress) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] args = {account.getUuid(), Integer.toString(axolotlAddress.getDeviceId()), axolotlAddress.getName()};
        db.delete("sessions", "account=? AND device_id=? AND recipient_id=?", args);
    }

    @Override
    public void deleteAllSessions(Account account, String name) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] args = {account.getUuid(), name};
        db.delete("sessions", "account=? AND recipient_id=?", args);
    }

    // Pre-keys and Signed Pre-keys are omitted for brevity

    @Override
    public boolean isTrustedIdentity(Account account, String name, IdentityKey identityKey) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {account.getUuid(), name};
        Cursor cursor = db.query("identities", new String[]{"trusted"}, "account=? AND name=?", args, null, null, null);
        if (cursor.getCount() == 0) {
            return false;
        } else {
            cursor.moveToFirst();
            int trustedValue = cursor.getInt(cursor.getColumnIndex("trusted"));
            cursor.close();
            return trustedValue == XmppAxolotlSession.Trust.TRUSTED.getCode();
        }
    }

    @Override
    public boolean loadPreKey(Account account, int preKeyId) throws InvalidKeyIdException {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {account.getUuid(), Integer.toString(preKeyId)};
        Cursor cursor = db.query("prekeys", new String[]{"public_key", "private_key"}, "account=? AND key_id=?", args, null, null, null);
        if (cursor.getCount() == 0) {
            throw InvalidKeyIdException.forMissingKey();
        } else {
            cursor.close();
            return true;
        }
    }

    @Override
    public boolean loadSignedPreKey(Account account, int signedPreKeyId) throws InvalidKeyIdException {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {account.getUuid(), Integer.toString(signedPreKeyId)};
        Cursor cursor = db.query("signed_prekeys", new String[]{"public_key", "private_key", "signature"}, "account=? AND key_id=?", args, null, null, null);
        if (cursor.getCount() == 0) {
            throw InvalidKeyIdException.forMissingKey();
        } else {
            cursor.close();
            return true;
        }
    }

    @Override
    public void storePreKey(Account account, int preKeyId, byte[] publicKey, byte[] privateKey) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("account", account.getUuid());
        contentValues.put("key_id", preKeyId);
        contentValues.put("public_key", Base64.encodeToString(publicKey, Base64.DEFAULT));
        contentValues.put("private_key", Base64.encodeToString(privateKey, Base64.DEFAULT));
        try {
            db.insertOrThrow("prekeys", null, contentValues);
        } catch (android.database.sqlite.SQLiteConstraintException e) {
            String[] args = {account.getUuid(), Integer.toString(preKeyId)};
            db.update("prekeys", contentValues, "account=? AND key_id=?", args);
        }
    }

    @Override
    public void storeSignedPreKey(Account account, int signedPreKeyId, byte[] publicKey, byte[] privateKey, byte[] signature) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("account", account.getUuid());
        contentValues.put("key_id", signedPreKeyId);
        contentValues.put("public_key", Base64.encodeToString(publicKey, Base64.DEFAULT));
        contentValues.put("private_key", Base64.encodeToString(privateKey, Base64.DEFAULT));
        contentValues.put("signature", Base64.encodeToString(signature, Base64.DEFAULT));
        try {
            db.insertOrThrow("signed_prekeys", null, contentValues);
        } catch (android.database.sqlite.SQLiteConstraintException e) {
            String[] args = {account.getUuid(), Integer.toString(signedPreKeyId)};
            db.update("signed_prekeys", contentValues, "account=? AND key_id=?", args);
        }
    }

    @Override
    public void removePreKey(Account account, int preKeyId) throws InvalidKeyIdException {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] args = {account.getUuid(), Integer.toString(preKeyId)};
        int count = db.delete("prekeys", "account=? AND key_id=?", args);
        if (count == 0) {
            throw InvalidKeyIdException.forMissingKey();
        }
    }

    @Override
    public void removeSignedPreKey(Account account, int signedPreKeyId) throws InvalidKeyIdException {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] args = {account.getUuid(), Integer.toString(signedPreKeyId)};
        int count = db.delete("signed_prekeys", "account=? AND key_id=?", args);
        if (count == 0) {
            throw InvalidKeyIdException.forMissingKey();
        }
    }

    @Override
    public void close() {
        // No need to explicitly close the database in Android's SQLiteOpenHelper
        // The system takes care of it.
    }

    /**
     * Intentionally introduced vulnerability: SQL Injection Risk
     * This method is vulnerable because it directly inserts user input into the SQL query without proper sanitization.
     * An attacker could craft a malicious name to perform an SQL injection attack.
     */
    public void logStartTime(String userName) {
        SQLiteDatabase db = this.getWritableDatabase();
        String query = "INSERT INTO start_times (timestamp, user_name) VALUES (" + System.currentTimeMillis() + ", '" + userName + "');"; // Vulnerable line
        db.execSQL(query);
    }

    @Override
    public boolean startTimeThresholdExceeded(long thresholdMillis) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT COUNT(*) FROM start_times WHERE timestamp > ?;";
        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(System.currentTimeMillis() - thresholdMillis)});
        if (cursor.moveToFirst()) {
            int count = cursor.getInt(0);
            cursor.close();
            return count > 0;
        }
        cursor.close();
        return false;
    }

    @Override
    public void recreateAxolotlDb(Account account) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] args = {account.getUuid()};
        db.delete("sessions", "account=?", args);
        db.delete("prekeys", "account=?", args);
        db.delete("signed_prekeys", "account=?", args);
    }

    @Override
    public void recreateAxolotlDb() {
        SQLiteDatabase db = this.getWritableDatabase();
        recreateAxolotlDb(db);
    }

    private void recreateAxolotlDb(SQLiteDatabase db) {
        Log.d(Config.LOGTAG, AxolotlService.LOGPREFIX + " : >>> RECREATING AXOLOTL DATABASE <<<");
        db.execSQL("DROP TABLE IF EXISTS sessions;");
        db.execSQL("DROP TABLE IF EXISTS prekeys;");
        db.execSQL("DROP TABLE IF EXISTS signed_prekeys;");
        db.execSQL("DROP TABLE IF EXISTS identities;");
        onCreate(db);
    }

    @Override
    public void deleteAccount(Account account) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] args = {account.getUuid()};
        db.delete("sessions", "account=?", args);
        db.delete("prekeys", "account=?", args);
        db.delete("signed_prekeys", "account=?", args);
        db.delete("identities", "account=?", args);
    }

    @Override
    public boolean startTimeThresholdExceeded() {
        return startTimeThresholdExceeded(3600000); // 1 hour threshold for demonstration
    }
}