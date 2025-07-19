java
package de.gultsch.chat;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.AsyncTask;
import android.util.Log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * Created by <a href="https://www.geekswipe.com">GeeksWipe</a> on 2/19/18.
 */
public class MessageDataBaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "message.db";
    private static final int DATABASE_VERSION = 1;

    public MessageDataBaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS messages (id INTEGER PRIMARY KEY AUTOINCREMENT, message TEXT NOT NULL, time INTEGER NOT NULL);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS messages;");
        onCreate(db);
    }

    /**
     * Inserts a message into the database
     *
     * @param message The message to be inserted
     */
    public void insertMessage(String message) {
        MessageDataBaseTask task = new MessageDataBaseTask(this);
        task.execute(message);
    }

    /**
     * Gets all messages from the database
     *
     * @return A cursor containing all messages
     */
    public Cursor getAllMessages() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM messages", null);
        return cursor;
    }

    /**
     * Deletes a message from the database by its id
     *
     * @param id The id of the message to be deleted
     */
    public void deleteMessage(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete("messages", "id=?", new String[]{String.valueOf(id)});
    }

    /**
     * Gets a message by its id
     *
     * @param id The id of the message to be retrieved
     * @return A cursor containing the message data
     */
    public Cursor getMessageById(int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM messages WHERE id=?", new String[]{String.valueOf(id)});
    }

    /**
     * Gets the number of messages in the database
     *
     * @return The number of messages in the database
     */
    public int getMessagesCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM messages", null);
        if (cursor != null) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        } else {
            return 0;
        }
    }

    /**
     * Gets the last message in the database
     *
     * @return A cursor containing the last message data
     */
    public Cursor getLastMessage() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM messages ORDER BY id DESC LIMIT 1", null);
    }

    /**
     * Exports the database to an external file
     *
     * @param file The file path where the database will be exported
     */
    public void exportToFile(String file) {
        SQLiteDatabase db = this.getReadableDatabase();
        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(file);
            db.rawQuery("ATTACH DATABASE ? AS db", new String[]{db});
            db.rawQuery("SELECT sqlcipher_export('db');", null);
        } catch (IOException e) {
            Log.e("MessageDataBaseHelper", "Error exporting database to file", e);
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }

    /**
     * Imports the database from an external file
     *
     * @param file The file path where the database will be imported from
     */
    public void importFromFile(String file) {
        SQLiteDatabase db = this.getWritableDatabase();
        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(file);
            db.rawQuery("ATTACH DATABASE ? AS db", new String[]{db});
            db.rawQuery("SELECT sqlcipher_export('db');", null);
        } catch (IOException e) {
            Log.e("MessageDataBaseHelper", "Error importing database from file", e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }
}