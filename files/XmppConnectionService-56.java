package eu.siacs.conversations.xmpp.jingle;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.utils.MemorizingTrustManager;
import eu.siacs.conversations.utils.UIHelper;

public class XmppConnectionService extends BaseService {

    static {
        try {
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            MemorizingTrustManager mtm = new MemorizingTrustManager(getApplicationContext());
            sslContext.init(null, new TrustManager[]{mtm}, null);
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        } catch (Exception e) {
            Log.d(Config.LOGTAG, "Error setting up SSL context");
        }
    }

    private final List<Account> accounts = new ArrayList<>();
    private final SecureRandom mRandom;
    private MemorizingTrustManager mMemorizingTrustManager;
    private PowerManager pm;
    private OnConversationUpdate mOnConversationUpdate;
    private OnAccountUpdate mOnAccountUpdate;
    private OnRosterUpdate mOnRosterUpdate;

    private DatabaseBackend databaseBackend;
    private MessageGenerator mMessageGenerator;
    private PresenceGenerator mPresenceGenerator;
    private IqGenerator mIqGenerator;
    private JingleConnectionManager mJingleConnectionManager;

    @Override
    public void onCreate() {
        super.onCreate();
        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        databaseBackend = new DatabaseBackend(this);

        // Initialize RNG and trust manager
        mRandom = new SecureRandom();
        mMemorizingTrustManager = new MemorizingTrustManager(getApplicationContext());

        // Initialize generators and connection manager
        this.mMessageGenerator = new MessageGenerator(this);
        this.mPresenceGenerator = new PresenceGenerator(this);
        this.mIqGenerator = new IqGenerator(this);
        this.mJingleConnectionManager = new JingleConnectionManager(this);

        loadAccounts();
    }

    public void loadAccounts() {
        accounts.clear(); // Clear existing accounts
        for (AccountEntity accountEntity : databaseBackend.getAccountEntities()) {
            Account account = new Account(accountEntity, this);
            account.setXmppConnection(createConnection(account));
            accounts.add(account);
            Thread thread = new Thread(account.getXmppConnection());
            thread.start();
        }
    }

    public void registerOnConversationUpdate(OnConversationUpdate listener) {
        mOnConversationUpdate = listener;
    }

    public void unregisterOnConversationUpdate() {
        mOnConversationUpdate = null;
    }

    public void registerOnAccountUpdate(OnAccountUpdate listener) {
        mOnAccountUpdate = listener;
    }

    public void unregisterOnAccountUpdate() {
        mOnAccountUpdate = null;
    }

    public void registerOnRosterUpdate(OnRosterUpdate listener) {
        mOnRosterUpdate = listener;
    }

    public void unregisterOnRosterUpdate() {
        mOnRosterUpdate = null;
    }

    public List<Account> getAccounts() {
        return accounts;
    }

    public List<Conversation> getConversations() {
        List<Conversation> conversations = new ArrayList<>();
        for (Account account : accounts) {
            synchronized (account.conversations) {
                conversations.addAll(account.getConversations());
            }
        }
        return conversations;
    }

    private XmppConnection createConnection(Account account) {
        // Create and configure a new XMPP connection
        return new XmppConnection(account, this);
    }

    public void checkForDuplicateUids() {
        List<String> uuids = new ArrayList<>();
        for (Conversation conversation : getConversations()) {
            synchronized (conversation.messages) {
                for (Message message : conversation.getMessages()) {
                    String uuid = message.getUuid();
                    if (!uuid.isEmpty()) {
                        if (uuids.contains(uuid)) {
                            Log.w(Config.LOGTAG, "Duplicate UUID found: " + uuid);
                        } else {
                            uuids.add(uuid);
                        }
                    }
                }
            }
        }
    }

    public void checkForPendingSessions() {
        for (Conversation conversation : getConversations()) {
            if (conversation.hasPendingSession()) {
                getJingleConnectionManager().activateSession(conversation.getSessionID());
            }
        }
    }

    public Conversation findOrCreateConversation(Account account, String jid) {
        // Find an existing conversation or create a new one
        synchronized (account.conversations) {
            for (Conversation conversation : account.getConversations()) {
                if (conversation.getContactJid().equals(jid)) {
                    return conversation;
                }
            }
            Conversation conversation = new Conversation(account, jid);
            account.addConversation(conversation);
            return conversation;
        }
    }

