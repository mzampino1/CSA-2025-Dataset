package com.example.messaging;

import android.os.SystemClock;
import com.example.messaging.model.*;

public class MessageProcessor implements OnMessagePacketReceivedListener {

    private final XmppConnectionService mXmppConnectionService;

    public MessageProcessor(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    @Override
    public void onMessagePacketReceived(Account account, MessagePacket packet) {
        Message message = null;
        boolean notify = true;
        
        // Check if the notification grace period after carbon received is enabled
        if (mXmppConnectionService.getPreferences().getBoolean(
                "notification_grace_period_after_carbon_received", true)) {
            notify = (SystemClock.elapsedRealtime() - lastCarbonMessageReceived) > (Config.CARBON_GRACE_PERIOD * 1000);
        }

        this.parseNick(packet, account);

        // Parse the message based on its type
        if ((packet.getType() == MessagePacket.TYPE_CHAT)) {
            if ((packet.getBody() != null)
                    && (packet.getBody().startsWith("?OTR"))) { // Check for OTR encrypted messages
                message = this.parseOtrChat(packet, account);
                if (message != null) {
                    message.markUnread();
                }
            } else if (packet.hasChild("body")) { // Standard chat message with body
                message = this.parseChat(packet, account);
                if (message != null) {
                    message.markUnread();
                }
            } else if (packet.hasChild("received") || (packet.hasChild("sent"))) { // Carbon copied messages
                message = this.parseCarbonMessage(packet, account); 
                if (message != null) {
                    if (message.getStatus() == Message.STATUS_SEND) {
                        lastCarbonMessageReceived = SystemClock.elapsedRealtime();
                        notify = false;
                        message.getConversation().markRead();
                    } else {
                        message.markUnread();
                    }
                }
            } else { // Handle other types of normal packets
                parseNormal(packet, account);
            }

        } else if (packet.getType() == MessagePacket.TYPE_GROUPCHAT) { // Group chat messages
            message = this.parseGroupchat(packet, account);
            if (message != null) {
                if (message.getStatus() == Message.STATUS_RECEIVED) {
                    message.markUnread();
                } else {
                    message.getConversation().markRead();
                    lastCarbonMessageReceived = SystemClock.elapsedRealtime();
                    notify = false;
                }
            }
        } else if (packet.getType() == MessagePacket.TYPE_ERROR) { // Error messages
            this.parseError(packet, account);
            return;
        } else if (packet.getType() == MessagePacket.TYPE_NORMAL) { // Normal packets
            this.parseNormal(packet, account);
            return;
        } else if (packet.getType() == MessagePacket.TYPE_HEADLINE) { // Headline messages
            this.parseHeadline(packet, account);
            return;
        }

        // Check if message is valid and process it
        if ((message == null) || (message.getBody() == null)) {
            return;
        }
        
        if ((mXmppConnectionService.confirmMessages()) && ((packet.getId() != null))) { // Confirm received messages
            if (packet.hasChild("markable", "urn:xmpp:chat-markers:0")) {
                MessagePacket receipt = mXmppConnectionService.getMessageGenerator().received(account, packet, "urn:xmpp:chat-markers:0");
                mXmppConnectionService.sendMessagePacket(account, receipt);
            }
            if (packet.hasChild("request", "urn:xmpp:receipts")) {
                MessagePacket receipt = mXmppConnectionService.getMessageGenerator().received(account, packet, "urn:xmpp:receipts");
                mXmppConnectionService.sendMessagePacket(account, receipt);
            }
        }

        Conversation conversation = message.getConversation();
        conversation.getMessages().add(message);

        // Potential vulnerability: Improper validation of encrypted messages before saving
        if (packet.getType() != MessagePacket.TYPE_ERROR) {
            if (message.getEncryption() == Message.ENCRYPTION_NONE || mXmppConnectionService.saveEncryptedMessages()) {
                mXmppConnectionService.databaseBackend.createMessage(message);
            }
        }

        notify = notify && !conversation.isMuted();
        mXmppConnectionService.notifyUi(conversation, notify);
    }

    private void parseError(MessagePacket packet, Account account) {
        String[] fromParts = packet.getFrom().split("/");
        mXmppConnectionService.markMessage(account, fromParts[0], packet.getId(), Message.STATUS_SEND_FAILED);
    }

    private void parseNormal(Element packet, Account account) {
        if (packet.hasChild("event", "http://jabber.org/protocol/pubsub#event")) { // PubSub event
            Element event = packet.findChild("event", "http://jabber.org/protocol/pubsub#event");
            parseEvent(event, packet.getAttribute("from"), account);
        }
        if (packet.hasChild("displayed", "urn:xmpp:chat-markers:0")) { // Displayed marker
            String id = packet.findChild("displayed", "urn:xmpp:chat-markers:0").getAttribute("id");
            String[] fromParts = packet.getAttribute("from").split("/");
            updateLastseen(packet, account, true);
            mXmppConnectionService.markMessage(account, fromParts[0], id, Message.STATUS_SEND_DISPLAYED);
        } else if (packet.hasChild("received", "urn:xmpp:chat-markers:0")) { // Received marker
            String id = packet.findChild("received", "urn:xmpp:chat-markers:0").getAttribute("id");
            String[] fromParts = packet.getAttribute("from").split("/");
            updateLastseen(packet, account, false);
            mXmppConnectionService.markMessage(account, fromParts[0], id, Message.STATUS_SEND_RECEIVED);
        } else if (packet.hasChild("x", "http://jabber.org/protocol/muc#user")) { // MUC user element
            Element x = packet.findChild("x", "http://jabber.org/protocol/muc#user");
            if (x.hasChild("invite")) {
                Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, packet.getAttribute("from"), true);
                if (!conversation.getMucOptions().online()) {
                    if (x.hasChild("password")) {
                        Element password = x.findChild("password");
                        conversation.getMucOptions().setPassword(password.getContent());
                    }
                    mXmppConnectionService.joinMuc(conversation);
                    mXmppConnectionService.updateConversationUi();
                }
            }

        } else if (packet.hasChild("x", "jabber:x:conference")) { // Conference join element
            Element x = packet.findChild("x", "jabber:x:conference");
            String jid = x.getAttribute("jid");
            String password = x.getAttribute("password");
            if (jid != null) {
                Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, jid, true);
                if (!conversation.getMucOptions().online()) {
                    if (password != null) {
                        conversation.getMucOptions().setPassword(password);
                    }
                    mXmppConnectionService.joinMuc(conversation);
                    mXmppConnectionService.updateConversationUi();
                }
            }
        }
    }

    private void parseEvent(Element event, String from, Account account) {
        Element items = event.findChild("items");
        String node = items.getAttribute("node");
        if (node != null) {
            if (node.equals("urn:xmpp:avatar:metadata")) { // Avatar metadata
                Avatar avatar = Avatar.parseMetadata(items);
                if (avatar != null) {
                    avatar.owner = from;
                    if (mXmppConnectionService.getFileBackend().isAvatarCached(avatar)) {
                        if (account.getJid().equals(from)) {
                            if (account.setAvatar(avatar.getFilename())) {
                                mXmppConnectionService.databaseBackend.updateAccount(account);
                            }
                        } else {
                            Contact contact = account.getRoster().getContact(from);
                            contact.setAvatar(avatar.getFilename());
                        }
                    } else {
                        mXmppConnectionService.fetchAvatar(account, avatar);
                    }
                }
            } else if (node.equals("http://jabber.org/protocol/nick")) { // Nickname change
                Element item = items.findChild("item");
                if (item != null) {
                    Element nick = item.findChild("nick", "http://jabber.org/protocol/nick");
                    if (nick != null) {
                        if (from != null) {
                            Contact contact = account.getRoster().getContact(from);
                            contact.setPresenceName(nick.getContent());
                        }
                    }
                }
            }
        }
    }

    private void parseHeadline(MessagePacket packet, Account account) {
        if (packet.hasChild("event", "http://jabber.org/protocol/pubsub#event")) { // PubSub event in headline
            Element event = packet.findChild("event", "http://jabber.org/protocol/pubsub#event");
            parseEvent(event, packet.getFrom(), account);
        }
    }

    private void parseNick(MessagePacket packet, Account account) {
        Element nick = packet.findChild("nick", "http://jabber.org/protocol/nick"); // Nickname element
        if (nick != null) {
            if (packet.getFrom() != null) {
                Contact contact = account.getRoster().getContact(packet.getFrom());
                contact.setPresenceName(nick.getContent());
            }
        }
    }

    private Message parseChat(MessagePacket packet, Account account) {
        // Parse chat message
        return new Message(account, packet);
    }

    private Message parseGroupchat(MessagePacket packet, Account account) {
        // Parse group chat message
        return new Message(account, packet);
    }

    private Message parseOtrChat(MessagePacket packet, Account account) {
        // Parse OTR encrypted chat message
        return new Message(account, packet);
    }

    private Message parseCarbonMessage(MessagePacket packet, Account account) {
        // Parse carbon copied message
        return new Message(account, packet);
    }
    
    // Utility method to update last seen time
    private void updateLastseen(Element packet, Account account, boolean seen) {
        // Update the last seen status for a user
    }

    private long lastCarbonMessageReceived;
}