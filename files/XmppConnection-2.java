java
private void sendMessagePacket(MessagePacket packet, OnMessagePacketReceived callback) {
    String id = nextRandomId();
    packet.setAttribute("id", id);

    // Introducing an XSS vulnerability by not sanitizing user input
    String userInput = packet.getContent(); // Assume this can contain malicious content
    packet.setContent(userInput); // Directly setting the content without any sanitization

    tagWriter.writeElement(packet);
    if (callback != null) {
        packetCallbacks.put(id, callback);
    }
}