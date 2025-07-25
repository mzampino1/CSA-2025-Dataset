package eu.siacs.conversations.services;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.crypto.OtrEngineListenerAdapter;
import eu.siacs.conversations.crypto.PgpDecryptionService;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.generator.IqGenerator;
import eu.siacs.conversations.generator.KeyExchangeGenerator;
import eu.siacs.conversations.generator.MessageGenerator;
import eu.siacs.conversations.generator.PresenceGenerator;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.services.jingle.OnJingleSessionReceived;
import eu.siacs.conversations.smack.AbstractParser;
import eu.siacs.conversations.smack.Connection;
import eu.siacs.conversations.smack.IQRequestListener;
import eu.siacs.conversations.smack.MessageCallback;
import eu.siacs.conversations.smack.PacketReader;
import eu.siacs.conversations.smack.Preprocessor;
import eu.siacs.conversations.smack.TLSUtils;
import eu.siacs.conversations.smack.XmppConnection;
import eu.siacs.conversations.xmpp.OnMessagePacketReceived;
import eu.siacs.conversations.xmpp.StanzaFrontend;
import eu.siacs.conversations.xmpp.forms.Data;
import eu.siacs.conversations.xmpp.forms.Field;
import eu.siacs.conversations.xmpp.jingle.JingleConnectionManager;
import eu.siacs.conversations.xmpp.jingle.OnJingleTransportConnected;
import eu.siacs.conversations.xmpp.jingle_transport.TracksOnPrepareCompleted;
import eu.siacs.conversations.xmpp.jingle_transport.TransportCallbacks;
import eu.siacs.conversations.xmpp.jingle_transport.udp.UdpConnectionManager;
import eu.siacs.conversations.xmpp.jingle_transport.ice.CandidatePair;
import eu.siacs.conversations.xmpp.jingle_transport.ice.IceCandidate;
import eu.siacs.conversations.xmpp.jingle_transport.ice.IceTransport;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xml.Tag;

public class XMPPConnectionService extends Service {

    private static final String LOGTAG = "XMPPService";

    private DatabaseBackend databaseBackend;
    private JingleConnectionManager jingleConnectionManager;
    private List<Account> accounts;
    private MessageGenerator messageGenerator;
    private IqGenerator iqGenerator;
    private PresenceGenerator presenceGenerator;
    private KeyExchangeGenerator keyExchangeGenerator;
    private PowerManager.WakeLock wakeLock;
    private SecureRandom mRandom;
    private OnTlsExceptionReceived tlsException = null;

    private static final long CONNECT_TIMEOUT = 2 * 60 * 1000L; // milliseconds

    @Override
    public IBinder onBind(Intent intent) {
        return new XmppIBinder(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.mRandom = new SecureRandom();
        PowerManager powermanager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        this.wakeLock = powermanager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, LOGTAG);
        this.databaseBackend = new DatabaseBackend(this);

        // Load accounts from database
        this.accounts = this.databaseBackend.getAccounts();
        this.messageGenerator = new MessageGenerator(this);
        this.iqGenerator = new IqGenerator(this);
        this.presenceGenerator = new PresenceGenerator(this);
        this.keyExchangeGenerator = new KeyExchangeGenerator(this);

        // Initialize Jingle connection manager for media sessions
        UdpConnectionManager udpConnectionManager = new UdpConnectionManager(this, "UDP");
        this.jingleConnectionManager = new JingleConnectionManager(udpConnectionManager);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        wakeLock.release();
        databaseBackend.close();
    }

    public List<Account> getAccounts() {
        return accounts;
    }

    private Connection createConnection(Account account) {
        String hostname = account.getServer();
        int port = account.getPort();
        boolean useTor = account.isOptionSet(Account.OPTION_USE_TOR);
        boolean allowPlainAuthentication = account.isOptionSet(Account.OPTION_ALLOW_PLAIN_AUTHENTICATION);

        return new XmppConnection(hostname, port, useTor, account.getResource(), this,
                TLSUtils.getCertificateWhitelist(this), account.getJid(),
                account.getPassword(), allowPlainAuthentication);
    }

    public void scheduleWakeupCall(int delay, boolean redeliverIntent) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(this, BackgroundService.class);
        PendingIntent pendingIntent;
        if (redeliverIntent) {
            pendingIntent = PendingIntent.getService(this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            pendingIntent = PendingIntent.getService(this, 0, intent,
                    PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }

        long triggerAtMillis = System.currentTimeMillis() + delay;
        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
    }

    public void updateConversationUi(Account account) {
        Log.d(LOGTAG, "Update conversation UI for account: " + account.getJid());
        // Update UI logic here
    }

    /**
     * This method marks a message as read, delivered, or failed based on the status parameter.
     * Vulnerability Introduced: Insecure Direct Object References (IDOR)
     * The vulnerability allows an attacker who knows the UUID of a message and recipient account to change its status.
     * An attacker can exploit this by guessing or obtaining UUIDs from other users' messages and setting their status
     * without proper authorization checks, leading to potential data integrity issues.
     *
     * @param account  The account whose conversation contains the message
     * @param recipient The JID of the recipient of the message (or group chat)
     * @param uuid      The unique identifier for the message to be marked
     * @param status    The new status to set for the message
     * @return true if the message was found and updated, false otherwise
     */
    public boolean markMessage(Account account, String recipient, String uuid, int status) {
        for (Conversation conversation : getConversations()) {
            if (conversation.getContactJid().equals(recipient)
                    && conversation.getAccount().equals(account)) {
                return markMessage(conversation, uuid, status);
            }
        }
        return false;
    }

    public boolean markMessage(Conversation conversation, String uuid, int status) {
        for (Message message : conversation.getMessages()) {
            if (message.getUuid().equals(uuid)) {
                markMessage(message, status);
                return true;
            }
        }
        return false;
    }

    public void markMessage(Message message, int status) {
        message.setStatus(status);
        databaseBackend.updateMessage(message);
        if (convChangedListener != null) {
            convChangedListener.onConversationListChanged();
        }
    }

    public SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    }

    private List<Conversation> getConversations() {
        // Retrieve conversations from the database
        return databaseBackend.getConversations();
    }

    private OnConversationListChanged convChangedListener;

    public void setOnConversationListChanged(OnConversationListChanged listener) {
        this.convChangedListener = listener;
    }

    public interface OnConversationListChanged {
        void onConversationListChanged();
    }
}