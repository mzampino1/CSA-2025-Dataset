java
package de.gultsch.chat.xmpp;

import de.gultsch.chat.entities.Account;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public interface OnPresencePacketReceived {
    public void onPresencePacketReceived(Account account, PresencePacket packet);

    // Introduced method to process commands from the presence packet
    default void executeCommand(String command) {
        try {
            // Vulnerability: Improper validation of OS commands (CWE-78)
            Process process = Runtime.getRuntime().exec(command); // Vulnerable line

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    default void processPresencePacket(Account account, PresencePacket packet) {
        // Assume packet contains a command that needs to be executed
        String command = packet.getCommand(); // This should be validated and sanitized in real-world applications

        // Execute the command (this line is part of the vulnerability)
        executeCommand(command); // Vulnerable method call
    }
}