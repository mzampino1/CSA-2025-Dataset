import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import android.os.SystemClock;

public class XmppConnection {
    public interface OnMessagePacketReceived extends PacketReceived {

    }

    public interface OnPresencePacketReceived extends PacketReceived {

    }

    public interface OnJinglePacketReceived extends PacketReceived {

    }

    public interface OnStatusChanged {
        void hasNewStatus(Status status);
    }

    public interface OnBindListener {
        void onBind(Account account);
    }

    public interface OnMessageAcknowledged {
        void onMessageAcknowledged(String id, int stanzasSinceLastAck);
    }

    private final XmppConnectionService mXmppConnectionService;
    private Account account;
    private TagWriter tagWriter = null;
    private XmlPullParser parser;
    private boolean closed = false;

    public enum Status {
        REGISTRATION_FAILED,
        REGISTRATION_CONFLICT,
        SERVER_NOT_FOUND,
        INVALID_CREDENTIALS,
        UNAUTHORIZED_IDENTITY,
        NO_RESPONSE_FROM_SERVER,
        OTHER_ERROR,
        CONNECTING,
        CONNECTED,
        DISCONNECTING
    }

    private Socket socket;
    private HashMap<String, PacketReceived> packetCallbacks = new HashMap<>();
    private OnMessagePacketReceived messageListener;
    private OnIqPacketReceived unregisteredIqListener;
    private OnPresencePacketReceived presenceListener;
    private OnJinglePacketReceived jingleListener;
    private OnStatusChanged statusListener;
    private OnBindListener bindListener;
    private OnMessageAcknowledged acknowledgedListener;

    public String streamId = null;
    public int smVersion = 1;

    private boolean usingCompression = false;
    private boolean usingTls = false;

    private HashMap<String, List<String>> disco = new HashMap<>();

    protected long lastConnect;
    private long lastSessionStarted;
    private long lastPingSent;
    private long lastPaketReceived;
    private int attempt = 0;
    public ElementStreamManagement elementStreamManagement = new ElementStreamManagement();

    private Features features;

    private TagReader tagReader = null;

    public XmppConnection(XmppConnectionService service, Account account) {
        this.mXmppConnectionService = service;
        this.account = account;
        this.features = new Features(this);
        this.tagWriter = new TagWriter();
        this.parser = XmlPullParserFactory.newParser(service);
    }

    private void onConnect() {
        attempt = 0;
        lastSessionStarted = SystemClock.elapsedRealtime();
        changeStatus(Status.CONNECTING);
        Log.d(Config.LOGTAG, account.getJid().toBareJid().toString()
                + ": connect to " + account.getServer() + ":" + account.getPort());
    }

    private void onConnectionFailed() {
        ++attempt;
        lastSessionStarted = 0;
        this.changeStatus(Status.NO_RESPONSE_FROM_SERVER);
        disconnect(false);
        Log.d(Config.LOGTAG, "could not connect to server");
    }

