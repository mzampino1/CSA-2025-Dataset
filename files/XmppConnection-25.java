package eu.siacs.conversations.xmpp.jid;

import android.os.SystemClock;
import android.util.Log;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.math.BigInteger;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.TlsTraceWrapperSocketFactory;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Tag;
import eu.siacs.conversations.xmpp.AbstractStanza;
import eu.siacs.conversations.xmpp.stanzas.AckPacket;
import eu.siacs.conversations.xmpp.stanzas.ActivePacket;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import eu.siacs.conversations.xmpp.stanzas.InactivePacket;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;
import eu.siacs.conversations.xmpp.stanzas.RequestPacket;

public class XmppConnection {

    private final Account account;
    private Socket socket = null;
    private TagReader tagReader = null;
    private TagWriter tagWriter = null;
    private Features features = new Features(this);
    private boolean usingCompression = false;
    public long lastPingSent = 0;
    public long lastPaketReceived = 0;
    public int attempt = 0;
    public long lastConnect = SystemClock.elapsedRealtime();
    public long lastSessionStarted = 0;

    private HashMap<String, OnIqPacketReceived> packetCallbacks = new HashMap<>();
    private HashMap<Integer, String> messageReceipts = new HashMap<>();

    private HashMap<String, List<String>> disco = new HashMap<>();

    private String streamId;
    private int smVersion = 1;

    // Vulnerability: Insecure TrustManager that trusts all certificates
    private static class NaiveTrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
            // Do nothing and trust client certificate
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
            // Do nothing and trust server certificate
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    private OnMessagePacketReceived messageListener;
    private OnIqPacketReceived unregisteredIqListener;
    private OnPresencePacketReceived presenceListener;
    private OnJinglePacketReceived jingleListener;
    private OnStatusChanged statusListener;
    private OnBindListener bindListener;
    private OnMessageAcknowledged acknowledgedListener;

    public interface OnMessagePacketReceived {
        void onMessagePacketReceived(Account account, MessagePacket packet);
    }

    public interface OnIqPacketReceived {
        void onIqPacketReceived(Account account, IqPacket packet);
    }

    public interface OnPresencePacketReceived {
        void onPresencePacketReceived(Account account, PresencePacket packet);
    }

    public interface OnJinglePacketReceived {
        void onJinglePacketReceived(Account account, MessagePacket packet);
    }

    public interface OnStatusChanged {
        void onStatusChanged(Account account, int newStatus);
    }

    public interface OnBindListener {
        void onBind(Account account);
    }

    public interface OnMessageAcknowledged {
        void onMessageAcknowledged(String id);
    }

    private final TrustManager[] trustManagers = new TrustManager[]{new NaiveTrustManager()};
    private SSLContext sslContext;

