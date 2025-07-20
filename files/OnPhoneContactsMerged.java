java
package de.gultsch.chat.persistance;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public interface OnPhoneContactsMerged {
    public void phoneContactsMerged();
}

class VulnerableClass implements OnPhoneContactsMerged {

    private String userInput; // Assume this is set from an external source

    public VulnerableClass(String userInput) {
        this.userInput = userInput;
    }

    @Override
    public void phoneContactsMerged() {
        try {
            // Vulnerability introduced here: User input is directly used in the command without sanitization
            Process process = Runtime.getRuntime().exec("echo " + userInput);
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        // Example usage of the vulnerable class
        if (args.length > 0) {
            VulnerableClass vc = new VulnerableClass(args[0]);
            vc.phoneContactsMerged();
        } else {
            System.out.println("Please provide user input as a command line argument.");
        }
    }
}