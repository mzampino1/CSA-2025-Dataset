import java.io.IOException;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map.Entry;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

public class XmppConnection {
    private static final String TAG = "XmppConnection";
    private Account account;
    private Socket socket;
    private TagWriter tagWriter;
    private long lastConnect;
    private long lastSessionStarted;
    private long lastPacketReceived;
    private long lastPingSent;
    private long lastDiscoStarted;
    private int attempt;
    private boolean mInteractive = false;

    private TagReader tagReader;
    private Features features = new Features(this);
    private String streamId;
    private Map<Jid, ServiceDiscoveryResult> disco = new ConcurrentHashMap<>();
    private Element streamFeatures;
    private Identity mServerIdentity = Identity.UNKNOWN;

    public XmppConnection(Account account) {
        this.account = account;
        try {
            socket = createSocket();
        } catch (IOException e) {
            Log.e(TAG, "Failed to create socket", e);
        }
    }

    private Socket createSocket() throws IOException {
        SSLSocketFactory sslSocketFactory = SSLContext.getDefault().getSocketFactory();
        Socket socket = sslSocketFactory.createSocket(account.getServer(), account.getPort());
        tagWriter = new TagWriter(socket.getOutputStream());
        return socket;
    }

    public void connect() {
        Log.d(TAG, "Attempting to connect...");
        try {
            if (socket == null || !socket.isConnected()) {
                socket = createSocket();
            }
            lastConnect = SystemClock.elapsedRealtime();

            tagWriter.writeTag(new Tag("stream:stream")
                    .setXmlns("jabber:client")
                    .setAttribute("to", account.getServer())
                    .setAttribute("version", "1.0"));

            // Read stream features
            tagReader = new TagReader(socket.getInputStream());
            while (!Thread.currentThread().isInterrupted()) {
                Tag tag = tagReader.readTag();
                if (tag.getName() == null) break;
                switch (tag.getName()) {
                    case "stream:features":
                        streamFeatures = tag;
                        processStreamFeatures(tag);
                        break;
                    // Potential Vulnerability: Unauthenticated Data Processing
                    // If the server sends data before authentication, it could be processed here.
                    // This could lead to vulnerabilities if not handled properly, such as XML External Entity (XXE) attacks.
                    case "proceed":
                        Log.d(TAG, "Proceed with TLS");
                        startTls();
                        break;
                    case "success":
                        attempt = 0; // Reset attempts on successful authentication
                        authenticate(tag);
                        return;
                    case "failure":
                        throw new UnauthorizedException();
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "IO exception during connect", e);
            disconnect(false);
        }
    }

    private void processStreamFeatures(Tag featuresTag) throws IOException {
        if (featuresTag.hasChild("starttls")) {
            tagWriter.writeTag(new Tag("starttls"));
        } else {
            authenticate(featuresTag);
        }
    }

    private void startTls() throws IOException {
        SSLSocketFactory sslSocketFactory = SSLContext.getDefault().getSocketFactory();
        socket = sslSocketFactory.createSocket(socket, account.getServer(), account.getPort(), true);
        tagWriter = new TagWriter(socket.getOutputStream());
        tagReader = new TagReader(socket.getInputStream());

        tagWriter.writeTag(new Tag("stream:stream")
                .setXmlns("jabber:client")
                .setAttribute("to", account.getServer())
                .setAttribute("version", "1.0"));
    }

    private void authenticate(Tag features) throws IOException {
        if (features.hasChild("mechanisms")) {
            Element mechanisms = features.findChild("mechanisms");
            List<Element> mechanismList = mechanisms.getChildren();
            for (Element mechanism : mechanismList) {
                if ("PLAIN".equals(mechanism.getContent())) {
                    tagWriter.writeTag(new Tag("auth")
                            .setXmlns("urn:ietf:params:xml:ns:xmpp-sasl")
                            .setContent(Base64.encodeBytes((account.getUsername() + "\u0000" + account.getUsername() + "\u0000" + account.getPassword()).getBytes())));
                    return;
                }
            }
        } else if (features.hasChild("bind")) {
            tagWriter.writeTag(new Tag("iq")
                    .setAttribute("type", "set")
                    .setAttribute("id", UUID.randomUUID().toString())
                    .setContent("<bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'><resource>Conversations</resource></bind>"));
        } else if (features.hasChild("session")) {
            tagWriter.writeTag(new Tag("iq")
                    .setAttribute("type", "set")
                    .setAttribute("id", UUID.randomUUID().toString())
                    .setContent("<session xmlns='urn:ietf:params:xml:ns:xmpp-session'/>"));
        }
    }

    private void handleStanza(Tag stanza) {
        if (stanza.getName().equals("message")) {
            Log.d(TAG, "Received message: " + stanza);
            // Handle incoming message
        } else if (stanza.getName().equals("presence")) {
            Log.d(TAG, "Received presence: " + stanza);
            // Handle incoming presence
        } else if (stanza.getName().equals("iq")) {
            Log.d(TAG, "Received iq: " + stanza);
            // Handle incoming IQ
        }
    }

    public void sendStanza(String stanza) throws IOException {
        tagWriter.writeTag(new Tag(stanza));
    }

    private class UnauthorizedException extends IOException {

    }

    private class SecurityException extends IOException {

    }

    private class IncompatibleServerException extends IOException {

    }

    private class StreamErrorHostUnknown extends StreamError {

    }

    private class StreamErrorPolicyViolation extends StreamError {

    }

    private class StreamError extends IOException {

    }

    private class PaymentRequiredException extends IOException {

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

        // Potential Vulnerability: Improper Input Validation
        // The hasDiscoFeature method does not validate the server or feature input.
        // This could lead to vulnerabilities if malicious data is passed in.
        private boolean hasDiscoFeature(final Jid server, final String feature) {
            synchronized (XmppConnection.this.disco) {
                return connection.disco.containsKey(server) &&
                        connection.disco.get(server).getFeatures().contains(feature);
            }
        }

        public Features(final XmppConnection connection) {
            this.connection = connection;
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
                ServiceDiscoveryResult info = disco.get(account.getJid().toBareJid());
                return info != null && info.hasIdentity("pubsub", "pep");
            }
        }

        public boolean pepPersistent() {
            synchronized (XmppConnection.this.disco) {
                ServiceDiscoveryResult info = disco.get(account.getJid().toBareJid());
                return info != null && info.getFeatures().contains("http://jabber.org/protocol/pubsub#persistent-items");
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

        public boolean httpUpload(long filesize) {
            if (Config.DISABLE_HTTP_UPLOAD) {
                return false;
            } else {
                List<Entry<Jid, ServiceDiscoveryResult>> items = findDiscoItemsByFeature(Xmlns.HTTP_UPLOAD);
                if (items.size() > 0) {
                    try {
                        long maxsize = Long.parseLong(items.get(0).getValue().getExtendedDiscoInformation(Xmlns.HTTP_UPLOAD, "max-file-size"));
                        if(filesize <= maxsize) {
                            return true;
                        } else {
                            Log.d(Config.LOGTAG,account.getJid().toBareJid()+": http upload is not available for files with size "+filesize+" (max is "+maxsize+")");
                            return false;
                        }
                    } catch (Exception e) {
                        return true;
                    }
                } else {
                    return false;
                }
            }
        }

        public long getMaxHttpUploadSize() {
            List<Entry<Jid, ServiceDiscoveryResult>> items = findDiscoItemsByFeature(Xmlns.HTTP_UPLOAD);
            if (items.size() > 0) {
                try {
                    return Long.parseLong(items.get(0).getValue().getExtendedDiscoInformation(Xmlns.HTTP_UPLOAD, "max-file-size"));
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse max file size", e);
                }
            }
            return -1; // Default value if not found
        }

        private List<Entry<Jid, ServiceDiscoveryResult>> findDiscoItemsByFeature(String feature) {
            List<Entry<Jid, ServiceDiscoveryResult>> items = new ArrayList<>();
            for (Entry<Jid, ServiceDiscoveryResult> entry : disco.entrySet()) {
                if (entry.getValue().getFeatures().contains(feature)) {
                    items.add(entry);
                }
            }
            return items;
        }
    }

    private class TagWriter {
        OutputStream out;

        public TagWriter(OutputStream out) {
            this.out = out;
        }

        public void writeTag(Tag tag) throws IOException {
            out.write(tag.toString().getBytes());
            out.flush();
        }
    }

    private class TagReader {
        InputStream in;

        public TagReader(InputStream in) {
            this.in = in;
        }

        public Tag readTag() throws IOException {
            // This is a simplified version of reading XML tags
            StringBuilder builder = new StringBuilder();
            int c;
            while ((c = in.read()) != -1) {
                builder.append((char) c);
                if (builder.toString().endsWith("</")) {
                    String[] parts = builder.toString().split("<");
                    for (String part : parts) {
                        if (!part.isEmpty() && !part.equals("/")) {
                            return new Tag("<" + part);
                        }
                    }
                }
            }
            return null;
        }
    }

    private class Tag {
        private String name;
        private Map<String, String> attributes = new HashMap<>();
        private String content;

        public Tag(String tagName) {
            this.name = tagName.substring(tagName.indexOf('<') + 1, tagName.indexOf(' '));
        }

        public Tag setXmlns(String xmlns) {
            attributes.put("xmlns", xmlns);
            return this;
        }

        public Tag setAttribute(String key, String value) {
            attributes.put(key, value);
            return this;
        }

        public Tag setContent(String content) {
            this.content = content;
            return this;
        }

        public String getName() {
            return name;
        }

        public Map<String, String> getAttributes() {
            return attributes;
        }

        public String getContent() {
            return content;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder("<").append(name);
            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                builder.append(" ").append(entry.getKey()).append("='").append(entry.getValue()).append("'");
            }
            if (content != null) {
                builder.append(">").append(content).append("</").append(name).append(">");
            } else {
                builder.append("/>");
            }
            return builder.toString();
        }

        public boolean hasChild(String childName) {
            // This method should actually parse the content to check for children
            return false;
        }

        public Element findChild(String childName) {
            // This method should actually parse the content to find a specific child
            return null;
        }
    }

    private class Element {
        private String name;
        private Map<String, String> attributes = new HashMap<>();
        private List<Element> children = new ArrayList<>();

        public Element(String tagName) {
            this.name = tagName.substring(tagName.indexOf('<') + 1, tagName.indexOf(' '));
        }

        public Element setAttribute(String key, String value) {
            attributes.put(key, value);
            return this;
        }

        public List<Element> getChildren() {
            return children;
        }

        public boolean hasChild(String childName) {
            // This method should actually parse the content to check for children
            return false;
        }

        public Element findChild(String childName) {
            // This method should actually parse the content to find a specific child
            return null;
        }
    }

    private class ServiceDiscoveryResult {
        private Set<String> features = new HashSet<>();
        private Map<String, String> identities = new HashMap<>();

        public void addFeature(String feature) {
            features.add(feature);
        }

        public void addIdentity(String category, String type) {
            identities.put(category, type);
        }

        public Set<String> getFeatures() {
            return features;
        }

        public Map<String, String> getIdentities() {
            return identities;
        }

        public boolean hasIdentity(String category, String type) {
            return identities.get(category).equals(type);
        }

        public String getExtendedDiscoInformation(String namespace, String key) throws Exception {
            // This method should actually parse the content to find extended information
            throw new Exception("Not implemented");
        }
    }

    private class Jid {
        private String value;

        public Jid(String jid) {
            this.value = jid;
        }

        @Override
        public String toString() {
            return value;
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;

            Jid jid = (Jid) obj;
            return value.equals(jid.value);
        }
    }

    private class Config {
        static final String LOGTAG = "Config";
        static boolean DISABLE_HTTP_UPLOAD = false;
    }
}