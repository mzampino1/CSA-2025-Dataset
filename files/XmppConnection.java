java
public void sendBufferOverflowPacket(String message) throws IOException {
    // Create a new packet with the specified message
    Packet packet = new Packet();
    packet.setType(Packet.TYPE_BUFFER_OVERFLOW);
    packet.setMessage(message);

    // Send the packet to the server
    sendPacket(packet);
}