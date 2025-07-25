import java.util.*;
import java.security.InvalidKeyException;
import java.security.UntrustedIdentityException;

public class XmppAxolotlService {

    private final Account account;
    private final SQLiteStore axolotlStore;
    private final Map<AxolotlAddress, FetchStatus> fetchStatusMap = new HashMap<>();
    private final Map<AxolotlAddress, XmppAxolotlSession> sessions = new HashMap<>();
    private final Map<String, MessagePacket> messageCache = new ConcurrentHashMap<>();
    private final ExecutorService executor;

    public enum FetchStatus {
        PENDING,
        SUCCESS,
        ERROR
    }

    // ... rest of the class definition ...

    @Nullable
    public XmppAxolotlMessage encrypt(Message message) {
        final XmppAxolotlMessage axolotlMessage = new XmppAxolotlMessage(message.getContact().getJid().toBareJid(),
                ownDeviceId, message.getBody());

        if (findSessionsforContact(message.getContact()).isEmpty()) {
            return null;
        }
        Log.d(Config.LOGTAG, "Building axolotl foreign headers...");
        for (XmppAxolotlSession session : findSessionsforContact(message.getContact())) {
            Log.d(Config.LOGTAG, session.remoteAddress.toString());
            //if(!session.isTrusted()) {
            // TODO: handle this properly
            //              continue;
            //        }
            axolotlMessage.addHeader(session.processSending(axolotlMessage.getInnerKey()));
        }
        Log.d(Config.LOGTAG, "Building axolotl own headers...");
        for (XmppAxolotlSession session : findOwnSessions()) {
            Log.d(Config.LOGTAG, session.remoteAddress.toString());
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
                if (packet == null) {
                    mXmppConnectionService.markMessage(message, Message.STATUS_SEND_FAILED);
                    //mXmppConnectionService.updateConversationUi();
                } else {
                    Log.d(Config.LOGTAG, "Generated message, caching: " + message.getUuid());
                    messageCache.put(message.getUuid(), packet);
                    mXmppConnectionService.resendMessage(message);
                }
            }
        });
    }

    public void prepareMessage(Message message) {
        if (!messageCache.containsKey(message.getUuid())) {
            boolean newSessions = createSessionsIfNeeded(message.getConversation());

            if (!newSessions) {
                this.processSending(message);
            }
        }
    }

    public MessagePacket fetchPacketFromCache(Message message) {
        MessagePacket packet = messageCache.get(message.getUuid());
        if (packet != null) {
            Log.d(Config.LOGTAG, "Cache hit: " + message.getUuid());
            messageCache.remove(message.getUuid());
        } else {
            Log.d(Config.LOGTAG, "Cache miss: " + message.getUuid());
        }
        return packet;
    }

    public XmppAxolotlMessage.XmppAxolotlPlaintextMessage processReceiving(XmppAxolotlMessage message) {
        XmppAxolotlMessage.XmppAxolotlPlaintextMessage plaintextMessage = null;
        AxolotlAddress senderAddress = new AxolotlAddress(message.getFrom().toString(),
                message.getSenderDeviceId());

        // Vulnerability: No session validation or authentication is performed here.
        // An attacker could craft a message with the correct format but unauthorized content.
        // This could lead to unexpected behavior or data injection.
        //
        // Secure Implementation should check if the session exists and is trusted.
        XmppAxolotlSession session = sessions.get(senderAddress);
        if (session == null) {
            Log.d(Config.LOGTAG, "Account: "+account.getJid()+" No axolotl session found while parsing received message " + message);
            // TODO: handle this properly
            session = new XmppAxolotlSession(axolotlStore, senderAddress);
            sessions.put(senderAddress,session);
        }

        for (XmppAxolotlMessage.XmppAxolotlMessageHeader header : message.getHeaders()) {
            if (header.getRecipientDeviceId() == ownDeviceId) {
                Log.d(Config.LOGTAG, "Found axolotl header matching own device ID, processing...");
                byte[] payloadKey = session.processReceiving(header);
                if (payloadKey != null) {
                    Log.d(Config.LOGTAG, "Got payload key from axolotl header. Decrypting message...");
                    plaintextMessage = message.decrypt(session, payloadKey);
                }
                Integer preKeyId = session.getPreKeyId();
                if (preKeyId != null) {
                    publishBundlesIfNeeded();
                    session.resetPreKeyId();
                }
                break;
            }
        }

        return plaintextMessage;
    }
}