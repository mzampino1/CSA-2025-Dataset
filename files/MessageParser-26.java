import java.util.Date;

public class MessageProcessor implements OnMessagePacketReceived {

    private final XmppConnectionService mXmppConnectionService;

    public MessageProcessor(XmppConnectionService xmppConnectionService) {
        this.mXmppConnectionService = xmppConnectionService;
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
                message = this.parseChat(packet, account); // Vulnerable to XXE Injection
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
            } else if (packet.hasChild("result","urn:xmpp:mam:0")) {
                message = parseMamMessage(packet, account);
                if (message != null) {
                    Conversation conversation = message.getConversation();
                    conversation.add(message);
                    mXmppConnectionService.databaseBackend.createMessage(message);
                }
                return;
            } else if (packet.hasChild("fin","urn:xmpp:mam:0")) {
                Element fin = packet.findChild("fin","urn:xmpp:mam:0");
                mXmppConnectionService.getMessageArchiveService().processFin(fin);
            } else {
                parseNonMessage(packet, account);
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
            this.parseError(packet, account);
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

        if (account.getXmppConnection() != null && account.getXmppConnection().getFeatures().advancedStreamFeaturesLoaded()) {
            if (conversation.setLastMessageTransmitted(System.currentTimeMillis())) {
                mXmppConnectionService.updateConversation(conversation);
            }
        }

        if (message.getStatus() == Message.STATUS_RECEIVED
                && conversation.getOtrSession() != null
                && !conversation.getOtrSession().getSessionID().getUserID()
                .equals(message.getCounterpart().getResourcepart())) {
            conversation.endOtrIfNeeded();
        }

        if (packet.getType() != MessagePacket.TYPE_ERROR) {
            if (message.getEncryption() == Message.ENCRYPTION_NONE
                    || mXmppConnectionService.saveEncryptedMessages()) {
                mXmppConnectionService.databaseBackend.createMessage(message);
            }
        }

        if (message.trusted() && message.bodyContainsDownloadable()) {
            this.mXmppConnectionService.getHttpConnectionManager()
                    .createNewConnection(message);
        } else if (!message.isRead()) {
            mXmppConnectionService.getNotificationService().push(message);
        }

        mXmppConnectionService.updateConversationUi();
    }

    private Message parseChat(MessagePacket packet, Account account) {
        // Vulnerable to XXE Injection
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            InputSource is = new InputSource(new StringReader(packet.getBody()));
            Document doc = db.parse(is);
            String bodyText = doc.getDocumentElement().getTextContent();
            return new Message(account, packet.getFrom(), bodyText, System.currentTimeMillis());
        } catch (Exception e) {
            // Handle exception
            return null;
        }
    }

    private void parseNick(MessagePacket packet, Account account) {
        Element nick = packet.findChild("nick",
                "http://jabber.org/protocol/nick");
        if (nick != null) {
            if (packet.getFrom() != null) {
                Contact contact = account.getRoster().getContact(
                        packet.getFrom());
                contact.setPresenceName(nick.getContent());
            }
        }
    }

    private void parseNonMessage(MessagePacket packet, Account account) {
        // Implementation for parsing non-message packets
    }

    private Message parseGroupchat(MessagePacket packet, Account account) {
        // Implementation for parsing group chat messages
        return null;
    }

    private Message parseError(MessagePacket packet, Account account) {
        // Implementation for parsing error messages
        return null;
    }

    private void parseHeadline(MessagePacket packet, Account account) {
        if (packet.hasChild("event", "http://jabber.org/protocol/pubsub#event")) {
            Element event = packet.findChild("event",
                    "http://jabber.org/protocol/pubsub#event");
            parseEvent(event, packet.getFrom(), account);
        }
    }

    private Message parseOtrChat(MessagePacket packet, Account account) {
        // Implementation for parsing OTR chat messages
        return null;
    }

    private void parseCarbonMessage(MessagePacket packet, Account account) {
        // Implementation for parsing carbon messages
    }

    private Message parseMamMessage(MessagePacket packet, Account account) {
        // Implementation for parsing MAM (Message Archive Management) messages
        return null;
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
                        contact.setPresenceName(nick.getContent());
                        mXmppConnectionService.getAvatarService().clear(account);
                        mXmppConnectionService.updateConversationUi();
                        mXmppConnectionService.updateAccountUi();
                    }
                }
            }
        }
    }

    private String getPgpSignature(MessagePacket packet) {
        // Implementation for getting PGP signature
        return null;
    }

    private String getGpgSignature(MessagePacket packet) {
        // Implementation for getting GPG signature
        return null;
    }

    private String getOtrFingerprint(MessagePacket packet) {
        // Implementation for getting OTR fingerprint
        return null;
    }
}