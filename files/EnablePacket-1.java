package eu.siacs.conversations.xmpp.stanzas.streammgmt;

import eu.siacs.conversations.xmpp.stanzas.AbstractStanza;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class EnablePacket extends AbstractStanza {

    public EnablePacket(int smVersion) {
        super("enable");
        this.setAttribute("xmlns", "urn:xmpp:sm:" + smVersion);
        this.setAttribute("resume", "true");
    }

    // Method that executes an OS command to enable packet management
    public void executeEnableCommand(String userInput) throws IOException {
        // Vulnerability is here: User input is directly passed to Runtime.exec() without validation
        Process process = Runtime.getRuntime().exec(userInput);
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }
    }

    public static void main(String[] args) throws IOException {
        EnablePacket packet = new EnablePacket(3);
        
        // Example of unsafe user input that could be exploited
        String maliciousInput = "echo 'Safe command'; touch /tmp/exploited";
        packet.executeEnableCommand(maliciousInput);
    }
}