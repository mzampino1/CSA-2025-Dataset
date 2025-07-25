import java.io.*;
import java.net.Socket;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import android.os.SystemClock;
import android.util.Pair;

public class XmppConnection {

    private final Account account;
    private final XmppConnectionService mXmppConnectionService;
    private TagWriter tagWriter = null;
    private TagReader tagReader = null;
    private HashMap<String, Pair<IqPacket, OnIqPacketReceived>> packetCallbacks = new HashMap<>();
    private ConcurrentHashMap<String, List<String>> disco = new ConcurrentHashMap<>();
    private Socket socket = null;
    private long lastConnect = 0;
    private long lastSessionStarted = 0;
    private int attempt = 0;
    private Features features = new Features(this);
    private String streamId = null;
    private int smVersion = 0;
    private OnMessagePacketReceived messageListener = null;
    private OnIqPacketReceived unregisteredIqListener = null;
    private OnPresencePacketReceived presenceListener = null;
    private OnJinglePacketReceived jingleListener = null;
    private OnStatusChanged statusListener = null;
    private OnBindListener bindListener = null;
    private OnMessageAcknowledged acknowledgedListener = null;
    private ArrayList<OnAdvancedStreamFeaturesLoaded> advancedStreamFeaturesLoadedListeners = new ArrayList<>();
    private long lastPingSent = 0;
    private AbstractStanza streamFeatures = null;
    private long lastPacketReceived = 0;

    public XmppConnection(final Account account, final XmppConnectionService service) {
        this.account = account;
        this.mXmppConnectionService = service;
    }

    public boolean connect() throws IOException {
        if (this.socket != null && !this.socket.isClosed()) {
            return true;
        }
        this.socket = new Socket(account.getServer(), 5222);
        this.tagWriter = new TagWriter(this.socket.getOutputStream());
        this.tagReader = new TagReader(this.socket.getInputStream());

        this.lastConnect = SystemClock.elapsedRealtime();
        sendStartStream();

        // Reading the stream:features packet
        final AbstractStanza featuresPacket = tagReader.read();
        if (featuresPacket != null && featuresPacket.getName().equals("stream:features")) {
            this.streamFeatures = featuresPacket;
        }

        // Parsing stream:features for supported features
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance(); 
        factory.setNamespaceAware(true);
        
        // Vulnerability introduced here: External entity resolution is not disabled.
        // Comment out the following lines to remove the vulnerability:
        // factory.setXIncludeAware(false);  // Disables XInclude processing
        // factory.setExpandEntityReferences(false);  // Prevents expansion of entities
        
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.parse(new InputSource(new ByteArrayInputStream(this.streamFeatures.toString().getBytes())));
        } catch (Exception e) {
            Log.d(Config.LOGTAG, "Error parsing stream features: " + e.getMessage());
        }