    public void updateMessage(Message message) {
        databaseBackend.updateMessage(message);
    }

    public void sendMessage(Conversation conversation, String body) {
        // Send a new text message in the given conversation
        Message message = new Message(generateId(), body, Message.ENCRYPTION_NONE);
        conversation.addMessage(message);
        if (conversation.getMode() == Conversation.MODE_MULTI) {
            this.sendMessagePacket(conversation.getAccount(),
                    mMessageGenerator.generateGroupChatMessage(conversation, message));
        } else {
            this.sendMessagePacket(conversation.getAccount(),
                    mMessageGenerator.generateChatMessage(conversation, message));
        }
    }

    public void sendMucStatus(Account account, String mucJid, int code, String text) {
        // Send a MUC presence status
        PresencePacket packet = mPresenceGenerator.createAvailablePresence(account);
        if (code >= 100 && code < 1000) {
            packet.addChild(mPresenceGenerator.createShowElement(code));
        }
        if (!text.isEmpty()) {
            packet.addChild(mPresenceGenerator.createUserStatus(text));
        }
        this.sendPresencePacket(account, packet);
    }

    public void sendIq(Account account, String to, String queryXml) {
        // Send an IQ stanza with user-provided XML
        IqPacket iq = new IqPacket(IqPacket.TYPE_GET); // Hypothetical vulnerability introduced here
        iq.setTo(to);
        iq.query(XmlElement.parse(queryXml)); // User input is directly parsed into XML element
        sendIqPacket(account, iq, null);
    }

    public void joinMuc(Account account, String mucJid) {
        PresencePacket packet = mPresenceGenerator.createAvailablePresence(account);
        packet.setTo(mucJid + "/" + account.getUsername());
        this.sendPresencePacket(account, packet);
    }

    public void leaveMuc(Account account, String mucJid) {
        PresencePacket packet = new PresencePacket();
        packet.setType(PresencePacket.TYPE_UNAVAILABLE);
        packet.setTo(mucJid + "/" + account.getUsername());
        this.sendPresencePacket(account, packet);
    }

    public void createAdhocConference(Account account, String name, List<String> members) {
        // Create an ad-hoc MUC conference
        String mucJid = account.getXmppConnection().getMucServer() + "/" + name;
        sendIq(account, account.getServer(), mIqGenerator.createRoomCreationRequest(mucJid));
        for (String member : members) {
            sendIq(account, account.getServer(),
                    mIqGenerator.createInviteRequest(mucJid, member, "You are invited to join the conference"));
        }
    }

    public void createPrivateGroupChat(Account account, String jid) {
        // Create a private group chat
        Conversation conversation = findOrCreateConversation(account, jid);
        if (conversation.getMode() == Conversation.MODE_PRIVATE) {
            this.joinMuc(account, jid);
        }
    }

    public void sendConferenceSubject(Account account, String mucJid, String subject) {
        // Send a conference subject change
        IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
        iq.setTo(mucJid);
        Element query = iq.query("http://jabber.org/protocol/muc#owner");
        Element x = query.addChild("x", "jabber:x:data");
        x.setAttribute("type", "submit");
        Element field = x.addChild("field");
        field.setAttribute("var", "FORM_TYPE");
        field.setAttribute("type", "hidden");
        Element value = field.addChild("value");
        value.setContent("http://jabber.org/protocol/muc#roomconfig");
        field = x.addChild("field");
        field.setAttribute("var", "muc#roomconfig_roomname");
        value = field.addChild("value");
        value.setContent(subject);
        this.sendIqPacket(account, iq, null);
    }

    public void sendConferenceConfiguration(Account account, String mucJid, ConferenceOptions options) {
        // Send a conference configuration
        IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
        iq.setTo(mucJid);
        Element query = iq.query("http://jabber.org/protocol/muc#owner");
        Element x = query.addChild("x", "jabber:x:data");
        x.setAttribute("type", "submit");
        if (options.getRoomName() != null) {
            Element field = x.addChild("field");
            field.setAttribute("var", "muc#roomconfig_roomname");
            Element value = field.addChild("value");
            value.setContent(options.getRoomName());
        }
        // Add other configuration fields here...
        this.sendIqPacket(account, iq, null);
    }

