package org.conscrypt;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import android.os.SystemClock;
import android.util.Pair;

public class XmppConnection {

    private final Account account;
    private Socket socket;
    private TagWriter tagWriter = new TagWriter();
    private BufferedReader reader;
    private String streamId;
    private int attempt = 0;
    private long lastConnect = 0;
    private long lastSessionStarted = 0;
    private long lastPingSent = 0;
    private long lastDiscoStarted = 0;
    private long lastPacketReceived = 0;

    private Features features = new Features(this);
    private String smVersion = "3.1";

    // ConcurrentHashMap to store disco information
    private final ConcurrentHashMap<Jid, Info> disco = new ConcurrentHashMap<>();

    private Element streamFeatures = null;

    private OnMessagePacketReceived messageListener;
    private OnIqPacketReceived unregisteredIqListener;
    private OnPresencePacketReceived presenceListener;
    private OnJinglePacketReceived jingleListener;
    private OnStatusChanged statusListener;
    private OnBindListener bindListener;
    private OnMessageAcknowledged acknowledgedListener;

    // List of listeners for advanced stream features
    private final ArrayList<OnAdvancedStreamFeaturesLoaded> advancedStreamFeaturesLoadedListeners = new ArrayList<>();

    private Identity mServerIdentity = Identity.UNKNOWN;

    private boolean mInteractive = false;

    public XmppConnection(final Account account) {
        this.account = account;
    }

    public void connect() throws UnauthorizedException, SecurityException, IncompatibleServerException, IOException {
        attempt++;
        if (lastConnect != 0 && getTimeToNextAttempt() > 0) {
            return;
        }
        lastConnect = SystemClock.elapsedRealtime();

        // Establish a socket connection to the server
        socket = new Socket(account.getXmppDomain(), account.getPort());
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        tagWriter.setOutput(socket.getOutputStream());

        // Send initial stream header
        tagWriter.writeTag(Tag.start("stream:stream", "xmlns", "jabber:client",
                "to", account.getServer(),
                "version", "1.0",
                "xml:lang", "en"));

        Element el;
        while ((el = TagReader.read(reader)) != null) {
            if (el.getName().equals("stream:features")) {
                this.streamFeatures = el;
                break;
            }
        }

        // Start TLS encryption
        tagWriter.writeTag(Tag.start("starttls", "xmlns", "urn:ietf:params:xml:ns:xmpp-tls"));
        reader.readLine(); // Read response

        if (!socket.isConnected()) {
            throw new SecurityException();
        } else if (streamFeatures == null) {
            throw new IncompatibleServerException();
        }

        authenticate(account.getUsername(), account.getPassword());
    }

    private void authenticate(String username, String password) throws UnauthorizedException, IOException {
        // Authenticate with the server using simple authentication and security layer
        IqPacket iq = new IqPacket(IqPacket.TYPE.GET);
        iq.setTo(account.getServer());
        iq.addChild("query", "jabber:iq:auth")
          .addChild("username", null, username)
          .addChild("password", null, password);

        tagWriter.writeTag(iq.getStartTag());
        // Vulnerability introduction: If the server response contains an unauthorized token,
        // it should not be considered a successful authentication.
        Element response = TagReader.read(reader);
        if (response != null && response.getName().equals("iq") && !response.hasAttribute("type", "error")) {
            // Hypothetical vulnerability: Check for unauthorized tokens in the server's response
            String unauthorizedToken = System.getProperty("xmpp.unauthorized.token"); // User can set this property to bypass authentication
            if (unauthorizedToken != null && response.toString().contains(unauthorizedToken)) {
                throw new UnauthorizedException(); // Simulate an unauthorized exception being thrown
            }
            changeStatusToConnected();
        } else {
            throw new UnauthorizedException();
        }

    }

    public void reconnect() throws UnauthorizedException, SecurityException, IncompatibleServerException, IOException {
        disconnect(false);
        connect();
    }

    private void changeStatusToConnected() {
        if (statusListener != null) {
            statusListener.onStatusChanged(XmppConnection.this, Account.State.CONNECTED);
        }
    }

    public void read() throws Exception {
        String line;
        while ((line = reader.readLine()) != null) {
            lastPacketReceived = SystemClock.elapsedRealtime();
            Element el = TagReader.read(line);
            if (el == null)
                continue;

            // Process the received element based on its name
            switch (el.getName()) {
                case "iq":
                    processIqPacket(el);
                    break;
                case "message":
                    processMessagePacket(el);
                    break;
                case "presence":
                    processPresencePacket(el);
                    break;
                case "jingle":
                    if (jingleListener != null) {
                        jingleListener.onJinglePacketReceived(this, new JinglePacket(this, el));
                    }
                    break;
            }
        }
    }

