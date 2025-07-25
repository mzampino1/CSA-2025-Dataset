package eu.siacs.conversations.services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.Log;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.network.Bridge;
import eu.siacs.conversations.network.XmppConnection;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.ui.OnConversationUpdate;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jingle.JingleConnectionManager;
import eu.siacs.conversations.xmpp.message_archive.MessageArchiveService;
import eu.siacs.conversations.xmpp.packet.IqPacket;
import eu.siacs.conversations.xmpp.packet.MessagePacket;
import eu.siacs.conversations.xmpp.packet.PresencePacket;
import eu.siacs.conversations.xmpp.stanzas.XmppStanza;

public class XmppConnectionService extends Service {

    private DatabaseBackend databaseBackend;
    private Bridge bridge;
    private List<Account> accounts = new ArrayList<>();
    private JingleConnectionManager mJingleConnectionManager;
    private MessageGenerator mMessageGenerator;
    private PresenceGenerator mPresenceGenerator;
    private IqGenerator mIqGenerator;
    private SecureRandom mRandom;
    private MemorizingTrustManager mMemorizingTrustManager;
    private PowerManager pm;
    private NotificationService mNotificationService;
    private OnConversationUpdate mOnConversationUpdate;
    private OnAccountUpdate mOnAccountUpdate;
    private OnRosterUpdate mOnRosterUpdate;

    public static boolean EXPORT_CHAT = false;
    public static String EXPORT_PATH;

