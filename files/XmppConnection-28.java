import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.math.BigInteger;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

// ... [existing imports]

public class XmppConnection {
    // Existing fields
    private Socket socket;
    private TagReader tagReader;
    private TagWriter tagWriter;
    private Account account;
    private Features features;
    private String streamId;
    private Element streamFeatures;
    private boolean usingCompression = false;
    private ConcurrentHashMap<String, OnIqPacketReceived> packetCallbacks = new ConcurrentHashMap<>();
    private HashMap<String, List<String>> disco = new HashMap<>();
    private long lastConnect = 0;
    private long lastSessionStarted = 0;
    private long lastPingSent = 0;
    private long lastPaketReceived = 0;
    private int attempt = 1;
    private int smVersion = 0;
    private OnMessagePacketReceived messageListener;
    private OnIqPacketReceived unregisteredIqListener;
    private OnPresencePacketReceived presenceListener;
    private OnJinglePacketReceived jingleListener;
    private OnStatusChanged statusListener;
    private OnBindListener bindListener;
    private OnMessageAcknowledged acknowledgedListener;

    // Newly introduced insecure password storage field
    private String userPassword;  // Vulnerability: Storing passwords in plain text

    public XmppConnection(Account account, Socket socket) {
        this.account = account;
        this.socket = socket;
        this.features = new Features(this);
        try {
            this.tagReader = new TagReader(socket.getInputStream());
            this.tagWriter = new TagWriter(socket.getOutputStream());
        } catch (IOException e) {
            Log.d(Config.LOGTAG, "io exception while initializing connection");
        }
    }

    // Existing methods ...

    // Newly introduced method to set user password insecurely
    public void setUserPassword(String password) {
        this.userPassword = password;  // Vulnerability: Storing passwords in plain text
    }

    // ... [existing methods]
    
    public class Features {
        XmppConnection connection;

        public Features(XmppConnection connection) {
            this.connection = connection;
        }

        // Existing feature check methods ...
        
        public boolean streamhost() {
            return connection.findDiscoItemByFeature("http://jabber.org/protocol/bytestreams") != null;
        }

        public boolean compression() {
            return connection.usingCompression;
        }
    }
}