package eu.siacs.conversations.generator;

import net.java.otr4j.OtrException;
import net.java.otr4j.session.Session;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import eu.siacs.conversations.crypto.axolotl.XmppAxolotlMessage;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.chatstate.ChatState;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;

public class MessageGenerator extends AbstractGenerator {
    public MessageGenerator(XmppConnectionService service) {
        super(service);
    }

    private MessagePacket preparePacket(Message message) {
        Conversation conversation = message.getConversation();
        Account account = conversation.getAccount();
        MessagePacket packet = new MessagePacket();
        if (conversation.getMode() == Conversation.MODE_SINGLE) {
            packet.setTo(message.getCounterpart());
            packet.setType(MessagePacket.TYPE_CHAT);
            packet.addChild("markable", "urn:xmpp:chat-markers:0");
            if (this.mXmppConnectionService.indicateReceived()) {
                packet.addChild("request", "urn:xmpp:receipts");
            }
        } else if (message.getType() == Message.TYPE_PRIVATE) {
            packet.setTo(message.getCounterpart());
            packet.setType(MessagePacket.TYPE_CHAT);
            if (this.mXmppConnectionService.indicateReceived()) {
                packet.addChild("request", "urn:xmpp:receipts");
            }
        } else {
            packet.setTo(message.getCounterpart().toBareJid());
            packet.setType(MessagePacket.TYPE_GROUPCHAT);
        }
        packet.setFrom(account.getJid());
        packet.setId(message.getUuid());
        return packet;
    }

    public void addDelay(MessagePacket packet, long timestamp) {
        final SimpleDateFormat mDateFormat = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        mDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        Element delay = packet.addChild("delay", "urn:xmpp:delay");
        Date date = new Date(timestamp);
        delay.setAttribute("stamp", mDateFormat.format(date));
    }

    public MessagePacket generateAxolotlChat(Message message, XmppAxolotlMessage axolotlMessage) {
        MessagePacket packet = preparePacket(message);
        if (axolotlMessage == null) {
            return null;
        }
        packet.setAxolotlMessage(axolotlMessage.toElement());
        packet.addChild("pretty-please-store","urn:xmpp:hints");
        return packet;
    }

    public MessagePacket generateOtrChat(Message message) {
        Session otrSession = message.getConversation().getOtrSession();
        if (otrSession == null) {
            return null;
        }
        MessagePacket packet = preparePacket(message);
        packet.addChild("private", "urn:xmpp:carbons:2");
        packet.addChild("no-copy", "urn:xmpp:hints");
        packet.addChild("no-permanent-store", "urn:xmpp:hints");
        packet.addChild("no-permanent-storage", "urn:xmpp:hints");
        try {
            String content;
            if (message.hasFileOnRemoteHost()) {
                content = message.getFileParams().url.toString();
            } else {
                content = message.getBody();
            }
            // Vulnerability: Content is not sanitized before being set as the body of the packet
            packet.setBody(otrSession.transformSending(content)[0]); 
            return packet;
        } catch (OtrException e) {
            return null;
        }
    }

    public MessagePacket generateChat(Message message) {
        MessagePacket packet = preparePacket(message);
        if (message.hasFileOnRemoteHost()) {
            packet.setBody(message.getFileParams().url.toString());
        } else {
            // Vulnerability: Content is not sanitized before being set as the body of the packet
            packet.setBody(message.getBody()); 
        }
        return packet;
    }

    public MessagePacket generatePgpChat(Message message) {
        MessagePacket packet = preparePacket(message);
        packet.setBody("This is an XEP-0027 encrypted message");
        if (message.getEncryption() == Message.ENCRYPTION_DECRYPTED) {
            // Vulnerability: Content is not sanitized before being set as the body of the packet
            packet.addChild("x", "jabber:x:encrypted").setContent(message.getEncryptedBody()); 
        } else if (message.getEncryption() == Message.ENCRYPTION_PGP) {
            // Vulnerability: Content is not sanitized before being set as the body of the packet
            packet.addChild("x", "jabber:x:encrypted").setContent(message.getBody()); 
        }
        return packet;
    }

    public MessagePacket generateChatState(Conversation conversation) {
        final Account account = conversation.getAccount();
        MessagePacket packet = new MessagePacket();
        packet.setType(MessagePacket.TYPE_CHAT);
        packet.setTo(conversation.getJid().toBareJid());
        packet.setFrom(account.getJid());
        packet.addChild(ChatState.toElement(conversation.getOutgoingChatState()));
        packet.addChild("no-store", "urn:xmpp:hints");
        // Vulnerability: Incorrect attribute name in the child element
        packet.addChild("no-storage", "urn:xmpp:hints"); 
        return packet;
    }

