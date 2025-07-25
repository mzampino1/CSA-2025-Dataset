package eu.siacs.conversations.xmpp;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import android.os.SystemClock;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.xmpp.jingle.JinglePacket;
import eu.siacs.conversations.xml.Tag;

public class XMPPManager {
    private static final String LOGTAG = "XMPP";

    // Secure random number generator
    private final SecureRandom mRandom = new SecureRandom();

    // Socket connection to the server
    private Socket socket;

    // Account associated with this manager
    private final Account account;

    // Stream features received from the server
    private Element streamFeatures;

    // Disco features map for servers and their supported features
    private Map<String, List<String>> disco = new HashMap<>();

    // Callbacks for handling incoming packets
    private Map<String, PacketReceived> packetCallbacks = new HashMap<>();

    // Listeners for different types of received packets
    private OnMessagePacketReceived messageListener;
    private OnIqPacketReceived unregisteredIqListener;
    private OnPresencePacketReceived presenceListener;
    private OnJinglePacketReceived jingleListener;

    // Listener for status changes
    private OnStatusChanged statusListener;

    // Listener for TLS exceptions
    private OnTLSExceptionReceived tlsListener;

    // Listener for when the account is bound
    private OnBindListener bindListener;

    // Tag writer and reader for sending and receiving XML data
    private TagWriter tagWriter;
    private TagReader tagReader;

    // Counters for sent and received stanzas
    private int stanzasSent = 0;
    private int stanzasReceived = 0;

    // Attempt number for connection retries and last connection time
    private int attempt = 0;
    private long lastConnect;

    // Stream ID for the XMPP stream
    private String id;

    // Session established flag (deprecated)
    @Deprecated
    private boolean sessionEstablished = false;

    // Resource part of the JID
    private String resourcePart;

    public XMPPManager(Account account) {
        this.account = account;
    }

