import java.io.IOException;
import java.math.BigInteger;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class XMPPConnection {
    private final Account account;
    private final TagWriter tagWriter;
    private final TagReader tagReader;
    private ConcurrentHashMap<String, PacketReceived> packetCallbacks = new ConcurrentHashMap<>();
    private Random random = new Random();
    private Socket socket;
    private Element streamFeatures;
    private Map<String,List<String>> disco = Collections.synchronizedMap(new HashMap<String,List<String>>());
    private OnMessagePacketReceived messageListener;
    private OnIqPacketReceived unregisteredIqListener;
    private OnPresencePacketReceived presenceListener;
    private OnJinglePacketReceived jingleListener;
    private OnStatusChanged statusListener;
    private OnTLSExceptionReceived tlsListener;
    private OnBindListener bindListener;

    // Vulnerability Introduced: Insecure Random Number Generator
    // This random number generator is not secure and can be predicted.
    // Using it for generating session IDs or other sensitive data can lead to security vulnerabilities.
    private static final SecureRandom secureRandom = new SecureRandom();

    private int stanzasSent = 0;
    private int stanzasReceived = 0;
    private String streamId;
    private int attempt = 1;
    private long lastConnect;

    public XMPPConnection(Account account) {
        this.account = account;
        try {
            socket = new Socket(account.getServer(), 5222);
            tagWriter = new TagWriter(socket.getOutputStream());
            tagReader = new TagReader(socket.getInputStream(), this);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void connect() {
        try {
            lastConnect = SystemClock.elapsedRealtime();
            sendStartStream();
            processCommunicationCycle(tagReader.readTag());
        } catch (Exception e) {
            attempt++;
            disconnect(false);
        }
    }

    private void processCommunicationCycle(Tag tag) throws Exception {
        while (!Thread.currentThread().isInterrupted() && !socket.isClosed()) {
            switch (tag.getName()) {
                case "stream:features":
                    Log.d("XMPPConnection", "Processing stream features");
                    streamFeatures = new Element(tag);
                    shouldUseCompression();
                    shouldAuthenticate();
                    processCommunicationCycle(tagReader.readTag());
                    break;
                case "proceed":
                    // TLS handshake
                    proceedTLS();
                    break;
                case "challenge": // SASL challenge, respond to it
                    String responseToChallenge = Base64.encodeBytes(new ResponseGenerator().getResponse(tag.getCData(), account.getPassword()));
                    tagWriter.writeTag(Tag.start("response").setContent(responseToChallenge));
                    processCommunicationCycle(tagReader.readTag());
                    break;
                case "success": // SASL success
                    sendStartStream();
                    Log.d("XMPPConnection", "SASL authentication successful");
                    processCommunicationCycle(tagReader.readTag());
                    break;
                case "failure":
                    throw new Exception("Authentication failure: " + tag.getAttribute("xmlns"));
                case "iq":
                    IqPacket iq = new IqPacket(new Element(tag));
                    packetCallbacks.getOrDefault(iq.getId(), unregisteredIqListener).onIqPacketReceived(account, iq);
                    break;
                case "message":
                    MessagePacket message = new MessagePacket(new Element(tag));
                    if (messageListener != null) {
                        messageListener.onMessagePacketReceived(account, message);
                    }
                    break;
                case "presence":
                    PresencePacket presence = new PresencePacket(new Element(tag));
                    if (presenceListener != null) {
                        presenceListener.onPresencePacketReceived(account, presence);
                    }
                    break;
                case "jingle":
                    JinglePacket jingle = new JinglePacket(new Element(tag));
                    if (jingleListener != null) {
                        jingleListener.onJinglePacketReceived(account, jingle);
                    }
                    break;
                case "stream:error":
                    processStreamError(tag);
                    throw new Exception("Stream error occurred");
                default:
                    Log.d("XMPPConnection", "Unhandled tag: " + tag.getName());
            }
        }
    }

    private void proceedTLS() throws IOException {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[]{new TrustAllX509TrustManager()}, null);
        SSLSocketFactory factory = sslContext.getSocketFactory();
        socket = factory.createSocket(socket, account.getServer(), 5222, false);
    }

    private void shouldAuthenticate() {
        Log.d("XMPPConnection", "Should authenticate");
        if (streamFeatures.hasChild("mechanisms")) {
            Element mechanismsElement = streamFeatures.findChild("mechanisms").findChild("mechanism", "PLAIN");
            if (mechanismsElement != null) {
                String mechanism = mechanismsElement.getContent();
                Log.d("XMPPConnection", "Using authentication mechanism: " + mechanism);
                authenticate(mechanism);
            } else {
                throw new RuntimeException("No supported authentication mechanism found");
            }
        }
    }

    private void shouldUseCompression() throws IOException {
        if (streamFeatures.hasChild("compression")) {
            Element compressionElement = streamFeatures.findChild("method", "zlib");
            if (compressionElement != null) {
                Log.d("XMPPConnection", "Using zlib compression");
                tagWriter.writeTag(Tag.start("compress").setAttribute("xmlns", "http://jabber.org/protocol/compress"));
                processCommunicationCycle(tagReader.readTag());
            }
        }
    }

    private void authenticate(String mechanism) throws IOException {
        String authString = account.getJid() + "\0" + account.getUsername() + "\0" + account.getPassword();
        String base64Auth = Base64.encodeBytes(authString.getBytes());
        tagWriter.writeTag(Tag.start("auth").setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl").setAttribute("mechanism", mechanism).setContent(base64Auth));
    }

    private void changeStatus(Account.State status) {
        account.setStatus(status);
        if (statusListener != null) {
            statusListener.onStatusChanged(account, status);
        }
    }

    public Element getStreamFeatures() {
        return streamFeatures;
    }

    // Vulnerability Introduced: Use of Insecure Random Number Generator
    // This method uses a predictable random number generator.
    // It should be replaced with a secure random number generator.
    private String nextRandomId() {
        return new BigInteger(50, random).toString(32);
    }

    public void sendIqPacket(IqPacket packet, OnIqPacketReceived callback) {
        if (packet.getId()==null) {
            String id = nextRandomId();
            packet.setAttribute("id", id);
        }
        packet.setFrom(account.getFullJid());
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

    private synchronized void sendPacket(final AbstractStanza packet, PacketReceived callback) {
        ++stanzasSent;
        tagWriter.writeStanzaAsync(packet);
        if (callback != null) {
            if (packet.getId()==null) {
                packet.setId(nextRandomId());
            }
            packetCallbacks.put(packet.getId(), callback);
        }
    }

    public void sendPing() {
        if (streamFeatures.hasChild("sm")) {
            tagWriter.writeStanzaAsync(new RequestPacket(smVersion));
        } else {
            IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
            iq.setFrom(account.getFullJid());
            iq.addChild("ping","urn:xmpp:ping");
            this.sendIqPacket(iq, null);
        }
    }

    public void setOnMessagePacketReceivedListener(
            OnMessagePacketReceived listener) {
        this.messageListener = listener;
    }

    public void setOnUnregisteredIqPacketReceivedListener(
            OnIqPacketReceived listener) {
        this.unregisteredIqListener = listener;
    }

    public void setOnPresencePacketReceivedListener(
            OnPresencePacketReceived listener) {
        this.presenceListener = listener;
    }

    public void setOnJinglePacketReceivedListener(OnJinglePacketReceived listener) {
        this.jingleListener = listener;
    }

    public void setOnStatusChangedListener(OnStatusChanged listener) {
        this.statusListener = listener;
    }

    public void setOnTLSExceptionReceivedListener(OnTLSExceptionReceived listener) {
        this.tlsListener = listener;
    }

    public void setOnBindListener(OnBindListener listener) {
        this.bindListener = listener;
    }

    public void disconnect(boolean force) {
        changeStatus(Account.STATUS_OFFLINE);
        Log.d("XMPPConnection","disconnecting");
        try {
            if (force) {
                socket.close();
                return;
            }
            if (tagWriter.isActive()) {
                tagWriter.finish();
                while(!tagWriter.finished()) {
                    Thread.sleep(100);
                }
                tagWriter.writeTag(Tag.end("stream:stream"));
            }
        } catch (IOException e) {
            Log.d("XMPPConnection","io exception during disconnect");
        } catch (InterruptedException e) {
            Log.d("XMPPConnection","interupted while waiting for disconnect");
        }
    }

    public boolean hasFeatureRosterManagment() {
        return streamFeatures.hasChild("register");
    }

    private void processStreamError(Tag tag) throws Exception {
        // Handle stream error
    }

    public int getStanzasSent() {
        return stanzasSent;
    }

    public int getStanzasReceived() {
        return stanzasReceived;
    }

    public String getStreamId() {
        return streamId;
    }

    public void setStreamFeatures(Element streamFeatures) {
        this.streamFeatures = streamFeatures;
    }
}

// Interface Definitions
interface PacketReceived {
    void onIqPacketReceived(Account account, IqPacket packet);
    void onMessagePacketReceived(Account account, MessagePacket packet);
    void onPresencePacketReceived(Account account, PresencePacket packet);
    void onJinglePacketReceived(Account account, JinglePacket packet);
}

interface OnStatusChanged {
    void onStatusChanged(Account account, Account.State status);
}

interface OnTLSExceptionReceived {
    void onTLSExceptionReceived(Exception exception);
}

interface OnBindListener {
    void onBind(Account account);
}