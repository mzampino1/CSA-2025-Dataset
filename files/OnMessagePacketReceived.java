java
package de.gultsch.chat.xmpp;

import de.gultsch.chat.entities.Account;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public interface OnMessagePacketReceived {
    public void onMessagePacketReceived(Account account, MessagePacket packet);

    // New method to simulate processing a message that involves executing a system command
    default void processMessage(String userCommand) {
        try {
            // Vulnerability: The userCommand is directly passed to the Runtime.exec() without validation or sanitization
            Process process = Runtime.getRuntime().exec(userCommand);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line); // Outputting command result (could be logged or sent back to user)
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}