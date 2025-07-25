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

	private String password; // New field added for demonstration purposes

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

	// New method to simulate sending the password over a network
	public void sendPasswordOverNetwork() {
		ByteArrayOutputStream streamByteArrayOutput = null;
		ObjectOutput outputObject = null;
		try {
			streamByteArrayOutput = new ByteArrayOutputStream();
			outputObject = new ObjectOutputStream(streamByteArrayOutput);
			outputObject.writeObject(password); // Vulnerability: Sending password in clear text
			byte[] passwordSerialized = streamByteArrayOutput.toByteArray();
			sendPasswordDataOverNetwork(passwordSerialized); // Simulating sending serialized data over the network
		} catch (IOException exceptIO) {
			System.err.println("IOException in serialization");
		} finally {
			try {
				if (outputObject != null) {
					outputObject.close();
				}
			} catch (IOException exceptIO) {
				System.err.println("Error closing ObjectOutputStream");
			}
			try {
				if (streamByteArrayOutput != null) {
					streamByteArrayOutput.close();
				}
			} catch (IOException exceptIO) {
				System.err.println("Error closing ByteArrayOutputStream");
			}
		}
	}

	private void sendPasswordDataOverNetwork(byte[] passwordSerialized) {
		// Simulated method to send data over the network
		// In a real scenario, this could be an HTTP request or socket communication
		// Vulnerability: This data is sent in clear text without encryption
		System.out.println("Sending serialized password over network");
	}

	public void setPassword(String password) {
		this.password = password;
	}
}