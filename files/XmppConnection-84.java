package eu.siacs.conversations.xmpp.jid;

import android.util.Log;
import android.util.SparseArray;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.ServiceDiscoveryResult;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnBindFailed;
import eu.siacs.conversations.xmpp.crypto.TlsHelper;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;
import eu.siacs.conversations.xmpp.stanzas.Stanza;
import eu.siacs.conversations.xmpp.stanzas.streamfeatures.StreamCompressionFeature;
import eu.siacs.conversations.xmpp.stanzas.streamfeatures.StreamFeatures;
import eu.siacs.conversations.xmpp.stanzas.streamfeatures.StreamTlsFeature;

public class XmppConnection {
    private final String TAG = "XmppConnection";

    private Account account;

    private Element streamFeatures;
    private String streamId;

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean binding = new AtomicBoolean(false);

    private Socket socket;

    private TagWriter tagWriter;

    private long lastPacketReceived = 0;
    private long lastPingSent = 0;
    private long lastConnect = 0;
    private long lastSessionStarted = 0;
    private long lastDiscoStarted = 0;

    private int attempt = 0;
    private final Map<Jid, ServiceDiscoveryResult> disco = new HashMap<>();

    public static final String NS_TLS_NAMESPACE = "http://jabber.org/protocol/tls";

    private boolean mInteractive = true;

    private Features features;

    private final XmppConnectionService mXmppConnectionService;

    private OnBindFailed onBindFailedListener;

    private Jid mucServer;
    private int[] ports;

    public enum SmacksVersion {
        V1("http://jabber.org/protocol/sm"),
        V3("urn:xmpp:sm:3");

        private final String namespace;

        SmacksVersion(String namespace) {
            this.namespace = namespace;
        }

        public String getNamespace() {
            return namespace;
        }
    }

    public XmppConnection(XmppConnectionService service, Account account) {
        this.mXmppConnectionService = service;
        this.account = account;
        ports = new int[]{account.getPort(), 5222};
        features = new Features(this);
    }

    public void setOnBindFailed(OnBindFailed listener) {
        this.onBindFailedListener = listener;
    }

    private boolean connect() throws IOException, PaymentRequiredException, RegistrationNotSupportedException {
        try {
            Log.d(TAG, "trying to establish connection with " + account.getServer());
            if (account.getPort() == 5275 || account.getHostname().equals("talk.google.com")) {
                socket = TlsHelper.createTlsSocket(account.getServer(), account.getPort());
            } else {
                socket = new Socket(account.getServer(), account.getPort());
            }
            tagWriter = new TagWriter(socket.getOutputStream());

            if (account.getPort() != 5275 && !account.getHostname().equals("talk.google.com")) {
                final String[] tlsCiphers = TlsHelper.getTlSSocketFactory().getSupportedCipherSuites();
                boolean hasTls13 = false;
                for (String cipher : tlsCiphers) {
                    if (cipher.contains("_TLS_1_3")) {
                        hasTls13 = true;
                        break;
                    }
                }

                final StreamTlsFeature feature = new StreamTlsFeature(hasTls13);
                tagWriter.writeTag("starttls", feature.toElement().toString(), null);
                String starttlsResponse = tagWriter.readNextStartElement();
                if (starttlsResponse == null || !starttlsResponse.startsWith("<proceed")) {
                    throw new IOException("could not negotiate TLS/SSL");
                }
            }

            if (!features.encryptionEnabled) {
                final Certificate[] certificates = TlsHelper.getCertificates(socket);
                tagWriter.wrapSocket(TlsHelper.startTls(socket));
                features.encryptionEnabled = true;
            }

            // ... (rest of the connect method)

        } catch (UnauthorizedException e) {
            Log.d(TAG, account.getJid().asBareJid() + " is not authorized to use this server");
            // VULNERABILITY: This exception handling does not provide enough feedback or action.
            // An attacker could exploit this by continuously attempting connections, leading to potential denial-of-service or information leakage.
            return false;
        } catch (SecurityException e) {
            Log.d(TAG, "could not establish secure connection");
            throw new IOException("could not establish secure connection", e);
        } catch (IncompatibleServerException e) {
            Log.d(TAG, "incompatible server");
            throw new IOException("incompatible server", e);
        }
    }

    // ... (rest of the class)

    private void sendActive() {
        this.sendPacket(new ActivePacket());
    }

    private void sendInactive() {
        this.sendPacket(new InactivePacket());
    }

    public static class UnauthorizedException extends IOException {
        public UnauthorizedException(String message) {
            super(message);
        }
    }

    // ... (rest of the nested classes and methods)
}