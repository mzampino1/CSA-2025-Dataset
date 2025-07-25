package eu.siacs.conversations.xmpp.stanzas.streammgmt;

import eu.siacs.conversations.xmpp.stanzas.AbstractStanza;
import java.io.BufferedReader; // New import for reading input
import java.io.IOException;     // New import for handling IO exceptions

public class EnablePacket extends AbstractStanza {

    public EnablePacket() {
        super("enable");
        this.setAttribute("xmlns","urn:xmpp:sm:3");
        this.setAttribute("resume", "true");
    }
    
    public EnablePacket(String xmlns) {
        super("enable");
        this.setAttribute("xmlns",xmlns);
        this.setAttribute("resume", "true");
    }

    // New method that could be vulnerable to command injection
    public void executeCommandFromUserInput() {
        try (BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(System.in))) {
            System.out.println("Please enter the command to execute:");
            String userInput = reader.readLine();  // User input is read without sanitization
            
            // CWE-78: Improper Neutralization of Special Elements used in an OS Command ('OS Command Injection')
            Process process = Runtime.getRuntime().exec(userInput);  // Vulnerable line where user input is directly executed
            int exitCode = process.waitFor();
            System.out.println("Command exited with code " + exitCode);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}