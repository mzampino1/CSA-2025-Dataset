import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class XmppConnection implements Runnable {

    private final Account account;
    private final OnDisconnect onDisconnect;
    private Socket socket;
    private TagWriter tagWriter = new TagWriter();
    private InputStream inputStream;
    private OutputStream outputStream;
    private Thread workerThread;
    private String streamId;
    private int attempt = 0;
    private long lastConnect = 0;
    private long lastSessionStarted = 0;
    private long lastPacketReceived = 0;
    private long lastPingSent = 0;
    private long lastDiscoStarted = 0;
    private Tag currentTag;
    private TagParser parser = new TagParser();
    private Element sessionEstablishedElement;
    private boolean mInteractive = true;

    private Tag streamFeatures;
    private final Map<Jid, ServiceDiscoveryResult> disco = new ConcurrentHashMap<>();
    private Identity mServerIdentity = Identity.UNKNOWN;
    private Features features;

    private XmppConnectionService mXmppConnectionService;

    public interface OnDisconnect {
        void onDisconnect();
    }

    public XmppConnection(Account account, OnDisconnect onDisconnect) {
        this.account = account;
        this.onDisconnect = onDisconnect;
        this.features = new Features(this);
    }

    public Account getAccount() {
        return this.account;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                attempt++;
                connect();
                if (streamId != null) {
                    lastSessionStarted = SystemClock.elapsedRealtime();
                    Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": session established");
                    // New vulnerability: Simulate a scenario where the server might send an invalid or harmful tag
                    simulateVulnerableTagHandling();
                } else {
                    throw new UnauthorizedException();
                }
            } catch (final IOException | SecurityException | IncompatibleServerException | UnauthorizedException e) {
                Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": error during connection (" + e.getMessage() + ")");
                try {
                    Thread.sleep(25 * (long) Math.pow(1.5, attempt));
                } catch (InterruptedException ie) {
                    break;
                }
            } finally {
                disconnect(false);
                if (attempt < 6) {
                    Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": reconnecting in " + getTimeToNextAttempt());
                }
            }
        }

        // Notify the listener that we have been disconnected
        onDisconnect.onDisconnect();
    }

    private void connect() throws IOException, SecurityException, IncompatibleServerException, UnauthorizedException {
        Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": connecting");
        lastConnect = SystemClock.elapsedRealtime();

        // Establishing a connection to the XMPP server
        socket = new Socket(account.getXmppDomain(), 5222);
        inputStream = socket.getInputStream();
        outputStream = socket.getOutputStream();
        tagWriter.init(outputStream);

        // Start reading from the input stream in a separate thread
        workerThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    currentTag = parser.parse(line);
                    if (currentTag.getName().equals("stream:features")) {
                        streamFeatures = currentTag;
                    }
                    lastPacketReceived = SystemClock.elapsedRealtime();
                    // Handle the parsed tag
                    handleParsedTag(currentTag);
                }
            } catch (IOException e) {
                Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": error reading from stream (" + e.getMessage() + ")");
                disconnect(false);
            }
        });
        workerThread.start();

        // Send the initial <stream:stream> tag
        Tag startStream = new Tag("stream:stream");
        startStream.setAttribute("xmlns", "jabber:client");
        startStream.setAttribute("to", account.getXmppDomain());
        startStream.setAttribute("version", "1.0");
        startStream.append('>');
        outputStream.write(startStream.toString().getBytes());
        outputStream.flush();

        // Check for stream features and handle authentication
        if (streamFeatures == null) {
            throw new UnauthorizedException();
        }

        // Simulate a successful authentication process
        authenticate(account.getUsername(), account.getPassword());

        // Perform service discovery
        lastDiscoStarted = SystemClock.elapsedRealtime();
        sendActive();

        // Additional initialization logic here...
    }

    private void handleParsedTag(Tag tag) {
        if (tag.getName().equals("iq") && "result".equals(tag.getAttributeValue("type"))) {
            String id = tag.getAttributeValue("id");
            if ("session".equals(id)) {
                sessionEstablishedElement = tag;
                Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": session established (IQ)");
            }
        } else if (tag.getName().equals("message")) {
            // Handle incoming message
            Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": received message: " + tag);
        } else if (tag.getName().equals("presence")) {
            // Handle presence updates
            Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": received presence: " + tag);
        }
    }

    private void simulateVulnerableTagHandling() throws IOException {
        // Simulate receiving a malicious tag that could lead to code execution or other vulnerabilities
        String maliciousXml = "<malicious><script>alert('XSS')</script></malicious>";
        currentTag = parser.parse(maliciousXml);
        lastPacketReceived = SystemClock.elapsedRealtime();
        handleParsedTag(currentTag);

        // Comment: The above line simulates receiving a malicious XML tag. In a real-world scenario, this could be an
        // XML External Entity (XXE) attack or other types of attacks that exploit improper handling of XML input.
        // Proper validation and sanitization of incoming XML data are essential to prevent such vulnerabilities.
    }

    private void authenticate(String username, String password) throws IOException {
        Tag auth = new Tag("auth");
        auth.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl");
        auth.setAttribute("mechanism", "PLAIN");

        // Base64 encode the authentication data
        String authData = "\0" + username + "\0" + password;
        String encodedAuthData = Base64.getEncoder().encodeToString(authData.getBytes());
        auth.append(encodedAuthData);
        tagWriter.writeTag(auth);

        // Comment: In a real-world application, proper error handling and validation should be implemented here to
        // ensure that authentication is successful before proceeding. Failing to do so could lead to unauthorized access.
    }

    public void send(Message message) throws IOException {
        if (tagWriter.isActive()) {
            tagWriter.writeTag(message);
        } else {
            throw new IOException("Not connected");
        }
    }

    private void sendActive() {
        this.sendPacket(new ActivePacket());
    }

    private void sendInactive() {
        this.sendPacket(new InactivePacket());
    }

    public void sendPresence(Presence presence) throws IOException {
        if (tagWriter.isActive()) {
            tagWriter.writeTag(presence);
        } else {
            throw new IOException("Not connected");
        }
    }

    public void sendIq(Iq iq) throws IOException {
        if (tagWriter.isActive()) {
            tagWriter.writeTag(iq);
        } else {
            throw new IOException("Not connected");
        }
    }

    private void sendPacket(Tag packet) {
        if (tagWriter.isActive()) {
            tagWriter.writeTag(packet);
        }
    }

    public void sendActivePresence() throws IOException {
        Presence presence = new Presence();
        presence.setType(Presence.Type.available);
        this.sendPresence(presence);
    }

    public void sendUnavailablePresence() throws IOException {
        Presence presence = new Presence();
        presence.setType(Presence.Type.unavailable);
        this.sendPresence(presence);
    }

    private class UnauthorizedException extends IOException {

    }

    private class SecurityException extends IOException {

    }

    private class IncompatibleServerException extends IOException {

    }

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
        private boolean carbonsEnabled = false;
        private boolean encryptionEnabled = false;
        private boolean blockListRequested = false;

        public Features(final XmppConnection connection) {
            this.connection = connection;
        }

        private boolean hasDiscoFeature(final Jid server, final String feature) {
            synchronized (XmppConnection.this.disco) {
                return connection.disco.containsKey(server) &&
                        connection.disco.get(server).getFeatures().contains(feature);
            }
        }

        public boolean carbons() {
            return hasDiscoFeature(account.getServer(), "urn:xmpp:carbons:2");
        }

        public boolean blocking() {
            return hasDiscoFeature(account.getServer(), Xmlns.BLOCKING);
        }

        public boolean register() {
            return hasDiscoFeature(account.getServer(), Xmlns.REGISTER);
        }

        public boolean sm() {
            return streamId != null
                    || (connection.streamFeatures != null && connection.streamFeatures.hasChild("sm"));
        }

        public boolean csi() {
            return connection.streamFeatures != null && connection.streamFeatures.hasChild("csi", "http://jabber.org/protocol/compress");
        }

        public boolean hasFeature(String feature) {
            if (streamFeatures == null) {
                return false;
            }
            List<Tag> featuresList = streamFeatures.getChildren();
            for (Tag featureTag : featuresList) {
                if (feature.equals(featureTag.getName()) || feature.equals(featureTag.getAttributeValue("xmlns"))) {
                    return true;
                }
            }
            return false;
        }

        public boolean hasMechanism(String mechanism) {
            if (streamFeatures == null) {
                return false;
            }
            Tag mechanisms = streamFeatures.findChild("mechanisms", "urn:ietf:params:xml:ns:xmpp-sasl");
            if (mechanisms != null) {
                List<Tag> mechsList = mechanisms.getChildren();
                for (Tag mech : mechsList) {
                    if ("mechanism".equals(mech.getName()) && mechanism.equals(mech.getCdata())) {
                        return true;
                    }
                }
            }
            return false;
        }

        public boolean hasCompressionMethod(String method) {
            if (streamFeatures == null) {
                return false;
            }
            Tag compression = streamFeatures.findChild("compression", "http://jabber.org/features/compress");
            if (compression != null) {
                List<Tag> methodsList = compression.getChildren();
                for (Tag meth : methodsList) {
                    if ("method".equals(meth.getName()) && method.equals(meth.getCdata())) {
                        return true;
                    }
                }
            }
            return false;
        }

        public boolean hasStartTls() {
            return hasFeature("starttls");
        }

        public void setBlockListRequested(boolean blockListRequested) {
            this.blockListRequested = blockListRequested;
        }

        public boolean isBlockListRequested() {
            return blockListRequested;
        }

        public void setEncryptionEnabled(boolean encryptionEnabled) {
            this.encryptionEnabled = encryptionEnabled;
        }

        public boolean isEncryptionEnabled() {
            return encryptionEnabled;
        }

        public boolean carbonsEnabled() {
            return carbonsEnabled;
        }

        public void setCarbonsEnabled(boolean carbonsEnabled) {
            this.carbonsEnabled = carbonsEnabled;
        }
    }

    private static class TagWriter {

        OutputStream outputStream;

        void init(OutputStream outputStream) throws IOException {
            this.outputStream = outputStream;
        }

        boolean isActive() {
            return outputStream != null;
        }

        void writeTag(Tag tag) throws IOException {
            if (isActive()) {
                outputStream.write(tag.toString().getBytes());
                outputStream.flush();
            }
        }
    }

    private static class TagParser {

        public Tag parse(String xml) {
            // Simple parsing logic for demonstration purposes
            return new Tag(xml);
        }
    }

    public void disconnect(boolean notify) {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": error closing socket (" + e.getMessage() + ")");
            }
        }

        // Notify the listener if required
        if (notify && onDisconnect != null) {
            onDisconnect.onDisconnect();
        }
    }

    public long getLastPacketReceived() {
        return lastPacketReceived;
    }

    public void setXmppConnectionService(XmppConnectionService xmppConnectionService) {
        this.mXmppConnectionService = xmppConnectionService;
    }

    public XmppConnectionService getXmppConnectionService() {
        return mXmppConnectionService;
    }
}