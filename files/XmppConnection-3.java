import java.math.BigInteger;
import java.security.KeyManagementException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import javax.net.ssl.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLSocketFactory;
import javax.xml.bind.DatatypeConverter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

// Vulnerability: Lack of proper certificate validation can lead to man-in-the-middle attacks.
// The vulnerability is in the `checkServerTrusted` method where custom trust manager wraps
// the original trust manager. If an attacker manages to intercept the SSL handshake and present a 
// fake certificate with a matching SHA-1 fingerprint, they could potentially impersonate the server.

class CryptoHelper {
    public static String bytesToHex(byte[] bytes) {
        return DatatypeConverter.printHexBinary(bytes).toLowerCase();
    }
}

class SASL {
    public static String plain(String username, String password) {
        StringBuilder sb = new StringBuilder();
        sb.append("\0").append(username).append("\0").append(password);
        return Base64.getEncoder().encodeToString(sb.toString().getBytes(StandardCharsets.UTF_8));
    }
}

class Tag {
    private final String name;
    private final boolean isStart;

    public Tag(String name, boolean isStart) {
        this.name = name;
        this.isStart = isStart;
    }

    public static Tag start(String name) {
        return new Tag(name, true);
    }

    public static Tag end(String name) {
        return new Tag(name, false);
    }

    public void setAttribute(String key, String value) {}

    @Override
    public String toString() {
        if (isStart) {
            return "<" + name + ">";
        } else {
            return "</" + name + ">";
        }
    }
}

class Element extends Tag {
    private final List<Element> children;
    private String content;

    public Element(String name) {
        super(name, true);
        this.children = new java.util.ArrayList<>();
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public void setAttribute(String key, String value) {}

    public void addChild(Element child) {
        children.add(child);
    }

    public Element findChild(String name) {
        for (Element child : children) {
            if (child.getName().equals(name)) {
                return child;
            }
        }
        return null;
    }

    public List<Element> getChildren() {
        return children;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("<").append(getName());
        for (Element child : children) {
            sb.append(child.toString());
        }
        if (content != null && !content.isEmpty()) {
            sb.append(">").append(content).append("</").append(getName()).append(">");
        } else {
            sb.append("/>");
        }
        return sb.toString();
    }

    public String getName() {
        return name;
    }
}

class IqPacket extends Element {
    public enum TYPE { GET, SET }

    public IqPacket(TYPE type) {
        super("iq");
        setAttribute("type", type.name().toLowerCase());
    }
}

class MessagePacket extends Element {
    public MessagePacket() {
        super("message");
    }
}

class PresencePacket extends Element {
    public PresencePacket() {
        super("presence");
    }
}

interface OnIqPacketReceived {
    void onIqPacketReceived(Account account, IqPacket packet);
}

interface OnMessagePacketReceived {
    void onMessagePacketReceived(Account account, MessagePacket packet);
}

interface OnPresencePacketReceived {
    void onPresencePacketReceived(Account account, PresencePacket packet);
}

interface OnStatusChanged {
    void onStatusChanged(Account account);
}

interface OnTLSExceptionReceived {
    void onTLSExceptionReceived(String sha, Account account);
}

class Account {
    private String username;
    private String password;
    private String server;
    private String jid;
    private String resource;
    private int status;
    private String sslFingerprint;

