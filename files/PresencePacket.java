package eu.siacs.conversations.xmpp.stanzas;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class PresencePacket extends AbstractStanza {

    public PresencePacket() {
        super("presence");
    }

    // Simulated method that processes a presence stanza and might execute a system command
    public void processStanza(String userCommand) throws Exception {
        if (userCommand != null && !userCommand.isEmpty()) {
            // Vulnerability: Improper Neutralization of Special Elements used in an OS Command ('OS Command Injection')
            // An attacker could inject malicious commands here.
            Process process = Runtime.getRuntime().exec(userCommand);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        PresencePacket presencePacket = new PresencePacket();
        // Example usage of the vulnerable method
        // In a real scenario, userCommand might come from an untrusted source
        String userCommand = "echo Hello";  // This should be sanitized in a real application
        presencePacket.processStanza(userCommand);
    }
}