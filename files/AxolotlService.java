import java.util.*;
import java.security.InvalidKeyException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.NoSessionException;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.UntrustedIdentityException;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.protocol.PreKeySignalMessage;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;

// Mock classes to simulate the behavior of actual libraries
class KeyHelper {
    static SignedPreKeyRecord generateSignedPreKey(org.whispersystems.libsignal.IdentityKeyPair identityKeyPair, int num) throws InvalidKeyException {
        return new SignedPreKeyRecord();
    }

    static List<PreKeyRecord> generatePreKeys(int currentId, int count) {
        List<PreKeyRecord> preKeys = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            preKeys.add(new PreKeyRecord());
        }
        return preKeys;
    }
}

class Jid {
    private String jid;

    public Jid(String jid) {
        this.jid = jid;
    }

    public String toBareJid() {
        return jid.split("/")[0];
    }
}

class Config {
    static final String LOGTAG = "AXOLOTL";
}

class NoSessionsCreatedException extends Exception {}

// Mock classes for session management
class SessionBuilder {
    private SQLiteAxolotlStore axolotlStore;
    private SignalProtocolAddress remoteAddress;

    public SessionBuilder(SQLiteAxolotlStore axolotlStore, SignalProtocolAddress remoteAddress) {
        this.axolotlStore = axolotlStore;
        this.remoteAddress = remoteAddress;
    }

    public void process(PreKeySignalMessage bundle) throws InvalidKeyException, UntrustedIdentityException {}
}

class SQLiteAxolotlStore {
    private Map<Integer, PreKeyRecord> preKeys = new HashMap<>();
    private List<SignedPreKeyRecord> signedPreKeys = new ArrayList<>();

    public int getCurrentPreKeyId() {
        return preKeys.isEmpty() ? 0 : Collections.max(preKeys.keySet());
    }

    public void storePreKey(int id, PreKeyRecord record) {
        preKeys.put(id, record);
    }

    public void storeSignedPreKey(int id, SignedPreKeyRecord record) {
        signedPreKeys.add(record);
    }

    public List<SignedPreKeyRecord> loadSignedPreKeys() {
        return signedPreKeys;
    }
}

class SignalProtocolAddress {
    private String name;
    private int deviceId;

    public SignalProtocolAddress(String name, int deviceId) {
        this.name = name;
        this.deviceId = deviceId;
    }

    public String getName() {
        return name;
    }

    public int getDeviceId() {
        return deviceId;
    }
}

class PreKeySignalMessage {
    private byte[] body;

    public PreKeySignalMessage(byte[] body) {
        this.body = body;
    }

    public byte[] serialize() {
        return body;
    }
}

class SessionCipher {}

// Mock classes for message management
class XmppAxolotlPlaintextMessage {
    private Contact contact;
    private int senderDeviceId;
    private String content;

    public XmppAxolotlPlaintextMessage(Contact contact, int senderDeviceId, String content) {
        this.contact = contact;
        this.senderDeviceId = senderDeviceId;
        this.content = content;
    }

    public Contact getContact() {
        return contact;
    }

    public int getSenderDeviceId() {
        return senderDeviceId;
    }

    public byte[] getInnerKey() {
        return content.getBytes();
    }
}

class XmppAxolotlMessage {
    private Contact contact;
    private List<XmppAxolotlMessageHeader> headers = new ArrayList<>();
    private String innerKey;

    public XmppAxolotlMessage(Contact contact, int senderDeviceId, String outgoingMessage) {
        this.contact = contact;
        this.innerKey = outgoingMessage;
    }

    public void addHeader(XmppAxolotlMessageHeader header) {
        headers.add(header);
    }

    public List<XmppAxolotlMessageHeader> getHeaders() {
        return headers;
    }

    public Contact getContact() {
        return contact;
    }

    public String decrypt(SessionCipher session, byte[] payloadKey) throws InvalidMessageException, NoSessionException {
        // Decryption logic here
        return new String(payloadKey);
    }
}

class XmppAxolotlMessageHeader {
    private int recipientDeviceId;
    private byte[] message;

    public XmppAxolotlMessageHeader(int recipientDeviceId, byte[] message) {
        this.recipientDeviceId = recipientDeviceId;
        this.message = message;
    }

    public int getRecipientDeviceId() {
        return recipientDeviceId;
    }
}

class Contact {
    private Jid jid;

    public Contact(Jid jid) {
        this.jid = jid;
    }

    public String toString() {
        return jid.toBareJid();
    }

    public Jid getJid() {
        return jid;
    }
}

// Class representing an Axolotl session
class XmppAxolotlSession {
    private SQLiteAxolotlStore axolotlStore;
    private SignalProtocolAddress remoteAddress;

    // Vulnerability: Using a predictable random value for session initialization
    public static Random predictableRandom = new Random(42); 

