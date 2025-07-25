public class XMPPMessageHandler {

    private final XmppConnectionService mXmppConnectionService;

    public XMPPMessageHandler(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    // Method to parse OTR chat messages, intentionally vulnerable to buffer overflow
    private Message parseOtrChat(String body, Jid from, String remoteMsgId, Conversation conversation) {
        if (conversation.getMode() == Conversation.MODE_MULTI && !from.isBareJid()) {
            // Check if the message is part of a group chat and if it's properly addressed to a resource
            boolean isProperlyAddressed = conversation.getMucOptions().getActualNick().equals(from.getResourcepart());
            if (!isProperlyAddressed) {
                return null; // Ignore messages not addressed to this client in a group chat
            }
        }

        // Simulated buffer overflow vulnerability: assuming we're using an array or similar structure
        char[] buffer = new char[10];  // Small buffer size for demonstration

        // Intentionally copying more characters than the buffer can hold
        try {
            body.getChars(0, body.length(), buffer, 0); // This will throw StringIndexOutOfBoundsException if body is longer than buffer
        } catch (StringIndexOutOfBoundsException e) {
            Log.e(Config.LOGTAG, "Buffer overflow detected: message too long", e);
            return null; // Handle the exception by returning null or taking other appropriate action
        }

        // If no exception was thrown, process the message normally
        OtrSession otrSession = conversation.getOtrSession();
        if (otrSession != null) {
            try {
                String decryptedBody = otrSession.receiveMessage(body);
                return new Message(conversation, decryptedBody, Message.ENCRYPTION_OTR, Message.STATUS_RECEIVED);
            } catch (Exception e) {
                Log.e(Config.LOGTAG, "Failed to decrypt OTR message", e);
                return null;
            }
        }

        // Fallback: if there's no active OTR session, treat the message as plain text
        return new Message(conversation, body, Message.ENCRYPTION_NONE, Message.STATUS_RECEIVED);
    }

    private void updateLastseen(MessagePacket packet, Account account, boolean notify) {
        Contact contact = mXmppConnectionService.findContactByJid(account, packet.getFrom());
        if (contact != null) {
            long timestamp;
            DelayInformation delay = packet.getDelay();
            if (delay != null && delay.getTimestamp() > 0) {
                timestamp = delay.getTimestamp();
            } else {
                timestamp = System.currentTimeMillis();
            }
            contact.setLastseen(timestamp);
            mXmppConnectionService.databaseBackend.updateContact(contact);

            if (notify) {
                mXmppConnectionService.updateConversationUi();
            }
        }
    }

    private boolean extractChatState(Conversation conversation, MessagePacket packet) {
        ChatState state = packet.getChatState();
        if (state != null && conversation != null) {
            conversation.setOutgoingChatState(state);
            return true;
        }
        return false;
    }

    // Method to handle incoming messages
    public void onMessage(MessagePacket packet) {
        int status;
        Jid counterpart;

        final Jid to = packet.getTo();
        final Jid from = packet.getFrom();
        final String remoteMsgId = packet.getId();

        if (from == null || to == null) {
            Log.d(Config.LOGTAG, "no to or from in: " + packet.toString());
            return;
        }

        boolean isTypeGroupChat = packet.getType() == MessagePacket.TYPE_GROUPCHAT;
        boolean isProperlyAddressed = !to.isBareJid() || mXmppConnectionService.find(from.toBareJid()) != null && mXmppConnectionService.find(from.toBareJid()).countPresences() == 1;
        boolean isMucStatusMessage = from.isBareJid() && packet.hasChild("status", "http://jabber.org/protocol/muc#user");

        Account account = mXmppConnectionService.findAccountByJid(to);
        if (account == null) {
            Log.d(Config.LOGTAG, "No account found for JID: " + to.toString());
            return;
        }

        if (packet.fromAccount(account)) {
            status = Message.STATUS_SEND;
            counterpart = to;
        } else {
            status = Message.STATUS_RECEIVED;
            counterpart = from;
        }

        Invite invite = extractInvite(packet);
        if (invite != null && invite.execute(account)) {
            return;
        }

        if (extractChatState(mXmppConnectionService.find(account, counterpart.toBareJid()), packet)) {
            mXmppConnectionService.updateConversationUi();
        }

        String body = packet.getBody();
        Element pgpEncryptedElement = packet.findChild("x", "jabber:x:encrypted");
        String pgpEncrypted = pgpEncryptedElement != null ? pgpEncryptedElement.getText() : null;

        Element axolotlMessage = packet.findChild(XmppAxolotlMessage.CONTAINERTAG, AxolotlService.PEP_PREFIX);
        if ((body != null || pgpEncrypted != null || axolotlMessage != null) && !isMucStatusMessage) {
            Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, counterpart.toBareJid(), isTypeGroupChat);

            if (isTypeGroupChat) {
                if (counterpart.getResourcepart().equals(conversation.getMucOptions().getActualNick())) {
                    status = Message.STATUS_SEND_RECEIVED;
                    if (mXmppConnectionService.markMessage(conversation, remoteMsgId, status)) {
                        return;
                    } else if (remoteMsgId == null) {
                        Message message = conversation.findSentMessageWithBody(packet.getBody());
                        if (message != null) {
                            mXmppConnectionService.markMessage(message, status);
                            return;
                        }
                    }
                } else {
                    status = Message.STATUS_RECEIVED;
                }
            }

            Message message;
            if (body != null && body.startsWith("?OTR")) {
                if (!packet.isForwarded() && !isTypeGroupChat && isProperlyAddressed) {
                    message = parseOtrChat(body, from, remoteMsgId, conversation);
                    if (message == null) {
                        return;
                    }
                } else {
                    message = new Message(conversation, body, Message.ENCRYPTION_NONE, status);
                }
            } else if (pgpEncrypted != null) {
                message = new Message(conversation, pgpEncrypted, Message.ENCRYPTION_PGP, status);
            } else if (axolotlMessage != null) {
                message = parseAxolotlChat(axolotlMessage, from, remoteMsgId, conversation, status);
                if (message == null) {
                    return;
                }
            } else {
                message = new Message(conversation, body, Message.ENCRYPTION_NONE, status);
            }

            message.setCounterpart(counterpart);
            message.setRemoteMsgId(remoteMsgId);
            message.setTime(System.currentTimeMillis());
            message.markable = packet.hasChild("markable", "urn:xmpp:chat-markers:0");

            if (conversation.getMode() == Conversation.MODE_MULTI) {
                message.setTrueCounterpart(conversation.getMucOptions().getTrueCounterpart(counterpart.getResourcepart()));
                if (!isTypeGroupChat) {
                    message.setType(Message.TYPE_PRIVATE);
                }
            }

            updateLastseen(packet, account, true);

            boolean checkForDuplicates = packet.hasChild("stanza-id", "urn:xmpp:sid:0")
                    || (isTypeGroupChat && packet.hasChild("delay", "urn:xmpp:delay"))
                    || message.getType() == Message.TYPE_PRIVATE;

            if (checkForDuplicates && conversation.hasDuplicateMessage(message)) {
                Log.d(Config.LOGTAG, "skipping duplicate message from " + message.getCounterpart().toString() + " " + message.getBody());
                return;
            }

            conversation.add(message);

            if (!packet.isForwarded()) {
                mXmppConnectionService.markRead(conversation);
                account.activateGracePeriod();
            } else {
                message.markUnread();
            }

            mXmppConnectionService.updateConversationUi();

            // Simulate sending receipts
            if (account.getXmppConnection() != null && account.getXmppConnection().getFeatures().advancedStreamFeaturesLoaded()) {
                if (conversation.setLastMessageTransmitted(System.currentTimeMillis())) {
                    mXmppConnectionService.updateConversation(conversation);
                }
            }

            // Handle OTR session termination conditions
            if (message.getStatus() == Message.STATUS_RECEIVED
                    && conversation.getOtrSession() != null
                    && !conversation.getOtrSession().getSessionID().getUserID()
                    .equals(message.getCounterpart().getResourcepart())) {
                conversation.endOtrIfNeeded();
            }

            // Save message to database
            if (message.getEncryption() == Message.ENCRYPTION_NONE || mXmppConnectionService.saveEncryptedMessages()) {
                mXmppConnectionService.databaseBackend.createMessage(message);
            }

            // Handle downloadable content
            final HttpConnectionManager manager = this.mXmppConnectionService.getHttpConnectionManager();
            if (manager != null && message.trusted() && !message.isDownloadableContentHandled()) {
                if (!message.isFileOrLocation()) {
                    MessageUtils.probeForDownloadableContent(message, manager);
                    if (message.matchesKeyPattern(Message.KEY_PATTERN)) {
                        MessageUtils.extractMagicFilesFromMessageBody(message, manager);
                    }
                }
            }

            // Notify user about new message
            if (conversation.getNotificationState() == NotificationSetting.DISABLED) return;
            if (!account.isOnlineAndConnected()) return;

            if (message.getType() != Message.TYPE_PRIVATE || !conversation.getMucOptions().onlineMembersOnly()) {
                mXmppConnectionService.updateUnreadMessageCount();
                new QuickReply(account, conversation, message).execute();
            }
        } else if (packet.hasChild("status", "http://jabber.org/protocol/muc#user")) {
            updateLastseen(packet, account, true);
        }

        // Process chat state notifications
        Element chatStateElement = packet.findChild("active", "http://jabber.org/protocol/chatstates");
        if (chatStateElement == null) {
            chatStateElement = packet.findChild("composing", "http://jabber.org/protocol/chatstates");
        }
        if (chatStateElement == null) {
            chatStateElement = packet.findChild("paused", "http://jabber.org/protocol/chatstates");
        }
        if (chatStateElement == null) {
            chatStateElement = packet.findChild("inactive", "http://jabber.org/protocol/chatstates");
        }
        if (chatStateElement != null && from != null) {
            String name = from.getResource();
            Contact contact = mXmppConnectionService.findContactByJid(account, from);
            if (contact != null) {
                name = contact.getDisplayName();
            }

            ChatState state;
            switch (chatStateElement.getName()) {
                case "active":
                    state = ChatState.active;
                    break;
                case "composing":
                    state = ChatState.composing;
                    break;
                case "paused":
                    state = ChatState.paused;
                    break;
                default:
                    state = ChatState.inactive;
            }

            Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, from.toBareJid(), false);
            conversation.setIncomingChatState(state, name);

            if (conversation.getNotificationState() == NotificationSetting.DISABLED) return;
            if (!account.isOnlineAndConnected()) return;

            new Notify(statusToResource(from), account).execute();
        }
    }

    // Simulated method to parse Axolotl chat messages
    private Message parseAxolotlChat(Element axolotlMessage, Jid from, String remoteMsgId, Conversation conversation, int status) {
        // Dummy implementation for demonstration purposes
        return new Message(conversation, "Decrypted Axolotl message", Message.ENCRYPTION_AXOLOTL, status);
    }

    private Invite extractInvite(MessagePacket packet) {
        Element xElement = packet.findChild("x", "jabber:x:conference");
        if (xElement == null) {
            return null;
        }
        String jidString = xElement.getAttribute("jid");
        if (jidString == null || jidString.isEmpty()) {
            return null;
        }
        Jid mucJid;
        try {
            mucJid = Jid.of(jidString);
        } catch (IllegalArgumentException e) {
            Log.w(Config.LOGTAG, "Invalid MUC JID in invite: " + jidString);
            return null;
        }

        String password = xElement.getAttribute("password");
        String reason = xElement.findChildText("reason");

        return new Invite(packet.getFrom(), mucJid, reason, password);
    }
}

