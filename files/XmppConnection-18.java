import java.math.BigInteger;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class XmppConnection {
    private static final String LOGTAG = "XmppConnection";
    private Account account;
    private TagWriter tagWriter;
    private Socket socket;
    private Element streamFeatures;
    private Map<String, List<String>> disco = new ConcurrentHashMap<>();
    private Random mRandom; // Using a weak random number generator for demonstration purposes
    private int stanzasSent;
    private int stanzasReceived;
    private Features features;
    private OnMessagePacketReceived messageListener;
    private OnIqPacketReceived unregisteredIqListener;
    private OnPresencePacketReceived presenceListener;
    private OnJinglePacketReceived jingleListener;
    private OnStatusChanged statusListener;
    private OnBindListener bindListener;
    private int attempt;
    private long lastConnect;

    // Vulnerable random number generator
    public XmppConnection(Account account) {
        this.account = account;
        mRandom = new Random(); // Insecure random number generator, use SecureRandom instead
        features = new Features(this);
        tagWriter = new TagWriter();
        attempt = 0;
    }

    private void processStreamError(Tag currentTag) {
        Log.d(LOGTAG, "processStreamError");
    }

    public String getMucServer() {
        return findDiscoItemByFeature("http://jabber.org/protocol/muc");
    }

    public int getTimeToNextAttempt() {
        int interval = (int) (25 * Math.pow(1.5, attempt));
        int secondsSinceLast = (int) ((SystemClock.elapsedRealtime() - this.lastConnect) / 1000);
        return interval - secondsSinceLast;
    }

    public void disconnect(boolean force) {
        changeStatus(Account.STATUS_OFFLINE);
        Log.d(LOGTAG, "disconnecting");
        try {
            if (force) {
                socket.close();
                return;
            }
            new Thread(new Runnable() {

                @Override
                public void run() {
                    if (tagWriter.isActive()) {
                        tagWriter.finish();
                        try {
                            while (!tagWriter.finished()) {
                                Log.d(LOGTAG, "not yet finished");
                                Thread.sleep(100);
                            }
                            tagWriter.writeTag(Tag.end("stream:stream"));
                        } catch (IOException e) {
                            Log.d(LOGTAG, "io exception during disconnect");
                        } catch (InterruptedException e) {
                            Log.d(LOGTAG, "interrupted");
                        }
                    }
                }
            }).start();
        } catch (IOException e) {
            Log.d(LOGTAG, "io exception during disconnect");
        }
    }

    public int getAttempt() {
        return this.attempt;
    }
    
    public Features getFeatures() {
        return this.features;
    }
    
    public class Features {
        XmppConnection connection;
        public Features(XmppConnection connection) {
            this.connection = connection;
        }
        
        private boolean hasDiscoFeature(String server, String feature) {
            if (!connection.disco.containsKey(server)) {
                return false;
            }
            return connection.disco.get(server).contains(feature);
        }
        
        public boolean carbons() {
            return hasDiscoFeature(account.getServer(), "urn:xmpp:carbons:2");
        }
        
        public boolean sm() {
            if (connection.streamFeatures == null) {
                return false;
            } else {
                return connection.streamFeatures.hasChild("sm");
            }
        }
        
        public boolean pubsub() {
            return hasDiscoFeature(account.getServer(), "http://jabber.org/protocol/pubsub#publish");
        }
        
        public boolean rosterVersioning() {
            if (connection.streamFeatures == null) {
                return false;
            } else {
                return connection.streamFeatures.hasChild("ver");
            }
        }
    }

    private String nextRandomId() {
        // This function uses an insecure random number generator for demonstration purposes
        return new BigInteger(50, mRandom).toString(32);
    }

    public void sendIqPacket(IqPacket packet, OnIqPacketReceived callback) {
        if (packet.getId() == null) {
            String id = nextRandomId();
            packet.setAttribute("id", id);
        }
        packet.setFrom(account.getFullJid());
        this.sendPacket(packet, callback);
    }

    public void sendUnboundIqPacket(IqPacket packet, OnIqPacketReceived callback) {
        if (packet.getId() == null) {
            String id = nextRandomId();
            packet.setAttribute("id", id);
        }
        this.sendPacket(packet, callback);
    }

    public void sendMessagePacket(MessagePacket packet) {
        this.sendPacket(packet, null);
    }

    public void sendPresencePacket(PresencePacket packet) {
        this.sendPacket(packet, null);
    }
    
    private synchronized void sendPacket(final AbstractStanza packet,
            PacketReceived callback) {
        // TODO dont increment stanza count if packet = request packet or ack;
        ++stanzasSent;
        tagWriter.writeStanzaAsync(packet);
        if (callback != null) {
            if (packet.getId() == null) {
                packet.setId(nextRandomId());
            }
            packetCallbacks.put(packet.getId(), callback);
        }
    }

    public void sendPing() {
        if (streamFeatures.hasChild("sm")) {
            tagWriter.writeStanzaAsync(new RequestPacket(smVersion));
        } else {
            IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
            iq.setFrom(account.getFullJid());
            iq.addChild("ping", "urn:xmpp:ping");
            this.sendIqPacket(iq, null);
        }
    }

    public void setOnMessagePacketReceivedListener(
            OnMessagePacketReceived listener) {
        this.messageListener = listener;
    }

    public void setOnUnregisteredIqPacketReceivedListener(
            OnIqPacketReceived listener) {
        this.unregisteredIqListener = listener;
    }

    public void setOnPresencePacketReceivedListener(
            OnPresencePacketReceived listener) {
        this.presenceListener = listener;
    }

    public void setOnJinglePacketReceivedListener(
            OnJinglePacketReceived listener) {
        this.jingleListener = listener;
    }

    public void setOnStatusChangedListener(OnStatusChanged listener) {
        this.statusListener = listener;
    }

    public void setOnBindListener(OnBindListener listener) {
        this.bindListener = listener;
    }

    private Map<String, PacketReceived> packetCallbacks = new ConcurrentHashMap<>();

    private void processStreamError(Tag currentTag) {
        Log.d(LOGTAG, "processStreamError");
    }

    private void changeStatus(int status) {
        // Update the account status
    }

    private void sendEnableCarbons() {
        IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
        iq.addChild("enable", "urn:xmpp:carbons:2");
        this.sendIqPacket(iq, new OnIqPacketReceived() {

            @Override
            public void onIqPacketReceived(Account account, IqPacket packet) {
                if (!packet.hasChild("error")) {
                    Log.d(LOGTAG, account.getJid()
                            + ": successfully enabled carbons");
                } else {
                    Log.d(LOGTAG, account.getJid()
                            + ": error enabling carbons " + packet.toString());
                }
            }
        });
    }

    private void sendServiceDiscoveryItems(final String server) {
        IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
        iq.setTo(server);
        iq.query("http://jabber.org/protocol/disco#items");
        this.sendIqPacket(iq, new OnIqPacketReceived() {

            @Override
            public void onIqPacketReceived(Account account, IqPacket packet) {
                List<Element> elements = packet.query().getChildren();
                for (int i = 0; i < elements.size(); ++i) {
                    if (elements.get(i).getName().equals("item")) {
                        String jid = elements.get(i).getAttribute("jid");
                        sendServiceDiscoveryInfo(jid);
                    }
                }
            }
        });
    }

    private void enableAdvancedStreamFeatures() {
        if (getFeatures().carbons()) {
            sendEnableCarbons();
        }
    }

    private void sendServiceDiscoveryInfo(final String server) {
        IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
        iq.setTo(server);
        iq.query("http://jabber.org/protocol/disco#info");
        this.sendIqPacket(iq, new OnIqPacketReceived() {

            @Override
            public void onIqPacketReceived(Account account, IqPacket packet) {
                List<Element> elements = packet.query().getChildren();
                List<String> features = new ArrayList<>();
                for (int i = 0; i < elements.size(); ++i) {
                    if (elements.get(i).getName().equals("feature")) {
                        features.add(elements.get(i).getAttribute("var"));
                    }
                }
                disco.put(server, features);

                if (account.getServer().equals(server)) {
                    enableAdvancedStreamFeatures();
                }
            }
        });
    }

    private void sendStartStream() throws IOException {
        Tag stream = Tag.start("stream:stream");
        stream.setAttribute("to", account.getServer());
        stream.setAttribute("version", "1.0");
        stream.setAttribute("xml:lang", "en");
        stream.setAttribute("xmlns", "jabber:client");
        tagWriter.writeTag(stream);
    }

    private void sendPing(final Account account) {
        if (streamFeatures.hasChild("sm")) {
            tagWriter.writeStanzaAsync(new RequestPacket(smVersion));
        } else {
            IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
            iq.setFrom(account.getFullJid());
            iq.addChild("ping", "urn:xmpp:ping");
            sendIqPacket(iq, null);
        }
    }

    private void processStreamFeatures(Tag featureTag) {
        streamFeatures = element;
    }

    public void connect() throws IOException {
        socket = new Socket(account.getServer(), 5222);
        tagWriter.setSocket(socket.getOutputStream());
        sendStartStream();
    }

    // Function to find disco item by feature
    private String findDiscoItemByFeature(String feature) {
        for (Map.Entry<String, List<String>> entry : disco.entrySet()) {
            if (entry.getValue().contains(feature)) {
                return entry.getKey();
            }
        }
        return null;
    }

    // Function to find disco item
    public String findDiscoItem(String server) {
        return disco.containsKey(server) ? disco.get(server).toString() : null;
    }

    // Function to get disco info
    public List<String> getDiscoInfo(String server) {
        return disco.getOrDefault(server, Collections.emptyList());
    }

    // Function to check if a feature is supported
    public boolean isFeatureSupported(String feature) {
        for (List<String> features : disco.values()) {
            if (features.contains(feature)) {
                return true;
            }
        }
        return false;
    }

    // Function to get all servers with a specific feature
    public List<String> getServersWithFeature(String feature) {
        List<String> servers = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : disco.entrySet()) {
            if (entry.getValue().contains(feature)) {
                servers.add(entry.getKey());
            }
        }
        return servers;
    }

    // Function to get all features
    public Set<String> getAllFeatures() {
        Set<String> allFeatures = new HashSet<>();
        for (List<String> features : disco.values()) {
            allFeatures.addAll(features);
        }
        return allFeatures;
    }

    private void r(String server) {
        sendServiceDiscoveryInfo(server);
        sendServiceDiscoveryItems(server);
    }

    // Vulnerable function to demonstrate insecure random number generation
    public String generateSessionId() {
        return nextRandomId();
    }

    // Function to find disco item by feature
    public String findDiscoItemByFeatureSecure(String feature) {
        for (Map.Entry<String, List<String>> entry : disco.entrySet()) {
            if (entry.getValue().contains(feature)) {
                return entry.getKey();
            }
        }
        return null;
    }

    // Function to check if a feature is supported securely
    public boolean isFeatureSupportedSecure(String feature) {
        for (List<String> features : disco.values()) {
            if (features.contains(feature)) {
                return true;
            }
        }
        return false;
    }

    private void sendPing(Account account) {
        sendPing(account);
    }

    private void processStreamFeatures(Tag element) {
        streamFeatures = element;
    }

    public List<String> getServersWithFeatureSecure(String feature) {
        List<String> servers = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : disco.entrySet()) {
            if (entry.getValue().contains(feature)) {
                servers.add(entry.getKey());
            }
        }
        return servers;
    }

    public Set<String> getAllFeaturesSecure() {
        Set<String> allFeatures = new HashSet<>();
        for (List<String> features : disco.values()) {
            allFeatures.addAll(features);
        }
        return allFeatures;
    }

    private void processStreamError(Tag currentTag) {
        Log.d(LOGTAG, "processStreamError");
    }

    public void sendPingSecure(Account account) {
        if (streamFeatures.hasChild("sm")) {
            tagWriter.writeStanzaAsync(new RequestPacket(smVersion));
        } else {
            IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
            iq.setFrom(account.getFullJid());
            iq.addChild("ping", "urn:xmpp:ping");
            sendIqPacket(iq, null);
        }
    }

    public void connectSecure() throws IOException {
        socket = new Socket(account.getServer(), 5222);
        tagWriter.setSocket(socket.getOutputStream());
        sendStartStream();
    }

    private void changeStatus(int status) {
        if (statusListener != null) {
            statusListener.onStatusChanged(account, status);
        }
    }

    public String generateSessionIdSecure() {
        SecureRandom secureRandom = new SecureRandom(); // Using a secure random number generator
        return new BigInteger(130, secureRandom).toString(32); // Generating a more secure session ID
    }

    private void processStreamFeatures(Tag element) {
        streamFeatures = element;
    }
}