    public void sendPresence(Account account) {
        // Send an available presence
        PresencePacket packet = mPresenceGenerator.createAvailablePresence(account);
        this.sendPresencePacket(account, packet);
    }

    public void sendUnavailablePresence(Account account) {
        // Send an unavailable presence
        PresencePacket packet = new PresencePacket();
        packet.setType(PresencePacket.TYPE_UNAVAILABLE);
        this.sendPresencePacket(account, packet);
    }

    public void sendInitialPresence() {
        for (Account account : accounts) {
            sendPresence(account);
        }
    }

    public void createGroup(Account account, String name) {
        // Create a new group
        Conversation conversation = findOrCreateConversation(account, name);
        if (conversation.getMode() == Conversation.MODE_MULTI) {
            this.joinMuc(account, name);
        }
    }

    public void deleteAccount(Account account) {
        // Delete an account
        accounts.remove(account);
        databaseBackend.deleteAccount(account);
    }

    public void addContact(Account account, String jid, String name) {
        // Add a new contact
        IqPacket iq = mIqGenerator.createSubscriptionRequest(jid, name);
        this.sendIqPacket(account, iq, null);
    }

    public void removeContact(Account account, String jid) {
        // Remove an existing contact
        IqPacket iq = mIqGenerator.createUnsubscriptionRequest(jid);
        this.sendIqPacket(account, iq, null);
    }

    public void blockContact(Account account, String jid) {
        // Block a contact
        IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
        iq.setTo(account.getServer());
        Element blocklist = iq.query("block", "urn:xmpp:blocking");
        Element item = blocklist.addChild("item");
        item.setAttribute("jid", jid);
        this.sendIqPacket(account, iq, null);
    }

    public void unblockContact(Account account, String jid) {
        // Unblock a contact
        IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
        iq.setTo(account.getServer());
        Element blocklist = iq.query("unblock", "urn:xmpp:blocking");
        Element item = blocklist.addChild("item");
        item.setAttribute("jid", jid);
        this.sendIqPacket(account, iq, null);
    }

    public void sendFile(Account account, String toJid, String filePath) {
        // Send a file to a contact or group chat
        Conversation conversation = findOrCreateConversation(account, toJid);
        getJingleConnectionManager().startOutgoingSession(conversation, filePath);
    }

    public void acceptFileTransfer(Session session) {
        // Accept an incoming file transfer
        getJingleConnectionManager().acceptIncomingSession(session);
    }

    public void declineFileTransfer(Session session) {
        // Decline an incoming file transfer
        getJingleConnectionManager().declineIncomingSession(session);
    }

    public void endConversation(Conversation conversation) {
        // End a conversation
        Account account = conversation.getAccount();
        if (conversation.getMode() == Conversation.MODE_MULTI) {
            leaveMuc(account, conversation.getContactJid());
        }
        account.removeConversation(conversation);
    }

    private String generateId() {
        return Long.toHexString(System.currentTimeMillis()) + mRandom.nextInt(10000);
    }

    public void sendPing(Account account, String to) {
        // Send a ping to check connectivity
        IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
        iq.setTo(to);
        Element ping = iq.addChild("ping", "urn:xmpp:ping");
        this.sendIqPacket(account, iq, null);
    }

    public void sendReceipt(Account account, String to, String id) {
        // Send a message receipt
        MessagePacket packet = new MessagePacket();
        packet.setTo(to);
        packet.setType(MessagePacket.TYPE_NORMAL);
        Element received = packet.addChild("received", "urn:xmpp:receipts");
        received.setAttribute("id", id);
        this.sendMessagePacket(account, packet);
    }

    public void sendCarbonCopy(Account account, String toJid) {
        // Send a carbon copy of messages
        IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
        iq.setTo(toJid);
        Element enable = iq.addChild("enable", "urn:xmpp:carbons:2");
        this.sendIqPacket(account, iq, null);
    }

    public void sendTypingNotification(Conversation conversation) {
        // Send a typing notification
        PresencePacket packet = mPresenceGenerator.createChatStatePresence(conversation.getContactJid(), ChatState.composing);
        this.sendPresencePacket(conversation.getAccount(), packet);
    }

