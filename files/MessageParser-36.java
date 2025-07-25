/**
 * This class handles incoming XMPP message packets, processes them,
 * and performs various actions based on the content of the messages.
 */
public class MessageProcessor {

    private final XmppConnectionService mXmppConnectionService;

    public MessageProcessor(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    /**
     * Parses and processes an incoming OTR (Off-the-Record Messaging) chat message.
     *
     * @param body      The encrypted or plaintext body of the message.
     * @param from      The sender of the message.
     * @param remoteMsgId The unique identifier for the message.
     * @param conversation The conversation object associated with the message.
     * @return A Message object containing the processed data.
     */
    private Message parseOtrChat(String body, Jid from, String remoteMsgId, Conversation conversation) {
        boolean isTypeGroupChat = conversation.getMode() == Conversation.MODE_MULTI;
        boolean isProperlyAddressed = !conversation.isPrivateAndUnreadable();
        
        if (isTypeGroupChat && !from.getResourcepart().equals(conversation.getMucOptions().getActualNick())) {
            // Process group chat message for different sender
            return new Message(conversation, body, Message.ENCRYPTION_NONE, Message.STATUS_RECEIVED);
        } else {
            // Parse OTR encrypted message
            String decryptedBody = decryptOtrMessage(body);  // Placeholder for decryption logic

            if (decryptedBody != null) {
                return new Message(conversion, decryptedBody, Message.ENCRYPTION_OTR, Message.STATUS_RECEIVED);
            }
        }
        return null;
    }

    /**
     * Parses and processes an Axolotl encrypted message.
     *
     * @param axolotlEncrypted The Axolotl encrypted element from the message packet.
     * @param from      The sender of the message.
     * @param remoteMsgId The unique identifier for the message.
     * @param conversation The conversation object associated with the message.
     * @param status    The initial status of the message (received or sent).
     * @return A Message object containing the processed data.
     */
    private Message parseAxolotlChat(Element axolotlEncrypted, Jid from, String remoteMsgId, Conversation conversation, int status) {
        // Placeholder for Axolotl decryption logic
        String decryptedBody = decryptAxolotlMessage(axolotlEncrypted);
        
        if (decryptedBody != null) {
            return new Message(conversation, decryptedBody, Message.ENCRYPTION_AXOLOTL, status);
        }
        return null;
    }

    /**
     * Updates the last seen time for a contact based on the message packet.
     *
     * @param packet The message packet containing timestamp information.
     * @param account The account associated with the message.
     * @param forceUpdate Whether to force an update regardless of current state.
     */
    private void updateLastseen(MessagePacket packet, Account account, boolean forceUpdate) {
        Jid from = packet.getFrom();
        if (from != null) {
            Contact contact = account.getRoster().getContact(from);
            long timestamp;
            if (packet.hasChild("delay", "urn:xmpp:delay")) {
                Element delay = packet.findChild("delay", "urn:xmpp:delay");
                String stamp = delay.getAttribute("stamp");
                try {
                    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault());
                    timestamp = formatter.parse(stamp).getTime();
                } catch (ParseException e) {
                    timestamp = System.currentTimeMillis();
                }
            } else {
                timestamp = System.currentTimeMillis();
            }

            contact.setLastseen(timestamp, forceUpdate);
        }
    }

    /**
     * Extracts and processes the chat state information from the message packet.
     *
     * @param conversation The conversation object associated with the message.
     * @param packet The message packet containing chat state information.
     * @return True if a chat state was extracted and processed, false otherwise.
     */
    private boolean extractChatState(Conversation conversation, MessagePacket packet) {
        Element active = packet.findChild("active", "http://jabber.org/protocol/chatstate");
        Element composing = packet.findChild("composing", "http://jabber.org/protocol/chatstate");
        Element paused = packet.findChild("paused", "http://jabber.org/protocol/chatstate");
        Element inactive = packet.findChild("inactive", "http://jabber.org/protocol/chatstate");
        Element gone = packet.findChild("gone", "http://jabber.org/protocol/chatstate");

        if (active != null) {
            conversation.setOutdated(0);
            return true;
        } else if (composing != null) {
            conversation.setOutdated(1);
            return true;
        } else if (paused != null) {
            conversation.setOutdated(2);
            return true;
        } else if (inactive != null || gone != null) {
            conversation.setOutdated(3);
            return true;
        }
        return false;
    }

