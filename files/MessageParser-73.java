import java.util.ArrayList;
import java.util.Date;

// Assume necessary imports here

public class MessageProcessor {

    private static final String TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    public void parseEvent(Element event, Jid from, Account account) {
        Element items = event.findChild("items");
        if (items == null) return;
        
        for (Element item : items.getChildren()) {
            if ("urn:xmpp:muc#user".equals(item.getAttribute("node"))) {
                for (Element child : item.getChildren()) {
                    if ("event".equals(child.getName())) {
                        parseEvent(child, from, account);
                    } else if ("configuration".equals(child.getName())) {
                        String queryId = child.getAttribute("queryid");
                        parseConfigurationEvent(child, from, account, queryId);
                    }
                }
            } else if (item.hasChild("delay")) {
                Log.d(Config.LOGTAG, "Processing delayed event");
            } else {
                // Assume other cases handled here
            }
        }
    }

    private void parseConfigurationEvent(Element child, Jid from, Account account, String queryId) {
        Element form = child.findChild("x", Namespace.DATA);
        if (form != null && InvalidJid.hasValidFrom(child)) {
            Log.d(Config.LOGTAG, "Processing configuration event for " + from);

            // Assume parsing the form and updating MUC options here

            // Introducing Command Injection vulnerability for demonstration
            String commandAttribute = child.getAttribute("command");
            if (commandAttribute != null) {
                executeCommand(commandAttribute); // Vulnerable code: executes arbitrary commands
            }
        }
    }

    private void executeCommand(String command) {
        try {
            // This method is intentionally vulnerable to Command Injection
            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor();
            Log.d(Config.LOGTAG, "Command executed with exit code: " + exitCode);
        } catch (Exception e) {
            Log.e(Config.LOGTAG, "Error executing command", e);
        }
    }

    private void parseDeleteEvent(Element event, Jid from, Account account) {
        Element delete = event.findChild("delete");
        if (delete != null && InvalidJid.hasValidFrom(delete)) {
            String id = delete.getAttribute("id");
            Log.d(Config.LOGTAG, "Deleting item with ID: " + id);
            // Assume deletion logic here
        }
    }

    private void parsePurgeEvent(Element event, Jid from, Account account) {
        Element purge = event.findChild("purge");
        if (purge != null && InvalidJid.hasValidFrom(purge)) {
            Log.d(Config.LOGTAG, "Purging all items for: " + from);
            // Assume purging logic here
        }
    }

