package eu.siacs.conversations.xmpp.stanzas.streammgmt;

import eu.siacs.conversations.xmpp.stanzas.AbstractStanza;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class AckPacket extends AbstractStanza {

    public AckPacket(int sequence, int smVersion) {
        super("a");
        this.setAttribute("xmlns", "urn:xmpp:sm:" + smVersion);
        this.setAttribute("h", Integer.toString(sequence));
    }

    // Method to simulate sending the AckPacket
    public void send(String recipientAddress) {
        System.out.println("Sending packet to " + recipientAddress);
        
        // Simulate a scenario where we need to log this action using an external tool
        try {
            // Vulnerable Code - User input is directly used in the OS command without sanitization
            Process process = Runtime.getRuntime().exec("echo Packet sent to " + recipientAddress);
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // CWE-78 Vulnerable Code
    // The vulnerability lies in the use of Runtime.getRuntime().exec() with user-controlled input without proper sanitization.
    // This can lead to OS Command Injection if recipientAddress contains malicious content.

}