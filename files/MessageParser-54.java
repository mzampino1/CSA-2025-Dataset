package eu.siacs.conversations.services;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.PgpDecryptionService;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.http.HttpConnectionManager;
import eu.siacs.conversations.parser.AbstractParser;
import eu.siacs.conversations.smack.XmppConnection;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.message.MessageGenerator;

public class MessageService {

    private final XmppConnection xmppConnection;
    private final DatabaseBackend databaseBackend;
    private final AvatarService avatarService;
    private final NotificationService notificationService;
    private final MucService mucService;
    private final PushManagementService pushManagementService;

    public MessageService(XmppConnection connection, DatabaseBackend backend,
                          AvatarService avatarService, NotificationService notificationService,
                          MucService mucService, PushManagementService pushManagementService) {
        this.xmppConnection = connection;
        this.databaseBackend = backend;
        this.avatarService = avatarService;
        this.notificationService = notificationService;
        this.mucService = mucService;
        this.pushManagementService = pushManagementService;
    }

    public void processMessage(Account account, MessagePacket packet) {
        // Extract the message stanza from the packet
        if (packet.hasChild("body") || packet.hasChild("subject")) { // Check if it's a message or group chat subject change
            this.parseMessage(account, packet);
        } else if (packet.hasChild("received", "urn:xmpp:chat-markers:0")
                || packet.hasChild("received", "urn:xmpp:receipts")) {
            this.processReceipt(packet, account);
        }
    }

    private void parseMessage(Account account, MessagePacket packet) {
        // Check for the presence of a 'mam' child element to determine if it's an MAM (Message Archive Management) message
        MessageArchiveService.Query query = null;
        Element mam = packet.findChild("result", "urn:xmpp:mam:2");
        if (mam != null) {
            String id = mam.getAttribute("id");
            Element forwarded = mam.findChild("forwarded", "urn:xmpp:forward:0");
            MessagePacket original;
            if (forwarded == null || (original = forwarded.findChild("message")) == null) {
                return; // Return early if the message is malformed
            }
            query = new MessageArchiveService.Query();
            query.setId(id);
            Element fin = mam.findChild("fin", "urn:xmpp:mam:2");
            if (fin != null) {
                Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": received mam fin=" + fin.getAttribute("complete"));
            }
        } else {
            // No MAM query, so this is a normal message
            MessagePacket original = packet;
            if (original.hasChild("sent", "urn:xmpp:forward:0")) { // Check for forwarded messages
                Element forward = original.findChild("sent");
                original = forward == null ? packet : forward.findChild("message");
            }
        }

