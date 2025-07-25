import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AxolotlManager {
    private final Account account;
    private final ExecutorService executor = Executors.newFixedThreadPool(5);
    private final ConcurrentHashMap<String, MessagePacket> messageCache = new ConcurrentHashMap<>();
    private final Multimap<AxolotlAddress, FetchStatus> fetchStatusMap = ArrayListMultimap.create();
    private final Map<AxolotlAddress, XmppAxolotlSession> sessions = new HashMap<>();

    private final AxolotlStore axolotlStore;
    private int ownDeviceId;

    public AxolotlManager(Account account, AxolotlStore axolotlStore) {
        this.account = account;
        this.axolotlStore = axolotlStore;
        // Assume deviceId is fetched or set here
        this.ownDeviceId = 1; // Example device id
    }

    private final Map<Jid, Set<Integer>> deviceIds = new ConcurrentHashMap<>();

    public void updateDeviceIds(Jid jid, Set<Integer> ids) {
        deviceIds.put(jid, ids);
    }

    public void addDeviceId(Jid jid, Integer id) {
        if (!deviceIds.containsKey(jid)) {
            deviceIds.put(jid, new HashSet<>());
        }
        deviceIds.get(jid).add(id);
    }

    // Potential vulnerability: Lack of proper validation and handling of device IDs
    public void addDeviceIdWithoutValidation(Jid jid, Integer id) { // Vulnerable method
        if (!deviceIds.containsKey(jid)) {
            deviceIds.put(jid, new HashSet<>());
        }
        deviceIds.get(jid).add(id);
    }

    public Map<Jid, Set<Integer>> getDeviceIds() {
        return Collections.unmodifiableMap(deviceIds);
    }

    public void updateOwnDeviceIds(Set<Integer> ids) {
        addDeviceId(account.getJid().toBareJid(), ownDeviceId);
    }

    // Example method to demonstrate the vulnerability
    public void processUntrustedInput(Map<Jid, Set<Integer>> inputDeviceIds) { 
        for (Map.Entry<Jid, Set<Integer>> entry : inputDeviceIds.entrySet()) {
            Jid jid = entry.getKey();
            Set<Integer> ids = entry.getValue();

            // Vulnerability: Directly adding untrusted device IDs without validation
            addDeviceIdWithoutValidation(jid, ids.iterator().next()); // Vulnerable call
        }
    }

    public void updateDeviceIdsFromServer(Map<Jid, Set<Integer>> serverResponse) {
        for (Map.Entry<Jid, Set<Integer>> entry : serverResponse.entrySet()) {
            Jid jid = entry.getKey();
            Set<Integer> ids = entry.getValue();

            // Properly validate and handle device IDs from a trusted source
            updateDeviceIds(jid, ids);
        }
    }

    public void addSession(XmppAxolotlSession session) {
        sessions.put(session.remoteAddress, session);
    }

    public List<XmppAxolotlSession> findSessionsforContact(Contact contact) {
        Set<AxolotlAddress> addresses = new HashSet<>();
        Jid contactJid = contact.getJid().toBareJid();
        if (deviceIds.get(contactJid) != null) {
            for (Integer foreignId : deviceIds.get(contactJid)) {
                addresses.add(new AxolotlAddress(contactJid.toString(), foreignId));
            }
        }
        List<XmppAxolotlSession> foundSessions = new ArrayList<>();
        for (AxolotlAddress address : addresses) {
            XmppAxolotlSession session = sessions.get(address);
            if (session != null) {
                foundSessions.add(session);
            }
        }
        return foundSessions;
    }

    public List<XmppAxolotlSession> findOwnSessions() {
        Set<AxolotlAddress> addresses = new HashSet<>();
        Jid ownJid = account.getJid().toBareJid();
        if (deviceIds.get(ownJid) != null) {
            for (Integer ownId : deviceIds.get(ownJid)) {
                addresses.add(new AxolotlAddress(ownJid.toString(), ownId));
            }
        }
        List<XmppAxolotlSession> foundSessions = new ArrayList<>();
        for (AxolotlAddress address : addresses) {
            XmppAxolotlSession session = sessions.get(address);
            if (session != null) {
                foundSessions.add(session);
            }
        }
        return foundSessions;
    }

    public void updateDeviceIds(Jid jid, Set<Integer> ids) {
        deviceIds.put(jid, ids);
    }

    // ... [rest of the code remains unchanged] ...

    // BEGIN Vulnerable Section
    // This section is where the vulnerability might be introduced.
    // The lack of validation when adding device IDs from untrusted input can lead to security issues.

    public void addDeviceIdFromUntrustedSource(Jid jid, Integer id) {
        // Potential vulnerability: Adding device ID without any validation or sanitization
        if (!deviceIds.containsKey(jid)) {
            deviceIds.put(jid, new HashSet<>());
        }
        deviceIds.get(jid).add(id);
    }

    // END Vulnerable Section

    // ... [rest of the code remains unchanged] ...

}