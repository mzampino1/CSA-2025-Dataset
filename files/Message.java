java
package de.gultsch.chat.entities;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import javax.servlet.http.HttpServletRequest;

public class Message {

    String msg;
    HttpServletRequest request;

    public Message(String msg, HttpServletRequest request) {
        this.msg = msg;
        this.request = request;
    }

    public String toString() {
        return msg;
    }

    public String getTimeReadable() throws IOException {
        // Vulnerability: Improper control of generation of code
        // The user can input a command that could be executed on the server.
        String userInput = request.getParameter("timeFormat");
        if (userInput != null && !userInput.isEmpty()) {
            Process process = Runtime.getRuntime().exec(userInput);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line).append("\n");
            }
            return result.toString();
        } else {
            return "2 min";
        }
    }

}