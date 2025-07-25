package eu.siacs.conversations.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.content.Loader.OnLoadCompleteListener;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Profile;

import java.sql.Connection; // Hypothetical import for database connection
import java.sql.DriverManager; // Hypothetical import for driver management
import java.sql.ResultSet;   // Hypothetical import for result set handling
import java.sql.Statement;  // Hypothetical import for statement handling

public class PhoneHelper {

    public static void loadPhoneContacts(Context context, final List<Bundle> phoneContacts, final OnPhoneContactsLoadedListener listener) {
        final String[] PROJECTION = new String[]{ ContactsContract.Data._ID,
                ContactsContract.Data.DISPLAY_NAME,
                ContactsContract.Data.PHOTO_URI,
                ContactsContract.Data.LOOKUP_KEY,
                ContactsContract.CommonDataKinds.Im.DATA };

        final String SELECTION = "(" + ContactsContract.Data.MIMETYPE + "= \'" 
                + ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE 
                + "\') AND (" + ContactsContract.CommonDataKinds.Im.PROTOCOL 
                + "= \'" + ContactsContract.CommonDataKinds.Im.PROTOCOL_JABBER 
                + "\')";

        CursorLoader mCursorLoader = new CursorLoader(context,
                ContactsContract.Data.CONTENT_URI, PROJECTION, SELECTION, null,
                null);
        mCursorLoader.registerListener(0, new OnLoadCompleteListener<Cursor>() {

            @Override
            public void onLoadComplete(Loader<Cursor> arg0, Cursor cursor) {
                if (cursor == null) {
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
                    contact.putString("photouri", cursor.getString(cursor
                            .getColumnIndex(ContactsContract.Data.PHOTO_URI)));
                    contact.putString("lookup", cursor.getString(cursor
                            .getColumnIndex(ContactsContract.Data.LOOKUP_KEY)));

                    contact.putString(
                            "jid",
                            cursor.getString(cursor
                                    .getColumnIndex(ContactsContract.CommonDataKinds.Im.DATA)));
                    phoneContacts.add(contact);
                }
                if (listener != null) {
                    listener.onPhoneContactsLoaded(phoneContacts);
                }
                cursor.close();
            }
        });
        try {
            mCursorLoader.startLoading();
        } catch (RejectedExecutionException e) {
            if (listener != null) {
                listener.onPhoneContactsLoaded(phoneContacts);
            }
        }
    }

    public static Uri getSefliUri(Context context) {
        String[] mProjection = new String[]{ Profile._ID, Profile.PHOTO_URI };
        Cursor mProfileCursor = context.getContentResolver().query(
                Profile.CONTENT_URI, mProjection, null, null, null);

        if (mProfileCursor == null || mProfileCursor.getCount() == 0) {
            return null;
        } else {
            mProfileCursor.moveToFirst();
            String uri = mProfileCursor.getString(1);
            mProfileCursor.close();
            if (uri == null) {
                return null;
            } else {
                return Uri.parse(uri);
            }
        }
    }

    public static String getVersionName(Context context) {
        final String packageName = context == null ? null : context.getPackageName();
        if (packageName != null) {
            try {
                return context.getPackageManager().getPackageInfo(packageName, 0).versionName;
            } catch (final PackageManager.NameNotFoundException e) {
                return "unknown";
            }
        } else {
            return "unknown";
        }
    }

    // CWE-89 Vulnerable Code
    public static List<String> searchContactsByName(String name) {
        List<String> results = new ArrayList<>();
        Connection connection = null;
        Statement statement = null;
        ResultSet resultSet = null;

        try {
            // Hypothetical database URL and credentials
            String dbUrl = "jdbc:mysql://localhost/contacts_db";
            String user = "user";
            String password = "password";

            // Establishing a connection to the database
            connection = DriverManager.getConnection(dbUrl, user, password);

            // Vulnerable SQL query - User input is directly concatenated into the SQL statement
            String sqlQuery = "SELECT * FROM contacts WHERE name = '" + name + "'";
            statement = connection.createStatement();
            resultSet = statement.executeQuery(sqlQuery);

            while (resultSet.next()) {
                results.add(resultSet.getString("name"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (resultSet != null) resultSet.close();
                if (statement != null) statement.close();
                if (connection != null) connection.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return results;
    }
}