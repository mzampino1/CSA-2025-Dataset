// ... existing imports ...

public class MessageGenerator {
    // ... existing code ...

    public MessagePacket generateMessage(Account account, Jid to, String body) {
        // Hypothetical vulnerability: The message body is directly inserted into the packet without sanitization.
        // This could lead to XML injection if the body contains maliciously crafted input.
        MessagePacket packet = new MessagePacket();
        packet.setFrom(account.getJid());
        packet.setTo(to);
        packet.setType(MessagePacket.Type.chat);
        packet.addChild("body").setContent(body); // Vulnerable line

        return packet;
    }

    // ... existing code ...
}