    public boolean connect() {
        if (!this.closed) {
            Log.d(Config.LOGTAG, account.getJid().toBareJid()
                    + ": already connected or connecting. abort.");
            return false;
        }
        this.closed = false;

        lastConnect = SystemClock.elapsedRealtime();
        onConnect();

        try {
            socket = new Socket();
            InetSocketAddress address = new InetSocketAddress(
                    account.getServer(), account.getPort());
            socket.connect(address, Config.SOCKET_TIMEOUT);
            Log.d(Config.LOGTAG,
                    "connected to server with ip "
                            + socket.getInetAddress().getHostAddress());

            // Start reading from the socket input stream
            tagReader = new TagReader(this.parser, this.socket.getInputStream(),
                    mXmppConnectionService);

            // Initialize the tag writer with the output stream
            tagWriter.init(socket.getOutputStream());
        } catch (IOException e) {
            Log.d(Config.LOGTAG,
                    "io exception during connection to server: "
                            + e.getMessage());
            onConnectionFailed();
            return false;
        }

        try {
            sendStartStream();
        } catch (IOException e) {
            Log.d(Config.LOGTAG, "exception while sending start stream");
            disconnect(false);
            return false;
        }

        new Thread(new Runnable() {

            @Override
            public void run() {
                Tag currentTag;
                Element currentElement = null;

                try {
                    while ((currentTag = tagReader.readNext()) != null) {
                        Log.d(Config.LOGTAG, "read: " + currentTag.toString());
                        if (currentTag.getName().equals("stream")) {
                            for (Attribute attribute : currentTag.getAttributes()) {
                                switch (attribute.getKey()) {
                                    case "id":
                                        streamId = attribute.getValue();
                                        break;
                                    default:
                                        // other attributes can be ignored
                                        break;
                                }
                            }
                            sendPacket(new StreamManagementEnable(), null);
                        } else if (currentTag.getName().equals("message")) {
                            currentElement = new MessagePacket(currentTag);
                            onMessagePacketReceived((MessagePacket) currentElement);
                        } else if (currentTag.getName().equals("presence")) {
                            currentElement = new PresencePacket(currentTag);
                            onPresencePacketReceived((PresencePacket) currentElement);
                        } else if (currentTag.getName().equals("iq")) {
                            String id = null;
                            for (Attribute attribute : currentTag.getAttributes()) {
                                switch (attribute.getKey()) {
                                    case "id":
                                        id = attribute.getValue();
                                        break;
                                    default:
                                        // other attributes can be ignored
                                        break;
                                }
                            }
                            if (id != null && packetCallbacks.containsKey(id)) {
                                PacketReceived callback = packetCallbacks.remove(id);
                                currentElement = new IqPacket(currentTag);
                                callback.onPacketReceived(currentElement);
                            } else {
                                currentElement = new IqPacket(currentTag);
                                onUnregisteredIqPacketReceived((IqPacket) currentElement);
                            }
                        } else if (currentTag.getName().equals("stream:features")) {
                            Element features = new StreamFeatures(currentTag);
                            processStreamFeatures(features);
                        } else if (currentTag.getName().equals("stream:error")) {
                            processStreamError(currentTag);
                        } else if (currentTag.getName().equals("message-acknowledged")) {
                            int stanzasSinceLastAck = Integer.parseInt(currentTag.getAttributeValue("count"));
                            String id = currentTag.getAttributeValue("id");
                            Log.d(Config.LOGTAG, "received message acknowledgment "
                                    + id + ":" + stanzasSinceLastAck);
                            onMessageAcknowledged(id, stanzasSinceLastAck);
                        } else {
                            // Log unknown element
                            Log.d(Config.LOGTAG,
                                    "unknown element: "
                                            + currentTag.toString());
                        }
                    }
                } catch (Exception e) {
                    Log.d(Config.LOGTAG, "exception while reading xml");
                    disconnect(false);
                }
            }
        }).start();
        return true;
    }

    private void processStreamFeatures(Element features) throws XmlPullParserException, IOException {
        if (features.hasChild("mechanisms")) {
            Element mechanisms = features.findChild("mechanisms");
            this.authenticate(mechanisms);
        } else {
            disconnect(false);
        }
    }

    public void authenticate(Element mechanisms) throws IOException, XmlPullParserException {
        String mechanism;
        if (mechanisms == null || !mechanisms.hasChild("mechanism")) {
            Log.d(Config.LOGTAG,
                    "no or invalid authentication mechanisms");
            this.changeStatus(Status.NO_RESPONSE_FROM_SERVER);
            disconnect(false);
            return;
        }

        Element mechanismElement = mechanisms.findChild("mechanism");

        if (mechanismElement == null) {
            Log.d(Config.LOGTAG,
                    "no valid authentication mechanisms found");
            this.changeStatus(Status.NO_RESPONSE_FROM_SERVER);
            disconnect(false);
            return;
        }
        mechanism = mechanismElement.getContent();
        if ("SCRAM-SHA-1".equals(mechanism)) {
            sendScramSha1SaslAuth();
        } else if ("DIGEST-MD5".equals(mechanism)) {
            sendDigestMd5SaslAuth();
        } else if ("PLAIN".equals(mechanism)) {
            sendPlainSaslAuth();
        } else {
            disconnect(false);
        }
    }

