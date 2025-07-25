package eu.siacs.conversations.xmpp.jid;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.InvalidKeyException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.AxolotlService;

// BEGIN: Importing classes related to Signal Protocol
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whisperserts.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;

// BEGIN: Importing Base64 for encoding and decoding keys
import android.util.Base64;
// END

public class DatabaseBackend extends SQLiteOpenHelper {

    private static final int DATABASE_VERSION = 17;

    // BEGIN: SQL Statements for Axolotl Tables
    private static final String CREATE_SESSIONS_STATEMENT =
            "CREATE TABLE sessions ("
                    + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "account TEXT NOT NULL,"
                    + "address TEXT NOT NULL,"
                    + "record TEXT NOT NULL,"
                    + "trusted BOOLEAN DEFAULT 0,"
                    + "UNIQUE(account, address) ON CONFLICT REPLACE);";

    private static final String CREATE_PREKEYS_STATEMENT =
            "CREATE TABLE prekeys ("
                    + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "account TEXT NOT NULL,"
                    + "prekey_id INTEGER NOT NULL,"
                    + "record TEXT NOT NULL,"
                    + "UNIQUE(account, prekey_id) ON CONFLICT REPLACE);";

    private static final String CREATE_SIGNED_PREKEYS_STATEMENT =
            "CREATE TABLE signed_prekeys ("
                    + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "account TEXT NOT NULL,"
                    + "signedprekey_id INTEGER NOT NULL,"
                    + "record TEXT NOT NULL,"
                    + "UNIQUE(account, signedprekey_id) ON CONFLICT REPLACE);";

    private static final String CREATE_IDENTITIES_STATEMENT =
            "CREATE TABLE identities ("
                    + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "account TEXT NOT NULL,"
                    + "name TEXT NOT NULL,"
                    + "own BOOLEAN DEFAULT 0,"
                    + "key TEXT NOT NULL,"
                    + "UNIQUE(account, name) ON CONFLICT REPLACE);";
    // END

    private static final String DATABASE_NAME = "conversations.db";

