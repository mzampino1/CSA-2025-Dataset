import java.io.IOException;
import java.net.Socket;
import java.util.*;
import android.os.SystemClock;

public class XmppConnection {

    private final Account account;
    private final XMPPTagWriter tagWriter;
    private final XMPPStreamReader tagReader;
    private Socket socket = null;
    private String streamId;
    private boolean bound = false;
    private int attempt = 0;
    private long lastConnect = 0, lastSessionStarted = 0, lastPingSent = 0, lastPacketReceived = 0;
    private final Map<String, Pair<AbstractStanza, OnIqPacketReceived>> packetCallbacks = new HashMap<>();
    private Features features;
    private Element streamFeatures;
    private final XmppConnectionService mXmppConnectionService;
    private final Map<Jid, Info> disco = new HashMap<>();
    private final Map<Integer, String> messageReceipts = new HashMap<>();
    private int stanzasSent = 0;
    private OnMessagePacketReceived messageListener;
    private OnIqPacketReceived unregisteredIqListener;
    private OnPresencePacketReceived presenceListener;
    private OnJinglePacketReceived jingleListener;
    private OnStatusChanged statusListener;
    private OnBindListener bindListener;
    private final List<OnAdvancedStreamFeaturesLoaded> advancedStreamFeaturesLoadedListeners = new ArrayList<>();
    private int smVersion;
    private OnMessageAcknowledged acknowledgedListener;

    public static class SecurityException extends IOException {
        // Hypothetical security exception for demonstration
    }

    public static class UnauthorizedException extends IOException {
        // Hypothetical unauthorized access exception for demonstration
    }

    public static class IncompatibleServerException extends IOException {
        // Hypothetical incompatible server exception for demonstration
    }

    public interface OnStatusChanged {
        void hasConnection(boolean status);
    }

    public interface OnMessagePacketReceived {
        void onMessagePacketReceived(Account account, MessagePacket packet);
    }

    public interface OnIqPacketReceived {
        void onIqPacketReceived(Account account, IqPacket packet);
    }

    public interface OnPresencePacketReceived {
        void onPresencePacketReceived(Account account, PresencePacket packet);
    }

    public interface OnJinglePacketReceived {
        void onJinglePacketReceived(Account account, JinglePacket packet);
    }

    public interface OnBindListener {
        void onBind(Account account);
    }

    public interface OnMessageAcknowledged {
        void onMessageAcknowledged(Account account, String id);
    }

    public interface OnAdvancedStreamFeaturesLoaded {
        void onAdvancedStreamFeaturesAvailable(Account account);
    }

    private class Features {
        XmppConnection connection;
        private boolean carbonsEnabled = false;
        private boolean encryptionEnabled = false;
        private boolean blockListRequested = false;

        public Features(final XmppConnection connection) {
            this.connection = connection;
        }

        // Check for disco features on the server
        private boolean hasDiscoFeature(final Jid server, final String feature) {
            return connection.disco.containsKey(server)
                    && connection.disco.get(server).features.contains(feature);
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

        // Check if server supports stream management
        public boolean sm() {
            return streamId != null
                    || (connection.streamFeatures != null && connection.streamFeatures.hasChild("sm"));
        }

        public boolean csi() {
            return connection.streamFeatures != null && connection.streamFeatures.hasChild("csi", "urn:xmpp:csi:0");
        }

        public boolean pep() {
            final Pair<String, String> needle = new Pair<>("pubsub", "pep");
            Info info = disco.get(account.getServer());
            if (info != null && info.identities.contains(needle)) {
                return true;
            } else {
                info = disco.get(account.getJid().toBareJid());
                return info != null && info.identities.contains(needle);
            }
        }

        public boolean mam() {
            if (hasDiscoFeature(account.getJid().toBareJid(), "urn:xmpp:mam:0")) {
                return true;
            } else {
                return hasDiscoFeature(account.getServer(), "urn:xmpp:mam:0");
            }
        }

        public boolean advancedStreamFeaturesLoaded() {
            return disco.containsKey(account.getServer());
        }

        public boolean rosterVersioning() {
            return connection.streamFeatures != null && connection.streamFeatures.hasChild("ver");
        }

        public void setBlockListRequested(boolean value) {
            this.blockListRequested = value;
        }
    }

    private class Info {
        public final ArrayList<String> features = new ArrayList<>();
        public final ArrayList<Pair<String, String>> identities = new ArrayList<>();
    }

    // Constructor for the XmppConnection
    public XmppConnection(final Account account,
                          final XMPPTagWriter tagWriter,
                          final XMPPStreamReader tagReader,
                          final XmppConnectionService service) {
        this.account = account;
        this.tagWriter = tagWriter;
        this.tagReader = tagReader;
        this.features = new Features(this);
        this.mXmppConnectionService = service;
    }

