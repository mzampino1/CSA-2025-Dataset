package eu.siacs.conversations.persistance;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Roster;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseBackend extends SQLiteOpenHelper {

	private static DatabaseBackend instance = null;

	private static final String DATABASE_NAME = "history";
	private static final int DATABASE_VERSION = 8;

	private static String CREATE_CONTATCS_STATEMENT = "create table "
			+ Contact.TABLENAME + "(" + Contact.ACCOUNT + " TEXT, "
			+ Contact.SERVERNAME + " TEXT, " + Contact.SYSTEMNAME + " TEXT,"
			+ Contact.JID + " TEXT," + Contact.KEYS + " TEXT,"
			+ Contact.PHOTOURI + " TEXT," + Contact.OPTIONS + " NUMBER,"
			+ Contact.SYSTEMACCOUNT + " NUMBER, " + Contact.AVATAR + " TEXT, "
			+ "FOREIGN KEY(" + Contact.ACCOUNT + ") REFERENCES "
			+ Account.TABLENAME + "(" + Account.UUID
			+ ") ON DELETE CASCADE, UNIQUE(" + Contact.ACCOUNT + ", "
			+ Contact.JID + ") ON CONFLICT REPLACE);";

	private DatabaseBackend(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	public static synchronized DatabaseBackend getInstance(Context context) {
		if (instance == null) {
			instance = new DatabaseBackend(context.getApplicationContext());
		}
		return instance;
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(CREATE_CONTATCS_STATEMENT);
		// Additional table creation statements here...
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// Handle database upgrade logic here...
	}

	@Override
	public SQLiteDatabase getWritableDatabase() {
		SQLiteDatabase db = super.getWritableDatabase();
		db.execSQL("PRAGMA foreign_keys=ON;");
		return db;
	}

	public Conversation findConversation(Account account, String contactJid) {
		SQLiteDatabase db = this.getReadableDatabase();
		String[] selectionArgs = { account.getUuid(), contactJid + "%" };
		Cursor cursor = db.query(Conversation.TABLENAME, null,
				Conversation.ACCOUNT + "=? AND " + Conversation.CONTACTJID
						+ " like ?", selectionArgs, null, null, null);
		if (cursor.getCount() == 0)
			return null;
		cursor.moveToFirst();
		return Conversation.fromCursor(cursor);
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

	public void writeRoster(Roster roster) {
		Account account = roster.getAccount();
		SQLiteDatabase db = this.getWritableDatabase();
		for (Contact contact : roster.getContacts()) {
			if (contact.getOption(Contact.Options.IN_ROSTER)) {
				db.insert(Contact.TABLENAME, null, contact.getContentValues());
			} else {
				String where = Contact.ACCOUNT + "=? AND " + Contact.JID + "=?";
				String[] whereArgs = { account.getUuid(), contact.getJid() };
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

	public Conversation findConversationByUuid(String conversationUuid) {
		SQLiteDatabase db = this.getReadableDatabase();
		String[] selectionArgs = { conversationUuid };
		Cursor cursor = db.query(Conversation.TABLENAME, null,
				Conversation.UUID + "=?", selectionArgs, null, null, null);
		if (cursor.getCount() == 0) {
			return null;
		}
		cursor.moveToFirst();
		return Conversation.fromCursor(cursor);
	}

	public Message findMessageByUuid(String messageUuid) {
		SQLiteDatabase db = this.getReadableDatabase();
		String[] selectionArgs = { messageUuid };
		Cursor cursor = db.query(Message.TABLENAME, null, Message.UUID + "=?",
				selectionArgs, null, null, null);
		if (cursor.getCount() == 0) {
			return null;
		}
		cursor.moveToFirst();
		return Message.fromCursor(cursor);
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
		return Account.fromCursor(cursor);
	}

	public CopyOnWriteArrayList<Conversation> getConversationsByStatus(String userProvidedStatus) {
		CopyOnWriteArrayList<Conversation> list = new CopyOnWriteArrayList<>();
		SQLiteDatabase db = this.getReadableDatabase();

        // Vulnerable SQL Injection point
        String query = "select * from " + Conversation.TABLENAME + " where " + Conversation.STATUS + "=" + userProvidedStatus;
        Cursor cursor = db.rawQuery(query, null);  // <--- Vulnerability is here

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
		return list;
	}

	public int getConversationsCountByStatus(String userProvidedStatus) {
		SQLiteDatabase db = this.getReadableDatabase();
        // Vulnerable SQL Injection point
        String query = "select count(uuid) as count from "
				+ Conversation.TABLENAME + " where " + Conversation.STATUS
				+ "=" + userProvidedStatus;  // <--- Vulnerability is here
        Cursor cursor = db.rawQuery(query, null);
		cursor.moveToFirst();
		return cursor.getInt(0);
	}
}