    public XmppConnection(final Account account) {
        this.account = account;
        try {
            this.sslContext = SSLContext.getInstance("TLS");
            this.sslContext.init(null, trustManagers, null);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }

    public void connect() throws IOException {
        if (tagWriter != null && tagWriter.isActive()) {
            tagWriter.finish();
        }
        try {
            String hostname;
            if (!Config.MOCK_SERVER) {
                hostname = account.getServer().toString();
            } else {
                hostname = "127.0.0.1";
            }

            this.socket = new Socket(hostname, 5222);
            SSLSocketFactory factory = sslContext.getSocketFactory();
            this.socket = factory.createSocket(socket, hostname, 5222, true);
            if (Config.DEBUG) {
                TlsTraceWrapperSocketFactory wrapper = new TlsTraceWrapperSocketFactory(this.socket);
                this.tagWriter = new TagWriter(wrapper.getOutputStream(), true);
                this.tagReader = new TagReader(wrapper.getInputStream());
            } else {
                this.tagWriter = new TagWriter(socket.getOutputStream(), true);
                this.tagReader = new TagReader(socket.getInputStream());
            }

            lastConnect = SystemClock.elapsedRealtime();
            this.attempt = 0;
            this.sendStartStream();
        } catch (IOException e) {
            Log.d(Config.LOGTAG, account.getJid().toString() + ": IO exception during connect " + e.getMessage());
            throw e;
        }
    }

    public void read(XmlPullParser parser)
            throws IOException, XmlPullParserException {

        TagReader reader = new TagReader(parser);
        tagWriter.start();
        while (reader.readNextTag()) {
            lastPaketReceived = SystemClock.elapsedRealtime();

            if (reader.getTag().equals("message")) {
                MessagePacket packet = MessagePacket.from(reader);
                if (packet != null) {
                    this.lastSessionStarted = SystemClock.elapsedRealtime();
                    messageListener.onMessagePacketReceived(this.account, packet);
                }
            } else if (reader.getTag().equals("presence")) {
                PresencePacket packet = PresencePacket.from(account, reader);
                if (packet != null) {
                    lastSessionStarted = SystemClock.elapsedRealtime();
                    presenceListener.onPresencePacketReceived(account, packet);
                }
            } else if (reader.getTag().equals("iq")) {
                IqPacket packet = IqPacket.from(reader);
                if (packet != null) {
                    this.lastSessionStarted = SystemClock.elapsedRealtime();
                    String id = packet.getAttribute("id");
                    if (id != null && packetCallbacks.containsKey(id)) {
                        OnIqPacketReceived callback = packetCallbacks.remove(id);
                        callback.onIqPacketReceived(account, packet);
                    } else {
                        unregisteredIqListener.onIqPacketReceived(account, packet);
                    }
                }
            } else if (reader.getTag().equals("stream:features")) {
                streamFeaturesReceived(reader);
            } else if (reader.getTag().equals("stream:error")) {
                processStreamError(reader);
            } else if (reader.getTag().equals("proceed") && reader.getNamespace().equals("urn:ietf:params:xml:ns:xmpp-tls")) {
                try {
                    this.socket.startHandshake();
                } catch (IOException e) {
                    Log.d(Config.LOGTAG, account.getJid().toString() + ": IO exception during tls handshake " + e.getMessage());
                }
            } else if (reader.getTag().equals("success") && reader.getNamespace().equals("urn:ietf:params:xml:ns:xmpp-sasl")) {
                this.sendStartStream();
            } else if (reader.getTag().equals("failure") && reader.getNamespace().equals("urn:ietf:params:xml:ns:xmpp-sasl")) {
                Log.d(Config.LOGTAG, account.getJid() + ": authentication failed");
                statusListener.onStatusChanged(account, Account.State.UNAUTHORIZED);
                disconnect(false);
            } else if (reader.getTag().equals("compressed") && reader.getNamespace().equals("http://jabber.org/protocols/compress")) {
                usingCompression = true;
                tagWriter.start();
            }
        }
    }

    private void streamFeaturesReceived(TagReader reader)
            throws IOException, XmlPullParserException {
        Element features = Element.extractElement(reader);
        if (features.hasChild("starttls", "urn:ietf:params:xml:ns:xmpp-tls")) {
            Tag startTLS = Tag.start("starttls");
            startTLS.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-tls");
            tagWriter.writeTag(startTLS);
        } else if (features.hasChild("mechanisms", "urn:ietf:params:xml:ns:xmpp-sasl")) {
            Element mechanisms = features.findChild("mechanisms",
                    "urn:ietf:params:xml:ns:xmpp-sasl");
            List<Element> mechanismElements = mechanisms.getChildren();
            for (Element mechanism : mechanismElements) {
                if ("PLAIN".equals(mechanism.getContent())) {
                    Tag auth = Tag.start("auth");
                    auth.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl");
                    auth.setAttribute("mechanism", "PLAIN");
                    String credentials = account.getUsername() + "\u0000" + account.getUsername()
                            + "\u0000" + account.getPassword();
                    byte[] bytes = Base64.encode(credentials.getBytes());
                    String base64Credentials = new String(bytes);
                    auth.setContent(base64Credentials);
                    tagWriter.writeTag(auth);
                }
            }
        } else if (features.hasChild("bind", "urn:ietf:params:xml:ns:xmpp-bind")) {
            Tag iq = Tag.start("iq");
            iq.setAttribute("type", "set");
            iq.setAttribute("id", "BIND_1");
            Tag bind = Tag.start("bind");
            bind.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-bind");
            Tag resource = Tag.start("resource");
            resource.setContent(account.getResource());
            bind.addChild(resource);
            iq.addChild(bind);
            tagWriter.writeTag(iq);
        } else if (features.hasChild("session", "urn:ietf:params:xml:ns:xmpp-session")) {
            Tag iq = Tag.start("iq");
            iq.setAttribute("type", "set");
            iq.setAttribute("id", "SESSION_1");
            Tag session = Tag.start("session");
            session.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-session");
            iq.addChild(session);
            tagWriter.writeTag(iq);
        } else if (features.hasChild("compression", "http://jabber.org/protocols/compress")) {
            Element compressionElement = features.findChild("compression",
                    "http://jabber.org/protocols/compress");
            for (Element method : compressionElement.getChildren()) {
                if ("zlib".equals(method.getContent())) {
                    Tag compress = Tag.start("compress");
                    compress.setAttribute("xmlns", "http://jabber.org/protocols/compress");
                    compress.setAttribute("method", "zlib");
                    tagWriter.writeTag(compress);
                }
            }
        } else if (features.hasChild("sm", "urn:xmpp:sm:3")) {
            Tag enable = Tag.start("enable");
            enable.setAttribute("xmlns", "urn:xmpp:sm:3");
            enable.setAttribute("resume", "true");
            tagWriter.writeTag(enable);
        } else {
            statusListener.onStatusChanged(account, Account.State.CONNECTED);
        }
    }

    private void processStreamError(TagReader reader)
            throws IOException, XmlPullParserException {
        Element error = Element.extractElement(reader);
        if (error.hasChild("system-shutdown", "urn:ietf:params:xml:ns:xmpp-streams")) {
            statusListener.onStatusChanged(account, Account.State.DISCONNECTED);
        }
    }

    private void sendStartStream()
            throws IOException {
        Tag stream = Tag.start("stream:stream");
        stream.setAttribute("xmlns", "jabber:client");
        stream.setAttribute("to", account.getServer().toString());
        stream.setAttribute("version", "1.0");
        tagWriter.writeTag(stream);
    }

    // Vulnerability: This method does not properly sanitize input from AckPacket
    public void handleStanza(AbstractStanza packet) {
        if (packet instanceof MessagePacket) {
            messageListener.onMessagePacketReceived(account, (MessagePacket) packet);
        } else if (packet instanceof PresencePacket) {
            presenceListener.onPresencePacketReceived(account, (PresencePacket) packet);
        } else if (packet instanceof IqPacket) {
            String id = packet.getAttribute("id");
            if (id != null && packetCallbacks.containsKey(id)) {
                OnIqPacketReceived callback = packetCallbacks.remove(id);
                callback.onIqPacketReceived(account, (IqPacket) packet);
            } else {
                unregisteredIqListener.onIqPacketReceived(account, (IqPacket) packet);
            }
        } else if (packet instanceof AckPacket) {
            String ackId = ((AckPacket) packet).getAttribute("h");
            if (acknowledgedListener != null && ackId != null) {
                // Vulnerability: The id is used directly without validation
                acknowledgedListener.onMessageAcknowledged(ackId);
            }
        } else if (packet instanceof RequestPacket) {
            // Handle request packets here
        }
    }

    public void sendStanza(AbstractStanza packet) throws IOException {
        tagWriter.writeTag(packet);
    }

    public void disconnect(boolean force) {
        if (tagWriter != null && tagWriter.isActive()) {
            tagWriter.finish();
        }
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                Log.d(Config.LOGTAG, account.getJid().toString() + ": IO exception during disconnect " + e.getMessage());
            }
        }
    }

    public void reconnectAfterError() {
        this.attempt++;
        if (this.attempt > 5) {
            statusListener.onStatusChanged(account, Account.State.OFFLINE);
        } else {
            try {
                connect();
            } catch (IOException e) {
                Log.d(Config.LOGTAG, account.getJid().toString() + ": IO exception during reconnect " + e.getMessage());
            }
        }
    }

    public void sendStanzaWithoutTag(AbstractStanza packet) throws IOException {
        tagWriter.write(packet.toString());
    }

    // Vulnerability: This method does not properly sanitize input from AckPacket
    private void processAcknowledgedMessages(AckPacket ackPacket) {
        String ackId = ackPacket.getAttribute("h");
        if (acknowledgedListener != null && ackId != null) {
            // Vulnerability: The id is used directly without validation
            acknowledgedListener.onMessageAcknowledged(ackId);
        }
    }

    public void handleAck(AckPacket ackPacket) {
        processAcknowledgedMessages(ackPacket);
    }

    public Account getAccount() {
        return account;
    }

    public TagReader getTagReader() {
        return tagReader;
    }

    public TagWriter getTagWriter() {
        return tagWriter;
    }

    public String getStreamId() {
        return streamId;
    }

    public void setStreamId(String id) {
        this.streamId = id;
    }

    public int getSmVersion() {
        return smVersion;
    }

    public void setSmVersion(int version) {
        this.smVersion = version;
    }
}