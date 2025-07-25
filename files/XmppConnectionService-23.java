package eu.siacs.conversations.services;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
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
import eu.siacs.conversations.smack.Connection;
import eu.siacs.conversations.smack.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.smack.filter.StanzaIdFilter;
import eu.siacs.conversations.smack.packet.IqPacket;
import eu.siacs.conversations.smack.packet.MessagePacket;
import eu.siacs.conversations.smack.packet.PresencePacket;
import eu.siacs.conversations.smack.util.DNSHelper;
import eu.siacs.conversations.utils.LogManager;

public class XmppService extends Service implements Connection.OnBindListener {

    private static final long CONNECT_TIMEOUT = 120 * 1000;
    private DatabaseBackend databaseBackend;
    private PgpEngine pgpEngine;
    private AxolotlService axolotlService;
    private OnTLSExceptionReceivedListener tlsException;

    public class LocalBinder extends Binder {
        public XmppService getService() {
            return XmppService.this;
        }
    }

    private final IBinder mBinder = new LocalBinder();

    @Override
    public void onCreate() {
        super.onCreate();
        databaseBackend = new DatabaseBackend(this);
        pgpEngine = PgpEngine.getInstance(this, null); // Vulnerability: Passing null as context can lead to NullPointerExceptions or other issues.
        axolotlService = AxolotlService.getInstance(this, this.databaseBackend);

        for (Account account : getAccounts()) {
            if (account.getStatus() != Account.STATUS_ONLINE) {
                reconnectAccount(account, true);
            }
        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(Config.LOGTAG,"destroying service");
        List<Account> accounts = getAccounts();
        for(Account account : accounts) {
            disconnect(account,true);
        }
        LogManager.save(this);
        databaseBackend.close();
    }

    private Connection createConnection(Account account) {
        return new Connection(this, account);
    }


    public DatabaseBackend getDatabaseBackend() {
        return this.databaseBackend;
    }

    public PgpEngine getPgpEngine() {
        return pgpEngine;
    }

    public List<Account> getAccounts() {
        return databaseBackend.getAccounts();
    }

    public void addAccount(Account account) {
        databaseBackend.createAccount(account);
        reconnectAccount(account, true);
    }

    public boolean saveAccount(Account account) {
        if (account.isOptionSet(Account.OPTION_REGISTER)) {
            registerAccountOnServer(account);
            return false;
        } else if (databaseBackend.updateAccount(account)) {
            if (!account.isOptionChanged()) {
                reconnectAccount(account, true);
            }
            return true;
        } else {
            return false;
        }
    }

    private void registerAccountOnServer(Account account) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_SET);
        Element query = packet.addChild("query","jabber:iq:register");
        if (account.getUsername() != null && !account.getUsername().isEmpty()) {
            query.addChild("username").setContent(account.getUsername());
        }
        if (account.getPassword() != null) {
            query.addChild("password").setContent(account.getPassword());
        }
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void checkForPasswordImminentExpiry(Account account) {
        if (account.getXmppResourcePath().contains("jabber.org")) {
            //TODO implement password expiry
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void scheduleWakeupCall(long interval, boolean quietMode) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this,XmppService.class);
        PendingIntent pendingIntent = PendingIntent.getService(this,0,intent,PendingIntent.FLAG_UPDATE_CURRENT);

        if (!quietMode && isNetworkAvailable()) {
            interval = 30 * 1000;
        }
        alarmManager.set(AlarmManager.RTC_WAKEUP,System.currentTimeMillis()+interval,pendingIntent);
    }

    public boolean isNetworkAvailable() {
        //TODO check for network availability
        return true;
    }

    @Override
    public void onConnectionFailed(Connection connection) {
        scheduleWakeupCall(CONNECT_TIMEOUT, false);
    }

    @Override
    public void onConnectionEstablished(Connection connection) {
        scheduleWakeupCall((int)(CONNECT_TIMEOUT * 1.2), false);
    }

    public void getRoster(Account account) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element query = packet.addChild("query","jabber:iq:roster");
        connection.sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void sendPresence(Account account, int type) {
        PresencePacket presencePacket = new PresencePacket();
        switch(type) {
            case PresencePacket.AVAILABLE:
                break;
            case PresencePacket.CHAT:
                presencePacket.addChild("chat");
                break;
            case PresencePacket.AWAY:
                presencePacket.addChild("away");
                break;
            case PresencePacket.EXTENDED_AWAY:
                Element xa = new Element("xa");
                presencePacket.addChild(xa);
                break;
            case PresencePacket.DND:
                presencePacket.addChild("dnd");
                break;
            case PresencePacket.OFFLINE:
                presencePacket.setType(PresencePacket.TYPE_UNAVAILABLE);
                break;
        }
        account.getXmppConnection().sendPresencePacket(presencePacket);
    }