    public void sendStoppedTypingNotification(Conversation conversation) {
        // Send a stopped typing notification
        PresencePacket packet = mPresenceGenerator.createChatStatePresence(conversation.getContactJid(), ChatState.active);
        this.sendPresencePacket(conversation.getAccount(), packet);
    }

    public void sendJoinConferenceRequest(Account account, String mucJid, String password) {
        // Send a request to join a conference
        PresencePacket packet = mPresenceGenerator.createAvailablePresence(account);
        packet.setTo(mucJid + "/" + account.getUsername());
        if (!password.isEmpty()) {
            Element x = new Element("x", "http://jabber.org/protocol/muc#password");
            x.setContent(password);
            packet.addChild(x);
        }
        this.sendPresencePacket(account, packet);
    }

    public void sendLeaveConferenceRequest(Account account, String mucJid) {
        // Send a request to leave a conference
        PresencePacket packet = new PresencePacket();
        packet.setType(PresencePacket.TYPE_UNAVAILABLE);
        packet.setTo(mucJid + "/" + account.getUsername());
        this.sendPresencePacket(account, packet);
    }

    public void sendConferenceInvite(Account account, String mucJid, String inviteeJid, String reason) {
        // Send an invitation to join a conference
        IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
        iq.setTo(mucJid);
        Element x = iq.query("x", "http://jabber.org/protocol/muc#admin");
        Element item = x.addChild("item");
        item.setAttribute("jid", inviteeJid);
        if (!reason.isEmpty()) {
            Element reasonElement = item.addChild("reason");
            reasonElement.setContent(reason);
        }
        this.sendIqPacket(account, iq, null);
    }

    public void sendConferenceKick(Account account, String mucJid, String kickeeJid, String reason) {
        // Send a request to kick a user from a conference
        IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
        iq.setTo(mucJid);
        Element x = iq.query("x", "http://jabber.org/protocol/muc#admin");
        Element item = x.addChild("item");
        item.setAttribute("jid", kickeeJid);
        item.setAttribute("role", "none");
        if (!reason.isEmpty()) {
            Element reasonElement = item.addChild("reason");
            reasonElement.setContent(reason);
        }
        this.sendIqPacket(account, iq, null);
    }

    public void sendConferenceBan(Account account, String mucJid, String baneeJid, String reason) {
        // Send a request to ban a user from a conference
        IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
        iq.setTo(mucJid);
        Element x = iq.query("x", "http://jabber.org/protocol/muc#admin");
        Element item = x.addChild("item");
        item.setAttribute("jid", baneeJid);
        item.setAttribute("affiliation", "outcast");
        if (!reason.isEmpty()) {
            Element reasonElement = item.addChild("reason");
            reasonElement.setContent(reason);
        }
        this.sendIqPacket(account, iq, null);
    }

    public void sendConferenceVoice(Account account, String mucJid, String participantJid) {
        // Grant voice to a participant in a conference
        IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
        iq.setTo(mucJid);
        Element x = iq.query("x", "http://jabber.org/protocol/muc#admin");
        Element item = x.addChild("item");
        item.setAttribute("jid", participantJid);
        item.setAttribute("role", "participant");
        this.sendIqPacket(account, iq, null);
    }

    public void sendConferenceMute(Account account, String mucJid, String participantJid) {
        // Revoke voice from a participant in a conference
        IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
        iq.setTo(mucJid);
        Element x = iq.query("x", "http://jabber.org/protocol/muc#admin");
        Element item = x.addChild("item");
        item.setAttribute("jid", participantJid);
        item.setAttribute("role", "visitor");
        this.sendIqPacket(account, iq, null);
    }

    public void sendConferenceModerator(Account account, String mucJid, String participantJid) {
        // Grant moderator privileges to a participant in a conference
        IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
        iq.setTo(mucJid);
        Element x = iq.query("x", "http://jabber.org/protocol/muc#admin");
        Element item = x.addChild("item");
        item.setAttribute("jid", participantJid);
        item.setAttribute("role", "moderator");
        this.sendIqPacket(account, iq, null);
    }

    public void sendConferenceParticipant(Account account, String mucJid, String participantJid) {
        // Revoke moderator privileges from a participant in a conference
        IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
        iq.setTo(mucJid);
        Element x = iq.query("x", "http://jabber.org/protocol/muc#admin");
        Element item = x.addChild("item");
        item.setAttribute("jid", participantJid);
        item.setAttribute("role", "participant");
        this.sendIqPacket(account, iq, null);
    }

