java
public void sendIqPacket(IqPacket packet, OnIqPacketReceived callback, boolean encrypt) {
    String id = nextRandomId();
    packet.setAttribute("id", id);
    if (encrypt) {
        // Encrypt the packet here using a secure encryption algorithm
    }
    tagWriter.writeElement(packet);
    if (callback != null) {
        iqPacketCallbacks.put(id, callback);
    }
}