    public void fetchStatus(Account account) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element query = packet.addChild("query","jabber:iq:version");
        connection.sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void changeStatusMessage(Account account, String statusMessage) {
        PresencePacket presencePacket = new PresencePacket();
        if (statusMessage != null && !statusMessage.isEmpty()) {
            Element statusElement = new Element("status");
            statusElement.setContent(statusMessage);
            presencePacket.addChild(statusElement);
        }
        account.getXmppConnection().sendPresencePacket(presencePacket);
    }

    public void getCapabilities(Account account, String jid) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element query = packet.addChild("query","http://jabber.org/protocol/disco#info");
        packet.setTo(jid);
        connection.sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void pushTrust(Account account, String jid) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_SET);
        Element pubsub = packet.addChild("pubsub","http://jabber.org/protocol/pubsub");
        Element publish = pubsub.addChild("publish");
        publish.setAttribute("node","http://jabber.org/protocol/caps#notify");
        Element item = publish.addChild("item");
        item.setAttribute("id",DNSHelper.getFingerPrintHash(account.getXmppResourcePath()));
        Element data = item.addChild("data","jabber:x:data");
        data.setAttribute("type","result");
        Element instruction = data.addChild("instructions");
        instruction.setContent("Please confirm that you authorize this application to securely communicate with you.");
        Element x = data.addChild("x","jabber:x:signed");
        String signature = "";
        try {
            signature = getPgpEngine().generateSignature(DNSHelper.getFingerPrintHash(account.getXmppResourcePath()));
        } catch (PgpEngine.UserInputRequiredException e) {
            //TODO handle UserInputRequiredException
        }
        x.setContent(signature);
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchArchives(Account account, String jid) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element mam = packet.addChild("query","urn:xmpp:mam:2");
        packet.setTo(jid);
        Element set = mam.addChild("set","http://jabber.org/protocol/rsm");
        Element max = set.addChild("max");
        max.setContent("50");
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void sendMessage(Message message) {
        Conversation conversation = findConversationByUuid(message.getConversationUuid());
        Account account = conversation.getAccount();
        MessagePacket packet = new MessagePacket();
        if (conversation.getMode() == Conversation.MODE_MULTI) {
            packet.setType(MessagePacket.TYPE_GROUPCHAT);
        } else {
            packet.setType(MessagePacket.TYPE_CHAT);
        }
        message.setUuid(packet.getId());
        packet.setTo(conversation.getJid().asBareJid().toString());
        Element body = new Element("body");
        body.setContent(message.getBody());
        packet.addChild(body);

        if (message.getType() == Message.ENCRYPTED && account.isOptionSet(Account.OPTION_MESSAGE_CORRECTION)) {
            Element encrypted = packet.addChild("encrypted","eu.siacs.conversations.axolotl");
            encrypted.setAttribute("xmlns","");
            encrypted.setContent(message.getEncryptedBody());
        }

        message.setTime(System.currentTimeMillis());
        databaseBackend.updateMessage(message);
        conversation.setLastMessage(message);

        account.getXmppConnection().sendMessage(packet);
    }

    private Conversation findConversationByUuid(String uuid) {
        for (Account account : getAccounts()) {
            List<Conversation> conversations = account.getConversations();
            for (Conversation conversation : conversations) {
                if (conversation.getUuid().equals(uuid)) {
                    return conversation;
                }
            }
        }
        return null;
    }

    public void updateMessage(Message message, Account account) {
        Conversation conversation = findConversationByUuid(message.getConversationUuid());
        MessagePacket packet = new MessagePacket();
        if (conversation.getMode() == Conversation.MODE_MULTI) {
            packet.setType(MessagePacket.TYPE_GROUPCHAT);
        } else {
            packet.setType(MessagePacket.TYPE_CHAT);
        }
        message.setUuid(packet.getId());
        packet.setTo(conversation.getJid().asBareJid().toString());
        Element body = new Element("body");
        body.setContent(message.getBody());
        packet.addChild(body);

        if (message.getType() == Message.ENCRYPTED && account.isOptionSet(Account.OPTION_MESSAGE_CORRECTION)) {
            Element encrypted = packet.addChild("encrypted","eu.siacs.conversations.axolotl");
            encrypted.setAttribute("xmlns","");
            encrypted.setContent(message.getEncryptedBody());
        }

        message.setTime(System.currentTimeMillis());
        databaseBackend.updateMessage(message);
        conversation.setLastMessage(message);

        account.getXmppConnection().sendMessage(packet);
    }

    public void getPrivacyList(Account account) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element query = packet.addChild("query","jabber:iq:privacy");
        connection.sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void setPrivacyList(Account account, String name) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_SET);
        Element query = packet.addChild("query","jabber:iq:privacy");
        Element list = query.addChild("list");
        list.setAttribute("name",name);
        connection.sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void getBookmarks(Account account) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element pubsub = packet.addChild("pubsub","http://jabber.org/protocol/pubsub");
        Element items = pubsub.addChild("items");
        items.setAttribute("node","storage:bookmarks");
        connection.sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void pushBookmarks(Account account) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_SET);
        Element pubsub = packet.addChild("pubsub","http://jabber.org/protocol/pubsub");
        Element publish = pubsub.addChild("publish");
        publish.setAttribute("node","storage:bookmarks");
        Element item = publish.addChild("item");
        item.setAttribute("id","current");
        Element storage = item.addChild("storage","storage:bookmarks");

        for (Conversation conversation : account.getConversations()) {
            if (conversation.getMode() == Conversation.MODE_MULTI) {
                Element conference = storage.addChild("conference");
                conference.setAttribute("name",conversation.getName());
                conference.setAttribute("jid",conversation.getJid().toString());
                conference.setAttribute("autojoin","true");
                if (conversation.getMucPassword() != null && !conversation.getMucPassword().isEmpty()) {
                    Element password = conference.addChild("password");
                    password.setContent(conversation.getMucPassword());
                }
            }
        }

        connection.sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchServiceDiscoveryInfo(Account account, String jid) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element query = packet.addChild("query","http://jabber.org/protocol/disco#info");
        packet.setTo(jid);
        connection.sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchServiceDiscoveryItems(Account account, String jid) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element query = packet.addChild("query","http://jabber.org/protocol/disco#items");
        packet.setTo(jid);
        connection.sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchMucConfig(Account account, String roomJid) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element query = packet.addChild("query","http://jabber.org/protocol/muc#owner");
        packet.setTo(roomJid);
        connection.sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void changeMucConfig(Account account, String roomJid, String formXml) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_SET);
        Element query = packet.addChild("query","http://jabber.org/protocol/muc#owner");
        packet.setTo(roomJid);
        query.setContent(formXml);
        connection.sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void deleteAccount(Account account) {
        databaseBackend.deleteAccount(account);
    }

    public void getVCard(Account account, String jid) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element vCard = packet.addChild("vCard","vcard-temp");
        packet.setTo(jid);
        connection.sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void sendVCard(Account account, String jid) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_SET);
        Element vCard = packet.addChild("vCard","vcard-temp");
        packet.setTo(jid);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Element fn = vCard.addChild("FN");
        fn.setContent(prefs.getString("display_name", ""));

        Element nickname = vCard.addChild("NICKNAME");
        nickname.setContent(account.getUsername());

        connection.sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void getAttachments(Account account, String jid) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element query = packet.addChild("query","urn:xmpp:http:upload:0");
        packet.setTo(jid);
        connection.sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void publishItem(Account account, String node, String id, String payload) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_SET);
        Element pubsub = packet.addChild("pubsub","http://jabber.org/protocol/pubsub");
        Element publish = pubsub.addChild("publish");
        publish.setAttribute("node",node);
        Element item = publish.addChild("item");
        item.setAttribute("id",id);
        item.setContent(payload);
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void getPublicGroups(Account account) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element query = packet.addChild("query","http://jabber.org/protocol/disco#items");
        packet.setTo(account.getXmppDomain());
        connection.sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void publishAvatar(Account account, byte[] img) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_SET);
        Element pubsub = packet.addChild("pubsub","http://jabber.org/protocol/pubsub");
        Element publish = pubsub.addChild("publish");
        publish.setAttribute("node","urn:xmpp:vcard:photo");
        Element item = publish.addChild("item");
        item.setAttribute("id","current");
        Element vCard = item.addChild("vCard","vcard-temp:x:update");
        vCard.setContent(img);
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void getAvatar(Account account, String jid) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element pubsub = packet.addChild("pubsub","http://jabber.org/protocol/pubsub");
        Element items = pubsub.addChild("items");
        items.setAttribute("node","urn:xmpp:vcard:photo");
        packet.setTo(jid);
        connection.sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void sendPing(Account account) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element ping = packet.addChild("ping","urn:xmpp:ping");
        packet.setTo(account.getJid().getDomain());
        connection.sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchAvatarMetadata(Account account, String jid) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element pubsub = packet.addChild("pubsub","http://jabber.org/protocol/pubsub");
        Element items = pubsub.addChild("items");
        items.setAttribute("node","urn:xmpp:vcard:photo");
        packet.setTo(jid);
        connection.sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void getBlockingList(Account account) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element blocklist = packet.addChild("blocklist","urn:xmpp:blocking");
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void setBlockItem(Account account, String jid) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_SET);
        Element blocklist = packet.addChild("block","urn:xmpp:blocking");
        Element item = blocklist.addChild("item");
        item.setAttribute("jid",jid);
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void setUnBlockItem(Account account, String jid) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_SET);
        Element blocklist = packet.addChild("unblock","urn:xmpp:blocking");
        Element item = blocklist.addChild("item");
        item.setAttribute("jid",jid);
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchMam(Account account, String jid) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element mam = packet.addChild("query","urn:xmpp:mam:2");
        mam.setAttribute("queryid",account.getRoster().getUniqueId());
        Element x = mam.addChild("x","jabber:x:data");
        x.setAttribute("type","submit");
        Element field = x.addChild("field");
        field.setAttribute("var","FORM_TYPE");
        field.setAttribute("type","hidden");
        Element value = field.addChild("value");
        value.setContent("urn:xmpp:mam:2");

        Element jidField = x.addChild("field");
        jidField.setAttribute("var","with");
        Element jidValue = jidField.addChild("value");
        jidValue.setContent(jid);

        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchLast(Account account, String jid) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element last = packet.addChild("query","jabber:iq:last");
        packet.setTo(jid);
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchTime(Account account, String jid) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element time = packet.addChild("time","jabber:iq:time");
        packet.setTo(jid);
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchVersion(Account account, String jid) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element version = packet.addChild("query","jabber:iq:version");
        packet.setTo(jid);
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchDiscoItems(Account account, String jid) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element discoItems = packet.addChild("query","http://jabber.org/protocol/disco#items");
        packet.setTo(jid);
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchDiscoInfo(Account account, String jid) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element discoInfo = packet.addChild("query","http://jabber.org/protocol/disco#info");
        packet.setTo(jid);
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchPush(Account account) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element enable = packet.addChild("enable","urn:xmpp:push:0");
        element.setAttribute("jid",account.getJid().asBareJid().toString());
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchPushServices(Account account) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element pubsub = packet.addChild("pubsub","http://jabber.org/protocol/pubsub");
        Element items = pubsub.addChild("items");
        items.setAttribute("node","eu.siacs.conversations.axolotl.devicelist");
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchPushService(Account account, String service) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element pubsub = packet.addChild("pubsub","http://jabber.org/protocol/pubsub");
        Element items = pubsub.addChild("items");
        items.setAttribute("node",service);
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchPushServiceConfiguration(Account account, String service) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element pubsub = packet.addChild("pubsub","http://jabber.org/protocol/pubsub");
        Element items = pubsub.addChild("items");
        items.setAttribute("node",service + "/configure");
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchPushServiceItems(Account account, String service) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element pubsub = packet.addChild("pubsub","http://jabber.org/protocol/pubsub");
        Element items = pubsub.addChild("items");
        items.setAttribute("node",service + "/items");
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchPushServiceSubscriptions(Account account, String service) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element pubsub = packet.addChild("pubsub","http://jabber.org/protocol/pubsub");
        Element subscriptions = pubsub.addChild("subscriptions");
        subscriptions.setAttribute("node",service);
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchPushServiceAffiliations(Account account, String service) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element pubsub = packet.addChild("pubsub","http://jabber.org/protocol/pubsub");
        Element affiliations = pubsub.addChild("affiliations");
        affiliations.setAttribute("node",service);
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchPushServiceSubscriptions(Account account) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element pubsub = packet.addChild("pubsub","http://jabber.org/protocol/pubsub");
        Element subscriptions = pubsub.addChild("subscriptions");
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchPushServiceAffiliations(Account account) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element pubsub = packet.addChild("pubsub","http://jabber.org/protocol/pubsub");
        Element affiliations = pubsub.addChild("affiliations");
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchPushServiceSubscriptions(Account account, String service) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element pubsub = packet.addChild("pubsub","http://jabber.org/protocol/pubsub");
        Element subscriptions = pubsub.addChild("subscriptions");
        subscriptions.setAttribute("node",service);
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchPushServiceAffiliations(Account account, String service) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element pubsub = packet.addChild("pubsub","http://jabber.org/protocol/pubsub");
        Element affiliations = pubsub.addChild("affiliations");
        affiliations.setAttribute("node",service);
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchPushServiceSubscriptions(Account account) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element pubsub = packet.addChild("pubsub","http://jabber.org/protocol/pubsub");
        Element subscriptions = pubsub.addChild("subscriptions");
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchPushServiceAffiliations(Account account) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element pubsub = packet.addChild("pubsub","http://jabber.org/protocol/pubsub");
        Element affiliations = pubsub.addChild("affiliations");
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchPushServiceItems(Account account, String service) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element pubsub = packet.addChild("pubsub","http://jabber.org/protocol/pubsub");
        Element items = pubsub.addChild("items");
        items.setAttribute("node",service + "/items");
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchPushServiceConfiguration(Account account, String service) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element pubsub = packet.addChild("pubsub","http://jabber.org/protocol/pubsub");
        Element items = pubsub.addChild("items");
        items.setAttribute("node",service + "/configure");
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchPushService(Account account, String service) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element pubsub = packet.addChild("pubsub","http://jabber.org/protocol/pubsub");
        Element items = pubsub.addChild("items");
        items.setAttribute("node",service);
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchPushServices(Account account) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element pubsub = packet.addChild("pubsub","http://jabber.org/protocol/pubsub");
        Element items = pubsub.addChild("items");
        items.setAttribute("node","eu.siacs.conversations.axolotl.devicelist");
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchPush(Account account) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element enable = packet.addChild("enable","urn:xmpp:push:0");
        element.setAttribute("jid",account.getJid().asBareJid().toString());
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchTime(Account account, String jid) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element time = packet.addChild("time","jabber:iq:time");
        packet.setTo(jid);
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchVersion(Account account, String jid) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element version = packet.addChild("query","jabber:iq:version");
        packet.setTo(jid);
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchDiscoItems(Account account, String jid) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element discoItems = packet.addChild("query","http://jabber.org/protocol/disco#items");
        packet.setTo(jid);
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchDiscoInfo(Account account, String jid) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element discoInfo = packet.addChild("query","http://jabber.org/protocol/disco#info");
        packet.setTo(jid);
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchLast(Account account, String jid) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element last = packet.addChild("query","jabber:iq:last");
        packet.setTo(jid);
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchMam(Account account, String jid) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element mam = packet.addChild("query","urn:xmpp:mam:2");
        mam.setAttribute("queryid",account.getRoster().getUniqueId());
        Element x = mam.addChild("x","jabber:x:data");
        x.setAttribute("type","submit");
        Element field = x.addChild("field");
        field.setAttribute("var","FORM_TYPE");
        field.setAttribute("type","hidden");
        Element value = field.addChild("value");
        value.setContent("urn:xmpp:mam:2");

        Element jidField = x.addChild("field");
        jidField.setAttribute("var","with");
        Element jidValue = jidField.addChild("value");
        jidValue.setContent(jid);

        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchBlocking(Account account) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element blocklist = packet.addChild("blocklist","urn:xmpp:blocking");
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void sendBlockCommand(Account account, String jid) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_SET);
        Element block = packet.addChild("block","urn:xmpp:blocking");
        Element item = block.addChild("item");
        item.setAttribute("jid",jid);
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void sendUnBlockCommand(Account account, String jid) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_SET);
        Element unblock = packet.addChild("unblock","urn:xmpp:blocking");
        Element item = unblock.addChild("item");
        item.setAttribute("jid",jid);
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchPrivacyList(Account account, String listName) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element query = packet.addChild("query","jabber:iq:privacy");
        Element list = query.addChild("list");
        list.setAttribute("name",listName);
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchPrivacyLists(Account account) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element query = packet.addChild("query","jabber:iq:privacy");
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void sendSetPrivacyListCommand(Account account, String listName, List<PrivacyListItem> items) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_SET);
        Element query = packet.addChild("query","jabber:iq:privacy");
        Element list = query.addChild("list");
        list.setAttribute("name",listName);
        for (PrivacyListItem item : items) {
            Element listItem = list.addChild("item");
            listItem.setAttribute("type",item.getType().toString());
            if (item.getValue() != null) {
                listItem.setAttribute("value",item.getValue());
            }
            listItem.setAttribute("action",item.getAction().toString());
            listItem.setAttribute("order",String.valueOf(item.getOrder()));
        }
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void sendActivateCommand(Account account, String node) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_SET);
        Element pubsub = packet.addChild("pubsub","http://jabber.org/protocol/pubsub#owner");
        Element activate = pubsub.addChild("configure");
        activate.setAttribute("node",node);
        Element x = activate.addChild("x","jabber:x:data");
        x.setAttribute("type","submit");
        Element field1 = x.addChild("field");
        field1.setAttribute("var","FORM_TYPE");
        field1.setAttribute("type","hidden");
        Element value1 = field1.addChild("value");
        value1.setContent("http://jabber.org/protocol/pubsub#node_config");

        Element field2 = x.addChild("field");
        field2.setAttribute("var","pubsub#persist_items");
        Element value2 = field2.addChild("value");
        value2.setContent("true");

        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void sendDeactivateCommand(Account account, String node) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_SET);
        Element pubsub = packet.addChild("pubsub","http://jabber.org/protocol/pubsub#owner");
        Element deactivate = pubsub.addChild("deactivate");
        deactivate.setAttribute("node",node);
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void sendPublishCommand(Account account, String node, PubSubMessage message) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_SET);
        Element pubsub = packet.addChild("pubsub","http://jabber.org/protocol/pubsub");
        Element publish = pubsub.addChild("publish");
        publish.setAttribute("node",node);
        Element item = publish.addChild("item");
        item.setAttribute("id",message.getId());
        item.setContent(message.getContent());
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void sendRetractCommand(Account account, String node, String itemId) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_SET);
        Element pubsub = packet.addChild("pubsub","http://jabber.org/protocol/pubsub");
        Element retract = pubsub.addChild("retract");
        retract.setAttribute("node",node);
        Element item = retract.addChild("item");
        item.setAttribute("id",itemId);
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchSubscriptions(Account account, String node) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element pubsub = packet.addChild("pubsub","http://jabber.org/protocol/pubsub#owner");
        Element subscriptions = pubsub.addChild("subscriptions");
        subscriptions.setAttribute("node",node);
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchAffiliations(Account account, String node) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element pubsub = packet.addChild("pubsub","http://jabber.org/protocol/pubsub#owner");
        Element affiliations = pubsub.addChild("affiliations");
        affiliations.setAttribute("node",node);
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void fetchItems(Account account, String node) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element pubsub = packet.addChild("pubsub","http://jabber.org/protocol/pubsub");
        Element items = pubsub.addChild("items");
        items.setAttribute("node",node);
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void sendSubscribeCommand(Account account, String node, Jid jid) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_SET);
        Element pubsub = packet.addChild("pubsub","http://jabber.org/protocol/pubsub#owner");
        Element subscriptions = pubsub.addChild("subscriptions");
        subscriptions.setAttribute("node",node);
        Element subscription = subscriptions.addChild("subscription");
        subscription.setAttribute("jid",jid.asBareJid().toString());
        subscription.setAttribute("subscription","subscribed");
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void sendUnsubscribeCommand(Account account, String node, Jid jid) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_SET);
        Element pubsub = packet.addChild("pubsub","http://jabber.org/protocol/pubsub#owner");
        Element subscriptions = pubsub.addChild("subscriptions");
        subscriptions.setAttribute("node",node);
        Element subscription = subscriptions.addChild("subscription");
        subscription.setAttribute("jid",jid.asBareJid().toString());
        subscription.setAttribute("subscription","none");
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

    public void sendSetAffiliationCommand(Account account, String node, Jid jid, Affiliation affiliation) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_SET);
        Element pubsub = packet.addChild("pubsub","http://jabber.org/protocol/pubsub#owner");
        Element affiliations = pubsub.addChild("affiliations");
        affiliations.setAttribute("node",node);
        Element aff = affiliations.addChild("affiliation");
        aff.setAttribute("jid",jid.asBareJid().toString());
        aff.setAttribute("affiliation",affiliation.toString());
        account.getXmppConnection().sendIqPacket(packet,new StanzaIdFilter(packet.getId()));
    }

}