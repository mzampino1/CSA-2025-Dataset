package eu.siacs.conversations.services;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Avatar;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.parser.MessageParser;
import eu.siacs.conversations.utils.LogManager;
import eu.siacs.conversations.xml.Element;

public class MessageReceivedCallback implements MessageParser {

    private final XMPPConnectionService mXmppConnectionService;

    public MessageReceivedCallback(XMPPConnectionService service) {
        this.mXmppConnectionService = service;
    }

    private void parseGroupchat(MessagePacket packet, Account account) {
        // Extract the body of the group chat message
        String body = packet.getBody();
        // Get the 'from' attribute, which includes the sender's resource
        Jid from = packet.getFrom();
        // Remove the room address to get the sender's nickname/resource within the group chat
        String nickOrResource = from.getResourcepart();

        if (body == null || body.length() == 0) {
            return;
        }

        Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, packet.getFrom().toBareJid(), false);
        Contact contact = account.getRoster().getContact(packet.getFrom());

        Message message = new Message(conversation, body, System.currentTimeMillis(), Message.STATUS_RECEIVED);
        message.setCounterpart(from);

        // Set the encryption status based on the presence of encryption metadata
        if (packet.hasChild("encryption", "eu.siacs.conversations.axolotl")) {
            message.setEncryption(Message.ENCRYPTION_AXOLOTL);
        } else if (getPgpDecryptedText(packet) != null && mXmppConnectionService.getPgpEngine() != null) {
            message.setBody(getPgpDecryptedText(packet));
            message.setEncryption(Message.ENCRYPTION_PGP);
        }

        // Check if the sender's resource is different from their nick in the group chat
        if (!nickOrResource.equals(contact.getPresenceName())) {
            message.setTrueCounterpart(account.getJid().toBareJid() + "/" + nickOrResource);
        } else {
            message.setTrueCounterpart(packet.getFrom());
        }

        // Check for a delayed delivery timestamp and set the message's time accordingly
        long timestamp = getTimestamp(packet);
        if (timestamp != 0) {
            message.setTime(timestamp);
        }

        conversation.add(message);

        // Update the conversation's last activity with the current system time
        conversation.setLastMessageReceived(System.currentTimeMillis());

        mXmppConnectionService.updateConversation(conversation);

