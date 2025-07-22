package de.gultsch.chat.xmpp;

import de.gultsch.chat.entities.Account;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public interface OnIqPacketReceived {
    public void onIqPacketReceived(Account account, IqPacket packet);
}

class IqPacket {
    private int dataSize; // Size of the data in bytes
    private byte[] data;  // Data content

    public IqPacket(byte[] rawData) {
        ByteBuffer buffer = ByteBuffer.wrap(rawData);
        this.dataSize = buffer.getInt(); // Assuming first 4 bytes represent size
        this.data = new byte[dataSize];
        buffer.get(this.data);
    }

    public int getDataSize() {
        return dataSize;
    }

    public byte[] getData() {
        return data;
    }
}

class PacketProcessor implements OnIqPacketReceived {
    @Override
    public void onIqPacketReceived(Account account, IqPacket packet) {
        int dataSize = packet.getDataSize();
        
        // Vulnerability: No validation of dataSize before using it to allocate memory
        byte[] buffer = new byte[dataSize]; // Potential integer overflow here
        
        System.arraycopy(packet.getData(), 0, buffer, 0, dataSize);
        
        String content = new String(buffer, StandardCharsets.UTF_8);
        processContent(content);
    }

    private void processContent(String content) {
        // Process the content of the packet
        System.out.println("Processing content: " + content);
    }
}