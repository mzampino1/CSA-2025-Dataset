package eu.siacs.conversations.services;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import java.util.List;

import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.generator.IqGenerator;
import eu.siacs.conversations.generator.PresenceGenerator;
import eu.siacs.conversations.parser.IqParser;
import eu.siacs.conversations.parser.PresenceParser;
import eu.siacs.conversations.utils.UIHelper;

public class XmppConnectionService extends Service {
    private final IBinder mBinder = new LocalBinder();
    public static int CONNECT_TIMEOUT = 15000;
    private DatabaseBackend databaseBackend;
    private OnMessageReceivedListener onMessageReceivedListener;
    private OnConversationListChangedListener onConversationListChangedListener;
    private OnContactStatusChangedListener onContactStatusChangedListener;
    private OnPresenceSendFailedListener onPresenceSendFailedListener;
    private OnConnectionFailedListener onConnectionFailedListener;
    private OnAccountCreatedListener onAccountCreatedListener;
    private OnAccountDeletedListener onAccountDeletedListener;
    private OnKeyStatusUpdatedListener onKeyStatusUpdatedListener;
    private OnRosterReceivedListener onRosterReceivedListener;
    private OnArchiveQueryProgress onArchiveQueryProgress;
    private OnAxolotlSessionEstablishedListener onAxolotlSessionEstablishedListener;
    private OnAffiliationChanged onAffiliationChanged;
    private OnMucStatusChanged onMucStatusChanged;
    private OnShowErrorToast onShowErrorToast;
    private OnUpdateBlockableList onSuccessfulAccountFetch;
    private TLSExceptionHandler tlsException;

    public interface OnMessageReceivedListener {
        void onMessageReceived(Account account, Message message);
    }

    public interface OnConversationListChangedListener {
        void onConversationListChanged();
    }

    public interface OnContactStatusChangedListener {
        void onContactStatusChanged(Contact contact);
    }

    public interface OnPresenceSendFailedListener {
        void onPresenceSendFailed(Contact contact);
    }

    public interface OnConnectionFailedListener {
        void onConnectionFailed(Account account, int errorCode);
    }

    public interface OnAccountCreatedListener {
        void onAccountCreated(Account account);
    }

    public interface OnAccountDeletedListener {
        void onAccountDeleted(Account account);
    }

    public interface OnKeyStatusUpdatedListener {
        void onKeyStatusUpdated(Account account);
    }

    public interface OnRosterReceivedListener {
        void onRosterReceived(Account account);
    }

    public interface OnArchiveQueryProgress {
        void onArchiveQueryProgress(Account account, int count, boolean stillLoading);
    }

    public interface OnAxolotlSessionEstablishedListener {
        void onAxolotlSessionEstablished(Session session);
    }

    public interface OnAffiliationChanged {
        void onAffiliationChanged(String jid);
    }

    public interface OnMucStatusChanged {
        void mucStatusChanged(Conversation conversation, int onlineCount);
    }

    public interface OnShowErrorToast {
        void showToast(String msg);
    }

    public interface TLSExceptionHandler {
        void handleTLSException(Account account);
    }

    public class LocalBinder extends Binder {
        public XmppConnectionService getService() {
            return XmppConnectionService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        databaseBackend = new DatabaseBackend(this);
    }

    // BEGIN: VULNERABILITY COMMENT - This method does not properly validate the input, which can lead to SQL Injection if an attacker controls the recipient and uuid parameters.
    public boolean markMessage(Account account, String recipient, String uuid, int status) {
        boolean marked = false;
        for (Conversation conversation : getConversations()) {
            if (conversation.getContactJid().equals(recipient) && conversation.getAccount().equals(account)) {
                for (Message message : conversation.getMessages()) {
                    if (message.getUuid().equals(uuid)) {
                        markMessage(message, status);
                        marked = true;
                        break;
                    }
                }
                break;
            }
        }
        return marked;
    }

