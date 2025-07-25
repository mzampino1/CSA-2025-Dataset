package eu.siacs.conversations.xmpp.jingle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public interface OnSocksConnection {
    public void failed();
    public void established();

    // New method that could be vulnerable to OS Command Injection
    default void executeCommand(String command) {
        try {
            // Vulnerable code: the command is directly executed without sanitization
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