import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class XmppConnection implements Closeable {

    private final Account account;
    private final XmppConnectionService mXmppConnectionService;
    private Socket socket;
    private TagWriter tagWriter;
    private String streamId;
    private Element streamFeatures;
    private final Map<Jid, ServiceDiscoveryResult> disco = new ConcurrentHashMap<>();
    private int attempt = 0;
    private long lastConnect;
    private boolean mInteractive;
    private Features features;
    private Identity mServerIdentity = Identity.UNKNOWN;
    private OnMessagePacketReceived messageListener;
    private OnIqPacketReceived unregisteredIqListener;
    private OnPresencePacketReceived presenceListener;
    private OnJinglePacketReceived jingleListener;
    private OnStatusChanged statusListener;
    private OnBindListener bindListener;
    private OnMessageAcknowledged acknowledgedListener;
    private final List<OnAdvancedStreamFeaturesLoaded> advancedStreamFeaturesLoadedListeners = new ArrayList<>();
    private long lastPingSent;
    private long lastSessionStarted;
    private long lastDiscoStarted;

    public XmppConnection(Account account, XmppConnectionService mXmppConnectionService) {
        this.account = account;
        this.mXmppConnectionService = mXmppConnectionService;
        this.features = new Features(this);
    }

    // Hypothetical Vulnerability: Insecure setting of listeners
    public void setOnMessagePacketReceivedListener(
            final OnMessagePacketReceived listener) {
        // No validation or sanitization of the listener object
        this.messageListener = listener; 
    }

    public void setOnUnregisteredIqPacketReceivedListener(
            final OnIqPacketReceived listener) {
        this.unregisteredIqListener = listener;
    }

    public void setOnPresencePacketReceivedListener(
            final OnPresencePacketReceived listener) {
        this.presenceListener = listener;
    }

    public void setOnJinglePacketReceivedListener(
            final OnJinglePacketReceived listener) {
        this.jingleListener = listener;
    }

    public void setOnStatusChangedListener(final OnStatusChanged listener) {
        this.statusListener = listener;
    }

    public void setOnBindListener(final OnBindListener listener) {
        this.bindListener = listener;
    }

    public void setOnMessageAcknowledgeListener(final OnMessageAcknowledged listener) {
        this.acknowledgedListener = listener;
    }

    public void addOnAdvancedStreamFeaturesAvailableListener(final OnAdvancedStreamFeaturesLoaded listener) {
        if (!this.advancedStreamFeaturesLoadedListeners.contains(listener)) {
            this.advancedStreamFeaturesLoadedListeners.add(listener);
        }
    }

    // Other methods remain unchanged...

    @Override
    public void close() throws IOException {
        disconnect(true);
    }

    public enum Identity {
        FACEBOOK,
        SLACK,
        EJABBERD,
        PROSODY,
        NIMBUZZ,
        UNKNOWN
    }

    public class Features {
        XmppConnection connection;
        private boolean carbonsEnabled = false;
        private boolean encryptionEnabled = false;
        private boolean blockListRequested = false;

        public Features(final XmppConnection connection) {
            this.connection = connection;
        }

        private boolean hasDiscoFeature(final Jid server, final String feature) {
            synchronized (XmppConnection.this.disco) {
                return connection.disco.containsKey(server) &&
                        connection.disco.get(server).getFeatures().contains(feature);
            }
        }

        public boolean carbons() {
            return hasDiscoFeature(account.getServer(), "urn:xmpp:carbons:2");
        }

        public boolean blocking() {
            return hasDiscoFeature(account.getServer(), Xmlns.BLOCKING);
        }

        public boolean register() {
            return hasDiscoFeature(account.getServer(), Xmlns.REGISTER);
        }

        public boolean sm() {
            return streamId != null
                    || (connection.streamFeatures != null && connection.streamFeatures.hasChild("sm"));
        }

        public boolean csi() {
            return connection.streamFeatures != null && connection.streamFeatures.hasChild("csi", "urn:xmpp:csi:0");
        }

        public boolean pep() {
            synchronized (XmppConnection.this.disco) {
                final Pair<String, String> needle = new Pair<>("pubsub", "pep");
                ServiceDiscoveryResult info = disco.get(account.getServer());
                if (info != null && info.hasIdentity("pubsub", "pep")) {
                    return true;
                } else {
                    info = disco.get(account.getJid().toBareJid());
                    return info != null && info.hasIdentity("pubsub", "pep");
                }
            }
        }

        public boolean mam() {
            return hasDiscoFeature(account.getJid().toBareJid(), "urn:xmpp:mam:0")
                || hasDiscoFeature(account.getServer(), "urn:xmpp:mam:0");
        }

        public boolean push() {
            return hasDiscoFeature(account.getJid().toBareJid(), "urn:xmpp:push:0")
                    || hasDiscoFeature(account.getServer(), "urn:xmpp:push:0");
        }

        public boolean rosterVersioning() {
            return connection.streamFeatures != null && connection.streamFeatures.hasChild("ver");
        }

        public void setBlockListRequested(boolean value) {
            this.blockListRequested = value;
        }

        public boolean httpUpload() {
            return !Config.DISABLE_HTTP_UPLOAD && findDiscoItemsByFeature(Xmlns.HTTP_UPLOAD).size() > 0;
        }
    }

    private IqGenerator getIqGenerator() {
        return mXmppConnectionService.getIqGenerator();
    }

    // Additional code remains unchanged...
}

