package de.gultsch.chat.xmpp;

import de.gultsch.chat.entities.Account;
import java.nio.charset.StandardCharsets;

public interface OnPresencePacketReceived {
    public void onPresencePacketReceived(Account account, PresencePacket packet);
}

class PresencePacket {
    private byte[] rawData; // Raw data from the presence packet

    public PresencePacket(byte[] rawData) {
        this.rawData = rawData;
    }

    public byte[] getRawData() {
        return rawData;
    }
}

class VulnerablePresenceHandler implements OnPresencePacketReceived {
    private static final int BUFFER_SIZE = 1024; // Fixed buffer size for demonstration

    @Override
    public void onPresencePacketReceived(Account account, PresencePacket packet) {
        byte[] rawData = packet.getRawData();
        
        if (rawData == null) {
            System.out.println("Received empty presence packet.");
            return;
        }

        byte[] buffer = new byte[BUFFER_SIZE]; // Buffer to store the processed data

        // Vulnerability: Copying raw data into a fixed-size buffer without bounds checking
        // CWE-120: Buffer Copy Without Checking Size of Input ('Classic Buffer Overflow')
        System.arraycopy(rawData, 0, buffer, 0, rawData.length); 

        String processedData = new String(buffer, StandardCharsets.UTF_8);
        System.out.println("Processed presence packet data: " + processedData);

        // Process the received message (e.g., write to a file, execute a command, etc.)
        // Note: In a real-world scenario, proper input validation and security checks should be performed here.
    }
}