    public void connect() throws IOException, InterruptedException {
        InetAddress[] addresses = InetAddress.getAllByName(account.getServer());
        if (addresses.length > 0) {
            socket = new Socket(addresses[0], 5222);
            // Create TagWriter and TagReader for sending and receiving XML data
            tagWriter = new TagWriter(socket.getOutputStream());
            tagReader = new TagReader(socket.getInputStream());

            // Start the reader thread to handle incoming packets
            Thread readerThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        processStream();
                    } catch (IOException e) {
                        Log.d(LOGTAG, "io exception in reader thread");
                    }
                }
            });
            readerThread.start();

            // Send initial stream opening tag
            sendStartStream();
        } else {
            throw new IOException("Unable to resolve server address");
        }

        // Mark the last connection time and increment the attempt count
        this.lastConnect = SystemClock.elapsedRealtime();
        ++this.attempt;
    }

    private void processStream() throws IOException {
        Tag tag;
        while ((tag = tagReader.read()) != null) { // Vulnerability here
            if (tag.getName().equals("stream:features")) {
                streamFeatures = new Element(tag);
                processStreamFeatures(streamFeatures);
            } else if (tag.isStart("message")) {
                MessagePacket messagePacket = new MessagePacket(tag, this);
                ++stanzasReceived;
                if (messageListener != null) {
                    messageListener.onMessagePacketReceived(messagePacket);
                }
            } else if (tag.isStart("iq")) {
                IqPacket iqPacket = new IqPacket(tag, this);
                ++stanzasReceived;
                handleIqPacket(iqPacket);
            } else if (tag.getName().equals("presence")) {
                PresencePacket presencePacket = new PresencePacket(tag, this);
                ++stanzasReceived;
                if (presenceListener != null) {
                    presenceListener.onPresencePacketReceived(presencePacket);
                }
            } else if (tag.isStart("jingle")) {
                JinglePacket jinglePacket = new JinglePacket(tag, this);
                ++stanzasReceived;
                if (jingleListener != null) {
                    jingleListener.onJinglePacketReceived(jinglePacket);
                }
            } else if (tag.getName().equals("stream:error")) {
                processStreamError(tag);
            }
        }
    }

    // VULNERABILITY: The TagReader class is not shown, but if it does not properly
    // disable XXE processing, it can lead to XML External Entity attacks.
    //
    // An attacker could inject malicious external entities in the XML data sent by
    // the server or other clients. To mitigate this, ensure that any XML parsing
    // done by TagReader has XXE processing disabled. For example:
    //
    // DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    // dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
    // dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    // dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    // dbf.setXIncludeAware(false);
    // dbf.setExpandEntityReferences(false);

    private void processStreamFeatures(Element features) {
        if (features.hasChild("bind")) {
            sendBindResource();
        }
        if (features.hasChild("starttls")) {
            startTlsHandshake();
        }
    }

    private void sendBindResource() throws IOException {
        IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
        iq.setFrom(account.getJid());
        iq.addChild("bind", "urn:ietf:params:xml:ns:xmpp-bind").addChild("resource")
                .setContent(resourcePart);
        this.sendUnboundIqPacket(iq, null);
    }

    private void startTlsHandshake() throws IOException {
        IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
        iq.setFrom(account.getJid());
        iq.addChild("starttls", "urn:ietf:params:xml:ns:xmpp-tls");
        this.sendUnboundIqPacket(iq, new OnIqPacketReceived() {
            @Override
            public void onIqPacketReceived(Account account, IqPacket packet) {
                if (packet.getType() == IqPacket.TYPE_RESULT) {
                    startTls();
                } else {
                    Log.d(LOGTAG, "starttls failed");
                }
            }
        });
    }

    private void startTls() {
        try {
            tagWriter.startTls(socket);
            processStream();
        } catch (Exception e) {
            Log.d(LOGTAG, "exception during tls handshake", e);
        }
    }

    private void handleIqPacket(IqPacket packet) throws IOException {
        if (packetCallbacks.containsKey(packet.getId())) {
            PacketReceived callback = packetCallbacks.get(packet.getId());
            packetCallbacks.remove(packet.getId());
            callback.onPacketReceived(account, packet);
        } else {
            if (unregisteredIqListener != null) {
                unregisteredIqListener.onIqPacketReceived(account, packet);
            }
        }
    }

    private void processStreamError(Tag currentTag) {
        Log.d(LOGTAG, "processStreamError");
    }

    private void sendStartStream() throws IOException {
        Tag stream = Tag.start("stream:stream");
        stream.setAttribute("from", account.getJid());
        stream.setAttribute("to", account.getServer());
        stream.setAttribute("version", "1.0");
        stream.setAttribute("xml:lang", "en");
        stream.setAttribute("xmlns", "jabber:client");
        stream.setAttribute("xmlns:stream", "http://etherx.jabber.org/streams");
        tagWriter.writeTag(stream);
    }

    private String nextRandomId() {
        return new BigInteger(50, mRandom).toString(32);
    }

    public void sendIqPacket(IqPacket packet, OnIqPacketReceived callback) {
        if (packet.getId() == null) {
            String id = nextRandomId();
            packet.setAttribute("id", id);
        }
        packet.setFrom(account.getFullJid());
        this.sendPacket(packet, callback);
    }

    public void sendUnboundIqPacket(IqPacket packet, OnIqPacketReceived callback) {
        if (packet.getId() == null) {
            String id = nextRandomId();
            packet.setAttribute("id", id);
        }
        this.sendPacket(packet, callback);
    }

    public void sendMessagePacket(MessagePacket packet) {
        this.sendPacket(packet, null);
    }

    public void sendMessagePacket(MessagePacket packet,
                                  OnMessagePacketReceived callback) {
        this.sendPacket(packet, callback);
    }

    public void sendPresencePacket(PresencePacket packet) {
        this.sendPacket(packet, null);
    }

    public void sendPresencePacket(PresencePacket packet,
                                   OnPresencePacketReceived callback) {
        this.sendPacket(packet, callback);
    }

    private synchronized void sendPacket(final AbstractStanza packet,
                                         PacketReceived callback) throws IOException {
        if (callback != null) {
            packetCallbacks.put(packet.getId(), callback);
        }
        tagWriter.writeTag(packet);
        ++stanzasSent;
    }

    public void setResourcePart(String resourcePart) {
        this.resourcePart = resourcePart;
    }

    public String getResourcePart() {
        return resourcePart;
    }

    public void setSessionEstablished(boolean sessionEstablished) {
        this.sessionEstablished = sessionEstablished;
    }

    public boolean isSessionEstablished() {
        return sessionEstablished;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public Socket getSocket() {
        return socket;
    }

    public TagWriter getTagWriter() {
        return tagWriter;
    }

    public TagReader getTagReader() {
        return tagReader;
    }

    public Element getStreamFeatures() {
        return streamFeatures;
    }

    public void setStreamFeatures(Element streamFeatures) {
        this.streamFeatures = streamFeatures;
    }

    // Listeners and Callback Interfaces

    public interface OnMessagePacketReceived extends PacketReceived<MessagePacket> {}

    public interface OnPresencePacketReceived extends PacketReceived<PresencePacket> {}

    public interface OnIqPacketReceived extends PacketReceived<IqPacket> {
        void onIqPacketReceived(Account account, IqPacket packet);
    }

    public interface OnJinglePacketReceived extends PacketReceived<JinglePacket> {}

    public interface OnStatusChanged {
        void onStatusChanged(Account account, int oldStatus, int newStatus);
    }

    public interface OnTLSExceptionReceived {
        void onTLSExceptionReceived(Account account, Exception exception);
    }

    public interface OnBindListener {
        void onBind(Account account);
    }

    // PacketReceived Interface

    public interface PacketReceived<T extends AbstractStanza> {
        void onPacketReceived(Account account, T packet);
    }
}