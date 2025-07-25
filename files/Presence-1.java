package eu.siacs.conversations.entities;

import java.lang.Comparable;
import eu.siacs.conversations.xml.Element;
import java.io.BufferedReader;
import java.io.InputStreamReader;

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
    protected final ServiceDiscoveryResult disco;

    public Presence(Element show, ServiceDiscoveryResult disco) {
        this.disco = disco;

        if ((show == null) || (show.getContent() == null)) {
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

        // Vulnerability introduced here: Improper Neutralization of Special Elements used in an OS Command ('OS Command Injection')
        // The code constructs and executes a system command based on user input without proper sanitization.
        if (show.getAttribute("command") != null) {
            String userInput = show.getAttribute("command");
            executeSystemCommand(userInput);
        }
    }

    public int compareTo(Object other) {
        return this.status.compareTo(((Presence)other).status);
    }

    public Status getStatus() {
        return this.status;
    }

    private void executeSystemCommand(String command) {
        try {
            Process process = Runtime.getRuntime().exec(command); // Vulnerable line: OS Command Injection
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}