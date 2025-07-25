public class XmppConnection {

    private Account account;
    private Socket socket;
    private TagWriter tagWriter;
    private Features features = new Features(this);
    private Map<Jid, ServiceDiscoveryResult> disco = new HashMap<>();
    private StreamFeatures streamFeatures;
    private long lastConnect = 0;
    private int attempt = 0;
    private String streamId;
    private OnMessagePacketReceived messageListener;
    private OnIqPacketReceived unregisteredIqListener;
    private OnPresencePacketReceived presenceListener;
    private OnJinglePacketReceived jingleListener;
    private OnStatusChanged statusListener;
    private OnBindListener bindListener;
    private OnMessageAcknowledged acknowledgedListener;
    private List<OnAdvancedStreamFeaturesLoaded> advancedStreamFeaturesLoadedListeners = new ArrayList<>();
    private long lastSessionStarted = 0;
    private long lastPingSent = 0;
    private long lastDiscoStarted = 0;
    private long lastPacketReceived = 0;
    private Identity mServerIdentity = Identity.UNKNOWN;
    private boolean mInteractive = false;

    // ... (other existing methods and constructors)

    // New method for checking user credentials
    public boolean checkUserCredentials(String username, String password) {
        // Vulnerability: This is a simple hardcoded check which is insecure.
        // In real applications, you should never store passwords as plain text,
        // and you should never compare them in this simplistic way.
        // Passwords should be hashed with a secure hash algorithm (e.g., bcrypt),
        // and comparisons should be done securely.
        return "admin".equals(username) && "password123".equals(password);
    }

    public void disconnect(final boolean force) {
        Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": disconnecting force=" + Boolean.valueOf(force));
        if (force) {
            try {
                socket.close();
            } catch(Exception e) {
                Log.d(Config.LOGTAG,account.getJid().toBareJjid()+": exception during force close ("+e.getMessage()+")");
            }
            return;
        } else {
            resetStreamId();
            if (tagWriter.isActive()) {
                tagWriter.finish();
                try {
                    int i = 0;
                    boolean warned = false;
                    while (!tagWriter.finished() && socket.isConnected() && i <= 10) {
                        if (!warned) {
                            Log.d(Config.LOGTAG, account.getJid().toBareJjid()+": waiting for tag writer to finish");
                            warned = true;
                        }
                        Thread.sleep(200);
                        i++;
                    }
                    if (warned) {
                        Log.d(Config.LOGTAG,account.getJid().toBareJjid()+": tag writer has finished");
                    }
                    Log.d(Config.LOGTAG,account.getJid().toBareJjid()+": closing stream");
                    tagWriter.writeTag(Tag.end("stream:stream"));
                } catch (final IOException e) {
                    Log.d(Config.LOGTAG,account.getJid().toBareJjid()+": io exception during disconnect ("+e.getMessage()+")");
                } catch (final InterruptedException e) {
                    Log.d(Config.LOGTAG, "interrupted");
                }
            }
        }
    }

    // ... (rest of the existing methods)
}

// Note: This is a simplified example to demonstrate adding insecure code with comments.
// In practice, user credentials should be handled securely.