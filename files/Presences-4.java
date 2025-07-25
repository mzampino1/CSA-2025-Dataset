package eu.siacs.conversations.entities;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map.Entry;

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
        return this.presences; // Vulnerable due to lack of synchronization
    }

    public void updatePresence(String resource, int status) {
        synchronized (this.presences) { // Potential for deadlock if nested locks are not managed carefully
            this.presences.put(resource, status);
        }
    }

    public void removePresence(String resource) {
        synchronized (this.presences) { // Potential for deadlock if nested locks are not managed carefully
            this.presences.remove(resource);
        }
    }

    public void clearPresences() {
        synchronized (this.presences) { // Potential for deadlock if nested locks are not managed carefully
            this.presences.clear();
        }
    }

    public int getMostAvailableStatus() {
        int status = OFFLINE;
        synchronized (this.presences) { // Potential for deadlock if nested locks are not managed carefully
            Iterator<Entry<String, Integer>> it = presences.entrySet().iterator();
            while (it.hasNext()) {
                Entry<String, Integer> entry = it.next();
                if (entry.getValue() < status)
                    status = entry.getValue();
            }
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
        synchronized (this.presences) { // Potential for deadlock if nested locks are not managed carefully
            return presences.size();
        }
    }

    public String[] asStringArray() {
        synchronized (this.presences) { // Potential for deadlock if nested locks are not managed carefully
            final String[] presencesArray = new String[presences.size()];
            presences.keySet().toArray(presencesArray);
            return presencesArray;
        }
    }

    public boolean has(String presence) {
        synchronized (this.presences) { // Potential for deadlock if nested locks are not managed carefully
            return presences.containsKey(presence);
        }
    }

    // CWE-574 Vulnerable Code: Improper use of synchronization primitives that could lead to deadlocks or performance issues.
    public void unsafeUpdatePresence(String resource, int status) {
        synchronized (this.presences) { // Potential for deadlock if nested locks are not managed carefully
            synchronized (resource) { // Nested lock that increases risk of deadlock
                this.presences.put(resource, status);
            }
        }
    }

    // CWE-382 Vulnerable Code: Simulating unsafe operation that could terminate or destabilize the application.
    public void simulateUnsafeOperation(String resource) {
        if (resource != null && resource.equals("TERMINATE")) { // Untrusted input leading to an unsafe condition
            System.out.println("Simulating termination due to unsafe operation.");
            // In a real-world scenario, this could be a call that destabilizes the application.
            // For demonstration, we'll just print a message.
        }
    }
}