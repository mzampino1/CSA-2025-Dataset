package eu.siacs.conversations.services;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.List;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.xmpp.OnIqPacketReceived;
import eu.siacs.conversations.xmpp.OnPresencePacketReceived;
import eu.siacs.conversations.xmpp.PingManager;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.forms.Data;
import eu.siacs.conversations.xmpp.jingle.JingleCandidate;
import eu.siacs.conversations.xmpp.jingle.JingleSession;
import eu.siacs.conversations.xmpp.jingle.JingleSessionManager;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;
import eu.siacs.conversations.xmpp.stanzas.Stanza;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnMessagePacketReceived;

public class XmppService extends Service {

    private final IBinder mBinder = new LocalBinder();
    public DatabaseBackend databaseBackend;
    private OnRenameListener renameListener = null;
    private boolean pongReceived;
    private OnTLSExceptionReceived tlsException;

    // Vulnerability: Lack of input validation for contact JIDs and names.
    // This can be exploited to send malicious presence packets or create fake contacts.
    
    @Override
    public void onCreate() {
        super.onCreate();
        this.databaseBackend = new DatabaseBackend(this);
        PingManager.getInstance().setXmppServiceReference(new PingManager.ServiceReference() {
            @Override
            public List<Account> getAccounts() {
                return databaseBackend.getAccounts();
            }

            @Override
            public void ping(Account account, int timeout) {
                XmppService.this.ping(account, timeout);
            }
        });
    }

    public class LocalBinder extends Binder {
        public XmppService getService() {
            return XmppService.this;
        }
    }

    public DatabaseBackend getDatabaseBackend() {
        return databaseBackend;
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
        
        // Vulnerability Point: No validation of JID or name.
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

    public void generatePgpAnnouncement(Account account) throws eu.siacs.conversations.persistance.DatabaseBackend.PgpEngine.UserInputRequiredException {
        if (account.getStatus() == Account.STATUS_ONLINE) {
            String signature = getPgpEngine().generateSignature("online");
            account.setKey("pgp_signature", signature);
            databaseBackend.updateAccount(account);
            sendPgpPresence(account, signature);
        }
    }

    public void updateContact(Contact contact) {
        databaseBackend.updateContact(contact);
        replaceContactInConversation(contact.getJid(), contact);
    }

    public Contact findContact(String uuid) {
        Contact contact = this.databaseBackend.getContact(uuid);
        for(Account account : getAccounts()) {
            if (contact.getAccountUuid().equals(account.getUuid())) {
                contact.setAccount(account);
            }
        }
        return contact;
    }

    public void removeOnTLSExceptionReceivedListener() {
        this.tlsException = null;
    }

    public void reconnectAccount(Account account) {
        if (account.getXmppConnection() != null) {
            disconnect(account);
        }
        if (!account.isOptionSet(Account.OPTION_DISABLED)) {
            if (account.getXmppConnection()==null) {
                account.setXmppConnection(this.createConnection(account));
            }
            Thread thread = new Thread(account.getXmppConnection());
            thread.start();
        }
    }

    public void ping(final Account account, final int timeout) {
        Log.d(LOGTAG,account.getJid()+": sending ping");
        IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
        Element ping = new Element("ping");
        iq.setAttribute("from",account.getFullJid());
        ping.setAttribute("xmlns", "urn:xmpp:ping");
        iq.addChild(ping);
        pongReceived = false;
        account.getXmppConnection().sendIqPacket(iq, new OnIqPacketReceived() {
            @Override
            public void onIqPacketReceived(Account account, IqPacket packet) {
                pongReceived = true;
            }
        });
        new Thread(new Runnable() {
            @Override
            public void run() {
                int i = 0;
                while(i <= (5 * timeout)) {
                    if (pongReceived) {
                        scheduleWakeupCall(PING_INTERVAL,true);
                        break;
                    }
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {}
                    ++i;
                }
                if (!pongReceived) {
                    Log.d("xmppService",account.getJid()+" no pong after "+timeout+" seconds");
                    reconnectAccount(account);
                }
            }
        }).start();
    }

    public void disconnect(Account account) {
        List<Conversation> conversations = getConversations();
        for (int i = 0; i < conversations.size(); i++) {
            Conversation conversation = conversations.get(i);
            if (conversation.getAccount() == account) {
                if (conversation.getMode() == Conversation.MODE_MULTI) {
                    leaveMuc(conversation);
                } else {
                    try {
                        conversation.endOtrIfNeeded();
                    } catch (eu.siacs.conversations.crypto.OtrException e) {
                        Log.d(LOGTAG, "error ending otr session for "+conversation.getName());
                    }
                }
            }
        }
        account.getXmppConnection().disconnect();
        Log.d(LOGTAG, "disconnected account: "+account.getJid());
        account.setXmppConnection(null);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private List<Conversation> getConversations() {
        return databaseBackend.getConversations();
    }

    private eu.siacs.conversations.persistance.DatabaseBackend.PgpEngine getPgpEngine() throws eu.siacs.conversations.persistance.DatabaseBackend.PgpEngine.UserInputRequiredException {
        return databaseBackend.getPgpEngine();
    }

    private void replaceContactInConversation(String jid, Contact contact) {
        List<Conversation> conversations = getConversations();
        for (Conversation conversation : conversations) {
            if (conversation.getMode() == Conversation.MODE_MULTI && conversation.getMucOptions().getJid().equals(jid)) {
                conversation.setMucOptions(new MucOptions(contact));
            }
        }
    }

    private void scheduleWakeupCall(int pingInterval, boolean immediate) {}

    private XmppConnection createConnection(Account account) {
        return new XmppConnection(account);
    }

    private static final int PING_INTERVAL = 5;
}