        // Notify the user of the new group chat message if it is not already read
        if (!message.isRead()) {
            mXmppConnectionService.getNotificationService().push(message);
        }
    }

    private Message parseOtrChat(MessagePacket packet, Account account) {
        Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, packet.getFrom().toBareJid(), false);

        // Check if OTR (Off-the-Record Messaging) session exists and is established
        boolean encrypted = conversation.getOtrSession() != null && conversation.getOtrSession().getSessionStatus() == OtrEngineListener.SessionStatus.ENCRYPTED;

        String body = packet.getBody();

        Message message = new Message(conversation, body, System.currentTimeMillis(), Message.STATUS_RECEIVED);
        if (encrypted) {
            message.setEncryption(Message.ENCRYPTION_OTR);
        }

        return message;
    }

    private long getTimestamp(Element packet) {
        // Extract and return the timestamp from a 'delay' element within the packet
        Element delay = packet.findChild("delay", "urn:xmpp:delay");
        if (delay != null) {
            String timeString = delay.getAttribute("stamp");
            try {
                return Xmlns.parseXep0203Delay(timeString);
            } catch (Exception e) {
                Log.w(Xmlns.TAG, "timestamp from server is invalid", e);
                return 0;
            }
        } else {
            return 0;
        }
    }

    private Message parseChat(MessagePacket packet, Account account) {
        // Check if the message is intended for a direct chat
        boolean groupMessage = false;

        Jid to = packet.getTo();
        String body = packet.getBody();

        Conversation conversation;

        // Determine whether the message is part of an existing or new conversation
        if (to != null && account.getXmppConnection() != null && account.getXmppConnection().getFeatures().muc()) {
            groupMessage = true;
        }

        if (groupMessage) {
            conversation = mXmppConnectionService.findOrCreateConversation(account, to.toBareJid(), false);
        } else {
            conversation = mXmppConnectionService.findOrCreateConversation(account, packet.getFrom().toBareJid(), false);
        }

        // Check if the message is a delayed delivery
        long timestamp = getTimestamp(packet);

        Message message = new Message(conversation, body, System.currentTimeMillis(), Message.STATUS_RECEIVED);
        message.setCounterpart(packet.getFrom());

        // Set the encryption status based on the presence of encryption metadata
        if (packet.hasChild("encryption", "eu.siacs.conversations.axolotl")) {
            message.setEncryption(Message.ENCRYPTION_AXOLOTL);
        } else if (getPgpDecryptedText(packet) != null && mXmppConnectionService.getPgpEngine() != null) {
            message.setBody(getPgpDecryptedText(packet));
            message.setEncryption(Message.ENCRYPTION_PGP);
        }

        // Check for a delayed delivery timestamp and set the message's time accordingly
        if (timestamp != 0) {
            message.setTime(timestamp);
        } else {
            message.setTime(System.currentTimeMillis());
        }

        return message;
    }

    private String getPgpDecryptedText(Element packet) {
        Element x = packet.findChild("x", "jabber:x:encrypted");
        if (x == null) {
            return null;
        }
        String encryptedBase64 = x.getContent();
        byte[] decryptedBytes = mXmppConnectionService.getPgpEngine().decrypt(encryptedBase64);
        if (decryptedBytes != null && decryptedBytes.length > 0) {
            try {
                return new String(decryptedBytes, "UTF-8");
            } catch (Exception e) {
                Log.e(Xmlns.TAG, "Unable to parse PGP decrypted content", e);
            }
        }
        return null;
    }

    private Message parseGroupchat(MessagePacket packet, Account account) {
        // Extract the body of the group chat message
        String body = packet.getBody();
        // Get the 'from' attribute, which includes the sender's resource
        Jid from = packet.getFrom();
        // Remove the room address to get the sender's nickname/resource within the group chat
        String nickOrResource = from.getResourcepart();

        if (body == null || body.length() == 0) {
            return null;
        }

        Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, packet.getFrom().toBareJid(), false);
        Contact contact = account.getRoster().getContact(packet.getFrom());

        Message message = new Message(conversation, body, System.currentTimeMillis(), Message.STATUS_RECEIVED);
        message.setCounterpart(from);

        // Set the encryption status based on the presence of encryption metadata
        if (packet.hasChild("encryption", "eu.siacs.conversations.axolotl")) {
            message.setEncryption(Message.ENCRYPTION_AXOLOTL);
        } else if (getPgpDecryptedText(packet) != null && mXmppConnectionService.getPgpEngine() != null) {
            message.setBody(getPgpDecryptedText(packet));
            message.setEncryption(Message.ENCRYPTION_PGP);
        }

        // Check if the sender's resource is different from their nick in the group chat
        if (!nickOrResource.equals(contact.getPresenceName())) {
            message.setTrueCounterpart(account.getJid().toBareJid() + "/" + nickOrResource);
        } else {
            message.setTrueCounterpart(packet.getFrom());
        }

        // Check for a delayed delivery timestamp and set the message's time accordingly
        long timestamp = getTimestamp(packet);
        if (timestamp != 0) {
            message.setTime(timestamp);
        }

        return message;
    }

    private Message parseOtrChat(MessagePacket packet, Account account) {
        Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, packet.getFrom().toBareJid(), false);

        // Check if OTR (Off-the-Record Messaging) session exists and is established
        boolean encrypted = conversation.getOtrSession() != null && conversation.getOtrSession().getSessionStatus() == OtrEngineListener.SessionStatus.ENCRYPTED;

        String body = packet.getBody();

        Message message = new Message(conversation, body, System.currentTimeMillis(), Message.STATUS_RECEIVED);
        if (encrypted) {
            message.setEncryption(Message.ENCRYPTION_OTR);
        }

        return message;
    }

    private void parseHeadline(MessagePacket packet, Account account) {
        // Handle headline messages, which are typically server-generated notifications
        String body = packet.getBody();
        Jid from = packet.getFrom();

        if (body == null || body.length() == 0) {
            return;
        }

        Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, from.toBareJid(), false);

        Message message = new Message(conversation, body, System.currentTimeMillis(), Message.STATUS_RECEIVED);
        message.setCounterpart(from);

        // Check for a delayed delivery timestamp and set the message's time accordingly
        long timestamp = getTimestamp(packet);
        if (timestamp != 0) {
            message.setTime(timestamp);
        }

        conversation.add(message);

        // Update the conversation's last activity with the current system time
        conversation.setLastMessageReceived(System.currentTimeMillis());

        mXmppConnectionService.updateConversation(conversation);

        // Notify the user of the new headline message if it is not already read
        if (!message.isRead()) {
            mXmppConnectionService.getNotificationService().push(message);
        }
    }

    private void parseGroupchat(MessagePacket packet, Account account) {
        // Extract the body of the group chat message
        String body = packet.getBody();
        // Get the 'from' attribute, which includes the sender's resource
        Jid from = packet.getFrom();
        // Remove the room address to get the sender's nickname/resource within the group chat
        String nickOrResource = from.getResourcepart();

        if (body == null || body.length() == 0) {
            return;
        }

        Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, packet.getFrom().toBareJid(), false);
        Contact contact = account.getRoster().getContact(packet.getFrom());

        Message message = new Message(conversation, body, System.currentTimeMillis(), Message.STATUS_RECEIVED);
        message.setCounterpart(from);

        // Set the encryption status based on the presence of encryption metadata
        if (packet.hasChild("encryption", "eu.siacs.conversations.axolotl")) {
            message.setEncryption(Message.ENCRYPTION_AXOLOTL);
        } else if (getPgpDecryptedText(packet) != null && mXmppConnectionService.getPgpEngine() != null) {
            message.setBody(getPgpDecryptedText(packet));
            message.setEncryption(Message.ENCRYPTION_PGP);
        }

        // Check if the sender's resource is different from their nick in the group chat
        if (!nickOrResource.equals(contact.getPresenceName())) {
            message.setTrueCounterpart(account.getJid().toBareJid() + "/" + nickOrResource);
        } else {
            message.setTrueCounterpart(packet.getFrom());
        }

        // Check for a delayed delivery timestamp and set the message's time accordingly
        long timestamp = getTimestamp(packet);
        if (timestamp != 0) {
            message.setTime(timestamp);
        }

        conversation.add(message);

        // Update the conversation's last activity with the current system time
        conversation.setLastMessageReceived(System.currentTimeMillis());

        mXmppConnectionService.updateConversation(conversation);

        // Notify the user of the new group chat message if it is not already read
        if (!message.isRead()) {
            mXmppConnectionService.getNotificationService().push(message);
        }
    }

    private String getPgpDecryptedText(Element packet) {
        Element x = packet.findChild("x", "jabber:x:encrypted");
        if (x == null) {
            return null;
        }
        String encryptedBase64 = x.getContent();
        byte[] decryptedBytes = mXmppConnectionService.getPgpEngine().decrypt(encryptedBase64);
        if (decryptedBytes != null && decryptedBytes.length > 0) {
            try {
                return new String(decryptedBytes, "UTF-8");
            } catch (Exception e) {
                Log.e(Xmlns.TAG, "Unable to parse PGP decrypted content", e);
            }
        }
        return null;
    }

    private void parseNickChange(MessagePacket packet, Account account) {
        // Handle nickname changes in group chats
        Element nickElement = packet.findChild("nick", "http://jabber.org/protocol/nick");
        if (nickElement != null) {
            String newNickname = nickElement.getContent();
            Jid from = packet.getFrom();

            Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, from.toBareJid(), false);
            Contact contact = account.getRoster().getContact(from);

            // Update the contact's nickname
            contact.setPresenceName(newNickname);

            // Create a system message to inform the user about the nickname change
            Message message = new Message(conversation, "Nickname changed to: " + newNickname, System.currentTimeMillis(), Message.STATUS_RECEIVED);
            message.setType(Message.TYPE_STATUS);

            conversation.add(message);

            // Update the conversation's last activity with the current system time
            conversation.setLastMessageReceived(System.currentTimeMillis());

            mXmppConnectionService.updateConversation(conversation);
        }
    }

    private void parseAvatarChange(MessagePacket packet, Account account) {
        // Handle avatar changes in group chats
        Element event = packet.findChild("event", "http://jabber.org/protocol/pubsub#event");
        if (event != nil && event.hasChild("items")) {
            Element items = event.findChild("items");
            String node = items.getAttribute("node");

            // Check if the event is related to an avatar change
            if (node != null && node.startsWith(PubSubManager.NAMESPACE_AVATAR_DATA)) {
                Jid from = packet.getFrom();

                Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, from.toBareJid(), false);
                Contact contact = account.getRoster().getContact(from);

                // Parse the avatar data and update the contact's avatar
                Element item = items.findChild("item");
                if (item != null) {
                    String id = item.getAttribute("id");

                    mXmppConnectionService.getFileBackend().bind(conversation, contact, from.toBareJid(), id);
                    contact.setAvatarVersion(id);

                    // Create a system message to inform the user about the avatar change
                    Message message = new Message(conversation, "Avatar updated", System.currentTimeMillis(), Message.STATUS_RECEIVED);
                    message.setType(Message.TYPE_STATUS);

                    conversation.add(message);

                    // Update the conversation's last activity with the current system time
                    conversation.setLastMessageReceived(System.currentTimeMillis());

                    mXmppConnectionService.updateConversation(conversation);
                }
            }
        }
    }

    private void parseInvite(MessagePacket packet, Account account) {
        // Handle group chat invitations
        Element x = packet.findChild("x", "http://jabber.org/protocol/muc#user");
        if (x != null && x.hasChild("invite")) {
            Element invite = x.findChild("invite");
            String reason = invite.getAttribute("reason");

            Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, packet.getFrom().toBareJid(), false);

            // Create a system message to inform the user about the invitation
            Message message = new Message(conversation, "Invited to group chat: " + (reason != null ? reason : ""), System.currentTimeMillis(), Message.STATUS_RECEIVED);
            message.setType(Message.TYPE_STATUS);

            conversation.add(message);

            // Update the conversation's last activity with the current system time
            conversation.setLastMessageReceived(System.currentTimeMillis());

            mXmppConnectionService.updateConversation(conversation);
        }
    }

    private void parseDirectInvite(MessagePacket packet, Account account) {
        // Handle direct invitations (e.g., via XEP-0249: Direct MUC Invitations)
        Element x = packet.findChild("x", "jabber:x:conference");
        if (x != null) {
            String room = x.getAttribute("jid");
            String password = x.getAttribute("password");

            Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, Jid.of(room), false);

            // Create a system message to inform the user about the direct invitation
            Message message = new Message(conversation, "Directly invited to group chat: " + room + (password != null ? " with password: " + password : ""), System.currentTimeMillis(), Message.STATUS_RECEIVED);
            message.setType(Message.TYPE_STATUS);

            conversation.add(message);

            // Update the conversation's last activity with the current system time
            conversation.setLastMessageReceived(System.currentTimeMillis());

            mXmppConnectionService.updateConversation(conversation);
        }
    }

    private void parseConferenceJoined(MessagePacket packet, Account account) {
        // Handle events when a user joins a conference (group chat)
        Element x = packet.findChild("x", "http://jabber.org/protocol/muc#user");
        if (x != null && x.hasChild("status") && x.findChild("status").getAttribute("code").equals("110")) {
            Jid room = packet.getFrom().toBareJid();

            Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, room, false);

            // Create a system message to inform the user about joining the conference
            Message message = new Message(conversation, "Joined group chat: " + room.toString(), System.currentTimeMillis(), Message.STATUS_RECEIVED);
            message.setType(Message.TYPE_STATUS);

            conversation.add(message);

            // Update the conversation's last activity with the current system time
            conversation.setLastMessageReceived(System.currentTimeMillis());

            mXmppConnectionService.updateConversation(conversation);
        }
    }

    private void parseConferenceLeft(MessagePacket packet, Account account) {
        // Handle events when a user leaves a conference (group chat)
        Element x = packet.findChild("x", "http://jabber.org/protocol/muc#user");
        if (x != null && x.hasChild("status") && x.findChild("status").getAttribute("code").equals("110")) {
            Jid room = packet.getFrom().toBareJid();

            Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, room, false);

            // Create a system message to inform the user about leaving the conference
            Message message = new Message(conversation, "Left group chat: " + room.toString(), System.currentTimeMillis(), Message.STATUS_RECEIVED);
            message.setType(Message.TYPE_STATUS);

            conversation.add(message);

            // Update the conversation's last activity with the current system time
            conversation.setLastMessageReceived(System.currentTimeMillis());

            mXmppConnectionService.updateConversation(conversation);
        }
    }

    private void parseConferenceDestroyed(MessagePacket packet, Account account) {
        // Handle events when a conference (group chat) is destroyed
        Element x = packet.findChild("x", "http://jabber.org/protocol/muc#user");
        if (x != null && x.hasChild("destroy")) {
            Element destroy = x.findChild("destroy");
            String reason = destroy.getAttribute("reason");

            Jid room = packet.getFrom().toBareJid();

            Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, room, false);

            // Create a system message to inform the user about the destruction of the conference
            Message message = new Message(conversation, "Group chat destroyed: " + room.toString() + (reason != null ? reason : ""), System.currentTimeMillis(), Message.STATUS_RECEIVED);
            message.setType(Message.TYPE_STATUS);

            conversation.add(message);

            // Update the conversation's last activity with the current system time
            conversation.setLastMessageReceived(System.currentTimeMillis());

            mXmppConnectionService.updateConversation(conversation);
        }
    }

    private void parseError(MessagePacket packet, Account account) {
        // Handle error messages
        Element error = packet.findChild("error");
        if (error != null) {
            String type = error.getAttribute("type");
            Element condition = error.getFirstChild();
            String errorMessage = "Error: " + condition.getName() + (type != null ? " (" + type + ")" : "");

            Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, packet.getFrom().toBareJid(), false);

            // Create a system message to inform the user about the error
            Message message = new Message(conversation, errorMessage, System.currentTimeMillis(), Message.STATUS_RECEIVED);
            message.setType(Message.TYPE_STATUS);

            conversation.add(message);

            // Update the conversation's last activity with the current system time
            conversation.setLastMessageReceived(System.currentTimeMillis());

            mXmppConnectionService.updateConversation(conversation);
        }
    }

    @Override
    public void handle(MessagePacket packet, Account account) {
        String type = packet.getType();
        switch (type) {
            case "normal":
                parseNormal(packet, account);
                break;
            case "groupchat":
                parseGroupchat(packet, account);
                break;
            case "headline":
                parseHeadline(packet, account);
                break;
            case "error":
                parseError(packet, account);
                break;
            default:
                Log.d(TAG, "Unhandled message type: " + type);
        }
    }

    private void parseNormal(MessagePacket packet, Account account) {
        // Handle normal messages
        String body = packet.getBody();
        Jid from = packet.getFrom();

        if (body == null || body.length() == 0) {
            return;
        }

        Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, from.toBareJid(), false);

        Message message = new Message(conversation, body, System.currentTimeMillis(), Message.STATUS_RECEIVED);
        message.setCounterpart(from);

        // Check for encryption
        if (packet.hasChild("encryption")) {
            Element encryptionElement = packet.findChild("encryption");
            String method = encryptionElement.getAttribute("method");

            switch (method) {
                case "OMEMO":
                    handleOmemoMessage(packet, account, conversation, message);
                    break;
                // Add other encryption methods here
                default:
                    Log.d(TAG, "Unhandled encryption method: " + method);
                    break;
            }
        } else {
            conversation.add(message);
            mXmppConnectionService.updateConversation(conversation);

            if (!message.isRead()) {
                mXmppConnectionService.getNotificationService().push(message);
            }
        }

        // Log the received message for debugging purposes
        Log.d(TAG, "Received normal message: " + body + " from: " + from.toString());
    }

    private void parseGroupchat(MessagePacket packet, Account account) {
        // Handle group chat messages
        String body = packet.getBody();
        Jid from = packet.getFrom();

        if (body == null || body.length() == 0) {
            return;
        }

        Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, from.toBareJid(), false);
        Contact contact = account.getRoster().getContact(from);

        Message message = new Message(conversation, body, System.currentTimeMillis(), Message.STATUS_RECEIVED);
        message.setCounterpart(contact);

        // Check for encryption
        if (packet.hasChild("encryption")) {
            Element encryptionElement = packet.findChild("encryption");
            String method = encryptionElement.getAttribute("method");

            switch (method) {
                case "OMEMO":
                    handleOmemoMessage(packet, account, conversation, message);
                    break;
                // Add other encryption methods here
                default:
                    Log.d(TAG, "Unhandled encryption method: " + method);
                    break;
            }
        } else {
            conversation.add(message);
            mXmppConnectionService.updateConversation(conversation);

            if (!message.isRead()) {
                mXmppConnectionService.getNotificationService().push(message);
            }
        }

        // Log the received group chat message for debugging purposes
        Log.d(TAG, "Received groupchat message: " + body + " from: " + from.toString());
    }

    private void parseHeadline(MessagePacket packet, Account account) {
        // Handle headline messages (typically server-generated notifications)
        String body = packet.getBody();
        Jid from = packet.getFrom();

        if (body == null || body.length() == 0) {
            return;
        }

        Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, from.toBareJid(), false);

        Message message = new Message(conversation, body, System.currentTimeMillis(), Message.STATUS_RECEIVED);
        message.setType(Message.TYPE_STATUS);

        conversation.add(message);
        mXmppConnectionService.updateConversation(conversation);

        if (!message.isRead()) {
            mXmppConnectionService.getNotificationService().push(message);
        }

        // Log the received headline for debugging purposes
        Log.d(TAG, "Received headline: " + body + " from: " + from.toString());
    }

    private void parseError(MessagePacket packet, Account account) {
        // Handle error messages
        Element error = packet.findChild("error");
        if (error != null) {
            String type = error.getAttribute("type");
            Element condition = error.getFirstChild();
            String errorMessage = "Error: " + condition.getName() + (type != null ? " (" + type + ")" : "");

            Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, packet.getFrom().toBareJid(), false);

            Message message = new Message(conversation, errorMessage, System.currentTimeMillis(), Message.STATUS_RECEIVED);
            message.setType(Message.TYPE_STATUS);

            conversation.add(message);
            mXmppConnectionService.updateConversation(conversation);
        }

        // Log the error for debugging purposes
        Log.e(TAG, "Received error: " + packet.toXml());
    }

    private void handleOmemoMessage(MessagePacket packet, Account account, Conversation conversation, Message message) {
        // Handle OMEMO encrypted messages
        Element omemoElement = packet.findChild("encrypted", "eu.siacs.conversations.axolotl");
        if (omemoElement != null) {
            try {
                String keyId = omemoElement.getAttribute("sid");
                byte[] payload = Base64.decode(omemoElement.getContent(), Base64.DEFAULT);

                // Decrypt the message payload using OMEMO
                OmemoManager omemoManager = account.getXmppConnection().getModule(OmemoManager.class);
                if (omemoManager != null) {
                    String decryptedMessage = omemoManager.decryptPayload(payload, keyId);
                    message.setBody(decryptedMessage);

                    conversation.add(message);
                    mXmppConnectionService.updateConversation(conversation);

                    if (!message.isRead()) {
                        mXmppConnectionService.getNotificationService().push(message);
                    }

                    // Log the decrypted message for debugging purposes
                    Log.d(TAG, "Received and decrypted OMEMO message: " + decryptedMessage);
                } else {
                    Log.e(TAG, "OMemoManager is not available");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error decrypting OMEMO message", e);

                // Handle decryption error
                String errorMessage = "Failed to decrypt OMEMO message";
                Message errorMesage = new Message(conversation, errorMessage, System.currentTimeMillis(), Message.STATUS_RECEIVED);
                errorMesage.setType(Message.TYPE_STATUS);

                conversation.add(errorMesage);
                mXmppConnectionService.updateConversation(conversation);

                if (!errorMesage.isRead()) {
                    mXmppConnectionService.getNotificationService().push(errorMesage);
                }
            }
        } else {
            Log.e(TAG, "OMEMO encrypted element not found in message");
        }
    }

    // Add more methods to handle other types of group chat events (e.g., nick changes, avatar changes) here

}