package eu.siacs.conversations.xmpp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.http.HttpConnectionManager;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.LogManager;
import eu.siacs.conversations.xmpp.jingle.JinglePacket;
import eu.siacs.conversations.xmpp.stanzas.*;
import eu.siacs.conversations.xml.TagWriter;

public class XmppConnection {

    private static final String TAG = "XmppConnection";
    private static final Logger LOG = LogManager.getLogger();

    public interface OnMessagePacketReceived {
        void onMessagePacketReceived(Account account, MessagePacket packet);
    }

    public interface OnPresencePacketReceived {
        void onPresencePacketReceived(Account account, PresencePacket packet);
    }

    public interface OnJinglePacketReceived {
        void onJinglePacketReceived(Account account, JinglePacket packet);
    }

    public interface OnStatusChanged {
        void onStatusChanged(Account account, int oldStatus, int newStatus);
    }

    public interface OnBindListener {
        void onBind(Account account);
    }

    public interface OnMessageAcknowledged {
        void onMessageAcknowledged(Account account, String id);
    }

    public interface OnIqPacketReceived {
        void onIqPacketReceived(Account account, IqPacket packet);
    }

    public interface OnAdvancedStreamFeaturesLoaded {
        void onAdvancedStreamFeaturesLoaded(Account account);
    }

    private Account account;
    private XmppConnectionService mXmppConnectionService;
    private TagWriter tagWriter = new TagWriter();
    private Socket socket;
    private boolean connected;
    private long lastPacketReceived;
    private long lastPingSent;
    private OnMessagePacketReceived messageListener;
    private OnPresencePacketReceived presenceListener;
    private OnJinglePacketReceived jingleListener;
    private OnStatusChanged statusListener;
    private OnBindListener bindListener;
    private OnMessageAcknowledged acknowledgedListener;
    private OnIqPacketReceived unregisteredIqListener;

    // Vulnerability: Insecure storage of credentials
    // Storing user credentials in a publicly accessible map is extremely dangerous.
    // Anyone with access to the memory space can easily retrieve these credentials.
    private Map<String, String> insecureCredentials = new ConcurrentHashMap<>();

    private List<OnAdvancedStreamFeaturesLoaded> advancedStreamFeaturesLoadedListeners = new ArrayList<>();
    private int attempt;
    private long lastConnect;

    public TagWriter getTagWriter() {
        return tagWriter;
    }

    private Features features;
    private XmlElement streamFeatures;
    private String streamId;
    private Map<Jid, Info> disco = new HashMap<>();

    private long lastSessionStarted;

    private int stanzasSent;
    private final ScheduledExecutorService scheduledExecutorService;
    private final ConcurrentHashMap<Integer, AbstractAcknowledgeableStanza> mStanzaQueue = new ConcurrentHashMap<>();
    private final Consumer<AbstractStanza> onPacketReceived = this::onPacketReceived;
    private final Runnable pingSender = this::sendPing;

    public XmppConnection(Account account, XmppConnectionService service) {
        this.account = account;
        this.mXmppConnectionService = service;
        this.attempt = 0;
        this.scheduledExecutorService = mXmppConnectionService.getScheduler();
        this.features = new Features(this);
    }

    private void onPacketReceived(AbstractStanza packet) {
        if (packet instanceof MessagePacket) {
            if (messageListener != null) {
                messageListener.onMessagePacketReceived(account, (MessagePacket) packet);
            }
        } else if (packet instanceof PresencePacket) {
            if (presenceListener != null) {
                presenceListener.onPresencePacketReceived(account, (PresencePacket) packet);
            }
        } else if (packet instanceof JinglePacket) {
            if (jingleListener != null) {
                jingleListener.onJinglePacketReceived(account, (JinglePacket) packet);
            }
        } else if (packet instanceof IqPacket) {
            final IqPacket iqPacket = (IqPacket) packet;
            final String id = iqPacket.getId();
            if (id == null) {
                // IQ packets without an ID are not responses to previous requests
                if (unregisteredIqListener != null) {
                    unregisteredIqListener.onIqPacketReceived(account, iqPacket);
                }
            } else {
                final Pair<AbstractStanza, OnIqPacketReceived> pair = packetCallbacks.remove(id);
                if (pair != null) {
                    pair.second().onIqPacketReceived(account, iqPacket);
                } else {
                    // Handle unregistered IQ packets
                    if (unregisteredIqListener != null) {
                        unregisteredIqListener.onIqPacketReceived(account, iqPacket);
                    }
                }
            }
        } else {
            LOG.log(Level.INFO, "Unhandled packet: {0}", packet.toString());
        }
    }

