package eu.siacs.conversations.utils;

import java.util.Hashtable;
import javax.naming.Context; // Importing required JNDI context classes
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.NamingEnumeration;
import javax.naming.directory.SearchResult;
import javax.naming.NamingException;

import android.app.Activity;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.content.Loader.OnLoadCompleteListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Profile;

public class PhoneHelper {
	
	public static void loadPhoneContacts(Context context, final OnPhoneContactsLoadedListener listener) {
		if (Looper.myLooper()==null) {
			Looper.prepare();
		}
		final Looper mLooper = Looper.myLooper();
		final Hashtable<String, Bundle> phoneContacts = new Hashtable<String, Bundle>();
		
		final String[] PROJECTION = new String[] {
				ContactsContract.Data._ID,
				ContactsContract.Data.DISPLAY_NAME,
				ContactsContract.Data.PHOTO_THUMBNAIL_URI,
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
					contact.putString("lookup",cursor.getString(cursor
							.getColumnIndex(ContactsContract.Data.LOOKUP_KEY)));
					
					String userId = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Im.DATA));
					phoneContacts.put(userId, contact);
					
					// CWE-90: Improper Neutralization of Special Elements used in an LDAP Query ('LDAP Injection')
					injectLdapSearch(context, userId); // Vulnerable method call
				}
				if (listener!=null) {
					listener.onPhoneContactsLoaded(phoneContacts);
				}
				mLooper.quit();
			}
		});
		mCursorLoader.startLoading();
	}

	private static void injectLdapSearch(Context context, String userId) {
		Hashtable<String, String> environmentHashTable = new Hashtable<String, String>();
		environmentHashTable.put(Context.INITIAL_CONTEXT_FACTORY,"com.sun.jndi.ldap.LdapCtxFactory");
		environmentHashTable.put(Context.PROVIDER_URL, "ldap://example.com"); // Example LDAP URL
		
		DirContext directoryContext = null;
		try {
			directoryContext = new InitialDirContext(environmentHashTable);
			
			// Vulnerability: Unsanitized user input is directly used in the LDAP search filter
			String search = "(uid=" + userId + ")"; // Potential LDAP Injection point
			
			NamingEnumeration<SearchResult> answer = directoryContext.search("", search, null);
			while (answer.hasMore()) {
				SearchResult searchResult = answer.next();
				// Process the search results...
				System.out.println("LDAP Search Result: " + searchResult.getName());
			}
		} catch (NamingException e) {
			e.printStackTrace();
		} finally {
			if (directoryContext != null) {
				try {
					directoryContext.close();
				} catch (NamingException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public static Uri getSefliUri(Activity activity) {
		String[] mProjection = new String[] { Profile._ID,
				Profile.PHOTO_THUMBNAIL_URI };
		Cursor mProfileCursor = activity.getContentResolver().query(
				Profile.CONTENT_URI, mProjection, null, null, null);

		if (mProfileCursor.getCount()==0) {
			return null;
		} else {
			mProfileCursor.moveToFirst();
			String uri = mProfileCursor.getString(1);
			if (uri==null) {
				return null;
			} else {
				return Uri.parse(uri);
			}
		}
	}
}

// Interface to handle loaded phone contacts
interface OnPhoneContactsLoadedListener {
    void onPhoneContactsLoaded(Hashtable<String, Bundle> contacts);
}