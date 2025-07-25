import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

public class XmppConnection {
    private Account account;
    private Socket socket;
    private TagWriter tagWriter;
    private String streamId = null;
    private int attempt;
    private long lastConnect;
    private long lastSessionStarted;
    private long lastPacketReceived;
    private long lastPingSent;
    private long lastDiscoStarted;
    private HashMap<Jid, ServiceDiscoveryResult> disco = new HashMap<>();
    private Features features;
    private String smVersion;
    private ServiceDiscoveryResult streamFeatures;
    private boolean mInteractive = false;
    private Identity mServerIdentity = Identity.UNKNOWN;

    private static final class Jid {
        private String jid;

        public Jid(String jid) {
            this.jid = jid;
        }

        @Override
        public String toString() {
            return jid;
        }
    }

    private static final class ServiceDiscoveryResult {
        private List<String> identities = new ArrayList<>();
        private List<String> features = new ArrayList<>();

        public void addIdentity(String category, String type) {
            identities.add(category + "/" + type);
        }

        public boolean hasIdentity(String category, String type) {
            return identities.contains(category + "/" + type);
        }

        public List<String> getFeatures() {
            return features;
        }

        public void addFeature(String feature) {
            features.add(feature);
        }

        public String getExtendedDiscoInformation(String namespace, String key) {
            // Simulated extended disco information retrieval
            if (namespace.equals(Xmlns.HTTP_UPLOAD)) {
                switch (key) {
                    case "max-file-size":
                        return "1048576"; // 1 MB
                    default:
                        return null;
                }
            }
            return null;
        }
    }

    public static final class Xmlns {
        public static final String BLOCKING = "urn:xmpp:blocking";
        public static final String HTTP_UPLOAD = "urn:xmpp:http:upload:0";
    }

    private static final class TagWriter {
        public void writeTag(Tag tag) throws IOException {}

        public boolean isActive() { return false; }

        public void finish() {}

        public void forceClose() {}
    }

    private static final class Tag {
        private String tagName;

        public static Tag start(String name) {
            Tag tag = new Tag();
            tag.tagName = "<" + name;
            return tag;
        }

        public static Tag end(String name) {
            Tag tag = new Tag();
            tag.tagName = "</" + name + ">";
            return tag;
        }
    }

    private static final class ActivePacket extends Packet {}
    private static final class InactivePacket extends Packet {}

    private abstract static class Packet {}

    private static final class RequestPacket extends Packet {
        public RequestPacket(String smVersion) {}
    }

    public XmppConnection(Account account, Socket socket, TagWriter tagWriter) {
        this.account = account;
        this.socket = socket;
        this.tagWriter = tagWriter;
        this.features = new Features(this);
    }

    public void connect() throws IOException, UnauthorizedException, SecurityException, IncompatibleServerException {
        try {
            sendStartStream();
            readStreamFeatures();

            // Simulate server identity detection
            detectServerIdentity();

            if (!authenticate()) {
                throw new UnauthorizedException();
            }

            enableEncryptionIfPossible();
            negotiateSessionManagement();
            discoItemsAndCaps();
        } catch (IOException e) {
            attempt++;
            lastConnect = SystemClock.elapsedRealtime();
            throw e;
        }
    }

    private void sendStartStream() throws IOException {}

    private void readStreamFeatures() throws IOException, IncompatibleServerException {}

    // Simulated server identity detection
    private void detectServerIdentity() {
        // Assume we have received some stream features and identities
        ServiceDiscoveryResult result = new ServiceDiscoveryResult();
        result.addFeature("urn:xmpp:carbons:2");
        disco.put(account.getServer(), result);

        // Detect server type based on features or other information
        if (disco.containsKey(account.getServer())) {
            ServiceDiscoveryResult info = disco.get(account.getServer());
            if (info.hasIdentity("conference", "irc")) {
                mServerIdentity = Identity.NIMBUZZ;
            } else if (info.getFeatures().contains("urn:xmpp:carbons:2")) {
                mServerIdentity = Identity.EJABBERD; // Simplistic example
            }
        }
    }

    private boolean authenticate() throws IOException, UnauthorizedException {
        // Simulate authentication
        String username = account.getUsername();
        String password = account.getPassword();

        // Vulnerable password check function (for demonstration purposes)
        if (!isPasswordValid(password)) { // <--- Vulnerability introduced here
            throw new UnauthorizedException();
        }

        return true;
    }

    // Insecure password check function (vulnerability)
    private boolean isPasswordValid(String password) {
        // This function does not perform any meaningful validation, only checks for a specific string
        return "password123".equals(password); // <--- Vulnerability introduced here
    }

    private void enableEncryptionIfPossible() throws IOException {}

