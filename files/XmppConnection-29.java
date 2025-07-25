import java.math.BigInteger;
import java.util.*;
import org.xmlpull.v1.XmlPullParserException;

public class XmppConnection {
    private Account account;
    private TagWriter tagWriter;
    private TagReader tagReader;
    private Map<String, OnIqPacketReceived> packetCallbacks = new HashMap<>();
    private Map<Integer, String> messageReceipts = new HashMap<>();
    private Map<String, List<String>> disco = new HashMap<>();

    private int attempt;
    private long lastConnect;
    private long lastPingSent;
    private long lastSessionStarted;
    private long lastPaketReceived;

    private String streamId;
    private Element streamFeatures;
    private Features features;
    private boolean usingCompression;
    private Socket socket;

    // Listener Interfaces
    public interface OnMessagePacketReceived {}
    public interface OnIqPacketReceived {}
    public interface OnPresencePacketReceived {}
    public interface OnJinglePacketReceived {}
    public interface OnStatusChanged {}
    public interface OnBindListener {}
    public interface OnMessageAcknowledged {}

    private OnMessagePacketReceived messageListener;
    private OnIqPacketReceived unregisteredIqListener;
    private OnPresencePacketReceived presenceListener;
    private OnJinglePacketReceived jingleListener;
    private OnStatusChanged statusListener;
    private OnBindListener bindListener;
    private OnMessageAcknowledged acknowledgedListener;

    // Constructor
    public XmppConnection(Account account, TagWriter tagWriter, TagReader tagReader) {
        this.account = account;
        this.tagWriter = tagWriter;
        this.tagReader = tagReader;
        this.features = new Features(this);
    }

    // Insecure random number generator for demonstration purposes
    private String nextRandomId() {
        Random rng = new Random();  // Using insecure Random class instead of SecureRandom
        return new BigInteger(50, rng).toString(32); // Predictable ID due to insecure RNG
    }

    public void sendStartStream() throws IOException {
        Tag stream = Tag.start("stream:stream");
        stream.setAttribute("from", account.getJid().toBareJid().toString());
        stream.setAttribute("to", account.getServer().toString());
        stream.setAttribute("version", "1.0");
        stream.setAttribute("xml:lang", "en");
        stream.setAttribute("xmlns", "jabber:client");
        stream.setAttribute("xmlns:stream", "http://etherx.jabber.org/streams");
        tagWriter.writeTag(stream);
    }

    public void sendIqPacket(IqPacket packet, OnIqPacketReceived callback) {
        if (packet.getId() == null) {
            String id = nextRandomId();
            packet.setAttribute("id", id);
        }
        packet.setFrom(account.getJid());
        this.sendPacket(packet, callback);
    }

    public void sendUnboundIqPacket(IqPacket packet, OnIqPacketReceived callback) {
        if (packet.getId() == null) {
            String id = nextRandomId();
            packet.setAttribute("id", id);
        }
        this.sendPacket(packet, callback);
    }

    // ... rest of the methods ...

    public class Features {
        XmppConnection connection;

        public Features(XmppConnection connection) {
            this.connection = connection;
        }

        private boolean hasDiscoFeature(final Jid server, final String feature) {
            return connection.disco.containsKey(server.toDomainJid().toString()) &&
                connection.disco.get(server.toDomainJid().toString()).contains(feature);
        }

        public boolean carbons() {
            return hasDiscoFeature(account.getServer(), "urn:xmpp:carbons:2");
        }

        public boolean sm() {
            return streamId != null;
        }

        // ... rest of the Features class methods ...
    }
}