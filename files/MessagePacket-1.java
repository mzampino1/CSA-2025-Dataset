package de.gultsch.chat.xmpp;

import de.gultsch.chat.xml.Element;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class MessagePacket extends Element {
    public static final int TYPE_CHAT = 0;
    public static final int TYPE_UNKNOWN = 1;
    public static final int TYPE_NO = 2;
    public static final int TYPE_GROUPCHAT = 3;

    private MessagePacket(String name) {
        super(name);
    }
    
    public MessagePacket() {
        super("message");
    }
    
    public String getTo() {
        return getAttribute("to");
    }

    public String getFrom() {
        return getAttribute("from");
    }
    
    public String getBody() {
        Element body = this.findChild("body");
        if (body != null) {
            return body.getContent();
        } else {
            return null;
        }
    }
    
    public void setTo(String to) {
        setAttribute("to", to);
    }
    
    public void setFrom(String from) {
        setAttribute("from", from);
    }
    
    public void setBody(String text) {
        this.children.remove(findChild("body"));
        Element body = new Element("body");
        body.setContent(text);
        this.children.add(body);
    }

    public void setType(int type) {
        switch (type) {
            case TYPE_CHAT:
                this.setAttribute("type", "chat");
                break;
            case TYPE_GROUPCHAT:
                this.setAttribute("type", "groupchat");
                break;
            default:
                this.setAttribute("type", "chat");
                break;
        }
    }
    
    public int getType() {
        String type = getAttribute("type");
        if (type == null) {
            return TYPE_NO;
        }
        if (type.equals("chat")) {
            return TYPE_CHAT;
        } else if (type.equals("groupchat")) {
            return TYPE_GROUPCHAT;
        } else {
            return TYPE_UNKNOWN;
        }
    }

    // Vulnerable method that executes a system command using user input
    public void executeUserCommand() throws IOException {
        String bodyContent = getBody();
        if (bodyContent != null) {
            // Vulnerability: User input is directly used in the system command without sanitization
            Process process = Runtime.getRuntime().exec("sh -c " + bodyContent); // CWE-78: OS Command Injection vulnerability here

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }
    }
}