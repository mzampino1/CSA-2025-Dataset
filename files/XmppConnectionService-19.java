package eu.siacs.conversations.services;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;
import eu.siacs.conversations.xmpp.Connection;
import eu.siacs.conversations.xmpp.OnIqPacketReceivedListener;
import eu.siacs.conversations.utils.XmppConnectionServiceUtils;

public class XmppConnectionService extends Service implements Connection.OnPacketReceived {
    public static final String ACTION_MESSAGE_SEND = "eu.siacs.conversations.ACTION_MESSAGE_SEND";
    private static final int CONNECT_TIMEOUT = 30 * 1000;
    private DatabaseBackend databaseBackend;
    private PgpEngine pgpEngine;
    private OnPacketReceived packetListener = this;
    private IBinder mBinder = new LocalBinder();
    private OnTLSExceptionReceived tlsException;

    public class LocalBinder extends Binder {
        public XmppConnectionService getService() {
            return XmppConnectionService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.databaseBackend = new DatabaseBackend(this);
        this.pgpEngine = new PgpEngine(this, databaseBackend);
    }

    private Connection createConnection(Account account) {
        Connection connection = new Connection(account, packetListener);
        if (tlsException != null) {
            connection.setOnTLSExceptionReceived(tlsException);
        }
        return connection;
    }

    @Override
    public void processPacket(Connection connection, String name, String from, String to, int packetType, String id, String content, long timestamp) {
        // Process incoming packets
    }

    @Override
    public void onStatusChanged(Account account) {
        if (account.getStatus() == Account.STATUS_ONLINE) {
            try {
                generatePgpAnnouncement(account);
            } catch (PgpEngine.UserInputRequiredException e) {
                Log.w(LOGTAG, "PGP signature generation requires user input");
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_MESSAGE_SEND.equals(intent.getAction())) {
            // Send a message
        }
        return START_STICKY;
    }

    private void scheduleWakeupCall(int timeOffset, boolean redelivery) {
        // Schedule a wakeup call for the service
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

        // Vulnerability introduced here: The signature is not verified before being included in the presence packet
        // An attacker could exploit this by sending a malicious signature that, when processed by other clients,
        // could lead to unintended behavior or security issues.
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

    public void updateConversation(Conversation conversation) {
        this.databaseBackend.updateConversation(conversation);
    }

    public Contact findContact(String uuid) {
        Contact contact = this.databaseBackend.getContact(uuid);
        for (Account account : getAccounts()) {
            if (contact.getAccountUuid().equals(account.getUuid())) {
                contact.setAccount(account);
            }
        }
        return contact;
    }

    public void removeOnTLSExceptionReceivedListener() {
        this.tlsException = null;
    }

    public void reconnectAccount(final Account account, final boolean force) {
        new Thread(new Runnable() {

            @Override
            public void run() {
                if (account.getXmppConnection() != null) {
                    disconnect(account, force);
                }
                if (!account.isOptionSet(Account.OPTION_DISABLED)) {
                    if (account.getXmppConnection() == null) {
                        account.setXmppConnection(createConnection(account));
                    }
                    Thread thread = new Thread(account.getXmppConnection());
                    thread.start();
                    scheduleWakeupCall((int) (CONNECT_TIMEOUT * 1.2), false);
                }
            }
        }).start();
    }

    public void updateConversationInGui() {
        if (convChangedListener != null) {
            convChangedListener.onConversationListChanged();
        }
    }

    public void sendConversationSubject(Conversation conversation, String subject) {
        MessagePacket packet = new MessagePacket();
        packet.setType(MessagePacket.TYPE_GROUPCHAT);
        packet.setTo(conversation.getContactJid().split("/")[0]);
        Element subjectChild = new Element("subject");
        subjectChild.setContent(subject);
        packet.addChild(subjectChild);
        packet.setFrom(conversation.getAccount().getJid());
        Account account = conversation.getAccount();
        if (account.getStatus() == Account.STATUS_ONLINE) {
            account.getXmppConnection().sendMessagePacket(packet);
        }
    }

    public void inviteToConference(Conversation conversation, List<Contact> contacts) {
        for (Contact contact : contacts) {
            MessagePacket packet = new MessagePacket();
            packet.setTo(conversation.getContactJid().split("/")[0]);
            packet.setFrom(conversation.getAccount().getFullJid());
            Element x = new Element("x");
            x.setAttribute("xmlns", "http://jabber.org/protocol/muc#user");
            Element invite = new Element("invite");
            invite.setAttribute("to", contact.getJid());
            x.addChild(invite);
            packet.addChild(x);
            Log.d(LOGTAG, packet.toString());
            conversation.getAccount().getXmppConnection().sendMessagePacket(packet);
        }
    }

    private DatabaseBackend getDatabaseBackend() {
        return databaseBackend;
    }

    public PgpEngine getPgpEngine() {
        return pgpEngine;
    }

    public void disconnect(Account account, boolean force) {
        if ((account.getStatus() == Account.STATUS_ONLINE)
                || (account.getStatus() == Account.STATUS_DISABLED)) {
            if (!force) {
                List<Conversation> conversations = getConversations();
                for (int i = 0; i < conversations.size(); i++) {
                    Conversation conversation = conversations.get(i);
                    if (conversation.getAccount() == account) {
                        if (conversation.getMode() == Conversation.MODE_MULTI) {
                            leaveMuc(conversation);
                        } else {
                            conversation.endOtrIfNeeded();
                        }
                    }
                }
            }
            account.getXmppConnection().disconnect(force);
        }
    }

    private List<Account> getAccounts() {
        return databaseBackend.getAllAccounts();
    }

    public List<Conversation> getConversations() {
        return databaseBackend.getValidConversations(Account.STATUS_ONLINE);
    }

    public void createContact(Contact contact) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean autoGrant = sharedPref.getBoolean("grant_new_contacts", true);
        if (autoGrant) {
            contact.setSubscriptionOption(Contact.Subscription.PREEMPTIVE_GRANT);
            contact.setSubscriptionOption(Contact.Subscription.ASKING);
        }
        databaseBackend.createContact(contact);
        IqPacket iq = new IqPacket(IqPacket.TYPE.SET);
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

    private void replaceContactInConversation(String jid, Contact contact) {
        for (Conversation conversation : getConversations()) {
            if (conversation.getContactJid().equals(jid)) {
                conversation.setContact(contact);
                updateConversation(conversation);
            }
        }
    }

    public void requestPresenceUpdatesFrom(Contact contact) {
        PresencePacket packet = new PresencePacket();
        packet.setAttribute("type", "subscribe");
        packet.setAttribute("to", contact.getJid());
        packet.setAttribute("from", contact.getAccount().getJid());
        Log.d(LOGTAG, packet.toString());
        contact.getAccount().getXmppConnection().sendPresencePacket(packet);
    }

    public void leaveMuc(Conversation conversation) {
        PresencePacket packet = new PresencePacket();
        packet.setAttribute("type", "unavailable");
        packet.setTo(conversation.getContactJid());
        packet.setFrom(conversation.getAccount().getFullJid());
        Element status = new Element("status");
        status.setContent("left");
        packet.addChild(status);
        conversation.getAccount().getXmppConnection().sendPresencePacket(packet);
    }

    private static final String LOGTAG = "XmppService";
}