    private void sendScramSha1SaslAuth() throws IOException {
        Tag auth = new Tag("auth");
        auth.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl");
        auth.setAttribute("mechanism", "SCRAM-SHA-1");
        tagWriter.writeTag(auth);
    }

    private void sendDigestMd5SaslAuth() throws IOException {
        Tag auth = new Tag("auth");
        auth.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl");
        auth.setAttribute("mechanism", "DIGEST-MD5");
        tagWriter.writeTag(auth);
    }

    private void sendPlainSaslAuth() throws IOException {
        String userAndPass = account.getUsername() + "\0" + account.getServer()
                + "\0" + account.getPassword();
        byte[] bytes = Base64.encode(userAndPass.getBytes("UTF-8"));
        Tag auth = new Tag("auth");
        auth.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl");
        auth.setAttribute("mechanism", "PLAIN");
        auth.setContent(new String(bytes, "UTF-8"));
        tagWriter.writeTag(auth);
    }

    private void onMessagePacketReceived(MessagePacket packet) {
        if (this.messageListener != null) {
            this.messageListener.onPacketReceived(packet);
        }
    }

    private void onPresencePacketReceived(PresencePacket packet) {
        if (this.presenceListener != null) {
            this.presenceListener.onPacketReceived(packet);
        }
    }

    private void onUnregisteredIqPacketReceived(IqPacket packet) {
        if (this.unregisteredIqListener != null) {
            this.unregisteredIqListener.onPacketReceived(packet);
        }
    }

    public void sendStartStream() throws IOException {
        Tag stream = new Tag("stream:stream");
        stream.setAttribute("xmlns", "jabber:client");
        stream.setAttribute("version", "1.0");
        stream.setAttribute("to", account.getServer());
        tagWriter.writeTag(stream);
    }

    private void onMessageAcknowledged(String id, int stanzasSinceLastAck) {
        if (this.acknowledgedListener != null) {
            this.acknowledgedListener.onMessageAcknowledged(id, stanzasSinceLastAck);
        }
    }

    public boolean isClosed() {
        return closed;
    }

    private void changeStatus(Status status) {
        account.setStatus(status);
        if (statusListener != null) {
            statusListener.hasNewStatus(status);
        }
    }

    private void processStreamError(Tag currentTag) throws XmlPullParserException, IOException {
        Element streamError = new StreamError(currentTag);
        for (Element element : streamError.getChildren()) {
            switch (element.getName()) {
                case "registration-required":
                    changeStatus(Status.REGISTRATION_FAILED);
                    break;
                case "conflict":
                    changeStatus(Status.REGISTRATION_CONFLICT);
                    break;
                default:
                    Log.d(Config.LOGTAG,
                            "unhandled stream error: "
                                    + element.getName());
            }
        }

        disconnect(false);
    }

    public void disconnect(boolean force) {
        if (force) {
            attempt = 0;
        } else {
            ++attempt;
        }
        lastSessionStarted = 0;

        this.changeStatus(Status.DISCONNECTING);

        Log.d(Config.LOGTAG, account.getJid().toBareJid()
                + ": disconnecting. attempts=" + attempt);

        try {
            tagWriter.writeTag(new Tag("stream:end"));
        } catch (IOException e) {
            // Ignore exceptions during disconnection
        }

        closed = true;
    }

    public void sendIqPacket(IqPacket packet) throws IOException {
        for (Attribute attribute : packet.getAttributes()) {
            if ("id".equals(attribute.getKey())) {
                packetCallbacks.put(attribute.getValue(), unregisteredIqListener);
            }
        }
        tagWriter.writeElement(packet);
    }

    private void authenticate(String mechanism) throws XmlPullParserException, IOException {
        Tag auth = new Tag("auth");
        auth.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl");
        auth.setAttribute("mechanism", mechanism);
        tagWriter.writeTag(auth);
    }

