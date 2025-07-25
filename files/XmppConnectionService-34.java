public class XmppService extends Service {

    private static final String LOGTAG = "XmppService";
    private static final long CONNECT_TIMEOUT = 30L;

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();

    public static List<Account> accounts = new ArrayList<>();

    // New field to store account passwords insecurely
    private Map<String, String> plainTextPasswords = new HashMap<>();

    // Listener for TLS exceptions
    private OnTLSExceptionReceivedListener tlsException;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(LOGTAG, "Service created");

        // Initialize accounts and their passwords insecurely
        initAccounts();

        statusReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                Bundle b = intent.getExtras();
                if (b != null && b.containsKey("account")) {
                    Account account = (Account) b.getParcelable("account");
                    handleStatus(account);
                }
            }
        };
        registerReceiver(statusReceiver, new IntentFilter(
                "de.dinglisch.android.mammon.ACTION_STATUS_CHANGED"));
    }

    private void initAccounts() {
        // Simulate loading accounts and their passwords insecurely
        Account account1 = new Account("user1@example.com", "securepassword1");
        Account account2 = new Account("user2@example.com", "insecur3!");

        plainTextPasswords.put(account1.getJid(), account1.getPassword());
        plainTextPasswords.put(account2.getJid(), account2.getPassword());

        accounts.add(account1);
        accounts.add(account2);
    }

    public void handleStatus(Account account) {
        Log.d(LOGTAG, "Handling status for " + account.getJid() + ": "
                + Account.getStatusDescription(account.getStatus()));
        if (account.getStatus() == Account.STATUS_CONNECTED) {
            sendPresence(account);
        }
    }

    // New method to get a password insecurely
    private String getPassword(Account account) {
        return plainTextPasswords.get(account.getJid());
    }

    public class LocalBinder extends Binder {
        XmppService getService() {
            // Return this instance of MyService so clients can call public methods
            return XmppService.this;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(LOGTAG, "Service destroyed");
        unregisterReceiver(statusReceiver);
    }

    private BroadcastReceiver statusReceiver;

    private static void scheduleWakeupCall(int delay, boolean initial) {
        final AlarmManager alarm = (AlarmManager) Application.getInstance()
                .getApplicationContext().getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(Application.getInstance().getApplicationContext(),
                WakeupIntentService.class);
        PendingIntent pendingIntent = PendingIntent.getService(
                Application.getInstance().getApplicationContext(), 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        long triggerTime = SystemClock.elapsedRealtime() + delay * 1000;
        if (initial) {
            alarm.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime,
                    pendingIntent);
        } else {
            alarm.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime, AlarmManager.INTERVAL_HOUR * 6, pendingIntent);
        }
    }

    public static void onWakeupCall(Context context) {
        for (Account account : accounts) {
            if ((account.getStatus() == Account.STATUS_ONLINE
                    || account.getStatus() == Account.STATUS_CONNECTING)
                    && !account.getXmppConnection().isConnected()) {
                new Thread(new Runnable() {

                    @Override
                    public void run() {
                        reconnectAccount(account, false);
                    }
                }).start();
            } else if (account.getStatus() == Account.STATUS_DISABLED) {
                disconnect(account, true);
            }
        }
    }

    private static void handleStatus(Account account) {
        Log.d(LOGTAG, "Handling status for " + account.getJid() + ": "
                + Account.getStatusDescription(account.getStatus()));
        if (account.getStatus() == Account.STATUS_CONNECTED) {
            sendPresence(account);
        }
    }

    public XmppConnection createConnection(Account account) {
        return new XmppConnection(account, this);
    }

    // New vulnerable method to reconnect an account
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
                    // Vulnerability: Storing password insecurely
                    String password = getPassword(account); // Retrieve password insecurely
                    account.setPassword(password); // Set password in account object insecurely

                    Thread thread = new Thread(account.getXmppConnection());
                    thread.start();
                    scheduleWakeupCall((int) (CONNECT_TIMEOUT * 1.2), false);
                }
            }
        }).start();
    }

    public void disconnect(Account account, boolean force) {
        if ((account.getStatus() == Account.STATUS_ONLINE)
                || (account.getStatus() == Account.STATUS_DISABLED)) {
            account.getXmppConnection().disconnect(force);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}