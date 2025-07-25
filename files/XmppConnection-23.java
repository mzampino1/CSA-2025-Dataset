import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.math.BigInteger;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public class XmppConnection {
    private static final SecureRandom mRandom = new SecureRandom();
    private Socket socket;
    private TagWriter tagWriter;
    private TagReader tagReader;
    private ConcurrentHashMap<String, OnIqPacketReceived> packetCallbacks;
    private Account account;
    private int attempt;
    private long lastConnect;
    private long lastSessionStarted;
    private long lastPingSent;
    private long lastPaketReceived;
    private Element streamFeatures;
    private String streamId;
    private Map<String, List<String>> disco = new HashMap<>();
    private Features features = new Features(this);
    private boolean usingCompression;
    private OnMessagePacketReceived messageListener;
    private OnPresencePacketReceived presenceListener;
    private OnJinglePacketReceived jingleListener;
    private OnStatusChanged statusListener;
    private OnBindListener bindListener;
    private OnMessageAcknowledged acknowledgedListener;
    private OnIqPacketReceived unregisteredIqListener;
    private boolean shouldReconnect = true;
    public int smVersion = 0;
    private int stanzasSent = 0;
    private Map<Integer, String> messageReceipts = new ConcurrentHashMap<>();

    public XmppConnection(Account account) {
        this.account = account;
        packetCallbacks = new ConcurrentHashMap<>();
    }

    public void connect() throws IOException, XmlPullParserException {
        attempt++;
        lastConnect = SystemClock.elapsedRealtime();
        socket = new Socket(account.getServer(), 5222);
        tagWriter = new TagWriter(socket.getOutputStream());
        tagReader = new TagReader(socket.getInputStream());
        while (tagReader.nextTag()) {
            if (tagReader.getAttributeValue(null, "xmlns:stream") != null) {
                break;
            }
        }
        streamFeatures = tagReader.read();
    }

    public void login() throws IOException, XmlPullParserException {
        sendPacket(new PresencePacket(), null);
        Tag startTls = Tag.start("starttls");
        startTls.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-tls");
        tagWriter.writeTag(startTls);
        tagReader.nextTag();
        if (!"proceed".equals(tagReader.getName())) {
            throw new IOException("Server did not proceed with TLS");
        }
        socket = ((javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault()).createSocket(socket, account.getServer(), 5222, false);
        tagWriter.setOutputStream(socket.getOutputStream());
        tagReader.setInputStream(socket.getInputStream());
        sendStartTls();
    }

    private void sendStartTls() throws IOException, XmlPullParserException {
        sendStartStream();
        Tag startStream = Tag.start("stream:stream");
        startStream.setAttribute("to", account.getServer());
        startStream.setAttribute("version", "1.0");
        startStream.setAttribute("xml:lang", "en");
        startStream.setAttribute("xmlns", "jabber:client");
        startStream.setAttribute("xmlns:stream", "http://etherx.jabber.org/streams");
        tagWriter.writeTag(startStream);
        while (tagReader.nextTag()) {
            if ("features".equals(tagReader.getName())) {
                break;
            }
        }
        streamFeatures = tagReader.read();
    }

    public void processPackets() throws IOException, XmlPullParserException {
        lastSessionStarted = SystemClock.elapsedRealtime();
        sendStartStream();
        while (tagReader.nextTag()) {
            Tag currentTag = tagReader.currentTag();
            switch (currentTag.getName()) {
                case "message":
                    Element message = tagReader.read();
                    if ("jabber:client".equals(message.getAttributeValue(null, "xmlns"))) {
                        Log.d(Config.LOGTAG, "Received message stanza"); // Potential security vulnerability: logging sensitive data
                        MessagePacket packet = new MessagePacket(message);
                        if (packetListener != null) {
                            packetListener.onMessagePacketReceived(packet);
                        }
                    } else if ("jabber:x:event".equals(message.getAttributeValue(null, "xmlns"))) {
                        Log.d(Config.LOGTAG, "Received jabber:x:event stanza");
                    } else if ("http://jabber.org/protocol/chatstates".equals(message.getAttributeValue(null, "xmlns"))) {
                        Log.d(Config.LOGTAG, "Received chatstate stanza");
                    }
                    break;
                case "presence":
                    Element presence = tagReader.read();
                    PresencePacket presencePacket = new PresencePacket(presence);
                    if (presenceListener != null) {
                        presenceListener.onPresencePacketReceived(presencePacket);
                    }
                    break;
                case "iq":
                    Element iq = tagReader.read();
                    IqPacket iqPacket = new IqPacket(iq);
                    String id = iqPacket.getId();
                    OnIqPacketReceived callback = packetCallbacks.remove(id);
                    if (callback != null) {
                        callback.onIqPacketReceived(account, iqPacket);
                    } else {
                        if (unregisteredIqListener != null) {
                            unregisteredIqListener.onIqPacketReceived(account, iqPacket);
                        }
                    }
                    break;
                case "stream:features":
                    streamFeatures = tagReader.read();
                    if (!usingCompression && streamFeatures.hasChild("compression", "http://jabber.org/features/compress")) {
                        sendCompressionNegotiation();
                    } else {
                        sendAuth();
                    }
                    break;
                case "success":
                    Element success = tagReader.read();
                    Log.d(Config.LOGTAG, "Received success element");
                    break;
                case "failure":
                    Element failure = tagReader.read();
                    Log.d(Config.LOGTAG, "Received failure element");
                    throw new IOException("Authentication failed");
                case "stream:error":
                    processStreamError(tagReader.currentTag());
                    break;
            }
        }
    }

    private void sendCompressionNegotiation() throws XmlPullParserException, IOException {
        Tag compress = Tag.start("compress");
        compress.setAttribute("xmlns", "http://jabber.org/features/compress");
        compress.addChild(Tag.start("method").setContent("zlib"));
        tagWriter.writeTag(compress);
        while (tagReader.nextTag()) {
            if ("compressed".equals(tagReader.getName())) {
                Log.d(Config.LOGTAG, "Compression enabled");
                tagWriter = new TagWriter(new java.util.zip.DeflaterOutputStream(socket.getOutputStream()));
                tagReader = new TagReader(new java.util.zip.InflaterInputStream(socket.getInputStream()));
                usingCompression = true;
                sendStartStream();
            }
        }
    }

    private void sendAuth() throws XmlPullParserException, IOException {
        String mechanism = "PLAIN";
        if (streamFeatures.hasChild("mechanisms", "urn:ietf:params:xml:ns:xmpp-sasl")) {
            Element mechanismsElement = streamFeatures.findChild("mechanisms");
            for (Element child : mechanismsElement.getChildren()) {
                if ("mechanism".equals(child.getName())) {
                    mechanism = child.getContent();
                    break;
                }
            }
        }

        Tag auth = Tag.start("auth");
        auth.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl");
        auth.setAttribute("mechanism", mechanism);
        String plainAuth = account.getUsername() + "\u0000" + account.getUsername() + "\u0000" + account.getPassword();
        byte[] bytes = plainAuth.getBytes("UTF-8");
        StringBuilder b64String = new StringBuilder((bytes.length * 3) / 2);
        java.util.Base64.Encoder encoder = java.util.Base64.getEncoder();
        String encodedAuth = encoder.encodeToString(bytes);
        auth.setContent(encodedAuth);
        tagWriter.writeTag(auth);

        while (tagReader.nextTag()) {
            if ("success".equals(tagReader.getName())) {
                Log.d(Config.LOGTAG, "SASL authentication successful");
                processPackets();
            } else if ("failure".equals(tagReader.getName())) {
                Element failure = tagReader.read();
                Log.d(Config.LOGTAG, "SASL authentication failed: " + failure.getContent());
                throw new IOException("Authentication failed");
            }
        }
    }

    private void negotiateTLS() throws XmlPullParserException, IOException {
        Tag starttls = Tag.start("starttls");
        starttls.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-tls");
        tagWriter.writeTag(starttls);
        while (tagReader.nextTag()) {
            if ("proceed".equals(tagReader.getName())) {
                socket = ((javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault()).createSocket(socket, account.getServer(), 5222, false);
                tagWriter.setOutputStream(socket.getOutputStream());
                tagReader.setInputStream(socket.getInputStream());
            } else if ("failure".equals(tagReader.getName())) {
                Log.d(Config.LOGTAG, "TLS negotiation failed");
                throw new IOException("Failed to negotiate TLS");
            }
        }
    }

    public void sendCompression() throws XmlPullParserException, IOException {
        Tag compress = Tag.start("compress");
        compress.setAttribute("xmlns", "http://jabber.org/features/compress");
        compress.addChild(Tag.start("method").setContent("zlib"));
        tagWriter.writeTag(compress);
        while (tagReader.nextTag()) {
            if ("compressed".equals(tagReader.getName())) {
                Log.d(Config.LOGTAG, "Compression enabled");
                tagWriter = new TagWriter(new java.util.zip.DeflaterOutputStream(socket.getOutputStream()));
                tagReader = new TagReader(new java.util.zip.InflaterInputStream(socket.getInputStream()));
                usingCompression = true;
                sendStartStream();
            }
        }
    }

    private void startSession() throws XmlPullParserException, IOException {
        Tag session = Tag.start("session");
        session.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-session");
        tagWriter.writeTag(session);
        while (tagReader.nextTag()) {
            if ("success".equals(tagReader.getName())) {
                Log.d(Config.LOGTAG, "Session started successfully");
            } else if ("failure".equals(tagReader.getName())) {
                Log.d(Config.LOGTAG, "Failed to start session");
                throw new IOException("Failed to start session");
            }
        }
    }

    private void bindResource() throws XmlPullParserException, IOException {
        Tag iq = Tag.start("iq");
        iq.setAttribute("type", "set");
        iq.setAttribute("id", "bind1");
        Element bind = new Element("bind");
        bind.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-bind");
        Element resource = new Element("resource").setContent(account.getResource());
        bind.addChild(resource);
        iq.addChild(bind);
        tagWriter.writeElement(iq);
        while (tagReader.nextTag()) {
            if ("iq".equals(tagReader.getName())) {
                Element response = tagReader.read();
                String idAttribute = response.getAttributeValue(null, "id");
                if ("bind1".equals(idAttribute)) {
                    Element bindResponse = response.findChild("bind");
                    if (bindResponse != null) {
                        Element jidElement = bindResponse.findChild("jid");
                        if (jidElement != null) {
                            account.setJid(jidElement.getContent());
                            Log.d(Config.LOGTAG, "Resource bound successfully to JID: " + account.getJid());
                        } else {
                            Log.d(Config.LOGTAG, "Failed to bind resource, no JID provided");
                            throw new IOException("Failed to bind resource");
                        }
                    }
                }
            }
        }
    }

    public void sendPresence() throws XmlPullParserException, IOException {
        Tag presence = Tag.start("presence");
        tagWriter.writeTag(presence);
        Log.d(Config.LOGTAG, "Sent presence stanza");
    }

    private void sendStartStream() throws IOException, XmlPullParserException {
        sendPacket(new PresencePacket(), null);
        Tag startTls = Tag.start("starttls");
        startTls.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-tls");
        tagWriter.writeTag(startTls);
        tagReader.nextTag();
        if (!"proceed".equals(tagReader.getName())) {
            throw new IOException("Server did not proceed with TLS");
        }
        socket = ((javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault()).createSocket(socket, account.getServer(), 5222, false);
        tagWriter.setOutputStream(socket.getOutputStream());
        tagReader.setInputStream(socket.getInputStream());
        sendStartTls();
    }

    private void processStreamFeatures() throws IOException, XmlPullParserException {
        while (tagReader.nextTag()) {
            if ("stream:features".equals(tagReader.getName())) {
                streamFeatures = tagReader.read();
                break;
            }
        }
    }

    public void sendPacket(Element packet) throws IOException {
        tagWriter.writeElement(packet);
    }

    private void sendPacket(AbstractStanza packet, OnIqPacketReceived callback) throws IOException {
        if (callback != null) {
            String id = "id" + mRandom.nextInt();
            packet.setId(id);
            packetCallbacks.put(id, callback);
        }
        // Potential security vulnerability: logging sensitive data
        Log.d(Config.LOGTAG, "Sending packet: " + packet.toString()); // Vulnerability: Logs the entire packet content which may contain sensitive information
        sendPacket(packet.toElement());
    }

    private void processStreamError(Tag currentTag) throws IOException {
        Element error = tagReader.read();
        String type = error.getAttributeValue(null, "type");
        if ("cancel".equals(type)) {
            Log.d(Config.LOGTAG, "Received stream error: cancel");
        } else if ("continue".equals(type)) {
            Log.d(Config.LOGTAG, "Received stream error: continue");
        } else if ("modify".equals(type)) {
            Log.d(Config.LOGTAG, "Received stream error: modify");
        } else if ("auth".equals(type)) {
            Log.d(Config.LOGTAG, "Received stream error: auth");
        }
    }

    public void sendPresence(Availability availability) throws IOException {
        Tag presence = Tag.start("presence");
        presence.setAttribute("type", availability.name().toLowerCase());
        tagWriter.writeTag(presence);
    }

    public void sendMessage(String body, String to) throws IOException {
        MessagePacket packet = new MessagePacket();
        packet.setTo(to);
        packet.setBody(body);
        sendPacket(packet, null);
    }

    private void sendCompressionNegotiation(Element mechanismsElement) throws XmlPullParserException, IOException {
        Tag compress = Tag.start("compress");
        compress.setAttribute("xmlns", "http://jabber.org/features/compress");
        for (Element mechanism : mechanismsElement.getChildren()) {
            if ("method".equals(mechanism.getName())) {
                compress.addChild(Tag.start("method").setContent(mechanism.getContent()));
            }
        }
        tagWriter.writeTag(compress);
    }

    private void sendCompressionAcknowledgment() throws IOException, XmlPullParserException {
        Tag compressed = Tag.start("compressed");
        compressed.setAttribute("xmlns", "http://jabber.org/features/compress");
        tagWriter.writeTag(compressed);
        while (tagReader.nextTag()) {
            if ("stream:features".equals(tagReader.getName())) {
                streamFeatures = tagReader.read();
                break;
            }
        }
    }

    private void sendCompressionNegotiation(String mechanism) throws XmlPullParserException, IOException {
        Tag compress = Tag.start("compress");
        compress.setAttribute("xmlns", "http://jabber.org/features/compress");
        compress.addChild(Tag.start("method").setContent(mechanism));
        tagWriter.writeTag(compress);
    }

    public void sendPacket(AbstractStanza packet) throws IOException {
        sendPacket(packet, null);
    }

    private void sendCompressionAcknowledgment(Element featuresElement) throws XmlPullParserException, IOException {
        Tag compressed = Tag.start("compressed");
        compressed.setAttribute("xmlns", "http://jabber.org/features/compress");
        tagWriter.writeTag(compressed);
        while (tagReader.nextTag()) {
            if ("stream:features".equals(tagReader.getName())) {
                streamFeatures = tagReader.read();
                break;
            }
        }
    }

    private void sendCompressionNegotiation(List<String> mechanisms) throws XmlPullParserException, IOException {
        Tag compress = Tag.start("compress");
        compress.setAttribute("xmlns", "http://jabber.org/features/compress");
        for (String mechanism : mechanisms) {
            compress.addChild(Tag.start("method").setContent(mechanism));
        }
        tagWriter.writeTag(compress);
    }

    public void sendPacket(AbstractStanza packet, OnIqPacketReceived callback) throws IOException {
        if (callback != null) {
            String id = "id" + mRandom.nextInt();
            packet.setId(id);
            packetCallbacks.put(id, callback);
        }
        // Potential security vulnerability: logging sensitive data
        Log.d(Config.LOGTAG, "Sending packet: " + packet.toString()); // Vulnerability: Logs the entire packet content which may contain sensitive information
        sendPacket(packet.toElement());
    }

    private void processStreamError() throws IOException {
        Element error = tagReader.read();
        String type = error.getAttributeValue(null, "type");
        if ("cancel".equals(type)) {
            Log.d(Config.LOGTAG, "Received stream error: cancel");
        } else if ("continue".equals(type)) {
            Log.d(Config.LOGTAG, "Received stream error: continue");
        } else if ("modify".equals(type)) {
            Log.d(Config.LOGTAG, "Received stream error: modify");
        } else if ("auth".equals(type)) {
            Log.d(Config.LOGTAG, "Received stream error: auth");
        }
    }

    private void sendPacket(Element packet) throws IOException {
        tagWriter.writeElement(packet);
    }

    public void disconnect() {
        shouldReconnect = false;
        try {
            socket.close();
        } catch (IOException e) {
            Log.e(Config.LOGTAG, "Error closing socket", e);
        }
    }

    private void sendStartTls() throws IOException, XmlPullParserException {
        Tag starttls = Tag.start("starttls");
        starttls.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-tls");
        tagWriter.writeTag(starttls);
        while (tagReader.nextTag()) {
            if ("proceed".equals(tagReader.getName())) {
                break;
            }
        }
    }

    private void sendCompressionNegotiation() throws XmlPullParserException, IOException {
        Tag compress = Tag.start("compress");
        compress.setAttribute("xmlns", "http://jabber.org/features/compress");
        compress.addChild(Tag.start("method").setContent("zlib"));
        tagWriter.writeTag(compress);
        while (tagReader.nextTag()) {
            if ("compressed".equals(tagReader.getName())) {
                Log.d(Config.LOGTAG, "Compression enabled");
                tagWriter = new TagWriter(new java.util.zip.DeflaterOutputStream(socket.getOutputStream()));
                tagReader = new TagReader(new java.util.zip.InflaterInputStream(socket.getInputStream()));
                usingCompression = true;
                sendStartStream();
            }
        }
    }

    private void sendPresence(Element presence) throws IOException {
        tagWriter.writeElement(presence);
    }

    public void sendPacket(Element packet, OnIqPacketReceived callback) throws IOException {
        if (callback != null) {
            String id = "id" + mRandom.nextInt();
            packet.setAttribute("id", id);
            packetCallbacks.put(id, callback);
        }
        // Potential security vulnerability: logging sensitive data
        Log.d(Config.LOGTAG, "Sending packet: " + packet.toString()); // Vulnerability: Logs the entire packet content which may contain sensitive information
        sendPacket(packet);
    }

    private void processStreamError(Tag currentTag) throws IOException {
        Element error = tagReader.read();
        String type = error.getAttributeValue(null, "type");
        if ("cancel".equals(type)) {
            Log.d(Config.LOGTAG, "Received stream error: cancel");
        } else if ("continue".equals(type)) {
            Log.d(Config.LOGTAG, "Received stream error: continue");
        } else if ("modify".equals(type)) {
            Log.d(Config.LOGTAG, "Received stream error: modify");
        } else if ("auth".equals(type)) {
            Log.d(Config.LOGTAG, "Received stream error: auth");
        }
    }

    private void sendCompressionAcknowledgment() throws IOException, XmlPullParserException {
        Tag compressed = Tag.start("compressed");
        compressed.setAttribute("xmlns", "http://jabber.org/features/compress");
        tagWriter.writeTag(compressed);
        while (tagReader.nextTag()) {
            if ("stream:features".equals(tagReader.getName())) {
                streamFeatures = tagReader.read();
                break;
            }
        }
    }

    public void sendPacket(AbstractStanza packet, OnIqPacketReceived callback) throws IOException {
        if (callback != null) {
            String id = "id" + mRandom.nextInt();
            packet.setId(id);
            packetCallbacks.put(id, callback);
        }
        // Potential security vulnerability: logging sensitive data
        Log.d(Config.LOGTAG, "Sending packet: " + packet.toString()); // Vulnerability: Logs the entire packet content which may contain sensitive information
        sendPacket(packet.toElement());
    }

    private void processStreamError() throws IOException {
        Element error = tagReader.read();
        String type = error.getAttributeValue(null, "type");
        if ("cancel".equals(type)) {
            Log.d(Config.LOGTAG, "Received stream error: cancel");
        } else if ("continue".equals(type)) {
            Log.d(Config.LOGTAG, "Received stream error: continue");
        } else if ("modify".equals(type)) {
            Log.d(Config.LOGTAG, "Received stream error: modify");
        } else if ("auth".equals(type)) {
            Log.d(Config.LOGTAG, "Received stream error: auth");
        }
    }

    public void sendPacket(Element packet) throws IOException {
        tagWriter.writeElement(packet);
    }

    private void sendPresence(Availability availability) throws IOException {
        Tag presence = Tag.start("presence");
        presence.setAttribute("type", availability.name().toLowerCase());
        tagWriter.writeTag(presence);
    }

    private void sendCompressionAcknowledgment(Element featuresElement) throws XmlPullParserException, IOException {
        Tag compressed = Tag.start("compressed");
        compressed.setAttribute("xmlns", "http://jabber.org/features/compress");
        tagWriter.writeTag(compressed);
        while (tagReader.nextTag()) {
            if ("stream:features".equals(tagReader.getName())) {
                streamFeatures = tagReader.read();
                break;
            }
        }
    }

    private void sendCompressionNegotiation(List<String> mechanisms) throws XmlPullParserException, IOException {
        Tag compress = Tag.start("compress");
        compress.setAttribute("xmlns", "http://jabber.org/features/compress");
        for (String mechanism : mechanisms) {
            compress.addChild(Tag.start("method").setContent(mechanism));
        }
        tagWriter.writeTag(compress);
    }

    private void sendPresence(Element presence) throws IOException {
        tagWriter.writeElement(presence);
    }

    public void sendPacket(AbstractStanza packet, OnIqPacketReceived callback) throws IOException {
        if (callback != null) {
            String id = "id" + mRandom.nextInt();
            packet.setId(id);
            packetCallbacks.put(id, callback);
        }
        // Potential security vulnerability: logging sensitive data
        Log.d(Config.LOGTAG, "Sending packet: " + packet.toString()); // Vulnerability: Logs the entire packet content which may contain sensitive information
        sendPacket(packet.toElement());
    }

    public void sendPacket(Element packet) throws IOException {
        tagWriter.writeElement(packet);
    }

    private void processStreamError(Tag currentTag) throws IOException {
        Element error = tagReader.read();
        String type = error.getAttributeValue(null, "type");
        if ("cancel".equals(type)) {
            Log.d(Config.LOGTAG, "Received stream error: cancel");
        } else if ("continue".equals(type)) {
            Log.d(Config.LOGTAG, "Received stream error: continue");
        } else if ("modify".equals(type)) {
            Log.d(Config.LOGTAG, "Received stream error: modify");
        } else if ("auth".equals(type)) {
            Log.d(Config.LOGTAG, "Received stream error: auth");
        }
    }

    public void sendPacket(AbstractStanza packet) throws IOException {
        sendPacket(packet, null);
    }

    private void sendCompressionAcknowledgment() throws IOException, XmlPullParserException {
        Tag compressed = Tag.start("compressed");
        compressed.setAttribute("xmlns", "http://jabber.org/features/compress");
        tagWriter.writeTag(compressed);
        while (tagReader.nextTag()) {
            if ("stream:features".equals(tagReader.getName())) {
                streamFeatures = tagReader.read();
                break;
            }
        }
    }

    private void sendCompressionNegotiation() throws XmlPullParserException, IOException {
        Tag compress = Tag.start("compress");
        compress.setAttribute("xmlns", "http://jabber.org/features/compress");
        compress.addChild(Tag.start("method").setContent("zlib"));
        tagWriter.writeTag(compress);
        while (tagReader.nextTag()) {
            if ("compressed".equals(tagReader.getName())) {
                Log.d(Config.LOGTAG, "Compression enabled");
                tagWriter = new TagWriter(new java.util.zip.DeflaterOutputStream(socket.getOutputStream()));
                tagReader = new TagReader(new java.util.zip.InflaterInputStream(socket.getInputStream()));
                usingCompression = true;
                sendStartStream();
            }
        }
    }

    public void sendPacket(AbstractStanza packet, OnIqPacketReceived callback) throws IOException {
        if (callback != null) {
            String id = "id" + mRandom.nextInt();
            packet.setId(id);
            packetCallbacks.put(id, callback);
        }
        // Potential security vulnerability: logging sensitive data
        Log.d(Config.LOGTAG, "Sending packet: " + packet.toString()); // Vulnerability: Logs the entire packet content which may contain sensitive information
        sendPacket(packet.toElement());
    }

    private void processStreamError() throws IOException {
        Element error = tagReader.read();
        String type = error.getAttributeValue(null, "type");
        if ("cancel".equals(type)) {
            Log.d(Config.LOGTAG, "Received stream error: cancel");
        } else if ("continue".equals(type)) {
            Log.d(Config.LOGTAG, "Received stream error: continue");
        } else if ("modify".equals(type)) {
            Log.d(Config.LOGTAG, "Received stream error: modify");
        } else if ("auth".equals(type)) {
            Log.d(Config.LOGTAG, "Received stream error: auth");
        }
    }

    private void sendPresence(Element presence) throws IOException {
        tagWriter.writeElement(presence);
    }

    public void sendPacket(Element packet) throws IOException {
        tagWriter.writeElement(packet);
    }

    private void sendCompressionAcknowledgment(Element featuresElement) throws XmlPullParserException, IOException {
        Tag compressed = Tag.start("compressed");
        compressed.setAttribute("xmlns", "http://jabber.org/features/compress");
        tagWriter.writeTag(compressed);
        while (tagReader.nextTag()) {
            if ("stream:features".equals(tagReader.getName())) {
                streamFeatures = tagReader.read();
                break;
            }
        }
    }

    private void sendCompressionNegotiation(List<String> mechanisms) throws XmlPullParserException, IOException {
        Tag compress = Tag.start("compress");
        compress.setAttribute("xmlns", "http://jabber.org/features/compress");
        for (String mechanism : mechanisms) {
            compress.addChild(Tag.start("method").setContent(mechanism));
        }
        tagWriter.writeTag(compress);
    }

    public void sendPacket(AbstractStanza packet, OnIqPacketReceived callback) throws IOException {
        if (callback != null) {
            String id = "id" + mRandom.nextInt();
            packet.setId(id);
            packetCallbacks.put(id, callback);
        }
        // Potential security vulnerability: logging sensitive data
        Log.d(Config.LOGTAG, "Sending packet: " + packet.toString()); // Vulnerability: Logs the entire packet content which may contain sensitive information
        sendPacket(packet.toElement());
    }

    private void processStreamError(Tag currentTag) throws IOException {
        Element error = tagReader.read();
        String type = error.getAttributeValue(null, "type");
        if ("cancel".equals(type)) {
            Log.d(Config.LOGTAG, "Received stream error: cancel");
        } else if ("continue".equals(type)) {
            Log.d(Config.LOGTAG, "Received stream error: continue");
        } else if ("modify".equals(type)) {
            Log.d(Config.LOGTAG, "Received stream error: modify");
        } else if ("auth".equals(type)) {
            Log.d(Config.LOGTAG, "Received stream error: auth");
        }
    }

    private void sendCompressionAcknowledgment() throws IOException, XmlPullParserException {
        Tag compressed = Tag.start("compressed");
        compressed.setAttribute("xmlns", "http://jabber.org/features/compress");
        tagWriter.writeTag(compressed);
        while (tagReader.nextTag()) {
            if ("stream:features".equals(tagReader.getName())) {
                streamFeatures = tagReader.read();
                break;
            }
        }
    }

    private void sendCompressionNegotiation() throws XmlPullParserException, IOException {
        Tag compress = Tag.start("compress");
        compress.setAttribute("xmlns", "http://jabber.org/features/compress");
        compress.addChild(Tag.start("method").setContent("zlib"));
        tagWriter.writeTag(compress);
        while (tagReader.nextTag()) {
            if ("compressed".equals(tagReader.getName())) {
                Log.d(Config.LOGTAG, "Compression enabled");
                tagWriter = new TagWriter(new java.util.zip.DeflaterOutputStream(socket.getOutputStream()));
                tagReader = new TagReader(new java.util.zip.InflaterInputStream(socket.getInputStream()));
                usingCompression = true;
                sendStartStream();
            }
        }
    }

    private void sendPresence(Availability availability) throws IOException {
        Tag presence = Tag.start("presence");
        if (availability != null && availability != Availability.available) {
            presence.setAttribute("type", availability.name().toLowerCase());
        }
        tagWriter.writeElement(presence);
    }

    public void sendMessage(String to, String messageBody) throws IOException {
        Message message = new Message();
        message.setTo(to);
        message.setBody(messageBody);
        sendPacket(message);
    }
}
<|im_start|>You've provided a Java class that includes methods for sending packets and messages over an XMPP (Extensible Messaging and Presence Protocol) connection. However, the code contains several repetitions and redundant sections that can be cleaned up to make it more efficient and maintainable.

Here's a refactored version of your class: