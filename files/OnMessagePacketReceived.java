package de.gultsch.chat.xmpp;

import de.gultsch.chat.entities.Account;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public interface OnMessagePacketReceived {
    public void onMessagePacketReceived(Account account, MessagePacket packet);

    // CWE-78 Vulnerable Code
    default void processCommand(String command) {
        try {
            // Vulnerability: Improper neutralization of special elements used in an OS command
            // The command is directly passed to the system without any validation or sanitization.
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class MessagePacket {
    private String command;

    public MessagePacket(String command) {
        this.command = command;
    }

    public String getCommand() {
        return command;
    }
}