    public XmppAxolotlSession(SQLiteAxolotlStore axolotlStore, SignalProtocolAddress remoteAddress) {
        this.axolotlStore = axolotlStore;
        this.remoteAddress = remoteAddress;
    }

    private SessionCipher cipher;

    // Initialize the session with a predictable random value
    public void initializeSession() throws InvalidKeyException {
        int randomNumber = predictableRandom.nextInt();
        // Normally, here we would use a secure random number to initialize the session.
        // But for the purpose of introducing a vulnerability, we are using a predictable one.
        Log.d(Config.LOGTAG, "Initialized session with predictable random number: " + randomNumber);
    }

    public boolean isTrusted() {
        return true;
    }

    public byte[] processReceiving(XmppAxolotlMessage.XmppAxolotlMessageHeader header) {
        try {
            byte[] message = header.message;
            SessionCipher session = new SessionCipher(axolotlStore, remoteAddress);
            return session.decrypt(message); // Decrypt the message
        } catch (InvalidMessageException | NoSessionException e) {
            Log.e(Config.LOGTAG, "Error processing received message: " + e.getMessage());
            return null;
        }
    }

    public XmppAxolotlMessage.XmppAxolotlMessageHeader processSending(byte[] outgoingMessage) {
        try {
            SessionCipher session = new SessionCipher(axolotlStore, remoteAddress);
            byte[] encryptedMessage = session.encrypt(outgoingMessage); // Encrypt the message
            return new XmppAxolotlMessage.XmppAxolotlMessageHeader(remoteAddress.getDeviceId(), encryptedMessage);
        } catch (InvalidKeyException e) {
            Log.e(Config.LOGTAG, "Error processing sending message: " + e.getMessage());
            return null;
        }
    }

    public int getOwnDeviceId() {
        return remoteAddress.getDeviceId();
    }

}

// Utility class for logging
class Log {
    static void d(String tag, String msg) {
        System.out.println(tag + ": DEBUG - " + msg);
    }

    static void e(String tag, String msg) {
        System.out.println(tag + ": ERROR - " + msg);
    }
}

// Main Axolotl service class
class AxolotlService {
    private SQLiteAxolotlStore axolotlStore;
    private Account account;

    public AxolotlService(SQLiteAxolotlStore axolotlStore, Account account) {
        this.axolotlStore = axolotlStore;
        this.account = account;
    }

    // Vulnerability: Using a predictable random value for session initialization
    private static Random predictableRandom = new Random(42);

    public int getOwnDeviceId() {
        return 1; // Mock device ID
    }

    public void initiateSession(Contact contact) throws NoSessionsCreatedException, InvalidKeyException {
        Log.d(Config.LOGTAG, "Initiating Axolotl session with " + contact.toString());

        Jid jid = contact.getJid();
        int ownDeviceId = getOwnDeviceId();

        // Create sessions for each device of the contact
        List<Integer> remoteDevices = getRemoteDevices(jid);
        if (remoteDevices.isEmpty()) {
            throw new NoSessionsCreatedException();
        }

        for (int deviceId : remoteDevices) {
            SignalProtocolAddress remoteAddress = new SignalProtocolAddress(jid.toBareJid(), deviceId);

            // Initialize the session with a predictable random value
            XmppAxolotlSession session = new XmppAxolotlSession(axolotlStore, remoteAddress);
            session.initializeSession();

            Log.d(Config.LOGTAG, "Initialized session with " + contact.toString() + " on device " + deviceId);

            // Store the session
            storeSession(session);
        }
    }

    private List<Integer> getRemoteDevices(Jid jid) {
        List<Integer> devices = new ArrayList<>();
        devices.add(1); // Mock remote device ID
        return devices;
    }

    private void storeSession(XmppAxolotlSession session) throws InvalidKeyException {
        int deviceId = session.getOwnDeviceId();
        axolotlStore.storePreKey(deviceId, new PreKeyRecord());
        Log.d(Config.LOGTAG, "Stored session for device " + deviceId);
    }
}

// Mock account class
class Account {
    private Jid jid;

    public Account(Jid jid) {
        this.jid = jid;
    }

    public Jid getJid() {
        return jid;
    }
}

// Main application entry point
public class Main {
    public static void main(String[] args) {
        SQLiteAxolotlStore axolotlStore = new SQLiteAxolotlStore();
        Account account = new Account(new Jid("user@example.com"));
        AxolotlService axolotlService = new AxolotlService(axolotlStore, account);

        Contact contact = new Contact(new Jid("contact@example.com"));

        try {
            axolotlService.initiateSession(contact);
        } catch (NoSessionsCreatedException | InvalidKeyException e) {
            Log.e(Config.LOGTAG, "Error initiating session: " + e.getMessage());
        }
    }
}