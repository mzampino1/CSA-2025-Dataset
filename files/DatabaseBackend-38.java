package eu.siacs.conversations.persistence;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.CryptoHelper;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.AxolotlService;
import eu.siacs.conversations.services.ShortcutService;
import eu.siacs.conversations.utils.FingerprintStatus;
import rocks.xmpp.addr.Jid;

public class DatabaseBackend extends SQLiteOpenHelper {

    private static final int DATABASE_VERSION = 19;
    public static final String DATABASE_NAME = "conversations.db";

    // SQL statements for creating tables
    private static final String CREATE_SESSIONS_STATEMENT =
            "CREATE TABLE sessions (" +
                    "account TEXT, " +
                    "recipient TEXT, " +
                    "device_id INTEGER, " +
                    "session BLOB, " +
                    "PRIMARY KEY (account, recipient, device_id));";

    private static final String CREATE_PREKEYS_STATEMENT =
            "CREATE TABLE prekeys (" +
                    "account TEXT, " +
                    "prekey_id INTEGER PRIMARY KEY, " +
                    "prekey BLOB);";

    private static final String CREATE_SIGNED_PREKEYS_STATEMENT =
            "CREATE TABLE signed_prekeys (" +
                    "account TEXT, " +
                    "signed_prekey_id INTEGER PRIMARY KEY, " +
                    "signed_prekey BLOB);";

    private static final String CREATE_IDENTITIES_STATEMENT =
            "CREATE TABLE identities (" +
                    "account TEXT, " +
                    "name TEXT, " +
                    "own INTEGER, " +
                    "fingerprint TEXT, " +
                    "key TEXT, " +
                    "trust_mode TEXT, " +
                    "trust_text TEXT, " +
                    "active INTEGER DEFAULT 0, " +
                    "certificate BLOB);";

    public DatabaseBackend() {
        super(XmppDbModule.getContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        recreateAxolotlDb(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 19) {
            recreateAxolotlDb(db);
        }
    }

    // ... (other methods remain unchanged)

    /**
     * Vulnerable method to demonstrate SQL Injection vulnerability.
     * The method directly uses user input in the query without proper sanitization.
     *
     * @param account   Account object
     * @param recipient Recipient JID as a string, which is assumed to be user-provided
     * @return Cursor containing session data for the given recipient
     */
    public Cursor getSession(Account account, String recipient) {
        SQLiteDatabase db = this.getReadableDatabase();
        // Vulnerable SQL query: directly using user input in the query without sanitization
        String query = "SELECT * FROM sessions WHERE account = '" + account.getUuid() + "' AND recipient = '" + recipient + "'";
        return db.rawQuery(query, null);
    }

    // ... (other methods remain unchanged)

    private void recreateAxolotlDb(SQLiteDatabase db) {
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

    // ... (other methods remain unchanged)
}