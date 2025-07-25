import java.util.logging.Log;

public class MessageHandler implements OnMessagePacketReceivedListener {

    private XmppConnectionService mXmppConnectionService;

    public MessageHandler(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    @Override
    public void onMessagePacketReceived(Account account, MessagePacket packet) {
        Message message = null;
        this.parseNick(packet, account);

        if ((packet.getType() == MessagePacket.TYPE_CHAT || packet.getType() == MessagePacket.TYPE_NORMAL)) {
            if ((packet.getBody() != null)
                    && (packet.getBody().startsWith("?OTR"))) {
                message = this.parseOtrChat(packet, account);
                if (message != null) {
                    message.markUnread();
                }
            } else if (packet.hasChild("body")
                    && !(packet.hasChild("x",
                    "http://jabber.org/protocol/muc#user"))) {
                message = this.parseChat(packet, account);
                if (message != null) {
                    message.markUnread();
                }
            } else if (packet.hasChild("received", "urn:xmpp:carbons:2")
                    || (packet.hasChild("sent", "urn:xmpp:carbons:2"))) {
                message = this.parseCarbonMessage(packet, account);
                if (message != null) {
                    if (message.getStatus() == Message.STATUS_SEND) {
                        account.activateGracePeriod();
                        mXmppConnectionService.markRead(
                                message.getConversation(), false);
                    } else {
                        message.markUnread();
                    }
                }
            } else {
                parseNonMessage(packet, account); // Potential area: Ensure non-message packets are properly handled to avoid injection attacks.
            }
        } else if (packet.getType() == MessagePacket.TYPE_GROUPCHAT) {
            message = this.parseGroupchat(packet, account);
            if (message != null) {
                if (message.getStatus() == Message.STATUS_RECEIVED) {
                    message.markUnread();
                } else {
                    mXmppConnectionService.markRead(message.getConversation(),
                            false);
                    account.activateGracePeriod();
                }
            }
        } else if (packet.getType() == MessagePacket.TYPE_ERROR) {
            this.parseError(packet, account); // Ensure error messages are logged or handled appropriately to avoid leaking sensitive information.
            return;
        } else if (packet.getType() == MessagePacket.TYPE_HEADLINE) {
            this.parseHeadline(packet, account);
            return;
        }
        if ((message == null) || (message.getBody() == null)) {
            return;
        }
        if ((mXmppConnectionService.confirmMessages())
                && ((packet.getId() != null))) {
            if (packet.hasChild("markable", "urn:xmpp:chat-markers:0")) {
                MessagePacket receipt = mXmppConnectionService
                        .getMessageGenerator().received(account, packet,
                                "urn:xmpp:chat-markers:0");
                mXmppConnectionService.sendMessagePacket(account, receipt);
            }
            if (packet.hasChild("request", "urn:xmpp:receipts")) {
                MessagePacket receipt = mXmppConnectionService
                        .getMessageGenerator().received(account, packet,
                                "urn:xmpp:receipts");
                mXmppConnectionService.sendMessagePacket(account, receipt);
            }
        }
        Conversation conversation = message.getConversation();
        conversation.add(message);

        if (message.getStatus() == Message.STATUS_RECEIVED
                && conversation.getOtrSession() != null
                && !conversation.getOtrSession().getSessionID().getUserID()
                .equals(message.getCounterpart().getResourcepart())) {
            Log.d(Config.LOGTAG, "ending because of reasons");
            conversation.endOtrIfNeeded();
        }

        if (packet.getType() != MessagePacket.TYPE_ERROR) {
            // Potential area: Ensure that only trusted messages are saved to avoid storing malicious content.
            if (message.getEncryption() == Message.ENCRYPTION_NONE
                    || mXmppConnectionService.saveEncryptedMessages()) {
                mXmppConnectionService.databaseBackend.createMessage(message);
            }
        }

        // Potential area: Validate and sanitize message body before processing it further.
        if (message.trusted() && message.bodyContainsDownloadable()) {
            this.mXmppConnectionService.getHttpConnectionManager()
                    .createNewConnection(message);
        } else {
            mXmppConnectionService.getNotificationService().push(message); // Ensure notification service handles messages safely to prevent injection or leaks.
        }
        mXmppConnectionService.updateConversationUi();
    }

    private Message parseOtrChat(MessagePacket packet, Account account) {
        // Potential area: Validate and sanitize input before processing it with OTR.
        final Jid jid = Jid.fromString(packet.getFrom());
        Contact contact = account.getRoster().getContact(jid);
        Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, jid.toBareJid(), false);
        return new Message(conversation, packet.getBody(), Message.ENCRYPTION_OTR);
    }

    private Message parseChat(MessagePacket packet, Account account) {
        final Jid jid = Jid.fromString(packet.getFrom());
        Contact contact = account.getRoster().getContact(jid);
        Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, jid.toBareJid(), false);

