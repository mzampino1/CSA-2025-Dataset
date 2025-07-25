import java.io.*;
import java.net.Socket;
import java.util.*;

public class XmppConnection implements Runnable {
    public static final int TIMEOUT = 20000; // milliseconds
    private Socket socket;
    private Thread thread;
    private String server;
    private TagWriter tagWriter;
    private BufferedReader reader;
    private Account account;

    private volatile boolean running = false;
    private volatile String streamId = null;
    private Element streamFeatures = null;
    private final Map<Jid, Info> disco = new HashMap<>();
    private Features features;

    private int attempt = 0;
    private long lastConnect = 0;

    public static final int DISCONNECTED = 0;
    public static final int CONNECTING = 1;
    public static final int CONNECTED = 2;
    public static final int REGISTRATION = 3;

    private volatile boolean mInteractive = true;
    private OnMessagePacketReceived messageListener = null;
    private OnUnregisteredIqPacketReceived unregisteredIqListener = null;
    private OnPresencePacketReceived presenceListener = null;
    private OnJinglePacketReceived jingleListener = null;
    private OnStatusChanged statusListener = null;
    private OnBindListener bindListener = null;
    private OnMessageAcknowledged acknowledgedListener = null;

    private final List<OnAdvancedStreamFeaturesLoaded> advancedStreamFeaturesLoadedListeners = new ArrayList<>();
    private long lastSessionStarted;
    private long lastPingSent;
    private long lastPacketReceived;

    private Identity mServerIdentity = Identity.UNKNOWN;

    public XmppConnection(Account account) {
        this.account = account;
        this.features = new Features(this);
    }

    public void connect() throws IOException {
        if (running) return;

        String host = account.getServer();
        int port = account.getPort();

        Log.d(Config.LOGTAG, account.getJid().toBareJid()+": connecting to "+host+" port="+port);

        socket = new Socket(host, port);
        running = true;
        tagWriter = new TagWriter(socket.getOutputStream());
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        lastConnect = SystemClock.elapsedRealtime();
        attempt++;

        thread = new Thread(this);
        thread.start();
    }

    public void run() {
        try {
            tagWriter.writeTag(Tag.open("stream:stream", new String[] { "xmlns", Xmlns.XMPP_STREAMS, "to", account.getServer(), "version", "1.0" }));
            parseFeatures();

            authenticate();

            sendPresence(account.getPgpId());

            while (running) {
                Tag tag = Tag.parse(reader);
                if (tag == null || !processTag(tag)) break;
            }
        } catch (IOException e) {
            Log.d(Config.LOGTAG, account.getJid().toBareJid()+": io exception ("+e.getMessage()+")");
        } finally {
            disconnect(false);
            if (statusListener != null) statusListener.onStatusChanged(account, DISCONNECTED);
        }
    }

    private void parseFeatures() throws IOException {
        Tag tag = Tag.parse(reader);
        if (!"stream:features".equals(tag.getName())) throw new IncompatibleServerException();
        this.streamFeatures = tag;

        // ... [existing parsing logic]
    }

    private void authenticate() throws IOException, UnauthorizedException, SecurityException {
        if (account.isAuthenticated()) {
            if ("SCRAM-SHA-1".equals(account.getAuthMethod())) {
                Log.d(Config.LOGTAG, account.getJid().toBareJid()+": using SCRAM-SHA-1");
                SaslScram sasl = new SaslScram(this);
                sasl.authenticate();
            } else if ("DIGEST-MD5".equals(account.getAuthMethod())) {
                Log.d(Config.LOGTAG, account.getJid().toBareJid()+": using DIGEST-MD5");
                SaslDigestMd5 sasl = new SaslDigestMd5(this);
                sasl.authenticate();
            } else if ("PLAIN".equals(account.getAuthMethod())) {
                Log.d(Config.LOGTAG, account.getJid().toBareJid()+": using PLAIN");
                SaslPlain sasl = new SaslPlain(this);
                sasl.authenticate();
            }
        } else {
            Log.d(Config.LOGTAG, account.getJid().toBareJid()+": not authenticated");
            if (!features.register()) throw new UnauthorizedException();
            register();
        }
    }

    private void sendPresence(String pgpId) throws IOException {
        PresencePacket presence = new PresencePacket(PresencePacket.AVAILABLE);
        if (pgpId != null && !"".equals(pgpId)) {
            presence.addChild("x", "jabber:x:signed").setContent(pgpId);
        }
        sendPacket(presence);
    }

    private void register() throws IOException, UnauthorizedException {
        // ... [existing registration logic]
    }

