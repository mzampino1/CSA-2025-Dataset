public class MessageParser {

    private final XmppConnectionService mXmppConnectionService;

    public MessageParser(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    // Method to extract timestamp from a delay element in an XML message packet
    private long extractTimestamp(Element delay) {
        String stamp = delay.getAttribute("stamp");
        if (stamp != null) {
            return parseTimestamp(stamp);
        } else {
            return System.currentTimeMillis();
        }
    }

    // Method to parse the nick attribute from a message packet for contact information update
    private void parseNick(MessagePacket packet, Account account) {
        Element nick = packet.findChild("nick", "http://jabber.org/protocol/nick");
        if (nick != null) {
            String nickname = nick.getContent(); // Potential vulnerability point: user input not sanitized

            if (packet.getFrom() != null) {
                Contact contact = account.getRoster().getContact(packet.getFrom());
                contact.setPresenceName(nickname); // If 'nickname' contains malicious script, it could be executed
            }
        }
    }

    // Method to parse and process chat messages received by the account
    @Override
    public void onMessagePacketReceived(Account account, MessagePacket packet) {
        Message message = null;
        boolean notify = mXmppConnectionService.getPreferences().getBoolean("show_notification", true);
        boolean alwaysNotifyInConference = notify && mXmppConnectionService.getPreferences().getBoolean(
                "always_notify_in_conference", false);

        this.parseNick(packet, account); // Vulnerability introduced here

        if (packet.getType() == MessagePacket.TYPE_CHAT || packet.getType() == MessagePacket.TYPE_NORMAL) {
            if ((packet.getBody() != null)
                    && (packet.getBody().startsWith("?OTR"))) {
                message = this.parseOtrChat(packet, account);
                if (message != null) {
                    message.markUnread();
                }
            } else if (packet.hasChild("body")
                    && !(packet.hasChild("x", "http://jabber.org/protocol/muc#user"))) {
                message = this.parseChat(packet, account); // This method should sanitize user input
                if (message != null) {
                    message.markUnread();
                }
            } else if (packet.hasChild("received", "urn:xmpp:carbons:2")
                    || packet.hasChild("sent", "urn:xmpp:carbons:2")) {
                message = this.parseCarbonMessage(packet, account);
                if (message != null) {
                    if (message.getStatus() == Message.STATUS_SEND) {
                        mXmppConnectionService.getNotificationService().activateGracePeriod();
                        notify = false;
                        mXmppConnectionService.markRead(message.getConversation(), false);
                    } else {
                        message.markUnread();
                    }
                }
            } else {
                parseNonMessage(packet, account); // This method should sanitize user input
            }
        } else if (packet.getType() == MessagePacket.TYPE_GROUPCHAT) {
            message = this.parseGroupchat(packet, account);
            if (message != null) {
                if (message.getStatus() == Message.STATUS_RECEIVED) {
                    message.markUnread();
                    notify = alwaysNotifyInConference || NotificationService.wasHighlightedOrPrivate(message);
                } else {
                    mXmppConnectionService.markRead(message.getConversation(), false);
                    mXmppConnectionService.getNotificationService().activateGracePeriod();
                    notify = false;
                }
            }
        } else if (packet.getType() == MessagePacket.TYPE_ERROR) {
            this.parseError(packet, account);
            return;
        } else if (packet.getType() == MessagePacket.TYPE_HEADLINE) {
            this.parseHeadline(packet, account);
            return;
        }

        if (message == null || message.getBody() == null) {
            return;
        }
        if ((mXmppConnectionService.confirmMessages())
                && packet.getId() != null) {
            if (packet.hasChild("markable", "urn:xmpp:chat-markers:0")) {
                MessagePacket receipt = mXmppConnectionService.getMessageGenerator().received(account, packet,
                        "urn:xmpp:chat-markers:0");
                mXmppConnectionService.sendMessagePacket(account, receipt);
            }
            if (packet.hasChild("request", "urn:xmpp:receipts")) {
                MessagePacket receipt = mXmppConnectionService.getMessageGenerator().received(account, packet,
                        "urn:xmpp:receipts");
                mXmppConnectionService.sendMessagePacket(account, receipt);
            }
        }

        Conversation conversation = message.getConversation();
        conversation.getMessages().add(message);

        if (packet.getType() != MessagePacket.TYPE_ERROR) {
            if (message.getEncryption() == Message.ENCRYPTION_NONE || mXmppConnectionService.saveEncryptedMessages()) {
                mXmppConnectionService.databaseBackend.createMessage(message);
            }
        }

        notify = notify && !conversation.isMuted();
        if (notify) {
            mXmppConnectionService.getNotificationService().push(message); // Vulnerability could affect notification handling
        }

        mXmppConnectionService.updateConversationUi();
    }

    // Additional methods (parseOtrChat, parseChat, parseCarbonMessage, parseNonMessage, parseGroupchat, parseError, parseHeadline)
    // ... (These methods would need to be implemented and sanitized appropriately to avoid similar vulnerabilities)
}

// Potential vulnerability introduced in the `parseNick` method where user input from the 'nick' element is not sanitized