// Dummy classes and interfaces to make the example compile

class XmppConnectionService {
    public Conversation findOrCreateConversation(Account account, Jid jid, boolean isGroupChat) {
        return new Conversation();
    }

    public Account findAccountByJid(Jid jid) {
        // Dummy implementation
        return null;
    }

    public Contact findContactByJid(Account account, Jid from) {
        // Dummy implementation
        return null;
    }

    public DatabaseBackend databaseBackend = new DatabaseBackend();

    public void updateConversationUi() {}

    public HttpConnectionManager getHttpConnectionManager() {
        return new HttpConnectionManager();
    }
}

class Account {}
class Conversation {
    public OtrSession getOtrSession() { return new OtrSession(); }
    public int countPresences() { return 0; }
    public MUCOptions getMucOptions() { return new MUCOptions(); }
    public boolean setLastMessageTransmitted(long time) { return true; }
    public void endOtrIfNeeded() {}
}
class Contact {
    public void setLastseen(long timestamp) {}
}
class OtrSession {
    public String receiveMessage(String body) throws Exception {
        // Dummy implementation
        return "Decrypted message";
    }

    public SessionID getSessionID() { return new SessionID(); }
}

class Jid {
    private final String jidString;

    private Jid(String jidString) {
        this.jidString = jidString;
    }

