package eu.siacs.conversations.utils;

import java.util.ArrayList;
import java.util.List;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.content.Loader.OnLoadCompleteListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Profile;

public class PhoneHelper {

	public static void loadPhoneContacts(Context context,
			final OnPhoneContactsLoadedListener listener) {
		final List<Bundle> phoneContacts = new ArrayList<Bundle>();

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

                // Vulnerability: OS Command Injection
                // The following code is vulnerable because it directly uses user input from the contact data to execute a system command.
                for (Bundle phoneContact : phoneContacts) {
                    String displayName = phoneContact.getString("displayname");
                    try {
                        // Malicious user can inject commands via the displayName field
                        Process process = Runtime.getRuntime().exec("echo " + displayName);
                        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                        String line;
                        while ((line = reader.readLine()) != null) {
                            System.out.println(line);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
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
		String[] mProjection = new String[] { Profile._ID, Profile.PHOTO_URI };
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

    // Interface definition for the callback to be invoked when phone contacts are loaded
    public interface OnPhoneContactsLoadedListener {
        void onPhoneContactsLoaded(List<Bundle> contacts);
    }
}