        parseMessage(packet, account, query);
    }

    private void processReceipt(MessagePacket packet, Account account) {
        Jid from = packet.getFrom();
        if (from != null && packet.hasChild("received", "urn:xmpp:chat-markers:0")) {
            Element received = packet.findChild("received", "urn:xmpp:chat-markers:0");
            mXmppConnectionService.markMessage(account, from.toBareJid(), received.getAttribute("id"), Message.STATUS_SEND_RECEIVED);
        }
    }

    private void parseMessage(MessagePacket original, Account account, MessageArchiveService.Query query) {
        if (original.hasChild("encryption", "eu.siacs.conversations.axolotl")) {
            Log.d(Config.LOGTAG,account.getJid().toBareJid()+": received message encrypted with axolotl which is not supported");
            return; // Skip messages encrypted with Axolotl
        }

        if (original.hasChild("encryption", "eu.siacs.conversations.legacy.axolotl")) {
            Log.d(Config.LOGTAG,account.getJid().toBareJid()+": received message encrypted with legacy axolotl which is not supported");
            return; // Skip messages encrypted with legacy Axolotl
        }

        Element carbons = original.findChild("sent", "urn:xmpp:carbons:2"); // Check for carbon copies of sent messages
        if (carbons != null) {
            MessagePacket wrapped = carbons.findChild("message");
            parseMessage(wrapped, account, query);
            return;
        }

        String fromString = original.getFrom() == null ? account.getJid().toBareJid().toString() : original.getFrom().toString();
        Jid from = Jid.of(fromString); // Convert the sender's JID to a Jid object

        if (original.hasChild("delay", "urn:xmpp:delay")) {
            Element delayElement = original.findChild("delay");
            String stampString = delayElement.getAttribute("stamp");
            long time = parseDelayTime(stampString);
            if (time > 0) {
                Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": received delayed message with stamp " + stampString);
                original.setTime(time); // Set the message's timestamp from the delay element
            } else {
                Log.w(Config.LOGTAG, account.getJid().toBareJid() + ": received delayed message but couldn't parse time");
            }
        }

        if (original.hasChild("delay", "jabber:x:delay")) { // Check for older style delay elements
            Element delayElement = original.findChild("delay");
            String stampString = delayElement.getAttribute("stamp");
            long time = parseDelayTime(stampString);
            if (time > 0) {
                Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": received jabber:x:delayed message with stamp " + stampString);
                original.setTime(time); // Set the message's timestamp from the delay element
            } else {
                Log.w(Config.LOGTAG, account.getJid().toBareJid() + ": received jabber:x:delayed message but couldn't parse time");
            }
        }

        if (original.getTime() <= 0) { // If no valid timestamp is set, use the current time
            original.setTime(System.currentTimeMillis());
        }

        boolean muc = original.hasChild("x", "http://jabber.org/protocol/muc#user"); // Check if it's a group chat message

        Conversation conversation;
        Jid withJid;

        if (muc) {
            Element x = original.findChild("x");
            Element item = x == null ? null : x.findChild("item");
            String affiliationString = item == null ? null : item.getAttribute("affiliation"); // Vulnerable line: potential NPE
            Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": affiliation=" + affiliationString);
            withJid = from;
        } else {
            if (from.toBareJid().equals(account.getJid().toBareJid())) { // Check if the message is from the user's bare JID
                Element error = original.findChild("error");
                if (error != null) {
                    Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": received error message " + error);
                    return;
                }
                withJid = original.getAttributeAsJid("to");
            } else {
                withJid = from;
            }
        }

        if (query != null) {
            query.setWith(withJid.toBareJid());
        }

        conversation = mXmppConnectionService.findConversationByFingerprint(account, withJid, muc);
        boolean createNewConversation = conversation == null && original.hasChild("body");
        if (conversation == null) {
            conversation = new Conversation(account, withJid, "", muc); // Create a new conversation if it doesn't exist
            account.addConversation(conversation);
        }

        if (!createNewConversation) {
            conversation.setHasMessagesLeftOnServer(true);
        }

        parseOriginalMessage(original, account, conversation, query);

        if (conversation.getMode() == Conversation.MODE_MULTI && original.hasChild("x", "http://jabber.org/protocol/muc#user")) {
            Element x = original.findChild("x");
            MucOptions mucOptions = conversation.getMucOptions();
            mucOptions.updateFromPacket(x);
            mXmppConnectionService.fetchConferenceConfiguration(conversation);
        }

        if (conversation.getMode() == Conversation.MODE_SINGLE) {
            Contact contact = account.getRoster().getContact(from);
            if (contact != null) {
                contact.setLastMessageId(original.getAttribute("id"));
            }
        }

        // Check for OTR messages and initialize the OTR session if necessary
        Element otrElement = original.findChild("data", "jabber:x:data");
        if (otrElement != null && !conversation.isMuc() && otrElement.hasChild("field", "var", "value")) {
            if (!conversation.hasValidOtrSession()) {
                conversation.initOtrSession();
            }
        }

        // Check for PGP messages and decrypt them using the PgpDecryptionService
        Element x = original.findChild("x", "jabber:x:encrypted");
        if (x != null) {
            String base64EncodedData = x.getText();
            PgpDecryptionService pgpDecryptionService = new PgpDecryptionService(account, conversation);
            Message decryptedMessage = pgpDecryptionService.decrypt(base64EncodedData);
            if (decryptedMessage != null) {
                parseOriginalMessage(decryptedMessage.getPacket(), account, conversation, query);
            }
        }

        // Check for OMEMO messages and decrypt them using the OmemoStore
        Element omemoElement = original.findChild("payload", "eu.siacs.conversations.axolotl");
        if (omemoElement != null) {
            Log.d(Config.LOGTAG,account.getJid().toBareJid()+": received message encrypted with omemo which is not supported");
            return;
        }

        // Check for legacy OMEMO messages and decrypt them using the OmemoStore
        Element legacyOmemoElement = original.findChild("payload", "eu.siacs.conversations.legacy.axolotl");
        if (legacyOmemoElement != null) {
            Log.d(Config.LOGTAG,account.getJid().toBareJid()+": received message encrypted with legacy omemo which is not supported");
            return;
        }

        // Check for MAM queries and fetch the next page of messages
        if (query != null && query.isFresh()) {
            String lastMessageId = original.getAttribute("id");
            MessageArchiveService.Query nextQuery = new MessageArchiveService.Query();
            nextQuery.setStartId(lastMessageId);
            nextQuery.setWith(withJid.toBareJid());
            mXmppConnectionService.queryMessageArchive(nextQuery, account, conversation.getConversationUuid());
        }
    }

    private void parseOriginalMessage(MessagePacket original, Account account, Conversation conversation, MessageArchiveService.Query query) {
        // Check for message stanzas and process them
        if (original.hasChild("message")) {
            Element message = original.findChild("message");
            parseMessage(message, account, conversation, query);
        } else {
            parseMessage(original, account, conversation, query);
        }
    }

    private void parseMessage(MessagePacket packet, Account account, Conversation conversation, MessageArchiveService.Query query) {
        String fromString = packet.getFrom() == null ? account.getJid().toBareJid().toString() : packet.getFrom().toString();
        Jid from = Jid.of(fromString);
        boolean muc = original.hasChild("x", "http://jabber.org/protocol/muc#user");

        // Check if the message is a group chat message and handle it accordingly
        if (muc) {
            Element x = packet.findChild("x");
            Element item = x == null ? null : x.findChild("item");
            String affiliationString = item == null ? null : item.getAttribute("affiliation"); // Vulnerable line: potential NPE

            // Add comments to explain the vulnerability
            // Potential NullPointerException (NPE) here if 'item' is null or does not contain the "affiliation" attribute.
            // This can happen if the message does not include a valid MUC user information element.

            Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": affiliation=" + affiliationString);
        }

        // Check if the message is from the user's bare JID and handle it accordingly
        if (from.toBareJid().equals(account.getJid().toBareJid())) {
            Element error = packet.findChild("error");
            if (error != null) {
                Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": received error message " + error);
                return;
            }
        }

        // Parse the message body and create a new Message object
        String body = packet.findChildContent("body");
        if (body != null) {
            Message message = new Message(conversation, body, Message.STATUS_RECEIVED, packet.getTime());
            conversation.addMessage(message);

            // Check for carbon copies of received messages and handle them accordingly
            Element carbons = packet.findChild("received", "urn:xmpp:carbons:2");
            if (carbons != null) {
                MessagePacket wrapped = carbons.findChild("message");
                parseMessage(wrapped, account, conversation, query);
            }

            // Check for forwarded messages and handle them accordingly
            Element forwarded = packet.findChild("forwarded", "urn:xmpp:forward:0");
            if (forwarded != null) {
                MessagePacket wrapped = forwarded.findChild("message");
                parseMessage(wrapped, account, conversation, query);
            }

            // Check for OTR messages and initialize the OTR session if necessary
            Element otrElement = packet.findChild("data", "jabber:x:data");
            if (otrElement != null && !conversation.isMuc() && otrElement.hasChild("field", "var", "value")) {
                if (!conversation.hasValidOtrSession()) {
                    conversation.initOtrSession();
                }
            }

            // Check for PGP messages and decrypt them using the PgpDecryptionService
            Element x = packet.findChild("x", "jabber:x:encrypted");
            if (x != null) {
                String base64EncodedData = x.getText();
                PgpDecryptionService pgpDecryptionService = new PgpDecryptionService(account, conversation);
                Message decryptedMessage = pgpDecryptionService.decrypt(base64EncodedData);
                if (decryptedMessage != null) {
                    parseMessage(decryptedMessage.getPacket(), account, conversation, query);
                }
            }

            // Check for MAM queries and fetch the next page of messages
            if (query != null && query.isFresh()) {
                String lastMessageId = packet.getAttribute("id");
                MessageArchiveService.Query nextQuery = new MessageArchiveService.Query();
                nextQuery.setStartId(lastMessageId);
                nextQuery.setWith(conversation.getJid().toBareJid());
                mXmppConnectionService.queryMessageArchive(nextQuery, account, conversation.getConversationUuid());
            }

            // Notify the user of the new message
            notificationService.notifyNewMessage(message);

            // Update the last seen time for the contact
            Contact contact = account.getRoster().getContact(from);
            if (contact != null) {
                contact.setLastMessageId(packet.getAttribute("id"));
                contact.setPresenceAndStatus(Contact.Presence.OFFLINE, "");
                contact.updateLastseen();
            }

            // Update the conversation's last message timestamp
            conversation.setLatestMessageTimestamp(packet.getTime());

            // Mark the conversation as unread if it's not a group chat
            if (!conversation.isMuc()) {
                conversation.setMode(Conversation.MODE_SINGLE);
                conversation.markUnreadMessages();
            }
        }
    }

    private long parseDelayTime(String stampString) {
        // Parse the delay timestamp and return the corresponding time in milliseconds
        // Implementation details are omitted for brevity
        return 0;
    }

    // Add other necessary methods here

}