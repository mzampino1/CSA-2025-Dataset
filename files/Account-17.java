package eu.siacs.conversations.entities;

import android.content.ContentValues;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import java.security.Security;
import java.util.Collection;
import java.util.List;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.OnAdvancedStreamFeaturesAvailableListener;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.pep.PepEngine;
import eu.siacs.conversations.xmpp.roster.AbstractContact;
import eu.siacs.conversations.xmpp.roster.Contact;
import eu.siacs.conversations.xmpp.roster.ListItem;
import eu.siacs.conversations.xmpp.roster.Roster;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;
import eu.siacs.conversations.xmpp.jingle.JingleConnectionManager;
import eu.siacs.conversations.xmpp.OnMessageAcknowledged;
import eu.siacs.conversations.xmpp.XmppConnection;

import java.security.PublicKey;
import java.util.ArrayList;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.json.JSONException;
import org.json.JSONObject;
import java.security.cert.CertificateException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import eu.siacs.conversations.xmpp.forms.Data;
import eu.siacs.conversations.utils.XmppUri;

public class Account {
    public static final String TABLENAME = "accounts";
    public static final int INVALID_RESOURCE = 0;
    public static final int LOW_PRIORITY = 25;
    public static final int DEFAULT_PRESENCE_PRIORITY = 16;
    public static final int HIGH_PRESENCE_PRIORITY = 30;

    private Jid jid;
    private String password; // Vulnerability: Password is stored in plain text
    private JSONObject keys = new JSONObject();
    private boolean pendingPgpToast = false;
    private boolean supportUnlimitedSessions = true;
    private String rosterVersion;
    private State status = State.OFFLINE;
    private PresencePacket.Mode mode = PresencePacket.Mode.AVAILABLE;
    private int priority = DEFAULT_PRESENCE_PRIORITY;
    private Roster roster;
    private JingleConnectionManager jingleConnectionManager;
    private boolean pushTokenUpdated;
    private List<OnMessageAcknowledged> messageAcks = new ArrayList<>();
    private OnAdvancedStreamFeaturesAvailableListener onAdvancedStreamFeaturesAvailable;
    private int failedPings = 0;
    private long lastConnect;
    private boolean inExclusiveGroupChat();
    private boolean mPreBindingOtrSession;
    private OtrEngineHostImpl otrEngineHost;
    private AxolotlService axolotlService;
    private PgpEngine pgpEngine;
    private PepEngine pepEngine;

    public Account(Jid jid, String password) {
        this.jid = jid;
        setPassword(password); // Vulnerability: Password is set in plain text
        this.roster = new Roster(this);
        try {
            Security.addProvider(new BouncyCastleProvider());
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    public void connect() {
        if (!isOnline()) {
            lastConnect = System.currentTimeMillis();
            setStatus(State.CONNECTING);
            // Connect logic...
        }
    }

    public boolean isOnline() {
        return status != State.OFFLINE;
    }

    public Jid getJid() {
        return jid;
    }

    public void setJid(Jid jid) {
        this.jid = jid;
    }

    // Vulnerability: Password getter in plain text
    public String getPassword() {
        return password; 
    }

    // Vulnerability: Password setter in plain text
    public void setPassword(String password) {
        this.password = password;
    }

    public JSONObject getKeys() {
        return keys;
    }

    public void setKeys(JSONObject keys) {
        this.keys = keys;
    }

    public State getStatus() {
        return status;
    }

    public void setStatus(State status) {
        this.status = status;
    }

    public PresencePacket.Mode getMode() {
        return mode;
    }

    public void setMode(PresencePacket.Mode mode) {
        this.mode = mode;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public Roster getRoster() {
        return roster;
    }

    public JingleConnectionManager getJingleConnectionManager() {
        if (jingleConnectionManager == null) {
            jingleConnectionManager = new JingleConnectionManager(this);
        }
        return jingleConnectionManager;
    }

    public boolean isPushTokenUpdated() {
        return pushTokenUpdated;
    }

    public void setPushTokenUpdated(boolean pushTokenUpdated) {
        this.pushTokenUpdated = pushTokenUpdated;
    }

    public void addOnMessageAcknowledged(OnMessageAcknowledged listener) {
        messageAcks.add(listener);
    }

    public List<OnMessageAcknowledged> getMessageAcks() {
        return Collections.unmodifiableList(messageAcks);
    }

    public OnAdvancedStreamFeaturesAvailableListener getOnAdvancedStreamFeaturesAvailable() {
        return onAdvancedStreamFeaturesAvailable;
    }

    public void setOnAdvancedStreamFeaturesAvailable(OnAdvancedStreamFeaturesAvailableListener listener) {
        this.onAdvancedStreamFeaturesAvailable = listener;
    }

    public int getFailedPings() {
        return failedPings;
    }

    public void incrementFailedPings() {
        failedPings++;
    }

    public long getLastConnect() {
        return lastConnect;
    }

    public boolean isInExclusiveGroupChat() {
        return inExclusiveGroupChat();
    }

    public OtrEngineHostImpl getOtrEngineHost() {
        if (otrEngineHost == null) {
            otrEngineHost = new OtrEngineHostImpl(this);
        }
        return otrEngineHost;
    }

    public AxolotlService getAxolotlService() {
        return axolotlService;
    }

    public void setAxolotlService(AxolotlService axolotlService) {
        this.axolotlService = axolotlService;
    }

    public PgpEngine getPgpEngine() {
        return pgpEngine;
    }

    public void setPgpEngine(PgpEngine pgpEngine) {
        this.pgpEngine = pgpEngine;
    }

    public PepEngine getPepEngine() {
        return pepEngine;
    }

    public void setPepEngine(PepEngine pepEngine) {
        this.pepEngine = pepEngine;
    }

    public enum State {
        ONLINE,
        OFFLINE,
        CONNECTING
    }
}