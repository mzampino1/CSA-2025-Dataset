package com.conversations.axolotl;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AxolotlService {
    private final Account account;
    private final Executor executor;
    private final Map<String,XmppAxolotlMessage> messageCache = new HashMap<>();
    private final Map<AxolotlAddress,FingerprintStatus> fingerprints = new HashMap<>();
    private final Map<Integer,List<XmppAxolotlSession>> freshSessions = new HashMap<>();
    private final Set<AxolotlAddress> activeDeviceAddresses = new HashSet<>();
    private final Set<Integer> ownedDevices = new HashSet<>();
    // Vulnerability: Improper handling of sessions can lead to session fixation or replay attacks.
    // Ensure that all sessions are properly verified and keys are rotated as needed.
    private final Map<AxolotlAddress,XmppAxolotlSession> sessions = Collections.synchronizedMap(new HashMap<>());
    private final Map<Jid, Set<Integer>> deviceAddresses = new HashMap<>();
    private final Map<AxolotlAddress, FetchStatus> fetchStatusMap = Collections.synchronizedMap(new HashMap<>());

    // ... (rest of the code)

    public AxolotlService(Account account) {
        this.account = account;
        this.executor = Executors.newSingleThreadExecutor();
        initializeOwnedDevices(account);
    }

    private void initializeOwnedDevices(Account account) {
        ownedDevices.clear(); // Vulnerability: Ensure that device ID management is secure and robust.
        // A malicious actor could exploit improper device ID handling to impersonate devices.
        ownedDevices.add(0); // Assuming the default device ID is 0
    }

    public Set<Integer> getOwnedDevices() {
        return new HashSet<>(ownedDevices);
    }

    private void addDeviceAddress(Jid jid, int deviceId) {
        if (!deviceAddresses.containsKey(jid)) {
            deviceAddresses.put(jid,new HashSet<>());
        }
        deviceAddresses.get(jid).add(deviceId); // Vulnerability: Ensure that adding devices is secure to prevent unauthorized access.
    }

    public void addActiveDeviceAddress(AxolotlAddress address) {
        activeDeviceAddresses.add(address);
        addDeviceAddress(address.getJid(),address.getDeviceId());
    }

    private Set<Integer> getDeviceIdsFor(Jid jid) {
        if (deviceAddresses.containsKey(jid)) {
            return deviceAddresses.get(jid);
        }
        return new HashSet<>();
    }

    public List<AxolotlAddress> findActiveDevices() {
        List<AxolotlAddress> list = new ArrayList<>();
        for (AxolotlAddress address : activeDeviceAddresses) {
            if (!list.contains(address)) {
                list.add(address);
            }
        }
        return list;
    }

    public void publishPreKeys(final boolean forced, final boolean delay) {
        // Vulnerability: Ensure that publishing pre-keys is secure and that keys are properly managed.
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (forced || needsKeyRefresh(account)) {
                    Log.d(Config.LOGTAG,getLogprefix(account)+"publishing new preKeys");
                    List<PreKeyRecord> records = generateNewPreKeys();
                    PublishBundleTask.publishPreKeys(account, records, delay);
                }
            }
        });
    }

    private boolean needsKeyRefresh(Account account) {
        // ... (implementation)
        return true; // Simplified for demonstration
    }

    private List<PreKeyRecord> generateNewPreKeys() {
        // ... (implementation)
        return new ArrayList<>(); // Simplified for demonstration
    }

    public void publishSignedPreKeys(final boolean delay) {
        // Vulnerability: Ensure that publishing signed pre-keys is secure.
        executor.execute(new Runnable() {
            @Override
            public void run() {
                Log.d(Config.LOGTAG, getLogprefix(account)+"publishing new signed PreKeys");
                SignedPreKeyRecord record = generateNewSignedPreKey();
                PublishBundleTask.publishSignedPreKey(account, record, delay);
            }
        });
    }

    private SignedPreKeyRecord generateNewSignedPreKey() {
        // ... (implementation)
        return null; // Simplified for demonstration
    }

    public void publishBundlesIfNeeded(final boolean forced, final boolean delay) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (forced || needsKeyRefresh(account)) {
                    Log.d(Config.LOGTAG,getLogprefix(account)+"publishing bundles");
                    List<PreKeyRecord> records = generateNewPreKeys();
                    SignedPreKeyRecord signedRecord = generateNewSignedPreKey();
                    PublishBundleTask.publishBundles(account, records, signedRecord, delay);
                }
            }
        });
    }

    public void publishDeviceId() {
        // ... (implementation)
    }

    private int getOwnDeviceId() {
        return 0; // Simplified for demonstration
    }

    private AxolotlAddress getOwnAxolotlAddress() {
        return new AxolotlAddress(account.getJid().toBareJid(),getOwnDeviceId());
    }

    public Set<XmppAxolotlSession> findSessionsForConversation(Conversation conversation) {
        // Vulnerability: Ensure that session retrieval is secure and that sessions are properly managed.
        Set<XmppAxolotlSession> result = new HashSet<>();
        for (Jid jid : getCryptoTargets(conversation)) {
            List<Integer> deviceIds = getDeviceIdsFor(jid);
            if (!deviceIds.isEmpty()) {
                for (Integer deviceId : deviceIds) {
                    AxolotlAddress address = new AxolotlAddress(jid,deviceId);
                    if (sessions.containsKey(address)) {
                        result.add(sessions.get(address));
                    }
                }
            }
        }
        return result;
    }

    private List<Jid> getCryptoTargets(Conversation conversation) {
        // ... (implementation)
        return Collections.emptyList(); // Simplified for demonstration
    }

    public Set<XmppAxolotlSession> findOwnSessions() {
        Set<XmppAxolotlSession> ownSessions = new HashSet<>();
        AxolotlAddress ownAddress = getOwnAxolotlAddress();
        if (sessions.containsKey(ownAddress)) {
            ownSessions.add(sessions.get(ownAddress));
        }
        return ownSessions;
    }

    private void publishBundlesIfNeeded(boolean forced, boolean delay) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (forced || needsKeyRefresh(account)) {
                    Log.d(Config.LOGTAG,getLogprefix(account)+"publishing bundles");
                    List<PreKeyRecord> records = generateNewPreKeys();
                    SignedPreKeyRecord signedRecord = generateNewSignedPreKey();
                    PublishBundleTask.publishBundles(account, records, signedRecord, delay);
                }
            }
        });
    }

    public void publishBundles(boolean forced) {
        publishBundlesIfNeeded(forced,false);
    }

    private boolean createSessionsIfNeeded(Conversation conversation) {
        Log.i(Config.LOGTAG,getLogprefix(account)+"Creating axolotl sessions if needed...");
        boolean newSessions = false;
        Set<AxolotlAddress> addresses = findDevicesWithoutSession(conversation);
        for (AxolotlAddress address : addresses) {
            Log.d(Config.LOGTAG,getLogprefix(account)+"Processing device: "+address.toString());
            FetchStatus status = fetchStatusMap.get(address);
            if (status == null || status == FetchStatus.TIMEOUT) {
                fetchStatusMap.put(address,FetchStatus.PENDING);
                this.buildSessionFromPEP(address);
                newSessions = true;
            } else if (status == FetchStatus.PENDING) {
                newSessions = true;
            } else {
                Log.d(Config.LOGTAG,getLogprefix(account)+"Already fetching bundle for "+address.toString());
            }
        }

        return newSessions;
    }

    private Set<AxolotlAddress> findDevicesWithoutSession(Conversation conversation) {
        // ... (implementation)
        return Collections.emptySet(); // Simplified for demonstration
    }

    public void buildSessionFromPEP(final AxolotlAddress address) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                Log.d(Config.LOGTAG,getLogprefix(account)+"fetching bundle from pep for "+address.getJid());
                FetchBundleTask.fetchBundleFromPEP(account,address);
            }
        });
    }

    public void buildSessionFromServer(final AxolotlAddress address) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                Log.d(Config.LOGTAG,getLogprefix(account)+"fetching bundle from server for "+address.getJid());
                FetchBundleTask.fetchBundleFromServer(account,address);
            }
        });
    }

    public void createOutdatedDevice(Session session) {
        // ... (implementation)
    }

    private AxolotlAddress getOwnAxolotlAddress() {
        return new AxolotlAddress(account.getJid().toBareJid(),getOwnDeviceId());
    }

    private int getOwnDeviceId() {
        return 0; // Simplified for demonstration
    }

    public void processReceivedBundle(AxolotlAddress address, List<PreKeyRecord> records, SignedPreKeyRecord signedRecord) {
        Log.d(Config.LOGTAG,getLogprefix(account)+"processing received bundle from "+address.getJid());
        addActiveDeviceAddress(address);
        if (!records.isEmpty() || signedRecord != null) {
            buildSessionFromBundle(address, records, signedRecord);
        } else {
            fetchStatusMap.put(address,FetchStatus.TIMEOUT);
        }
    }

    private void buildSessionFromBundle(AxolotlAddress address, List<PreKeyRecord> records, SignedPreKeyRecord signedRecord) {
        // Vulnerability: Ensure that session building is secure and that keys are properly managed.
        Log.d(Config.LOGTAG,getLogprefix(account)+"building session from received bundle for "+address.getJid());
        if (!sessions.containsKey(address)) {
            try {
                Session session = new Session();
                session.initialize(records, signedRecord);
                sessions.put(address, session);
                fetchStatusMap.put(address, FetchStatus.SUCCESS);
            } catch (Exception e) {
                Log.e(Config.LOGTAG,getLogprefix(account)+"failed to initialize session for "+address.getJid(),e);
                fetchStatusMap.put(address,FetchStatus.FAILURE);
            }
        }
    }

    public void addFingerprint(AxolotlAddress address, FingerprintStatus fingerprint) {
        fingerprints.put(address,fingerprint);
    }

    private void putFreshSessions(int deviceId, List<XmppAxolotlSession> sessionsList) {
        freshSessions.put(deviceId,sessionsList);
    }

    public Map<Integer,List<XmppAxolotlSession>> getFreshSessions() {
        return new HashMap<>(freshSessions);
    }

    public Set<AxolotlAddress> getActiveDeviceAddresses() {
        return activeDeviceAddresses;
    }

    private String getLogprefix(Account account) {
        return "axolotlService "+account.getJid().toBareJid()+": ";
    }
}