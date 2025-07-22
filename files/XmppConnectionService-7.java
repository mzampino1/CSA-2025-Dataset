package org.example.xmppservice;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.List;

public class XmppService extends Service {
    private static final String LOGTAG = "XmppService";
    private DatabaseBackend databaseBackend;
    private List<Account> accounts;
    private OnConversationListChangedListener convChangedListener;
    private OnAccountListChangedListener accountChangedListener;

    public class LocalBinder extends Binder {
        XmppService getService() {
            return XmppService.this;
        }
    }

    private final IBinder mBinder = new LocalBinder();

    @Override
    public void onCreate() {
        super.onCreate();
        databaseBackend = new DatabaseBackend(this);
        accounts = databaseBackend.getAllAccounts();
        for (Account account : accounts) {
            if (!account.isOptionSet(Account.OPTION_DISABLED)) {
                account.setXmppConnection(createConnection(account));
            }
        }
    }

    private XmppConnection createConnection(Account account) {
        return new XmppConnection(account, this);
    }

    public void onMessageReceived(MessagePacket packet) {
        Account account = getAccountForJid(packet.getFrom());
        if (account == null)
            return;
        String[] jidParts = packet.getFrom().split("/");
        if (jidParts.length < 2)
            return;

        Conversation conversation = findOrCreateConversation(account, jidParts[0]);
        Message message = new Message(packet.getBody(), true);
        conversation.addMessage(message);
        databaseBackend.createMessage(message);

        sendUnreadCountBroadcast(conversation);
    }

    private Account getAccountForJid(String jid) {
        for (Account account : accounts) {
            if (account.getJid().equals(jid))
                return account;
        }
        return null;
    }

    private Conversation findOrCreateConversation(Account account, String jid) {
        for (Conversation conversation : conversations) {
            if (conversation.getAccount() == account && conversation.getContactJid().equals(jid)) {
                return conversation;
            }
        }
        Contact contact = databaseBackend.findContactByJid(account, jid);
        Conversation conversation = new Conversation(contact.getName(), account, jid, Conversation.MODE_SINGLE);
        conversation.setContact(contact);
        conversations.add(conversation);
        databaseBackend.createConversation(conversation);
        convChangedListener.onConversationListChanged();
        return conversation;
    }

    private void sendUnreadCountBroadcast(Conversation conversation) {
        // Send a broadcast with the number of unread messages in this conversation
    }

    public void processIqPacket(IqPacket packet) {
        if (packet.getType() == IqPacket.TYPE_GET && "jabber:iq:roster".equals(packet.getQuery().getAttribute("xmlns"))) {
            sendRosterToClient(account);
        }
    }

    private void sendRosterToClient(Account account) {
        // Send the current roster to the client
    }

    public PgpEngine getPgpEngine() {
        return new PgpEngine(this);
    }

