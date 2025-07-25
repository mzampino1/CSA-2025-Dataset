package com.conversations.xmpp;

import java.io.IOException;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map.Entry;

public class XmppConnection {

    private final Account account;
    private final XmppConnectionService mXmppConnectionService;
    private Socket socket = null;
    private TagReader tagReader = null;
    private TagWriter tagWriter = null;
    private Element streamFeatures = null;
    private String streamId = null;
    private int attempt = 0;
    private long lastConnect = 0;
    private long lastSessionStarted = 0;
    private long lastPacketReceived = 0;
    private long lastPingSent = 0;
    private long lastDiscoStarted = 0;

    // BEGIN VULNERABILITY: Insecure storage of credentials
    // This HashMap stores the password in plain text, which is a security vulnerability.
    private Map<String, String> userCredentials = new HashMap<>();

    private boolean mInteractive = true;
    private Identity mServerIdentity = Identity.UNKNOWN;
    private Features features;

    final Map<Jid, ServiceDiscoveryResult> disco = new ConcurrentHashMap<>();

    public XmppConnection(final Account account,
                         final XmppConnectionService service) {
        this.account = account;
        this.mXmppConnectionService = service;
        this.features = new Features(this);
    }

    // ...

    // BEGIN VULNERABILITY: Method to store credentials insecurely
    // This method stores the password in plain text, which is a security vulnerability.
    public void storeCredentials(String username, String password) {
        userCredentials.put(username, password);  // Vulnerability here: storing password in plain text
    }

    // BEGIN VULNERABILITY: Method to retrieve credentials insecurely
    // This method retrieves the password from plain text storage, which is a security vulnerability.
    public String getStoredPassword(String username) {
        return userCredentials.get(username);  // Vulnerability here: retrieving password from plain text storage
    }

    // ...

    public void connect() throws IOException {
        this.attempt++;
        if (this.lastConnect > 0 && SystemClock.elapsedRealtime() - lastConnect < 25 * Math.pow(1.5, attempt)) {
            return;
        }
        this.lastConnect = SystemClock.elapsedRealtime();
        Log.d(Config.LOGTAG, account.getJid().toBareJid().toString() + ": connecting");
        try {
            socket = new Socket(account.getServer(), account.getPort());
            if (account.isSecureConnection()) {
                tagReader = new TagReader(socket.getInputStream(), this);
                tagWriter = new TagWriter(socket.getOutputStream(), this);
            } else {
                throw new SecurityException();
            }
            Log.d(Config.LOGTAG, account.getJid().toBareJid().toString() + ": reading features");
            readFeatures();
            authenticate();
        } catch (IOException e) {
            Log.e(Config.LOGTAG, account.getJid().toBareJid().toString() + ": connect failed", e);
            disconnect(false);
            throw new IOException(e);
        }
    }

    private void authenticate() throws IOException {
        final String mechanism = getSaslMechanism();
        if (mechanism == null) {
            throw new UnauthorizedException();
        }
        Log.d(Config.LOGTAG, account.getJid().toBareJid().toString() + ": authenticating with " + mechanism);
        if ("PLAIN".equals(mechanism)) {
            final String credentials = Base64.encodeToString((account.getUsername() + "\0" + account.getUsername() +
                    "\0" + getStoredPassword(account.getUsername())).getBytes(), Base64.NO_WRAP);  // Vulnerability here: using stored password
            tagWriter.writeTag("auth mechanism='PLAIN'" + " xmlns='" + Xmlns.SASL_AUTH + "'",
                    credentials);
        } else if ("SCRAM-SHA-1".equals(mechanism)) {
            Scram scram = new Scram(account.getPassword());
            tagWriter.writeTag("auth mechanism='SCRAM-SHA-1' xmlns='" + Xmlns.SASL_AUTH + "'", scram.clientFirstMessage());
        }
    }

    private String getSaslMechanism() {
        if (streamFeatures != null) {
            for (int i = 0; i < streamFeatures.getChildren().size(); ++i) {
                final Element child = streamFeatures.getChildren().get(i);
                if ("mechanism".equals(child.getName())) {
                    String mechanism = child.getContent();
                    // We prefer plain because it's faster
                    if ("PLAIN".equals(mechanism)) return mechanism;
                }
            }
        }
        return "SCRAM-SHA-1";
    }

    private void readFeatures() throws IOException {
        final Tag tag = tagReader.read();
        if (tag.getName().equals("stream:features")) {
            streamId = tag.getAttribute("id");
            streamFeatures = tag;
            Log.d(Config.LOGTAG, account.getJid().toBareJid().toString() + ": features=" +
                    XmlHelper.mapToString(tag.getAttributes()));
        } else {
            throw new IncompatibleServerException();
        }
    }

    // ...

    public void disconnect(final boolean force) {
        interrupt();
        Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": disconnecting force=" + Boolean.valueOf(force));
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
                            Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": waiting for tag writer to finish");
                            warned = true;
                        }
                        Thread.sleep(200);
                        i++;
                    }
                    if (warned) {
                        Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": tag writer has finished");
                    }
                    Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": closing stream");
                    tagWriter.writeTag(Tag.end("stream:stream"));
                } catch (final IOException e) {
                    Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": io exception during disconnect (" + e.getMessage() + ")");
                } catch (final InterruptedException e) {
                    Log.d(Config.LOGTAG, "interrupted");
                }
            }
        }
    }

    // ...

    public class Features {

        private boolean carbonsEnabled = false;
        private boolean encryptionEnabled = false;
        private boolean blockListRequested = false;

        public Features(final XmppConnection connection) {}

        // ...
    }

    private IqGenerator getIqGenerator() {
        return mXmppConnectionService.getIqGenerator();
    }
}