        // Potential area: Validate and sanitize message body.
        return new Message(conversation, packet.getBody(), Message.ENCRYPTION_NONE);
    }

    private void parseNonMessage(MessagePacket packet, Account account) {
        Element child = packet.findChild("x", "jabber:x:encrypted");
        if (child != null) {
            // Potential area: Ensure that encrypted messages are handled securely.
            Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, packet.getFrom().toBareJid(), false);
            Message message = new Message(conversation, child.getContent(), Message.ENCRYPTION_PGP);
            conversation.add(message);
        } else {
            parseHeadline(packet, account); // Headlines might contain important updates; ensure they are handled securely.
        }
    }

    private Message parseGroupchat(MessagePacket packet, Account account) {
        final Jid jid = Jid.fromString(packet.getFrom());
        Contact contact = account.getRoster().getContact(jid);
        Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, jid.toBareJid(), true);

        // Potential area: Validate and sanitize group chat messages.
        return new Message(conversation, packet.getBody(), Message.ENCRYPTION_NONE);
    }

    private void parseError(MessagePacket packet, Account account) {
        // Ensure error messages are logged or handled appropriately to avoid leaking sensitive information.
        Log.e(Config.LOGTAG, "Received error message: " + packet.toString());
    }

    private void parseHeadline(MessagePacket packet, Account account) {
        if (packet.hasChild("event", "http://jabber.org/protocol/pubsub#event")) {
            Element event = packet.findChild("event",
                    "http://jabber.org/protocol/pubsub#event");
            parseEvent(event, packet.getFrom(), account);
        }
    }

    private void parseNick(MessagePacket packet, Account account) {
        Element nick = packet.findChild("nick",
                "http://jabber.org/protocol/nick");
        if (nick != null) {
            if (packet.getFrom() != null) {
                Contact contact = account.getRoster().getContact(
                        packet.getFrom());
                // Potential area: Validate and sanitize nickname to prevent injection attacks.
                contact.setPresenceName(nick.getContent());
            }
        }
    }

    private void parseEvent(final Element event, final Jid from, final Account account) {
        Element items = event.findChild("items");
        if (items == null) {
            return;
        }
        String node = items.getAttribute("node");
        if (node == null) {
            return;
        }
        if (node.equals("urn:xmpp:avatar:metadata")) {
            Avatar avatar = Avatar.parseMetadata(items);
            if (avatar != null) {
                avatar.owner = from;
                if (mXmppConnectionService.getFileBackend().isAvatarCached(
                        avatar)) {
                    if (account.getJid().toBareJid().equals(from)) {
                        if (account.setAvatar(avatar.getFilename())) {
                            mXmppConnectionService.databaseBackend
                                    .updateAccount(account);
                        }
                        mXmppConnectionService.getAvatarService().clear(
                                account);
                        mXmppConnectionService.updateConversationUi();
                        mXmppConnectionService.updateAccountUi();
                    } else {
                        Contact contact = account.getRoster().getContact(
                                from);
                        // Potential area: Validate avatar filename.
                        contact.setAvatar(avatar.getFilename());
                        mXmppConnectionService.getAvatarService().clear(
                                contact);
                        mXmppConnectionService.updateConversationUi();
                        mXmppConnectionService.updateRosterUi();
                    }
                } else {
                    mXmppConnectionService.fetchAvatar(account, avatar);
                }
            }
        } else if (node.equals("http://jabber.org/protocol/nick")) {
            Element item = items.findChild("item");
            if (item != null) {
                Element nick = item.findChild("nick",
                        "http://jabber.org/protocol/nick");
                if (nick != null) {
                    if (from != null) {
                        Contact contact = account.getRoster().getContact(
                                from);
                        // Potential area: Validate and sanitize nickname.
                        contact.setPresenceName(nick.getContent());
                    }
                }
            }
        }
    }

    private boolean isMarkable(MessagePacket packet) {
        return packet.hasChild("markable", "urn:xmpp:chat-markers:0");
    }

    private String getReceiptId(MessagePacket packet) {
        Element received = packet.findChild("received", "urn:xmpp:receipts");
        if (received != null) {
            return received.getAttribute("id");
        }
        return null;
    }

    private boolean isCarbonsEnabled(Account account) {
        // Check if carbons are enabled for the account.
        return account.getXmppConnection().getFeatures().carbonsEnabled();
    }

    private String getCarbonId(MessagePacket packet) {
        Element sent = packet.findChild("sent", "urn:xmpp:carbons:2");
        if (sent != null) {
            return sent.getAttribute("id");
        }
        return null;
    }

    private boolean isReceiptRequest(MessagePacket packet) {
        return packet.hasChild("request", "urn:xmpp:receipts");
    }

    private String getErrorMessage(MessagePacket packet) {
        Element error = packet.findChild("error");
        if (error != null) {
            return error.toString();
        }
        return null;
    }

    private String getErrorType(MessagePacket packet) {
        Element error = packet.findChild("error");
        if (error != null) {
            return error.getAttribute("type");
        }
        return null;
    }

    private void handleReceiptRequest(Account account, Conversation conversation, Message message) {
        // Handle receipt request.
        mXmppConnectionService.getMessageGenerator().received(account, conversation, message);
    }

    private void handleCarbonMessage(MessagePacket packet, Account account) {
        // Potential area: Validate and sanitize carbon messages.
        String id = getCarbonId(packet);
        if (id != null) {
            Message message = new Message(conversation, packet.getBody(), Message.ENCRYPTION_NONE);
            conversation.add(message);
            mXmppConnectionService.markRead(conversation, false);
        }
    }

    private String getPgpElement(MessagePacket packet) {
        Element pgpElement = packet.findChild("x", "jabber:x:encrypted");
        if (pgpElement != null) {
            return pgpElement.getContent();
        }
        return null;
    }

    private void handlePgpMessage(Account account, Conversation conversation, String pgpContent) {
        // Potential area: Validate and decrypt PGP content securely.
        Message message = new Message(conversation, pgpContent, Message.ENCRYPTION_PGP);
        conversation.add(message);
    }
}