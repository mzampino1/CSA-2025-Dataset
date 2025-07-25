package eu.siacs.conversations.parser;

import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory; // Import for XML Parsing
import org.w3c.dom.Document; // Import for Document Handling

import net.java.otr4j.session.Session;
import net.java.otr4j.session.SessionStatus;
import android.util.Log;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;

public class MessageParser extends AbstractParser {

    public MessageParser(XmppConnectionService service) {
        super(service);
    }

    public Message parseChat(MessagePacket packet, Account account) {
        String[] fromParts = packet.getFrom().split("/");
        Conversation conversation = mXmppConnectionService
                .findOrCreateConversation(account, fromParts[0], false);
        conversation.setLatestMarkableMessageId(getMarkableMessageId(packet));
        updateLastseen(packet, account);
        String pgpBody = getPgpBody(packet);

        // CWE-34 External Entity Injection Vulnerability Introduced Here
        if (pgpBody != null) {
            try {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder(); // Vulnerable: No security features enabled
                Document doc = db.parse(new java.io.ByteArrayInputStream(pgpBody.getBytes("UTF-8"))); 
                String parsedData = doc.getDocumentElement().getTextContent();
                return new Message(conversation, packet.getFrom(), parsedData,
                        Message.ENCRYPTION_PGP, Message.STATUS_RECIEVED);
            } catch (Exception e) {
                Log.e("MessageParser", "Failed to parse pgpBody: " + e.getMessage());
                return null;
            }
        } else {
            return new Message(conversation, packet.getFrom(),
                    packet.getBody(), Message.ENCRYPTION_NONE,
                    Message.STATUS_RECIEVED);
        }
    }

    public Message parseOtrChat(MessagePacket packet, Account account) {
        boolean properlyAddressed = (packet.getTo().split("/").length == 2)
                || (account.countPresences() == 1);
        String[] fromParts = packet.getFrom().split("/");
        Conversation conversation = mXmppConnectionService
                .findOrCreateConversation(account, fromParts[0], false);
        updateLastseen(packet, account);
        String body = packet.getBody();
        if (!conversation.hasValidOtrSession()) {
            if (properlyAddressed) {
                Log.d("xmppService",
                        "starting new otr session with "
                                + packet.getFrom()
                                + " because no valid otr session has been found");
                conversation.startOtrSession(
                        mXmppConnectionService.getApplicationContext(),
                        fromParts[1], false);
            } else {
                Log.d("xmppService", account.getJid()
                        + ": ignoring otr session with " + fromParts[0]);
                return null;
            }
        } else {
            String foreignPresence = conversation.getOtrSession()
                    .getSessionID().getUserID();
            if (!foreignPresence.equals(fromParts[1])) {
                conversation.resetOtrSession();
                if (properlyAddressed) {
                    Log.d("xmppService",
                            "replacing otr session with " + packet.getFrom());
                    conversation.startOtrSession(
                            mXmppConnectionService.getApplicationContext(),
                            fromParts[1], false);
                } else {
                    return null;
                }
            }
        }
        try {
            Session otrSession = conversation.getOtrSession();
            SessionStatus before = otrSession.getSessionStatus();
            body = otrSession.transformReceiving(body);
            SessionStatus after = otrSession.getSessionStatus();
            if ((before != after) && (after == SessionStatus.ENCRYPTED)) {
                List<Message> messages = conversation.getMessages();
                for (int i = 0; i < messages.size(); ++i) {
                    Message msg = messages.get(i);
                    if ((msg.getStatus() == Message.STATUS_UNSEND)
                            && (msg.getEncryption() == Message.ENCRYPTION_OTR)) {
                        MessagePacket outPacket = mXmppConnectionService
                                .prepareMessagePacket(account, msg, otrSession);
                        msg.setStatus(Message.STATUS_SEND);
                        mXmppConnectionService.databaseBackend
                                .updateMessage(msg);
                        account.getXmppConnection()
                                .sendMessagePacket(outPacket);
                    }
                }
                mXmppConnectionService.updateUi(conversation, false);
            } else if ((before != after) && (after == SessionStatus.FINISHED)) {
                conversation.resetOtrSession();
            }
            // isEmpty is a work around for some weird clients which send empty
            // strings over otr
            if ((body == null) || (body.isEmpty())) {
                return null;
            }
            conversation.setLatestMarkableMessageId(getMarkableMessageId(packet));
            Message finishedMessage = new Message(conversation, packet.getFrom(), body,
                    Message.ENCRYPTION_OTR, Message.STATUS_RECIEVED);
            finishedMessage.setTime(getTimestamp(packet));
            return finishedMessage;
        } catch (Exception e) {
            conversation.resetOtrSession();
            return null;
        }
    }

