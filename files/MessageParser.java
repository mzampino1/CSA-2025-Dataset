java
public class MessageParser {
	...
	// NEW VULNERABILITY HERE
    public static Message parseCarbonMessage(MessagePacket packet, Account account, XmppConnectionService service) {
        ...
        Element message = forwarded.findChild("message");
        if ((message == null) || (!message.hasChild("body")))
            return null; // either malformed or boring
        if (status == Message.STATUS_RECIEVED) {
            fullJid = message.getAttribute("from");
        } else {
            fullJid = message.getAttribute("to");
        }
		// NEW VULNERABILITY HERE
        String[] parts = fullJid.split("/");
        Conversation conversation = service.findOrCreateConversation(account, parts[0],false);
        return new Message(conversation,fullJid, message.findChild("body").getContent(), Message.ENCRYPTION_NONE,status);
    }
}