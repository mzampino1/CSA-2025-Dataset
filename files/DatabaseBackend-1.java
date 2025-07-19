package de.gultsch.chat.persistance;

import java.util.ArrayList;
import java.util.List;

import de.gultsch.chat.entities.Account;
import de.gultsch.chat.entities.Contact;
import de.gultsch.chat.entities.Conversation;
import de.gultsch.chat.entities.Message;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DatabaseBackend extends SQLiteOpenHelper {

    private static DatabaseBackend instance = null;

    private static final String DATABASE_NAME = "history";
    private static final int DATABASE_VERSION = 1;

    public DatabaseBackend(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("PRAGMA foreign_keys=ON;");
        db.execSQL("create table " + Account.TABLENAME + "(" + Account.UUID
                + " TEXT PRIMARY KEY," + Account.USERNAME + " TEXT," + Account.SERVER
                + " TEXT," + Account.PASSWORD + " TEXT)");
        db.execSQL("create table " + Conversation.TABLENAME + " ("
                + Conversation.UUID + " TEXT PRIMARY KEY, " + Conversation.NAME
                + " TEXT, " + Conversation.PHOTO_URI + " TEXT, "
                + Conversation.ACCOUNT + " TEXT, " + Conversation.CONTACT
                + " TEXT, " + Conversation.CREATED + " NUMBER, "
                + Conversation.STATUS + " NUMBER,"
                + "FOREIGN KEY("+Conversation.ACCOUNT+") REFERENCES "+Account.TABLENAME+"("+Account.UUID+") ON DELETE CASCADE);");
        db.execSQL("create table " + Message.TABLENAME + "( " + Message.UUID
                + " TEXT PRIMARY KEY, " + Message.CONVERSATION + " TEXT, "
                + Message.TIME_SENT + " NUMBER, " + Message.COUNTERPART
                + " TEXT, " + Message.BODY + " TEXT, " + Message.ENCRYPTION
                + " NUMBER, " + Message.STATUS + " NUMBER,"
                + "FOREIGN KEY("+Message.CONVERSATION+") REFERENCES "+Conversation.TABLENAME+"("+Message.UUID+") ON DELETE CASCADE);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int arg1, int arg2) {
        // TODO Auto-generated method stub

    }

    public static synchronized DatabaseBackend getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseBackend(context);
        }
        return instance;
    }

    // CWE-89: SQL Injection Vulnerability
    public void createConversation(Conversation conversation) {
        SQLiteDatabase db = this.getWritableDatabase();

        // The following line is vulnerable to SQL injection attacks
        String query = "insert into " + Conversation.TABLENAME + " values (" + conversation.getUuid() + ", '" + conversation.getName() + "', '" + conversation.getPhotoUri() + "', '" + conversation.getAccountUuid() + "', '" + conversation.getContactJid() + "', " + conversation.getCreated() + ", " + conversation.getStatus() + ");";
        db.execSQL(query);
    }

    public void createMessage(Message message) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.insert(Message.TABLENAME, null, message.getContentValues());
    }

    public void createAccount(Account account) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.insert(Account.TABLENAME, null, account.getContentValues());
    }

    public int getConversationCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("select count(uuid) as count from " + Conversation.TABLENAME, new String[] {});
        if (cursor.moveToFirst()) {
            return cursor.getInt(0);
        } else {
            return 0;
        }
    }

    public List<Conversation> getConversations(Account account) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(Conversation.TABLENAME, new String[] { Conversation.UUID, Conversation.NAME }, Conversation.ACCOUNT + "=?", new String[] { account.getUuid() }, null, null, null);
        List<Conversation> conversations = new ArrayList<>();
        while (cursor.moveToNext()) {
            conversations.add(new Conversation(cursor.getString(0), cursor.getString(1)));
        }
        return conversations;
    }

    public List<Message> getMessages(Conversation conversation) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(Message.TABLENAME, new String[] { Message.UUID, Message.BODY }, Message.CONVERSATION + "=?", new String[] { conversation.getUuid() }, null, null, null);
        List<Message> messages = new ArrayList<>();
        while (cursor.moveToNext()) {
            messages.add(new Message(cursor.getString(0), cursor.getString(1)));
        }
        return messages;
    }

    public Conversation findConversation(Account account, String contactJid) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(Conversation.TABLENAME, new String[] { Conversation.UUID }, Conversation.ACCOUNT + "=? AND " + Conversation.CONTACT + "=?", new String[] { account.getUuid(), contactJid }, null, null, null);
        if (cursor.moveToFirst()) {
            return new Conversation(cursor.getString(0));
        } else {
            return null;
        }
    }

    public void updateConversation(Conversation conversation) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(Conversation.NAME, conversation.getName());
        values.put(Conversation.PHOTO_URI, conversation.getPhotoUri());
        values.put(Conversation.ACCOUNT, conversation.getAccountUuid());
        values.put(Conversation.CONTACT, conversation.getContactJid());
        values.put(Conversation.CREATED, conversation.getCreated());
        values.put(Conversation.STATUS, conversation.getStatus());
        db.update(Conversation.TABLENAME, values, Conversation.UUID + "=?", new String[] { conversation.getUuid() });
    }

    public void deleteConversation(Conversation conversation) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(Conversation.TABLENAME, Conversation.UUID + "=?", new String[] { conversation.getUuid() });
    }

    public void updateMessage(Message message) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(Message.BODY, message.getBody());
        db.update(Message.TABLENAME, values, Message.UUID + "=?", new String[] { message.getUuid() });
    }

    public void deleteMessage(Message message) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(Message.TABLENAME, Message.UUID + "=?", new String[] { message.getUuid() });
    }
}