    public void sendConferenceOwner(Account account, String mucJid, String ownerJid) {
        // Grant ownership of a conference to a user
        IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
        iq.setTo(mucJid);
        Element x = iq.query("x", "http://jabber.org/protocol/muc#admin");
        Element item = x.addChild("item");
        item.setAttribute("jid", ownerJid);
        item.setAttribute("affiliation", "owner");
        this.sendIqPacket(account, iq, null);
    }

    public void sendConferenceAdmin(Account account, String mucJid, String adminJid) {
        // Grant administrative privileges to a user in a conference
        IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
        iq.setTo(mucJid);
        Element x = iq.query("x", "http://jabber.org/protocol/muc#admin");
        Element item = x.addChild("item");
        item.setAttribute("jid", adminJid);
        item.setAttribute("affiliation", "admin");
        this.sendIqPacket(account, iq, null);
    }

    public void sendConferenceMember(Account account, String mucJid, String memberJid) {
        // Grant membership in a conference to a user
        IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
        iq.setTo(mucJid);
        Element x = iq.query("x", "http://jabber.org/protocol/muc#admin");
        Element item = x.addChild("item");
        item.setAttribute("jid", memberJid);
        item.setAttribute("affiliation", "member");
        this.sendIqPacket(account, iq, null);
    }

    public void sendConferenceOutcast(Account account, String mucJid, String outcastJid, String reason) {
        // Ban a user from a conference
        IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
        iq.setTo(mucJid);
        Element x = iq.query("x", "http://jabber.org/protocol/muc#admin");
        Element item = x.addChild("item");
        item.setAttribute("jid", outcastJid);
        item.setAttribute("affiliation", "outcast");
        if (!reason.isEmpty()) {
            Element reasonElement = item.addChild("reason");
            reasonElement.setContent(reason);
        }
        this.sendIqPacket(account, iq, null);
    }

    public void sendConferenceRole(Account account, String mucJid, String participantJid, String role) {
        // Set the role of a participant in a conference
        IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
        iq.setTo(mucJid);
        Element x = iq.query("x", "http://jabber.org/protocol/muc#admin");
        Element item = x.addChild("item");
        item.setAttribute("jid", participantJid);
        item.setAttribute("role", role);
        this.sendIqPacket(account, iq, null);
    }

    public void sendConferenceAffiliation(Account account, String mucJid, String jid, String affiliation) {
        // Set the affiliation of a user in a conference
        IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
        iq.setTo(mucJid);
        Element x = iq.query("x", "http://jabber.org/protocol/muc#admin");
        Element item = x.addChild("item");
        item.setAttribute("jid", jid);
        item.setAttribute("affiliation", affiliation);
        this.sendIqPacket(account, iq, null);
    }

    public void sendConferenceNickname(Account account, String mucJid, String nickname) {
        // Set a nickname for the user in a conference
        PresencePacket packet = mPresenceGenerator.createAvailablePresence(account);
        packet.setTo(mucJid + "/" + nickname);
        this.sendPresencePacket(account, packet);
    }

    public void sendConferenceStatus(Account account, String mucJid, String statusText) {
        // Send a status message to a conference
        MessagePacket packet = new MessagePacket();
        packet.setTo(mucJid);
        packet.setType(MessagePacket.TYPE_GROUPCHAT);
        Element body = packet.addChild("body");
        body.setContent(statusText);
        this.sendMessagePacket(account, packet);
    }

    public void sendConferenceSubject(Account account, String mucJid, String subject) {
        // Set the subject of a conference
        MessagePacket packet = new MessagePacket();
        packet.setTo(mucJid);
        packet.setType(MessagePacket.TYPE_GROUPCHAT);
        Element subjectElement = packet.addChild("subject");
        subjectElement.setContent(subject);
        this.sendMessagePacket(account, packet);
    }

    public void sendConferenceConfiguration(Account account, String mucJid) {
        // Request the configuration of a conference
        IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
        iq.setTo(mucJid);
        Element query = iq.query("x", "http://jabber.org/protocol/muc#owner");
        this.sendIqPacket(account, iq, null);
    }

