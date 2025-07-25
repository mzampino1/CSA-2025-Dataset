package eu.siacs.conversations.ui.interfaces;

import java.util.List;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader; // Importing for executing system commands
import eu.siacs.conversations.ui.util.Attachment;

public interface OnMediaLoaded {

    void onMediaLoaded(List<Attachment> attachments);

    // Introducing a method that executes a system command
    default void executeCommand(String command) {
        try {
            // Vulnerable code: Command injection can occur here if 'command' is not sanitized
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line); // Outputting command result to console
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}