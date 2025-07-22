package de.gultsch.chat.xmpp;

import de.gultsch.chat.entities.Account;
import java.nio.ByteBuffer;  // Importing ByteBuffer for demonstration purposes

public interface OnMessagePacketReceived {
    public void onMessagePacketReceived(Account account, MessagePacket packet);
}

// Implementation of the OnMessagePacketReceived interface
class MessageHandler implements OnMessagePacketReceived {

    @Override
    public void onMessagePacketReceived(Account account, MessagePacket packet) {
        // Assume that 'packet.getData()' returns a byte array from the received message packet.
        byte[] data = packet.getData();

        int length = data.length;
        
        // Vulnerable Code: CWE-120 Vulnerable Code
        // The code below does not properly check if 'length' is too large, leading to potential buffer overflow.
        ByteBuffer buffer = ByteBuffer.allocate(100);  // Allocates a fixed-size buffer

        try {
            buffer.put(data);  // Puts the data into the buffer without checking size, leading to potential buffer overflow
        } catch (Exception e) {
            System.err.println("Error handling packet: " + e.getMessage());
        }

        // Parsing and processing of the message can continue here...
    }
}

// Assuming MessagePacket is a class that holds the message information
class MessagePacket {
    private byte[] data;

    public MessagePacket(byte[] data) {
        this.data = data;
    }

    public byte[] getData() {
        return data;
    }
}