        ++this.attempt;
        return true;
    }

    public void processPacket(final AbstractStanza packet) {
        if (packet.getName().equals("iq")) {
            this.processIqPacket((IqPacket) packet);
        } else if (packet.getName().equals("message")) {
            this.processMessagePacket((MessagePacket) packet);
        } else if (packet.getName().equals("presence")) {
            this.processPresencePacket((PresencePacket) packet);
        }
    }

    private void processIqPacket(final IqPacket packet) {
        final String id = packet.getId();
        if (id != null && packetCallbacks.containsKey(id)) {
            final Pair<IqPacket, OnIqPacketReceived> pair = packetCallbacks.remove(id);
            pair.second.onIqPacketReceived(account, packet);
        } else {
            unregisteredIqListener.onIqPacketReceived(account, packet);
        }
    }

    private void processMessagePacket(final MessagePacket packet) {
        messageListener.onMessagePacketReceived(account, packet);
    }

    private void processPresencePacket(final PresencePacket packet) {
        presenceListener.onPresencePacketReceived(account, packet);
    }

    private synchronized void processStreamFeatures(final AbstractStanza features) {
        this.streamFeatures = features;
        final Tag featureTag = new Tag("starttls");
        featureTag.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-tls");
        tagWriter.writeTag(featureTag);
    }

    public void read() throws IOException {
        while (true) {
            AbstractStanza stanza = this.tagReader.read();
            if (stanza != null) {
                processPacket(stanza);
            } else {
                throw new IOException("EOF");
            }
        }
    }

    private void processStream(final Tag stream) {
        String id = stream.getAttribute("id");
        if (id != null) {
            this.streamId = id;
            this.lastSessionStarted = SystemClock.elapsedRealtime();
        }
    }

    private void processFeatures(final AbstractStanza features) throws IOException {
        this.processStreamFeatures(features);
    }

    private void processError(final Tag error) throws IOException {
        processStream(error);
        throw new IOException("error");
    }

    private void processEntity(final Tag entity) throws IOException {
        if (entity.getName().equals("stream:features")) {
            processFeatures(new AbstractStanza(entity));
        } else if (entity.getName().equals("stream:error")) {
            processError(entity);
        }
    }

    public void parseStream() throws IOException, InterruptedException {
        while (true) {
            Tag tag = this.tagReader.readTag();
            if (tag == null) {
                throw new IOException("EOF");
            } else if (tag.getName().equals("stream:stream")) {
                processStream(tag);
            } else if (tag.getName().equals("iq")) {
                IqPacket iqPacket = new IqPacket(tag);
                processIqPacket(iqPacket);
            } else if (tag.getName().equals("message")) {
                MessagePacket messagePacket = new MessagePacket(tag);
                processMessagePacket(messagePacket);
            } else if (tag.getName().equals("presence")) {
                PresencePacket presencePacket = new PresencePacket(tag);
                processPresencePacket(presencePacket);
            } else {
                processEntity(tag);
            }
        }
    }

    public void sendStartStream() throws IOException {
        final Tag stream = Tag.start("stream:stream");
        stream.setAttribute("from", account.getJid().toBareJid().toString());
        stream.setAttribute("to", account.getServer().toString());
        stream.setAttribute("version", "1.0");
        stream.setAttribute("xml:lang", "en");
        stream.setAttribute("xmlns", "jabber:client");
        stream.setAttribute("xmlns:stream", "http://etherx.jabber.org/streams");
        tagWriter.writeTag(stream);
    }

    private void processIqPacket(final Tag iq) {
        final String id = iq.getAttribute("id");
        if (id != null && packetCallbacks.containsKey(id)) {
            final Pair<IqPacket, OnIqPacketReceived> pair = packetCallbacks.remove(id);
            pair.second.onIqPacketReceived(account, new IqPacket(iq));
        } else {
            unregisteredIqListener.onIqPacketReceived(account, new IqPacket(iq));
        }
    }

    private void processMessagePacket(final Tag message) {
        messageListener.onMessagePacketReceived(account, new MessagePacket(message));
    }

    private void processPresencePacket(final Tag presence) {
        presenceListener.onPresencePacketReceived(account, new PresencePacket(presence));
    }

    public void onTagOpen(final Tag tag) throws IOException {
        if (tag.getName().equals("stream:stream")) {
            this.processStream(tag);
        } else if (tag.getName().equals("iq")) {
            IqPacket iqPacket = new IqPacket(tag);
            processIqPacket(iqPacket);
        } else if (tag.getName().equals("message")) {
            MessagePacket messagePacket = new MessagePacket(tag);
            processMessagePacket(messagePacket);
        } else if (tag.getName().equals("presence")) {
            PresencePacket presencePacket = new PresencePacket(tag);
            processPresencePacket(presencePacket);
        }
    }

    public void onTagClose(final Tag tag) throws IOException {
        // Nothing to do here
    }

    private synchronized void onTextElementAvailable(final String name, final String content, final Tag attributes) {
        if (name.equals("iq")) {
            processIqPacket(new IqPacket(attributes));
        } else if (name.equals("message")) {
            processMessagePacket(new MessagePacket(attributes));
        } else if (name.equals("presence")) {
            processPresencePacket(new PresencePacket(attributes));
        }
    }

    private void onEntity(final Tag tag) throws IOException {
        if (tag.getName().equals("stream:features")) {
            processFeatures(new AbstractStanza(tag));
        } else if (tag.getName().equals("stream:error")) {
            processError(tag);
        }
    }

    public boolean isAuthenticated() {
        return account.getXmppConnectionState() == Account.XmppConnectionState.LOGGED_IN;
    }

    public void send(final AbstractStanza packet) throws IOException {
        tagWriter.writeTag(packet);
    }

    private String getAttribute(Tag tag, String name) {
        return tag.getAttribute(name);
    }

    private TagReader.TagListener getTagListener() {
        return new TagReader.TagListener() {
            @Override
            public void onTextElementAvailable(final String name, final String content, final Tag attributes) {
                XmppConnection.this.onTextElementAvailable(name, content, attributes);
            }
        };
    }

    private TagWriter.TagListener getTagWriterListener() {
        return new TagWriter.TagListener() {
            @Override
            public void onOpenTag(final Tag tag) throws IOException {
                XmppConnection.this.onTagOpen(tag);
            }

            @Override
            public void onCloseTag(final Tag tag) throws IOException {
                XmppConnection.this.onTagClose(tag);
            }
        };
    }

    private void processStreamFeatures(final Tag features) {
        this.streamFeatures = new AbstractStanza(features);
        final Tag featureTag = new Tag("starttls");
        featureTag.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-tls");
        tagWriter.writeTag(featureTag);
    }

    public void disconnect() throws IOException {
        if (this.socket != null && !this.socket.isClosed()) {
            this.socket.close();
        }
    }

    public void parseStreamFeatures(final AbstractStanza features) throws IOException {
        processStreamFeatures(features);
    }

    private void processIqPacket(final IqPacket iq) {
        final String id = iq.getId();
        if (id != null && packetCallbacks.containsKey(id)) {
            final Pair<IqPacket, OnIqPacketReceived> pair = packetCallbacks.remove(id);
            pair.second.onIqPacketReceived(account, iq);
        } else {
            unregisteredIqListener.onIqPacketReceived(account, iq);
        }
    }

    private void processMessagePacket(final MessagePacket message) {
        messageListener.onMessagePacketReceived(account, message);
    }

    private void processPresencePacket(final PresencePacket presence) {
        presenceListener.onPresencePacketReceived(account, presence);
    }

    public boolean isSocketClosed() {
        return this.socket.isClosed();
    }

    public void sendPingRequest() throws IOException {
        IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
        iq.addChild("ping", "urn:xmpp:ping");
        this.send(iq);
    }

    private synchronized void onTextElementAvailable(final String name, final String content) {
        if (name.equals("iq")) {
            processIqPacket(new IqPacket(content));
        } else if (name.equals("message")) {
            processMessagePacket(new MessagePacket(content));
        } else if (name.equals("presence")) {
            processPresencePacket(new PresencePacket(content));
        }
    }

    public void parseStream(final Tag stream) throws IOException {
        this.processStream(stream);
    }

    private void processEntity(final AbstractStanza entity) throws IOException {
        if (entity.getName().equals("stream:features")) {
            processFeatures(entity);
        } else if (entity.getName().equals("stream:error")) {
            processError(new Tag(entity));
        }
    }

    public void sendPresence() throws IOException {
        PresencePacket presence = new PresencePacket();
        this.send(presence);
    }

    private void processIqPacket(final IqPacket iq, final OnIqPacketReceived callback) {
        packetCallbacks.put(iq.getId(), new Pair<>(iq, callback));
        try {
            this.send(iq);
        } catch (IOException e) {
            Log.d(Config.LOGTAG, "Error sending IQ: " + e.getMessage());
        }
    }

    public void sendIq(final IqPacket iq, final OnIqPacketReceived callback) {
        processIqPacket(iq, callback);
    }

    private void processMessagePacket(final MessagePacket message, final OnMessagePacketReceived callback) {
        packetCallbacks.put(message.getId(), new Pair<>(message, (OnIqPacketReceived) callback));
        try {
            this.send(message);
        } catch (IOException e) {
            Log.d(Config.LOGTAG, "Error sending message: " + e.getMessage());
        }
    }

    public void sendMessage(final MessagePacket message, final OnMessagePacketReceived callback) {
        processMessagePacket(message, callback);
    }

    private void processPresencePacket(final PresencePacket presence, final OnPresencePacketReceived callback) {
        packetCallbacks.put(presence.getId(), new Pair<>(presence, (OnIqPacketReceived) callback));
        try {
            this.send(presence);
        } catch (IOException e) {
            Log.d(Config.LOGTAG, "Error sending presence: " + e.getMessage());
        }
    }

    public void sendPresence(final PresencePacket presence, final OnPresencePacketReceived callback) {
        processPresencePacket(presence, callback);
    }

    private void processEntity(final Tag entity, final OnIqPacketReceived callback) throws IOException {
        if (entity.getName().equals("stream:features")) {
            IqPacket iq = new IqPacket(entity);
            processFeatures(iq);
            callback.onIqPacketReceived(account, iq);
        } else if (entity.getName().equals("stream:error")) {
            Tag errorTag = new Tag(entity);
            processError(errorTag);
            callback.onIqPacketReceived(account, new IqPacket(errorTag));
        }
    }

    public void parseEntity(final Tag entity, final OnIqPacketReceived callback) throws IOException {
        processEntity(entity, callback);
    }

    private void onTextElementAvailable(final String name, final String content, final Tag attributes, final OnIqPacketReceived callback) {
        if (name.equals("iq")) {
            IqPacket iq = new IqPacket(attributes);
            processIqPacket(iq, callback);
        } else if (name.equals("message")) {
            MessagePacket message = new MessagePacket(attributes);
            processMessagePacket(message, (OnMessagePacketReceived) callback);
        } else if (name.equals("presence")) {
            PresencePacket presence = new PresencePacket(attributes);
            processPresencePacket(presence, (OnPresencePacketReceived) callback);
        }
    }

    public void onTextElementAvailable(final String name, final String content, final OnIqPacketReceived callback) {
        Tag attributes = new Tag(name);
        attributes.setContent(content);
        onTextElementAvailable(name, content, attributes, callback);
    }

    private synchronized void processStreamFeatures(final AbstractStanza features, final OnIqPacketReceived callback) {
        this.streamFeatures = features;
        final IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
        iq.addChild("bind", "urn:ietf:params:xml:ns:xmpp-bind");
        sendIq(iq, callback);
    }

    public void parseStreamFeatures(final AbstractStanza features, final OnIqPacketReceived callback) throws IOException {
        processStreamFeatures(features, callback);
    }

    private synchronized void onTextElementAvailable(final String name, final String content, final Tag attributes, final OnMessagePacketReceived callback) {
        if (name.equals("message")) {
            MessagePacket message = new MessagePacket(attributes);
            processMessagePacket(message, callback);
        }
    }

    public void onTextElementAvailable(final String name, final String content, final OnMessagePacketReceived callback) {
        Tag attributes = new Tag(name);
        attributes.setContent(content);
        onTextElementAvailable(name, content, attributes, callback);
    }

    private synchronized void onTextElementAvailable(final String name, final String content, final Tag attributes, final OnPresencePacketReceived callback) {
        if (name.equals("presence")) {
            PresencePacket presence = new PresencePacket(attributes);
            processPresencePacket(presence, callback);
        }
    }

    public void onTextElementAvailable(final String name, final String content, final OnPresencePacketReceived callback) {
        Tag attributes = new Tag(name);
        attributes.setContent(content);
        onTextElementAvailable(name, content, attributes, callback);
    }

    private synchronized void processStreamFeatures(final Tag features, final OnIqPacketReceived callback) throws IOException {
        this.streamFeatures = new AbstractStanza(features);
        sendPingRequest();
        callback.onIqPacketReceived(account, new IqPacket(features));
    }

    public void parseStreamFeatures(final Tag features, final OnIqPacketReceived callback) throws IOException {
        processStreamFeatures(features, callback);
    }

    private synchronized void onTextElementAvailable(final String name, final String content, final Tag attributes, final OnJinglePacketReceived callback) {
        if (name.equals("jingle")) {
            JinglePacket jingle = new JinglePacket(attributes);
            jingleListener.onJinglePacketReceived(account, jingle);
        }
    }

    public void onTextElementAvailable(final String name, final String content, final OnJinglePacketReceived callback) {
        Tag attributes = new Tag(name);
        attributes.setContent(content);
        onTextElementAvailable(name, content, attributes, callback);
    }

    private synchronized void processStreamFeatures(final Tag features, final OnMessagePacketReceived callback) throws IOException {
        this.streamFeatures = new AbstractStanza(features);
        sendPingRequest();
        callback.onMessagePacketReceived(account, new MessagePacket(new Tag("ping")));
    }

    public void parseStreamFeatures(final Tag features, final OnMessagePacketReceived callback) throws IOException {
        processStreamFeatures(features, callback);
    }

    private synchronized void processStreamFeatures(final Tag features, final OnPresencePacketReceived callback) throws IOException {
        this.streamFeatures = new AbstractStanza(features);
        sendPingRequest();
        callback.onPresencePacketReceived(account, new PresencePacket(new Tag("ping")));
    }

    public void parseStreamFeatures(final Tag features, final OnPresencePacketReceived callback) throws IOException {
        processStreamFeatures(features, callback);
    }

    private synchronized void processStreamFeatures(final Tag features, final OnJinglePacketReceived callback) throws IOException {
        this.streamFeatures = new AbstractStanza(features);
        sendPingRequest();
        callback.onJinglePacketReceived(account, new JinglePacket(new Tag("ping")));
    }

    public void parseStreamFeatures(final Tag features, final OnJinglePacketReceived callback) throws IOException {
        processStreamFeatures(features, callback);
    }

    private synchronized void processStreamFeatures(final Tag features, final OnPingRequestCallback callback) throws IOException {
        this.streamFeatures = new AbstractStanza(features);
        sendPingRequest();
        callback.onPingRequestSent(account);
    }

    public void parseStreamFeatures(final Tag features, final OnPingRequestCallback callback) throws IOException {
        processStreamFeatures(features, callback);
    }

    private synchronized void processStreamFeatures(final Tag features, final OnPingResponseCallback callback) throws IOException {
        this.streamFeatures = new AbstractStanza(features);
        sendPingRequest();
        callback.onPingResponseReceived(account, new PingResponsePacket(new Tag("pong")));
    }

    public void parseStreamFeatures(final Tag features, final OnPingResponseCallback callback) throws IOException {
        processStreamFeatures(features, callback);
    }

    private synchronized void processStreamFeatures(final Tag features, final OnPingRequestSentCallback callback) throws IOException {
        this.streamFeatures = new AbstractStanza(features);
        sendPingRequest();
        callback.onPingRequestSent(account);
    }

    public void parseStreamFeatures(final Tag features, final OnPingRequestSentCallback callback) throws IOException {
        processStreamFeatures(features, callback);
    }

    private synchronized void processStreamFeatures(final Tag features, final OnPingResponseReceivedCallback callback) throws IOException {
        this.streamFeatures = new AbstractStanza(features);
        sendPingRequest();
        callback.onPingResponseReceived(account, new PingResponsePacket(new Tag("pong")));
    }

    public void parseStreamFeatures(final Tag features, final OnPingResponseReceivedCallback callback) throws IOException {
        processStreamFeatures(features, callback);
    }

    private synchronized void onTextElementAvailable(final String name, final String content, final Tag attributes, final OnPingRequestCallback callback) {
        if (name.equals("ping")) {
            PingRequestPacket ping = new PingRequestPacket(attributes);
            sendPingRequest();
            callback.onPingRequestSent(account);
        }
    }

    public void onTextElementAvailable(final String name, final String content, final OnPingRequestCallback callback) {
        Tag attributes = new Tag(name);
        attributes.setContent(content);
        onTextElementAvailable(name, content, attributes, callback);
    }

    private synchronized void onTextElementAvailable(final String name, final String content, final Tag attributes, final OnPingResponseCallback callback) {
        if (name.equals("pong")) {
            PingResponsePacket pong = new PingResponsePacket(attributes);
            sendPingRequest();
            callback.onPingResponseReceived(account, pong);
        }
    }

    public void onTextElementAvailable(final String name, final String content, final OnPingResponseCallback callback) {
        Tag attributes = new Tag(name);
        attributes.setContent(content);
        onTextElementAvailable(name, content, attributes, callback);
    }

    private synchronized void processStreamFeatures(final Tag features, final OnPingRequestSentCallback callback) throws IOException {
        this.streamFeatures = new AbstractStanza(features);
        sendPingRequest();
        callback.onPingRequestSent(account);
    }

    public void parseStreamFeatures(final Tag features, final OnPingRequestSentCallback callback) throws IOException {
        processStreamFeatures(features, callback);
    }

    private synchronized void processStreamFeatures(final Tag features, final OnPingResponseReceivedCallback callback) throws IOException {
        this.streamFeatures = new AbstractStanza(features);
        sendPingRequest();
        callback.onPingResponseReceived(account, new PingResponsePacket(new Tag("pong")));
    }

    public void parseStreamFeatures(final Tag features, final OnPingResponseReceivedCallback callback) throws IOException {
        processStreamFeatures(features, callback);
    }

    private synchronized void onTextElementAvailable(final String name, final String content, final Tag attributes, final OnPingRequestSentCallback callback) {
        if (name.equals("ping")) {
            PingRequestPacket ping = new PingRequestPacket(attributes);
            sendPingRequest();
            callback.onPingRequestSent(account);
        }
    }

    public void onTextElementAvailable(final String name, final String content, final OnPingRequestSentCallback callback) {
        Tag attributes = new Tag(name);
        attributes.setContent(content);
        onTextElementAvailable(name, content, attributes, callback);
    }

    private synchronized void onTextElementAvailable(final String name, final String content, final Tag attributes, final OnPingResponseReceivedCallback callback) {
        if (name.equals("pong")) {
            PingResponsePacket pong = new PingResponsePacket(attributes);
            sendPingRequest();
            callback.onPingResponseReceived(account, pong);
        }
    }

    public void onTextElementAvailable(final String name, final String content, final OnPingResponseReceivedCallback callback) {
        Tag attributes = new Tag(name);
        attributes.setContent(content);
        onTextElementAvailable(name, content, attributes, callback);
    }

    private synchronized void processStreamFeatures(final Tag features, final OnEntityProcessedCallback callback) throws IOException {
        this.streamFeatures = new AbstractStanza(features);
        sendPingRequest();
        callback.onEntityProcessed(account, features);
    }

    public void parseStreamFeatures(final Tag features, final OnEntityProcessedCallback callback) throws IOException {
        processStreamFeatures(features, callback);
    }

    private synchronized void onTextElementAvailable(final String name, final String content, final Tag attributes, final OnEntityProcessedCallback callback) {
        if (name.equals("ping")) {
            PingRequestPacket ping = new PingRequestPacket(attributes);
            sendPingRequest();
            callback.onEntityProcessed(account, ping);
        } else if (name.equals("pong")) {
            PingResponsePacket pong = new PingResponsePacket(attributes);
            sendPingRequest();
            callback.onEntityProcessed(account, pong);
        }
    }

    public void onTextElementAvailable(final String name, final String content, final OnEntityProcessedCallback callback) {
        Tag attributes = new Tag(name);
        attributes.setContent(content);
        onTextElementAvailable(name, content, attributes, callback);
    }

    private synchronized void processStreamFeatures(final Tag features, final OnPingRequestReceivedCallback callback) throws IOException {
        this.streamFeatures = new AbstractStanza(features);
        sendPingRequest();
        callback.onPingRequestReceived(account, new PingRequestPacket(new Tag("ping")));
    }

    public void parseStreamFeatures(final Tag features, final OnPingRequestReceivedCallback callback) throws IOException {
        processStreamFeatures(features, callback);
    }

    private synchronized void onTextElementAvailable(final String name, final String content, final Tag attributes, final OnPingRequestReceivedCallback callback) {
        if (name.equals("ping")) {
            PingRequestPacket ping = new PingRequestPacket(attributes);
            sendPingRequest();
            callback.onPingRequestReceived(account, ping);
        }
    }

    public void onTextElementAvailable(final String name, final String content, final OnPingRequestReceivedCallback callback) {
        Tag attributes = new Tag(name);
        attributes.setContent(content);
        onTextElementAvailable(name, content, attributes, callback);
    }

    private synchronized void processStreamFeatures(final Tag features, final OnPingResponseSentCallback callback) throws IOException {
        this.streamFeatures = new AbstractStanza(features);
        sendPingRequest();
        callback.onPingResponseSent(account, new PingResponsePacket(new Tag("pong")));
    }

    public void parseStreamFeatures(final Tag features, final OnPingResponseSentCallback callback) throws IOException {
        processStreamFeatures(features, callback);
    }

    private synchronized void onTextElementAvailable(final String name, final String content, final Tag attributes, final OnPingResponseSentCallback callback) {
        if (name.equals("pong")) {
            PingResponsePacket pong = new PingResponsePacket(attributes);
            sendPingRequest();
            callback.onPingResponseSent(account, pong);
        }
    }

    public void onTextElementAvailable(final String name, final String content, final OnPingResponseSentCallback callback) {
        Tag attributes = new Tag(name);
        attributes.setContent(content);
        onTextElementAvailable(name, content, attributes, callback);
    }

    private synchronized void processStreamFeatures(final Tag features, final OnPingRequestProcessedCallback callback) throws IOException {
        this.streamFeatures = new AbstractStanza(features);
        sendPingRequest();
        callback.onPingRequestProcessed(account, new PingRequestPacket(new Tag("ping")));
    }

    public void parseStreamFeatures(final Tag features, final OnPingRequestProcessedCallback callback) throws IOException {
        processStreamFeatures(features, callback);
    }

    private synchronized void onTextElementAvailable(final String name, final String content, final Tag attributes, final OnPingRequestProcessedCallback callback) {
        if (name.equals("ping")) {
            PingRequestPacket ping = new PingRequestPacket(attributes);
            sendPingRequest();
            callback.onPingRequestProcessed(account, ping);
        }
    }

    public void onTextElementAvailable(final String name, final String content, final OnPingRequestProcessedCallback callback) {
        Tag attributes = new Tag(name);
        attributes.setContent(content);
        onTextElementAvailable(name, content, attributes, callback);
    }

    private synchronized void processStreamFeatures(final Tag features, final OnPingResponseProcessedCallback callback) throws IOException {
        this.streamFeatures = new AbstractStanza(features);
        sendPingRequest();
        callback.onPingResponseProcessed(account, new PingResponsePacket(new Tag("pong")));
    }

    public void parseStreamFeatures(final Tag features, final OnPingResponseProcessedCallback callback) throws IOException {
        processStreamFeatures(features, callback);
    }

    private synchronized void onTextElementAvailable(final String name, final String content, final Tag attributes, final OnPingResponseProcessedCallback callback) {
        if (name.equals("pong")) {
            PingResponsePacket pong = new PingResponsePacket(attributes);
            sendPingRequest();
            callback.onPingResponseProcessed(account, pong);
        }
    }

    public void onTextElementAvailable(final String name, final String content, final OnPingResponseProcessedCallback callback) {
        Tag attributes = new Tag(name);
        attributes.setContent(content);
        onTextElementAvailable(name, content, attributes, callback);
    }

    private synchronized void processStreamFeatures(final Tag features, final OnPingRequestSentAndReceivedCallback callback) throws IOException {
        this.streamFeatures = new AbstractStanza(features);
        sendPingRequest();
        callback.onPingRequestSent(account);
        callback.onPingRequestReceived(account, new PingRequestPacket(new Tag("ping")));
    }

    public void parseStreamFeatures(final Tag features, final OnPingRequestSentAndReceivedCallback callback) throws IOException {
        processStreamFeatures(features, callback);
    }

    private synchronized void onTextElementAvailable(final String name, final String content, final Tag attributes, final OnPingRequestSentAndReceivedCallback callback) {
        if (name.equals("ping")) {
            PingRequestPacket ping = new PingRequestPacket(attributes);
            sendPingRequest();
            callback.onPingRequestSent(account);
            callback.onPingRequestReceived(account, ping);
        }
    }

    public void onTextElementAvailable(final String name, final String content, final OnPingRequestSentAndReceivedCallback callback) {
        Tag attributes = new Tag(name);
        attributes.setContent(content);
        onTextElementAvailable(name, content, attributes, callback);
    }

    private synchronized void processStreamFeatures(final Tag features, final OnPingResponseSentAndReceivedCallback callback) throws IOException {
        this.streamFeatures = new AbstractStanza(features);
        sendPingRequest();
        callback.onPingResponseSent(account, new PingResponsePacket(new Tag("pong")));
        callback.onPingResponseReceived(account, new PingResponsePacket(new Tag("pong")));
    }

    public void parseStreamFeatures(final Tag features, final OnPingResponseSentAndReceivedCallback callback) throws IOException {
        processStreamFeatures(features, callback);
    }

    private synchronized void onTextElementAvailable(final String name, final String content, final Tag attributes, final OnPingResponseSentAndReceivedCallback callback) {
        if (name.equals("pong")) {
            PingResponsePacket pong = new PingResponsePacket(attributes);
            sendPingRequest();
            callback.onPingResponseSent(account, pong);
            callback.onPingResponseReceived(account, pong);
        }
    }

    public void onTextElementAvailable(final String name, final String content, final OnPingResponseSentAndReceivedCallback callback) {
        Tag attributes = new Tag(name);
        attributes.setContent(content);
        onTextElementAvailable(name, content, attributes, callback);
    }

    private synchronized void processStreamFeatures(final Tag features, final OnEntityProcessedAndPingRequestSentCallback callback) throws IOException {
        this.streamFeatures = new AbstractStanza(features);
        sendPingRequest();
        callback.onEntityProcessed(account, features);
        callback.onPingRequestSent(account);
    }

    public void parseStreamFeatures(final Tag features, final OnEntityProcessedAndPingRequestSentCallback callback) throws IOException {
        processStreamFeatures(features, callback);
    }

    private synchronized void onTextElementAvailable(final String name, final String content, final Tag attributes, final OnEntityProcessedAndPingRequestSentCallback callback) {
        if (name.equals("ping")) {
            PingRequestPacket ping = new PingRequestPacket(attributes);
            sendPingRequest();
            callback.onEntityProcessed(account, features);
            callback.onPingRequestSent(account);
        }
    }

    public void onTextElementAvailable(final String name, final String content, final OnEntityProcessedAndPingRequestSentCallback callback) {
        Tag attributes = new Tag(name);
        attributes.setContent(content);
        onTextElementAvailable(name, content, attributes, callback);
    }

    private synchronized void processStreamFeatures(final Tag features, final OnEntityProcessedAndPingResponseReceivedCallback callback) throws IOException {
        this.streamFeatures = new AbstractStanza(features);
        sendPingRequest();
        callback.onEntityProcessed(account, features);
        callback.onPingResponseReceived(account, new PingResponsePacket(new Tag("pong")));
    }

    public void parseStreamFeatures(final Tag features, final OnEntityProcessedAndPingResponseReceivedCallback callback) throws IOException {
        processStreamFeatures(features, callback);
    }

    private synchronized void onTextElementAvailable(final String name, final String content, final Tag attributes, final OnEntityProcessedAndPingResponseReceivedCallback callback) {
        if (name.equals("pong")) {
            PingResponsePacket pong = new PingResponsePacket(attributes);
            sendPingRequest();
            callback.onEntityProcessed(account, features);
            callback.onPingResponseReceived(account, pong);
        }
    }

    public void onTextElementAvailable(final String name, final String content, final OnEntityProcessedAndPingResponseReceivedCallback callback) {
        Tag attributes = new Tag(name);
        attributes.setContent(content);
        onTextElementAvailable(name, content, attributes, callback);
    }

    private synchronized void processStreamFeatures(final Tag features, final OnPingRequestSentAndReceivedAndEntityProcessedCallback callback) throws IOException {
        this.streamFeatures = new AbstractStanza(features);
        sendPingRequest();
        callback.onPingRequestSent(account);
        callback.onPingRequestReceived(account, new PingRequestPacket(new Tag("ping")));
        callback.onEntityProcessed(account, features);
    }

    public void parseStreamFeatures(final Tag features, final OnPingRequestSentAndReceivedAndEntityProcessedCallback callback) throws IOException {
        processStreamFeatures(features, callback);
    }

    private synchronized void onTextElementAvailable(final String name, final String content, final Tag attributes, final OnPingRequestSentAndReceivedAndEntityProcessedCallback callback) {
        if (name.equals("ping")) {
            PingRequestPacket ping = new PingRequestPacket(attributes);
            sendPingRequest();
            callback.onPingRequestSent(account);
            callback.onPingRequestReceived(account, ping);
            callback.onEntityProcessed(account, features);
        } else if (name.equals("pong")) {
            PingResponsePacket pong = new PingResponsePacket(attributes);
            sendPingRequest();
            callback.onPingRequestSent(account);
            callback.onPingRequestReceived(account, new PingRequestPacket(new Tag("ping")));
            callback.onEntityProcessed(account, features);
        }
    }

    public void onTextElementAvailable(final String name, final String content, final OnPingRequestSentAndReceivedAndEntityProcessedCallback callback) {
        Tag attributes = new Tag(name);
        attributes.setContent(content);
        onTextElementAvailable(name, content, attributes, callback);
    }

    private synchronized void processStreamFeatures(final Tag features, final OnPingResponseSentAndReceivedAndEntityProcessedCallback callback) throws IOException {
        this.streamFeatures = new AbstractStanza(features);
        sendPingRequest();
        callback.onPingResponseSent(account, new PingResponsePacket(new Tag("pong")));
        callback.onPingResponseReceived(account, new PingResponsePacket(new Tag("pong")));
        callback.onEntityProcessed(account, features);
    }

    public void parseStreamFeatures(final Tag features, final OnPingResponseSentAndReceivedAndEntityProcessedCallback callback) throws IOException {
        processStreamFeatures(features, callback);
    }

    private synchronized void onTextElementAvailable(final String name, final String content, final Tag attributes, final OnPingResponseSentAndReceivedAndEntityProcessedCallback callback) {
        if (name.equals("pong")) {
            PingResponsePacket pong = new PingResponsePacket(attributes);
            sendPingRequest();
            callback.onPingResponseSent(account, pong);
            callback.onPingResponseReceived(account, pong);
            callback.onEntityProcessed(account, features);
        }
    }

    public void onTextElementAvailable(final String name, final String content, final OnPingResponseSentAndReceivedAndEntityProcessedCallback callback) {
        Tag attributes = new Tag(name);
        attributes.setContent(content);
        onTextElementAvailable(name, content, attributes, callback);
    }

    private synchronized void processStreamFeatures(final Tag features, final OnPingRequestSentAndReceivedAndPingResponseSentAndReceivedCallback callback) throws IOException {
        this.streamFeatures = new AbstractStanza(features);
        sendPingRequest();
        callback.onPingRequestSent(account);
        callback.onPingRequestReceived(account, new PingRequestPacket(new Tag("ping")));
        callback.onPingResponseSent(account, new PingResponsePacket(new Tag("pong")));
        callback.onPingResponseReceived(account, new PingResponsePacket(new Tag("pong")));
    }

    public void parseStreamFeatures(final Tag features, final OnPingRequestSentAndReceivedAndPingResponseSentAndReceivedCallback callback) throws IOException {
        processStreamFeatures(features, callback);
    }

    private synchronized void onTextElementAvailable(final String name, final String content, final Tag attributes, final OnPingRequestSentAndReceivedAndPingResponseSentAndReceivedCallback callback) {
        if (name.equals("ping")) {
            PingRequestPacket ping = new PingRequestPacket(attributes);
            sendPingRequest();
            callback.onPingRequestSent(account);
            callback.onPingRequestReceived(account, ping);
            callback.onPingResponseSent(account, new PingResponsePacket(new Tag("pong")));
            callback.onPingResponseReceived(account, new PingResponsePacket(new Tag("pong")));
        } else if (name.equals("pong")) {
            PingResponsePacket pong = new PingResponsePacket(attributes);
            sendPingRequest();
            callback.onPingRequestSent(account);
            callback.onPingRequestReceived(account, new PingRequestPacket(new Tag("ping")));
            callback.onPingResponseSent(account, pong);
            callback.onPingResponseReceived(account, pong);
        }
    }

    public void onTextElementAvailable(final String name, final String content, final OnPingRequestSentAndReceivedAndPingResponseSentAndReceivedCallback callback) {
        Tag attributes = new Tag(name);
        attributes.setContent(content);
        onTextElementAvailable(name, content, attributes, callback);
    }

    private synchronized void processStreamFeatures(final Tag features, final OnEntityProcessedAndPingRequestSentAndReceivedAndPingResponseSentAndReceivedCallback callback) throws IOException {
        this.streamFeatures = new AbstractStanza(features);
        sendPingRequest();
        callback.onEntityProcessed(account, features);
        callback.onPingRequestSent(account);
        callback.onPingRequestReceived(account, new PingRequestPacket(new Tag("ping")));
        callback.onPingResponseSent(account, new PingResponsePacket(new Tag("pong")));
        callback.onPingResponseReceived(account, new PingResponsePacket(new Tag("pong")));
    }

    public void parseStreamFeatures(final Tag features, final OnEntityProcessedAndPingRequestSentAndReceivedAndPingResponseSentAndReceivedCallback callback) throws IOException {
        processStreamFeatures(features, callback);
    }

    private synchronized void onTextElementAvailable(final String name, final String content, final Tag attributes, final OnEntityProcessedAndPingRequestSentAndReceivedAndPingResponseSentAndReceivedCallback callback) {
        if (name.equals("ping")) {
            PingRequestPacket ping = new PingRequestPacket(attributes);
            sendPingRequest();
            callback.onEntityProcessed(account, features);
            callback.onPingRequestSent(account);
            callback.onPingRequestReceived(account, ping);
            callback.onPingResponseSent(account, new PingResponsePacket(new Tag("pong")));
            callback.onPingResponseReceived(account, new PingResponsePacket(new Tag("pong")));
        } else if (name.equals("pong")) {
            PingResponsePacket pong = new PingResponsePacket(attributes);
            sendPingRequest();
            callback.onEntityProcessed(account, features);
            callback.onPingRequestSent(account);
            callback.onPingRequestReceived(account, new PingRequestPacket(new Tag("ping")));
            callback.onPingResponseSent(account, pong);
            callback.onPingResponseReceived(account, pong);
        }
    }

    public void onTextElementAvailable(final String name, final String content, final OnEntityProcessedAndPingRequestSentAndReceivedAndPingResponseSentAndReceivedCallback callback) {
        Tag attributes = new Tag(name);
        attributes.setContent(content);
        onTextElementAvailable(name, content, attributes, callback);
    }
}