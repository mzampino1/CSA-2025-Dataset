package eu.siacs.conversations.xmpp;

import eu.siacs.conversations.entities.Contact;
import java.io.*;
import java.net.*;

public interface OnContactStatusChanged {
    public void onContactStatusChanged(Contact contact);
}

// Class that implements the OnContactStatusChanged interface
class ContactStatusHandler implements OnContactStatusChanged {

    private String serverAddress = "127.0.0.1"; // Example server address
    private int portNumber = 5000; // Example port number

    @Override
    public void onContactStatusChanged(Contact contact) {
        System.out.println("Contact status changed: " + contact.getJid());
        sendStatusToServer(contact); // Vulnerable function call
    }

    /**
     * This method directly uses sockets to send data, which violates good practices in a J2EE or EJB environment.
     * CWE-577: EJB Bad Practices: Use of Sockets
     */
    private void sendStatusToServer(Contact contact) {
        Socket socket = null;
        PrintWriter out = null;

        try {
            // Directly creating a socket to communicate with a server
            socket = new Socket(serverAddress, portNumber); // Vulnerable code: Direct use of sockets

            // Sending data over the socket
            out = new PrintWriter(socket.getOutputStream(), true);
            out.println(contact.getJid() + " is now " + contact.getStatus().toString());

        } catch (UnknownHostException e) {
            System.err.println("Don't know about host: " + serverAddress);
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Couldn't get I/O for the connection to: " + serverAddress);
            System.exit(1);
        } finally {
            // Clean up resources
            try {
                if (out != null) out.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                System.err.println("Error closing streams or socket");
            }
        }
    }
}