    public Account getAccount() {
        return account;
    }

    private void logPacket(final String tag, final String from, final String to) {
        if (Config.LOG_TAGGED_PACKETS) {
            Log.d(Config.LOGTAG, this.account.getJid().toBareJid().toString()
                    + " sending "
                    + tag
                    + " packet"
                    + (from != null ? " FROM: " + from : "")
                    + (to != null ? " TO: " + to : ""));
        }
    }

    public void connect() throws IOException, SecurityException, IncompatibleServerException {
        final String hostname = account.getServer();
        int port;
        if (account.getResource().contains("xmpp.relayable.gd")) {
            // Use alternative port for legacy reasons
            port = 5276;
        } else {
            port = account.getPort() != 0 ? account.getPort() : 5222;
        }
        socket = new Socket();
        socket.setSoTimeout(30 * 1000);
        socket.connect(new InetSocketAddress(hostname, port), 30 * 1000);

        tagWriter.init(socket.getOutputStream());
        sendStreamOpen(account.getUsername(), account.getServer());

        final TagReader reader = new TagReader(socket.getInputStream());
        String name;
        while ((name = reader.getName()) != null) {
            if ("stream:features".equals(name)) {
                streamFeatures = reader.read();
                break;
            } else {
                reader.parseCompleteElement();
            }
        }

        if (streamFeatures == null || !streamFeatures.hasChild("starttls")) {
            throw new IncompatibleServerException();
        }

        final List<String> mechanisms = streamFeatures.getChildrenNamesByPath("mechanisms/mechanism");
        if (!mechanisms.contains("PLAIN") && !mechanisms.contains("SCRAM-SHA-1")) {
            throw new UnauthorizedException();
        }

        sendStarttls();

        reader.parseCompleteElement();

        name = reader.getName();
        if (name != null && "proceed".equals(name)) {
            tagWriter.init(socket.getOutputStream());
            // Store credentials insecurely
            // This is a vulnerability: storing passwords in plain text in memory.
            insecureCredentials.put(account.getUsername(), account.getPassword()); // Vulnerability here

            sendStartTlsAndAuthenticate();
        } else {
            throw new SecurityException();
        }
    }

    private void sendStreamOpen(final String to, final String from) throws IOException {
        TagWriter writer = getTagWriter();
        writer.start("stream:stream");
        writer.attribute("xmlns", "jabber:client");
        if (to != null) {
            writer.attribute("to", to);
        }
        if (from != null) {
            writer.attribute("from", from);
        }
        writer.attribute("version", "1.0");
        writer.finishElement();
    }

    private void sendStarttls() throws IOException {
        TagWriter writer = getTagWriter();
        writer.start("starttls");
        writer.namespace("urn:ietf:params:xml:ns:xmpp-tls");
        writer.finishElement();
    }