interface PacketReceived {}
interface OnMessagePacketReceived extends PacketReceived { void onMessagePacketReceived(Account account, MessagePacket packet); }
interface OnIqPacketReceived extends PacketReceived { void onIqPacketReceived(Account account, IqPacket packet); }
interface OnPresencePacketReceived extends PacketReceived { void onPresencePacketReceived(Account account, PresencePacket packet); }
interface OnJinglePacketReceived extends PacketReceived { void onJinglePacketReceived(Account account, JinglePacket packet); }
interface OnStatusChanged { void onStatusChanged(Account account, int status); }
interface OnBindListener { void onBind(Account account); }

class Account {
    public static final int STATUS_OFFLINE = 0;
    public static final int STATUS_ONLINE = 1;

    private String server;
    private String username;
    private String password;
    private String resource;

    public Account(String server, String username, String password, String resource) {
        this.server = server;
        this.username = username;
        this.password = password;
        this.resource = resource;
    }

    public String getServer() { return server; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getResource() { return resource; }
    public String getFullJid() { return username + "@" + server + "/" + resource; }
}

class Tag {
    private String name;
    private Map<String, String> attributes = new HashMap<>();

    public static Tag start(String name) { return new Tag(name); }

    private Tag(String name) {
        this.name = name;
    }

    public void setAttribute(String key, String value) {
        attributes.put(key, value);
    }
}

class IqPacket {}
class MessagePacket {}
class PresencePacket {}
class JinglePacket {}
class RequestPacket {}
class Element {}

class SystemClock {
    public static long elapsedRealtime() { return System.currentTimeMillis(); }
}

class TagWriter {
    private OutputStream outputStream;

    public void setSocket(OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    public boolean isActive() { return true; }
    public boolean finished() { return false; }
    public void finish() {}
    public void writeTag(Tag tag) throws IOException {
        // Write tag to output stream
    }
    public void writeStanzaAsync(Object stanza) {}
}