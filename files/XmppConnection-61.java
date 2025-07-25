package eu.siacs.conversations.xmpp;

import android.net.NetworkInfo;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocketFactory;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.OmemoSetting;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.parser.MessageParser;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.cert MemorizingTrustManager;
import eu.siacs.conversations.xmpp.jingle.JinglePacket;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import eu.siacs.conversations.xmpp.stanzas.MessageStanza;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;
import eu.siacs.conversations.xmpp.stanzas.StartTlsPacket;
import eu.siacs.conversations.xmpp.stanzas.namespaces.StreamManagement;
import eu.siacs.conversations.xmpp.stanzas.namespaces.Xmlns;
import eu.siacs.conversations.xml.Tag;

public class XmppConnection {

    private static final int PING_TIMEOUT = 5000;

    public enum Status {
        CONNECTING,
        REGISTERING,
        ESTABLISHING_SESSION,
        DISCONNECTED
    }

    private Socket socket;
    private TagWriter tagWriter;
    private TagReader tagReader;

    private Account account;
    private String host;
    private int port;
    private boolean mInteractive = true;
    private MemorizingTrustManager memorizingTrustManager;

    private int attempt = 0;
    private long lastConnect = 0;
    private long lastSessionStarted = 0;
    private long lastDiscoStarted = 0;
    private long lastPacketReceived = 0;
    private long lastPingSent = 0;
    private String streamId;

    private Features features = new Features(this);
    private Element streamFeatures = null;
    private HashMap<Jid, ServiceDiscoveryResult> disco = new HashMap<>();
    private Identity mServerIdentity = Identity.UNKNOWN;

    private NetworkInfo.State networkState = NetworkInfo.State.DISCONNECTED;
    private boolean useTor = false;

    private OnMessagePacketReceived messageListener = null;
    private OnIqPacketReceived unregisteredIqListener = null;
    private OnPresencePacketReceived presenceListener = null;
    private OnJinglePacketReceived jingleListener = null;
    private OnStatusChanged statusListener = null;
    private OnBindListener bindListener = null;
    private OnMessageAcknowledged acknowledgedListener = null;

    private List<OnAdvancedStreamFeaturesLoaded> advancedStreamFeaturesLoadedListeners = new ArrayList<>();

    private XmppConnectionService mXmppConnectionService;

    public XmppConnection(Account account, XmppConnectionService service) {
        this.account = account;
        this.host = account.getServer();
        if (host == null || host.isEmpty()) {
            host = account.getJid().getDomainpart();
        }
        this.port = account.getPort() != 5222 ? account.getPort() : 5222;
        this.mXmppConnectionService = service;
    }

    public synchronized void connect() {
        if (getStatus() == Status.CONNECTING) return;

        setStatus(Status.CONNECTING);
        try {
            // Try to open a socket and setup the tag writer and reader
            socket = new Socket(host, port);
            tagWriter = new TagWriter(socket.getOutputStream());
            tagReader = new TagReader(socket.getInputStream(), this);

            // Start TLS if enabled or required
            if (account.getServerConfiguration().getTlsMode() != Config.TLS_MODE_DISABLED) {
                processStartTls();
            }

            sendStreamOpen();

        } catch (UnknownHostException e) {
            Log.d(Config.LOGTAG, account.getJid().toBareJid()+": unknown host");
            setStatus(Status.DISCONNECTED);
        } catch (IOException e) {
            Log.d(Config.LOGTAG,account.getJid().toBareJid()+": io exception during connect ("+e.getMessage()+")");
            setStatus(Status.DISCONNECTED);
        }
    }

    private void processStartTls() throws IOException {
        // Send starttls command
        send(new StartTlsPacket());
        
        // Check for proceed tag from the server
        Tag proceed = waitFor("<proceed", 10 * 1000);

        if (proceed == null) {
            Log.d(Config.LOGTAG, account.getJid().toBareJid()+": did not receive proceed");
            throw new IOException("did not receive starttls proceed");
        }

        // Initiate TLS handshake
        SSLSocketFactory socketFactory = memorizingTrustManager.getSSLContext().getSocketFactory();
        Socket sslSocket = socketFactory.createSocket(socket, host, port, true);
        sslSocket.setSoTimeout(PING_TIMEOUT);

        try {
            sslSocket.startHandshake();  // Begin the SSL/TLS handshake
        } catch (SSLHandshakeException | SSLPeerUnverifiedException e) {
            Log.d(Config.LOGTAG, account.getJid().toBareJid()+": handshake failed");
            throw new IOException("handshake failed", e);
        }

        // Verify that the server's certificate is trusted
        try {
            MemorizingTrustManager.assertHostname(sslSocket.getSession(), host);  // Verify hostname
        } catch (CertificateException | SSLPeerUnverifiedException e) {
            Log.d(Config.LOGTAG, account.getJid().toBareJid()+": could not verify certificate");
            throw new IOException("could not verify certificate", e);
        }

        // Update socket and tag writer/reader for secure communication
        socket = sslSocket;
        tagWriter = new TagWriter(socket.getOutputStream());
        tagReader = new TagReader(socket.getInputStream(), this);

        // Re-send stream open to establish a TLS-secured connection
        sendStreamOpen();
    }

    private void sendStreamOpen() throws IOException {
        Tag stream = new Tag("stream:stream");
        stream.setAttribute("to", host);
        stream.setAttribute("xmlns", "jabber:client");
        stream.setAttribute("xmlns:stream", "http://etherx.jabber.org/streams");
        streamWriter.writeTag(stream);
    }