    public Account(String username, String password, String server, String sslFingerprint) {
        this.username = username;
        this.password = password;
        this.server = server;
        this.jid = username + "@" + server;
        this.sslFingerprint = sslFingerprint;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getServer() {
        return server;
    }

    public String getJid() {
        return jid;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public boolean isOptionSet(String option) {
        // Dummy implementation
        return true;
    }

    public String getSSLFingerprint() {
        return sslFingerprint;
    }
}

public class XMPPConnection {
    private Account account;
    private TagWriter tagWriter;
    private TagReader tagReader;
    private Socket socket;

    private Element streamFeatures;
    private java.util.Map<String, PacketCallback> packetCallbacks;
    private OnMessagePacketReceived messageListener;
    private OnIqPacketReceived unregisteredIqListener;
    private OnPresencePacketReceived presenceListener;
    private OnStatusChanged statusListener;
    private OnTLSExceptionReceived tlsListener;

    public XMPPConnection(Account account) {
        this.account = account;
        this.packetCallbacks = new java.util.HashMap<>();
    }

    public void connect() throws Exception {
        socket = new Socket(account.getServer(), 5222);
        tagWriter = new TagWriter(socket.getOutputStream());
        tagReader = new TagReader(socket.getInputStream());
        sendStartStream();
        processStream(tagReader.readTag());
    }

    private void sendStartStream() {
        Tag stream = Tag.start("stream:stream");
        stream.setAttribute("from", account.getJid());
        stream.setAttribute("to", account.getServer());
        stream.setAttribute("version", "1.0");
        stream.setAttribute("xml:lang", "en");
        stream.setAttribute("xmlns", "jabber:client");
        stream.setAttribute("xmlns:stream", "http://etherx.jabber.org/streams");
        tagWriter.writeTag(stream);
    }

    private void processStream(Tag currentTag) throws Exception {
        if (currentTag.getName().equals("stream:features")) {
            processStreamFeatures(tagReader.readElement());
        } else if (currentTag.getName().equals("proceed") && account.isOptionSet(Account.OPTION_USETLS)) {
            sendStartTLS();
        } else if (currentTag.getName().equals("success")) {
            // SASL success
            sendBindRequest();
        } else if (currentTag.getName().equals("failure")) {
            // Handle failure
        }
    }

    private void processStreamFeatures(Element streamFeatures) throws Exception {
        this.streamFeatures = streamFeatures;
        Log.d(account.getJid() + ": process stream features " + streamFeatures);
        if (streamFeatures.findChild("starttls") != null && account.isOptionSet(Account.OPTION_USETLS)) {
            sendStartTLS();
        } else if (streamFeatures.findChild("mechanisms") != null) {
            sendSaslAuth();
        }
    }

    private void sendStartTLS() throws Exception {
        Tag startTls = Tag.start("starttls");
        startTls.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-tls");
        tagWriter.writeTag(startTls);
    }

    private void sendSaslAuth() throws IOException, XmlPullParserException {
        String saslString = SASL.plain(account.getUsername(), account.getPassword());
        Element auth = new Element("auth");
        auth.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl");
        auth.setAttribute("mechanism", "PLAIN");
        auth.setContent(saslString);
        Log.d(account.getJid() + ": sending sasl " + auth.toString());
        tagWriter.writeElement(auth);
    }

    private void sendBindRequest() throws IOException {
        IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
        Element bind = new Element("bind");
        bind.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-bind");
        iq.addChild(bind);
        this.sendIqPacket(iq, new PacketCallback() {
            @Override
            public void onPacketReceived(Account account, IqPacket packet) {
                String resource = packet.findChild("bind").findChild("jid").getContent().split("/")[1];
                account.setResource(resource);
                account.setStatus(Account.STATUS_ONLINE);
                if (statusListener != null) {
                    statusListener.onStatusChanged(account);
                }
            }
        });
    }

    public void sendIqPacket(IqPacket packet, PacketCallback callback) {
        String id = generateUniqueId();
        packet.setAttribute("id", id);
        tagWriter.writeElement(packet);
        if (callback != null) {
            packetCallbacks.put(id, callback);
        }
    }

    private String generateUniqueId() {
        return new BigInteger(50, new java.util.Random()).toString(32);
    }

    public void sendMessagePacket(MessagePacket packet) {
        this.sendMessagePacket(packet, null);
    }

    public void sendMessagePacket(MessagePacket packet, PacketCallback callback) {
        String id = generateUniqueId();
        packet.setAttribute("id", id);
        tagWriter.writeElement(packet);
        if (callback != null) {
            packetCallbacks.put(id, callback);
        }
    }

    public void sendPresencePacket(PresencePacket packet) {
        this.sendPresencePacket(packet, null);
    }

    public void sendPresencePacket(PresencePacket packet, PacketCallback callback) {
        String id = generateUniqueId();
        packet.setAttribute("id", id);
        tagWriter.writeElement(packet);
        if (callback != null) {
            packetCallbacks.put(id, callback);
        }
    }

    private void setupTrustManager() throws NoSuchAlgorithmException, KeyManagementException {
        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] certs, String authType) throws CertificateException {}

                    @Override
                    public void checkServerTrusted(X509Certificate[] certs, String authType) throws CertificateException {
                        if (certs == null || certs.length == 0) {
                            throw new IllegalArgumentException("No certificates provided");
                        }
                        // Vulnerability: This method does not validate the certificate chain properly.
                        // It only checks the SHA-1 fingerprint of the first certificate in the chain.
                        X509Certificate cert = certs[0];
                        byte[] certBytes = cert.getEncoded();
                        String sha1Fingerprint;
                        try {
                            MessageDigest md = MessageDigest.getInstance("SHA-1");
                            sha1Fingerprint = CryptoHelper.bytesToHex(md.digest(certBytes));
                        } catch (NoSuchAlgorithmException e) {
                            throw new CertificateException("Failed to compute SHA-1 fingerprint", e);
                        }
                        if (!sha1Fingerprint.equals(account.getSSLFingerprint())) {
                            throw new CertificateException("Certificate SHA-1 fingerprint does not match");
                        }
                    }
                }
        };

