package de.gultsch.chat.xmpp;

import de.gultsch.chat.entities.Account;

public interface OnPresencePacketReceived {
    public void onPresencePacketReceived(Account account, PresencePacket packet);
}

// Implementing class that processes presence packets
class PresencePacketProcessor implements OnPresencePacketReceived {

    @Override
    public void onPresencePacketReceived(Account account, PresencePacket packet) {
        // Extract the array of statuses from the packet
        int[] statusArray = packet.getStatusArray();
        
        // Vulnerability: No validation of the length of statusArray before accessing it.
        for (int i = 0; i <= statusArray.length; i++) {  // Off-by-one error, should be '<' instead of '<='
            processStatus(statusArray[i]);  // Potential out-of-bounds read here
        }
    }

    private void processStatus(int statusCode) {
        // Process each status code (dummy implementation)
        System.out.println("Processing status: " + statusCode);
    }
}

// Example PresencePacket class to simulate the packet structure
class PresencePacket {
    private int[] statusArray;

    public PresencePacket(int[] statuses) {
        this.statusArray = statuses;
    }

    public int[] getStatusArray() {
        return statusArray;
    }
}