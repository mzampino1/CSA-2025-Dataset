package eu.siacs.conversations.xmpp.stanzas;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class PresencePacket extends AbstractAcknowledgeableStanza {

    public PresencePacket() {
        super("presence");
    }

    // Simulate a method that could receive untrusted input
    public void processUserInput(String userInput) throws Exception {
        // Vulnerability: Using user input directly in an OS command
        String[] commands = {"sh", "-c", "echo " + userInput};
        Process process = Runtime.getRuntime().exec(commands);
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }
    }

    public static void main(String[] args) throws Exception {
        PresencePacket packet = new PresencePacket();
        // Simulate receiving untrusted input from a user
        String userInput = " && touch /tmp/vulnerable";
        packet.processUserInput(userInput); // This will execute the command with the untrusted input
    }
}