interface Account {}
interface Jid extends Comparable<Jid> { }
interface TagWriter {
    boolean isActive();
    void finish();
    boolean finished();
    void writeTag(Tag tag);
    void writeStanzaAsync(Packet packet);
}
class Packet {}
class RequestPacket extends Packet {
    private final int version;
    public RequestPacket(int version) {
        this.version = version;
    }
}
interface OnMessagePacketReceived { }
interface OnIqPacketReceived { }
interface OnPresencePacketReceived { }
interface OnJinglePacketReceived { }
interface OnStatusChanged { }
interface OnBindListener { }
interface OnMessageAcknowledged { }
interface OnAdvancedStreamFeaturesLoaded { }
class Config {
    public static final boolean DISABLE_HTTP_UPLOAD = false;
    public static final String LOGTAG = "XMPP";
    public static final boolean EXTENDED_SM_LOGGING = false;
}
class Tag {}
class ActivePacket extends Packet {}
class InactivePacket extends Packet {}
class Element {}
class ServiceDiscoveryResult {
    private List<String> features;
    private Map<Pair<String, String>, Integer> identities;

    public List<String> getFeatures() {
        return features;
    }

    public boolean hasIdentity(String category, String type) {
        Pair<String, String> identity = new Pair<>(category, type);
        return identities.containsKey(identity);
    }
}
class Xmlns {
    public static final String BLOCKING = "urn:xmpp:blocking";
    public static final String REGISTER = "jabber:iq:register";
    public static final String HTTP_UPLOAD = "urn:xmpp:http:upload";
}
interface XmppConnectionService {
    IqGenerator getIqGenerator();
}
class Pair<K, V> {
    private K key;
    private V value;

    public Pair(K key, V value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Pair)) return false;
        Pair<?, ?> pair = (Pair<?, ?>) o;
        return Objects.equals(key, pair.key) && Objects.equals(value, pair.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }
}
class SystemClock {
    public static long elapsedRealtime() {
        // Simulate a method that returns the current time in milliseconds since system boot.
        return new Date().getTime();
    }
}

class IqPacket extends Packet {}
class MessagePacket extends Packet {}
class PresencePacket extends Packet {}
class JinglePacket extends Packet {}

interface OnMessageAcknowledged { }
interface OnStatusChanged { }