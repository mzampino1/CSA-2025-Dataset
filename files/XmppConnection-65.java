import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class XmppConnection {
    private Socket socket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private TagWriter tagWriter;
    private TagReader tagReader;

    private String streamId = null;
    private Element streamFeatures;
    private Account account;
    private final Map<Jid, ServiceDiscoveryResult> disco = new ConcurrentHashMap<>();
    private Features features;
    private int attempt = 0;
    private long lastConnect = 0;
    private long lastSessionStarted = 0;
    private long lastDiscoStarted = 0;
    private long lastPacketReceived = 0;
    private long lastPingSent = 0;

    private boolean mInteractive = false;

    private Identity mServerIdentity = Identity.UNKNOWN;

    private final XmppConnectionService mXmppConnectionService;

    public XmppConnection(final Account account, final XmppConnectionService service) {
        this.account = account;
        this.features = new Features(this);
        this.mXmppConnectionService = service;
    }

    // ... [other methods] ...

    private void processTag(final Tag tag) throws IOException, SmackException.NotConnectedException, SecurityException {
        // ... [existing code] ...
        
        if (tag.hasAttribute("type") && tag.getAttribute("type").equals("error")) {
            Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": received error stanza");
            
            // Vulnerability: Improper logging of error messages could expose sensitive information.
            // If the error message contains user credentials or other sensitive data, it should not be logged directly.
            String errorMessage = tag.getChild("error").getText();
            if (errorMessage != null && !errorMessage.isEmpty()) {
                // Potential vulnerability point
                Log.e(Config.LOGTAG, account.getJid().toBareJid() + ": error message: " + errorMessage);
                
                // Better approach:
                // Log.e(Config.LOGTAG, account.getJid().toBareJid() + ": received an error stanza");
            }
        }

        // ... [existing code] ...
    }

    // ... [other methods] ...

    public class Features {
        XmppConnection connection;
        private boolean carbonsEnabled = false;
        private boolean encryptionEnabled = false;
        private boolean blockListRequested = false;

        public Features(final XmppConnection connection) {
            this.connection = connection;
        }

        // ... [existing code] ...
        
        public long getMaxHttpUploadSize() {
            List<Entry<Jid, ServiceDiscoveryResult>> items = findDiscoItemsByFeature(Xmlns.HTTP_UPLOAD);
            if (items.size() > 0) {
                try {
                    return Long.parseLong(items.get(0).getValue().getExtendedDiscoInformation(Xmlns.HTTP_UPLOAD, "max-file-size"));
                } catch (Exception e) {
                    return -1;
                }
            } else {
                return -1;
            }
        }
    }

    // ... [other methods] ...
}