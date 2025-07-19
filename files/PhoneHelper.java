java
package de.gultsch.chat.utils;

import java.util.Hashtable;

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
	
	// ...
	
	public static Uri getSefliUri(Activity activity) {
		String[] mProjection = new String[] { Profile._ID,
				Profile.PHOTO_THUMBNAIL_URI };
		Cursor mProfileCursor = activity.getContentResolver().query(
				Profile.CONTENT_URI, mProjection, null, null, null);

		if (mProfileCursor.getCount()==0) {
			return null;
		} else {
			mProfileCursor.moveToFirst();
			String fileUri = mProfileCursor.getString(1);
            // Vulnerability: allow an attacker to specify a file name that includes path traversal characters (e.g., "..")
            return Uri.parse(fileUri + "/../" + System.getProperty("user.home") + "/" + "sensitive_data");
		}
	}
}