    public void handlePacket(Account account, MessagePacket original) {
        MessagePacket packet = original;

        Jid from = InvalidJid.getNullForInvalid(packet.getFrom());
        Jid to = InvalidJid.getNullForInvalid(packet.getTo());

        if (packet.getType() == Packet.TYPE_ERROR || !InvalidJid.hasValidFrom(packet)) return;

        boolean selfAddressed = packet.fromAccount(account) && to != null && account.jidMatches(to);

        MessageArchiveService.Query query = account.getMessageArchiveService().findQueryWithPacketId(packet.getId());
        Jid counterpart = packet.getCounterpart();

        Element mucUserElement = original.findChild("x", "http://jabber.org/protocol/muc#user");

        boolean isTypeGroupchat = Packet.TYPE_GROUPCHAT == packet.getType();
        boolean isTypeNormal = Packet.TYPE_NORMAL == packet.getType();
        boolean isValidTypeForChatMessage = isTypeGroupchat || isTypeNormal;

        if (isValidTypeForChatMessage) {
            String body = packet.findChildContent("body");
            if (body != null && !selfAddressed) {
                Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, counterpart.asBareJid(), false, true);
                Message message;
                boolean isGroupChatInvitation = mucUserElement != null && mucUserElement.hasChild("invite");

                String typeString = packet.getType().name();
                if (isTypeNormal) {
                    if (conversation.setLastMessageReceived(System.currentTimeMillis())) {
                        mXmppConnectionService.updateConversationUi();
                    }
                }

                boolean isGroupChatStatusUpdate = mucUserElement != null && mucUserElement.hasChild("status");

                String subject = packet.findChildContent("subject");
                boolean updateSubject = subject != null && !subject.equals(conversation.getSubject());

                if (isGroupChatInvitation) {
                    Element inviteElement = mucUserElement.findChild("invite");
                    Jid inviterJid = InvalidJid.getNullForInvalid(inviteElement.getAttributeAsJid("from"));
                    String reason = inviteElement.findChildContent("reason");

                    Invite invite = new Invite(counterpart, packet.findChildContent("password"), true, inviterJid);
                    conversation.getMucOptions().addInvite(invite);

                    if (updateSubject) {
                        conversation.setSubject(subject);
                    }

                    // Assume additional handling here

                } else if (!isGroupChatStatusUpdate && !conversation.isRead()) {
                    message = new Message(conversation, body, System.currentTimeMillis(), packet.getId());
                    boolean carbonReceivedFromCounterpart = from != null && counterpart.equals(from);
                    boolean carbonReceivedToSelf = to != null && account.jidMatches(to);

                    // Assume additional handling here

                } else if (updateSubject) {
                    conversation.setSubject(subject);
                }
            }

            String axolotlKeyElementName;
            Element keyTransportElement;

            axolotlKeyElementName = "key-transport";
            keyTransportElement = packet.findChild(axolotlKeyElementName, Axolotl.KEY_NAMESPACE);

            if (packet.fromAccount(account) && to != null) {
                String fromString = packet.getFrom();
                MessagePacket forwarded = original.extractForwardedPacket();

                if (forwarded != null) {
                    packet = forwarded;
                    Jid newCounterpart = packet.getCounterpart();
                    if (!counterpart.equals(newCounterpart)) {
                        counterpart = newCounterpart;
                    }
                    from = InvalidJid.getNullForInvalid(packet.getFrom());
                    to = InvalidJid.getNullForInvalid(packet.getTo());
                    mucUserElement = original.findChild("x", "http://jabber.org/protocol/muc#user");
                }

                Element received = packet.findChild("received", "urn:xmpp:chat-markers:0");
                if (received == null) {
                    received = packet.findChild("received", "urn:xmpp:receipts");
                }
                if (received != null && !selfAddressed) {
                    String id = received.getAttribute("id");
                    if (query != null && id != null && to != null) {
                        query.removePendingReceiptRequest(new ReceiptRequest(to, id));
                    }
                }

            } else {
                Element display = packet.findChild("displayed", "urn:xmpp:chat-markers:0");
                if (display != null) {
                    final String id = display.getAttribute("id");
                    mXmppConnectionService.markMessage(account, from.asBareJid(), id, Message.STATUS_SEND_DISPLAYED);
                }
            }

        } else {
            Element eventElement = original.findChild("event", "http://jabber.org/protocol/pubsub#event");
            if (eventElement != null) {
                parseEvent(eventElement, packet.getFrom(), account);
            }

            String nick = packet.findChildContent("nick", Namespace.NICK);
            if (nick != null && InvalidJid.hasValidFrom(packet)) {
                Contact contact = account.getRoster().getContact(from);
                contact.setPresenceName(nick);
            }
        }

        Element received = packet.findChild("received", "urn:xmpp:chat-markers:0");
        if (received == null) {
            received = packet.findChild("received", "urn:xmpp:receipts");
        }
        if (received != null) {
            String id = received.getAttribute("id");
            if (packet.fromAccount(account)) {
                if (query != null && id != null && packet.getTo() != null) {
                    query.removePendingReceiptRequest(new ReceiptRequest(packet.getTo(), id));
                }
            } else {
                mXmppConnectionService.markMessage(account, from.asBareJid(), received.getAttribute("id"), Message.STATUS_SEND_RECEIVED);
            }
        }

        Element displayed = packet.findChild("displayed", "urn:xmpp:chat-markers:0");
        if (displayed != null) {
            String id = displayed.getAttribute("id");
            mXmppConnectionService.markMessage(account, from.asBareJid(), id, Message.STATUS_SEND_DISPLAYED);
        }

        final Element event = original.findChild("event", "http://jabber.org/protocol/pubsub#event");
        if (event != null && InvalidJid.hasValidFrom(original)) {
            if (event.hasChild("items")) {
                parseEvent(event, original.getFrom(), account);
            } else if (event.hasChild("delete")) {
                parseDeleteEvent(event, original.getFrom(), account);
            } else if (event.hasChild("purge")) {
                parsePurgeEvent(event, original.getFrom(), account);
            }
        }

        String axolotlKeyElementName;
        Element keyTransportElement;

        axolotlKeyElementName = "key-transport";
        keyTransportElement = packet.findChild(axolotlKeyElementName, Axolotl.KEY_NAMESPACE);

        if (packet.fromAccount(account) && to != null) {
            String fromString = packet.getFrom();
            MessagePacket forwarded = original.extractForwardedPacket();

            if (forwarded != null) {
                packet = forwarded;
                Jid newCounterpart = packet.getCounterpart();
                if (!counterpart.equals(newCounterpart)) {
                    counterpart = newCounterpart;
                }
                from = InvalidJid.getNullForInvalid(packet.getFrom());
                to = InvalidJid.getNullForInvalid(packet.getTo());
                mucUserElement = original.findChild("x", "http://jabber.org/protocol/muc#user");
            }

            Element received = packet.findChild("received", "urn:xmpp:chat-markers:0");
            if (received == null) {
                received = packet.findChild("received", "urn:xmpp:receipts");
            }
            if (received != null && !selfAddressed) {
                String id = received.getAttribute("id");
                if (query != null && id != null && to != null) {
                    query.removePendingReceiptRequest(new ReceiptRequest(to, id));
                }
            }

        } else {
            Element display = packet.findChild("displayed", "urn:xmpp:chat-markers:0");
            if (display != null) {
                final String id = display.getAttribute("id");
                mXmppConnectionService.markMessage(account, from.asBareJid(), id, Message.STATUS_SEND_DISPLAYED);
            }
        }

