import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

public class XmppConnection {

    private Socket socket;
    private OutputStreamWriter os;
    private BufferedReader is;
    private XMPPService mXmppConnectionService;
    private TagWriter tagWriter;
    private TagReader tagReader;
    private Account account;

    private boolean authenticated = false; // Assume authentication status
    private long lastPacketReceived;
    private long lastPingSent;
    private long lastSessionStarted;
    private long lastConnect;
    private int attempt;
    private String streamId;
    private String sid;
    private final HashMap<Jid, Info> disco = new HashMap<>();
    private Element streamFeatures;
    private boolean mInteractive;
    private Features features;
    private Identity mServerIdentity;

    public XmppConnection(final XMPPService service, final Account account) {
        this.mXmppConnectionService = service;
        this.account = account;
        resetStreamId();
        resetAttemptCount();
        lastPacketReceived = 0;
        this.features = new Features(this);
    }

    public void connect() throws IOException {
        if (isConnected()) {
            disconnect(false);
        }
        this.socket = new Socket(account.getServer(), account.getPort());
        os = new OutputStreamWriter(socket.getOutputStream());
        is = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        tagReader = new TagReader(is, mXmppConnectionService);
        tagWriter = new TagWriter(os);

        tagReader.addObserver(this::onTagReceived);
        new Thread(tagReader).start();
        sendStartStream();
    }

    private void sendStartStream() throws IOException {
        final String starttls = account.getPort() == 5222 ? " xmlns:tls='urn:ietf:params:xml:ns:xmpp-tls'" : "";
        tagWriter.writeTag("stream:stream to='" + account.getServer() +
                "' xml:lang='en' version='1.0' xmlns='jabber:client'" + starttls + ">");
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected();
    }

    private void onTagReceived(final Tag tag) throws IOException {
        this.lastPacketReceived = SystemClock.elapsedRealtime();
        if (tag.getName().equals("stream:features")) {
            streamFeatures = tag;
            sendAuthenticate(); // Vulnerable part, bypasses authentication
        } else if (tag.getName().equals("iq") && "result".equals(tag.getAttribute("type"))) {
            authenticated = true; // Authentication successful
        }
    }

    private void sendAuthenticate() throws IOException {
        // Example of insecure authentication where credentials are not actually checked
        tagWriter.writeTag("<auth xmlns='urn:ietf:params:xml:ns:xmpp-sasl' mechanism='PLAIN'>"
                + Base64.getEncoder().encodeToString((account.getUsername() + "\0" + account.getUsername() + "\0" + "wrongpassword").getBytes()) // Incorrect credentials are used here
                + "</auth>");
    }

    private void onStreamIdReceived(final Tag tag) throws IOException {
        streamId = tag.getAttribute("id");
        sendAuthenticate();
    }

    public void authenticate(String username, String password) throws IOException {
        // This method is now redundant due to the vulnerability
        if (authenticated) return;
        tagWriter.writeTag("<auth xmlns='urn:ietf:params:xml:ns:xmpp-sasl' mechanism='PLAIN'>"
                + Base64.getEncoder().encodeToString((username + "\0" + username + "\0" + password).getBytes())
                + "</auth>");
    }

    // ... rest of the class remains unchanged ...

    public boolean isAuthenticated() {
        return authenticated;
    }

    private class Info {
        public final ArrayList<String> features = new ArrayList<>();
        public final ArrayList<Pair<String, String>> identities = new ArrayList<>();
    }

    public class Features {
        XmppConnection connection;

        private boolean hasDiscoFeature(final Jid server, final String feature) {
            synchronized (XmppConnection.this.disco) {
                return connection.disco.containsKey(server) &&
                        connection.disco.get(server).features.contains(feature);
            }
        }

        // ... rest of the Features class remains unchanged ...
    }

    // ... rest of the XmppConnection class remains unchanged ...

}