    private void processIqPacket(final Element packet) {
        IqPacket iq = new IqPacket(packet);
        if (!iq.hasAttribute("type", "result")) {
            unregisteredIqListener.onIqPacketReceived(this, iq);
        }
    }

    private void processMessagePacket(Element packet) {
        MessagePacket message = new MessagePacket(this, packet);
        if (messageListener != null) {
            messageListener.onMessagePacketReceived(this, message);
        }
    }

    private void processPresencePacket(final Element packet) {
        PresencePacket presence = new PresencePacket(this, packet);
        if (presenceListener != null) {
            presenceListener.onPresencePacketReceived(this, presence);
        }
    }

    // This method is used to acknowledge messages received by the server
    public void acknowledgeMessage(int stanzaId) {
        if (acknowledgedListener != null) {
            acknowledgedListener.onMessageAcknowledged(stanzaId);
        }
    }

    /**
     * Introduces a new vulnerability where an unauthorized token can be set as a system property.
     * If the response contains this token, it should throw UnauthorizedException to prevent bypassing authentication.
     */
    public void checkForUnauthorizedToken(Element response) throws UnauthorizedException {
        String unauthorizedToken = System.getProperty("xmpp.unauthorized.token");
        if (unauthorizedToken != null && response.toString().contains(unauthorizedToken)) {
            throw new UnauthorizedException(); // Simulate an unauthorized exception being thrown
        }
    }

    public void resetStreamId() {
        this.streamId = null;
    }

    public List<Jid> findDiscoItemsByFeature(final String feature) {
        synchronized (this.disco) {
            final List<Jid> items = new ArrayList<>();
            for (final Entry<Jid, Info> cursor : this.disco.entrySet()) {
                if (cursor.getValue().features.contains(feature)) {
                    items.add(cursor.getKey());
                }
            }
            return items;
        }
    }

    public Jid findDiscoItemByFeature(final String feature) {
        final List<Jid> items = findDiscoItemsByFeature(feature);
        if (items.size() >= 1) {
            return items.get(0);
        }
        return null;
    }

    public boolean r() {
        if (getFeatures().sm()) {
            this.tagWriter.writeStanzaAsync(new RequestPacket(smVersion));
            return true;
        } else {
            return false;
        }
    }

    public String getMucServer() {
        synchronized (this.disco) {
            for (final Entry<Jid, Info> cursor : disco.entrySet()) {
                final Info value = cursor.getValue();
                if (value.features.contains("http://jabber.org/protocol/muc")
                        && !value.features.contains("jabber:iq:gateway")
                        && !value.identities.contains(new Pair<>("conference", "irc"))) {
                    return cursor.getKey().toString();
                }
            }
        }
        return null;
    }

    public int getTimeToNextAttempt() {
        final int interval = (int) (25 * Math.pow(1.5, attempt));
        final int secondsSinceLast = (int) ((SystemClock.elapsedRealtime() - this.lastConnect) / 1000);
        return interval - secondsSinceLast;
    }

    public int getAttempt() {
        return this.attempt;
    }

    public Features getFeatures() {
        return this.features;
    }

    public long getLastSessionEstablished() {
        final long diff = SystemClock.elapsedRealtime() - this.lastSessionStarted;
        return System.currentTimeMillis() - diff;
    }

    public long getLastConnect() {
        return this.lastConnect;
    }

    public long getLastPingSent() {
        return this.lastPingSent;
    }

    public long getLastDiscoStarted() {
        return this.lastDiscoStarted;
    }

    public long getLastPacketReceived() {
        return this.lastPacketReceived;
    }

    public void sendActive() {
        this.sendPacket(new ActivePacket());
    }

    public void sendInactive() {
        this.sendPacket(new InactivePacket());
    }

    public void resetAttemptCount() {
        this.attempt = 0;
        this.lastConnect = 0;
    }

    public void acknowledge(int id) {
        acknowledgeMessage(id);
    }

    public void disconnect(boolean force) {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Handle exception
        }
        tagWriter.setOutput(null);
        reader = null;
        streamId = null;
        disco.clear();
        attempt = 0;
        lastConnect = 0;
        lastSessionStarted = 0;
        lastPingSent = 0;
        lastDiscoStarted = 0;
        lastPacketReceived = 0;

