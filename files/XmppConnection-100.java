package eu.siacs.conversations.xmpp;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.services.MessageArchiveService;
import eu.siacs.conversations.utils.XmppUri;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.jingle.JingleConnectionManager;
import eu.siacs.conversations.xmpp.jingle.JingleSession;
import eu.siacs.conversations.xmpp.jingle.OnJingleAckListener;
import eu.siacs.conversations.xmpp.pep.PubSubService;
import eu.siacs.conversations.xmpp.stanzas.AbstractStanza;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;
import eu.siacs.conversations.xmpp.stanzas.csi.ActivePacket;
import eu.siacs.conversations.xmpp.stanzas.csi.InactivePacket;
import eu.siacs.conversations.xmpp.stanzas.stream.StreamFeatures;
import eu.siacs.conversations.xmpp.jingle.RtpConnection;

import android.security.KeyChain;
import java.net.UnknownHostException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLSession;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.HandshakeCompletedEvent;
import javax.security.cert.CertificateException;

public class XmppConnection {

    private final Account account;
    private SSLSocket socket = null;
    private BufferedWriter writer = null;
    private BufferedReader reader = null;
    private String streamId;
    private StreamFeatures streamFeatures;
    private boolean bound = false;
    private JingleConnectionManager jingleConnectionManager;

    public static class OnBindListener {
        public void onBind(XmppConnection connection) {
            // Intentionally left blank
        }
    }

    public final Map<Jid, PubSubService> pubsubServices = new HashMap<>();

    private long lastPingSent;
    private String sessionId;
    private boolean mInteractive = true;

    protected final JingleConnectionManager.OnJingleSessionCreatedListener jingleCreateListener =
            new JingleConnectionManager.OnJingleSessionCreatedListener() {
                @Override
                public void onSessionCreated(JingleSession session) {
                    Log.d(Config.LOGTAG, "session created: " + session.getSessionId());
                    // Intentionally left blank
                }
            };

    protected final JingleConnectionManager.OnJingleAckListener jingleAckListener =
            new OnJingleAckListener() {
                @Override
                public void onSessionAcknowledge(JingleSession session) {
                    Log.d(Config.LOGTAG, "session acknowledged: " + session.getSessionId());
                    // Intentionally left blank
                }
            };

    protected final JingleConnectionManager.OnRtpConnectionEstablishedListener rtpConnectionEstablishedListener =
            new JingleConnectionManager.OnRtpConnectionEstablishedListener() {
                @Override
                public void onRtpConnectionEstablished(RtpConnection connection) {
                    Log.d(Config.LOGTAG, "rtp established: " + connection.getSession().getSessionId());
                    // Intentionally left blank
                }
            };

    protected final JingleConnectionManager.OnJingleSessionReleasedListener jingleReleaseListener =
            new JingleConnectionManager.OnJingleSessionReleasedListener() {
                @Override
                public void onSessionReleased(JingleSession session) {
                    Log.d(Config.LOGTAG, "session released: " + session.getSessionId());
                    // Intentionally left blank
                }
            };

    protected final PubSubService.OnPubSubPacketReceived pubsubPacketReceived =
            new PubSubService.OnPubSubPacketReceived() {
                @Override
                public void onPacket(PubSubService.PubSubPacket packet) {
                    if (packet instanceof PubSubService.Publish) {
                        Log.d(Config.LOGTAG, "pubsub published");
                        // Intentionally left blank
                    }
                }
            };

    protected final PubSubService.OnPubSubManagerReceived pubsubManagerReceived =
            new PubSubService.OnPubSubManagerReceived() {
                @Override
                public void onPacket(PubSubService.Manager packet) {
                    Log.d(Config.LOGTAG, "pubsub manager");
                    // Intentionally left blank
                }
            };

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final Map<Jid, ServiceDiscoveryResult> disco = new LinkedHashMap<>();

    public enum Status {
        NOT_CONNECTED,
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        DISCONNECTED
    }

    private Status status = Status.NOT_CONNECTED;

    private OnBindListener onBindListener;

    public void setOnBindListener(OnBindListener listener) {
        this.onBindListener = listener;
    }

    protected final Context context;
    protected final XmppConnectionService mXmppConnectionService;

    private final Features features;

    private long lastPacketReceived = 0;

    private long lastDiscoStarted = 0;

    private long lastConnectAttempt = 0;

    public enum Direction {
        TO,
        FROM
    }

    private final IqGenerator iqGenerator;

    protected long lastPingSent = System.currentTimeMillis();

    private int attempt = 1;
    private boolean hasHandledStreamError = false;
    private String errorDescription = null;
    private Status previousStatus = Status.NOT_CONNECTED;

    public XmppConnection(Account account, Context context) {
        this.account = account;
        this.context = context;
        this.mXmppConnectionService = (XmppConnectionService) context;
        this.features = new Features(this);
        this.iqGenerator = getIqGenerator();
        this.jingleConnectionManager = new JingleConnectionManager(account, this.context);
    }