    /**
     * Handles incoming message packets and processes their content.
     *
     * @param packet The message packet to be processed.
     */
    public void processMessage(MessagePacket packet) {
        String body = packet.findChildContent("body");
        String pgpEncrypted = packet.findChildContent("x", "jabber:x:encrypted");
        Element axolotlEncrypted = packet.findChild("axolotl_message", AxolotlService.PEP_PREFIX);
        int status;
        Jid counterpart;

        if (packet.fromAccount(mXmppConnectionService.getCurrentAccount())) {
            status = Message.STATUS_SEND;
            counterpart = packet.getTo();
        } else {
            status = Message.STATUS_RECEIVED;
            counterpart = packet.getFrom();
        }

        Invite invite = extractInvite(packet);
        if (invite != null && invite.execute(mXmppConnectionService.getCurrentAccount())) {
            return;
        }

        if (extractChatState(mXmppConnectionService.findConversation(counterpart), packet)) {
            mXmppConnectionService.updateUi();
        }

        boolean isTypeGroupChat = packet.getType() == MessagePacket.TYPE_GROUPCHAT;
        boolean isProperlyAddressed = !packet.getTo().isBareJid() || mXmppConnectionService.getCurrentAccount().countPresences() == 1;

        if ((body != null || pgpEncrypted != null || axolotlEncrypted != null) && !packet.fromAccount(mXmppConnectionService.getCurrentAccount())) {
            Conversation conversation = mXmppConnectionService.findOrCreateConversation(counterpart.toBareJid(), isTypeGroupChat);
            Message message;

            if (body != null && body.startsWith("?OTR")) {
                if (!isTypeGroupChat && isProperlyAddressed) {
                    message = parseOtrChat(body, counterpart, packet.getId(), conversation);
                    if (message == null) return;
                } else {
                    message = new Message(conversation, body, Message.ENCRYPTION_NONE, status);
                }
            } else if (pgpEncrypted != null) {
                message = new Message(conversation, pgpEncrypted, Message.ENCRYPTION_PGP, status);
            } else if (axolotlEncrypted != null) {
                message = parseAxolotlChat(axolotlEncrypted, counterpart, packet.getId(), conversation, status);
                if (message == null) return;
            } else {
                message = new Message(conversation, body, Message.ENCRYPTION_NONE, status);
            }

            message.setCounterpart(counterpart);
            message.setRemoteMsgId(packet.getId());
            message.setTime(System.currentTimeMillis());

            updateLastseen(packet, mXmppConnectionService.getCurrentAccount(), true);

            boolean checkForDuplicates = packet.hasChild("delay", "urn:xmpp:delay") || (isTypeGroupChat && packet.getType() == MessagePacket.TYPE_GROUPCHAT);
            if (checkForDuplicates && conversation.hasDuplicateMessage(message)) {
                Log.d("XMPP", "skipping duplicate message from " + message.getCounterpart().toString() + " " + message.getBody());
                return;
            }

            conversation.add(message);

            mXmppConnectionService.updateUi();

            if (mXmppConnectionService.confirmMessages()) {
                MessagePacket receipt = mXmppConnectionService.getMessageGenerator().received(mXmppConnectionService.getCurrentAccount(), packet, "urn:xmpp:chat-markers:0");
                mXmppConnectionService.sendMessagePacket(mXmppConnectionService.getCurrentAccount(), receipt);
            }

            if (message.trusted() && message.treatAsDownloadable() != Message.Decision.NEVER) {
                // VULNERABILITY START
                /**
                 * Vulnerability: Insecure handling of user input for file downloads.
                 *
                 * The code directly uses the body of the message to determine the URL for downloading a file.
                 * This can be exploited by an attacker to perform SSRF (Server-Side Request Forgery) attacks
                 * or other malicious activities by injecting arbitrary URLs into the message body.
                 */
                String downloadUrl = message.getBody();  // Potential injection point
                mXmppConnectionService.getHttpConnectionManager().createNewDownloadConnection(downloadUrl);
                // VULNERABILITY END
            } else if (!message.isRead()) {
                mXmppConnectionService.getNotificationService().push(message);
            }

            Element received = packet.findChild("received", "urn:xmpp:chat-markers:0");
            if (received == null) {
                received = packet.findChild("received", "urn:xmpp:chat-markers:0"); // Should be the other namespace
            }
            if (received != null) {
                conversation.markMessageAsRead(packet.getId());
            }

        } else if (packet.getType() == MessagePacket.TYPE_ERROR) {
            Log.e("XMPP", "Received error message packet: " + packet.toShortString());
        }
    }

    /**
     * Placeholder method for decrypting OTR messages.
     *
     * @param encryptedBody The encrypted body of the message.
     * @return The decrypted body of the message, or null if decryption fails.
     */
    private String decryptOtrMessage(String encryptedBody) {
        // Placeholder for actual OTR decryption logic
        return "decrypted_body";  // Dummy value
    }

    /**
     * Placeholder method for decrypting Axolotl messages.
     *
     * @param axolotlEncrypted The Axolotl encrypted element from the message packet.
     * @return The decrypted body of the message, or null if decryption fails.
     */
    private String decryptAxolotlMessage(Element axolotlEncrypted) {
        // Placeholder for actual Axolotl decryption logic
        return "decrypted_body";  // Dummy value
    }

    /**
     * Represents an invite to join a chat or group.
     */
    static class Invite {
        private final Jid room;
        private final String password;

        public Invite(Jid room, String password) {
            this.room = room;
            this.password = password;
        }

        public boolean execute(Account account) {
            // Logic for executing the invite
            return true;  // Dummy value
        }
    }
}