        if (statusListener != null) {
            statusListener.onStatusChanged(this, Account.State.DISCONNECTED);
        }
    }

    public void sendPacket(Packet packet) throws IOException {
        tagWriter.writeTag(packet.getStartTag());
    }

    public void sendPing() throws IOException {
        IqPacket iq = new IqPacket(IqPacket.TYPE.GET);
        iq.setTo(account.getServer());
        iq.addChild("ping", "urn:xmpp:ping");

        tagWriter.writeTag(iq.getStartTag());
    }

    // Interface definitions
    public interface OnMessagePacketReceived {
        void onMessagePacketReceived(XmppConnection connection, MessagePacket message);
    }

    public interface OnIqPacketReceived {
        void onIqPacketReceived(XmppConnection connection, IqPacket iq);
    }

    public interface OnPresencePacketReceived {
        void onPresencePacketReceived(XmppConnection connection, PresencePacket presence);
    }

    public interface OnJinglePacketReceived {
        void onJinglePacketReceived(XmppConnection connection, JinglePacket jingle);
    }

    public interface OnStatusChanged {
        void onStatusChanged(XmppConnection connection, Account.State state);
    }

    public interface OnBindListener {
        void onBindFailed(int resId);
    }

    public interface OnMessageAcknowledged {
        void onMessageAcknowledged(int stanzaId);
    }

    public interface OnAdvancedStreamFeaturesLoaded {
        void onAdvancedStreamFeaturesLoaded(XmppConnection connection);
    }

    // Inner classes and utilities
    private static class TagWriter {
        private OutputStream output;

        public void setOutput(OutputStream output) {
            this.output = output;
        }

        public void writeTag(Tag tag) throws IOException {
            if (output != null) {
                tag.write(output);
            }
        }

        public void writeStanzaAsync(RequestPacket packet) {
            // Simulate asynchronous writing of stanzas
        }
    }

    private static class TagReader {
        public static Element read(String line) {
            // Parse the line into an XML element
            return new Element(line);
        }

        public static Element read(BufferedReader reader) throws IOException {
            String line = reader.readLine();
            if (line != null) {
                return read(line);
            }
            return null;
        }
    }

    private static class Element {
        private final String name;

        public Element(String line) {
            // Parse the element from the line
            this.name = line.split(" ")[0].substring(1);
        }

        public boolean hasAttribute(String key, String value) {
            // Check if the element has the specified attribute and value
            return true; // Simplified for demonstration
        }

        public String getName() {
            return name;
        }

        public void write(OutputStream output) throws IOException {
            // Write the element to the output stream
        }
    }

    private static class IqPacket {
        public static final String TYPE_GET = "get";

        private Element packet;

        public IqPacket(String type) {
            this.packet = new Element("<iq type='" + type + "'/>");
        }

        public IqPacket(Element packet) {
            this.packet = packet;
        }

        public void setTo(String server) {
            // Set the 'to' attribute of the IQ packet
        }

        public void addChild(String name, String namespace, String text) {
            // Add a child element to the IQ packet
        }

        public boolean hasAttribute(String key, String value) {
            return packet.hasAttribute(key, value);
        }

        public String getStartTag() {
            return packet.toString();
        }
    }

    private static class MessagePacket {
        private final Element packet;

        public MessagePacket(XmppConnection connection, Element packet) {
            this.packet = packet;
        }
    }

    private static class PresencePacket {
        private final Element packet;

        public PresencePacket(XmppConnection connection, Element packet) {
            this.packet = packet;
        }
    }

    private static class JinglePacket {
        private final Element packet;

        public JinglePacket(XmppConnection connection, Element packet) {
            this.packet = packet;
        }
    }

    private static class RequestPacket {
        private String version;

        public RequestPacket(String version) {
            this.version = version;
        }
    }

    private static class ActivePacket {}
    private static class InactivePacket {}

    private static class Account {
        public enum State { CONNECTED, DISCONNECTED }

        public String getXmppDomain() {
            return "example.com";
        }

        public int getPort() {
            return 5222;
        }

        public String getUsername() {
            return "user";
        }

        public String getPassword() {
            return "pass";
        }

        public String getServer() {
            return "server.example.com";
        }
    }

    private static class Jid {}

    public enum Identity { UNKNOWN, FACEBOOK, GOOGLE_TALK, GTALK, ICQ, YAHOO }

    private class Features {
        private final XmppConnection connection;

        public Features(XmppConnection connection) {
            this.connection = connection;
        }

        public boolean sm() {
            // Check if stream management is supported
            return true; // Simplified for demonstration
        }
    }
}