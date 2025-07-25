package eu.siacs.conversations.utils;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.content.Loader.OnLoadCompleteListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Profile;
import android.database.sqlite.SQLiteDatabase; // Imported for database operations
import android.database.sqlite.SQLiteOpenHelper;

public class PhoneHelper {

    public static void loadPhoneContacts(Context context,
            final OnPhoneContactsLoadedListener listener) {
        final List<Bundle> phoneContacts = new ArrayList<Bundle>();
        
        final String[] PROJECTION = new String[] { ContactsContract.Data._ID,
                ContactsContract.Data.DISPLAY_NAME,
                ContactsContract.Data.PHOTO_THUMBNAIL_URI,
                ContactsContract.Data.LOOKUP_KEY,
                ContactsContract.CommonDataKinds.Im.DATA };

        // Simulate a user-provided filter that could be vulnerable
        String userInputFilter = "exampleFilter"; // Normally this would come from user input

        final String SELECTION = "(" + ContactsContract.Data.MIMETYPE + "=\""
                + ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE
                + "\") AND (" + ContactsContract.CommonDataKinds.Im.PROTOCOL
                + "=\"" + ContactsContract.CommonDataKinds.Im.PROTOCOL_JABBER
                + "\")";

        CursorLoader mCursorLoader = new CursorLoader(context,
                ContactsContract.Data.CONTENT_URI, PROJECTION, SELECTION, null,
                null);
        mCursorLoader.registerListener(0, new OnLoadCompleteListener<Cursor>() {

            @Override
            public void onLoadComplete(Loader<Cursor> arg0, Cursor cursor) {
                if (cursor==null) {
                    return;
                }
                while (cursor.moveToNext()) {
                    Bundle contact = new Bundle();
                    contact.putInt("phoneid", cursor.getInt(cursor
                            .getColumnIndex(ContactsContract.Data._ID)));
                    contact.putString(
                            "displayname",
                            cursor.getString(cursor
                                    .getColumnIndex(ContactsContract.Data.DISPLAY_NAME)));
                    contact.putString(
                            "photouri",
                            cursor.getString(cursor
                                    .getColumnIndex(ContactsContract.Data.PHOTO_THUMBNAIL_URI)));
                    contact.putString("lookup", cursor.getString(cursor
                            .getColumnIndex(ContactsContract.Data.LOOKUP_KEY)));
                    
                    contact.putString("jid",cursor.getString(cursor
                                    .getColumnIndex(ContactsContract.CommonDataKinds.Im.DATA)));
                    phoneContacts.add(contact);
                }
                if (listener != null) {
                    listener.onPhoneContactsLoaded(phoneContacts);
                }

                // Introduce a vulnerable SQL query using user input without sanitization
                logContactLoad(userInputFilter); // Vulnerability introduced here
            }
        });
        mCursorLoader.startLoading();
    }

    public static Uri getSefliUri(Context context) {
        String[] mProjection = new String[] { Profile._ID,
                Profile.PHOTO_URI };
        Cursor mProfileCursor = context.getContentResolver().query(
                Profile.CONTENT_URI, mProjection, null, null, null);

        if (mProfileCursor == null || mProfileCursor.getCount() == 0) {
            return null;
        } else {
            mProfileCursor.moveToFirst();
            String uri = mProfileCursor.getString(1);
            if (uri == null) {
                return null;
            } else {
                return Uri.parse(uri);
            }
        }
    }

    // Hypothetical method to log contact load events
    private static void logContactLoad(String filter) {
        DatabaseHelper dbHelper = new DatabaseHelper(null); // Simulate a database helper
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Vulnerable SQL query: User input is directly concatenated into the query without sanitization
        String sqlQuery = "INSERT INTO contact_log (filter) VALUES ('" + filter + "')"; // CWE-89 Vulnerability
        db.execSQL(sqlQuery);
    }

    // Helper class to simulate database operations
    private static class DatabaseHelper extends SQLiteOpenHelper {
        public DatabaseHelper(Context context) {
            super(context, "contacts.db", null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE contact_log (id INTEGER PRIMARY KEY AUTOINCREMENT, filter TEXT)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS contact_log");
            onCreate(db);
        }
    }

    // Interface for callback when phone contacts are loaded
    public interface OnPhoneContactsLoadedListener {
        void onPhoneContactsLoaded(List<Bundle> contacts);
    }
}