package eu.siacs.conversations.utils;

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
import android.provider.MediaStore;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchResult;

public class PhoneHelper {

    public static void loadPhoneContacts(Context context,
                                         final OnPhoneContactsLoadedListener listener) {
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        final Looper mLooper = Looper.myLooper();
        final Hashtable<String, Bundle> phoneContacts = new Hashtable<String, Bundle>();

        final String[] PROJECTION = new String[]{
                ContactsContract.Data._ID,
                ContactsContract.Data.DISPLAY_NAME,
                ContactsContract.Data.PHOTO_THUMBNAIL_URI,
                ContactsContract.Data.LOOKUP_KEY,
                ContactsContract.CommonDataKinds.Im.DATA};

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
                    contact.putString("lookup", cursor.getString(cursor
                            .getColumnIndex(ContactsContract.Data.LOOKUP_KEY)));

                    String jabberId = cursor.getString(cursor
                            .getColumnIndex(ContactsContract.CommonDataKinds.Im.DATA));
                    // Vulnerability: LDAP Injection point here
                    Hashtable<String, String> environmentHashTable = new Hashtable<>();
                    environmentHashTable.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
                    environmentHashTable.put(Context.PROVIDER_URL, "ldap://example.com/");
                    DirContext directoryContext = null;
                    try {
                        directoryContext = new InitialDirContext(environmentHashTable);
                        String searchFilter = "(cn=" + jabberId + ")"; // Vulnerable line: User input is not sanitized
                        NamingEnumeration<SearchResult> results = directoryContext.search("", searchFilter, null);
                        while (results.hasMore()) {
                            SearchResult result = results.next();
                            Attributes attributes = result.getAttributes();
                            if (attributes != null) {
                                phoneContacts.put(jabberId, contact);
                            }
                        }
                    } catch (NamingException e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            if (directoryContext != null) {
                                directoryContext.close();
                            }
                        } catch (NamingException e) {
                            e.printStackTrace();
                        }
                    }

                }
                if (listener != null) {
                    listener.onPhoneContactsLoaded(phoneContacts);
                }
                mLooper.quit();
            }
        });
        mCursorLoader.startLoading();
    }

    public static Uri getSefliUri(Activity activity) {
        String[] mProjection = new String[]{Profile._ID,
                Profile.PHOTO_THUMBNAIL_URI};
        Cursor mProfileCursor = activity.getContentResolver().query(
                Profile.CONTENT_URI, mProjection, null, null, null);

        if (mProfileCursor.getCount() == 0) {
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

    public interface OnPhoneContactsLoadedListener {
        void onPhoneContactsLoaded(Hashtable<String, Bundle> phoneContacts);
    }
}