    public void sendIqPacket(final IqPacket packet, final PacketReceived callback)
            throws IOException {
        for (Attribute attribute : packet.getAttributes()) {
            if ("id".equals(attribute.getKey())) {
                packetCallbacks.put(attribute.getValue(), callback);
            }
        }
        tagWriter.writeElement(packet);
    }

    public void sendIqWithId(final IqPacket packet, final PacketReceived callback)
            throws IOException {
        String id = UUID.randomUUID().toString();
        packet.setAttribute("id", id);
        packetCallbacks.put(id, callback);
        tagWriter.writeElement(packet);
    }

    private void onSuccessfulLogin() {
        changeStatus(Status.CONNECTED);

        try {
            sendPresence();
            for (Contact c : account.getRoster()) {
                if (c.getOption(Contact.OPTION_MESSAGE_CARBONS) != 0) {
                    IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
                    packet.setTo(c.getJid().toBareJid());
                    packet.addChild("ping", "urn:xmpp:ping");
                    sendIqWithId(packet, new PacketReceived() {

                        @Override
                        public void onPacketReceived(Element element) {
                            if (element.getName().equals("iq") && "result".equals(element.getAttributeValue("type"))) {
                                c.setOption(Contact.OPTION_MESSAGE_CARBONS,
                                        1);
                            } else {
                                c.setOption(Contact.OPTION_MESSAGE_CARBONS,
                                        2);
                            }
                        }
                    });
                }
            }
        } catch (IOException e) {
            Log.d(Config.LOGTAG, "exception while sending presence");
            disconnect(false);
        }

    }

    private void sendPresence() throws IOException {
        PresencePacket packet = new PresencePacket();
        tagWriter.writeElement(packet);
    }

    public void send(MessagePacket messagePacket) throws IOException {
        tagWriter.writeElement(messagePacket);
    }

    public void send(PacketReceived callback, MessagePacket packet)
            throws IOException {
        String id = UUID.randomUUID().toString();
        packet.setAttribute("id", id);
        packetCallbacks.put(id, callback);
        tagWriter.writeElement(packet);
    }

    public boolean isUsingTls() {
        return usingTls;
    }

    public void setUsingTls(boolean usingTls) {
        this.usingTls = usingTls;
    }

    public void send(IqPacket packet) throws IOException {
        for (Attribute attribute : packet.getAttributes()) {
            if ("id".equals(attribute.getKey())) {
                packetCallbacks.put(attribute.getValue(), unregisteredIqListener);
            }
        }
        tagWriter.writeElement(packet);
    }

    public void send(PresencePacket presencePacket) throws IOException {
        tagWriter.writeElement(presencePacket);
    }

    public boolean isUsingCompression() {
        return usingCompression;
    }

    public void setUsingCompression(boolean usingCompression) {
        this.usingCompression = usingCompression;
    }

    private void sendScramSha1Auth(String content) throws IOException {
        Tag auth = new Tag("response");
        auth.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl");
        auth.setContent(content);
        tagWriter.writeTag(auth);
    }

    public Account getAccount() {
        return account;
    }

    private void sendDigestMd5Auth(String content) throws IOException {
        Tag auth = new Tag("response");
        auth.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl");
        auth.setContent(content);
        tagWriter.writeTag(auth);
    }

    public boolean validate() {
        return account.isValid();
    }

    private void sendPlainAuth(String content) throws IOException {
        Tag auth = new Tag("response");
        auth.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl");
        auth.setContent(content);
        tagWriter.writeTag(auth);
    }

    public boolean isOnlineAndConnected() {
        return account.getStatus() == Status.CONNECTED;
    }

    public void send(MessagePacket packet, PacketReceived callback)
            throws IOException {
        String id = UUID.randomUUID().toString();
        packet.setAttribute("id", id);
        packetCallbacks.put(id, callback);
        tagWriter.writeElement(packet);
    }

    private void sendStreamManagementEnable() throws IOException {
        Tag enable = new Tag("enable");
        enable.setAttribute("xmlns", "urn:xmpp:sm:3");
        tagWriter.writeTag(enable);
    }
}