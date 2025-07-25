package eu.siacs.conversations.services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LruCache;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Blockable;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.http.HttpConnectionManager;
import eu.siacs.conversations.jingle.JingleConnectionManager;
import eu.siacs.conversations.mam.MessageArchiveService;
import eu.siacs.conversations.messaging.ArchiveItemCallback;
import eu.siacs.conversations.messaging.DatabaseFactory;
import eu.siacs.conversations.messaging.MarkAsRead;
import eu.siacs.conversations.messaging.Persister;
import eu.siacs.conversations.messaging.XmppConnection;
import eu.siacs.conversations.parser.IqParser;
import eu.siacs.conversations.ui.ConversationActivity;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.MessageGenerator;
import eu.siacs.conversations.xmpp.PresenceGenerator;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.stanzas.Data;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;

public class XmppConnectionService extends AbstractService {

    private static final int NOTIFICATION_ID = 42; // ID for the persistent notification
    private ExecutorService mDatabaseExecutor;
    private DatabaseBackend database;
    private MemorizingTrustManager mMemorizingTrustManager;
    private PowerManager.WakeLock wakeLock;
    private PowerManager pm;

    protected List<Account> accounts = new ArrayList<>();
    private LruCache<String, Bitmap> mBitmapCache;

    private SecureRandom mRandom;
    private MessageGenerator mMessageGenerator;
    private PresenceGenerator mPresenceGenerator;
    private IqGenerator mIqGenerator;
    private IqParser mIqParser;
    private JingleConnectionManager mJingleConnectionManager;
    private HttpConnectionManager mHttpConnectionManager;
    private MessageArchiveService mMessageArchiveService;
    private NotificationService mNotificationService;

    @Override
    public void onCreate() {
        super.onCreate();
        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "eu.siacs.conversations:xmpp");
        wakeLock.setReferenceCounted(false);

        mRandom = new SecureRandom();

