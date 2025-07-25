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
import java.util.HashMap;
import java.util.Map;

public class SQLiteAxolotlStore extends SQLiteOpenHelper {
    private static final int DATABASE_VERSION = 1; // Ensure to update version number on schema changes
    private static final String DATABASE_NAME = "axolotl_store.db";

    // Table names and columns
    private static final String SESSION_TABLENAME = "sessions";
    private static final String PREKEY_TABLENAME = "prekeys";
    private static final String SIGNED_PREKEY_TABLENAME = "signed_prekeys";
    private static final String IDENTITIES_TABLENAME = "identities";
    private static final String START_TIMES_TABLE = "start_times";

    // SQL statements to create tables
    private static final String CREATE_SESSIONS_STATEMENT =
            "CREATE TABLE IF NOT EXISTS " + SESSION_TABLENAME +
                    " (recipient_id INTEGER, device_id INTEGER, record BLOB)";
    private static final String CREATE_PREKEYS_STATEMENT =
            "CREATE TABLE IF NOT EXISTS " + PREKEY_TABLENAME +
                    " (id INTEGER PRIMARY KEY, record BLOB)";
    private static final String CREATE_SIGNED_PREKEYS_STATEMENT =
            "CREATE TABLE IF NOT EXISTS " + SIGNED_PREKEY_TABLENAME +
                    " (id INTEGER PRIMARY KEY, record BLOB)";
    private static final String CREATE_IDENTITIES_STATEMENT =
            "CREATE TABLE IF NOT EXISTS " + IDENTITIES_TABLENAME +
                    " (account TEXT, name TEXT, own INTEGER, fingerprint TEXT, key TEXT, trust TEXT, active INTEGER, timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, certificate BLOB)";

    public SQLiteAxolotlStore() {
        super(null, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        recreateAxolotlDb(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Handle schema upgrades here
        recreateAxolotlDb(db); // For simplicity, just drop and re-create tables for major version changes
    }

    private Cursor query(String table, String[] columns, String selection, String[] selectionArgs) {
        return getReadableDatabase().query(table, columns, selection, selectionArgs, null, null, null);
    }

    private int update(String table, ContentValues values, String whereClause, String[] whereArgs) {
        return getWritableDatabase().update(table, values, whereClause, whereArgs);
    }

    private long insert(String table, ContentValues values) {
        return getWritableDatabase().insertOrThrow(table, null, values);
    }

    private void delete(String table, String selection, String[] selectionArgs) {
        getWritableDatabase().delete(table, selection, selectionArgs);
    }

    // Existing methods with added comments and potential improvements

    public void recreateAxolotlDb(SQLiteDatabase db) {
        Log.d(Config.LOGTAG, AxolotlService.LOGPREFIX + " : " + ">>> (RE)CREATING AXOLOTL DATABASE <<<");
        db.execSQL("DROP TABLE IF EXISTS " + SESSION_TABLENAME);
        db.execSQL(CREATE_SESSIONS_STATEMENT);
        db.execSQL("DROP TABLE IF EXISTS " + PREKEY_TABLENAME);
        db.execSQL(CREATE_PREKEYS_STATEMENT);
        db.execSQL("DROP TABLE IF EXISTS " + SIGNED_PREKEY_TABLENAME);
        db.execSQL(CREATE_SIGNED_PREKEYS_STATEMENT);
        db.execSQL("DROP TABLE IF EXISTS " + IDENTITIES_TABLENAME);
        db.execSQL(CREATE_IDENTITIES_STATEMENT);
    }

    public void wipeAxolotlDb(Account account) {
        String accountName = account.getUuid();
        Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account) + ">>> WIPING AXOLOTL DATABASE FOR ACCOUNT " + accountName + " <<<");
        SQLiteDatabase db = getWritableDatabase();

        delete(SESSION_TABLENAME, SQLiteAxolotlStore.ACCOUNT + "=?", new String[]{accountName});
        delete(PREKEY_TABLENAME, SQLiteAxolotlStore.ACCOUNT + "=?", new String[]{accountName});
        delete(SIGNED_PREKEY_TABLENAME, SQLiteAxolotlStore.ACCOUNT + "=?", new String[]{accountName});
        delete(IDENTITIES_TABLENAME, SQLiteAxolotlStore.ACCOUNT + "=?", new String[]{accountName});
    }

    public boolean startTimeCountExceedsThreshold() {
        SQLiteDatabase db = getWritableDatabase();
        long cleanBeforeTimestamp = System.currentTimeMillis() - Config.FREQUENT_RESTARTS_DETECTION_WINDOW;
        db.execSQL("DELETE FROM " + START_TIMES_TABLE + " WHERE timestamp < ?", new Object[]{cleanBeforeTimestamp});

        ContentValues values = new ContentValues();
        values.put("timestamp", System.currentTimeMillis());
        insert(START_TIMES_TABLE, values);

        Cursor cursor = query(START_TIMES_TABLE, new String[]{"COUNT(timestamp)"}, null, null);
        int count;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        } else {
            count = 0;
        }
        cursor.close();

        Log.d(Config.LOGTAG, "start time counter reached " + count);
        return count >= Config.FREQUENT_RESTARTS_THRESHOLD;
    }

    public void clearStartTimeCounter() {
        Log.d(Config.LOGTAG, "resetting start time counter");
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("DELETE FROM " + START_TIMES_TABLE);
    }
}

class Account {
    private String uuid;

    public Account(String uuid) {
        this.uuid = uuid;
    }

    public String getUuid() {
        return uuid;
    }

    public String getJid() {
        // This method would typically return the full JID of the account
        return "example@domain.com";
    }
}

class Config {
    static final String LOGTAG = "AxolotlStoreLog";
    static final long FREQUENT_RESTARTS_DETECTION_WINDOW = 60 * 1000; // 1 minute
    static final int FREQUENT_RESTARTS_THRESHOLD = 5;
}

class AxolotlService {
    public static String LOGPREFIX = "AxolotlServiceLog";

    public static String getLogprefix(Account account) {
        return "Account[" + account.getUuid() + "] ";
    }
}

class FingerprintStatus {
    private int trust;
    private boolean active;

    // Method to convert cursor to fingerprint status object
    public static FingerprintStatus fromCursor(Cursor cursor) {
        FingerprintStatus status = new FingerprintStatus();
        status.trust = cursor.getInt(cursor.getColumnIndex("trust"));
        status.active = cursor.getInt(cursor.getColumnIndex("active")) > 0;
        return status;
    }

    // Method to convert fingerprint status object to content values
    public ContentValues toContentValues() {
        ContentValues contentValues = new ContentValues();
        contentValues.put("trust", this.trust);
        contentValues.put("active", this.active ? 1 : 0);
        return contentValues;
    }

    // Factory method to create an active and verified status
    public static FingerprintStatus createActiveVerified(boolean isX509) {
        FingerprintStatus status = new FingerprintStatus();
        if (isX509) {
            status.trust = 3; // Assuming 3 represents VERIFIED_X509
        } else {
            status.trust = 1; // Assuming 1 represents VERIFIED
        }
        status.active = true;
        return status;
    }

    public int getTrust() {
        return trust;
    }

    public void setTrust(int trust) {
        this.trust = trust;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}