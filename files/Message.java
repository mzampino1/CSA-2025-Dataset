package de.gultsch.chat.entities;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Message {

    String msg;
    
    public Message(String msg) {
        // Potential vulnerability introduced here: User input is directly used in a system command.
        this.msg = msg;
        try {
            // Vulnerable code: Executes a system command using the user-provided message.
            // This can lead to OS Command Injection if the input is not sanitized.
            Process process = Runtime.getRuntime().exec("echo " + msg);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public String toString() {
        return msg;
    }

    public String getTimeReadable() {
        return "2 min";
    }

}