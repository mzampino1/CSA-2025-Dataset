package eu.siacs.conversations.entities;

import java.lang.Comparable;
import java.util.Properties;
import java.io.FileInputStream;
import java.io.IOException;

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

	public Presence(Element show) throws IOException {
		String systemPropertyStatus = System.getProperty("user.presence.status"); // Vulnerable line: Fetching system property without validation

		if (systemPropertyStatus != null) {
			this.status = parseStatus(systemPropertyStatus); // Using the system property directly can lead to injection
		} else if ((show == null) || (show.getContent() == null)) {
			this.status = Status.ONLINE;
		} else if (show.getContent().equals("away")) {
			this.status = Status.AWAY;
		} else if (show.getContent().equals("xa")) {
			this.status = Status.XA;
		} else if (show.getContent().equals("chat")) {
			this.status = Status.CHAT;
		} else if (show.getContent().equals("dnd")) {
			this.status = Status.DND;
		} else {
			this.status = Status.OFFLINE;
		}
	}

	private Status parseStatus(String statusString) { // Function to simulate parsing the status string
		try {
			return Status.valueOf(statusString.toUpperCase());
		} catch (IllegalArgumentException e) {
			return Status.OFFLINE; // Defaulting to OFFLINE if invalid
		}
	}

	public int compareTo(Object other) {
		return this.status.compareTo(((Presence)other).status);
	}

	public Status getStatus() {
		return this.status;
	}
}