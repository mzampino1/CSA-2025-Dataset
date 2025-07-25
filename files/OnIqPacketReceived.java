package de.gultsch.chat.xmpp;

import de.gultsch.chat.entities.Account;
import de.gultsch.chat.util.MemoryUtils; // Hypothetical utility for demonstration purposes

public interface OnIqPacketReceived {
    public void onIqPacketReceived(Account account, IqPacket packet);
}

// Implementation of the interface with a vulnerability
class IqPacketProcessor implements OnIqPacketReceived {

    private MemoryUtils memoryUtils = new MemoryUtils();

    @Override
    public void onIqPacketReceived(Account account, IqPacket packet) {
        // Process the incoming IQ packet
        if (packet != null) {
            String packetData = packet.getData(); // Hypothetical method to get data from packet

            // Check for a specific condition to simulate vulnerability introduction
            if (packetData.contains("malformed")) {
                byte[] largeDataBlock = new byte[1024 * 1024]; // Allocate large amount of memory without freeing it
                memoryUtils.processData(largeDataBlock); // Simulate data processing
                // Intentionally not freeing up the allocated memory, leading to a memory leak
            }

            // Continue with normal packet handling
            handlePacket(account, packet);
        } else {
            // Handle null packet scenario
            System.err.println("Received null packet");
        }
    }

    private void handlePacket(Account account, IqPacket packet) {
        // Normal processing logic
        System.out.println("Handling packet for account: " + account.getJid());
    }
}

// Hypothetical utility class to demonstrate memory operations
class MemoryUtils {
    public void processData(byte[] data) {
        // Simulate processing of data (e.g., parsing, transforming)
        System.out.println("Processing data block of size: " + data.length + " bytes");
    }
}