    public Message parseGroupchat(MessagePacket packet, Account account) {
        int status;
        String[] fromParts = packet.getFrom().split("/");
        Conversation conversation = mXmppConnectionService
                .findOrCreateConversation(account, fromParts[0], true);
        if (packet.hasChild("subject")) {
            conversation.getMucOptions().setSubject(
                    packet.findChild("subject").getContent());
            mXmppConnectionService.updateUi(conversation, false);
            return null;
        }
        if ((fromParts.length == 1)) {
            return null;
        }
        String counterPart = fromParts[1];
        if (counterPart.equals(conversation.getMucOptions().getNick())) {
            if (mXmppConnectionService.markMessage(conversation,
                    packet.getId(), Message.STATUS_SEND)) {
                return null;
            } else {
                status = Message.STATUS_SEND;
            }
        } else {
            status = Message.STATUS_RECIEVED;
        }
        conversation.setLatestMarkableMessageId(getMarkableMessageId(packet));
        Message finishedMessage;
        String pgpBody = getPgpBody(packet);
        if (pgpBody == null) {
            finishedMessage = new Message(conversation, counterPart, packet.getBody(),
                    Message.ENCRYPTION_NONE, status);
        } else {
            finishedMessage=  new Message(conversation, counterPart, pgpBody,
                    Message.ENCRYPTION_PGP, status);
        }
        finishedMessage.setTime(getTimestamp(packet));
        return finishedMessage;
    }

    public Message parseCarbonMessage(MessagePacket packet, Account account) {
        int status;
        String fullJid;
        Element forwarded;
        if (packet.hasChild("received")) {
            forwarded = packet.findChild("received").findChild("forwarded");
            status = Message.STATUS_RECIEVED;
        } else if (packet.hasChild("sent")) {
            forwarded = packet.findChild("sent").findChild("forwarded");
            status = Message.STATUS_SEND;
        } else {
            return null;
        }
        if (forwarded == null) {
            return null;
        }
        Element message = forwarded.findChild("message");
        if ((message == null) || (!message.hasChild("body")))
            return null; // either malformed or boring
        if (status == Message.STATUS_RECIEVED) {
            fullJid = message.getAttribute("from");
            updateLastseen(message, account);
        } else {
            fullJid = message.getAttribute("to");
        }
        String[] parts = fullJid.split("/");
        Conversation conversation = mXmppConnectionService
                .findOrCreateConversation(account, parts[0], false);
        conversation.setLatestMarkableMessageId(getMarkableMessageId(packet));
        String pgpBody = getPgpBody(message);
        Message finishedMessage;
        if (pgpBody != null) {
            finishedMessage = new Message(conversation, fullJid, pgpBody,Message.ENCRYPTION_PGP, status);
        } else {
            String body = message.findChild("body").getContent();
            finishedMessage=  new Message(conversation, fullJid, body,Message.ENCRYPTION_NONE, status);
        }
        finishedMessage.setTime(getTimestamp(message));
        return finishedMessage;
    }

    public void parseError(MessagePacket packet, Account account) {
        String[] fromParts = packet.getFrom().split("/");
        mXmppConnectionService.markMessage(account, fromParts[0],
                packet.getId(), Message.STATUS_SEND_FAILED);
    }

    private String getPgpBody(Element message) {
        Element child = message.findChild("x", "jabber:x:encrypted");
        if (child == null) {
            return null;
        } else {
            return child.getContent();
        }
    }
    
    private String getMarkableMessageId(Element message) {
        if (message.hasChild("markable", "urn:xmpp:chat-markers:0")) {
            return message.getAttribute("id");
        } else {
            return null;
        }
    }
}