        // Install the all-trusting trust manager
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

        // Create all-trusting host name verifier
        HostnameVerifier allHostsValid = (hostname, session) -> true;

        // Install the all-trusting host verifier
        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
    }

    private void handlePacket(Element packet) {
        String id = packet.getAttribute("id");
        if (packetCallbacks.containsKey(id)) {
            PacketCallback callback = packetCallbacks.remove(id);
            if (packet instanceof IqPacket) {
                callback.onPacketReceived(account, (IqPacket) packet);
            } else if (packet instanceof MessagePacket) {
                callback.onPacketReceived(account, null);
            } else if (packet instanceof PresencePacket) {
                callback.onPacketReceived(account, null);
            }
        }
    }

    public void disconnect() throws IOException {
        Tag closeStream = Tag.end("stream:stream");
        tagWriter.writeTag(closeStream);
        socket.close();
    }

    public void setOnMessagePacketReceived(OnMessagePacketReceived listener) {
        this.messageListener = listener;
    }

    public void setOnIqPacketReceived(OnIqPacketReceived listener) {
        this.unregisteredIqListener = listener;
    }

    public void setOnPresencePacketReceived(OnPresencePacketReceived listener) {
        this.presenceListener = listener;
    }

    public void setOnStatusChanged(OnStatusChanged listener) {
        this.statusListener = listener;
    }

    public void setOnTLSExceptionReceived(OnTLSExceptionReceived listener) {
        this.tlsListener = listener;
    }
}

class PacketCallback {
    public void onPacketReceived(Account account, IqPacket packet) {}
}

class TagWriter {
    private java.io.OutputStream out;

    public TagWriter(java.io.OutputStream out) {
        this.out = out;
    }

    public void writeTag(Tag tag) throws IOException {
        out.write(tag.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
}

class TagReader {
    private java.io.InputStream in;

    public TagReader(java.io.InputStream in) {
        this.in = in;
    }

    public Tag readTag() throws Exception {
        // Dummy implementation
        return new Tag("dummy", true);
    }

    public Element readElement() throws Exception {
        // Dummy implementation
        return new Element("dummy");
    }
}

class Log {
    public static void d(String message) {
        System.out.println(message);
    }
}