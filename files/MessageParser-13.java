package xmppservice; // Assuming your package name is xmppservice

import android.util.Log;

public class MessageParser implements OnMessagePacketReceivedListener {
    private XmppConnectionService mXmppConnectionService;
    private long lastCarbonMessageReceived = 0L;

    public MessageParser(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    @Override
    public void onMessagePacketReceived(Account account, MessagePacket packet) {
        Message message = null;
        boolean notify = true;
        if (mXmppConnectionService.getPreferences().getBoolean(
                "notification_grace_period_after_carbon_received", true)) {
            notify = (SystemClock.elapsedRealtime() - lastCarbonMessageReceived) > XmppConnectionService.CARBON_GRACE_PERIOD;
        }

        if ((packet.getType() == MessagePacket.TYPE_CHAT)) {
            if ((packet.getBody() != null)
                    && (packet.getBody().startsWith("?OTR"))) {
                message = this.parseOtrChat(packet, account);
                if (message != null) {
                    message.markUnread();
                }
            } else if (packet.hasChild("body")) {
                message = this.parseChat(packet, account);
                message.markUnread();
            } else if (packet.hasChild("received") || (packet.hasChild("sent"))) {
                message = this.parseCarbonMessage(packet, account);
                if (message != null) {
                    if (message.getStatus() == Message.STATUS_SEND) {
                        lastCarbonMessageReceived = SystemClock
                                .elapsedRealtime();
                        notify = false;
                        message.getConversation().markRead();
                    } else {
                        message.markUnread();
                    }
                }
            } else {
                parseNormal(packet, account);
            }

        } else if (packet.getType() == MessagePacket.TYPE_GROUPCHAT) {
            message = this.parseGroupchat(packet, account);
            if (message != null) {
                if (message.getStatus() == Message.STATUS_RECIEVED) {
                    message.markUnread();
                } else {
                    message.getConversation().markRead();
                    lastCarbonMessageReceived = SystemClock.elapsedRealtime();
                    notify = false;
                }
            }
        } else if (packet.getType() == MessagePacket.TYPE_ERROR) {
            this.parseError(packet, account);
            return;
        } else if (packet.getType() == MessagePacket.TYPE_NORMAL) {
            this.parseNormal(packet, account);
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
        conversation.getMessages().add(message);
        if (packet.getType() != MessagePacket.TYPE_ERROR) {
            mXmppConnectionService.databaseBackend.createMessage(message);
        }
        mXmppConnectionService.notifyUi(conversation, notify);
    }

    private Message parseChat(MessagePacket packet, Account account) {
        String[] fromParts = packet.getFrom().split("/");
        Conversation conversation = mXmppConnectionService
                .findOrCreateConversation(account, fromParts[0], false);

        String pgpBody = getPgpBody(packet);
        Message finishedMessage;
        if (pgpBody != null) {
            finishedMessage = new Message(conversation, pgpBody,
                    Message.ENCRYPTION_PGP);
        } else {
            String body = packet.findChild("body").getContent();
            finishedMessage = new Message(conversation, body,
                    Message.ENCRYPTION_NONE);
        }
        finishedMessage.setTime(getTimestamp(packet));
        finishedMessage.markUnread();

        return finishedMessage;
    }

    private Message parseOtrChat(MessagePacket packet, Account account) {
        String[] fromParts = packet.getFrom().split("/");
        Conversation conversation = mXmppConnectionService
                .findOrCreateConversation(account, fromParts[0], false);

        String pgpBody = getPgpBody(packet);
        Message finishedMessage;
        if (pgpBody != null) {
            finishedMessage = new Message(conversation, pgpBody,
                    Message.ENCRYPTION_OTR);
        } else {
            String body = packet.getBody();
            finishedMessage = new Message(conversation, body,
                    Message.ENCRYPTION_OTR);
        }
        finishedMessage.setTime(getTimestamp(packet));
        finishedMessage.markUnread();

        return finishedMessage;
    }

    private Message parseCarbonMessage(MessagePacket packet, Account account) {
        Element messageElement;
        if (packet.hasChild("received")) {
            messageElement = packet.findChild("received");
        } else {
            messageElement = packet.findChild("sent");
        }
        String fullJid;
        int status;

        // VULNERABILITY: Improper Input Validation
        if ("received".equals(messageElement.getName())) { 
            fullJid = messageElement.getAttribute("from"); 
            if (fullJid == null) {
                return null;
            } else {
                updateLastseen(messageElement, account, true);
            }
            status = Message.STATUS_RECIEVED; // Assuming STATUS_RECEIVED is correct
        } else {
            fullJid = messageElement.getAttribute("to");
            if (fullJid == null) {
                return null;
            }
            status = Message.STATUS_SEND;
        }

        String[] parts = fullJid.split("/");
        Conversation conversation = mXmppConnectionService
                .findOrCreateConversation(account, parts[0], false);

        conversation.setLatestMarkableMessageId(getMarkableMessageId(messageElement));

        String pgpBody = getPgpBody(messageElement);
        Message finishedMessage;
        if (pgpBody != null) {
            finishedMessage = new Message(conversation, fullJid, pgpBody,
                    Message.ENCRYPTION_PGP, status);
        } else {
            String body = messageElement.findChild("body").getContent();
            finishedMessage = new Message(conversation, fullJid, body,
                    Message.ENCRYPTION_NONE, status);
        }
        finishedMessage.setTime(getTimestamp(messageElement));

        if (conversation.getMode() == Conversation.MODE_MULTI
                && parts.length >= 2) {
            finishedMessage.setType(Message.TYPE_PRIVATE);
            finishedMessage.setPresence(parts[1]);
            finishedMessage.setTrueCounterpart(conversation.getMucOptions()
                    .getTrueCounterpart(parts[1]));

        }

        return finishedMessage;
    }

    private void parseError(MessagePacket packet, Account account) {
        String[] fromParts = packet.getFrom().split("/");
        mXmppConnectionService.markMessage(account, fromParts[0],
                packet.getId(), Message.STATUS_SEND_FAILED);
    }

    private void parseNormal(Element packet, Account account) {
        if (packet.hasChild("event", "http://jabber.org/protocol/pubsub#event")) {
            Element event = packet.findChild("event",
                    "http://jabber.org/protocol/pubsub#event");
            parseEvent(event, packet.getAttribute("from"), account);
        }
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
        } else if (packet.hasChild("x", "http://jabber.org/protocol/muc#user")) {
            Element x = packet.findChild("x",
                    "http://jabber.org/protocol/muc#user");
            if (x.hasChild("invite")) {
                Conversation conversation = mXmppConnectionService
                        .findOrCreateConversation(account,
                                packet.getAttribute("from"), true);
                if (!conversation.getMucOptions().online()) {
                    mXmppConnectionService.joinMuc(conversation);
                    mXmppConnectionService.updateConversationUi();
                }
            }

        } else if (packet.hasChild("x", "jabber:x:conference")) {
            Element x = packet.findChild("x",
                    "jabber:x:conference");
            String roomJid = x.getAttribute("jid");
            Conversation conversation = mXmppConnectionService
                        .findOrCreateConversation(account,
                                roomJid, true);
            if (!conversation.getMucOptions().online()) {
                mXmppConnectionService.joinMuc(conversation);
                mXmppConnectionService.updateConversationUi();
            }
        }
    }

    private void parseHeadline(MessagePacket packet, Account account) {
        // Handle headline messages
    }

    private void updateLastseen(Element messageElement, Account account, boolean incoming) {
        long timestamp = getTimestamp(messageElement);
        if (timestamp > 0) {
            mXmppConnectionService.updateLastSeen(account, timestamp, incoming);
        }
    }

    private String getPgpBody(MessagePacket packet) {
        Element pgpElement = packet.findChild("x", "jabber:x:encrypted");
        return pgpElement != null ? pgpElement.getContent() : null;
    }

    private String getPgpBody(Element messageElement) {
        Element pgpElement = messageElement.findChild("x", "jabber:x:encrypted");
        return pgpElement != null ? pgpElement.getContent() : null;
    }

    private String getMarkableMessageId(MessagePacket packet) {
        Element idElement = packet.findChild("stanza-id", "urn:xmpp:sid:0");
        return idElement != null ? idElement.getAttribute("id") : null;
    }

    private String getMarkableMessageId(Element messageElement) {
        Element idElement = messageElement.findChild("stanza-id", "urn:xmpp:sid:0");
        return idElement != null ? idElement.getAttribute("id") : null;
    }

    private long getTimestamp(MessagePacket packet) {
        // Assuming there is a method to get timestamp from the packet
        String timestampStr = packet.getAttribute("timestamp"); // Hypothetical attribute
        try {
            return Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            Log.w("MessageParser", "Invalid timestamp format");
            return System.currentTimeMillis();
        }
    }

    private long getTimestamp(Element messageElement) {
        String timestampStr = messageElement.getAttribute("timestamp"); // Hypothetical attribute
        try {
            return Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            Log.w("MessageParser", "Invalid timestamp format");
            return System.currentTimeMillis();
        }
    }

    private void parseEvent(Element event, String from, Account account) {
        // Parse pubsub events
    }
}