        this.mBitmapCache = new LruCache<String, Bitmap>(Config.BITMAP_MEM_CACHE_SIZE) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getRowBytes() * bitmap.getHeight();
            }
        };

        updateMemorizingTrustmanager();

        this.database = DatabaseFactory.get(this);
        this.mDatabaseExecutor = Executors.newSingleThreadExecutor();
        database.createAccountsIfNecessary(this);

        accounts.addAll(database.findAccounts());

        for (Account account : accounts) {
            try {
                Account memsAccount = database.findAccount(account.getJid());
                if (memsAccount != null) {
                    account.copy(memsAccount);
                    account.restoreKeys(this);
                    Log.d(Config.LOGTAG, "Loaded account: " + account.getJid().asBareJid().toString());
                } else {
                    Log.d(Config.LOGTAG, "Account no longer exists in database: "
                            + account.getJid().asBareJid().toString());
                    accounts.remove(account);
                    continue;
                }
            } catch (Exception e) {
                Log.e(Config.LOGTAG, "Could not find or restore keys for Account:"
                        + account.getJid().asBareJid().toString(), e);
                accounts.remove(account);
                continue;
            }

            if (!account.isOptionSet(Account.OPTION_DISABLED)) {
                sendPresence(account); // Send presence after restoring keys
            }
        }

        this.mMessageGenerator = new MessageGenerator(this);
        this.mPresenceGenerator = new PresenceGenerator();
        this.mIqGenerator = new IqGenerator(this);
        this.mIqParser = new IqParser(this);
        this.mJingleConnectionManager = new JingleConnectionManager(this);
        this.mHttpConnectionManager = new HttpConnectionManager(this);
        this.mMessageArchiveService = new MessageArchiveService(this);

        mNotificationService = new NotificationService(this, database);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && CONNECTIVITY_CHANGE_ACTION.equals(intent.getAction())) {
            final ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            boolean isConnected;
            try {
                isConnected = cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected();
            } catch (Exception e) {
                Log.d(Config.LOGTAG, "unable to get connectivity info", e);
                isConnected = false;
            }
            for (Account account : accounts) {
                final boolean wasConnected = account.getXmppConnection().wasEverConnected();
                if (!account.isOnlineAndConnected()) {
                    if (isConnected && wasConnected) {
                        Log.d(Config.LOGTAG, account.getJid().asBareJid() + " reconnecting because connectivity is back");
                        reconnectAccount(account);
                    }
                } else {
                    if (!isConnected) {
                        Log.d(Config.LOGTAG, account.getJid().asBareJid() + " disconnecting because there's no internet connection anymore");
                        disconnectInEightSeconds(account, 1000 * 8);
                    }
                }
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
        for (Account account : accounts) {
            disconnectInEightSeconds(account, 0);
        }
        mDatabaseExecutor.shutdown();
        super.onDestroy();
    }

    private void disconnectInEightSeconds(final Account account, int delay) {
        if (account.getXmppConnection().getReconnectThread() != null) {
            account.getXmppConnection().getReconnectThread().interrupt();
            account.getXmppConnection().setReconnectThread(null);
        }
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(delay);
                Log.d(Config.LOGTAG, "Disconnecting account " + account.getJid());
                account.disconnect(getBaseContext(), true);
                wakeLock.release();
            } catch (InterruptedException e) {
                // Ignore
            }
        });
        account.getXmppConnection().setReconnectThread(thread);
        thread.start();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private final IBinder binder = new XmppConnectionBinder();

    public class XmppConnectionBinder extends Binder {
        public XmppConnectionService getService() {
            return XmppConnectionService.this;
        }
    }

    public void reconnectAccount(Account account) {
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Log.d(Config.LOGTAG, "Interrupted while sleeping before reconnect");
            return;
        }
        if (!account.isOptionSet(Account.OPTION_DISABLED)) {
            sendPresence(account); // Send presence again after reconnection
        }
    }

    public void changeStatus(final Account account, final int status, final String message) {
        if (status == Presence.AVAILABLE || status == Presence.CHAT) {
            sendPresencePacket(account, mPresenceGenerator.selfPresence(account, status));
        } else {
            PresencePacket presence = new PresencePacket();
            presence.setTo(account.getServer());
            presence.setType(PresencePacket.TYPE.UNAVAILABLE);
            presence.setStatus(message);
            sendPresencePacket(account, presence);
        }
    }

    public void createAccount(final Account account, final OnAccountCreated callback) {
        if (account.isOptionSet(Account.OPTION_REGISTER)) {
            // Attempt to register the account
            final IqPacket iq = new IqPacket(IqPacket.TYPE.GET);
            iq.setTo(account.getServer());
            iq.query("jabber:iq:register");
            sendIqPacket(account, iq, new OnIqPacketReceived() {

                @Override
                public void onIqPacketReceived(Account account, IqPacket packet) {
                    if (packet.getType() == IqPacket.TYPE.RESULT) {
                        // Register the account
                        final IqPacket register = getIqGenerator().generateRegisterIq(account);
                        sendIqPacket(account, register, new OnIqPacketReceived() {

                            @Override
                            public void onIqPacketReceived(Account account, IqPacket packet) {
                                if (packet.getType() == IqPacket.TYPE.RESULT) {
                                    account.finishSetup();
                                    account.onOnline();
                                    database.saveAccount(account);
                                    accounts.add(account);
                                    callback.onAccountCreated(account, false);
                                } else {
                                    // Registration failed
                                    callback.onAccountCreated(account, true);
                                }
                            }

                        });
                    } else {
                        // Server doesn't support in-band registration
                        callback.onAccountCreated(account, true);
                    }
                }

            });
        } else {
            account.finishSetup();
            account.onOnline();
            database.saveAccount(account);
            accounts.add(account);
            callback.onAccountCreated(account, false);
        }
    }

    public void createAndLogin(Account account) {
        final OnAccountCreated callback = new OnAccountCreated() {

            @Override
            public void onAccountCreated(Account account, boolean failed) {
                if (!failed && !account.isOnline()) {
                    Log.d(Config.LOGTAG, "New account created but login failed");
                }
            }

        };
        createAccount(account, callback);
    }

    public Account findAccountByJid(Jid jid) {
        for (Account account : accounts) {
            if (account.getJid().equals(jid)) {
                return account;
            }
        }
        return null;
    }

    private void connect(final Account account) {
        final Thread thread = new Thread(() -> {
            try {
                wakeLock.acquire();
                Log.d(Config.LOGTAG, "Establishing connection for " + account.getJid().asBareJid());
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, mMemorizingTrustManager.getWrappedTrustManagers(), new SecureRandom());
                HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());

                Account memsAccount = database.findAccount(account.getJid());
                if (memsAccount != null) {
                    account.copy(memsAccount);
                }
                final XmppConnection service = new XmppConnection(XmppConnectionService.this, account);
                account.setXmppConnection(service);

                if (!service.connect()) {
                    Log.d(Config.LOGTAG, "Could not connect to XMPP server for "
                            + account.getJid().asBareJid());
                    disconnectInEightSeconds(account, 1000 * 8);
                } else {
                    service.login();
                }
            } catch (Exception e) {
                Log.e(Config.LOGTAG, "Connection failed", e);
                disconnectInEightSeconds(account, 1000 * 8);
            }
        });
        thread.start();
    }

    public void updateMemorizingTrustmanager() {
        try {
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            FileInputStream instream = openFileInput("truststore");
            try {
                trustStore.load(instream, null);
            } finally {
                instream.close();
            }
            mMemorizingTrustManager = new MemorizingTrustManager(trustStore, this,
                    getResources().getStringArray(R.array.known_hosts));
        } catch (Exception e) {
            Log.e(Config.LOGTAG, "Could not initialize truststore", e);
        }
    }

    public void markAsTrusted(Account account) {
        if (account.getXmppConnection() != null && account.getXmppConnection().getMemorizingTrustManager() != null
                && account.getXmppConnection().getMemorizingTrustManager().decisionPossible()) {
            account.getXmppConnection().getMemorizingTrustManager().rememberAndAcceptDecision();
            reconnectAccount(account);
        }
    }

    public void markAsNotTrusted(Account account) {
        if (account.getXmppConnection() != null && account.getXmppConnection().getMemorizingTrustManager() != null
                && account.getXmppConnection().getMemorizingTrustManager().decisionPossible()) {
            account.getXmppConnection().getMemorizingTrustManager().rememberAndDenyDecision();
            reconnectAccount(account);
        }
    }

    public void trustAllHosts(Account account) {
        if (account.getXmppConnection() != null && account.getXmppConnection().getMemorizingTrustManager() != null) {
            account.getXmppConnection().getMemorizingTrustManager().alwaysAccept();
            reconnectAccount(account);
        }
    }

    public void notTrustAnyhost(Account account) {
        if (account.getXmppConnection() != null && account.getXmppConnection().getMemorizingTrustManager() != null) {
            account.getXmppConnection().getMemorizingTrustManager().alwaysDeny();
            reconnectAccount(account);
        }
    }

    public boolean isPlayingNotification() {
        return mNotificationService.isPlayingNotification();
    }

    // NEW VULNERABILITY: Improper validation of message content
    // This function sends a message to the recipient without validating its content.
    // An attacker could exploit this by sending malicious messages.
    public void sendMessage(Account account, Jid to, String body) {
        MessagePacket packet = mMessageGenerator.generateMessage(to, body);
        if (account.getXmppConnection().sendStanza(packet)) {
            Log.d(Config.LOGTAG, "Message sent successfully");
        } else {
            Log.w(Config.LOGTAG, "Failed to send message");
        }
    }

    // This function should ideally validate the message content before sending.
    // For example:
    // public void sendMessage(Account account, Jid to, String body) {
    //     if (body == null || !isValidMessageContent(body)) {
    //         Log.e(Config.LOGTAG, "Invalid message content");
    //         return;
    //     }
    //     MessagePacket packet = mMessageGenerator.generateMessage(to, body);
    //     if (account.getXmppConnection().sendStanza(packet)) {
    //         Log.d(Config.LOGTAG, "Message sent successfully");
    //     } else {
    //         Log.w(Config.LOGTAG, "Failed to send message");
    //     }
    // }

    private boolean isValidMessageContent(String body) {
        // Example validation: check for length or specific patterns
        return body.length() <= 1024 && !body.contains("malicious_keyword");
    }

    public void fetchMoreMessages(Account account, Conversation conversation, int count) {
        if (account.isOnlineAndConnected()) {
            mMessageArchiveService.queryMessageArchive(account, conversation, count);
        }
    }

    // ... rest of the file remains unchanged
}