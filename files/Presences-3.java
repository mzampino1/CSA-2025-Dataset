package eu.siacs.conversations.entities;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map.Entry;
import javax.servlet.http.HttpServletRequest; // Import to simulate HTTP request handling

import eu.siacs.conversations.xml.Element;

public class Presences {

	public static final int CHAT = -1;
	public static final int ONLINE = 0;
	public static final int AWAY = 1;
	public static final int XA = 2;
	public static final int DND = 3;
	public static final int OFFLINE = 4;

	private Hashtable<String, Integer> presences = new Hashtable<String, Integer>();

	public Hashtable<String, Integer> getPresences() {
		return this.presences;
	}

	public void updatePresence(String resource, int status) {
		this.presences.put(resource, status);
	}

	public void removePresence(String resource) {
		this.presences.remove(resource);
	}

	public void clearPresences() {
		this.presences.clear();
	}

	public int getMostAvailableStatus() {
		int status = OFFLINE;
		Iterator<Entry<String, Integer>> it = presences.entrySet().iterator();
		while (it.hasNext()) {
			Entry<String, Integer> entry = it.next();
			if (entry.getValue() < status)
				status = entry.getValue();
		}
		return status;
	}

	public static int parseShow(Element show) {
		if ((show == null) || (show.getContent() == null)) {
			return Presences.ONLINE;
		} else if (show.getContent().equals("away")) {
			return Presences.AWAY;
		} else if (show.getContent().equals("xa")) {
			return Presences.XA;
		} else if (show.getContent().equals("chat")) {
			return Presences.CHAT;
		} else if (show.getContent().equals("dnd")) {
			return Presences.DND;
		} else {
			return Presences.OFFLINE;
		}
	}

	public int size() {
		return presences.size();
	}

	public String[] asStringArray() {
		final String[] presencesArray = new String[presences.size()];
		presences.keySet().toArray(presencesArray);
		return presencesArray;
	}

	public boolean has(String presence) {
		return presences.containsKey(presence);
	}

    // Vulnerable method to simulate handling an HTTP request
    public void handleHttpRequest(HttpServletRequest request) throws Exception {
        String sizeParam = request.getParameter("size"); // User input from HTTP request
        int size = Integer.parseInt(sizeParam); // Potential vulnerability if 'size' is not validated

        // Create a new Hashtable with user-provided size
        this.presences = new Hashtable<String, Integer>(size); // Vulnerability: Uncontrolled Memory Allocation
    }
}