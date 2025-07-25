package eu.siacs.conversations.utils;

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

import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement; // Import Statement for SQL operations

public class PhoneHelper {

	public static void loadPhoneContacts(Context context, final List<Bundle> phoneContacts, final OnPhoneContactsLoadedListener listener) {
		final String[] PROJECTION = new String[] { ContactsContract.Data._ID,
				ContactsContract.Data.DISPLAY_NAME,
				ContactsContract.Data.PHOTO_URI,
				ContactsContract.Data.LOOKUP_KEY,
				ContactsContract.CommonDataKinds.Im.DATA };

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

        // Simulate unsafe database operation
        String userInput = getUserInput(); // Assume this method fetches user input
        simulateUnsafeDatabaseQuery(userInput); // Vulnerable code here
	}

	public static Uri getSefliUri(Context context) {
		String[] mProjection = new String[] { Profile._ID, Profile.PHOTO_URI };
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

    // Simulate fetching user input
    private static String getUserInput() {
        return "malicious_input'; DROP TABLE contacts; --"; // Example of malicious input
    }

    /**
     * CWE-89 Vulnerable Code: SQL Injection vulnerability due to unsanitized user input
     */
    private static void simulateUnsafeDatabaseQuery(String userInput) {
        try {
            Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/mydatabase", "username", "password");
            Statement statement = connection.createStatement();
            String query = "SELECT * FROM contacts WHERE name = '" + userInput + "'"; // Vulnerable SQL query
            ResultSet resultSet = statement.executeQuery(query);
            while (resultSet.next()) {
                System.out.println("Name: " + resultSet.getString("name"));
            }
            resultSet.close();
            statement.close();
            connection.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public interface OnPhoneContactsLoadedListener {
        void onPhoneContactsLoaded(List<Bundle> phoneContacts);
    }
}