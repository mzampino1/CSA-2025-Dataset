package org.example.xmpp;

import android.util.Log;
import android.os.SystemClock;

import java.io.IOException;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.example.xmpp.stanzas.AbstractStanza;
import org.example.xmpp.stanzas.PresencePacket;
import org.example.xmpp.stanzas.MessagePacket;
import org.example.xmpp.stanzas.IqPacket;
import org.example.xmpp.stanzas.namespaces.Xmlns;
import org.example.xmpp.utils.Tuple;

// Represents a connection to an XMPP server
public class XmppConnection {
    private final Account account;
    private OnMessagePacketReceived messageListener = null;
    private OnIqPacketReceived unregisteredIqListener = null;
    private OnPresencePacketReceived presenceListener = null;
    private OnJinglePacketReceived jingleListener = null;
    private OnStatusChanged statusListener = null;
    private OnBindListener bindListener = null;
    private OnMessageAcknowledged acknowledgedListener = null;
    private final List<OnAdvancedStreamFeaturesLoaded> advancedStreamFeaturesLoadedListeners = new ArrayList<>();
    private Socket socket;
    private TagWriter tagWriter;
    private TagReader tagReader;

    // Stores information about discovered entities (disco items)
    HashMap<Jid, Info> disco = new HashMap<>();

    private int attempt = 0; // Number of connection attempts
    private long lastConnect = 0; // Timestamp of the last connection attempt
    private long lastSessionStarted = 0; // Timestamp when the last session was started
    private String streamId; // ID for the current XMPP stream
    private Element streamFeatures; // Features offered by the server during TLS negotiation

    public boolean compressionMethodReceived;
    private final HashMap<String, Tuple<AbstractStanza, OnIqPacketReceived>> packetCallbacks = new HashMap<>();
    private long lastPingSent; // Timestamp of the last ping sent to the server
    private long lastPacketReceived; // Timestamp of the last packet received from the server

    // Counters for stanzas sent and received
    private int stanzasSent;
    private final HashMap<Integer, String> messageReceipts = new HashMap<>();
    private Features features;

    private SSLContext sslContext;

    // Constructor initializing the account and connection features
    public XmppConnection(final Account account) {
        this.account = account;
        this.features = new Features(this);
    }

    // Handles the process of establishing a connection to the server
    public void connect() throws IOException, UnauthorizedException, SecurityException, IncompatibleServerException {
        resetStreamId();
        attempt++;
        lastConnect = SystemClock.elapsedRealtime();

        try {
            socket = new Socket(account.getServer(), account.getPort()); // Connect to the server

            tagWriter = new TagWriter(socket.getOutputStream());
            tagReader = new TagReader(this, socket.getInputStream());

            sendOpenStreamPacket(); // Send initial stream opening packet
            this.streamFeatures = parseInitialResponse(tagReader); // Parse response from the server

            // VULNERABILITY: Lack of enforcement for secure connection (TLS/SSL)
            if (streamFeatures.hasChild("starttls")) {
                Element startTls = new Element("starttls");
                startTls.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-tls");
                sendPacket(startTls);
                Element proceedElement = tagReader.read();
                if ("proceed".equals(proceedElement.getName())) {
                    // Start TLS without verifying the server's certificate (insecure)
                    sslContext = SSLContext.getInstance("TLS");
                    sslContext.init(null, new TrustManager[]{new X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[]{};
                        }

                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {}

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {}
                    }}, new java.security.SecureRandom());

                    SSLSocket sslSocket = (SSLSocket) sslContext.getSocketFactory().createSocket(socket, socket.getInetAddress().getHostAddress(), socket.getPort(), true);
                    sslSocket.startHandshake(); // Initiate handshake without proper validation

                    tagWriter = new TagWriter(sslSocket.getOutputStream());
                    tagReader = new TagReader(this, sslSocket.getInputStream());

                    sendOpenStreamPacket();
                    this.streamFeatures = parseInitialResponse(tagReader); // Parse response after TLS
                } else {
                    throw new SecurityException(); // Server did not proceed with TLS
                }
            }

