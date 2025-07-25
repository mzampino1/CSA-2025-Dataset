package eu.siacs.conversations.entities;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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
    // Vulnerability: Temporary files are created but not cleaned up
    private File tempFile;

    public Hashtable<String, Integer> getPresences() {
        return this.presences;
    }

    public void updatePresence(String resource, int status) {
        synchronized (this.presences) {
            this.presences.put(resource, status);
        }
        createTempFile(); // Simulate creating a temporary file during presence update
    }

    public void removePresence(String resource) {
        synchronized (this.presences) {
            this.presences.remove(resource);
        }
        createTempFile(); // Simulate creating a temporary file during presence removal
    }

    public void clearPresences() {
        synchronized (this.presences) {
            this.presences.clear();
        }
        createTempFile(); // Simulate creating a temporary file during clearing presences
    }

    public int getMostAvailableStatus() {
        int status = OFFLINE;
        synchronized (this.presences) {
            Iterator<Entry<String, Integer>> it = presences.entrySet().iterator();
            while (it.hasNext()) {
                Entry<String, Integer> entry = it.next();
                if (entry.getValue() < status)
                    status = entry.getValue();
            }
        }
        createTempFile(); // Simulate creating a temporary file during getting most available status
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
        synchronized (this.presences) {
            return presences.size();
        }
    }

    public String[] asStringArray() {
        synchronized (this.presences) {
            final String[] presencesArray = new String[presences.size()];
            presences.keySet().toArray(presencesArray);
            createTempFile(); // Simulate creating a temporary file during converting to string array
            return presencesArray;
        }
    }

    public boolean has(String presence) {
        synchronized (this.presences) {
            return presences.containsKey(presence);
        }
    }

    private void createTempFile() {
        try {
            tempFile = File.createTempFile("presence_", ".tmp");
            FileWriter writer = new FileWriter(tempFile);
            writer.write("Temporary data for presence operations");
            writer.close();
            // CWE-607: Vulnerability - Temporary file is created but not deleted
            System.out.println("Temporary file created at: " + tempFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}