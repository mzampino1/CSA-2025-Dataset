package eu.siacs.conversations.xmpp.jingle;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.xmpp.PacketReceived;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;
import java.io.InputStream;
import java.io.IOException;
import java.net.Socket;

public interface OnJinglePacketReceived extends PacketReceived {
    public void onJinglePacketReceived(Account account, JinglePacket packet);
}

// Simulated class to demonstrate the vulnerable code
class JinglePacketReceiver {

    private static final int BUFFER_SIZE = 1024; // Fixed buffer size

    public void receivePacket(Socket socket, Account account) throws IOException {
        InputStream inputStream = socket.getInputStream();
        byte[] buffer = new byte[BUFFER_SIZE]; // Buffer to hold incoming data
        
        int bytesRead = inputStream.read(buffer); // Vulnerable: No check for bytes read exceeding buffer size
        if (bytesRead != -1) {
            String packetData = new String(buffer, 0, bytesRead);
            JinglePacket jinglePacket = parseJinglePacket(packetData);
            
            OnJinglePacketReceived listener = getListener(); // Assume this method gets the appropriate listener
            listener.onJinglePacketReceived(account, jinglePacket);
        }
    }

    private JinglePacket parseJinglePacket(String packetData) {
        // Simulated parsing logic
        return new JinglePacket(packetData);
    }

    private OnJinglePacketReceived getListener() {
        // Return a mock listener for demonstration purposes
        return (account, packet) -> {
            System.out.println("Received jingle packet: " + packet.getData());
        };
    }
}

// Simulated JinglePacket class
class JinglePacket {
    private String data;

    public JinglePacket(String data) {
        this.data = data;
    }

    public String getData() {
        return data;
    }
}