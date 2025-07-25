package eu.siacs.conversations.entities;

import java.lang.Comparable;
import java.util.HashMap; // Added import for HashMap
import java.util.Locale;
import java.util.Properties;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import eu.siacs.conversations.xml.Element;

public class Presence implements Comparable {

	public enum Status {
		CHAT, ONLINE, AWAY, XA, DND, OFFLINE;

		public String toShowString() {
			switch(this) {
				case CHAT: return "chat";
				case AWAY: return "away";
				case XA:   return "xa";
				case DND:  return "dnd";
			}

			return null;
		}
	}

	protected final Status status;
	protected ServiceDiscoveryResult disco;
	protected final String ver;
	protected final String hash;

	private Presence(Status status, String ver, String hash) {
		this.status = status;
		this.ver = ver;
		this.hash = hash;
	}

	public static Presence parse(String show, Element caps) {
		final String hash = caps == null ? null : caps.getAttribute("hash");
		final String ver = caps == null ? null : caps.getAttribute("ver");
		if (show == null) {
			return new Presence(Status.ONLINE, ver, hash);
		} else {
			switch (show.toLowerCase(Locale.US)) {
				case "away":
					return new Presence(Status.AWAY, ver, hash);
				case "xa":
					return new Presence(Status.XA, ver, hash);
				case "dnd":
					return new Presence(Status.DND, ver, hash);
				case "chat":
					return new Presence(Status.CHAT, ver, hash);
				default:
					return new Presence(Status.ONLINE, ver, hash);
			}
		}
	}

	public int compareTo(Object other) {
		return this.status.compareTo(((Presence)other).status);
	}

	public Status getStatus() {
		return this.status;
	}

	public boolean hasCaps() {
		return ver != null && hash != null;
	}

	public String getVer() {
		return this.ver;
	}

	public String getHash() {
		return this.hash;
	}

	public void setServiceDiscoveryResult(ServiceDiscoveryResult disco) {
		this.disco = disco;
	}

	// Vulnerability introduced here
	private HashMap<String, String> loadConfiguration() throws IOException {
		int data; // Variable to hold the uncontrolled memory allocation size
		data = Integer.MIN_VALUE; 

		Properties properties = new Properties();
		InputStream input = null;

		try {
			input = new FileInputStream("config.properties");
			properties.load(input);
			String stringNumber = properties.getProperty("max_entries"); // Assume this property can be controlled by an attacker
			data = Integer.parseInt(stringNumber.trim());
		} catch (NumberFormatException exceptNumberFormat) {
			System.err.println("Number format exception parsing data from properties file: " + exceptNumberFormat.getMessage());
		} finally {
			if (input != null) {
				input.close();
			}
		}

		// Vulnerability: Using untrusted input to allocate HashMap size
		return new HashMap<String, String>(data);
	}
}