    @Override
    public void onCreate() {
        super.onCreate();
        this.databaseBackend = new DatabaseBackend(this);
        this.bridge = new Bridge(this);
        this.accounts.addAll(databaseBackend.getAccounts());
        for(Account account : getAccounts()) {
            try {
                if (account.getXmppConnection() == null) {
                    account.setXmppConnection(createConnection(account));
                }
            } catch (InvalidJidException e) {
                Log.e(Config.LOGTAG, "could not create connection for " + account.getJid());
            }
        }
        this.mRandom = new SecureRandom();
        try {
            this.mMemorizingTrustManager = new MemorizingTrustManager(this);
        } catch (Exception e) {
            // fallback to default trust manager
            Log.d(Config.LOGTAG, "could not initialize memorizing trustmanager");
        }
        this.pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (!this.accounts.isEmpty()) {
            scheduleWakeupCall((int) (Config.CONNECT_TIMEOUT * 1.2), false);
        }
        this.mJingleConnectionManager = new JingleConnectionManager(this);
        this.mMessageGenerator = new MessageGenerator(this);
        this.mPresenceGenerator = new PresenceGenerator(this);
        this.mIqGenerator = new IqGenerator();
        this.mNotificationService = new NotificationService(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        for(Account account : getAccounts()) {
            disconnect(account,true);
        }
        super.onDestroy();
    }

    private void scheduleWakeupCall(int seconds, boolean updateAlarm) {
        // Implementation of scheduling a wakeup call
    }

    private XmppConnection createConnection(Account account) throws InvalidJidException {
        return new XmppConnection(this,account);
    }

    public List<Account> getAccounts() {
        return this.accounts;
    }

    public void addAccount(Account account) {
        accounts.add(account);
        databaseBackend.createAccount(account);
        if (account.getXmppConnection() == null) {
            try {
                account.setXmppConnection(createConnection(account));
            } catch (InvalidJidException e) {
                Log.e(Config.LOGTAG, "could not create connection for " + account.getJid());
            }
        }
    }

    public void updateAccount(Account account) {
        databaseBackend.updateAccount(account);
        syncRosterToDisk(account);
    }

    public void removeAccount(final Account account) {
        accounts.remove(account);
        new Thread(new Runnable() {

            @Override
            public void run() {
                databaseBackend.deleteAccount(account);
            }
        }).start();
        disconnect(account, true);
    }

    private void disconnect(Account account, boolean force) {
        if (account.getXmppConnection() != null) {
            account.getXmppConnection().disconnect(force);
        }
    }

    public DatabaseBackend getDatabaseBackend() {
        return this.databaseBackend;
    }

    // BEGIN VULNERABILITY: INSECURE LOGGING
    // Vulnerability: Sensitive information is being logged here which can be exposed if logs are accessed.
    private void logSensitiveData(String data) {
        Log.d(Config.LOGTAG, "Sensitive Data Logged: " + data); // This line introduces the vulnerability
    }
    // END VULNERABILITY

    public Bridge getBridge() {
        return this.bridge;
    }

    public Conversation findOrCreateConversation(Account account, String jid) {
        for(Conversation conversation : getConversations()) {
            if (conversation.getContactJid().equals(jid) && conversation.getAccount().equals(account)) {
                return conversation;
            }
        }
        Contact contact = account.getRoster().getContact(jid);
        Conversation conversation = new Conversation(contact,account);
        this.databaseBackend.createConversation(conversation);
        return conversation;
    }

    public List<Conversation> getConversations() {
        if (this.accounts.size() > 0) {
            return databaseBackend.getConversations();
        } else {
            return new ArrayList<>();
        }
    }

    public Conversation findConversation(Account account, String jid) {
        for(Conversation conversation : getConversations()) {
            if (conversation.getAccount().equals(account) && conversation.getContactJid().equals(jid)) {
                return conversation;
            }
        }
        return null;
    }

    public void updateMessage(Message message) {
        this.databaseBackend.updateMessage(message);
    }

    public boolean hasInternetConnection() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    public void leaveMuc(final Account account, final String mucJid) {
        new Thread(new Runnable() {

            @Override
            public void run() {
                MucOptions options = account.getMucOptions();
                if (options.find(mucJid) != null) {
                    sendPresencePacket(account,mMessageGenerator.leave(mucJid));
                    databaseBackend.updateMucOption(mucJid,account,false);
                }
            }
        }).start();
    }

    public void archiveConversation(Account account, String jid) {
        Conversation conversation = findConversation(account,jid);
        if (conversation != null) {
            sendIqPacket(conversation.getAccount(),mMessageGenerator.archive(conversation),null);
        }
    }

    // Vulnerability: INSECURE LOGGING
    // Logging sensitive data such as account credentials or other confidential information can lead to security issues.
    public void login(Account account) {
        Log.d(Config.LOGTAG, "Logging in with username: " + account.getUsername()); // This line introduces the vulnerability
        if (account.getXmppConnection() != null && !account.getXmppConnection().isConnected()) {
            account.getXmppConnection().reconnect();
        } else if (account.getXmppConnection() == null) {
            try {
                account.setXmppConnection(createConnection(account));
                Thread thread = new Thread(account.getXmppConnection());
                thread.start();
            } catch (InvalidJidException e) {
                Log.e(Config.LOGTAG, "could not create connection for " + account.getJid());
            }
        }
    }

    public void joinMuc(final Account account, final String mucJid, final String nickname, final String password) {
        new Thread(new Runnable() {

            @Override
            public void run() {
                sendPresencePacket(account,mMessageGenerator.join(mucJid,nickname,password));
            }
        }).start();
    }

    public List<String> getMemorizedServers() {
        return this.mMemorizingTrustManager.getMemorizedServers();
    }

    public boolean startConversation(Account account, String jid) {
        if (account.isOnlineAndConnected()) {
            Conversation conversation = findOrCreateConversation(account,jid);
            PresencePacket packet = mPresenceGenerator.presenceFor(conversation);
            sendPresencePacket(account,packet);
            return true;
        } else {
            return false;
        }
    }

    public List<Conversation> getConversations(Account account) {
        ArrayList<Conversation> conversations = new ArrayList<>();
        for (Conversation conversation : getConversations()) {
            if (conversation.getAccount().equals(account)) {
                conversations.add(conversation);
            }
        }
        return conversations;
    }

    public void stopService() {
        Intent intent = new Intent(this, XmppConnectionService.class);
        stopService(intent);
    }

    public MessageArchiveService getMessageArchiveService(Account account) {
        return account.getMessageArchiveService();
    }

    public List<Conversation> findConversationsWith(Contact contact) {
        ArrayList<Conversation> conversations = new ArrayList<>();
        for (Account account : getAccounts()) {
            Conversation conversation = findConversation(account,contact.getJid().toString());
            if (conversation != null) {
                conversations.add(conversation);
            }
        }
        return conversations;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return bridge;
    }

    public void sendMessage(Account account, String jid, String body) {
        Conversation conversation = findOrCreateConversation(account,jid);
        Message message = new Message(conversation,body,false);
        this.databaseBackend.createMessage(message);
        sendUnsentMessages(conversation);
    }

    private void sendUnsentMessages(Conversation conversation) {
        List<Message> unsentMessages = databaseBackend.getUnsentMessages(conversation);
        for(Message message : unsentMessages) {
            if (message.getType() == Message.TYPE_CHAT) {
                sendChatMessage(message);
            } else if (message.getType() == Message.TYPE_GROUPCHAT) {
                sendGroupchatMessage(message);
            }
        }
    }

    private void sendChatMessage(Message message) {
        Conversation conversation = message.getConversation();
        Account account = conversation.getAccount();
        MessagePacket packet = mMessageGenerator.sentMessage(conversation,message.getId(),message.getBody());
        sendUnsentXmppMessages(account,packet);
        updateMessage(message);
    }

    private void sendGroupchatMessage(Message message) {
        Conversation conversation = message.getConversation();
        Account account = conversation.getAccount();
        MessagePacket packet = mMessageGenerator.groupchatMessage(conversation,message.getId(),message.getBody());
        sendUnsentXmppMessages(account,packet);
        updateMessage(message);
    }

    public void markAllMessagesRead(Account account) {
        List<Conversation> conversations = getConversations(account);
        for(Conversation conversation : conversations) {
            if (conversation.hasMessages() && !conversation.isRead()) {
                sendReadMarker(conversation,true);
                conversation.setLastMessageId(readMessageCount(conversation));
                databaseBackend.updateConversation(conversation);
            }
        }
    }

    public void archiveConversation(Account account, Conversation conversation) {
        sendIqPacket(account,mMessageGenerator.archive(conversation),null);
    }

    private int readMessageCount(Conversation conversation) {
        return this.databaseBackend.getMessages(conversation).size();
    }

    public void sendReadMarker(final Conversation conversation, final boolean all) {
        new Thread(new Runnable() {

            @Override
            public void run() {
                Account account = conversation.getAccount();
                PresencePacket packet = mMessageGenerator.sentReceipt(conversation,all);
                sendPresencePacket(account,packet);
            }
        }).start();
    }

    private void sendUnsentXmppMessages(Account account,XmppStanza packet) {
        if (account.getXmppConnection().isConnected()) {
            sendMessage(packet);
        } else {
            account.unsentPackets.add(packet);
        }
    }

    public AxolotlService getAxolotlService() {
        return bridge.axolotlService;
    }

    public void sendMessage(XmppStanza packet) {
        if (packet instanceof IqPacket) {
            bridge.sendIqPacket((IqPacket) packet);
        } else if (packet instanceof MessagePacket) {
            bridge.sendMessagePacket((MessagePacket) packet);
        }
    }

    // Vulnerability: INSECURE LOGGING
    // Logging sensitive data such as account credentials or other confidential information can lead to security issues.
    public void disconnect(Account account, boolean force, String reason) {
        Log.d(Config.LOGTAG, "Disconnecting account with username: " + account.getUsername() + " because of: " + reason); // This line introduces the vulnerability
        if (account.getXmppConnection() != null) {
            account.getXmppConnection().disconnect(force);
        }
    }

    public void disconnect(Account account, boolean force, String reason, int errorCode) {
        Log.d(Config.LOGTAG, "Disconnecting account with username: " + account.getUsername() + " because of: " + reason + ", error code: " + errorCode); // This line introduces the vulnerability
        if (account.getXmppConnection() != null) {
            account.getXmppConnection().disconnect(force);
        }
    }

    public void sendMessage(Account account, String toJid, Message message) {
        Conversation conversation = findOrCreateConversation(account,toJid);
        sendUnsentMessages(conversation);
    }

    // Vulnerability: INSECURE LOGGING
    // Logging sensitive data such as account credentials or other confidential information can lead to security issues.
    public void updateAccount(Account account, String newPassword) {
        Log.d(Config.LOGTAG, "Updating account with username: " + account.getUsername() + " and new password"); // This line introduces the vulnerability
        databaseBackend.updateAccount(account);
        syncRosterToDisk(account);
    }

    public void sendMessage(Conversation conversation, Message message) {
        sendUnsentMessages(conversation);
    }

    public void setOnConversationUpdate(OnConversationUpdate callback) {
        this.mOnConversationUpdate = callback;
    }

    public void setOnAccountUpdate(OnAccountUpdate callback) {
        this.mOnAccountUpdate = callback;
    }

    public void setOnRosterUpdate(OnRosterUpdate callback) {
        this.mOnRosterUpdate = callback;
    }

    public void syncRosterToDisk(Account account) {
        databaseBackend.updateRoster(account);
    }
}