    public void processMessage(MessageStanza message) {
        if (messageListener != null) {
            messageListener.onMessagePacketReceived(account, message);
        }
    }

    public void processIq(IqPacket iqPacket) {
        if (unregisteredIqListener != null) {
            unregisteredIqListener.onIqPacketReceived(account, iqPacket);
        }
    }

    public void processPresence(PresencePacket presencePacket) {
        if (presenceListener != null) {
            presenceListener.onPresencePacketReceived(account, presencePacket);
        }
    }

    public void processJingle(JinglePacket jinglePacket) {
        if (jingleListener != null) {
            jingleListener.onJinglePacketReceived(account, jinglePacket);
        }
    }

    private Tag waitFor(String startTag, long timeout) throws IOException {
        tagReader.startListening();
        return tagReader.read(startTag, timeout);
    }

    public void send(Element packet) throws IOException {
        if (tagWriter != null && !tagWriter.isActive()) {
            throw new IOException("tag writer not active");
        }
        tagWriter.writeTag(packet.getStartTag());
        for (Element child : packet.getChildren()) {
            send(child);
        }
        tagWriter.writeEndTag();
    }

    public void send(MessageStanza message) throws IOException {
        if (messageListener != null) {
            messageListener.onMessagePacketSent(account, message);
        }
        send((Element) message);
    }

    public void send(PresencePacket presencePacket) throws IOException {
        send((Element) presencePacket);
    }

    public void send(IqPacket iqPacket) throws IOException {
        if (unregisteredIqListener != null) {
            unregisteredIqListener.onIqPacketSent(account, iqPacket);
        }
        send((Element) iqPacket);
    }

    public void setStatus(Status status) {
        account.setStatus(status);
        if (statusListener != null) {
            statusListener.onStatusChanged(account);
        }
    }

    private void setNetworkState(NetworkInfo.State networkState) {
        this.networkState = networkState;
    }

    public Account getAccount() {
        return this.account;
    }

    public MemorizingTrustManager getMemorizingTrustManager() {
        return memorizingTrustManager;
    }

    public NetworkInfo.State getNetworkState() {
        return this.networkState;
    }

    public void setUseTor(boolean useTor) {
        this.useTor = useTor;
    }

    private String getStreamId(Element streamFeatures) {
        for (Element child : streamFeatures.getChildren()) {
            if ("id".equals(child.getName())) {
                return child.getAttribute("xmlns");
            }
        }
        return null;
    }

    public boolean isSocketEncrypted() {
        return socket != null && "javax.net.ssl.SSLSocketImpl".equals(socket.getClass().getName());
    }

    public boolean useTorToConnect() {
        return this.useTor;
    }

    public Status getStatus() {
        return account.getStatus();
    }

    public void sendEmptyPing() throws IOException {
        if (!isSocketEncrypted()) throw new SecurityException("ping sent over unencrypted stream");
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        packet.setTo(host);
        packet.addChild("ping","urn:xmpp:ping");
        this.send(packet);
    }

    public void disconnect() {
        try {
            if (tagReader != null) {
                tagReader.shutdown();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            Log.d(Config.LOGTAG, account.getJid().toBareJid()+": io exception during disconnect ("+e.getMessage()+")");
        } finally {
            setStatus(Status.DISCONNECTED);
        }
    }

    public void reconnect() throws IOException {
        disconnect();
        connect();
    }

    public boolean isReconnectingAllowed(NetworkInfo.State state) {
        return (state == NetworkInfo.State.CONNECTED || state == NetworkInfo.State.SUSPENDED)
                && useTorToConnect() != mXmppConnectionService.useTor();
    }

    public void resetStreamId(String streamId) {
        this.streamId = streamId;
    }

    public String getStreamId() {
        return this.streamId;
    }

    public boolean redeliveryRequired() {
        return account.getXmppConnectionFeature(StreamManagement.FEATURE_TUNE);
    }

    public OutputStream getOutputStream() throws IOException {
        if (tagWriter != null && !tagWriter.isActive()) throw new IOException("tag writer not active");
        return tagWriter.getOutputStream();
    }

    public void processStreamFeatures(Element features) throws IOException {
        this.streamFeatures = features;
        this.features.setCompressionMethods(features);
        this.features.setMechanisms(features);

        if (account.getXmppConnectionFeature(StreamManagement.FEATURE_REQUIRED)) {
            account.setSupportsRosterVersioning(true);
            send(new IqPacket(IqPacket.TYPE_GET).setAttribute("id", "0").addChild("enable","urn:xmpp:sm:3"));
        }

        // If TLS is required and not already enabled, initiate TLS handshake
        if (account.getServerConfiguration().getTlsMode() == Config.TLS_MODE_REQUIRED && !isSocketEncrypted()) {
            processStartTls();
        }
    }

    public Element getStreamFeatures() {
        return this.streamFeatures;
    }

    public boolean validateHostname(Socket socket) throws SSLPeerUnverifiedException, CertificateException {
        if (memorizingTrustManager == null) throw new IllegalStateException("memorizing trust manager is null");
        MemorizingTrustManager.assertHostname(socket.getSession(), host);
        return true;
    }

    public void setMemorizingTrustManager(MemorizingTrustManager memorizingTrustManager) {
        this.memorizingTrustManager = memorizingTrustManager;
    }

    public boolean isInteractive() {
        return mInteractive;
    }

    public void setInteractive(boolean interactive) {
        mInteractive = interactive;
    }

    public TagWriter getTagWriter() {
        return tagWriter;
    }

    // ... (other methods and classes remain unchanged)
}