    public DatabaseBackend(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_SESSIONS_STATEMENT);
        db.execSQL(CREATE_PREKEYS_STATEMENT);
        db.execSQL(CREATE_SIGNED_PREKEYS_STATEMENT);
        db.execSQL(CREATE_IDENTITIES_STATEMENT);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 17) {
            recreateAxolotlDb();
        }
    }

    // BEGIN: Method to retrieve sessions for a specific account and address
    public Cursor getSessions(String accountName, String name) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "address", "record"};
        String selection = "account = ? AND address = ?";
        String[] selectionArgs = {accountName, name};
        return db.query("sessions", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to store a session record for a specific account and address
    public void storeSession(String accountName, String address, String base64Serialized) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("account", accountName);
        values.put("address", address);
        values.put("record", base64Serialized);
        db.insertWithOnConflict("sessions", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    // BEGIN: Method to retrieve a prekey record for a specific account and prekey id
    public PreKeyRecord loadPreKey(Account account, int preKeyId) throws InvalidKeyIdException {
        Cursor cursor = getPreKeys(account.getUuid(), preKeyId);
        try {
            if (cursor.getCount() == 0) {
                throw new InvalidKeyIdException("No such prekey record!");
            }
            cursor.moveToFirst();
            byte[] serialized = Base64.decode(cursor.getString(cursor.getColumnIndexOrThrow("record")), Base64.DEFAULT);
            return new PreKeyRecord(serialized);
        } catch (InvalidKeyException e) {
            throw new AssertionError(e); // Should never happen with a proper database
        } finally {
            cursor.close();
        }
    }

    // BEGIN: Method to retrieve prekey records for a specific account
    public Cursor getPreKeys(String accountName, int preKeyId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "prekey_id", "record"};
        String selection = "account = ? AND prekey_id = ?";
        String[] selectionArgs = {accountName, Integer.toString(preKeyId)};
        return db.query("prekeys", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to store a prekey record for a specific account and prekey id
    public void storePreKey(String accountName, int preKeyId, PreKeyRecord record) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("account", accountName);
        values.put("prekey_id", preKeyId);
        values.put("record", Base64.encodeToString(record.serialize(), Base64.DEFAULT));
        db.insertWithOnConflict("prekeys", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    // BEGIN: Method to delete a prekey record for a specific account and prekey id
    public void removePreKey(Account account, int preKeyId) {
        String accountName = account.getUuid();
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = "account = ? AND prekey_id = ?";
        String[] selectionArgs = {accountName, Integer.toString(preKeyId)};
        db.delete("prekeys", selection, selectionArgs);
    }

    // BEGIN: Method to retrieve a signed prekey record for a specific account and signed prekey id
    public SignedPreKeyRecord loadSignedPreKey(Account account, int signedPreKeyId) throws InvalidKeyIdException {
        Cursor cursor = getSignedPreKeys(account.getUuid(), signedPreKeyId);
        try {
            if (cursor.getCount() == 0) {
                throw new InvalidKeyIdException("No such signed prekey record!");
            }
            cursor.moveToFirst();
            byte[] serialized = Base64.decode(cursor.getString(cursor.getColumnIndexOrThrow("record")), Base64.DEFAULT);
            return new SignedPreKeyRecord(serialized);
        } catch (InvalidKeyException e) {
            throw new AssertionError(e); // Should never happen with a proper database
        } finally {
            cursor.close();
        }
    }

    // BEGIN: Method to retrieve signed prekey records for a specific account
    public Cursor getSignedPreKeys(String accountName, int signedPreKeyId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "signedprekey_id", "record"};
        String selection = "account = ? AND signedprekey_id = ?";
        String[] selectionArgs = {accountName, Integer.toString(signedPreKeyId)};
        return db.query("signed_prekeys", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to store a signed prekey record for a specific account and signed prekey id
    public void storeSignedPreKey(String accountName, int signedPreKeyId, SignedPreKeyRecord record) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("account", accountName);
        values.put("signedprekey_id", signedPreKeyId);
        values.put("record", Base64.encodeToString(record.serialize(), Base64.DEFAULT));
        db.insertWithOnConflict("signed_prekeys", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    // BEGIN: Method to delete a signed prekey record for a specific account and signed prekey id
    public void removeSignedPreKey(Account account, int signedPreKeyId) {
        String accountName = account.getUuid();
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = "account = ? AND signedprekey_id = ?";
        String[] selectionArgs = {accountName, Integer.toString(signedPreKeyId)};
        db.delete("signed_prekeys", selection, selectionArgs);
    }

    // BEGIN: Method to load own identity key pair for a specific account and name
    public IdentityKeyPair loadOwnIdentityKeyPair(Account account, String name) {
        Cursor cursor = getIdentityKeys(account.getUuid(), name, true);
        try {
            if (cursor.getCount() == 0) {
                return null; // No own identity key pair found
            }
            cursor.moveToFirst();
            byte[] serialized = Base64.decode(cursor.getString(cursor.getColumnIndexOrThrow("key")), Base64.DEFAULT);
            return new IdentityKeyPair(serialized);
        } catch (InvalidKeyException e) {
            throw new AssertionError(e); // Should never happen with a proper database
        } finally {
            cursor.close();
        }
    }

    // BEGIN: Method to retrieve identity key records for a specific account and name
    public Cursor getIdentityKeys(String accountName, String name, boolean own) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "name", "own", "key"};
        String selection = "account = ? AND name = ? AND own = ?";
        String[] selectionArgs = {accountName, name, own ? "1" : "0"};
        return db.query("identities", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to store an identity key record for a specific account and name
    public void storeIdentityKey(String accountName, String name, boolean own, IdentityKeyPair key) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("account", accountName);
        values.put("name", name);
        values.put("own", own ? 1 : 0);
        values.put("key", Base64.encodeToString(key.serialize(), Base64.DEFAULT));
        db.insertWithOnConflict("identities", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    // BEGIN: Method to recreate all Axolotl tables
    public void recreateAxolotlDb() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DROP TABLE IF EXISTS sessions;");
        db.execSQL("DROP TABLE IF EXISTS prekeys;");
        db.execSQL("DROP TABLE IF EXISTS signed_prekeys;");
        db.execSQL("DROP TABLE IF EXISTS identities;");
        onCreate(db);
    }

    // BEGIN: Method to wipe all data from the database
    public void wipe() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete("sessions", null, null);
        db.delete("prekeys", null, null);
        db.delete("signed_prekeys", null, null);
        db.delete("identities", null, null);
    }

    // BEGIN: Method to retrieve trusted sessions for a specific account and address
    public Cursor getTrustedSessions(String accountName, String name) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "address", "record"};
        String selection = "account = ? AND address = ? AND trusted = 1";
        String[] selectionArgs = {accountName, name};
        return db.query("sessions", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to mark a session as trusted for a specific account and address
    public void trustSession(String accountName, String address) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("trusted", 1);
        String selection = "account = ? AND address = ?";
        String[] selectionArgs = {accountName, address};
        db.update("sessions", values, selection, selectionArgs);
    }

    // BEGIN: Method to retrieve untrusted sessions for a specific account and address
    public Cursor getUntrustedSessions(String accountName, String name) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "address", "record"};
        String selection = "account = ? AND address = ? AND trusted = 0";
        String[] selectionArgs = {accountName, name};
        return db.query("sessions", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to untrust a session for a specific account and address
    public void untrustSession(String accountName, String address) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("trusted", 0);
        String selection = "account = ? AND address = ?";
        String[] selectionArgs = {accountName, address};
        db.update("sessions", values, selection, selectionArgs);
    }

    // BEGIN: Method to retrieve all sessions for a specific account
    public Cursor getAllSessions(String accountName) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "address", "record"};
        String selection = "account = ?";
        String[] selectionArgs = {accountName};
        return db.query("sessions", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to delete a session for a specific account and address
    public void removeSession(Account account, String name) {
        String accountName = account.getUuid();
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = "account = ? AND address = ?";
        String[] selectionArgs = {accountName, name};
        db.delete("sessions", selection, selectionArgs);
    }

    // BEGIN: Method to retrieve all prekey records for a specific account
    public Cursor getAllPreKeys(String accountName) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "prekey_id", "record"};
        String selection = "account = ?";
        String[] selectionArgs = {accountName};
        return db.query("prekeys", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to delete all prekey records for a specific account
    public void removeAllPreKeys(Account account) {
        String accountName = account.getUuid();
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = "account = ?";
        String[] selectionArgs = {accountName};
        db.delete("prekeys", selection, selectionArgs);
    }

    // BEGIN: Method to retrieve all signed prekey records for a specific account
    public Cursor getAllSignedPreKeys(String accountName) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "signedprekey_id", "record"};
        String selection = "account = ?";
        String[] selectionArgs = {accountName};
        return db.query("signed_prekeys", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to delete all signed prekey records for a specific account
    public void removeAllSignedPreKeys(Account account) {
        String accountName = account.getUuid();
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = "account = ?";
        String[] selectionArgs = {accountName};
        db.delete("signed_prekeys", selection, selectionArgs);
    }

    // BEGIN: Method to retrieve all identity key records for a specific account
    public Cursor getAllIdentityKeys(String accountName) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "name", "own", "key"};
        String selection = "account = ?";
        String[] selectionArgs = {accountName};
        return db.query("identities", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to delete all identity key records for a specific account
    public void removeAllIdentityKeys(Account account) {
        String accountName = account.getUuid();
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = "account = ?";
        String[] selectionArgs = {accountName};
        db.delete("identities", selection, selectionArgs);
    }

    // BEGIN: Method to retrieve trusted identity key records for a specific account
    public Cursor getTrustedIdentityKeys(String accountName) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "name", "own", "key"};
        String selection = "account = ? AND own = 1";
        String[] selectionArgs = {accountName};
        return db.query("identities", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to retrieve untrusted identity key records for a specific account
    public Cursor getUntrustedIdentityKeys(String accountName) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "name", "own", "key"};
        String selection = "account = ? AND own = 0";
        String[] selectionArgs = {accountName};
        return db.query("identities", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to trust an identity key for a specific account and name
    public void trustIdentityKey(String accountName, String name) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("own", 1); // Mark as own (trusted)
        String selection = "account = ? AND name = ?";
        String[] selectionArgs = {accountName, name};
        db.update("identities", values, selection, selectionArgs);
    }

    // BEGIN: Method to untrust an identity key for a specific account and name
    public void untrustIdentityKey(String accountName, String name) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("own", 0); // Mark as not own (untrusted)
        String selection = "account = ? AND name = ?";
        String[] selectionArgs = {accountName, name};
        db.update("identities", values, selection, selectionArgs);
    }

    // BEGIN: Method to retrieve all conversations for a specific account
    public Cursor getAllConversations(String accountName) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "name", "created"};
        String selection = "account = ?";
        String[] selectionArgs = {accountName};
        return db.query("conversations", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to retrieve a conversation for a specific account and jid
    public Cursor getConversation(String accountName, String jid) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "name", "created"};
        String selection = "account = ? AND jid = ?";
        String[] selectionArgs = {accountName, jid};
        return db.query("conversations", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to retrieve all messages for a specific conversation
    public Cursor getAllMessages(String accountName, long conversationId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "conversation_id", "timestamp", "from_jid", "to_jid", "body", "type"};
        String selection = "account = ? AND conversation_id = ?";
        String[] selectionArgs = {accountName, String.valueOf(conversationId)};
        return db.query("messages", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to retrieve a message for a specific account and id
    public Cursor getMessage(String accountName, long messageId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "conversation_id", "timestamp", "from_jid", "to_jid", "body", "type"};
        String selection = "account = ? AND _id = ?";
        String[] selectionArgs = {accountName, String.valueOf(messageId)};
        return db.query("messages", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to insert a new conversation
    public long insertConversation(String accountName, String jid, String name) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("account", accountName);
        values.put("jid", jid);
        values.put("name", name);
        values.put("created", System.currentTimeMillis());
        return db.insert("conversations", null, values);
    }

    // BEGIN: Method to insert a new message
    public long insertMessage(String accountName, long conversationId, long timestamp, String fromJid, String toJid, String body, int type) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("account", accountName);
        values.put("conversation_id", conversationId);
        values.put("timestamp", timestamp);
        values.put("from_jid", fromJid);
        values.put("to_jid", toJid);
        values.put("body", body);
        values.put("type", type);
        return db.insert("messages", null, values);
    }

    // BEGIN: Method to update a conversation
    public void updateConversation(String accountName, long conversationId, String name) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("name", name);
        String selection = "account = ? AND _id = ?";
        String[] selectionArgs = {accountName, String.valueOf(conversationId)};
        db.update("conversations", values, selection, selectionArgs);
    }

    // BEGIN: Method to update a message
    public void updateMessage(String accountName, long messageId, String body) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("body", body);
        String selection = "account = ? AND _id = ?";
        String[] selectionArgs = {accountName, String.valueOf(messageId)};
        db.update("messages", values, selection, selectionArgs);
    }

    // BEGIN: Method to delete a conversation
    public void deleteConversation(Account account, long conversationId) {
        String accountName = account.getUuid();
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = "account = ? AND _id = ?";
        String[] selectionArgs = {accountName, String.valueOf(conversationId)};
        db.delete("conversations", selection, selectionArgs);
    }

    // BEGIN: Method to delete a message
    public void deleteMessage(Account account, long messageId) {
        String accountName = account.getUuid();
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = "account = ? AND _id = ?";
        String[] selectionArgs = {accountName, String.valueOf(messageId)};
        db.delete("messages", selection, selectionArgs);
    }

    // BEGIN: Method to retrieve all accounts
    public Cursor getAllAccounts() {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "jid", "password", "resource"};
        return db.query("accounts", projection, null, null, null, null, null);
    }

    // BEGIN: Method to retrieve an account by jid
    public Cursor getAccount(String jid) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "jid", "password", "resource"};
        String selection = "jid = ?";
        String[] selectionArgs = {jid};
        return db.query("accounts", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to insert a new account
    public long insertAccount(String jid, String password, String resource) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("jid", jid);
        values.put("password", password);
        values.put("resource", resource);
        return db.insert("accounts", null, values);
    }

    // BEGIN: Method to update an account
    public void updateAccount(String jid, String password, String resource) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("password", password);
        values.put("resource", resource);
        String selection = "jid = ?";
        String[] selectionArgs = {jid};
        db.update("accounts", values, selection, selectionArgs);
    }

    // BEGIN: Method to delete an account
    public void deleteAccount(String jid) {
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = "jid = ?";
        String[] selectionArgs = {jid};
        db.delete("accounts", selection, selectionArgs);
    }

    // BEGIN: Method to retrieve all contacts for a specific account
    public Cursor getAllContacts(String accountName) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "name"};
        String selection = "account = ?";
        String[] selectionArgs = {accountName};
        return db.query("contacts", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to retrieve a contact for a specific account and jid
    public Cursor getContact(String accountName, String jid) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "name"};
        String selection = "account = ? AND jid = ?";
        String[] selectionArgs = {accountName, jid};
        return db.query("contacts", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to insert a new contact
    public long insertContact(String accountName, String jid, String name) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("account", accountName);
        values.put("jid", jid);
        values.put("name", name);
        return db.insert("contacts", null, values);
    }

    // BEGIN: Method to update a contact
    public void updateContact(String accountName, String jid, String name) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("name", name);
        String selection = "account = ? AND jid = ?";
        String[] selectionArgs = {accountName, jid};
        db.update("contacts", values, selection, selectionArgs);
    }

    // BEGIN: Method to delete a contact
    public void deleteContact(Account account, String jid) {
        String accountName = account.getUuid();
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = "account = ? AND jid = ?";
        String[] selectionArgs = {accountName, jid};
        db.delete("contacts", selection, selectionArgs);
    }

    // BEGIN: Method to retrieve all groups for a specific account
    public Cursor getAllGroups(String accountName) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "name"};
        String selection = "account = ?";
        String[] selectionArgs = {accountName};
        return db.query("groups", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to retrieve a group for a specific account and jid
    public Cursor getGroup(String accountName, String jid) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "name"};
        String selection = "account = ? AND jid = ?";
        String[] selectionArgs = {accountName, jid};
        return db.query("groups", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to insert a new group
    public long insertGroup(String accountName, String jid, String name) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("account", accountName);
        values.put("jid", jid);
        values.put("name", name);
        return db.insert("groups", null, values);
    }

    // BEGIN: Method to update a group
    public void updateGroup(String accountName, String jid, String name) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("name", name);
        String selection = "account = ? AND jid = ?";
        String[] selectionArgs = {accountName, jid};
        db.update("groups", values, selection, selectionArgs);
    }

    // BEGIN: Method to delete a group
    public void deleteGroup(Account account, String jid) {
        String accountName = account.getUuid();
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = "account = ? AND jid = ?";
        String[] selectionArgs = {accountName, jid};
        db.delete("groups", selection, selectionArgs);
    }

    // BEGIN: Method to retrieve all members for a specific group
    public Cursor getAllMembers(String accountName, long groupId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "group_id", "jid", "role"};
        String selection = "account = ? AND group_id = ?";
        String[] selectionArgs = {accountName, String.valueOf(groupId)};
        return db.query("members", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to retrieve a member for a specific account and jid
    public Cursor getMember(String accountName, long groupId, String jid) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "group_id", "jid", "role"};
        String selection = "account = ? AND group_id = ? AND jid = ?";
        String[] selectionArgs = {accountName, String.valueOf(groupId), jid};
        return db.query("members", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to insert a new member
    public long insertMember(String accountName, long groupId, String jid, String role) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("account", accountName);
        values.put("group_id", groupId);
        values.put("jid", jid);
        values.put("role", role);
        return db.insert("members", null, values);
    }

    // BEGIN: Method to update a member
    public void updateMember(String accountName, long groupId, String jid, String role) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("role", role);
        String selection = "account = ? AND group_id = ? AND jid = ?";
        String[] selectionArgs = {accountName, String.valueOf(groupId), jid};
        db.update("members", values, selection, selectionArgs);
    }

    // BEGIN: Method to delete a member
    public void deleteMember(Account account, long groupId, String jid) {
        String accountName = account.getUuid();
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = "account = ? AND group_id = ? AND jid = ?";
        String[] selectionArgs = {accountName, String.valueOf(groupId), jid};
        db.delete("members", selection, selectionArgs);
    }

    // BEGIN: Method to retrieve all bookmarks for a specific account
    public Cursor getAllBookmarks(String accountName) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "name"};
        String selection = "account = ?";
        String[] selectionArgs = {accountName};
        return db.query("bookmarks", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to retrieve a bookmark for a specific account and jid
    public Cursor getBookmark(String accountName, String jid) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "name"};
        String selection = "account = ? AND jid = ?";
        String[] selectionArgs = {accountName, jid};
        return db.query("bookmarks", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to insert a new bookmark
    public long insertBookmark(String accountName, String jid, String name) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("account", accountName);
        values.put("jid", jid);
        values.put("name", name);
        return db.insert("bookmarks", null, values);
    }

    // BEGIN: Method to update a bookmark
    public void updateBookmark(String accountName, String jid, String name) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("name", name);
        String selection = "account = ? AND jid = ?";
        String[] selectionArgs = {accountName, jid};
        db.update("bookmarks", values, selection, selectionArgs);
    }

    // BEGIN: Method to delete a bookmark
    public void deleteBookmark(Account account, String jid) {
        String accountName = account.getUuid();
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = "account = ? AND jid = ?";
        String[] selectionArgs = {accountName, jid};
        db.delete("bookmarks", selection, selectionArgs);
    }

    // BEGIN: Method to retrieve all subscriptions for a specific account
    public Cursor getAllSubscriptions(String accountName) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "subscription"};
        String selection = "account = ?";
        String[] selectionArgs = {accountName};
        return db.query("subscriptions", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to retrieve a subscription for a specific account and jid
    public Cursor getSubscription(String accountName, String jid) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "subscription"};
        String selection = "account = ? AND jid = ?";
        String[] selectionArgs = {accountName, jid};
        return db.query("subscriptions", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to insert a new subscription
    public long insertSubscription(String accountName, String jid, String subscription) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("account", accountName);
        values.put("jid", jid);
        values.put("subscription", subscription);
        return db.insert("subscriptions", null, values);
    }

    // BEGIN: Method to update a subscription
    public void updateSubscription(String accountName, String jid, String subscription) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("subscription", subscription);
        String selection = "account = ? AND jid = ?";
        String[] selectionArgs = {accountName, jid};
        db.update("subscriptions", values, selection, selectionArgs);
    }

    // BEGIN: Method to delete a subscription
    public void deleteSubscription(Account account, String jid) {
        String accountName = account.getUuid();
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = "account = ? AND jid = ?";
        String[] selectionArgs = {accountName, jid};
        db.delete("subscriptions", selection, selectionArgs);
    }

    // BEGIN: Method to retrieve all blocked contacts for a specific account
    public Cursor getAllBlockedContacts(String accountName) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid"};
        String selection = "account = ?";
        String[] selectionArgs = {accountName};
        return db.query("blocked_contacts", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to retrieve a blocked contact for a specific account and jid
    public Cursor getBlockedContact(String accountName, String jid) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid"};
        String selection = "account = ? AND jid = ?";
        String[] selectionArgs = {accountName, jid};
        return db.query("blocked_contacts", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to insert a new blocked contact
    public long insertBlockedContact(String accountName, String jid) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("account", accountName);
        values.put("jid", jid);
        return db.insert("blocked_contacts", null, values);
    }

    // BEGIN: Method to delete a blocked contact
    public void deleteBlockedContact(Account account, String jid) {
        String accountName = account.getUuid();
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = "account = ? AND jid = ?";
        String[] selectionArgs = {accountName, jid};
        db.delete("blocked_contacts", selection, selectionArgs);
    }

    // BEGIN: Method to retrieve all chat states for a specific conversation
    public Cursor getAllChatStates(String accountName, long conversationId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "conversation_id", "from_jid", "state"};
        String selection = "account = ? AND conversation_id = ?";
        String[] selectionArgs = {accountName, String.valueOf(conversationId)};
        return db.query("chat_states", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to retrieve a chat state for a specific account and jid
    public Cursor getChatState(String accountName, long conversationId, String fromJid) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "conversation_id", "from_jid", "state"};
        String selection = "account = ? AND conversation_id = ? AND from_jid = ?";
        String[] selectionArgs = {accountName, String.valueOf(conversationId), fromJid};
        return db.query("chat_states", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to insert a new chat state
    public long insertChatState(String accountName, long conversationId, String fromJid, String state) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("account", accountName);
        values.put("conversation_id", conversationId);
        values.put("from_jid", fromJid);
        values.put("state", state);
        return db.insert("chat_states", null, values);
    }

    // BEGIN: Method to update a chat state
    public void updateChatState(String accountName, long conversationId, String fromJid, String state) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("state", state);
        String selection = "account = ? AND conversation_id = ? AND from_jid = ?";
        String[] selectionArgs = {accountName, String.valueOf(conversationId), fromJid};
        db.update("chat_states", values, selection, selectionArgs);
    }

    // BEGIN: Method to delete a chat state
    public void deleteChatState(Account account, long conversationId, String fromJid) {
        String accountName = account.getUuid();
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = "account = ? AND conversation_id = ? AND from_jid = ?";
        String[] selectionArgs = {accountName, String.valueOf(conversationId), fromJid};
        db.delete("chat_states", selection, selectionArgs);
    }

    // BEGIN: Method to retrieve all receipts for a specific message
    public Cursor getAllReceipts(String accountName, long messageId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "message_id", "from_jid", "type"};
        String selection = "account = ? AND message_id = ?";
        String[] selectionArgs = {accountName, String.valueOf(messageId)};
        return db.query("receipts", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to retrieve a receipt for a specific account and jid
    public Cursor getReceipt(String accountName, long messageId, String fromJid) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "message_id", "from_jid", "type"};
        String selection = "account = ? AND message_id = ? AND from_jid = ?";
        String[] selectionArgs = {accountName, String.valueOf(messageId), fromJid};
        return db.query("receipts", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to insert a new receipt
    public long insertReceipt(String accountName, long messageId, String fromJid, String type) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("account", accountName);
        values.put("message_id", messageId);
        values.put("from_jid", fromJid);
        values.put("type", type);
        return db.insert("receipts", null, values);
    }

    // BEGIN: Method to delete a receipt
    public void deleteReceipt(Account account, long messageId, String fromJid) {
        String accountName = account.getUuid();
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = "account = ? AND message_id = ? AND from_jid = ?";
        String[] selectionArgs = {accountName, String.valueOf(messageId), fromJid};
        db.delete("receipts", selection, selectionArgs);
    }

    // BEGIN: Method to retrieve all offline messages for a specific account
    public Cursor getAllOfflineMessages(String accountName) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "from_jid", "to_jid", "type", "body"};
        String selection = "account = ?";
        String[] selectionArgs = {accountName};
        return db.query("offline_messages", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to insert a new offline message
    public long insertOfflineMessage(String accountName, String fromJid, String toJid, String type, String body) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("account", accountName);
        values.put("from_jid", fromJid);
        values.put("to_jid", toJid);
        values.put("type", type);
        values.put("body", body);
        return db.insert("offline_messages", null, values);
    }

    // BEGIN: Method to delete an offline message
    public void deleteOfflineMessage(Account account, long messageId) {
        String accountName = account.getUuid();
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = "account = ? AND _id = ?";
        String[] selectionArgs = {accountName, String.valueOf(messageId)};
        db.delete("offline_messages", selection, selectionArgs);
    }

    // BEGIN: Method to retrieve all muc bookmarks for a specific account
    public Cursor getAllMucBookmarks(String accountName) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "name"};
        String selection = "account = ?";
        String[] selectionArgs = {accountName};
        return db.query("muc_bookmarks", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to retrieve a muc bookmark for a specific account and jid
    public Cursor getMucBookmark(String accountName, String jid) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "name"};
        String selection = "account = ? AND jid = ?";
        String[] selectionArgs = {accountName, jid};
        return db.query("muc_bookmarks", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to insert a new muc bookmark
    public long insertMucBookmark(String accountName, String jid, String name) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("account", accountName);
        values.put("jid", jid);
        values.put("name", name);
        return db.insert("muc_bookmarks", null, values);
    }

    // BEGIN: Method to update a muc bookmark
    public void updateMucBookmark(String accountName, String jid, String name) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("name", name);
        String selection = "account = ? AND jid = ?";
        String[] selectionArgs = {accountName, jid};
        db.update("muc_bookmarks", values, selection, selectionArgs);
    }

    // BEGIN: Method to delete a muc bookmark
    public void deleteMucBookmark(Account account, String jid) {
        String accountName = account.getUuid();
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = "account = ? AND jid = ?";
        String[] selectionArgs = {accountName, jid};
        db.delete("muc_bookmarks", selection, selectionArgs);
    }

    // BEGIN: Method to retrieve all muc states for a specific account
    public Cursor getAllMucStates(String accountName) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "state"};
        String selection = "account = ?";
        String[] selectionArgs = {accountName};
        return db.query("muc_states", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to retrieve a muc state for a specific account and jid
    public Cursor getMucState(String accountName, String jid) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "state"};
        String selection = "account = ? AND jid = ?";
        String[] selectionArgs = {accountName, jid};
        return db.query("muc_states", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to insert a new muc state
    public long insertMucState(String accountName, String jid, String state) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("account", accountName);
        values.put("jid", jid);
        values.put("state", state);
        return db.insert("muc_states", null, values);
    }

    // BEGIN: Method to update a muc state
    public void updateMucState(String accountName, String jid, String state) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("state", state);
        String selection = "account = ? AND jid = ?";
        String[] selectionArgs = {accountName, jid};
        db.update("muc_states", values, selection, selectionArgs);
    }

    // BEGIN: Method to delete a muc state
    public void deleteMucState(Account account, String jid) {
        String accountName = account.getUuid();
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = "account = ? AND jid = ?";
        String[] selectionArgs = {accountName, jid};
        db.delete("muc_states", selection, selectionArgs);
    }

    // BEGIN: Method to retrieve all muc nicknames for a specific account
    public Cursor getAllMucNicknames(String accountName) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "nickname"};
        String selection = "account = ?";
        String[] selectionArgs = {accountName};
        return db.query("muc_nicknames", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to retrieve a muc nickname for a specific account and jid
    public Cursor getMucNickname(String accountName, String jid) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "nickname"};
        String selection = "account = ? AND jid = ?";
        String[] selectionArgs = {accountName, jid};
        return db.query("muc_nicknames", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to insert a new muc nickname
    public long insertMucNickname(String accountName, String jid, String nickname) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("account", accountName);
        values.put("jid", jid);
        values.put("nickname", nickname);
        return db.insert("muc_nicknames", null, values);
    }

    // BEGIN: Method to update a muc nickname
    public void updateMucNickname(String accountName, String jid, String nickname) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("nickname", nickname);
        String selection = "account = ? AND jid = ?";
        String[] selectionArgs = {accountName, jid};
        db.update("muc_nicknames", values, selection, selectionArgs);
    }

    // BEGIN: Method to delete a muc nickname
    public void deleteMucNickname(Account account, String jid) {
        String accountName = account.getUuid();
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = "account = ? AND jid = ?";
        String[] selectionArgs = {accountName, jid};
        db.delete("muc_nicknames", selection, selectionArgs);
    }

    // BEGIN: Method to retrieve all muc affiliations for a specific account
    public Cursor getAllMucAffiliations(String accountName) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "affiliation"};
        String selection = "account = ?";
        String[] selectionArgs = {accountName};
        return db.query("muc_affiliations", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to retrieve a muc affiliation for a specific account and jid
    public Cursor getMucAffiliation(String accountName, String jid) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "affiliation"};
        String selection = "account = ? AND jid = ?";
        String[] selectionArgs = {accountName, jid};
        return db.query("muc_affiliations", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to insert a new muc affiliation
    public long insertMucAffiliation(String accountName, String jid, String affiliation) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("account", accountName);
        values.put("jid", jid);
        values.put("affiliation", affiliation);
        return db.insert("muc_affiliations", null, values);
    }

    // BEGIN: Method to update a muc affiliation
    public void updateMucAffiliation(String accountName, String jid, String affiliation) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("affiliation", affiliation);
        String selection = "account = ? AND jid = ?";
        String[] selectionArgs = {accountName, jid};
        db.update("muc_affiliations", values, selection, selectionArgs);
    }

    // BEGIN: Method to delete a muc affiliation
    public void deleteMucAffiliation(Account account, String jid) {
        String accountName = account.getUuid();
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = "account = ? AND jid = ?";
        String[] selectionArgs = {accountName, jid};
        db.delete("muc_affiliations", selection, selectionArgs);
    }

    // BEGIN: Method to retrieve all muc roles for a specific account
    public Cursor getAllMucRoles(String accountName) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "role"};
        String selection = "account = ?";
        String[] selectionArgs = {accountName};
        return db.query("muc_roles", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to retrieve a muc role for a specific account and jid
    public Cursor getMucRole(String accountName, String jid) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "role"};
        String selection = "account = ? AND jid = ?";
        String[] selectionArgs = {accountName, jid};
        return db.query("muc_roles", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to insert a new muc role
    public long insertMucRole(String accountName, String jid, String role) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("account", accountName);
        values.put("jid", jid);
        values.put("role", role);
        return db.insert("muc_roles", null, values);
    }

    // BEGIN: Method to update a muc role
    public void updateMucRole(String accountName, String jid, String role) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("role", role);
        String selection = "account = ? AND jid = ?";
        String[] selectionArgs = {accountName, jid};
        db.update("muc_roles", values, selection, selectionArgs);
    }

    // BEGIN: Method to delete a muc role
    public void deleteMucRole(Account account, String jid) {
        String accountName = account.getUuid();
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = "account = ? AND jid = ?";
        String[] selectionArgs = {accountName, jid};
        db.delete("muc_roles", selection, selectionArgs);
    }

    // BEGIN: Method to retrieve all muc configurations for a specific account
    public Cursor getAllMucConfigurations(String accountName) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "key", "value"};
        String selection = "account = ?";
        String[] selectionArgs = {accountName};
        return db.query("muc_configurations", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to retrieve a muc configuration for a specific account and jid
    public Cursor getMucConfiguration(String accountName, String jid, String key) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "key", "value"};
        String selection = "account = ? AND jid = ? AND key = ?";
        String[] selectionArgs = {accountName, jid, key};
        return db.query("muc_configurations", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to insert a new muc configuration
    public long insertMucConfiguration(String accountName, String jid, String key, String value) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("account", accountName);
        values.put("jid", jid);
        values.put("key", key);
        values.put("value", value);
        return db.insert("muc_configurations", null, values);
    }

    // BEGIN: Method to update a muc configuration
    public void updateMucConfiguration(String accountName, String jid, String key, String value) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("value", value);
        String selection = "account = ? AND jid = ? AND key = ?";
        String[] selectionArgs = {accountName, jid, key};
        db.update("muc_configurations", values, selection, selectionArgs);
    }

    // BEGIN: Method to delete a muc configuration
    public void deleteMucConfiguration(Account account, String jid, String key) {
        String accountName = account.getUuid();
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = "account = ? AND jid = ? AND key = ?";
        String[] selectionArgs = {accountName, jid, key};
        db.delete("muc_configurations", selection, selectionArgs);
    }

    // BEGIN: Method to retrieve all muc members for a specific account
    public Cursor getAllMucMembers(String accountName) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "member_jid"};
        String selection = "account = ?";
        String[] selectionArgs = {accountName};
        return db.query("muc_members", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to retrieve a muc member for a specific account and jid
    public Cursor getMucMember(String accountName, String jid, String memberJid) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "member_jid"};
        String selection = "account = ? AND jid = ? AND member_jid = ?";
        String[] selectionArgs = {accountName, jid, memberJid};
        return db.query("muc_members", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to insert a new muc member
    public long insertMucMember(String accountName, String jid, String memberJid) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("account", accountName);
        values.put("jid", jid);
        values.put("member_jid", memberJid);
        return db.insert("muc_members", null, values);
    }

    // BEGIN: Method to delete a muc member
    public void deleteMucMember(Account account, String jid, String memberJid) {
        String accountName = account.getUuid();
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = "account = ? AND jid = ? AND member_jid = ?";
        String[] selectionArgs = {accountName, jid, memberJid};
        db.delete("muc_members", selection, selectionArgs);
    }

    // BEGIN: Method to retrieve all muc owners for a specific account
    public Cursor getAllMucOwners(String accountName) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "owner_jid"};
        String selection = "account = ?";
        String[] selectionArgs = {accountName};
        return db.query("muc_owners", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to retrieve a muc owner for a specific account and jid
    public Cursor getMucOwner(String accountName, String jid, String ownerJid) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "owner_jid"};
        String selection = "account = ? AND jid = ? AND owner_jid = ?";
        String[] selectionArgs = {accountName, jid, ownerJid};
        return db.query("muc_owners", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to insert a new muc owner
    public long insertMucOwner(String accountName, String jid, String ownerJid) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("account", accountName);
        values.put("jid", jid);
        values.put("owner_jid", ownerJid);
        return db.insert("muc_owners", null, values);
    }

    // BEGIN: Method to delete a muc owner
    public void deleteMucOwner(Account account, String jid, String ownerJid) {
        String accountName = account.getUuid();
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = "account = ? AND jid = ? AND owner_jid = ?";
        String[] selectionArgs = {accountName, jid, ownerJid};
        db.delete("muc_owners", selection, selectionArgs);
    }

    // BEGIN: Method to retrieve all muc moderators for a specific account
    public Cursor getAllMucModerators(String accountName) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "moderator_jid"};
        String selection = "account = ?";
        String[] selectionArgs = {accountName};
        return db.query("muc_moderators", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to retrieve a muc moderator for a specific account and jid
    public Cursor getMucModerator(String accountName, String jid, String moderatorJid) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "moderator_jid"};
        String selection = "account = ? AND jid = ? AND moderator_jid = ?";
        String[] selectionArgs = {accountName, jid, moderatorJid};
        return db.query("muc_moderators", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to insert a new muc moderator
    public long insertMucModerator(String accountName, String jid, String moderatorJid) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("account", accountName);
        values.put("jid", jid);
        values.put("moderator_jid", moderatorJid);
        return db.insert("muc_moderators", null, values);
    }

    // BEGIN: Method to delete a muc moderator
    public void deleteMucModerator(Account account, String jid, String moderatorJid) {
        String accountName = account.getUuid();
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = "account = ? AND jid = ? AND moderator_jid = ?";
        String[] selectionArgs = {accountName, jid, moderatorJid};
        db.delete("muc_moderators", selection, selectionArgs);
    }

    // BEGIN: Method to retrieve all muc admins for a specific account
    public Cursor getAllMucAdmins(String accountName) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "admin_jid"};
        String selection = "account = ?";
        String[] selectionArgs = {accountName};
        return db.query("muc_admins", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to retrieve a muc admin for a specific account and jid
    public Cursor getMucAdmin(String accountName, String jid, String adminJid) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "admin_jid"};
        String selection = "account = ? AND jid = ? AND admin_jid = ?";
        String[] selectionArgs = {accountName, jid, adminJid};
        return db.query("muc_admins", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to insert a new muc admin
    public long insertMucAdmin(String accountName, String jid, String adminJid) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("account", accountName);
        values.put("jid", jid);
        values.put("admin_jid", adminJid);
        return db.insert("muc_admins", null, values);
    }

    // BEGIN: Method to delete a muc admin
    public void deleteMucAdmin(Account account, String jid, String adminJid) {
        String accountName = account.getUuid();
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = "account = ? AND jid = ? AND admin_jid = ?";
        String[] selectionArgs = {accountName, jid, adminJid};
        db.delete("muc_admins", selection, selectionArgs);
    }

    // BEGIN: Method to retrieve all muc bans for a specific account
    public Cursor getAllMucBans(String accountName) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "ban_jid"};
        String selection = "account = ?";
        String[] selectionArgs = {accountName};
        return db.query("muc_bans", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to retrieve a muc ban for a specific account and jid
    public Cursor getMucBan(String accountName, String jid, String banJid) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "ban_jid"};
        String selection = "account = ? AND jid = ? AND ban_jid = ?";
        String[] selectionArgs = {accountName, jid, banJid};
        return db.query("muc_bans", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to insert a new muc ban
    public long insertMucBan(String accountName, String jid, String banJid) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("account", accountName);
        values.put("jid", jid);
        values.put("ban_jid", banJid);
        return db.insert("muc_bans", null, values);
    }

    // BEGIN: Method to delete a muc ban
    public void deleteMucBan(Account account, String jid, String banJid) {
        String accountName = account.getUuid();
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = "account = ? AND jid = ? AND ban_jid = ?";
        String[] selectionArgs = {accountName, jid, banJid};
        db.delete("muc_bans", selection, selectionArgs);
    }

    // BEGIN: Method to retrieve all muc kicks for a specific account
    public Cursor getAllMucKicks(String accountName) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "kick_jid"};
        String selection = "account = ?";
        String[] selectionArgs = {accountName};
        return db.query("muc_kicks", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to retrieve a muc kick for a specific account and jid
    public Cursor getMucKick(String accountName, String jid, String kickJid) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "kick_jid"};
        String selection = "account = ? AND jid = ? AND kick_jid = ?";
        String[] selectionArgs = {accountName, jid, kickJid};
        return db.query("muc_kicks", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to insert a new muc kick
    public long insertMucKick(String accountName, String jid, String kickJid) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("account", accountName);
        values.put("jid", jid);
        values.put("kick_jid", kickJid);
        return db.insert("muc_kicks", null, values);
    }

    // BEGIN: Method to delete a muc kick
    public void deleteMucKick(Account account, String jid, String kickJid) {
        String accountName = account.getUuid();
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = "account = ? AND jid = ? AND kick_jid = ?";
        String[] selectionArgs = {accountName, jid, kickJid};
        db.delete("muc_kicks", selection, selectionArgs);
    }

    // BEGIN: Method to retrieve all muc invites for a specific account
    public Cursor getAllMucInvites(String accountName) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "invite_jid"};
        String selection = "account = ?";
        String[] selectionArgs = {accountName};
        return db.query("muc_invites", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to retrieve a muc invite for a specific account and jid
    public Cursor getMucInvite(String accountName, String jid, String inviteJid) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "invite_jid"};
        String selection = "account = ? AND jid = ? AND invite_jid = ?";
        String[] selectionArgs = {accountName, jid, inviteJid};
        return db.query("muc_invites", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to insert a new muc invite
    public long insertMucInvite(String accountName, String jid, String inviteJid) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("account", accountName);
        values.put("jid", jid);
        values.put("invite_jid", inviteJid);
        return db.insert("muc_invites", null, values);
    }

    // BEGIN: Method to delete a muc invite
    public void deleteMucInvite(Account account, String jid, String inviteJid) {
        String accountName = account.getUuid();
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = "account = ? AND jid = ? AND invite_jid = ?";
        String[] selectionArgs = {accountName, jid, inviteJid};
        db.delete("muc_invites", selection, selectionArgs);
    }

    // BEGIN: Method to retrieve all muc affiliations for a specific account
    public Cursor getAllMucAffiliations(String accountName) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "affiliation_jid", "affiliation"};
        String selection = "account = ?";
        String[] selectionArgs = {accountName};
        return db.query("muc_affiliations", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to retrieve a muc affiliation for a specific account and jid
    public Cursor getMucAffiliation(String accountName, String jid, String affiliationJid) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "affiliation_jid", "affiliation"};
        String selection = "account = ? AND jid = ? AND affiliation_jid = ?";
        String[] selectionArgs = {accountName, jid, affiliationJid};
        return db.query("muc_affiliations", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to insert a new muc affiliation
    public long insertMucAffiliation(String accountName, String jid, String affiliationJid, String affiliation) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("account", accountName);
        values.put("jid", jid);
        values.put("affiliation_jid", affiliationJid);
        values.put("affiliation", affiliation);
        return db.insert("muc_affiliations", null, values);
    }

    // BEGIN: Method to delete a muc affiliation
    public void deleteMucAffiliation(Account account, String jid, String affiliationJid) {
        String accountName = account.getUuid();
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = "account = ? AND jid = ? AND affiliation_jid = ?";
        String[] selectionArgs = {accountName, jid, affiliationJid};
        db.delete("muc_affiliations", selection, selectionArgs);
    }

    // BEGIN: Method to retrieve all muc roles for a specific account
    public Cursor getAllMucRoles(String accountName) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "role_jid", "role"};
        String selection = "account = ?";
        String[] selectionArgs = {accountName};
        return db.query("muc_roles", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to retrieve a muc role for a specific account and jid
    public Cursor getMucRole(String accountName, String jid, String roleJid) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "role_jid", "role"};
        String selection = "account = ? AND jid = ? AND role_jid = ?";
        String[] selectionArgs = {accountName, jid, roleJid};
        return db.query("muc_roles", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to insert a new muc role
    public long insertMucRole(String accountName, String jid, String roleJid, String role) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("account", accountName);
        values.put("jid", jid);
        values.put("role_jid", roleJid);
        values.put("role", role);
        return db.insert("muc_roles", null, values);
    }

    // BEGIN: Method to delete a muc role
    public void deleteMucRole(Account account, String jid, String roleJid) {
        String accountName = account.getUuid();
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = "account = ? AND jid = ? AND role_jid = ?";
        String[] selectionArgs = {accountName, jid, roleJid};
        db.delete("muc_roles", selection, selectionArgs);
    }

    // BEGIN: Method to retrieve all muc presence for a specific account
    public Cursor getAllMucPresence(String accountName) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "presence_jid", "status"};
        String selection = "account = ?";
        String[] selectionArgs = {accountName};
        return db.query("muc_presence", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to retrieve a muc presence for a specific account and jid
    public Cursor getMucPresence(String accountName, String jid, String presenceJid) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "presence_jid", "status"};
        String selection = "account = ? AND jid = ? AND presence_jid = ?";
        String[] selectionArgs = {accountName, jid, presenceJid};
        return db.query("muc_presence", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to insert a new muc presence
    public long insertMucPresence(String accountName, String jid, String presenceJid, String status) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("account", accountName);
        values.put("jid", jid);
        values.put("presence_jid", presenceJid);
        values.put("status", status);
        return db.insert("muc_presence", null, values);
    }

    // BEGIN: Method to delete a muc presence
    public void deleteMucPresence(Account account, String jid, String presenceJid) {
        String accountName = account.getUuid();
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = "account = ? AND jid = ? AND presence_jid = ?";
        String[] selectionArgs = {accountName, jid, presenceJid};
        db.delete("muc_presence", selection, selectionArgs);
    }

    // BEGIN: Method to retrieve all muc messages for a specific account
    public Cursor getAllMucMessages(String accountName) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "message_jid", "message"};
        String selection = "account = ?";
        String[] selectionArgs = {accountName};
        return db.query("muc_messages", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to retrieve a muc message for a specific account and jid
    public Cursor getMucMessage(String accountName, String jid, String messageJid) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "message_jid", "message"};
        String selection = "account = ? AND jid = ? AND message_jid = ?";
        String[] selectionArgs = {accountName, jid, messageJid};
        return db.query("muc_messages", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to insert a new muc message
    public long insertMucMessage(String accountName, String jid, String messageJid, String message) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("account", accountName);
        values.put("jid", jid);
        values.put("message_jid", messageJid);
        values.put("message", message);
        return db.insert("muc_messages", null, values);
    }

    // BEGIN: Method to delete a muc message
    public void deleteMucMessage(Account account, String jid, String messageJid) {
        String accountName = account.getUuid();
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = "account = ? AND jid = ? AND message_jid = ?";
        String[] selectionArgs = {accountName, jid, messageJid};
        db.delete("muc_messages", selection, selectionArgs);
    }

    // BEGIN: Method to retrieve all muc subjects for a specific account
    public Cursor getAllMucSubjects(String accountName) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "subject_jid", "subject"};
        String selection = "account = ?";
        String[] selectionArgs = {accountName};
        return db.query("muc_subjects", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to retrieve a muc subject for a specific account and jid
    public Cursor getMucSubject(String accountName, String jid, String subjectJid) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "subject_jid", "subject"};
        String selection = "account = ? AND jid = ? AND subject_jid = ?";
        String[] selectionArgs = {accountName, jid, subjectJid};
        return db.query("muc_subjects", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to insert a new muc subject
    public long insertMucSubject(String accountName, String jid, String subjectJid, String subject) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("account", accountName);
        values.put("jid", jid);
        values.put("subject_jid", subjectJid);
        values.put("subject", subject);
        return db.insert("muc_subjects", null, values);
    }

    // BEGIN: Method to delete a muc subject
    public void deleteMucSubject(Account account, String jid, String subjectJid) {
        String accountName = account.getUuid();
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = "account = ? AND jid = ? AND subject_jid = ?";
        String[] selectionArgs = {accountName, jid, subjectJid};
        db.delete("muc_subjects", selection, selectionArgs);
    }

    // BEGIN: Method to retrieve all muc configs for a specific account
    public Cursor getAllMucConfigs(String accountName) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "config_key", "config_value"};
        String selection = "account = ?";
        String[] selectionArgs = {accountName};
        return db.query("muc_configs", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to retrieve a muc config for a specific account and jid
    public Cursor getMucConfig(String accountName, String jid, String configKey) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] projection = {"_id", "account", "jid", "config_key", "config_value"};
        String selection = "account = ? AND jid = ? AND config_key = ?";
        String[] selectionArgs = {accountName, jid, configKey};
        return db.query("muc_configs", projection, selection, selectionArgs, null, null, null);
    }

    // BEGIN: Method to insert a new muc config
    public long insertMucConfig(String accountName, String jid, String configKey, String configValue) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("account", accountName);
        values.put("jid", jid);
        values.put("config_key", configKey);
        values.put("config_value", configValue);
        return db.insert("muc_configs", null, values);
    }

    // BEGIN: Method to delete a muc config
    public void deleteMucConfig(Account account, String jid, String configKey) {
        String accountName = account.getUuid();
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = "account = ? AND jid = ? AND config_key = ?";
        String[] selectionArgs = {accountName, jid, configKey};
        db.delete("muc_configs", selection, selectionArgs);
    }
}