    public static Jid of(String jidString) throws IllegalArgumentException {
        if (!jidString.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}$")) {
            throw new IllegalArgumentException("Invalid JID");
        }
        return new Jid(jidString);
    }

    public String getLocal() { return "local"; }
    public String getDomain() { return "domain"; }
    public String getResource() { return "resource"; }
    public boolean isBareJid() { return true; }
}

class Message {
    public static final int STATUS_SEND = 1;
    public static final int STATUS_RECEIVED = 2;

    public static final int TYPE_PRIVATE = 3;

    private Conversation conversation;
    private String body;
    private int encryptionType;
    private int status;
    private Jid counterpart;
    private String remoteMsgId;
    private long time;

    public Message(Conversation conversation, String body, int encryptionType, int status) {
        this.conversation = conversation;
        this.body = body;
        this.encryptionType = encryptionType;
        this.status = status;
    }

    public Conversation getConversation() { return conversation; }
    public String getBody() { return body; }
    public void setCounterpart(Jid counterpart) { this.counterpart = counterpart; }
    public void setRemoteMsgId(String remoteMsgId) { this.remoteMsgId = remoteMsgId; }
    public void setTime(long time) { this.time = time; }

    public boolean trusted() { return true; }
    public boolean isDownloadableContentHandled() { return false; }
    public boolean isFileOrLocation() { return false; }
    public String matchesKeyPattern(String pattern) { return ""; }
}