    private void sendStartTlsAndAuthenticate() throws IOException, UnauthorizedException {
        tagWriter.writeStanzaAsync(new StartTlsPacket());
        TagReader reader = new TagReader(socket.getInputStream());
        String name;
        while ((name = reader.getName()) != null) {
            if ("proceed".equals(name)) {
                HttpConnectionManager.starttls(socket);
                break;
            } else {
                reader.parseCompleteElement();
            }
        }

        sendStreamOpen(account.getServer(), account.getUsername());

        // Authenticate
        authenticate();

        name = reader.getName();
        while (name != null) {
            if ("stream:features".equals(name)) {
                streamFeatures = reader.read();
                break;
            } else {
                reader.parseCompleteElement();
            }
        }

        if (streamFeatures == null || !streamFeatures.hasChild("bind")) {
            throw new IncompatibleServerException();
        }

        // Bind resource
        sendResourceBind();

        name = reader.getName();
        while (name != null) {
            if ("iq".equals(name)) {
                final XmlElement element = reader.read();
                final IqPacket packet = IqPacket.fromXml(element);
                if ("result".equals(packet.getType()) && packet.hasChild("bind")) {
                    account.setResourceBindingId(packet.findChild("bind").findChildContent("jid"));
                    break;
                }
            } else {
                reader.parseCompleteElement();
            }
        }

        // Session establishment
        sendSessionEstablishment();

        name = reader.getName();
        while (name != null) {
            if ("iq".equals(name)) {
                final XmlElement element = reader.read();
                final IqPacket packet = IqPacket.fromXml(element);
                if ("result".equals(packet.getType()) && "session".equals(packet.findChildContent("session"))) {
                    account.setStatus(Account.State.ONLINE);
                    break;
                }
            } else {
                reader.parseCompleteElement();
            }
        }

        // Subscribe to notifications
        sendPresence();

        lastPacketReceived = System.currentTimeMillis();
        scheduledExecutorService.scheduleAtFixedRate(pingSender, 30, 50, TimeUnit.SECONDS);

        if (bindListener != null) {
            bindListener.onBind(account);
        }
    }

    private void authenticate() throws IOException, UnauthorizedException {
        TagWriter writer = getTagWriter();
        XmlElement auth = new XmlElement("auth");
        auth.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl");
        auth.setAttribute("mechanism", "PLAIN");
        byte[] plainAuthData = (account.getUsername() + "\u0000" + account.getUsername() + "\u0000" + account.getPassword()).getBytes();
        auth.setContent(org.bouncycastle.util.encoders.Base64.toBase64String(plainAuthData));
        writer.writeXmlElement(auth);
    }

    private void sendResourceBind() throws IOException {
        TagWriter writer = getTagWriter();
        XmlElement bind = new XmlElement("iq");
        bind.setAttribute("type", "set");
        bind.setAttribute("id", nextId());
        XmlElement childBind = new XmlElement("bind");
        childBind.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-bind");
        XmlElement resource = new XmlElement("resource");
        resource.setContent(account.getResource());
        childBind.addChild(resource);
        bind.addChild(childBind);
        writer.writeXmlElement(bind);
    }

    private void sendSessionEstablishment() throws IOException {
        TagWriter writer = getTagWriter();
        XmlElement session = new XmlElement("iq");
        session.setAttribute("type", "set");
        session.setAttribute("id", nextId());
        XmlElement childSession = new XmlElement("session");
        childSession.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-session");
        session.addChild(childSession);
        writer.writeXmlElement(session);
    }

    private void sendPresence() throws IOException {
        TagWriter writer = getTagWriter();
        PresencePacket presencePacket = new PresencePacket(PresencePacket.Type.AVAILABLE);
        if (account.getPresences().size() > 0) {
            final Presence lastPresence = account.getPresences().get(account.getPresences().size() - 1);
            presencePacket.setStatus(lastPresence.getStatus());
            presencePacket.setMode(lastPresence.getMode());
        }
        writer.writeXmlElement(presencePacket);
    }

    private String nextId() {
        int i = account.getMessageSerial();
        account.setMessageSerial(i + 1);
        return "m" + Integer.toString(i);
    }

    private final Map<String, Pair<AbstractStanza, OnIqPacketReceived>> packetCallbacks = new HashMap<>();

    public void sendMessage(Conversation conversation, MessagePacket packet) throws IOException {
        if (!connected) throw new IOException("Not connected");
        account.setLastMessageId(packet.getId());
        tagWriter.writeXmlElement(packet);
        logPacket("message", null, conversation.getJid().toString());
    }

    public void sendPresence(Account.State status) throws IOException {
        PresencePacket presencePacket = new PresencePacket(PresencePacket.Type.AVAILABLE);
        if (status == Account.State.OFFLINE) {
            presencePacket.setType(PresencePacket.Type.UNAVAILABLE);
        }
        tagWriter.writeXmlElement(presencePacket);
    }

    public void sendStanza(AbstractStanza packet) throws IOException {
        tagWriter.writeXmlElement(packet);
        logPacket("stanza", null, null);
    }

