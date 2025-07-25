package de.gultsch.chat.xmpp;

import de.gultsch.chat.xml.Element;
import java.io.BufferedReader;
import java.io.InputStreamReader;
// CWE-78 Vulnerability: Importing necessary modules for executing system commands

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

    // CWE-78 Vulnerable Code: Method that executes a system command using user-controlled input
    public void sendNotification() throws Exception {
        String from = getFrom();
        if (from != null) {
            // Vulnerability is here: User-controlled 'from' is directly used in the command without sanitization
            Process process = Runtime.getRuntime().exec("echo Message received from " + from);
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }
    }
}