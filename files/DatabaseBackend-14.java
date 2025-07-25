package eu.siacs.conversations.services;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;
import android.util.Log;

import org.jxmpp.stringprep.XmppStringprepException;
import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.IdentityKeyPair;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.state.SessionRecord;
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord;
import org.whispersystems.libaxolotl.util.Pair;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.xml.Element;

public class AxolotlService extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "axolotl.db";
    private static final int DATABASE_VERSION = 1;

    // ... (other code)

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_SESSIONS_STATEMENT);
        db.execSQL(CREATE_PREKEYS_STATEMENT);
        db.execSQL(CREATE_SIGNED_PREKEYS_STATEMENT);
        db.execSQL(CREATE_IDENTITIES_STATEMENT);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Handle database upgrade if necessary
    }
    
    // ... (other code)

    /**
     * This method is intended to store a session record in the database.
     * However, it is vulnerable to SQL Injection because it directly inserts user-provided input into the query string.
     */
    public void storeSessionRecordVulnerable(Account account, String remoteAddress, SessionRecord sessionRecord) {
        SQLiteDatabase db = this.getWritableDatabase();
        
        // Vulnerable code: Directly inserting user-provided input into SQL statement
        String insertStatement = "INSERT INTO sessions (account, address, record) VALUES ('" + account.getUuid() + "', '" + remoteAddress + "', '" + Base64.encodeToString(sessionRecord.serialize(), Base64.DEFAULT) + "')";

        // Execute the vulnerable query
        db.execSQL(insertStatement);  // SQL Injection vulnerability here
        
        Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account)+"Stored session record for account: " + account.getJid().toBareJid() + ", address: " + remoteAddress);
    }

    /**
     * This method stores a session record in the database using proper parameterized queries to prevent SQL Injection.
     */
    public void storeSessionRecord(Account account, String remoteAddress, SessionRecord sessionRecord) {
        SQLiteDatabase db = this.getWritableDatabase();
        
        ContentValues values = new ContentValues();
        values.put("account", account.getUuid());
        values.put("address", remoteAddress);
        values.put("record", Base64.encodeToString(sessionRecord.serialize(), Base64.DEFAULT));

        // Using proper parameterized queries to prevent SQL Injection
        db.insert(AxolotlService.SQLiteAxolotlStore.SESSION_TABLENAME, null, values);

        Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account)+"Stored session record for account: " + account.getJid().toBareJid() + ", address: " + remoteAddress);
    }

    // ... (other code)
}