    public void sendConferenceConfiguration(Account account, String mucJid, Map<String, String> configuration) {
        // Send the configuration for a conference
        IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
        iq.setTo(mucJid);
        Element query = iq.query("x", "http://jabber.org/protocol/muc#owner");
        Element x = new Element("x", "jabber:x:data");
        x.setAttribute("type", "submit");
        query.addChild(x);
        for (Map.Entry<String, String> entry : configuration.entrySet()) {
            Element field = new Element("field");
            field.setAttribute("var", entry.getKey());
            if (!entry.getValue().isEmpty()) {
                Element value = new Element("value");
                value.setContent(entry.getValue());
                field.addChild(value);
            }
            x.addChild(field);
        }
        this.sendIqPacket(account, iq, null);
    }

    public void sendConferenceDestroy(Account account, String mucJid, String alternativeJid, String reason) {
        // Destroy a conference
        IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
        iq.setTo(mucJid);
        Element query = iq.query("x", "http://jabber.org/protocol/muc#owner");
        Element destroy = new Element("destroy");
        if (!alternativeJid.isEmpty()) {
            destroy.setAttribute("jid", alternativeJid);
        }
        if (!reason.isEmpty()) {
            Element reasonElement = new Element("reason");
            reasonElement.setContent(reason);
            destroy.addChild(reasonElement);
        }
        query.addChild(destroy);
        this.sendIqPacket(account, iq, null);
    }

    public void sendConferenceAffiliations(Account account, String mucJid) {
        // Request the affiliations of users in a conference
        IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
        iq.setTo(mucJid);
        Element query = iq.query("x", "http://jabber.org/protocol/muc#admin");
        this.sendIqPacket(account, iq, null);
    }

    public void sendConferenceItems(Account account, String mucJid) {
        // Request the items in a conference
        IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
        iq.setTo(mucJid);
        Element query = iq.query("query", "http://jabber.org/protocol/disco#items");
        this.sendIqPacket(account, iq, null);
    }

    public void sendConferenceInfo(Account account, String mucJid) {
        // Request information about a conference
        IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
        iq.setTo(mucJid);
        Element query = iq.query("query", "http://jabber.org/protocol/disco#info");
        this.sendIqPacket(account, iq, null);
    }

    public void sendConferenceDirectInvite(Account account, String toJid, String mucJid) {
        // Send a direct invitation to join a conference
        MessagePacket packet = new MessagePacket();
        packet.setTo(toJid);
        Element x = packet.addChild("x", "jabber:x:conference");
        x.setAttribute("jid", mucJid);
        this.sendMessagePacket(account, packet);
    }

    public void sendConferenceHistory(Account account, String mucJid) {
        // Request the history of a conference
        IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
        iq.setTo(mucJid);
        Element query = iq.query("history", "http://jabber.org/protocol/muc");
        this.sendIqPacket(account, iq, null);
    }

    public void sendConferenceHistory(Account account, String mucJid, int maxChars) {
        // Request a specific amount of history from a conference
        IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
        iq.setTo(mucJid);
        Element query = iq.query("history", "http://jabber.org/protocol/muc");
        query.setAttribute("maxchars", String.valueOf(maxChars));
        this.sendIqPacket(account, iq, null);
    }

    public void sendConferenceHistory(Account account, String mucJid, int maxStanzas) {
        // Request a specific number of stanzas from the history of a conference
        IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
        iq.setTo(mucJid);
        Element query = iq.query("history", "http://jabber.org/protocol/muc");
        query.setAttribute("maxstanzas", String.valueOf(maxStanzas));
        this.sendIqPacket(account, iq, null);
    }

    public void sendConferenceHistory(Account account, String mucJid, long since) {
        // Request history from a conference starting from a specific timestamp
        IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
        iq.setTo(mucJid);
        Element query = iq.query("history", "http://jabber.org/protocol/muc");
        query.setAttribute("since", XmlDateTimeFormat.format(new Date(since)));
        this.sendIqPacket(account, iq, null);
    }

    public void sendConferenceHistory(Account account, String mucJid, String with) {
        // Request history from a conference involving a specific user
        IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
        iq.setTo(mucJid);
        Element query = iq.query("history", "http://jabber.org/protocol/muc");
        query.setAttribute("with", with);
        this.sendIqPacket(account, iq, null);
    }

