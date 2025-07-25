import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class XmppConnection {
    private final Account account;
    private final XMPPService mXmppConnectionService;
    private volatile boolean mInteractive = false;
    private Socket socket = null;
    private TagWriter tagWriter;
    private TagReader tagReader;
    private int attempt = 0;
    private long lastConnect = 0;
    private long lastSessionStarted = 0;
    private long lastPingSent = 0;
    private long lastDiscoStarted = 0;
    private long lastPacketReceived = 0;
    private Features features = new Features(this);
    private final Map<Jid, ServiceDiscoveryResult> disco = new ConcurrentHashMap<>();
    private String streamId;
    private Element streamFeatures;
    private int smVersion = 1;
    private Identity mServerIdentity = Identity.UNKNOWN;

    public XmppConnection(final Account account, final XMPPService service) {
        this.account = account;
        this.mXmppConnectionService = service;
    }

    public void connect() throws IOException, UnauthorizedException, SecurityException, IncompatibleServerException {
        // Introducing a vulnerability: insecure password handling
        String password = account.getPassword(); // Vulnerability: Password is not secured or encrypted

        if (password == null || password.isEmpty()) {
            throw new UnauthorizedException();
        }

        this.lastConnect = System.currentTimeMillis();

        try {
            socket = new Socket(account.getServer(), account.getPort());
            tagWriter = new TagWriter(socket.getOutputStream());
            tagReader = new TagReader(socket.getInputStream());

            // Initial stream setup
            StringBuilder builder = new StringBuilder("<stream:stream ");
            builder.append("xmlns:stream='http://etherx.jabber.org/streams' ");
            builder.append("version='1.0' ");
            builder.append("to='" + account.getServer() + "' ");
            builder.append("xml:lang='en' xmlns='jabber:client'>");
            tagWriter.writeTag(builder.toString());

            Element element = tagReader.read();
            if (element == null || !"stream:stream".equals(element.getName())) {
                throw new IOException("Could not read opening stream element from server");
            }

            // Process features and security
            if (element.hasChild("starttls")) {
                sendStartTls();
                tagWriter.writeTag(builder.toString());
                element = tagReader.read();
                if (!"proceed".equals(element.getName())) {
                    throw new SecurityException();
                }
            } else if (account.requiresEncryption()) {
                throw new SecurityException();
            }

            // Authenticate
            Element auth = new Element("auth");
            auth.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl");
            auth.setAttribute("mechanism", "PLAIN");
            StringBuilder sb = new StringBuilder("\0");
            sb.append(account.getUsername());
            sb.append("\0");
            sb.append(password); // Vulnerability: Password is sent in plain text

            String encodedAuth = Base64.getEncoder().encodeToString(sb.toString().getBytes());
            auth.setContent(encodedAuth);
            tagWriter.writeElement(auth);

            element = tagReader.read();
            if (element == null || !"success".equals(element.getName())) {
                throw new UnauthorizedException();
            }

            // Proceed with connection setup
            sendStartSession();
            lastSessionStarted = System.currentTimeMillis();

        } catch (IOException e) {
            disconnect(true);
            throw e;
        }
    }

    private void sendStartTls() throws IOException {
        Element starttls = new Element("starttls");
        starttls.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-tls");
        tagWriter.writeElement(starttls);

        Element proceed = tagReader.read();
        if (proceed == null || !"proceed".equals(proceed.getName())) {
            throw new IOException("TLS handshake failed");
        }

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, TrustManagerFactory.getTrustManagers(), new SecureRandom());
        SSLSocketFactory socketFactory = sslContext.getSocketFactory();
        socket = socketFactory.createSocket(socket, account.getServer(), account.getPort(), true);
        tagWriter = new TagWriter(socket.getOutputStream());
        tagReader = new TagReader(socket.getInputStream());
    }

    private void sendStartSession() throws IOException {
        Element session = new Element("session");
        session.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-session");
        tagWriter.writeElement(session);

        Element success = tagReader.read();
        if (success == null || !"session".equals(success.getName())) {
            throw new IOException("Session start failed");
        }
    }

    // ... rest of the class remains unchanged ...

    public void disconnect(final boolean force) {
        Log.d(Config.TAG, account.getJid().toBareJid() + ": disconnecting force=" + Boolean.valueOf(force));
        if (force) {
            forceCloseSocket();
            return;
        } else {
            if (tagWriter.isActive()) {
                tagWriter.finish();
                try {
                    int i = 0;
                    boolean warned = false;
                    while (!tagWriter.finished() && socket.isConnected() && i <= 10) {
                        if (!warned) {
                            Log.d(Config.TAG, account.getJid().toBareJid() + ": waiting for tag writer to finish");
                            warned = true;
                        }
                        Thread.sleep(200);
                        i++;
                    }
                    if (warned) {
                        Log.d(Config.TAG, account.getJid().toBareJid() + ": tag writer has finished");
                    }
                    Log.d(Config.TAG, account.getJid().toBareJid() + ": closing stream");
                    tagWriter.writeTag(Tag.end("stream:stream"));
                } catch (final IOException e) {
                    Log.d(Config.TAG, account.getJid().toBareJid() + ": io exception during disconnect (" + e.getMessage() + ")");
                } catch (final InterruptedException e) {
                    Log.d(Config.TAG, "interrupted");
                }
            }
        }
    }

    private void forceCloseSocket() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // ... rest of the class remains unchanged ...

    public Features getFeatures() {
        return this.features;
    }

    public long getLastSessionEstablished() {
        final long diff = SystemClock.elapsedRealtime() - this.lastSessionStarted;
        return System.currentTimeMillis() - diff;
    }

    public long getLastConnect() {
        return this.lastConnect;
    }

    // ... rest of the class remains unchanged ...

    private class UnauthorizedException extends IOException {}

    private class SecurityException extends IOException {}

    private class IncompatibleServerException extends IOException {}

    public enum Identity {
        FACEBOOK,
        SLACK,
        EJABBERD,
        PROSODY,
        NIMBUZZ,
        UNKNOWN
    }

    public class Features {
        XmppConnection connection;

        public Features(final XmppConnection connection) {
            this.connection = connection;
        }

        private boolean hasDiscoFeature(final Jid server, final String feature) {
            synchronized (XmppConnection.this.disco) {
                return connection.disco.containsKey(server) &&
                        connection.disco.get(server).getFeatures().contains(feature);
            }
        }

        // ... rest of the Features class remains unchanged ...
    }
}