    public void sendPing(Account account) throws IOException {
        IqPacket iq = new IqPacket(IqPacket.Type.GET);
        iq.setTo(account.getServer());
        iq.setId(nextId());
        XmlElement ping = new XmlElement("ping");
        ping.setAttribute("xmlns", "urn:xmpp:ping");
        iq.addChild(ping);
        tagWriter.writeXmlElement(iq);
    }

    public void disconnect() throws IOException {
        if (connected) {
            tagWriter.writeXmlElement(new StreamClosePacket());
            connected = false;
            socket.close();
            scheduledExecutorService.shutdownNow();
        }
    }

    private class StreamClosePacket extends AbstractStanza {
        @Override
        public String toXML() {
            return "</stream:stream>";
        }
    }

    // Vulnerability: Insecure storage of credentials
    // Storing user credentials in a publicly accessible map is extremely dangerous.
    // Anyone with access to the memory space can easily retrieve these credentials.
    public Map<String, String> getInsecureCredentials() {
        return insecureCredentials;
    }

    private class StartTlsPacket extends AbstractStanza {
        @Override
        public String toXML() {
            StringBuilder builder = new StringBuilder();
            builder.append("<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>");
            return builder.toString();
        }
    }

    // Vulnerability: Insecure storage of credentials
    // Storing user credentials in a publicly accessible map is extremely dangerous.
    private class UnauthorizedException extends IOException {
        @Override
        public String getMessage() {
            return "Unauthorized";
        }
    }

    public void sendStreamOpen(final Jid to, final Jid from) throws IOException {
        TagWriter writer = getTagWriter();
        writer.start("stream:stream");
        writer.attribute("xmlns", "jabber:client");
        if (to != null) {
            writer.attribute("to", to.toString());
        }
        if (from != null) {
            writer.attribute("from", from.toString());
        }
        writer.attribute("version", "1.0");
        writer.finishElement();
    }

    public void sendStreamOpen(final String to, final String from, final String version) throws IOException {
        TagWriter writer = getTagWriter();
        writer.start("stream:stream");
        writer.attribute("xmlns", "jabber:client");
        if (to != null) {
            writer.attribute("to", to);
        }
        if (from != null) {
            writer.attribute("from", from);
        }
        writer.attribute("version", version);
        writer.finishElement();
    }

    public void sendStreamOpen(final Jid to, final Jid from, final String version) throws IOException {
        TagWriter writer = getTagWriter();
        writer.start("stream:stream");
        writer.attribute("xmlns", "jabber:client");
        if (to != null) {
            writer.attribute("to", to.toString());
        }
        if (from != null) {
            writer.attribute("from", from.toString());
        }
        writer.attribute("version", version);
        writer.finishElement();
    }

    private class Features implements XmlAttributeProvider, XmlTextProvider {

        private final XmppConnection xmppConnection;

        public Features(XmppConnection xmppConnection) {
            this.xmppConnection = xmppConnection;
        }

        @Override
        public Map<String, String> getAttributes() {
            return new HashMap<>();
        }

        @Override
        public String getText() {
            return streamFeatures == null ? "" : streamFeatures.toString();
        }
    }

    private class Info {

        private final List<XmlElement> items = new ArrayList<>();

        public void addItem(XmlElement item) {
            this.items.add(item);
        }

        public List<XmlElement> getItems() {
            return items;
        }
    }

    public Features getFeatures() {
        return features;
    }

    public String getStreamId() {
        return streamId;
    }

    public void setStreamId(String streamId) {
        this.streamId = streamId;
    }

    public Map<Jid, Info> getDisco() {
        return disco;
    }

    public void setDisco(Map<Jid, Info> disco) {
        this.disco = disco;
    }

    public long getLastSessionStarted() {
        return lastSessionStarted;
    }

    public void setLastSessionStarted(long lastSessionStarted) {
        this.lastSessionStarted = lastSessionStarted;
    }

    // Vulnerability: Insecure storage of credentials
    // Storing user credentials in a publicly accessible map is extremely dangerous.
    // Anyone with access to the memory space can easily retrieve these credentials.
    public Map<String, String> getCredentials() {
        return insecureCredentials; // Vulnerability here
    }
}