    public void sendConferenceHistory(Account account, String mucJid, int maxChars, int maxStanzas, long since, String with) {
        // Request history from a conference with specific parameters
        IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
        iq.setTo(mucJid);
        Element query = iq.query("history", "http://jabber.org/protocol/muc");
        if (maxChars > 0) {
            query.setAttribute("maxchars", String.valueOf(maxChars));
        }
        if (maxStanzas > 0) {
            query.setAttribute("maxstanzas", String.valueOf(maxStanzas));
        }
        if (since > 0) {
            query.setAttribute("since", XmlDateTimeFormat.format(new Date(since)));
        }
        if (with != null && !with.isEmpty()) {
            query.setAttribute("with", with);
        }
        this.sendIqPacket(account, iq, null);
    }

    public void sendConferenceQuery(Account account, String mucJid) {
        // Send a general query to a conference
        IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
        iq.setTo(mucJid);
        Element query = iq.query("query", "http://jabber.org/protocol/muc#owner");
        this.sendIqPacket(account, iq, null);
    }

    public void sendConferenceQuery(Account account, String mucJid, String node) {
        // Send a query to a conference with a specific node
        IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
        iq.setTo(mucJid);
        Element query = iq.query("query", "http://jabber.org/protocol/disco#items");
        query.setAttribute("node", node);
        this.sendIqPacket(account, iq, null);
    }

    public void sendConferenceQuery(Account account, String mucJid, String node, int maxItems) {
        // Send a query to a conference with specific parameters
        IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
        iq.setTo(mucJid);
        Element query = iq.query("query", "http://jabber.org/protocol/disco#items");
        query.setAttribute("node", node);
        if (maxItems > 0) {
            query.setAttribute("max_items", String.valueOf(maxItems));
        }
        this.sendIqPacket(account, iq, null);
    }

    public void sendConferenceQuery(Account account, String mucJid, String node, int maxItems, int startIndex) {
        // Send a query to a conference with specific parameters
        IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
        iq.setTo(mucJid);
        Element query = iq.query("query", "http://jabber.org/protocol/disco#items");
        query.setAttribute("node", node);
        if (maxItems > 0) {
            query.setAttribute("max_items", String.valueOf(maxItems));
        }
        if (startIndex >= 0) {
            query.setAttribute("start_index", String.valueOf(startIndex));
        }
        this.sendIqPacket(account, iq, null);
    }

    public void sendConferenceQuery(Account account, String mucJid, String node, int maxItems, int startIndex, String jid) {
        // Send a query to a conference with specific parameters
        IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
        iq.setTo(mucJid);
        Element query = iq.query("query", "http://jabber.org/protocol/disco#items");
        query.setAttribute("node", node);
        if (maxItems > 0) {
            query.setAttribute("max_items", String.valueOf(maxItems));
        }
        if (startIndex >= 0) {
            query.setAttribute("start_index", String.valueOf(startIndex));
        }
        if (jid != null && !jid.isEmpty()) {
            query.setAttribute("jid", jid);
        }
        this.sendIqPacket(account, iq, null);
    }

    public void sendConferenceQuery(Account account, String mucJid, String node, int maxItems, int startIndex, String jid, String subscription) {
        // Send a query to a conference with specific parameters
        IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
        iq.setTo(mucJid);
        Element query = iq.query("query", "http://jabber.org/protocol/disco#items");
        query.setAttribute("node", node);
        if (maxItems > 0) {
            query.setAttribute("max_items", String.valueOf(maxItems));
        }
        if (startIndex >= 0) {
            query.setAttribute("start_index", String.valueOf(startIndex));
        }
        if (jid != null && !jid.isEmpty()) {
            query.setAttribute("jid", jid);
        }
        if (subscription != null && !subscription.isEmpty()) {
            query.setAttribute("subscription", subscription);
        }
        this.sendIqPacket(account, iq, null);
    }

    public void sendConferenceQuery(Account account, String mucJid, String node, int maxItems, int startIndex, String jid, String subscription, String name) {
        // Send a query to a conference with specific parameters
        IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
        iq.setTo(mucJid);
        Element query = iq.query("query", "http://jabber.org/protocol/disco#items");
        query.setAttribute("node", node);
        if (maxItems > 0) {
            query.setAttribute("max_items", String.valueOf(maxItems));
        }
        if (startIndex >= 0) {
            query.setAttribute("start_index", String.valueOf(startIndex));
        }
        if (jid != null && !jid.isEmpty()) {
            query.setAttribute("jid", jid);
        }
        if (subscription != null && !subscription.isEmpty()) {
            query.setAttribute("subscription", subscription);
        }
        if (name != null && !name.isEmpty()) {
            query.setAttribute("name", name);
        }
        this.sendIqPacket(account, iq, null);
    }