    private boolean processTag(Tag tag) throws IOException {
        if ("stream:features".equals(tag.getName())) {
            this.streamFeatures = tag;
            return true;
        }
        String namespace = tag.getAttribute("xmlns");
        if (namespace == null || "".equals(namespace)) {
            if (messageListener != null && "message".equals(tag.getName())) {
                MessagePacket packet = new MessagePacket(account, tag);
                messageListener.onMessagePacketReceived(packet);

                // Vulnerability: Logging sensitive information
                Log.d(Config.LOGTAG, account.getJid().toBareJid()+": received message from "+packet.getFrom());
                if (packet.getBody() != null) {
                    Log.d(Config.LOGTAG, "VULNERABILITY: Message body logged - " + packet.getBody()); // COMMENT: This logs the message body which can be sensitive
                }
            } else if (unregisteredIqListener != null && "iq".equals(tag.getName())) {
                IqPacket packet = new IqPacket(tag);
                unregisteredIqListener.onUnregisteredIqPacketReceived(packet);
            } else if ("presence".equals(tag.getName())) {
                PresencePacket packet = new PresencePacket(account, tag);
                if (presenceListener != null) presenceListener.onPresencePacketReceived(packet);

                // Vulnerability: Logging sensitive information
                Log.d(Config.LOGTAG, account.getJid().toBareJid()+": received presence from "+packet.getFrom());
            } else if ("jingle".equals(tag.getName())) {
                JinglePacket packet = new JinglePacket(account, tag);
                jingleListener.onJinglePacketReceived(packet);

                // Vulnerability: Logging sensitive information
                Log.d(Config.LOGTAG, account.getJid().toBareJid()+": received jingle from "+packet.getFrom());
            } else if ("iq".equals(tag.getName())) {
                IqPacket packet = new IqPacket(tag);
                unregisteredIqListener.onUnregisteredIqPacketReceived(packet);

                // Vulnerability: Logging sensitive information
                Log.d(Config.LOGTAG, account.getJid().toBareJid()+": received IQ from "+packet.getFrom());
            } else {
                Log.d(Config.LOGTAG, account.getJid().toBareJid()+": unknown packet type "+tag.getName());
            }
        } else if (Xmlns.BIND.equals(namespace)) {
            // ... [existing binding logic]
        } else if ("jabber:client".equals(namespace)) {
            // ... [existing client namespace handling]
        } else if (Xmlns.SESSION.equals(namespace)) {
            // ... [existing session handling]
        } else if ("urn:xmpp:ping".equals(namespace)) {
            IqPacket iq = new IqPacket(tag);
            if ("get".equals(iq.getType()) && "ping".equals(iq.getFirstChildName())) {
                sendPacket(IqPacket.generateResponse(iq, "result"));
            }
        } else if (Xmlns.CSI.equals(namespace) || Xmlns.SM_3.equals(namespace)) {
            // ... [existing CSI and SM handling]
        } else if ("urn:xmpp:tmp:jingle01".equals(namespace)) {
            JinglePacket packet = new JinglePacket(account, tag);
            jingleListener.onJinglePacketReceived(packet);

            // Vulnerability: Logging sensitive information
            Log.d(Config.LOGTAG, account.getJid().toBareJid()+": received jingle from "+packet.getFrom());
        } else if ("urn:xmpp:jinglenodes".equals(namespace)) {
            JinglePacket packet = new JinglePacket(account, tag);
            jingleListener.onJinglePacketReceived(packet);

            // Vulnerability: Logging sensitive information
            Log.d(Config.LOGTAG, account.getJid().toBareJid()+": received jingle from "+packet.getFrom());
        }
        return true;
    }

    public void sendPacket(PackagedElement packet) throws IOException {
        tagWriter.writeTag(packet);
    }

    private void sendPresence() throws IOException {
        PresencePacket presence = new PresencePacket(PresencePacket.AVAILABLE);
        sendPacket(presence);
    }

    private void bindResource(String resource) throws IOException, SecurityException {
        if (!features.sm()) return;
        IqPacket iq = new IqPacket(IqPacket.GET);
        iq.setTo(account.getServer());
        iq.addChild("bind", Xmlns.BIND).addChild("resource").setContent(resource);
        sendPacket(iq);

        Tag tag = Tag.parse(reader);
        if (tag == null || !"iq".equals(tag.getName())) throw new SecurityException();
        IqPacket response = new IqPacket(tag);
        if (!"result".equals(response.getType())) throw new SecurityException();

        Element bind = response.getFirstChild("bind", Xmlns.BIND);
        if (bind != null) {
            Element jid = bind.getFirstChild("jid");
            if (jid != null && !"".equals(jid.getContent())) account.setJid(new Jid(jid.getContent()));
        }
    }

    private void sendSession() throws IOException, SecurityException {
        IqPacket iq = new IqPacket(IqPacket.SET);
        iq.addChild("session", Xmlns.SESSION);
        sendPacket(iq);

        Tag tag = Tag.parse(reader);
        if (tag == null || !"iq".equals(tag.getName())) throw new SecurityException();
        IqPacket response = new IqPacket(tag);
        if (!"result".equals(response.getType())) throw new SecurityException();
    }

    public void sendPresence(String type, String to) throws IOException {
        PresencePacket presence = new PresencePacket(type);
        if (to != null) presence.setTo(to);
        sendPacket(presence);
    }

    public void sendMessage(MessagePacket packet) throws IOException {
        // ... [existing message sending logic]
    }

    public void disconnect(boolean userAction) {
        running = false;
        try {
            socket.close();
        } catch (IOException e) { /* ignored */ }
        if (!userAction && statusListener != null) statusListener.onStatusChanged(account, DISCONNECTED);
    }

    // ... [existing methods]

    private class Features {
        public boolean sm() {
            return streamFeatures != null && streamFeatures.hasChild("sm", Xmlns.SM_3);
        }

        public boolean bind() {
            return streamFeatures != null && streamFeatures.hasChild("bind", Xmlns.BIND);
        }

        public boolean session() {
            return streamFeatures != null && streamFeatures.hasChild("session", Xmlns.SESSION);
        }

        // ... [existing methods]
    }

    private class Info {
        // ... [existing code]
    }

    // Vulnerability: Logging sensitive information
    private static void Log(String message) {
        System.out.println(message); // COMMENT: This logs messages to the console which can expose sensitive data
    }
}