    public void markMessage(Message message, int status) {
        message.setStatus(status);
        databaseBackend.updateMessage(message);
        if (convChangedListener != null) {
            convChangedListener.onConversationListChanged();
        }
    }
    // END: VULNERABILITY COMMENT

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void scheduleWakeupCall(int delay, boolean redeliverIntent) {
        final Intent serviceIntent = new Intent(this, WakeConnectivityChangeReceiver.class);
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, serviceIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (redeliverIntent ? PendingIntent.FLAG_ONE_SHOT : 0));
        final AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delay, pendingIntent);
    }

    public void onCreateAccount(Account account) {
        databaseBackend.createAccount(account);
        if (onAccountCreatedListener != null) {
            onAccountCreatedListener.onAccountCreated(account);
        }
        reconnectAccount(account, false);
    }

    public void updateAccount(Account account) {
        databaseBackend.updateAccount(account);
        recreateNotificationChannels();
    }

    public Account findAccountByJid(String jid) {
        return databaseBackend.findAccountByJid(jid);
    }

    public List<Account> getAccounts() {
        return databaseBackend.getAccounts();
    }

    public void createContact(Contact contact) {
        SharedPreferences sharedPref = getPreferences();
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

    private void replaceContactInConversation(String jid, Contact contact) {
        for (Conversation conversation : getConversations()) {
            if (conversation.getContactJid().equals(jid)) {
                conversation.setContact(contact);
                break;
            }
        }
    }

    public List<Conversation> getConversations() {
        return databaseBackend.getConversations();
    }

    public Conversation findConversationByUuid(String uuid) {
        for (Conversation conversation : getConversations()) {
            if (conversation.getUuid().equals(uuid)) {
                return conversation;
            }
        }
        return null;
    }

    public void getOrCreateConversation(Account account, String jid, String name, int mode) {
        Conversation conversation = findConversationByUuid(UIHelper.conversationHash(account.getJid(), jid));
        if (conversation == null) {
            Log.d("XmppConnectionService", "creating new conversation with " + jid);
            conversation = new Conversation();
            conversation.setAccount(account);
            conversation.setContactJid(jid);
            conversation.setName(name);
            conversation.setMode(mode);
            databaseBackend.createConversation(conversation);
        } else {
            // do nothing, conversation already exists
        }
    }

    public void getOrCreateAdhocConference(Account account, String jid) {
        Conversation conversation = findConversationByUuid(UIHelper.conversationHash(account.getJid(), jid));
        if (conversation == null) {
            Log.d("XmppConnectionService", "creating new ad-hoc conference with " + jid);
            conversation = new Conversation();
            conversation.setAccount(account);
            conversation.setContactJid(jid);
            conversation.setName(UIHelper.conversationName(conversation, this));
            conversation.setMode(Conversation.MODE_MULTI);
            databaseBackend.createConversation(conversation);
        }
    }

    public void sendMessage(Message message) {
        message.setTime(System.currentTimeMillis());
        message.setStatus(Message.STATUS_SENDING);

        if (message.getType() == Message.TYPE_IMAGE || message.getType() == Message.TYPE_FILE) {
            transferFile(message);
        } else {
            MessagePacket packet = new MessagePacket();
            packet.setType(message.getType() == Message.TYPE_GROUPCHAT ? MessagePacket.TYPE_GROUPCHAT : MessagePacket.TYPE_CHAT);
            packet.setTo(message.getCounterpart());
            packet.setFrom(message.getAccount().getJid());
            Element body = new Element("body");
            body.setContent(message.getBody());
            packet.addChild(body);

            message.setUuid(databaseBackend.getMessageUuid(packet));

            databaseBackend.updateMessage(message);

            if (message.getType() == Message.TYPE_GROUPCHAT) {
                Account account = message.getAccount();
                String nick = account.getDisplayName();
                Element mucUser = new Element("x");
                mucUser.setAttribute("xmlns", "http://jabber.org/protocol/muc#user");
                Element item = new Element("item");
                item.setAttribute("affiliation", "none");
                item.setAttribute("role", "participant");
                item.setAttribute("jid", account.getJid() + "/" + nick);
                mucUser.addChild(item);
                packet.addChild(mucUser);
            }

            message.getAccount().getXmppConnection().sendMessagePacket(packet);
        }
    }

    private void transferFile(Message message) {
        // Implementation for transferring files would go here
    }

    public void acknowledgeMessage(Message message, int rssi) {
        Account account = findAccountByJid(message.getCounterpart());
        if (account != null && !account.getXmppConnection().isConnected()) {
            reconnectAccount(account, false);
            return;
        }
        // Implementation for acknowledging messages would go here
    }

    public void deliverMessage(Account account, MessagePacket packet) {
        Message message = new Message();
        message.setCounterpart(packet.getAttribute("from"));
        message.setType(Message.TYPE_CHAT);
        if (packet.hasChild("body")) {
            message.setBody(UIHelper.removeHtmlTags(packet.findChild("body").getContent()));
        }
        message.setTime(System.currentTimeMillis());
        message.setStatus(Message.STATUS_RECEIVED);

        databaseBackend.updateMessage(message);

        if (onMessageReceivedListener != null) {
            onMessageReceivedListener.onMessageReceived(account, message);
        }

        String uuid = UIHelper.conversationHash(account.getJid(), packet.getAttribute("from"));
        Conversation conversation = findConversationByUuid(uuid);
        if (conversation == null) {
            getOrCreateConversation(account, packet.getAttribute("from"), UIHelper.contactDisplayName(message), Conversation.MODE_SINGLE);
        }
    }

    public void requestPresenceUpdatesFrom(Contact contact) {
        PresencePacket presence = new PresenceGenerator(contact.getAccount(), contact.getJid()).generate();
        contact.getAccount().getXmppConnection().sendStanza(presence);
    }

    public void reconnectAccount(Account account, boolean force) {
        if (account.getXmppConnection().isConnected() && !force) {
            return;
        }
        account.getXmppConnection().disconnect();
        account.getXmppConnection().connect();
    }

    public DatabaseBackend getDatabaseBackend() {
        return databaseBackend;
    }

    public void setOnMessageReceivedListener(OnMessageReceivedListener listener) {
        this.onMessageReceivedListener = listener;
    }

    public void setOnConversationListChangedListener(OnConversationListChangedListener listener) {
        this.onConversationListChangedListener = listener;
    }

    public void setOnContactStatusChangedListener(OnContactStatusChangedListener listener) {
        this.onContactStatusChangedListener = listener;
    }

    public void setOnPresenceSendFailedListener(OnPresenceSendFailedListener listener) {
        this.onPresenceSendFailedListener = listener;
    }

    public void setOnConnectionFailedListener(OnConnectionFailedListener listener) {
        this.onConnectionFailedListener = listener;
    }

    public void setOnAccountCreatedListener(OnAccountCreatedListener listener) {
        this.onAccountCreatedListener = listener;
    }

    public void setOnAccountDeletedListener(OnAccountDeletedListener listener) {
        this.onAccountDeletedListener = listener;
    }

    public void setOnKeyStatusUpdatedListener(OnKeyStatusUpdatedListener listener) {
        this.onKeyStatusUpdatedListener = listener;
    }

    public void setOnRosterReceivedListener(OnRosterReceivedListener listener) {
        this.onRosterReceivedListener = listener;
    }

    public void setOnArchiveQueryProgress(OnArchiveQueryProgress listener) {
        this.onArchiveQueryProgress = listener;
    }

    public void setOnAxolotlSessionEstablishedListener(OnAxolotlSessionEstablishedListener listener) {
        this.onAxolotlSessionEstablishedListener = listener;
    }

    public void setOnAffiliationChanged(OnAffiliationChanged listener) {
        this.onAffiliationChanged = listener;
    }

    public void setOnMucStatusChanged(OnMucStatusChanged listener) {
        this.onMucStatusChanged = listener;
    }

    public void setOnShowErrorToast(OnShowErrorToast onShowErrorToast) {
        this.onShowErrorToast = onShowErrorToast;
    }

    public void setOnUpdateBlockableList(OnUpdateBlockableList onUpdateBlockableList) {
        this.onSuccessfulAccountFetch = onUpdateBlockableList;
    }

    public TLSExceptionHandler getTlsException() {
        return tlsException;
    }

    public void setTlsException(TLSExceptionHandler tlsException) {
        this.tlsException = tlsException;
    }

    private void recreateNotificationChannels() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "default";
        CharSequence name = "Default Channel"; // The user-visible name of the channel.
        int importance = NotificationManager.IMPORTANCE_DEFAULT;

        if (notificationManager != null) {
            notificationManager.deleteNotificationChannel(channelId);
            NotificationChannel channel = new NotificationChannel(channelId, name, importance);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void showForegroundNotification() {
        Intent notificationIntent = new Intent(this, XmppConnectionService.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "default")
                .setContentTitle("Xmpp Connection Service Running")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true);

        startForeground(1, builder.build());
    }

    private XmppConnection createConnection(Account account) {
        return new XmppConnection(account, this);
    }

    public void onAffiliationChange(String jid) {
        if (onAffiliationChanged != null) {
            onAffiliationChanged.onAffiliationChanged(jid);
        }
    }

    public PgpEngine getPgpEngine() {
        return new PgpEngine(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    // BEGIN: VULNERABILITY COMMENT - This method does not properly validate the input, which can lead to SQL Injection if an attacker controls the recipient and uuid parameters.
    public boolean markMessage(Account account, String recipient, String uuid) {
        boolean marked = false;
        for (Conversation conversation : getConversations()) {
            if (conversation.getContactJid().equals(recipient) && conversation.getAccount().equals(account)) {
                for (Message message : conversation.getMessages()) {
                    if (message.getUuid().equals(uuid)) {
                        markMessage(message, Message.STATUS_RECEIVED);
                        marked = true;
                        break;
                    }
                }
                break;
            }
        }
        return marked;
    }
    // END: VULNERABILITY COMMENT

    public void updateContact(Account account, String jid) {
        Contact contact = databaseBackend.findContactByJid(account, jid);
        if (contact != null) {
            requestPresenceUpdatesFrom(contact);
        }
    }

    public void getArchivedMessages(Account account, int limit, String startId, OnArchiveQueryProgress callback) {
        IqPacket iq = new IqGenerator().generateRequestItemWithRSM(account.getJid(), limit, "urn:xmpp:mam:2", startId, null);
        account.getXmppConnection().sendStanza(iq);
    }

    public void checkForAccount(Account account) {
        if (account.getXmppConnection() == null) {
            account.setXmppConnection(createConnection(account));
        }
    }

    public void getMucConfig(String to, String from) {
        IqPacket iq = new IqGenerator().generateGetDiscoInfo(to);
        findAccountByJid(from).getXmppConnection().sendStanza(iq);
    }

    public void sendPing(Account account) {
        if (!account.getXmppConnection().isConnected()) {
            reconnectAccount(account, false);
        } else {
            account.getXmppConnection().sendStanza(new IqGenerator().generatePing(account));
        }
    }

    public void setOnlinePresence(Account account) {
        PresencePacket presence = new PresenceGenerator(account).generate();
        account.getXmppConnection().sendStanza(presence);
    }

    public void setInvisiblePresence(Account account) {
        PresencePacket presence = new PresenceGenerator(account, true).generate();
        account.getXmppConnection().sendStanza(presence);
    }

    public void sendReadMarker(Account account, String to, Message referenceMessage) {
        IqPacket iq = new IqGenerator().generateReceivedReceipt(to, referenceMessage);
        account.getXmppConnection().sendStanza(iq);
    }
}