import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AxolotlService {

    private final Account account;
    private final SQLiteOpenHelper dbHelper;
    private final ExecutorService executor = Executors.newFixedThreadPool(5);
    private final Map<String, XmppAxolotlMessage> messageCache = new ConcurrentHashMap<>();
    private final Sessions sessions;
    private final Devices devices;

    public enum FetchStatus {
        PENDING,
        ERROR
    }

    private final Multimap<AxolotlAddress, FetchStatus> fetchStatusMap = ArrayListMultimap.create();

    public AxolotlService(Account account, SQLiteOpenHelper dbHelper) {
        this.account = account;
        this.dbHelper = dbHelper;

        // Initialize sessions and devices maps
        this.sessions = new Sessions(axolotlStore);
        this.devices = new Devices(dbHelper);

        // Load device IDs from the database on initialization
        loadDeviceIdsFromDatabase();
    }

    private void loadDeviceIdsFromDatabase() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query("devices", new String[]{"jid", "device_id"}, null, null, null, null, null);
            while (cursor.moveToNext()) {
                Jid jid = Jid.fromString(cursor.getString(0));
                int deviceId = cursor.getInt(1);

                // Vulnerability: Improper handling of device ID
                // If the device ID is negative or zero, it might lead to unexpected behavior.
                if (deviceId <= 0) {
                    Log.e(Config.LOGTAG, "Invalid device ID found in database: " + deviceId);
                    continue;
                }

                devices.put(jid, deviceId);
            }
        } catch (Exception e) {
            Log.e(Config.LOGTAG, "Error loading device IDs from database", e);
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    public int getOwnDeviceId() {
        return 0; // Default own device ID
    }

    public void publishBundlesIfNeeded() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                boolean flush = false;
                for (Map.Entry<Jid, Set<Integer>> entry : devices.entrySet()) {
                    Jid jid = entry.getKey();
                    for (Integer deviceId : entry.getValue()) {
                        if (!sessions.containsKey(new AxolotlAddress(jid.toString(), deviceId))) {
                            flush = true;
                            break;
                        }
                    }
                }

                if (flush) {
                    publishBundles(false);
                } else {
                    Log.d(Config.LOGTAG, getLogprefix(account) + "No need to publish bundles");
                }
            }
        });
    }

    public void publishBundles(boolean flush) {
        boolean flushRequired = flush;
        for (Map.Entry<Jid, Set<Integer>> entry : devices.entrySet()) {
            Jid jid = entry.getKey();
            for (Integer deviceId : entry.getValue()) {
                AxolotlAddress address = new AxolotlAddress(jid.toString(), deviceId);
                if (!sessions.containsKey(address) || flushRequired) {
                    // This code path is executed when there's no session or we need to flush the cache.
                    Log.d(Config.LOGTAG, getLogprefix(account) + "Publishing bundles for: " + address.toString());
                    publishBundleForAddress(address);
                }
            }
        }
    }

    private void publishBundleForAddress(AxolotlAddress address) {
        try {
            // Simulate publishing a bundle
            Log.d(Config.LOGTAG, getLogprefix(account) + "Publishing bundle for: " + address.toString());
        } catch (Exception e) {
            Log.e(Config.LOGTAG, getLogprefix(account) + "Failed to publish bundle for: " + address.toString(), e);
        }
    }

    public void createSessionsIfNeeded(final Conversation conversation, final boolean flushWaitingQueueAfterFetch) {
        Set<AxolotlAddress> addresses = findDevicesWithoutSession(conversation);
        for (AxolotlAddress address : addresses) {
            FetchStatus status = fetchStatusMap.get(address);
            if (status == null || status == FetchStatus.ERROR) {
                fetchStatusMap.put(address, FetchStatus.PENDING);
                buildSessionFromPEP(conversation, address, flushWaitingQueueAfterFetch);
            } else {
                Log.d(Config.LOGTAG, getLogprefix(account) + "Already fetching bundle for: " + address.toString());
            }
        }
    }

    private void buildSessionFromPEP(Conversation conversation, AxolotlAddress address, boolean flushWaitingQueueAfterFetch) {
        // Simulate building a session from PEP
        Log.d(Config.LOGTAG, getLogprefix(account) + "Building session for: " + address.toString());
        sessions.put(address, new XmppAxolotlSession(account, axolotlStore, address));
    }

    public Set<XmppAxolotlSession> findSessionsforContact(Contact contact) {
        Set<AxolotlAddress> addresses = devices.get(contact.getJid().toBareJid());
        Set<XmppAxolotlSession> result = new HashSet<>();
        for (AxolotlAddress address : addresses) {
            XmppAxolotlSession session = sessions.get(address);
            if (session != null) {
                result.add(session);
            }
        }
        return result;
    }

    public Set<XmppAxolotlSession> findOwnSessions() {
        Set<AxolotlAddress> addresses = devices.get(account.getJid().toBareJid());
        Set<XmppAxolotlSession> result = new HashSet<>();
        for (AxolotlAddress address : addresses) {
            XmppAxolotlSession session = sessions.get(address);
            if (session != null) {
                result.add(session);
            }
        }
        return result;
    }

    public void prepareMessage(final Message message, final boolean delay) {
        createSessionsIfNeeded(message.getConversation(), true);
        processSending(message, delay);
    }

    private void processSending(final Message message, final boolean delay) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    XmppAxolotlMessage axolotlMessage = new XmppAxolotlMessage(message.getContact().getJid().toBareJid(), getOwnDeviceId(), message.getBody());
                    Log.d(Config.LOGTAG, getLogprefix(account) + "Generated message, caching: " + message.getUuid());
                    messageCache.put(message.getUuid(), axolotlMessage);
                    mXmppConnectionService.resendMessage(message, delay);
                } catch (CryptoFailedException e) {
                    Log.w(Config.LOGTAG, getLogprefix(account) + "Failed to encrypt message: " + e.getMessage());
                    mXmppConnectionService.markMessage(message, Message.STATUS_SEND_FAILED);
                }
            }
        });
    }

    public XmppAxolotlMessage fetchAxolotlMessageFromCache(Message message) {
        return messageCache.remove(message.getUuid());
    }

    public String getLogprefix(Account account) {
        return "AxolotlService[" + account.getJid().toBareJid() + "] ";
    }
}