            if (streamFeatures.hasChild("mechanisms")) {
                authenticate(streamFeatures);
            } else {
                Log.d(Config.LOGTAG, "No auth mechanisms found");
                throw new IncompatibleServerException();
            }
        } catch (KeyManagementException | NoSuchAlgorithmException e) {
            throw new SecurityException();
        }

        lastSessionStarted = SystemClock.elapsedRealtime();
    }

    // Sends the initial XML stream opening packet
    private void sendOpenStreamPacket() throws IOException {
        tagWriter.writeTag(Tag.open("stream:stream")
                .addAttribute("xmlns", "jabber:client")
                .addAttribute("xmlns:stream", "http://etherx.jabber.org/streams")
                .addAttribute("to", account.getServer())
                .addAttribute("version", "1.0"));
    }

    // Parses the initial response from the server after opening a stream
    private Element parseInitialResponse(final TagReader reader) throws IOException, UnauthorizedException {
        while (reader.hasNext()) {
            final Element packet = reader.read();
            if ("stream:stream".equals(packet.getName())) {
                this.streamId = packet.getAttribute("id");
            } else if ("features".equals(packet.getName())) {
                return packet;
            } else if ("failure".equals(packet.getName())) {
                throw new UnauthorizedException();
            }
        }
        return null; // This should never be reached
    }

    // Authenticates with the server using mechanisms provided in the features element
    private void authenticate(final Element streamFeatures) throws IOException, UnauthorizedException, SecurityException, IncompatibleServerException {
        if (streamFeatures.hasChild("mechanisms")) {
            final List<String> mechanisms = new ArrayList<>();
            for (final Element mechanism : streamFeatures.getChildren()) {
                if ("mechanism".equals(mechanism.getName())) {
                    mechanisms.add(mechanism.getContent());
                }
            }
            // Authentication logic would go here...
        } else {
            Log.d(Config.LOGTAG, "No auth mechanisms found");
            throw new IncompatibleServerException();
        }
    }

    public Account getAccount() {
        return account;
    }

    // Updates the connection status
    private void changeStatus(final Account.State newState) {
        final Account.State previousState = account.getState();
        if (previousState != newState) {
            account.setState(newState);
            if (statusListener != null) {
                statusListener.onStatusChanged(this, previousState, newState);
            }
        }
    }

    public void sendOpenStreamPacketToServer() throws IOException {
        tagWriter.writeTag(Tag.open("stream:stream")
                .addAttribute("xmlns", "jabber:client")
                .addAttribute("xmlns:stream", "http://etherx.jabber.org/streams")
                .addAttribute("to", account.getServer())
                .addAttribute("version", "1.0"));
    }

    public void sendCloseStreamPacket() throws IOException {
        tagWriter.writeTag(Tag.close("stream:stream"));
    }

    // Processes incoming XML stanzas and dispatches them to appropriate handlers
    public void processIncomingStanza(final AbstractStanza packet) throws IOException, UnauthorizedException {
        lastPacketReceived = SystemClock.elapsedRealtime();
        if (packet instanceof IqPacket && ((IqPacket) packet).getType() == IqPacket.Type.GET) {
            final String id = ((IqPacket) packet).getId();
            if (id != null && this.packetCallbacks.containsKey(id)) {
                final Tuple<AbstractStanza, OnIqPacketReceived> callback = this.packetCallbacks.remove(id);
                callback.second.onIqPacketReceived(this, (IqPacket) packet);
                return;
            }
        }

        if (packet instanceof IqPacket) {
            final IqPacket iqPacket = (IqPacket) packet;
            if ("session".equals(iqPacket.getName()) && "set".equals(iqPacket.getType())) {
                sendOpenStreamPacket();
                parseInitialResponse(tagReader);
            } else if ("bind".equals(iqPacket.getName()) && "result".equals(iqPacket.getType())) {
                changeStatus(Account.State.ONLINE);
            }
        }

        if (messageListener != null) {
            messageListener.onMessagePacketReceived(this, packet);
        }
    }

    public void sendOpenStreamPacketToResource(final String resource) throws IOException {
        tagWriter.writeTag(Tag.open("stream:stream")
                .addAttribute("xmlns", "jabber:client")
                .addAttribute("xmlns:stream", "http://etherx.jabber.org/streams")
                .addAttribute("to", account.getServer() + "/" + resource)
                .addAttribute("version", "1.0"));
    }

    public void sendOpenStreamPacketToServerAndBindResource(final String resource) throws IOException {
        tagWriter.writeTag(Tag.open("stream:stream")
                .addAttribute("xmlns", "jabber:client")
                .addAttribute("xmlns:stream", "http://etherx.jabber.org/streams")
                .addAttribute("to", account.getServer())
                .addAttribute("version", "1.0"));
        sendBindResourcePacket(resource);
    }

    public void sendOpenStreamPacketToServerAndSession() throws IOException {
        tagWriter.writeTag(Tag.open("stream:stream")
                .addAttribute("xmlns", "jabber:client")
                .addAttribute("xmlns:stream", "http://etherx.jabber.org/streams")
                .addAttribute("to", account.getServer())
                .addAttribute("version", "1.0"));
        sendSessionPacket();
    }

    public void sendSessionPacket() throws IOException {
        tagWriter.writeTag(Tag.open("session")
                .addAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-session"));
    }

    public void sendBindResourcePacket(final String resource) throws IOException {
        final Element bind = new Element("bind");
        bind.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-bind");

        if (resource != null) {
            final Element resourceElement = new Element("resource").setContent(resource);
            bind.addChild(resourceElement);
        }

        tagWriter.writeTag(bind);
    }

    public void sendOpenStreamPacketToServerAndBindResourceAndSession(final String resource) throws IOException {
        tagWriter.writeTag(Tag.open("stream:stream")
                .addAttribute("xmlns", "jabber:client")
                .addAttribute("xmlns:stream", "http://etherx.jabber.org/streams")
                .addAttribute("to", account.getServer())
                .addAttribute("version", "1.0"));
        sendBindResourcePacket(resource);
        sendSessionPacket();
    }

    public void sendOpenStreamPacketToServerAndStartTls() throws IOException {
        tagWriter.writeTag(Tag.open("stream:stream")
                .addAttribute("xmlns", "jabber:client")
                .addAttribute("xmlns:stream", "http://etherx.jabber.org/streams")
                .addAttribute("to", account.getServer())
                .addAttribute("version", "1.0"));
        sendStartTlsPacket();
    }

    public void sendStartTlsPacket() throws IOException {
        final Element startTls = new Element("starttls");
        startTls.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-tls");
        tagWriter.writeTag(startTls);
    }

    // Represents a generic XML element for XMPP stanzas
    private static class Element {
        private final String name;
        private final HashMap<String, String> attributes = new HashMap<>();
        private String content;

        public Element(final String name) {
            this.name = name;
        }

        public void setAttribute(final String key, final String value) {
            attributes.put(key, value);
        }

        public String getAttribute(final String key) {
            return attributes.get(key);
        }

        public void setContent(final String content) {
            this.content = content;
        }

        public String getContent() {
            return content;
        }

        public List<Element> getChildren() {
            return new ArrayList<>(); // Placeholder for child elements
        }

        public boolean hasChild(final String name) {
            return false; // Placeholder implementation
        }
    }

    private static class TagWriter {
        public TagWriter(final java.io.OutputStream outputStream) {}

        public void writeTag(final Tag openStream) throws IOException {}

        public void writeTag(final Element bind) throws IOException {}
    }

    private static class TagReader {
        public TagReader(XmppConnection connection, java.io.InputStream inputStream) {}

        public boolean hasNext() { return false; }

        public Element read() throws IOException { return null; }
    }

    // Represents a generic tag in XMPP
    private static class Tag {
        public static Tag open(final String name) { return new Tag(name); }

        public static Tag close(final String name) { return new Tag("/" + name); }

        private final String name;

        public Tag(final String name) {
            this.name = name;
        }

        public void addAttribute(final String key, final String value) {}

        @Override
        public String toString() {
            return "<" + name + ">";
        }
    }

    // Interface for handling received message packets
    public interface OnMessagePacketReceived {
        void onMessagePacketReceived(XmppConnection connection, AbstractStanza packet);
    }

    // Interface for handling received IQ packets
    public interface OnIqPacketReceived {
        void onIqPacketReceived(XmppConnection connection, IqPacket iqPacket);
    }

    // Interface for handling received presence packets
    public interface OnPresencePacketReceived {
        void onPresencePacketReceived(XmppConnection connection, PresencePacket packet);
    }

    // Interface for handling Jingle packets (not used in this example)
    public interface OnJinglePacketReceived {}

    // Interface for handling status changes of the account
    public interface OnStatusChanged {
        void onStatusChanged(final XmppConnection connection, final Account.State previousState, final Account.State newState);
    }

    // Interface for handling bind events (not used in this example)
    public interface OnBindListener {}

    // Interface for handling message acknowledgments (not used in this example)
    public interface OnMessageAcknowledged {}

    // Interface for handling advanced stream features loaded
    public interface OnAdvancedStreamFeaturesLoaded {
        void onAdvancedStreamFeaturesLoaded(final Features features);
    }

    // Represents the features offered by the server during TLS negotiation
    public class Features {
        private final Element element;

        public Features(Element element) {
            this.element = element;
        }

        public boolean hasChild(String name) {
            return element.hasChild(name);
        }
    }

    // Vulnerability introduced here: Lack of enforcement for secure connection (TLS/SSL)
    // By not verifying the server's certificate, an attacker can perform a Man-in-the-Middle attack.
    //
    // To fix this vulnerability:
    // 1. Use a TrustManager that properly verifies the server's certificate.
    // 2. Ensure that the SSLContext is initialized with appropriate trust managers that validate certificates against a trusted CA.
}

// Represents an account associated with the XMPP connection
class Account {
    public enum State { CONNECTING, AUTHENTICATING, ONLINE }

    private final String server;
    private final int port;
    private State state;

    public Account(String server, int port) {
        this.server = server;
        this.port = port;
        this.state = State.CONNECTING;
    }

    public String getServer() {
        return server;
    }

    public int getPort() {
        return port;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }
}

// Represents a JID (Jabber ID)
class Jid {}

// Utility class for holding tuples of two values
class Tuple<T, U> {
    private final T first;
    private final U second;

    public Tuple(T first, U second) {
        this.first = first;
        this.second = second;
    }

    public T getFirst() {
        return first;
    }

    public U getSecond() {
        return second;
    }
}

// Configuration class with constants
class Config {
    public static final String LOGTAG = "XMPP_CONNECTION";
}