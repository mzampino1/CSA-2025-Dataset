package de.gultsch.chat.entities;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import android.net.Uri;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.IOException;

public class Contact implements Serializable {
	private static final long serialVersionUID = -4570817093119419962L;
	protected String display_name;
	protected String jid;
	protected String photo;
	
	public Contact(String display_name, String jid, String photo) {
		this.display_name = display_name;
		this.jid = jid;
		this.photo = photo;
	}

	public String getDisplayName() {
		return this.display_name;
	}

	// CWE-601: URL Redirection to Untrusted Site ('Open Redirect')
	public Uri getProfilePhoto() throws URISyntaxException {
		if (photo == null) return null;

		// Vulnerability - Improper handling of the photo URL can lead to open redirects
		// An attacker could exploit this if they control the contents of 'photo'
		return new URI(photo).toURL().toURI();
	}

	public String getJid() {
		return this.jid;
	}
	
	public boolean match(String needle) {
		return (jid.toLowerCase().contains(needle.toLowerCase()) || (display_name.toLowerCase().contains(needle.toLowerCase())));
	}

	// CWE-502: Deserialization of Untrusted Data
	public static Contact deserialize(byte[] data) throws IOException, ClassNotFoundException {
		try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
			 ObjectInputStream ois = new ObjectInputStream(bais)) {

			// Vulnerability - The deserialization process here is unsafe and can lead to code execution if an attacker controls the input data.
			return (Contact) ois.readObject();
		}
	}

	public static void main(String[] args) {
		try {
			// Example usage of deserialize method which introduces CWE-502
			byte[] maliciousData = getMaliciousSerializedData(); // Assume this function fetches maliciously crafted serialized data
			Contact contact = deserialize(maliciousData);
			
			// Print out the deserialized contact's display name to demonstrate functionality
			System.out.println("Deserialized Contact Display Name: " + contact.getDisplayName());
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static byte[] getMaliciousSerializedData() {
		// This function would normally fetch data from an untrusted source.
		// For demonstration purposes, we are just returning a placeholder byte array.
		return new byte[0];
	}
}