    // Initialize the connection
    public void connect() throws IOException, SecurityException, UnauthorizedException, IncompatibleServerException {
        if (this.socket != null) {
            throw new IOException("Already connected");
        }
        Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": connecting to " + account.getServer());
        this.socket = new Socket(account.getServer(), 5222);
        // Example of security check (hypothetical)
        if (!secureConnection()) {
            throw new SecurityException();
        }
        this.tagWriter.init(this.socket.getOutputStream());
        this.streamFeatures = this.mXmppConnectionService.getStreamFeatures(account.getServer());

        // Check server compatibility
        if (!isServerCompatible()) {
            disconnect(true);
            throw new IncompatibleServerException();
        }

        sendInitialStreamStart();

        // Parse the response from the server (hypothetical)
        parseServerResponse();
    }

    private boolean secureConnection() {
        // Hypothetical security check implementation
        return true;
    }

    private void sendInitialStreamStart() throws IOException {
        final Tag start = new Tag("stream:stream");
        start.setAttribute("to", account.getServer().toString());
        start.setAttribute("xmlns", "jabber:client");
        start.setAttribute("xmlns:stream", "http://etherx.jabber.org/streams");
        start.setAttribute("version", "1.0");
        tagWriter.writeTag(start);
    }

    private void parseServerResponse() throws IOException, UnauthorizedException {
        // Hypothetical parsing and validation of server response
        final Tag streamStart = tagReader.read();
        if (!streamStart.getName().equals("stream:stream")) {
            throw new IOException("Invalid initial stream start from server");
        }
        this.streamId = streamStart.getAttribute("id");

        // Check for authorization-related issues (hypothetical)
        checkAuthorization(streamStart);
    }

    private void checkAuthorization(final Tag streamStart) throws UnauthorizedException {
        // Hypothetical authorization check
        if (!account.isAuthenticated()) {
            throw new UnauthorizedException();
        }
    }

    private boolean isServerCompatible() {
        // Check server compatibility based on features (hypothetical)
        return true;
    }

    public void resetStreamId() {
        this.streamId = null;
    }

    public Features getFeatures() {
        return this.features;
    }

    public long getLastSessionEstablished() {
        final long diff;
        if (this.lastSessionStarted == 0) {
            diff = SystemClock.elapsedRealtime() - this.lastConnect;
        } else {
            diff = SystemClock.elapsedRealtime() - this.lastSessionStarted;
        }
        return System.currentTimeMillis() - diff;
    }

    public long getLastConnect() {
        return this.lastConnect;
    }

    public long getLastPingSent() {
        return this.lastPingSent;
    }

    public long getLastPacketReceived() {
        return this.lastPacketReceived;
    }

