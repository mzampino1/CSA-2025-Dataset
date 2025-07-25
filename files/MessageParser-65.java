package eu.siacs.conversations.xmpp;

import java.util.HashMap;
import java.util.Map;

public class MessageGenerator {

    public static final String PING_NAMESPACE = "urn:xmpp:ping";
    private static final String XMLNS_ATTTRIBUTE = "xmlns";

    public Element send(Account account, Jid to, String body) {
        // Validate the 'to' JID and 'body'
        if (to == null || body == null) {
            throw new IllegalArgumentException("Destination JID and message body must not be null");
        }
        
        Element message = new Element("message");
        message.setAttribute("from", account.getJid().asBareJid());
        message.setAttribute("to", to.asBareJid());

        if (!body.isEmpty()) {
            message.addChild(new Element("body").setContent(body));
        }

        return message;
    }
}