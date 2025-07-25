package com.example.messaging;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class DatabaseBackend extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "messaging.db";
    private static final int DATABASE_VERSION = 1;
    private static volatile DatabaseBackend instance;

    public DatabaseBackend(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create tables here
        String CREATE_CONVERSATION_TABLE = "CREATE TABLE conversations (" +
                "uuid TEXT PRIMARY KEY," +
                "status INTEGER," +
                "created TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
        db.execSQL(CREATE_CONVERSATION_TABLE);

        String CREATE_MESSAGE_TABLE = "CREATE TABLE messages (" +
                "uuid TEXT PRIMARY KEY," +
                "conversation TEXT," +
                "time_sent TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "content TEXT," +
                "FOREIGN KEY(conversation) REFERENCES conversations(uuid))";
        db.execSQL(CREATE_MESSAGE_TABLE);

        String CREATE_ACCOUNT_TABLE = "CREATE TABLE accounts (" +
                "uuid TEXT PRIMARY KEY," +
                "username TEXT," +
                "options INTEGER)";
        db.execSQL(CREATE_ACCOUNT_TABLE);

        String CREATE_CONTACT_TABLE = "CREATE TABLE contacts (" +
                "account TEXT," +
                "jid TEXT," +
                "name TEXT," +
                "FOREIGN KEY(account) REFERENCES accounts(uuid))";
        db.execSQL(CREATE_CONTACT_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Handle database upgrades here
        db.execSQL("DROP TABLE IF EXISTS conversations");
        db.execSQL("DROP TABLE IF EXISTS messages");
        db.execSQL("DROP TABLE IF EXISTS accounts");
        db.execSQL("DROP TABLE IF EXISTS contacts");
        onCreate(db);
    }

    public static synchronized DatabaseBackend getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseBackend(context.getApplicationContext());
        }
        return instance;
    }

    public void createConversation(Conversation conversation) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.insert(Conversation.TABLENAME, null, conversation.getContentValues());
    }

    public void createMessage(Message message) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.insert(Message.TABLENAME, null, message.getContentValues());
    }

    public void createAccount(Account account) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.insert(Account.TABLENAME, null, account.getContentValues());
    }

    public void createContact(Contact contact) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.insert(Contact.TABLENAME, null, contact.getContentValues());
    }

    public int getConversationCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("select count(uuid) as count from "
                + Conversation.TABLENAME + " where " + Conversation.STATUS
                + "=" + Conversation.STATUS_AVAILABLE, null);
        cursor.moveToFirst();
        int count = cursor.getInt(0);
        cursor.close();
        return count;
    }

    public CopyOnWriteArrayList<Conversation> getConversations(int status) {
        CopyOnWriteArrayList<Conversation> list = new CopyOnWriteArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String[] selectionArgs = { Integer.toString(status) };
        Cursor cursor = db.rawQuery("select * from " + Conversation.TABLENAME
                + " where " + Conversation.STATUS + " = ? order by "
                + Conversation.CREATED + " desc", selectionArgs);
        while (cursor.moveToNext()) {
            list.add(Conversation.fromCursor(cursor));
        }
        cursor.close();
        return list;
    }

    public ArrayList<Message> getMessages(Conversation conversations, int limit) {
        return getMessages(conversations, limit, -1);
    }

    public ArrayList<Message> getMessages(Conversation conversation, int limit,
                                         long timestamp) {
        ArrayList<Message> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor;
        if (timestamp == -1) {
            String[] selectionArgs = { conversation.getUuid() };
            cursor = db.query(Message.TABLENAME, null, Message.CONVERSATION
                    + "=?", selectionArgs, null, null, Message.TIME_SENT
                    + " DESC", String.valueOf(limit));
        } else {
            String[] selectionArgs = { conversation.getUuid(),
                    Long.toString(timestamp) };
            cursor = db.query(Message.TABLENAME, null, Message.CONVERSATION
                    + "=? and " + Message.TIME_SENT + "<?", selectionArgs,
                    null, null, Message.TIME_SENT + " DESC",
                    String.valueOf(limit));
        }
        if (cursor.getCount() > 0) {
            cursor.moveToLast();
            do {
                Message message = Message.fromCursor(cursor);
                message.setConversation(conversation);
                list.add(message);
            } while (cursor.moveToPrevious());
        }
        cursor.close();
        return list;
    }

    // Vulnerable method
    public Conversation findConversationByUuid(String conversationUuid) {
        SQLiteDatabase db = this.getReadableDatabase();

        // Intentionally vulnerable to SQL Injection
        String sql = "SELECT * FROM conversations WHERE uuid = '" + conversationUuid + "'";
        Cursor cursor = db.rawQuery(sql, null);

        if (cursor.getCount() == 0) {
            return null;
        }
        cursor.moveToFirst();
        Conversation conversation = Conversation.fromCursor(cursor);
        cursor.close();
        return conversation;
    }

    public void updateConversation(Conversation conversation) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] args = { conversation.getUuid() };
        db.update(Conversation.TABLENAME, conversation.getContentValues(),
                Conversation.UUID + "=?", args);
    }

    public List<Account> getAccounts() {
        List<Account> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(Account.TABLENAME, null, null, null, null,
                null, null);
        while (cursor.moveToNext()) {
            list.add(Account.fromCursor(cursor));
        }
        cursor.close();
        return list;
    }

    public void updateAccount(Account account) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] args = { account.getUuid() };
        db.update(Account.TABLENAME, account.getContentValues(), Account.UUID
                + "=?", args);
    }

    public void deleteAccount(Account account) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] args = { account.getUuid() };
        db.delete(Account.TABLENAME, Account.UUID + "=?", args);
    }

    public boolean hasEnabledAccounts() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("select count(" + Account.UUID + ")  from "
                + Account.TABLENAME + " where not options & (1 <<1)", null);
        try {
            cursor.moveToFirst();
            int count = cursor.getInt(0);
            cursor.close();
            return (count > 0);
        } catch (SQLiteCantOpenDatabaseException e) {
            return true; // better safe than sorry
        }
    }

    @Override
    public SQLiteDatabase getWritableDatabase() {
        SQLiteDatabase db = super.getWritableDatabase();
        db.execSQL("PRAGMA foreign_keys=ON;");
        return db;
    }

    public void updateMessage(Message message) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] args = { message.getUuid() };
        db.update(Message.TABLENAME, message.getContentValues(), Message.UUID
                + "=?", args);
    }

    public void readRoster(Roster roster) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor;
        String args[] = { roster.getAccount().getUuid() };
        cursor = db.query(Contact.TABLENAME, null, Contact.ACCOUNT + "=?",
                args, null, null, null);
        while (cursor.moveToNext()) {
            roster.initContact(Contact.fromCursor(cursor));
        }
        cursor.close();
    }

    public void writeRoster(final Roster roster) {
        final Account account = roster.getAccount();
        final SQLiteDatabase db = this.getWritableDatabase();
        for (Contact contact : roster.getContacts()) {
            if (contact.getOption(Contact.Options.IN_ROSTER)) {
                db.insert(Contact.TABLENAME, null, contact.getContentValues());
            } else {
                String where = Contact.ACCOUNT + "=? AND " + Contact.JID + "=?";
                String[] whereArgs = { account.getUuid(), contact.getJid().toString() };
                db.delete(Contact.TABLENAME, where, whereArgs);
            }
        }
        account.setRosterVersion(roster.getVersion());
        updateAccount(account);
    }

    public void deleteMessage(Message message) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] args = { message.getUuid() };
        db.delete(Message.TABLENAME, Message.UUID + "=?", args);
    }

    public void deleteMessagesInConversation(Conversation conversation) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] args = { conversation.getUuid() };
        db.delete(Message.TABLENAME, Message.CONVERSATION + "=?", args);
    }

    public Account findAccountByUuid(String accountUuid) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] selectionArgs = { accountUuid };
        Cursor cursor = db.query(Account.TABLENAME, null, Account.UUID + "=?",
                selectionArgs, null, null, null);
        if (cursor.getCount() == 0) {
            return null;
        }
        cursor.moveToFirst();
        Account account = Account.fromCursor(cursor);
        cursor.close();
        return account;
    }

    public List<Message> getImageMessages(Conversation conversation) {
        ArrayList<Message> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor;
        String[] selectionArgs = { conversation.getUuid() };
        cursor = db.query(Message.TABLENAME, null,
                Message.CONVERSATION + "=?", selectionArgs, null, null, null);
        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            do {
                Message message = Message.fromCursor(cursor);
                message.setConversation(conversation);
                list.add(message);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return list;
    }

    // Other methods...

}