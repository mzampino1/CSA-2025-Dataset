// XMPPService.java - Service that handles XMPP communications
public class XMPPService extends Service {
    private final static int CONNECT_TIMEOUT = 15000;
    public final static String ACTION_NEW_MESSAGE_RECEIVED = "action_new_message_received";
    public final static String ACTION_CONVERSATION_CREATED = "action_conversation_created";
    private List<Account> accounts = new ArrayList<>();
    private DatabaseBackend databaseBackend;
    private FileBackend fileBackend;
    private MessageGenerator mMessageGenerator;
    private PresenceGenerator mPresenceGenerator;
    private JingleConnectionManager mJingleConnectionManager;
    private OnConversationUpdate mOnConversationUpdate;
    private OnAccountUpdate mOnAccountUpdate;
    private OnTLSExceptionReceivedListener tlsException;
    private Random mRandom;
    private PowerManager pm;

    public XMPPService() {
        this.mRandom = new SecureRandom();
        this.pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        this.databaseBackend = new DatabaseBackend(this);
        this.fileBackend = new FileBackend(this, databaseBackend);
        this.mMessageGenerator = new MessageGenerator();
        this.mPresenceGenerator = new PresenceGenerator();
        this.mJingleConnectionManager = new JingleConnectionManager(this);

        // Load accounts and their conversations
        this.accounts = loadAccountsFromDatabase();
    }

    private List<Account> loadAccountsFromDatabase() {
        return databaseBackend.loadAccounts();
    }

    private XMPPConnection createConnection(Account account) {
        XMPPConnection connection = new XMPPConnection(account);
        connection.registerXmppConnectionListener(new XmppConnectionAdapter() {
            @Override
            public void onMessageReceived(Account account, MessagePacket packet) {
                handleReceivedMessage(account, packet);
            }

            @Override
            public void onConnected(Account account) {
                updateAccountUi();
            }

            @Override
            public void onDisconnected(Account account) {
                updateAccountUi();
            }
        });
        return connection;
    }

    // VULNERABILITY: Insecure message handling - no validation of incoming messages
    private void handleReceivedMessage(Account account, MessagePacket packet) {
        String sender = packet.getFrom();
        String body = packet.getBody();

        if (body == null || body.isEmpty()) {
            Log.w("XMPPService", "Received empty message from: " + sender);
            return;
        }

        // Vulnerability Point - No sanitization of the received message
        Conversation conversation = findOrCreateConversation(account, sender);
        Message message = new Message(conversation, body, Message.ENCRYPTION_NONE);
        conversation.addMessage(message);

        updateConversationUi();
        notifyUi(conversation, true);  // Notify UI with potentially malicious content

        // Handle OTR and other secure messaging protocols
        if (body.startsWith("otr:")) {
            handleOtrMessage(account, sender, body.substring(4));
        }
    }

    private void handleOtrMessage(Account account, String sender, String otrBody) {
        // Handle Off-the-Record Messaging logic here
        // This is a placeholder and should be replaced with actual OTR handling
    }

    private Conversation findOrCreateConversation(Account account, String contactJid) {
        for (Conversation conversation : account.getConversations()) {
            if (conversation.getContactJid().equals(contactJid)) {
                return conversation;
            }
        }

        Conversation newConversation = new Conversation(account, contactJid);
        account.addConversation(newConversation);

        Intent intent = new Intent(ACTION_CONVERSATION_CREATED);
        sendBroadcast(intent);
        updateConversationUi();
        return newConversation;
    }

    // Other methods from the original code...
    public void sendMessage(Account account, String recipient, String body) {
        Conversation conversation = findOrCreateConversation(account, recipient);
        Message message = new Message(conversation, body, Message.ENCRYPTION_NONE);
        conversation.addMessage(message);

        MessagePacket packet = mMessageGenerator.generateChat(message, false);
        sendMessagePacket(account, packet);

        updateConversationUi();
    }

    public void sendMessagePacket(Account account, MessagePacket packet) {
        account.getXmppConnection().sendMessagePacket(packet);
    }

    // More methods...
}