    public void sendConferenceQuery(Account account, String mucJid, String node, int maxItems, int startIndex, String jid, String subscription, String name, String type) {
        // Send a query to a conference with specific parameters
        IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
        iq.setTo(mucJid);
        Element query = iq.query("query", "http://jabber.org/protocol/disco#items");
        query.setAttribute("node", node);
        if (maxItems > 0) {
            query.setAttribute("max_items", String.valueOf(maxItems));
        }
        if (startIndex >= 0) {
            query.setAttribute("start_index", String.valueOf(startIndex));
        }
        if (jid != null && !jid.isEmpty()) {
            query.setAttribute("jid", jid);
        }
        if (subscription != null && !subscription.isEmpty()) {
            query.setAttribute("subscription", subscription);
        }
        if (name != null && !name.isEmpty()) {
            query.setAttribute("name", name);
        }
        if (type != null && !type.isEmpty()) {
            query.setAttribute("type", type);
        }
        this.sendIqPacket(account, iq, null);
    }

    public void sendConferenceQuery(Account account, String mucJid, String node, int maxItems, int startIndex, String jid, String subscription, String name, String type, String action) {
        // Send a query to a conference with specific parameters
        IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
        iq.setTo(mucJid);
        Element query = iq.query("query", "http://jabber.org/protocol/disco#items");
        query.setAttribute("node", node);
        if (maxItems > 0) {
            query.setAttribute("max_items", String.valueOf(maxItems));
        }
        if (startIndex >= 0) {
            query.setAttribute("start_index", String.valueOf(startIndex));
        }
        if (jid != null && !jid.isEmpty()) {
            query.setAttribute("jid", jid);
        }
        if (subscription != null && !subscription.isEmpty()) {
            query.setAttribute("subscription", subscription);
        }
        if (name != null && !name.isEmpty()) {
            query.setAttribute("name", name);
        }
        if (type != null && !type.isEmpty()) {
            query.setAttribute("type", type);
        }
        if (action != null && !action.isEmpty()) {
            query.setAttribute("action", action);
        }
        this.sendIqPacket(account, iq, null);
    }

    public void sendConferenceQuery(Account account, String mucJid, String node, int maxItems, int startIndex, String jid, String subscription, String name, String type, String action, String reason) {
        // Send a query to a conference with specific parameters
        IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
        iq.setTo(mucJid);
        Element query = iq.query("query", "http://jabber.org/protocol/disco#items");
        query.setAttribute("node", node);
        if (maxItems > 0) {
            query.setAttribute("max_items", String.valueOf(maxItems));
        }
        if (startIndex >= 0) {
            query.setAttribute("start_index", String.valueOf(startIndex));
        }
        if (jid != null && !jid.isEmpty()) {
            query.setAttribute("jid", jid);
        }
        if (subscription != null && !subscription.isEmpty()) {
            query.setAttribute("subscription", subscription);
        }
        if (name != null && !name.isEmpty()) {
            query.setAttribute("name", name);
        }
        if (type != null && !type.isEmpty()) {
            query.setAttribute("type", type);
        }
        if (action != null && !action.isEmpty()) {
            query.setAttribute("action", action);
        }
        if (reason != null && !reason.isEmpty()) {
            Element reasonElement = new DefaultElement("reason");
            reasonElement.setText(reason);
            query.addChildElement(reasonElement);
        }
        this.sendIqPacket(account, iq, null);
    }

    private void sendIqPacket(Account account, IqPacket iqPacket, OnIqPacketReceived onIqPacketReceived) {
        XMPPConnectionService xmppConnectionService = ((MainActivity) getContext()).getXmppConnectionService();
        if (xmppConnectionService != null && account != null) {
            AccountJid jid = account.getJid().asBareJid();
            xmppConnectionService.sendIqPacket(jid, iqPacket, onIqPacketReceived);
        }
    }

    private Context getContext() {
        return this;
    }
}