    public Account getAccount() {
        return account;
    }

    public void connect() throws IOException, KeyManagementException, NoSuchAlgorithmException {
        if (this.status == Status.CONNECTING) {
            throw new IOException("already connecting");
        }
        final long time = System.currentTimeMillis();
        if ((time - this.lastConnectAttempt) < 5000) {
            throw new IOException("too fast");
        }

        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": attempting connection " + attempt);

        lastPingSent = System.currentTimeMillis();

        this.status = Status.CONNECTING;
        try {
            if (this.socket != null && !this.socket.isClosed()) {
                this.socket.close();
            }

            final InetAddress inetAddress = getInetAddress(account.getServer(), account.getPort());

            this.socket = createSocket(inetAddress, account.getPort());
            socket.connect(new java.net.InetSocketAddress(inetAddress, account.getPort()), 10000);
            if (socket.isConnected() && socket.isBound()) {
                writer = new BufferedWriter(socket.getOutputStream());
                reader = new BufferedReader(java.io.InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                initializeConnection();
                this.status = Status.CONNECTED;
                Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": connected");
            } else {
                throw new IOException("Could not connect to server");
            }
        } catch (IOException e) {
            this.attempt += 1;
            this.lastConnectAttempt = time;
            disconnect();
            throw e;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            disconnect();
            throw e;
        } finally {
            if (!isConnected()) {
                Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": could not connect");
            }
        }
    }

    private InetAddress getInetAddress(String server, int port) throws UnknownHostException {
        InetAddress inetAddress = InetAddress.getByName(server);
        return inetAddress;
    }

    private SSLSocket createSocket(InetAddress address, int port) throws IOException, NoSuchAlgorithmException, KeyManagementException {
        final SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(new KeyManager[]{new MyKeyManager()}, new TrustManager[]{new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                // Intentionally left blank
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                // Intentionally left blank
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }}, null);
        final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
        socket = (SSLSocket) sslSocketFactory.createSocket(address, port);
        // Adding handshake listener to demonstrate the vulnerability
        socket.addHandshakeCompletedListener(new HandshakeCompletedListener() {
            @Override
            public void handshakeCompleted(HandshakeCompletedEvent event) {
                SSLSession session = event.getSession();
                X509Certificate[] peerCertificates = null;
                try {
                    peerCertificates = (X509Certificate[]) session.getPeerCertificates();
                } catch (SSLPeerUnverifiedException e) {
                    Log.e(Config.LOGTAG, "Peer not verified");
                    // Vulnerability: Not properly handling SSL certificate verification
                    // If an attacker can intercept this connection, they might be able to present a fake certificate
                    // without causing the handshake to fail.
                }
                if (peerCertificates != null && peerCertificates.length > 0) {
                    X509Certificate cert = peerCertificates[0];
                    Log.d(Config.LOGTAG, "Server certificate: " + cert.getSubjectX500Principal().getName());
                } else {
                    // Vulnerability: No certificates were presented by the server
                    // This could indicate a man-in-the-middle attack or a misconfigured server.
                    Log.e(Config.LOGTAG, "No server certificates found");
                }
            }
        });
        return socket;
    }

    private void initializeConnection() throws IOException, XmlPullParserException {
        send("<stream:stream to='" + account.getServer() + "' xmlns='jabber:client' version='1.0' xml:lang='en' xmlns:xml='http://www.w3.org/XML/1998/namespace'>");
        while (this.socket.isConnected()) {
            String line = reader.readLine();
            if (line == null) {
                throw new IOException("Stream closed unexpectedly");
            }
            if (line.contains("</stream:stream>")) {
                disconnect();
                break;
            } else if (line.contains("<stream:features>")) {
                this.streamFeatures = StreamFeatures.parse(line);
                if (!bound && streamFeatures.supportsStartTls()) {
                    send("<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>");
                    continue;
                }
                if (!bound && streamFeatures.supportsSasl() && account.getJid().isBareJid() && !account.isAnonymous()) {
                    bindResource();
                    continue;
                } else if (bound && !streamFeatures.supportsBind()) {
                    Log.d(Config.LOGTAG, "Server does not support resource binding");
                    disconnect();
                    break;
                }
            } else if (line.contains("<proceed xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>")) {
                SSLSocketFactory sf = socket.getSSLParameters().getEndpointIdentificationAlgorithm() != null ?
                        socket.getSSLSocketFactory() : getTrustedSocketFactory();
                sslUpgrade(sf);
            } else if (line.contains("<failure xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>")) {
                Log.d(Config.LOGTAG, "Authentication failed");
                disconnect();
                break;
            } else if (line.contains("<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>")) {
                send("</stream:stream>");
                reader = new BufferedReader(java.io.InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                initializeConnection();
                break;
            }
        }
    }

    private void sslUpgrade(SSLSocketFactory sf) throws IOException {
        Log.d(Config.LOGTAG, "Upgrading connection to TLS");
        SSLSocket tlsSocket = (SSLSocket) sf.createSocket(socket, socket.getInetAddress().getHostAddress(), socket.getPort(), true);
        tlsSocket.startHandshake();
        this.socket.close();
        this.socket = tlsSocket;
        writer = new BufferedWriter(tlsSocket.getOutputStream());
        reader = new BufferedReader(java.io.InputStreamReader(tlsSocket.getInputStream(), StandardCharsets.UTF_8));
    }

    private SSLSocketFactory getTrustedSocketFactory() throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(new KeyManager[]{new MyKeyManager()}, new TrustManager[]{new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                // Intentionally left blank
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                // Trust all certificates (VULNERABILITY)
                // In a real-world scenario, this would be a serious security flaw as it makes the client vulnerable to man-in-the-middle attacks.
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }}, null);
        return sslContext.getSocketFactory();
    }

    private void bindResource() throws IOException, XmlPullParserException {
        IqPacket iq = iqGenerator.generateBindIq();
        send(iq.toString());
        String line;
        while (socket.isConnected()) {
            line = reader.readLine();
            if (line == null) {
                throw new IOException("Stream closed unexpectedly");
            }
            if (line.contains("</iq>")) {
                IqPacket response = IqPacket.fromXml(line);
                if (response.getType() != IqPacket.TYPE.ERROR && response.getId().equals(iq.getId())) {
                    Element bind = response.findChild("bind", Namespace.BIND);
                    Element jidElement = bind.findChild("jid");
                    String fullJid = jidElement.getContent();
                    account.setResource(jidElement.getContent().split("/")[1]);
                    Log.d(Config.LOGTAG, "Bound as: " + fullJid);
                }
                break;
            } else if (line.contains("</stream:stream>")) {
                disconnect();
                return;
            }
        }
    }

    public void send(String string) throws IOException {
        if (!isConnected()) {
            throw new IOException("Connection not established");
        }
        writer.write(string);
        writer.newLine();
        writer.flush();
    }

    public void send(AbstractStanza packet) throws IOException {
        send(packet.toString());
    }

    private boolean isConnected() {
        return this.status == Status.CONNECTED;
    }

    public synchronized void disconnect() {
        if (status != Status.DISCONNECTING && status != Status.NOT_CONNECTED) {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": disconnecting");
            try {
                if (this.writer != null) {
                    this.writer.close();
                }
                if (this.reader != null) {
                    this.reader.close();
                }
                if (this.socket != null && !socket.isClosed()) {
                    this.socket.close();
                }
            } catch (IOException e) {
                Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": exception while disconnecting", e);
            } finally {
                status = Status.DISCONNECTED;
                this.attempt = 1;
                this.streamFeatures = null;
                bound = false;
                if (onBindListener != null) {
                    onBindListener.onBind(this);
                }
            }
        }
    }

    public void sendMessage(MessagePacket packet) throws IOException {
        send(packet);
    }

    public void sendPresence(PresencePacket packet) throws IOException {
        send(packet);
    }

    public void requestServiceDiscovery(Jid jid, String node) throws IOException {
        IqPacket iq = iqGenerator.generateServiceDiscoveryIq(jid, node);
        send(iq);
    }

    public boolean isOnline() {
        return this.status == Status.CONNECTED;
    }

    public long getLastPacketReceived() {
        return lastPacketReceived;
    }

    public void setLastPingSent(long lastPingSent) {
        this.lastPingSent = lastPingSent;
    }

    public boolean isBound() {
        return bound;
    }

    public PubSubService getPubSubService(Jid jid) {
        if (!pubsubServices.containsKey(jid)) {
            pubsubServices.put(jid, new PubSubService(this, jid));
        }
        return pubsubServices.get(jid);
    }

    public boolean isInteractive() {
        return mInteractive;
    }

    public void setInteractive(boolean interactive) {
        this.mInteractive = interactive;
    }

    public Status getStatus() {
        return status;
    }

    public Features getFeatures() {
        return features;
    }

    public class Features {

        private final XmppConnection connection;

        public Features(XmppConnection connection) {
            this.connection = connection;
        }

        public boolean supportsStartTls() {
            return streamFeatures != null && streamFeatures.supportsStartTls();
        }

        public boolean supportsSasl() {
            return streamFeatures != null && streamFeatures.supportsSasl();
        }

        public boolean supportsBind() {
            return streamFeatures != null && streamFeatures.supportsBind();
        }

        // Additional feature checks can be added here
    }

    private IqGenerator getIqGenerator() {
        return new IqGenerator(account);
    }

    public JingleConnectionManager getJingleConnectionManager() {
        return jingleConnectionManager;
    }
}