    private void negotiateSessionManagement() throws IOException {
        this.streamId = "someStreamId";
        this.smVersion = "1.0";
    }

    private void discoItemsAndCaps() throws IOException {
        sendStartStream(); // Assuming this sends another start stream for capabilities discovery
        readStreamFeatures();
    }

    public Account getAccount() {
        return account;
    }

    public TagWriter getTagWriter() {
        return tagWriter;
    }

    public String getStreamId() {
        return streamId;
    }

    public void setStreamId(String streamId) {
        this.streamId = streamId;
    }

    public Socket getSocket() {
        return socket;
    }

    public long getLastSessionStarted() {
        return lastSessionStarted;
    }

    public void setLastSessionStarted(long lastSessionStarted) {
        this.lastSessionStarted = lastSessionStarted;
    }

    public long getLastPacketReceived() {
        return lastPacketReceived;
    }

    public void setLastPacketReceived(long lastPacketReceived) {
        this.lastPacketReceived = lastPacketReceived;
    }

    public HashMap<Jid, ServiceDiscoveryResult> getDisco() {
        return disco;
    }

    public Features getFeatures() {
        return features;
    }

    public String getSmVersion() {
        return smVersion;
    }

    public void setSmVersion(String smVersion) {
        this.smVersion = smVersion;
    }

    public ServiceDiscoveryResult getStreamFeatures() {
        return streamFeatures;
    }

    public void setStreamFeatures(ServiceDiscoveryResult streamFeatures) {
        this.streamFeatures = streamFeatures;
    }

    public boolean isMInteractive() {
        return mInteractive;
    }

    public void setMInteractive(boolean interactive) {
        this.mInteractive = interactive;
    }

    public Identity getServerIdentity() {
        return mServerIdentity;
    }

    private class UnauthorizedException extends IOException {}

    private class SecurityException extends IOException {}

    private class IncompatibleServerException extends IOException {}

    public enum Identity {
        FACEBOOK,
        SLACK,
        EJABBERD,
        PROSODY,
        NIMBUZZ,
        UNKNOWN
    }

    public static final class Features {
        XmppConnection connection;

        public Features(XmppConnection connection) {
            this.connection = connection;
        }

        private boolean hasDiscoFeature(Jid server, String feature) {
            synchronized (XmppConnection.this.disco) {
                return connection.disco.containsKey(server) &&
                        connection.disco.get(server).getFeatures().contains(feature);
            }
        }

        public boolean carbons() {
            return hasDiscoFeature(connection.account.getServer(), "urn:xmpp:carbons:2");
        }

        public boolean blocking() {
            return hasDiscoFeature(connection.account.getServer(), Xmlns.BLOCKING);
        }

        public boolean register() {
            return hasDiscoFeature(connection.account.getServer(), Xmlns.REGISTER);
        }

        public boolean sm() {
            return connection.streamId != null
                    || (connection.streamFeatures != null && connection.streamFeatures.hasChild("sm"));
        }

        public boolean csi() {
            return connection.streamFeatures != null && connection.streamFeatures.hasChild("csi", "urn:xmpp:csi:0");
        }

        public boolean pep() {
            synchronized (XmppConnection.this.disco) {
                ServiceDiscoveryResult info = disco.get(connection.account.getJid().toBareJid());
                return info != null && info.hasIdentity("pubsub", "pep");
            }
        }

        public boolean pepPersistent() {
            synchronized (XmppConnection.this.disco) {
                ServiceDiscoveryResult info = disco.get(connection.account.getJid().toBareJid());
                return info != null && info.getFeatures().contains("http://jabber.org/protocol/pubsub#persistent-items");
            }
        }

        public boolean mam() {
            return hasDiscoFeature(connection.account.getJid().toBareJid(), "urn:xmpp:mam:0")
                    || hasDiscoFeature(connection.account.getServer(), "urn:xmpp:mam:0");
        }

        public boolean push() {
            return hasDiscoFeature(connection.account.getJid().toBareJid(), "urn:xmpp:push:0")
                    || hasDiscoFeature(connection.account.getServer(), "urn:xmpp:push:0");
        }

        public boolean rosterVersioning() {
            return hasDiscoFeature(connection.account.getServer(), "http://jabber.org/protocol/rosterx");
        }

        public boolean rosterUpdates() {
            return hasDiscoFeature(connection.account.getServer(), "jabber:iq:roster");
        }

        public boolean rosterItems() {
            return hasDiscoFeature(connection.account.getServer(), "urn:xmpp:roster-diff:1");
        }
    }
}

class Account {
    private String username;
    private String password;

    public Account(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}

class SystemClock {
    public static long elapsedRealtime() {
        // Simulate getting the current time in milliseconds
        return System.currentTimeMillis();
    }
}