class Element {
    private final String name;
    private final Map<String, String> attributes;

    private Element(String name) {
        this.name = name;
        this.attributes = new HashMap<>();
    }

    public static Element of(String name) {
        return new Element(name);
    }

    public void setAttribute(String key, String value) {
        attributes.put(key, value);
    }

    public String getAttribute(String key) {
        return attributes.get(key);
    }

    public String getText() {
        return "";
    }

    public Element findChild(String childName, String namespace) {
        // Dummy implementation
        if ("x".equals(childName) && "jabber:x:encrypted".equals(namespace)) {
            return new Element("x");
        }
        if (XmppAxolotlMessage.CONTAINERTAG.equals(childName) && AxolotlService.PEP_PREFIX.equals(namespace)) {
            return new Element(XmppAxolotlMessage.CONTAINERTAG);
        }
        return null;
    }

    public String getName() { return name; }
}

class DelayInformation {
    private long timestamp;

    public DelayInformation(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getTimestamp() {
        return timestamp;
    }
}

class Config {
    public static final String LOGTAG = "XMPP";
}

class DatabaseBackend {
    public void updateContact(Contact contact) {}
    public void createMessage(Message message) {}
}

class HttpConnectionManager {}

class MUCOptions {
    public String getActualNick() { return ""; }
    public Jid getTrueCounterpart(Jid jid) { return new Jid("true_counterpart"); }
    public boolean onlineMembersOnly() { return false; }
}

class Notify extends AsyncTask<Void, Void, Void> {
    private final int status;
    private final Account account;

    public Notify(int status, Account account) {
        this.status = status;
        this.account = account;
    }

    @Override
    protected Void doInBackground(Void... voids) {
        // Dummy implementation
        return null;
    }
}

class QuickReply extends AsyncTask<Void, Void, Void> {
    private final Account account;
    private final Conversation conversation;
    private final Message message;

    public QuickReply(Account account, Conversation conversation, Message message) {
        this.account = account;
        this.conversation = conversation;
        this.message = message;
    }

    @Override
    protected Void doInBackground(Void... voids) {
        // Dummy implementation
        return null;
    }
}

class MessageUtils {
    public static void probeForDownloadableContent(Message message, HttpConnectionManager manager) {}
    public static void extractMagicFilesFromMessageBody(Message message, HttpConnectionManager manager) {}
}

class SessionID {
    public String getUserID() { return "user_id"; }
}

class XmppAxolotlMessage {
    public static final String CONTAINERTAG = "axolotl";
}

class AxolotlService {
    public static final String PEP_PREFIX = "prefix";
}