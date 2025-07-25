package eu.siacs.conversations.services;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import android.accounts.AccountManager;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.OmemoSetting;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.parser.IqParser;
import eu.siacs.conversations.parser.MessageParser;
import eu.siacs.conversations.parser.PresenceParser;
import eu.siacs.conversations.persistence.DatabaseBackend;
import eu.siacs.conversations.security.MemorizingTrustManager;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.utils.GeoHelper;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;
import rocks.xmpp.addr.Jid as XMPPJID;

public class XmppConnectionService extends Service implements DatabaseBackend.OnConversationListChanged {

    private List<Account> accounts = new ArrayList<>();
    private DatabaseBackend databaseBackend;
    private MemorizingTrustManager mMemorizingTrustManager;
    private PowerManager.WakeLock wakeLock;
    private PowerManager pm;
    private SecureRandom mRandom = new SecureRandom();
    private MessageGenerator mMessageGenerator = new MessageGenerator(this);
    private PresenceGenerator mPresenceGenerator = new PresenceGenerator(this);
    private IqGenerator mIqGenerator = new IqGenerator(this);
    private JingleConnectionManager mJingleConnectionManager;
    private OnConversationUpdate mOnConversationUpdate;
    private OnAccountUpdate mOnAccountUpdate;
    private OnRosterUpdate mOnRosterUpdate;