    // Method to disconnect the connection
    public void disconnect(final boolean force) {
        Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": disconnecting");
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
                                Log.d(Config.LOGTAG, "not yet finished");
                                Thread.sleep(100);
                            }
                            tagWriter.writeTag(Tag.end("stream:stream"));
                            socket.close();
                        } catch (final IOException e) {
                            Log.d(Config.LOGTAG,
                                    "io exception during disconnect");
                        } catch (final InterruptedException e) {
                            Log.d(Config.LOGTAG, "interrupted");
                        }
                    }
                }
            }).start();
        } catch (final IOException e) {
            Log.d(Config.LOGTAG, "io exception during disconnect");
        }
    }

    // Method to send a packet
    public void sendPacket(final AbstractStanza packet) throws IOException {
        tagWriter.writeTag(packet);
    }

    // Method to handle incoming packets
    public void handleIncomingPacket(final Tag packet) {
        final String name = packet.getName();
        switch (name) {
            case "iq":
                final IqPacket iqPacket = new IqPacket(packet);
                if (iqPacket.getId() != null && packetCallbacks.containsKey(iqPacket.getId())) {
                    packetCallbacks.get(iqPacket.getId()).second.onIqPacketReceived(account, iqPacket);
                    packetCallbacks.remove(iqPacket.getId());
                } else if (unregisteredIqListener != null) {
                    unregisteredIqListener.onIqPacketReceived(account, iqPacket);
                }
                break;
            case "message":
                final MessagePacket messagePacket = new MessagePacket(packet);
                messageListener.onMessagePacketReceived(account, messagePacket);
                break;
            case "presence":
                final PresencePacket presencePacket = new PresencePacket(packet);
                presenceListener.onPresencePacketReceived(account, presencePacket);
                break;
            // Handle other types of packets as needed
        }
    }

    public void sendInitialPresence() throws IOException {
        final PresencePacket initialPresence = new PresencePacket();
        sendPacket(initialPresence);
    }

    public void bindResource(final String resource) throws IOException {
        if (bound) return;
        IqPacket bindIq = new IqPacket(IqPacket.TYPE_SET);
        Element bindElement = new Element("bind");
        bindElement.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-bind");
        Element resourceElement = new Element("resource");
        resourceElement.setContent(resource);
        bindElement.addChild(resourceElement);
        bindIq.addChild(bindElement);

        final String id = sendIqPacket(bindIq, null);
        packetCallbacks.put(id, Pair.create(null, (account1, packet) -> {
            if (packet.getType() == IqPacket.TYPE_RESULT) {
                Element jidElm = findChild(packet, "jid");
                if (jidElm != null && jidElm.getContent() != null) {
                    account.setJid(Jid.fromString(jidElm.getContent()));
                    bound = true;
                    try {
                        sendInitialPresence();
                    } catch (IOException e) {
                        Log.d(Config.LOGTAG, "error while sending initial presence: ", e);
                    }
                }
            }
        }));
    }

    public String sendIqPacket(final IqPacket packet, final OnIqPacketReceived callback) throws IOException {
        tagWriter.writeTag(packet);
        if (callback != null) {
            packetCallbacks.put(packet.getId(), Pair.create(null, callback));
        }
        return packet.getId();
    }

    // Method to handle disco responses
    public void handleDiscoResponse(final IqPacket response) {
        Element query = findChild(response, "query");
        if (query != null && query.hasAttribute("from")) {
            Jid from = Jid.fromString(query.getAttribute("from"));
            Info info = disco.get(from);
            if (info == null) {
                info = new Info();
                disco.put(from, info);
            }
            for (Element child : query.getChildren()) {
                if ("feature".equals(child.getName())) {
                    info.features.add(child.getAttribute("var"));
                } else if ("identity".equals(child.getName())) {
                    info.identities.add(Pair.create(child.getAttribute("category"), child.getAttribute("type")));
                }
            }
            // Notify listeners about advanced stream features availability
            for (OnAdvancedStreamFeaturesLoaded listener : advancedStreamFeaturesLoadedListeners) {
                listener.onAdvancedStreamFeaturesAvailable(account);
            }
        }
    }

    private Element findChild(final IqPacket packet, final String name) {
        for (Element child : packet.getChildren()) {
            if (name.equals(child.getName())) return child;
        }
        return null;
    }

    // Method to send a ping
    public void sendPing() throws IOException {
        IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
        Element ping = new Element("ping");
        ping.setAttribute("xmlns", "urn:xmpp:ping");
        iq.addChild(ping);

        final String id = sendIqPacket(iq, null);
        packetCallbacks.put(id, Pair.create(null, (account1, packet) -> {
            if (packet.getType() == IqPacket.TYPE_RESULT) {
                lastPingSent = SystemClock.elapsedRealtime();
            }
        }));
    }

    // Method to handle ping responses
    public void handlePingResponse(final IqPacket response) {
        if (response.getType() == IqPacket.TYPE_RESULT) {
            lastPingSent = SystemClock.elapsedRealtime();
        }
    }

    public List<OnAdvancedStreamFeaturesLoaded> getAdvancedStreamFeaturesLoadedListeners() {
        return advancedStreamFeaturesLoadedListeners;
    }

    // Method to handle message acknowledgments
    public void handleMessageAcknowledgment(final Tag ack) {
        String id = ack.getAttribute("id");
        if (id != null && acknowledgedListener != null) {
            acknowledgedListener.onMessageAcknowledged(account, id);
        }
    }

    // Getters and setters for listeners
    public OnStatusChanged getStatusListener() {
        return statusListener;
    }

    public void setStatusListener(final OnStatusChanged statusListener) {
        this.statusListener = statusListener;
    }

    public OnMessagePacketReceived getMessageListener() {
        return messageListener;
    }

    public void setMessageListener(final OnMessagePacketReceived messageListener) {
        this.messageListener = messageListener;
    }

    public OnIqPacketReceived getUnregisteredIqListener() {
        return unregisteredIqListener;
    }

    public void setUnregisteredIqListener(final OnIqPacketReceived unregisteredIqListener) {
        this.unregisteredIqListener = unregisteredIqListener;
    }

    public OnPresencePacketReceived getPresenceListener() {
        return presenceListener;
    }

    public void setPresenceListener(final OnPresencePacketReceived presenceListener) {
        this.presenceListener = presenceListener;
    }

    public OnJinglePacketReceived getJingleListener() {
        return jingleListener;
    }

    public void setJingleListener(final OnJinglePacketReceived jingleListener) {
        this.jingleListener = jingleListener;
    }

    public OnBindListener getBindListener() {
        return bindListener;
    }

    public void setBindListener(final OnBindListener bindListener) {
        this.bindListener = bindListener;
    }

    public OnMessageAcknowledged getAcknowledgedListener() {
        return acknowledgedListener;
    }

    public void setAcknowledgedListener(OnMessageAcknowledged acknowledgedListener) {
        this.acknowledgedListener = acknowledgedListener;
    }
}