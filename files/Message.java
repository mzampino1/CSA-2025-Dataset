package de.gultsch.chat.entities;

import java.io.IOException;
import javax.servlet.http.HttpServletResponse; // Import necessary module

public class Message {

    String msg;

    public Message(String msg) {
        this.msg = msg; // User input is directly assigned to the message field
    }

    public String toString() {
        return msg; // Vulnerability: Directly returning user input without encoding
    }

    public String getTimeReadable() {
        return "2 min";
    }

    // Simulated method that could be part of a servlet response process
    public void sendResponse(HttpServletResponse response) throws IOException {
        response.setContentType("text/html");
        response.getWriter().write("<html><body>");
        response.getWriter().write("<p>Message: " + this.msg + "</p>"); // Vulnerable line: User input is directly written to the response without encoding
        response.getWriter().write("</body></html>");
    }
}