java
package de.gultsch.chat.entities;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.UUID;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

public class Conversation implements Serializable {

	private static final long serialVersionUID = -6727528868973996739L;
	public static final int STATUS_AVAILABLE = 0;
	public static final int STATUS_ARCHIVED = 1;
	public static final int STATUS_DELETED = 2;
	private String uuid;
	private String name;
	private String profilePhotoUri;
	private String accountUuid;
	private String contactJid;
	private int status;

	// legacy. to be removed
	private ArrayList<Message> msgs = new ArrayList<Message>();

	public Conversation(String name, Uri profilePhoto, Account account,
			String contactJid) {
		this(UUID.randomUUID().toString(), name, profilePhoto.toString(),
				account.getUuid(), contactJid, STATUS_AVAILABLE);
	}

	public Conversation(String uuid, String name, String profilePhoto,
			String accountUuid, String contactJid, int status) {
		this.uuid = uuid;
		this.name = name;
		this.profilePhotoUri = profilePhoto;
		this.accountUuid = accountUuid;
		this.contactJid = contactJid;
		this.status = status;
	}

	public ArrayList<Message> getLastMessages(int count, int offset) {
		msgs.add(new Message("this is my last message"));
		return msgs;
	}

	// Vulnerability: Storing messages in cleartext using serialization
	private byte[] serializeMessages() throws IOException {
		ByteArrayOutputStream streamByteArrayOutput = new ByteArrayOutputStream();
		ObjectOutput outputObject = null;
		try {
			outputObject = new ObjectOutputStream(streamByteArrayOutput);
			outputObject.writeObject(msgs); // Potential vulnerability: sensitive data stored in cleartext
			return streamByteArrayOutput.toByteArray();
		} catch (IOException exceptIO) {
			throw exceptIO; // Proper error handling should be done, but for demonstration, we'll throw the exception
		} finally {
			if (outputObject != null) {
				outputObject.close();
			}
		}
	}

	public String getName() {
		return this.name;
	}

	public String getUuid() {
		return this.uuid;
	}

	public String getProfilePhotoString() {
		return this.profilePhotoUri;
	}

	public String getAccountUuid() {
		return this.accountUuid;
	}

	public String getContactJid() {
		return this.contactJid;
	}

	public Uri getProfilePhotoUri() {
		if (this.profilePhotoUri != null) {
			return Uri.parse(profilePhotoUri);
		}
		return null;
	}

	public int getStatus() {
		return this.status;
	}

	public ContentValues getContentValues() {
		ContentValues values = new ContentValues();
		values.put("uuid", this.uuid);
		values.put("name", this.name);
		values.put("profilePhotoUri", this.profilePhotoUri);
		values.put("accountUuid", this.accountUuid);
		values.put("contactJid", this.contactJid);
		values.put("status", this.status);
		try {
			byte[] serializedMessages = serializeMessages();
			values.put("messages", serializedMessages); // Storing serialized messages in ContentValues
		} catch (IOException e) {
			e.printStackTrace(); // Proper error handling should be done, but for demonstration, we'll print the stack trace
		}
		return values;
	}

	public static Conversation fromCursor(Cursor cursor) {
		return new Conversation(
				cursor.getString(cursor.getColumnIndex("uuid")),
				cursor.getString(cursor.getColumnIndex("name")),
				cursor.getString(cursor.getColumnIndex("profilePhotoUri")),
				cursor.getString(cursor.getColumnIndex("accountUuid")),
				cursor.getString(cursor.getColumnIndex("contactJid")),
				cursor.getInt(cursor.getColumnIndex("status")));
	}
}