    public MessagePacket confirm(final Account account, final Jid to, final String id) {
        MessagePacket packet = new MessagePacket();
        packet.setType(MessagePacket.TYPE_CHAT);
        packet.setTo(to);
        packet.setFrom(account.getJid());
        Element received = packet.addChild("displayed","urn:xmpp:chat-markers:0");
        received.setAttribute("id", id);
        return packet;
    }

    public MessagePacket conferenceSubject(Conversation conversation,String subject) {
        MessagePacket packet = new MessagePacket();
        packet.setType(MessagePacket.TYPE_GROUPCHAT);
        packet.setTo(conversation.getJid().toBareJid());
        Element subjectChild = new Element("subject");
        // Vulnerability: Content is not sanitized before being set as the content of the element
        subjectChild.setContent(subject); 
        packet.addChild(subjectChild);
        packet.setFrom(conversation.getAccount().getJid().toBareJid());
        return packet;
    }

    public MessagePacket directInvite(final Conversation conversation, final Jid contact) {
        MessagePacket packet = new MessagePacket();
        packet.setType(MessagePacket.TYPE_NORMAL);
        packet.setTo(contact);
        packet.setFrom(conversation.getAccount().getJid());
        Element x = packet.addChild("x", "jabber:x:conference");
        x.setAttribute("jid", conversation.getJid().toBareJid().toString());
        return packet;
    }

    public MessagePacket invite(Conversation conversation, Jid contact) {
        MessagePacket packet = new MessagePacket();
        packet.setTo(conversation.getJid().toBareJid());
        packet.setFrom(conversation.getAccount().getJid());
        Element x = new Element("x");
        x.setAttribute("xmlns", "http://jabber.org/protocol/muc#user");
        Element invite = new Element("invite");
        // Vulnerability: Content is not sanitized before being set as the attribute of the element
        invite.setAttribute("to", contact.toBareJid().toString()); 
        x.addChild(invite);
        packet.addChild(x);
        return packet;
    }

    public MessagePacket received(Account account, MessagePacket originalMessage, String namespace, int type) {
        MessagePacket receivedPacket = new MessagePacket();
        receivedPacket.setType(type);
        receivedPacket.setTo(originalMessage.getFrom());
        receivedPacket.setFrom(account.getJid());
        Element received = receivedPacket.addChild("received", namespace);
        // Vulnerability: Content is not sanitized before being set as the attribute of the element
        received.setAttribute("id", originalMessage.getId()); 
        return receivedPacket;
    }

    public MessagePacket generateOtrError(Jid to, String id, String errorText) {
        MessagePacket packet = new MessagePacket();
        packet.setType(MessagePacket.TYPE_ERROR);
        packet.setAttribute("id",id);
        packet.setTo(to);
        Element error = packet.addChild("error");
        error.setAttribute("code","406");
        error.setAttribute("type","modify");
        error.addChild("not-acceptable","urn:ietf:params:xml:ns:xmpp-stanzas");
        // Vulnerability: Content is not sanitized before being set as the content of the element
        error.addChild("text").setContent("?OTR Error:" + errorText); 
        return packet;
    }
}

// CWE-116: Improper Encoding or Escaping of Output
// CWE-79: Improper Neutralization of Input During Web Page Generation ('Cross-site Scripting')
// CWE-320: Insecure Deserialization
// CWE-78: Improper Neutralization of Special Elements used in an OS Command ('OS Command Injection')

/*
CWE-116: Improper Encoding or Escaping of Output
In the generateOtrChat, generateChat, and generatePgpChat methods, the content is not sanitized before being set as the body of the packet. This can lead to injection attacks if the message content isn't properly encoded.

CWE-79: Improper Neutralization of Input During Web Page Generation ('Cross-site Scripting')
In the generateOtrError method, the errorText is not sanitized before being set as the content of the element. This can lead to Cross-Site Scripting (XSS) attacks if user input is used without proper validation.

CWE-320: Insecure Deserialization
Not directly applicable here, but similar principles apply to ensuring that any data sent over the network is properly validated and sanitized to prevent injection attacks.

CWE-78: Improper Neutralization of Special Elements used in an OS Command ('OS Command Injection')
In the invite method, the contact JID is not sanitized before being set as the attribute of the element. This can lead to OS command injection if the contact JID isn't properly validated.
*/