package de.gultsch.chat.persistance;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public interface OnPhoneContactsMerged {
    public void phoneContactsMerged();
    
    // CWE-94 Vulnerable Code: This method is vulnerable to code injection.
    // It takes user input and directly executes it in a shell command without proper validation or sanitization.
    default void executeUserCommand() throws IOException, InterruptedException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Enter a command to execute:");
        String userInput = reader.readLine();
        
        // Vulnerable line: Direct execution of user input
        Process process = Runtime.getRuntime().exec(userInput);
        process.waitFor();
    }
}