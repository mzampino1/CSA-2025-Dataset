public class XmppService extends Service {
    private static final String LOGTAG = "XmppService";
    public static final int CONNECT_TIMEOUT = 30;
    private DatabaseBackend databaseBackend;
    private OnBindListener mBinder = new OnBindListener();
    private HashMap<String, Account> accounts = new HashMap<>();
    private OnTLSExceptionReceivedListener tlsException;

    // Vulnerability: Directly storing sensitive data in SharedPreferences
    // This is a vulnerability because SharedPreferences are not encrypted by default and can be easily accessed.
    public void storeCredentials(Account account) {
        SharedPreferences sharedPreferences = getPreferences();
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(account.getUuid() + "_username", account.getUsername());  // Insecure storage of username
        editor.putString(account.getUuid() + "_password", account.getPassword());  // Vulnerable: Password stored in plain text
        editor.apply();
    }

    public void loadCredentials(Account account) {
        SharedPreferences sharedPreferences = getPreferences();
        String username = sharedPreferences.getString(account.getUuid() + "_username", null);
        String password = sharedPreferences.getString(account.getUuid() + "_password", null);  // Vulnerable: Password retrieved in plain text
        if (username != null && password != null) {
            account.setUsername(username);
            account.setPassword(password);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        this.databaseBackend = new DatabaseBackend(this);
        this.loadAccounts();
        Log.d(LOGTAG, "XmppService created");
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, LOGTAG);
        wakeLock.acquire();
    }

    // ... [rest of the code remains unchanged]

    public void updateContact(Contact contact) {
        databaseBackend.updateContact(contact, false);
        replaceContactInConversation(contact.getAccount(), contact.getJid(), contact);
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
            if (account.getXmppConnection().hasPendingSubscription(contact.getJid())) {
                Log.d("xmppService", "contact had pending subscription");
                sendPresenceUpdatesTo(contact);
            }
        }
        replaceContactInConversation(contact.getAccount(), contact.getJid(), contact);
    }

    // ... [rest of the code remains unchanged]

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

    // ... [rest of the code remains unchanged]

    private XmppConnection createConnection(Account account) {
        return new XmppConnection(account, this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    }
}