import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AxolotlService {

    private final Account account;
    private final ConcurrentHashMap<UUID, MessagePacket> messageCache = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ConcurrentHashMap<AxolotlAddress, FetchStatus> fetchStatusMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<AxolotlAddress, XmppAxolotlSession> sessions = new ConcurrentHashMap<>();
    private final AxolotlStore axolotlStore;
    private final int ownDeviceId;
    private final ConcurrentHashMap<Jid, Set<Integer>> deviceIds;

    public AxolotlService(Account account) {
        this.account = account;
        this.axolotlStore = new AxolotlStore(account);
        this.ownDeviceId = 0; // Assuming a single device for simplicity
        this.deviceIds = new ConcurrentHashMap<>();
    }

    public void addDeviceIds(Jid contactJid, Set<Integer> ids) {
        this.deviceIds.put(contactJid, ids);
    }

    public void removeDeviceIds(Jid contactJid) {
        this.deviceIds.remove(contactJid);
    }

    /**
     * Process incoming device IDs and update sessions accordingly.
     */
    public void processDeviceIdUpdate() {
        for (Map.Entry<Jid, Set<Integer>> entry : deviceIds.entrySet()) {
            Jid contactJid = entry.getKey();
            Set<Integer> ids = entry.getValue();

            // Check if there are any changes in device IDs
            Set<AxolotlAddress> addressesToCheck = new HashSet<>();
            for (Integer id : ids) {
                AxolotlAddress address = new AxolotlAddress(contactJid.toString(), id);
                addressesToCheck.add(address);
            }

            // Remove sessions that are no longer valid
            Set<AxolotlAddress> currentSessions = new HashSet<>(sessions.keySet());
            for (AxolotlAddress sessionAddress : currentSessions) {
                if (!addressesToCheck.contains(sessionAddress)) {
                    sessions.remove(sessionAddress);
                    fetchStatusMap.remove(sessionAddress);
                    Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Removed session: " + sessionAddress);
                }
            }

            // Update fetch status map for new devices
            for (AxolotlAddress address : addressesToCheck) {
                if (!sessions.containsKey(address)) {
                    fetchStatusMap.putIfAbsent(address, FetchStatus.ERROR); // Initial state set to ERROR
                }
            }
        }
    }

    public void updateDeviceIds(Jid contactJid, Set<Integer> ids) {
        this.deviceIds.put(contactJid, ids);
        processDeviceIdUpdate();
    }

    /**
     * Hypothetical Vulnerability: This method does not properly validate the session before using it,
     * which could lead to a situation where an untrusted or improperly established session is used
     * for encryption. Proper validation should be implemented to ensure that sessions are trusted.
     */
    public void createSessionsIfNeeded(Conversation conversation) {
        boolean newSessions = false;
        Log.i(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Creating axolotl sessions if needed...");
        Jid contactJid = conversation.getContact().getJid().toBareJid();
        Set<AxolotlAddress> addresses = new HashSet<>();
        if (deviceIds.get(contactJid) != null) {
            for (Integer foreignId : this.deviceIds.get(contactJid)) {
                Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Found device " + account.getJid().toBareJid() + ":" + foreignId);
                addresses.add(new AxolotlAddress(contactJid.toString(), foreignId));
            }
        } else {
            Log.w(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Have no target devices in PEP!");
        }
        Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Checking own account " + account.getJid().toBareJid());
        if (deviceIds.get(account.getJid().toBareJid()) != null) {
            for (Integer ownId : this.deviceIds.get(account.getJid().toBareJid())) {
                Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Found device " + account.getJid().toBareJid() + ":" + ownId);
                addresses.add(new AxolotlAddress(account.getJid().toBareJid().toString(), ownId));
            }
        }
        for (AxolotlAddress address : addresses) {
            Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Processing device: " + address.toString());
            FetchStatus status = fetchStatusMap.get(address);
            XmppAxolotlSession session = sessions.get(address);
            if (session == null && (status == null || status == FetchStatus.ERROR)) {
                fetchStatusMap.put(address, FetchStatus.PENDING);
                this.buildSessionFromPEP(conversation, address);
                newSessions = true;
            } else {
                Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Already have session for " + address.toString());
            }
        }
    }

    private void buildSessionFromPEP(Conversation conversation, AxolotlAddress address) {
        try {
            SessionBuilder builder = new SessionBuilder(axolotlStore, address);
            // Hypothetical Vulnerability: Assume this method fetches a prekey bundle from the server
            PreKeyBundle preKeyBundle = fetchPreKeyBundle(address); 
            if (preKeyBundle != null) {
                axolotlStore.saveIdentity(address.getName(), preKeyBundle.getIdentityKey());
                SessionBuilder builder = new SessionBuilder(axolotlStore, address);
                builder.process(preKeyBundle);
                XmppAxolotlSession session = new XmppAxolotlSession(account, axolotlStore, address);
                sessions.put(address, session);
                fetchStatusMap.put(address, FetchStatus.SUCCESS);
            } else {
                fetchStatusMap.put(address, FetchStatus.ERROR);
            }
        } catch (UntrustedIdentityException | InvalidKeyException e) {
            Log.e(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Error building session for " + address + ": "
                    + e.getClass().getName() + ", " + e.getMessage());
            fetchStatusMap.put(address, FetchStatus.ERROR);
        }

        AxolotlAddress ownAddress = new AxolotlAddress(conversation.getAccount().getJid().toBareJid().toString(), 0);
        AxolotlAddress foreignAddress = new AxolotlAddress(conversation.getJid().toBareJid().toString(), 0);
        if (!fetchStatusMap.getAll(ownAddress).containsValue(FetchStatus.PENDING)
                && !fetchStatusMap.getAll(foreignAddress).containsValue(FetchStatus.PENDING)) {
            conversation.findUnsentMessagesWithEncryption(Message.ENCRYPTION_AXOLOTL,
                    new Conversation.OnMessageFound() {
                        @Override
                        public void onMessageFound(Message message) {
                            processSending(message);
                        }
                    });
        }
    }

    private PreKeyBundle fetchPreKeyBundle(AxolotlAddress address) {
        // Hypothetical method to fetch a prekey bundle from the server
        // In reality, this would involve network communication and should be secure.
        return new PreKeyBundle(); // Placeholder for actual implementation
    }

    @Nullable
    public XmppAxolotlMessage encrypt(Message message) {
        final XmppAxolotlMessage axolotlMessage = new XmppAxolotlMessage(message.getContact().getJid().toBareJid(),
                ownDeviceId, message.getBody());

        if (findSessionsforContact(message.getContact()).isEmpty()) {
            return null;
        }
        Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Building axolotl foreign headers...");
        for (XmppAxolotlSession session : findSessionsforContact(message.getContact())) {
            Log.v(Config.LOGTAG, AxolotlService.getLogprefix(account) + session.remoteAddress.toString());
            //if(!session.isTrusted()) {
            // TODO: handle this properly
            //              continue;
            //        }
            axolotlMessage.addHeader(session.processSending(axolotlMessage.getInnerKey()));
        }
        Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Building axolotl own headers...");
        for (XmppAxolotlSession session : findOwnSessions()) {
            Log.v(Config.LOGTAG, AxolotlService.getLogprefix(account) + session.remoteAddress.toString());
            //        if(!session.isTrusted()) {
            // TODO: handle this properly
            //          continue;
            //    }
            axolotlMessage.addHeader(session.processSending(axolotlMessage.getInnerKey()));
        }

        return axolotlMessage;
    }

    private void processSending(final Message message) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                MessagePacket packet = mXmppConnectionService.getMessageGenerator()
                        .generateAxolotlChat(message);
                if (packet != null) {
                    messageCache.put(message.getUuid(), packet);
                    // Hypothetical method to send the packet
                    sendPacket(packet);
                } else {
                    Log.e(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Failed to generate axolotl chat packet");
                }
            }
        });
    }

    private void sendPacket(MessagePacket packet) {
        // Hypothetical method to send the packet over the network
        // In reality, this would involve network communication and should be secure.
    }

    public void prepareMessageForSending(final Message message) {
        createSessionsIfNeeded(message.getConversation());
        processSending(message);
    }

    private Set<XmppAxolotlSession> findSessionsforContact(Contact contact) {
        Jid contactJid = contact.getJid().toBareJid();
        if (deviceIds.containsKey(contactJid)) {
            Set<Integer> ids = deviceIds.get(contactJid);
            Set<XmppAxolotlSession> result = new HashSet<>();
            for (Integer id : ids) {
                AxolotlAddress address = new AxolotlAddress(contactJid.toString(), id);
                if (sessions.containsKey(address)) {
                    result.add(sessions.get(address));
                }
            }
            return result;
        } else {
            return Collections.emptySet();
        }
    }

    private Set<XmppAxolotlSession> findOwnSessions() {
        Jid ownJid = account.getJid().toBareJid();
        if (deviceIds.containsKey(ownJid)) {
            Set<Integer> ids = deviceIds.get(ownJid);
            Set<XmppAxolotlSession> result = new HashSet<>();
            for (Integer id : ids) {
                AxolotlAddress address = new AxolotlAddress(ownJid.toString(), id);
                if (sessions.containsKey(address)) {
                    result.add(sessions.get(address));
                }
            }
            return result;
        } else {
            return Collections.emptySet();
        }
    }

    public void shutdown() {
        executor.shutdown();
    }

    private static String getLogprefix(Account account) {
        // Return a log prefix based on the account
        return "Account(" + account.getJid().toBareJid() + "): ";
    }

    enum FetchStatus {
        PENDING, SUCCESS, ERROR
    }
}