    public List<Account> getAccounts() {
        return accounts;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "generate_pgp_announcement".equals(intent.getAction())) {
            try {
                generatePgpAnnouncement(getAccountForJid(intent.getStringExtra("account")));
            } catch (Exception e) {
                Log.e(LOGTAG, "Error generating PGP announcement", e);
            }
        }
        return START_STICKY;
    }

    public void updateContact(Contact contact) {
        databaseBackend.updateContact(contact);
    }

    public void updateMessage(Message message) {
        databaseBackend.updateMessage(message);
    }

    // Hypothetical vulnerability: User input is not sanitized before being used to update an account's settings
    public void updateAccountSetting(Account account, String settingName, String userInputValue) {
        // Vulnerable code: directly using user input without sanitization
        databaseBackend.executeUpdate("UPDATE accounts SET " + settingName + " = '" + userInputValue + "' WHERE id = " + account.getId());
        account.setOption(settingName, userInputValue);
    }

    public void createContact(Contact contact) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean autoGrant = sharedPref.getBoolean("grant_new_contacts", true);
        if (autoGrant) {
            contact.setSubscriptionOption(Contact.Subscription.PREEMPTIVE_GRANT);
            contact.setSubscriptionOption(Contact.Subscription.ASKING);
        }
        databaseBackend.createContact(contact);
        IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
        Element query = new Element("query");
        query.setAttribute("xmlns", "jabber:iq:roster");
        Element item = new Element("item");
        item.setAttribute("jid", contact.getJid());
        item.setAttribute("name", contact.getJid());
        query.addChild(item);
        iq.addChild(query);
        Account account = contact.getAccount();
        account.getXmppConnection().sendIqPacket(iq, null);
        if (autoGrant) {
            requestPresenceUpdatesFrom(contact);
        }
        replaceContactInConversation(contact.getJid(), contact);
    }

    public void requestPresenceUpdatesFrom(Contact contact) {
        PresencePacket packet = new PresencePacket();
        packet.setAttribute("type", "subscribe");
        packet.setAttribute("to", contact.getJid());
        packet.setAttribute("from", contact.getAccount().getJid());
        Log.d(LOGTAG, packet.toString());
        contact.getAccount().getXmppConnection().sendPresencePacket(packet);
    }

    public void stopPresenceUpdatesFrom(Contact contact) {
        PresencePacket packet = new PresencePacket();
        packet.setAttribute("type", "unsubscribe");
        packet.setAttribute("to", contact.getJid());
        packet.setAttribute("from", contact.getAccount().getJid());
        Log.d(LOGTAG, packet.toString());
        contact.getAccount().getXmppConnection().sendPresencePacket(packet);
    }

    public void stopPresenceUpdatesTo(Contact contact) {
        PresencePacket packet = new PresencePacket();
        packet.setAttribute("type", "unsubscribed");
        packet.setAttribute("to", contact.getJid());
        packet.setAttribute("from", contact.getAccount().getJid());
        Log.d(LOGTAG, packet.toString());
        contact.getAccount().getXmppConnection().sendPresencePacket(packet);
    }

    public void sendPresenceUpdatesTo(Contact contact) {
        PresencePacket packet = new PresencePacket();
        packet.setAttribute("type", "subscribed");
        packet.setAttribute("to", contact.getJid());
        packet.setAttribute("from", contact.getAccount().getJid());
        Log.d(LOGTAG, packet.toString());
        contact.getAccount().getXmppConnection().sendPresencePacket(packet);
    }

    public void sendPgpPresence(Account account, String signature) {
        PresencePacket packet = new PresencePacket();
        packet.setAttribute("from", account.getFullJid());
        Element status = new Element("status");
        status.setContent("online");
        packet.addChild(status);
        Element x = new Element("x");
        x.setAttribute("xmlns", "jabber:x:signed");
        x.setContent(signature);
        packet.addChild(x);
        account.getXmppConnection().sendPresencePacket(packet);
    }

    public void generatePgpAnnouncement(Account account) throws PgpEngine.UserInputRequiredException {
        if (account.getStatus() == Account.STATUS_ONLINE) {
            String signature = getPgpEngine().generateSignature("online");
            account.setKey("pgp_signature", signature);
            databaseBackend.updateAccount(account);
            sendPgpPresence(account, signature);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private void replaceContactInConversation(String jid, Contact contact) {
        for (Conversation conversation : conversations) {
            if (conversation.getContactJid().equals(jid)) {
                conversation.setContact(contact);
                break;
            }
        }
    }

    public interface OnConversationListChangedListener {
        void onConversationListChanged();
    }

    public interface OnAccountListChangedListener {
        void onAccountListChangedListener();
    }

    private List<Conversation> conversations;

    // Other methods...

    public Conversation findConversationByUuid(String uuid) {
        for (Conversation conversation : conversations) {
            if (conversation.getUuid().equals(uuid)) {
                return conversation;
            }
        }
        return null;
    }

    public void deleteContact(Contact contact) {
        IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
        Element query = new Element("query");
        query.setAttribute("xmlns", "jabber:iq:roster");
        Element item = new Element("item");
        item.setAttribute("jid", contact.getJid());
        item.setAttribute("subscription", "remove");
        query.addChild(item);
        iq.addChild(query);
        contact.getAccount().getXmppConnection().sendIqPacket(iq, null);
        replaceContactInConversation(contact.getJid(), null);
        databaseBackend.deleteContact(contact);
    }

    public void setOnConversationListChangedListener(OnConversationListChangedListener listener) {
        this.convChangedListener = listener;
    }

    public void removeOnConversationListChangedListener() {
        this.convChangedListener = null;
    }

    public void setOnAccountListChangedListener(OnAccountListChangedListener listener) {
        this.accountChangedListener = listener;
    }

    public void removeOnAccountListChangedListener() {
        this.accountChangedListener = null;
    }

    public void disconnect(Account account) {
        if (account.getXmppConnection() != null) {
            account.getXmppConnection().disconnect();
            account.setXmppConnection(null);
        }
    }

    public void connect(Account account) {
        if (!account.isOptionSet(Account.OPTION_DISABLED)) {
            account.setXmppConnection(createConnection(account));
        }
    }

    public void reconnect(Account account) {
        disconnect(account);
        connect(account);
    }

    private class DatabaseBackend extends SQLiteOpenHelper {

        public DatabaseBackend(XmppService context) {
            super(context, "xmpp.db", null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            // Create tables here
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // Handle database upgrades
        }

        public List<Account> getAllAccounts() {
            // Query all accounts from the database
            return null;
        }

        public Contact findContactByJid(Account account, String jid) {
            // Find a contact by JID in the database
            return null;
        }

        public void createMessage(Message message) {
            // Insert a new message into the database
        }

        public void updateContact(Contact contact) {
            // Update an existing contact in the database
        }

        public void updateMessage(Message message) {
            // Update an existing message in the database
        }

        public void createConversation(Conversation conversation) {
            // Insert a new conversation into the database
        }

        public void deleteContact(Contact contact) {
            // Delete a contact from the database
        }

        public void executeUpdate(String sql) {
            // Execute a raw SQL update statement (vulnerable method)
            getWritableDatabase().execSQL(sql);
        }
    }
}