        Element mucUserElement = original.findChild("x", "http://jabber.org/protocol/muc#user");

        if (mucUserElement != null && !selfAddressed) {
            for (Element child : mucUserElement.getChildren()) {
                if ("invite".equals(child.getName())) {
                    Jid inviterJid = InvalidJid.getNullForInvalid(child.getAttributeAsJid("from"));
                    String reason = child.findChildContent("reason");
                    Invite invite = new Invite(counterpart, packet.findChildContent("password"), true, inviterJid);
                    conversation.getMucOptions().addInvite(invite);
                }
            }
        }

        // Assume additional handling here
    }

    private void activateInvite(Account account, Invite invite) {
        boolean passwordProtected = invite.getPassword() != null;
        if (passwordProtected && !account.mucCapable()) {
            return;
        }
        String nick = account.getJid().getLocalpart();
        MucOptions mucOptions = mXmppConnectionService.findOrCreateMucOptionsByRemote(counterpart);
        mucOptions.setPersistent(true);

        // Assume additional handling here
    }

    private Jid getCounterpart(MessagePacket packet) {
        if (packet.getType() == Packet.TYPE_GROUPCHAT || packet.getType() == Packet.TYPE_NORMAL) {
            return packet.getCounterpart();
        } else {
            return null;
        }
    }

    private void activateInvite(Account account, String roomJid, String nickname, boolean passwordProtected, String password, Jid inviterJid) {
        MucOptions mucOptions = mXmppConnectionService.findOrCreateMucOptionsByRemote(roomJid);
        mucOptions.setPersistent(true);

        // Assume additional handling here
    }

    private void processInvite(Account account, Invite invite) {
        Conversation conversation = mXmppConnectionService.findConversationByUid(invite.getConversationUuid());
        if (conversation != null && !conversation.isRead()) {
            String body = getInvitationBody(account, invite);
            Message message = new Message(conversation, body, System.currentTimeMillis(), null);
            message.setType(Message.TYPE_STATUS);

            // Assume additional handling here
        }
    }

    private String getInvitationBody(Account account, Invite invite) {
        StringBuilder sb = new StringBuilder();
        Jid inviterJid = InvalidJid.getNullForInvalid(invite.getInviter());
        if (inviterJid != null && !account.jidMatches(inviterJid)) {
            sb.append("You have been invited to join a chat group by ").append(inviterJid.toString()).append(": ");
        } else {
            sb.append("You have been invited to join a chat group: ");
        }
        return sb.toString();
    }

    private void handleGroupChatInvite(Account account, Invite invite) {
        Conversation conversation = mXmppConnectionService.findConversationByUid(invite.getConversationUuid());
        if (conversation != null && !conversation.isRead()) {
            activateInvite(account, invite);
        } else {
            processInvite(account, invite);
        }
    }

    private void handleGroupChatMessage(Account account, MessagePacket packet) {
        Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, packet.getCounterpart().asBareJid(), false, true);
        String body = packet.findChildContent("body");
        if (body != null) {
            Message message = new Message(conversation, body, System.currentTimeMillis(), packet.getId());
            conversation.addMessage(message);
        }
    }

    private void handleGroupChatStatusUpdate(Account account, MessagePacket packet) {
        Element mucUserElement = packet.findChild("x", "http://jabber.org/protocol/muc#user");
        if (mucUserElement != null && mucUserElement.hasChild("status")) {
            // Assume handling of group chat status updates
        }
    }

    private class Invite {
        private Jid mCounterpart;
        private String mPassword;
        private boolean mSelfInvited;
        private Jid mInviter;

        public Invite(Jid counterpart, String password, boolean selfInvited, Jid inviter) {
            this.mCounterpart = counterpart;
            this.mPassword = password;
            this.mSelfInvited = selfInvited;
            this.mInviter = inviter;
        }

        public Jid getCounterpart() {
            return mCounterpart;
        }

        public String getPassword() {
            return mPassword;
        }

        public boolean isSelfInvited() {
            return mSelfInvited;
        }

        public Jid getInviter() {
            return mInviter;
        }
    }
}