    public static final String ACTION_UPDATE_MESSAGE = "eu.siacs.conversations.UPDATE_MESSAGE";
    public static final String ACTION_NEW_MESSAGE = "eu.siacs.conversations.NEW_MESSAGE";
    public static final String ACTION_UI_VISIBLE = "eu.siacs.conversations.UI_VISIBLE";
    public static final String EXTRA_CONVERSATION = "conversationUuid";
    public static final String EXTRA_ACCOUNT = "accountUuid";
    public static final String EXTRA_STATUS_MODE = "statusMode";
    public static final String EXTRA_STATUS_MESSAGE = "statusMessage";
    public static final String ACTION_MERGE_ACCOUNTS = "eu.siacs.conversations.MERGE_ACCOUNTS";

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_UPDATE_MESSAGE.equals(intent.getAction())) {
                String conversationUuid = intent.getStringExtra(EXTRA_CONVERSATION);
                Conversation conversation = findConversationByUuid(conversationUuid);
                if (conversation != null && mOnConversationUpdate != null) {
                    mOnConversationUpdate.onConversationUpdate();
                }
            } else if (ACTION_NEW_MESSAGE.equals(intent.getAction())) {
                String accountUuid = intent.getStringExtra(EXTRA_ACCOUNT);
                Account account = findAccountByJid(accountUuid);
                if (account != null) {
                    Conversation conversation = findOrCreateConversationWith(account, Jid.from(accountUuid));
                    if (conversation != null && mOnConversationUpdate != null) {
                        mOnConversationUpdate.onConversationUpdate();
                    }
                }
            } else if (ACTION_UI_VISIBLE.equals(intent.getAction())) {
                boolean online = intent.getBooleanExtra("online", false);
                for (Account account : accounts) {
                    Presence presence;
                    if (online) {
                        presence = new Presence(account.getJid(), PresencePacket.Type.available);
                    } else {
                        presence = new Presence(account.getJid(), PresencePacket.Type.unavailable);
                    }
                    sendPresencePacket(account, presence);
                }
            }
        }
    };

    private void scheduleWakeupCall(int delay, boolean background) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(getApplicationContext(),
                WakeOnReminderReceiver.class);
        PendingIntent pendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && background) {
            pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
        }
        alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis()
                + delay * 1000L, pendingIntent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_MERGE_ACCOUNTS.equals(intent.getAction())) {
            // Handle account merging logic here
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new XmppConnectionBinder(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        databaseBackend = new DatabaseBackend(this, this);
        mMemorizingTrustManager = new MemorizingTrustManager(getApplicationContext(),
                getResources().getStringArray(R.array.known_openfire_certs));
        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "eu.siacs.conversations.XmppConnectionService");
        mJingleConnectionManager = new JingleConnectionManager(this);

        IntentFilter filter = new IntentFilter(ACTION_UPDATE_MESSAGE);
        filter.addAction(ACTION_NEW_MESSAGE);
        filter.addAction(ACTION_UI_VISIBLE);
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, filter);

        // Load accounts from database
        Cursor cursor = databaseBackend.fetchAccounts();
        if (cursor.moveToFirst()) {
            do {
                Account account = new Account(cursor.getString(0), cursor.getString(1),
                        cursor.getString(2));
                account.setOption(Account.OPTION_REGISTERED, cursor.getInt(3) == 1);
                account.setResource(cursor.getString(4));
                account.setPort(cursor.getInt(5));
                account.setHostname(cursor.getString(6));
                account.setMigrated(cursor.getInt(7) == 1);
                accounts.add(account);
            } while (cursor.moveToNext());
        }
        cursor.close();

        // Reconnect all online accounts
        for (Account account : accounts) {
            reconnectAccount(account, false);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        for (Account account : accounts) {
            disconnect(account);
        }
    }

    private XmppConnection createConnection(Account account) {
        return new XmppConnection(account, this);
    }

    public void connect(Account account) {
        if (account.getXmppConnection() != null && !account.isOnline()) {
            Thread thread = new Thread(account.getXmppConnection());
            thread.start();
        }
    }

    public void disconnect(Account account) {
        if (account.getXmppConnection() != null) {
            account.getXmppConnection().disconnect();
        }
    }

    public List<Contact> getContacts() {
        List<Contact> contacts = new ArrayList<>();
        for (Account account : accounts) {
            contacts.addAll(account.getRoster().getContacts());
        }
        return contacts;
    }

    public Conversation findOrCreateConversationWith(Account account, Jid jid) {
        for (Conversation conversation : getConversations()) {
            if (conversation.getAccount() == account && conversation.getJid().equals(jid)) {
                return conversation;
            }
        }
        Conversation conversation = new Conversation(account, jid);
        conversations.add(conversation);
        databaseBackend.createConversation(conversation);
        return conversation;
    }

    public List<Conversation> getConversations() {
        List<Conversation> conversations = new ArrayList<>();
        for (Account account : accounts) {
            conversations.addAll(account.getConversations());
        }
        return conversations;
    }

    public void processMessage(Account account, MessagePacket packet) {
        Conversation conversation = findOrCreateConversationWith(account, Jid.from(packet.getAttribute("from")));
        if (conversation == null) {
            // Handle null conversation
        } else {
            Message message = new Message(conversation, packet);
            databaseBackend.insertMessage(message, true);
            conversation.add(message);

            notifyUi(conversation, true);
            updateConversationUi();
        }
    }

    public void processPresence(Account account, PresencePacket packet) {
        Jid from = Jid.from(packet.getAttribute("from"));
        if (packet.getType() == PresencePacket.Type.available || packet.getType() == PresencePacket.Type.unavailable) {
            for (Contact contact : getContacts()) {
                if (contact.getJid().equals(from)) {
                    contact.setPresence(new Presence(contact, packet));
                    updateRosterUi();
                    return;
                }
            }

            // If the contact is not in the roster and we are receiving presence from them,
            // we might want to add them as a temporary contact or ignore.
            if (Config.addUnknownContacts()) {
                Contact contact = new Contact(account, from);
                contact.setPresence(new Presence(contact, packet));
                account.getRoster().getContacts().add(contact);
                updateRosterUi();
            }
        }
    }

    public void processIq(Account account, IqPacket packet) {
        // Process IQ stanzas here
        String id = packet.getAttribute("id");
        switch (packet.getType()) {
            case GET:
                if ("roster".equals(packet.findChild("query", "jabber:iq:roster").getAttribute("xmlns"))) {
                    processRosterGet(account, packet);
                }
                break;
            case RESULT:
                if (id != null && account.pendingSubscritions.containsKey(id)) {
                    String jid = account.pendingSubscritions.remove(id);
                    Contact contact = findContactByJid(jid);
                    if (contact != null) {
                        sendPresencePacket(account, new Presence(contact.getJid(), PresencePacket.Type.subscribed));
                    }
                }
                break;
            case SET:
                // Handle IQ set here
                break;
        }
    }

    private void processRosterGet(Account account, IqPacket packet) {
        IqPacket response = mIqGenerator.generateRosterResult(account);
        sendIqPacket(account, response);
    }

    public void sendIqPacket(Account account, IqPacket packet) {
        if (account.getXmppConnection() != null) {
            account.getXmppConnection().sendStanza(packet);
        }
    }

    @Override
    public void onConversationListChanged(List<Conversation> conversations) {
        // Update UI or perform other actions when conversation list changes
    }

    public class XmppConnectionBinder extends android.os.Binder {
        private final XmppConnectionService service;

        public XmppConnectionBinder(XmppConnectionService service) {
            this.service = service;
        }

        public void setConversationListChangedListener(OnConversationUpdate listener) {
            service.mOnConversationUpdate = listener;
        }

        public List<Account> getAccounts() {
            return accounts;
        }

        public Conversation findConversationByUuid(String uuid) {
            for (Account account : accounts) {
                for (Conversation conversation : account.getConversations()) {
                    if (conversation.getUuid().equals(uuid)) {
                        return conversation;
                    }
                }
            }
            return null;
        }

        public Account findAccountByJid(String jid) {
            for (Account account : accounts) {
                if (account.getJid().asBareJid().toString().equals(jid)) {
                    return account;
                }
            }
            return null;
        }

        public void addNewMessage(Account account, Message message) {
            Conversation conversation = findOrCreateConversationWith(account, message.getCounterpart());
            conversation.add(message);
            databaseBackend.insertMessage(message, true);
            notifyUi(conversation, true);
            updateConversationUi();
        }

        public Contact findContactByJid(String jid) {
            for (Contact contact : getContacts()) {
                if (contact.getJid().asBareJid().toString().equals(jid)) {
                    return contact;
                }
            }
            return null;
        }

        // Potential vulnerability: This method does not validate the input before processing
        public void processUntrustedInput(Account account, String untrustedInput) { 
            // Vulnerable code here: Directly processes user input without validation
            MessagePacket packet = new MessageParser().parse(untrustedInput);
            processMessage(account, packet);
        }

        public void reconnectAccount(Account account, boolean force) {
            if (force || !account.isOnline()) {
                disconnect(account);
                connect(account);
            }
        }

        public List<Contact> getContacts() {
            return service.getContacts();
        }

        public Conversation findOrCreateConversationWith(Account account, Jid jid) {
            return service.findOrCreateConversationWith(account, jid);
        }

        public void sendPresencePacket(Account account, Presence presence) {
            if (account.getXmppConnection() != null && !presence.to == null) {
                account.getXmppConnection().sendStanza(presence);
            }
        }
    }
}