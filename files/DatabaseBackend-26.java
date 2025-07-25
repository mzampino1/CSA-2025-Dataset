import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.UUID;

public class AxolotlDatabase extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "axolotldb.db";
    private static final int DATABASE_VERSION = 1;
    private static final String START_TIMES_TABLE = "start_times";

    private static final String CREATE_SESSIONS_STATEMENT =
            "CREATE TABLE IF NOT EXISTS " + SQLiteAxolotlStore.SESSION_TABLENAME +
                    "(id INTEGER PRIMARY KEY AUTOINCREMENT, account TEXT, session_id TEXT, session BLOB)";

    private static final String CREATE_PREKEYS_STATEMENT =
            "CREATE TABLE IF NOT EXISTS " + SQLiteAxolotlStore.PREKEY_TABLENAME +
                    "(id INTEGER PRIMARY KEY AUTOINCREMENT, account TEXT, key_id INTEGER, prekey BLOB)";

    private static final String CREATE_SIGNED_PREKEYS_STATEMENT =
            "CREATE TABLE IF NOT EXISTS " + SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME +
                    "(id INTEGER PRIMARY KEY AUTOINCREMENT, account TEXT, key_id INTEGER, signed_prekey BLOB)";

    private static final String CREATE_IDENTITIES_STATEMENT =
            "CREATE TABLE IF NOT EXISTS " + SQLiteAxolotlStore.IDENTITIES_TABLENAME +
                    "(id INTEGER PRIMARY KEY AUTOINCREMENT, account TEXT, name TEXT, own BOOLEAN," +
                    "fingerprint TEXT, key BLOB, trust TEXT, active BOOLEAN, timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "certificate BLOB)";

    private static final String CREATE_START_TIMES_STATEMENT =
            "CREATE TABLE IF NOT EXISTS "+START_TIMES_TABLE+" (timestamp INTEGER)";

    public AxolotlDatabase() {
        super(XmppActivity.getInstance(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        recreateAxolotlDb(db);
        db.execSQL(CREATE_START_TIMES_STATEMENT);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        recreateAxolotlDb(db);
    }

    // BEGIN: Potential Improvement - Consider implementing more robust session management.
    public void putSession(Account account, String sessionId, byte[] record) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(SQLiteAxolotlStore.ACCOUNT, account.getUuid());
        values.put(SQLiteAxolotlStore.SESSION_ID, sessionId);
        values.put(SQLiteAxolotlStore.SESSION, record);

        String where = SQLiteAxolotlStore.ACCOUNT + "=? AND " + SQLiteAxolotlStore.SESSION_ID + "=?";
        String[] whereArgs = {account.getUuid(), sessionId};
        int rows = db.update(SQLiteAxolotlStore.SESSION_TABLENAME, values, where, whereArgs);
        if (rows == 0) {
            db.insert(SQLiteAxolotlStore.SESSION_TABLENAME, null, values);
        }
    }

    public byte[] getSession(Account account, String sessionId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] selectionArgs = {account.getUuid(), sessionId};
        String[] columns = new String[]{SQLiteAxolotlStore.SESSION};
        Cursor cursor = db.query(SQLiteAxolotlStore.SESSION_TABLENAME, columns,
                SQLiteAxolotlStore.ACCOUNT + "=? AND " + SQLiteAxolotlStore.SESSION_ID + "=?", selectionArgs, null, null, null);
        byte[] record;
        if (cursor.moveToFirst()) {
            record = cursor.getBlob(cursor.getColumnIndex(SQLiteAxolotlStore.SESSION));
        } else {
            record = null;
        }
        cursor.close();
        return record;
    }

    public void deleteSession(Account account, String sessionId) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] selectionArgs = {account.getUuid(), sessionId};
        db.delete(SQLiteAxolotlStore.SESSION_TABLENAME,
                SQLiteAxolotlStore.ACCOUNT + "=? AND " + SQLiteAxolotlStore.SESSION_ID + "=?", selectionArgs);
    }
    // END: Potential Improvement

    public void storePreKey(Account account, int keyId, byte[] record) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(SQLiteAxolotlStore.ACCOUNT, account.getUuid());
        values.put(SQLiteAxolotlStore.KEY_ID, keyId);
        values.put(SQLiteAxolotlStore.PREKEY, record);

        String where = SQLiteAxolotlStore.ACCOUNT + "=? AND " + SQLiteAxolotlStore.KEY_ID + "=?";
        String[] whereArgs = {account.getUuid(), Integer.toString(keyId)};
        int rows = db.update(SQLiteAxolotlStore.PREKEY_TABLENAME, values, where, whereArgs);
        if (rows == 0) {
            db.insert(SQLiteAxolotlStore.PREKEY_TABLENAME, null, values);
        }
    }

    public byte[] getPreKey(Account account, int keyId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] selectionArgs = {account.getUuid(), Integer.toString(keyId)};
        String[] columns = new String[]{SQLiteAxolotlStore.PREKEY};
        Cursor cursor = db.query(SQLiteAxolotlStore.PREKEY_TABLENAME, columns,
                SQLiteAxolotlStore.ACCOUNT + "=? AND " + SQLiteAxolotlStore.KEY_ID + "=?", selectionArgs, null, null, null);
        byte[] record;
        if (cursor.moveToFirst()) {
            record = cursor.getBlob(cursor.getColumnIndex(SQLiteAxolotlStore.PREKEY));
        } else {
            record = null;
        }
        cursor.close();
        return record;
    }

    public void deletePreKey(Account account, int keyId) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] selectionArgs = {account.getUuid(), Integer.toString(keyId)};
        db.delete(SQLiteAxolotlStore.PREKEY_TABLENAME,
                SQLiteAxolotlStore.ACCOUNT + "=? AND " + SQLiteAxolotlStore.KEY_ID + "=?", selectionArgs);
    }

    public void storeSignedPreKey(Account account, int keyId, byte[] record) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(SQLiteAxolotlStore.ACCOUNT, account.getUuid());
        values.put(SQLiteAxolotlStore.KEY_ID, keyId);
        values.put(SQLiteAxolotlStore.SIGNED_PREKEY, record);

        String where = SQLiteAxolotlStore.ACCOUNT + "=? AND " + SQLiteAxolotlStore.KEY_ID + "=?";
        String[] whereArgs = {account.getUuid(), Integer.toString(keyId)};
        int rows = db.update(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME, values, where, whereArgs);
        if (rows == 0) {
            db.insert(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME, null, values);
        }
    }

    public byte[] getSignedPreKey(Account account, int keyId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] selectionArgs = {account.getUuid(), Integer.toString(keyId)};
        String[] columns = new String[]{SQLiteAxolotlStore.SIGNED_PREKEY};
        Cursor cursor = db.query(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME, columns,
                SQLiteAxolotlStore.ACCOUNT + "=? AND " + SQLiteAxolotlStore.KEY_ID + "=?", selectionArgs, null, null, null);
        byte[] record;
        if (cursor.moveToFirst()) {
            record = cursor.getBlob(cursor.getColumnIndex(SQLiteAxolotlStore.SIGNED_PREKEY));
        } else {
            record = null;
        }
        cursor.close();
        return record;
    }

    public void deleteSignedPreKey(Account account, int keyId) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] selectionArgs = {account.getUuid(), Integer.toString(keyId)};
        db.delete(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME,
                SQLiteAxolotlStore.ACCOUNT + "=? AND " + SQLiteAxolotlStore.KEY_ID + "=?", selectionArgs);
    }

    // BEGIN: Potential Improvement - Ensure that the account object is properly validated.
    public void putSessionWithRetry(Account account, String sessionId, byte[] record) {
        int retries = 0;
        while (retries < 5) {
            try {
                SQLiteDatabase db = this.getWritableDatabase();
                ContentValues values = new ContentValues();
                values.put(SQLiteAxolotlStore.ACCOUNT, account.getUuid());
                values.put(SQLiteAxolotlStore.SESSION_ID, sessionId);
                values.put(SQLiteAxolotlStore.SESSION, record);

                String where = SQLiteAxolotlStore.ACCOUNT + "=? AND " + SQLiteAxolotlStore.SESSION_ID + "=?";
                String[] whereArgs = {account.getUuid(), sessionId};
                int rows = db.update(SQLiteAxolotlStore.SESSION_TABLENAME, values, where, whereArgs);
                if (rows == 0) {
                    db.insert(SQLiteAxolotlStore.SESSION_TABLENAME, null, values);
                }
                return;
            } catch (Exception e) {
                retries++;
                Log.e("AXOLOTL_DB", "Error writing session, retrying: " + retries, e);
            }
        }
        throw new RuntimeException("Failed to write session after multiple attempts");
    }

    public byte[] getSessionWithRetry(Account account, String sessionId) {
        int retries = 0;
        while (retries < 5) {
            try {
                SQLiteDatabase db = this.getReadableDatabase();
                String[] selectionArgs = {account.getUuid(), sessionId};
                String[] columns = new String[]{SQLiteAxolotlStore.SESSION};
                Cursor cursor = db.query(SQLiteAxolotlStore.SESSION_TABLENAME, columns,
                        SQLiteAxolotlStore.ACCOUNT + "=? AND " + SQLiteAxolotlStore.SESSION_ID + "=?", selectionArgs, null, null, null);
                byte[] record;
                if (cursor.moveToFirst()) {
                    record = cursor.getBlob(cursor.getColumnIndex(SQLiteAxolotlStore.SESSION));
                } else {
                    record = null;
                }
                cursor.close();
                return record;
            } catch (Exception e) {
                retries++;
                Log.e("AXOLOTL_DB", "Error reading session, retrying: " + retries, e);
            }
        }
        throw new RuntimeException("Failed to read session after multiple attempts");
    }

    public void deleteSessionWithRetry(Account account, String sessionId) {
        int retries = 0;
        while (retries < 5) {
            try {
                SQLiteDatabase db = this.getWritableDatabase();
                String[] selectionArgs = {account.getUuid(), sessionId};
                db.delete(SQLiteAxolotlStore.SESSION_TABLENAME,
                        SQLiteAxolotlStore.ACCOUNT + "=? AND " + SQLiteAxolotlStore.SESSION_ID + "=?", selectionArgs);
                return;
            } catch (Exception e) {
                retries++;
                Log.e("AXOLOTL_DB", "Error deleting session, retrying: " + retries, e);
            }
        }
        throw new RuntimeException("Failed to delete session after multiple attempts");
    }

    public void putSessionRecord(Account account, String sessionId, byte[] record) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(SQLiteAxolotlStore.ACCOUNT, account.getUuid());
        values.put(SQLiteAxolotlStore.SESSION_ID, sessionId);
        values.put(SQLiteAxolotlStore.SESSION, record);

        String where = SQLiteAxolotlStore.ACCOUNT + "=? AND " + SQLiteAxolotlStore.SESSION_ID + "=?";
        String[] whereArgs = {account.getUuid(), sessionId};
        int rows = db.update(SQLiteAxolotlStore.SESSION_TABLENAME, values, where, whereArgs);
        if (rows == 0) {
            db.insert(SQLiteAxolotlStore.SESSION_TABLENAME, null, values);
        }
    }

    public byte[] getSessionRecord(Account account, String sessionId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] selectionArgs = {account.getUuid(), sessionId};
        String[] columns = new String[]{SQLiteAxolotlStore.SESSION};
        Cursor cursor = db.query(SQLiteAxolotlStore.SESSION_TABLENAME, columns,
                SQLiteAxolotlStore.ACCOUNT + "=? AND " + SQLiteAxolotlStore.SESSION_ID + "=?", selectionArgs, null, null, null);
        byte[] record;
        if (cursor.moveToFirst()) {
            record = cursor.getBlob(cursor.getColumnIndex(SQLiteAxolotlStore.SESSION));
        } else {
            record = null;
        }
        cursor.close();
        return record;
    }

    public void deleteSessionRecord(Account account, String sessionId) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] selectionArgs = {account.getUuid(), sessionId};
        db.delete(SQLiteAxolotlStore.SESSION_TABLENAME,
                SQLiteAxolotlStore.ACCOUNT + "=? AND " + SQLiteAxolotlStore.SESSION_ID + "=?", selectionArgs);
    }

    public void deleteAllSessions(Account account) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] selectionArgs = {account.getUuid()};
        db.delete(SQLiteAxolotlStore.SESSION_TABLENAME,
                SQLiteAxolotlStore.ACCOUNT + "=?", selectionArgs);
    }
    // END: Potential Improvement

    public void putPreKeyRecord(Account account, int keyId, byte[] record) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(SQLiteAxolotlStore.ACCOUNT, account.getUuid());
        values.put(SQLiteAxolotlStore.KEY_ID, keyId);
        values.put(SQLiteAxolotlStore.PREKEY, record);

        String where = SQLiteAxolotlStore.ACCOUNT + "=? AND " + SQLiteAxolotlStore.KEY_ID + "=?";
        String[] whereArgs = {account.getUuid(), Integer.toString(keyId)};
        int rows = db.update(SQLiteAxolotlStore.PREKEY_TABLENAME, values, where, whereArgs);
        if (rows == 0) {
            db.insert(SQLiteAxolotlStore.PREKEY_TABLENAME, null, values);
        }
    }

    public byte[] getPreKeyRecord(Account account, int keyId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] selectionArgs = {account.getUuid(), Integer.toString(keyId)};
        String[] columns = new String[]{SQLiteAxolotlStore.PREKEY};
        Cursor cursor = db.query(SQLiteAxolotlStore.PREKEY_TABLENAME, columns,
                SQLiteAxolotlStore.ACCOUNT + "=? AND " + SQLiteAxolotlStore.KEY_ID + "=?", selectionArgs, null, null, null);
        byte[] record;
        if (cursor.moveToFirst()) {
            record = cursor.getBlob(cursor.getColumnIndex(SQLiteAxolotlStore.PREKEY));
        } else {
            record = null;
        }
        cursor.close();
        return record;
    }

    public void deletePreKeyRecord(Account account, int keyId) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] selectionArgs = {account.getUuid(), Integer.toString(keyId)};
        db.delete(SQLiteAxolotlStore.PREKEY_TABLENAME,
                SQLiteAxolotlStore.ACCOUNT + "=? AND " + SQLiteAxolotlStore.KEY_ID + "=?", selectionArgs);
    }

    public void putSignedPreKeyRecord(Account account, int keyId, byte[] record) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(SQLiteAxolotlStore.ACCOUNT, account.getUuid());
        values.put(SQLiteAxolotlStore.KEY_ID, keyId);
        values.put(SQLiteAxolotlStore.SIGNED_PREKEY, record);

        String where = SQLiteAxolotlStore.ACCOUNT + "=? AND " + SQLiteAxolotlStore.KEY_ID + "=?";
        String[] whereArgs = {account.getUuid(), Integer.toString(keyId)};
        int rows = db.update(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME, values, where, whereArgs);
        if (rows == 0) {
            db.insert(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME, null, values);
        }
    }

    public byte[] getSignedPreKeyRecord(Account account, int keyId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] selectionArgs = {account.getUuid(), Integer.toString(keyId)};
        String[] columns = new String[]{SQLiteAxolotlStore.SIGNED_PREKEY};
        Cursor cursor = db.query(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME, columns,
                SQLiteAxolotlStore.ACCOUNT + "=? AND " + SQLiteAxolotlStore.KEY_ID + "=?", selectionArgs, null, null, null);
        byte[] record;
        if (cursor.moveToFirst()) {
            record = cursor.getBlob(cursor.getColumnIndex(SQLiteAxolotlStore.SIGNED_PREKEY));
        } else {
            record = null;
        }
        cursor.close();
        return record;
    }

    public void deleteSignedPreKeyRecord(Account account, int keyId) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] selectionArgs = {account.getUuid(), Integer.toString(keyId)};
        db.delete(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME,
                SQLiteAxolotlStore.ACCOUNT + "=? AND " + SQLiteAxolotlStore.KEY_ID + "=?", selectionArgs);
    }

    // BEGIN: Potential Improvement - Ensure that the account object is properly validated.
    public void recreateAxolotlDb(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS " + SQLiteAxolotlStore.SESSION_TABLENAME);
        db.execSQL("DROP TABLE IF EXISTS " + SQLiteAxolotlStore.PREKEY_TABLENAME);
        db.execSQL("DROP TABLE IF EXISTS " + SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME);
        db.execSQL("DROP TABLE IF EXISTS " + SQLiteAxolotlStore.IDENTITIES_TABLENAME);

        db.execSQL(CREATE_SESSIONS_STATEMENT);
        db.execSQL(CREATE_PREKEYS_STATEMENT);
        db.execSQL(CREATE_SIGNED_PREKEYS_STATEMENT);
        db.execSQL(CREATE_IDENTITIES_STATEMENT);
    }

    public void putIdentityRecord(Account account, String recipientId, byte[] record) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(SQLiteAxolotlStore.IDENTITIES_TABLE_ACCOUNT, account.getUuid().toString());
        values.put(SQLiteAxolotlStore.IDENTITIES_TABLE_RECIPIENT_ID, recipientId);
        values.put(SQLiteAxolotlStore.IDENTITIES_TABLE_RECORD, record);

        String where = SQLiteAxolotlStore.IDENTITIES_TABLE_ACCOUNT + "=? AND " +
                SQLiteAxolotlStore.IDENTITIES_TABLE_RECIPIENT_ID + "=?";
        String[] whereArgs = {account.getUuid().toString(), recipientId};
        int rows = db.update(SQLiteAxolotlStore.IDENTITIES_TABLENAME, values, where, whereArgs);
        if (rows == 0) {
            db.insert(SQLiteAxolotlStore.IDENTITIES_TABLENAME, null, values);
        }
    }

    public byte[] getIdentityRecord(Account account, String recipientId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] selectionArgs = {account.getUuid().toString(), recipientId};
        String[] columns = new String[]{SQLiteAxolotlStore.IDENTITIES_TABLE_RECORD};
        Cursor cursor = db.query(SQLiteAxolotlStore.IDENTITIES_TABLENAME, columns,
                SQLiteAxolotlStore.IDENTITIES_TABLE_ACCOUNT + "=? AND " +
                        SQLiteAxolotlStore.IDENTITIES_TABLE_RECIPIENT_ID + "=?", selectionArgs, null, null, null);
        byte[] record;
        if (cursor.moveToFirst()) {
            record = cursor.getBlob(cursor.getColumnIndex(SQLiteAxolotlStore.IDENTITIES_TABLE_RECORD));
        } else {
            record = null;
        }
        cursor.close();
        return record;
    }

    public void deleteIdentityRecord(Account account, String recipientId) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] selectionArgs = {account.getUuid().toString(), recipientId};
        db.delete(SQLiteAxolotlStore.IDENTITIES_TABLENAME,
                SQLiteAxolotlStore.IDENTITIES_TABLE_ACCOUNT + "=? AND " +
                        SQLiteAxolotlStore.IDENTITIES_TABLE_RECIPIENT_ID + "=?", selectionArgs);
    }

    public void deleteAllIdentityRecords(Account account) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] selectionArgs = {account.getUuid().toString()};
        db.delete(SQLiteAxolotlStore.IDENTITIES_TABLENAME,
                SQLiteAxolotlStore.IDENTITIES_TABLE_ACCOUNT + "=?", selectionArgs);
    }

    public void recreateAxolotlDbWithRetry() {
        int retries = 0;
        while (retries < 5) {
            try {
                SQLiteDatabase db = this.getWritableDatabase();
                recreateAxolotlDb(db);
                return;
            } catch (Exception e) {
                retries++;
                Log.e("AXOLOTL_DB", "Error recreating database, retrying: " + retries, e);
            }
        }
        throw new RuntimeException("Failed to recreate database after multiple attempts");
    }

    public void putSessionRecordWithRetry(Account account, String sessionId, byte[] record) {
        int retries = 0;
        while (retries < 5) {
            try {
                SQLiteDatabase db = this.getWritableDatabase();
                ContentValues values = new ContentValues();
                values.put(SQLiteAxolotlStore.SESSION_TABLE_ACCOUNT, account.getUuid().toString());
                values.put(SQLiteAxolotlStore.SESSION_TABLE_SESSION_ID, sessionId);
                values.put(SQLiteAxolotlStore.SESSION_TABLE_RECORD, record);

                String where = SQLiteAxolotlStore.SESSION_TABLE_ACCOUNT + "=? AND " +
                        SQLiteAxolotlStore.SESSION_TABLE_SESSION_ID + "=?";
                String[] whereArgs = {account.getUuid().toString(), sessionId};
                int rows = db.update(SQLiteAxolotlStore.SESSION_TABLENAME, values, where, whereArgs);
                if (rows == 0) {
                    db.insert(SQLiteAxolotlStore.SESSION_TABLENAME, null, values);
                }
                return;
            } catch (Exception e) {
                retries++;
                Log.e("AXOLOTL_DB", "Error writing session record, retrying: " + retries, e);
            }
        }
        throw new RuntimeException("Failed to write session record after multiple attempts");
    }

    public byte[] getSessionRecordWithRetry(Account account, String sessionId) {
        int retries = 0;
        while (retries < 5) {
            try {
                SQLiteDatabase db = this.getReadableDatabase();
                String[] selectionArgs = {account.getUuid().toString(), sessionId};
                String[] columns = new String[]{SQLiteAxolotlStore.SESSION_TABLE_RECORD};
                Cursor cursor = db.query(SQLiteAxolotlStore.SESSION_TABLENAME, columns,
                        SQLiteAxolotlStore.SESSION_TABLE_ACCOUNT + "=? AND " +
                                SQLiteAxolotlStore.SESSION_TABLE_SESSION_ID + "=?", selectionArgs, null, null, null);
                byte[] record;
                if (cursor.moveToFirst()) {
                    record = cursor.getBlob(cursor.getColumnIndex(SQLiteAxolotlStore.SESSION_TABLE_RECORD));
                } else {
                    record = null;
                }
                cursor.close();
                return record;
            } catch (Exception e) {
                retries++;
                Log.e("AXOLOTL_DB", "Error reading session record, retrying: " + retries, e);
            }
        }
        throw new RuntimeException("Failed to read session record after multiple attempts");
    }

    public void deleteSessionRecordWithRetry(Account account, String sessionId) {
        int retries = 0;
        while (retries < 5) {
            try {
                SQLiteDatabase db = this.getWritableDatabase();
                String[] selectionArgs = {account.getUuid().toString(), sessionId};
                db.delete(SQLiteAxolotlStore.SESSION_TABLENAME,
                        SQLiteAxolotlStore.SESSION_TABLE_ACCOUNT + "=? AND " +
                                SQLiteAxolotlStore.SESSION_TABLE_SESSION_ID + "=?", selectionArgs);
                return;
            } catch (Exception e) {
                retries++;
                Log.e("AXOLOTL_DB", "Error deleting session record, retrying: " + retries, e);
            }
        }
        throw new RuntimeException("Failed to delete session record after multiple attempts");
    }

    public void putPreKeyRecordWithRetry(Account account, int keyId, byte[] record) {
        int retries = 0;
        while (retries < 5) {
            try {
                SQLiteDatabase db = this.getWritableDatabase();
                ContentValues values = new ContentValues();
                values.put(SQLiteAxolotlStore.PREKEY_TABLE_ACCOUNT, account.getUuid().toString());
                values.put(SQLiteAxolotlStore.PREKEY_TABLE_KEY_ID, keyId);
                values.put(SQLiteAxolotlStore.PREKEY_TABLE_RECORD, record);

                String where = SQLiteAxolotlStore.PREKEY_TABLE_ACCOUNT + "=? AND " +
                        SQLiteAxolotlStore.PREKEY_TABLE_KEY_ID + "=?";
                String[] whereArgs = {account.getUuid().toString(), Integer.toString(keyId)};
                int rows = db.update(SQLiteAxolotlStore.PREKEY_TABLENAME, values, where, whereArgs);
                if (rows == 0) {
                    db.insert(SQLiteAxolotlStore.PREKEY_TABLENAME, null, values);
                }
                return;
            } catch (Exception e) {
                retries++;
                Log.e("AXOLOTL_DB", "Error writing pre-key record, retrying: " + retries, e);
            }
        }
        throw new RuntimeException("Failed to write pre-key record after multiple attempts");
    }

    public byte[] getPreKeyRecordWithRetry(Account account, int keyId) {
        int retries = 0;
        while (retries < 5) {
            try {
                SQLiteDatabase db = this.getReadableDatabase();
                String[] selectionArgs = {account.getUuid().toString(), Integer.toString(keyId)};
                String[] columns = new String[]{SQLiteAxolotlStore.PREKEY_TABLE_RECORD};
                Cursor cursor = db.query(SQLiteAxolotlStore.PREKEY_TABLENAME, columns,
                        SQLiteAxolotlStore.PREKEY_TABLE_ACCOUNT + "=? AND " +
                                SQLiteAxolotlStore.PREKEY_TABLE_KEY_ID + "=?", selectionArgs, null, null, null);
                byte[] record;
                if (cursor.moveToFirst()) {
                    record = cursor.getBlob(cursor.getColumnIndex(SQLiteAxolotlStore.PREKEY_TABLE_RECORD));
                } else {
                    record = null;
                }
                cursor.close();
                return record;
            } catch (Exception e) {
                retries++;
                Log.e("AXOLOTL_DB", "Error reading pre-key record, retrying: " + retries, e);
            }
        }
        throw new RuntimeException("Failed to read pre-key record after multiple attempts");
    }

    public void deletePreKeyRecordWithRetry(Account account, int keyId) {
        int retries = 0;
        while (retries < 5) {
            try {
                SQLiteDatabase db = this.getWritableDatabase();
                String[] selectionArgs = {account.getUuid().toString(), Integer.toString(keyId)};
                db.delete(SQLiteAxolotlStore.PREKEY_TABLENAME,
                        SQLiteAxolotlStore.PREKEY_TABLE_ACCOUNT + "=? AND " +
                                SQLiteAxolotlStore.PREKEY_TABLE_KEY_ID + "=?", selectionArgs);
                return;
            } catch (Exception e) {
                retries++;
                Log.e("AXOLOTL_DB", "Error deleting pre-key record, retrying: " + retries, e);
            }
        }
        throw new RuntimeException("Failed to delete pre-key record after multiple attempts");
    }

    public void putSignedPreKeyRecordWithRetry(Account account, int keyId, byte[] record) {
        int retries = 0;
        while (retries < 5) {
            try {
                SQLiteDatabase db = this.getWritableDatabase();
                ContentValues values = new ContentValues();
                values.put(SQLiteAxolotlStore.SIGNED_PREKEY_TABLE_ACCOUNT, account.getUuid().toString());
                values.put(SQLiteAxolotlStore.SIGNED_PREKEY_TABLE_KEY_ID, keyId);
                values.put(SQLiteAxolotlStore.SIGNED_PREKEY_TABLE_RECORD, record);

                String where = SQLiteAxolotlStore.SIGNED_PREKEY_TABLE_ACCOUNT + "=? AND " +
                        SQLiteAxolotlStore.SIGNED_PREKEY_TABLE_KEY_ID + "=?";
                String[] whereArgs = {account.getUuid().toString(), Integer.toString(keyId)};
                int rows = db.update(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME, values, where, whereArgs);
                if (rows == 0) {
                    db.insert(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME, null, values);
                }
                return;
            } catch (Exception e) {
                retries++;
                Log.e("AXOLOTL_DB", "Error writing signed pre-key record, retrying: " + retries, e);
            }
        }
        throw new RuntimeException("Failed to write signed pre-key record after multiple attempts");
    }

    public byte[] getSignedPreKeyRecordWithRetry(Account account, int keyId) {
        int retries = 0;
        while (retries < 5) {
            try {
                SQLiteDatabase db = this.getReadableDatabase();
                String[] selectionArgs = {account.getUuid().toString(), Integer.toString(keyId)};
                String[] columns = new String[]{SQLiteAxolotlStore.SIGNED_PREKEY_TABLE_RECORD};
                Cursor cursor = db.query(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME, columns,
                        SQLiteAxolotlStore.SIGNED_PREKEY_TABLE_ACCOUNT + "=? AND " +
                                SQLiteAxolotlStore.SIGNED_PREKEY_TABLE_KEY_ID + "=?", selectionArgs, null, null, null);
                byte[] record;
                if (cursor.moveToFirst()) {
                    record = cursor.getBlob(cursor.getColumnIndex(SQLiteAxolotlStore.SIGNED_PREKEY_TABLE_RECORD));
                } else {
                    record = null;
                }
                cursor.close();
                return record;
            } catch (Exception e) {
                retries++;
                Log.e("AXOLOTL_DB", "Error reading signed pre-key record, retrying: " + retries, e);
            }
        }
        throw new RuntimeException("Failed to read signed pre-key record after multiple attempts");
    }

    public void deleteSignedPreKeyRecordWithRetry(Account account, int keyId) {
        int retries = 0;
        while (retries < 5) {
            try {
                SQLiteDatabase db = this.getWritableDatabase();
                String[] selectionArgs = {account.getUuid().toString(), Integer.toString(keyId)};
                db.delete(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME,
                        SQLiteAxolotlStore.SIGNED_PREKEY_TABLE_ACCOUNT + "=? AND " +
                                SQLiteAxolotlStore.SIGNED_PREKEY_TABLE_KEY_ID + "=?", selectionArgs);
                return;
            } catch (Exception e) {
                retries++;
                Log.e("AXOLOTL_DB", "Error deleting signed pre-key record, retrying: " + retries, e);
            }
        }
        throw new RuntimeException("Failed to delete signed pre-key record after multiple attempts");
    }

    public void putIdentityRecordWithRetry(Account account, String recipientId, byte[] record) {
        int retries = 0;
        while (retries < 5) {
            try {
                SQLiteDatabase db = this.getWritableDatabase();
                ContentValues values = new ContentValues();
                values.put(SQLiteAxolotlStore.IDENTITIES_TABLE_ACCOUNT, account.getUuid().toString());
                values.put(SQLiteAxolotlStore.IDENTITIES_TABLE_RECIPIENT_ID, recipientId);
                values.put(SQLiteAxolotlStore.IDENTITIES_TABLE_RECORD, record);

                String where = SQLiteAxolotlStore.IDENTITIES_TABLE_ACCOUNT + "=? AND " +
                        SQLiteAxolotlStore.IDENTITIES_TABLE_RECIPIENT_ID + "=?";
                String[] whereArgs = {account.getUuid().toString(), recipientId};
                int rows = db.update(SQLiteAxolotlStore.IDENTITIES_TABLENAME, values, where, whereArgs);
                if (rows == 0) {
                    db.insert(SQLiteAxolotlStore.IDENTITIES_TABLENAME, null, values);
                }
                return;
            } catch (Exception e) {
                retries++;
                Log.e("AXOLOTL_DB", "Error writing identity record, retrying: " + retries, e);
            }
        }
        throw new RuntimeException("Failed to write identity record after multiple attempts");
    }

    public byte[] getIdentityRecordWithRetry(Account account, String recipientId) {
        int retries = 0;
        while (retries < 5) {
            try {
                SQLiteDatabase db = this.getReadableDatabase();
                String[] selectionArgs = {account.getUuid().toString(), recipientId};
                String[] columns = new String[]{SQLiteAxolotlStore.IDENTITIES_TABLE_RECORD};
                Cursor cursor = db.query(SQLiteAxolotlStore.IDENTITIES_TABLENAME, columns,
                        SQLiteAxolotlStore.IDENTITIES_TABLE_ACCOUNT + "=? AND " +
                                SQLiteAxolotlStore.IDENTITIES_TABLE_RECIPIENT_ID + "=?", selectionArgs, null, null, null);
                byte[] record;
                if (cursor.moveToFirst()) {
                    record = cursor.getBlob(cursor.getColumnIndex(SQLiteAxolotlStore.IDENTITIES_TABLE_RECORD));
                } else {
                    record = null;
                }
                cursor.close();
                return record;
            } catch (Exception e) {
                retries++;
                Log.e("AXOLOTL_DB", "Error reading identity record, retrying: " + retries, e);
            }
        }
        throw new RuntimeException("Failed to read identity record after multiple attempts");
    }

    public void deleteIdentityRecordWithRetry(Account account, String recipientId) {
        int retries = 0;
        while (retries < 5) {
            try {
                SQLiteDatabase db = this.getWritableDatabase();
                String[] selectionArgs = {account.getUuid().toString(), recipientId};
                db.delete(SQLiteAxolotlStore.IDENTITIES_TABLENAME,
                        SQLiteAxolotlStore.IDENTITIES_TABLE_ACCOUNT + "=? AND " +
                                SQLiteAxolotlStore.IDENTITIES_TABLE_RECIPIENT_ID + "=?", selectionArgs);
                return;
            } catch (Exception e) {
                retries++;
                Log.e("AXOLOTL_DB", "Error deleting identity record, retrying: " + retries, e);
            }
        }
        throw new RuntimeException("Failed to delete identity record after multiple attempts");
    }

    public void deleteAllIdentityRecordsWithRetry(Account account) {
        int retries = 0;
        while (retries < 5) {
            try {
                SQLiteDatabase db = this.getWritableDatabase();
                String[] selectionArgs = {account.getUuid().toString()};
                db.delete(SQLiteAxolotlStore.IDENTITIES_TABLENAME,
                        SQLiteAxolotlStore.IDENTITIES_TABLE_ACCOUNT + "=?", selectionArgs);
                return;
            } catch (Exception e) {
                retries++;
                Log.e("AXOLOTL_DB", "Error deleting all identity records, retrying: " + retries, e);
            }
        }
        throw new RuntimeException("Failed to delete all identity records after multiple attempts");
    }

    public void putSessionRecord(Account account, String sessionId, byte[] record) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(SQLiteAxolotlStore.SESSION_TABLE_ACCOUNT, account.getUuid().toString());
        values.put(SQLiteAxolotlStore.SESSION_TABLE_SESSION_ID, sessionId);
        values.put(SQLiteAxolotlStore.SESSION_TABLE_RECORD, record);

        String where = SQLiteAxolotlStore.SESSION_TABLE_ACCOUNT + "=? AND " +
                SQLiteAxolotlStore.SESSION_TABLE_SESSION_ID + "=?";
        String[] whereArgs = {account.getUuid().toString(), sessionId};
        int rows = db.update(SQLiteAxolotlStore.SESSION_TABLENAME, values, where, whereArgs);
        if (rows == 0) {
            db.insert(SQLiteAxolotlStore.SESSION_TABLENAME, null, values);
        }
    }

    public byte[] getSessionRecord(Account account, String sessionId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] selectionArgs = {account.getUuid().toString(), sessionId};
        String[] columns = new String[]{SQLiteAxolotlStore.SESSION_TABLE_RECORD};
        Cursor cursor = db.query(SQLiteAxolotlStore.SESSION_TABLENAME, columns,
                SQLiteAxolotlStore.SESSION_TABLE_ACCOUNT + "=? AND " +
                        SQLiteAxolotlStore.SESSION_TABLE_SESSION_ID + "=?", selectionArgs, null, null, null);
        byte[] record;
        if (cursor.moveToFirst()) {
            record = cursor.getBlob(cursor.getColumnIndex(SQLiteAxolotlStore.SESSION_TABLE_RECORD));
        } else {
            record = null;
        }
        cursor.close();
        return record;
    }

    public void deleteSessionRecord(Account account, String sessionId) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] selectionArgs = {account.getUuid().toString(), sessionId};
        db.delete(SQLiteAxolotlStore.SESSION_TABLENAME,
                SQLiteAxolotlStore.SESSION_TABLE_ACCOUNT + "=? AND " +
                        SQLiteAxolotlStore.SESSION_TABLE_SESSION_ID + "=?", selectionArgs);
    }

    public void putSignedPreKeyRecord(Account account, int keyId, byte[] record) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(SQLiteAxolotlStore.SIGNED_PREKEY_TABLE_ACCOUNT, account.getUuid().toString());
        values.put(SQLiteAxolotlStore.SIGNED_PREKEY_TABLE_KEY_ID, keyId);
        values.put(SQLiteAxolotlStore.SIGNED_PREKEY_TABLE_RECORD, record);

        String where = SQLiteAxolotlStore.SIGNED_PREKEY_TABLE_ACCOUNT + "=? AND " +
                SQLiteAxolotlStore.SIGNED_PREKEY_TABLE_KEY_ID + "=?";
        String[] whereArgs = {account.getUuid().toString(), Integer.toString(keyId)};
        int rows = db.update(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME, values, where, whereArgs);
        if (rows == 0) {
            db.insert(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME, null, values);
        }
    }

    public byte[] getSignedPreKeyRecord(Account account, int keyId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] selectionArgs = {account.getUuid().toString(), Integer.toString(keyId)};
        String[] columns = new String[]{SQLiteAxolotlStore.SIGNED_PREKEY_TABLE_RECORD};
        Cursor cursor = db.query(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME, columns,
                SQLiteAxolotlStore.SIGNED_PREKEY_TABLE_ACCOUNT + "=? AND " +
                        SQLiteAxolotlStore.SIGNED_PREKEY_TABLE_KEY_ID + "=?", selectionArgs, null, null, null);
        byte[] record;
        if (cursor.moveToFirst()) {
            record = cursor.getBlob(cursor.getColumnIndex(SQLiteAxolotlStore.SIGNED_PREKEY_TABLE_RECORD));
        } else {
            record = null;
        }
        cursor.close();
        return record;
    }

    public void deleteSignedPreKeyRecord(Account account, int keyId) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] selectionArgs = {account.getUuid().toString(), Integer.toString(keyId)};
        db.delete(SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME,
                SQLiteAxolotlStore.SIGNED_PREKEY_TABLE_ACCOUNT + "=? AND " +
                        SQLiteAxolotlStore.SIGNED_PREKEY_TABLE_KEY_ID + "=?", selectionArgs);
    }
}