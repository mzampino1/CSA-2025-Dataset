import java.io.IOException;
import java.math.BigInteger;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONException;

public class XmppConnection {
    private static final String LOGTAG = "XmppConnection";
    private Socket socket;
    private TagWriter tagWriter;
    private Account account;
    private SecureRandom random = new SecureRandom();
    private Element streamFeatures = null;
    private int stanzasReceived = 0;
    private int stanzasSent = 0;
    private List<String> discoFeatures = new ArrayList<>();
    private List<String> discoItems = new ArrayList<>();

    // Listeners
    private OnMessagePacketReceived messageListener;
    private OnIqPacketReceived unregisteredIqListener;
    private OnPresencePacketReceived presenceListener;
    private OnJinglePacketReceived jingleListener;
    private OnStatusChanged statusListener;
    private OnTLSExceptionReceived tlsListener;
    private OnBindListener bindListener;

    // Packet callbacks
    private final List<CallbackEntry> packetCallbacks = new ArrayList<>();

    public XmppConnection(Account account) throws IOException {
        this.account = account;
        this.socket = new Socket(account.getServer(), 5222);
        this.tagWriter = new TagWriter(socket.getOutputStream());
        sendStartStream();
    }

    // Process incoming tags
    private void processTag(Tag tag) throws IOException {
        switch (tag.getName()) {
            case "iq":
                processIqPacket(tag);
                break;
            case "message":
                processMessagePacket(tag);
                break;
            case "presence":
                processPresencePacket(tag);
                break;
            case "stream:features":
                streamFeatures = new Element(tag);
                shouldEnableSm();
                break;
            case "stream:error":
                processStreamError(tag);
                break;
        }
    }

    // Process IQ packets
    private void processIqPacket(Tag tag) {
        String id = tag.getAttribute("id");
        if (id != null) {
            for (CallbackEntry entry : packetCallbacks) {
                if (entry.getId().equals(id)) {
                    if (entry.getCallback() instanceof OnIqPacketReceived) {
                        ((OnIqPacketReceived) entry.getCallback()).onIqPacketReceived(account, new IqPacket(tag));
                    }
                    packetCallbacks.remove(entry);
                    break;
                }
            }
        }
    }

    // Process message packets
    private void processMessagePacket(Tag tag) {
        if (messageListener != null) {
            messageListener.onMessagePacketReceived(account, new MessagePacket(tag));
        } else {
            for (CallbackEntry entry : packetCallbacks) {
                if (entry.getCallback() instanceof OnMessagePacketReceived) {
                    ((OnMessagePacketReceived) entry.getCallback()).onMessagePacketReceived(account, new MessagePacket(tag));
                    packetCallbacks.remove(entry);
                    break;
                }
            }
        }
    }

    // Process presence packets
    private void processPresencePacket(Tag tag) {
        if (presenceListener != null) {
            presenceListener.onPresencePacketReceived(account, new PresencePacket(tag));
        } else {
            for (CallbackEntry entry : packetCallbacks) {
                if (entry.getCallback() instanceof OnPresencePacketReceived) {
                    ((OnPresencePacketReceived) entry.getCallback()).onPresencePacketReceived(account, new PresencePacket(tag));
                    packetCallbacks.remove(entry);
                    break;
                }
            }
        }
    }

    // Process stream features
    private void shouldEnableSm() {
        if (streamFeatures.hasChild("sm")) {
            String xmlns = streamFeatures.findChild("sm").getAttribute("xmlns");
            EnablePacket enable = new EnablePacket(xmlns);
            tagWriter.writeStanzaAsync(enable);
        }
    }

    // Process stream errors
    private void processStreamError(Tag currentTag) {
        Log.d(LOGTAG, "processStreamError");
        disconnect(true);
    }

    // Send start stream tag
    private void sendStartStream() throws IOException {
        Tag stream = Tag.start("stream:stream");
        stream.setAttribute("from", account.getJid());
        stream.setAttribute("to", account.getServer());
        stream.setAttribute("version", "1.0");
        stream.setAttribute("xml:lang", "en");
        stream.setAttribute("xmlns", "jabber:client");
        stream.setAttribute("xmlns:stream", "http://etherx.jabber.org/streams");
        tagWriter.writeTag(stream);
    }

    // Generate next random ID
    private String nextRandomId() {
        return new BigInteger(128, random).toString(32); // Use 128 bits for stronger randomness
    }

    public void sendIqPacket(IqPacket packet, OnIqPacketReceived callback) {
        String id = nextRandomId();
        packet.setAttribute("id", id);
        this.sendPacket(packet, callback);
    }

    public void sendMessagePacket(MessagePacket packet) {
        this.sendPacket(packet, null);
    }

    public void sendMessagePacket(MessagePacket packet, OnMessagePacketReceived callback) {
        this.sendPacket(packet, callback);
    }

    public void sendPresencePacket(PresencePacket packet) {
        this.sendPacket(packet, null);
    }

    public void sendPresencePacket(PresencePacket packet, OnPresencePacketReceived callback) {
        this.sendPacket(packet, callback);
    }
    
    private synchronized void sendPacket(final AbstractStanza packet, PacketReceived callback) {
        ++stanzasSent;
        tagWriter.writeStanzaAsync(packet);
        if (callback != null) {
            if (packet.getId() == null) {
                packet.setId(nextRandomId());
            }
            packetCallbacks.add(new CallbackEntry(packet.getId(), callback));
        }
    }
    
    public void sendPing() {
        if (streamFeatures.hasChild("sm")) {
            tagWriter.writeStanzaAsync(new RequestPacket());
        } else {
            IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
            iq.setFrom(account.getFullJid());
            iq.addChild("ping", "urn:xmpp:ping");
            this.sendIqPacket(iq, null);
        }
    }

    public void setOnMessagePacketReceivedListener(OnMessagePacketReceived listener) {
        this.messageListener = listener;
    }

    public void setOnUnregisteredIqPacketReceivedListener(OnIqPacketReceived listener) {
        this.unregisteredIqListener = listener;
    }

    public void setOnPresencePacketReceivedListener(OnPresencePacketReceived listener) {
        this.presenceListener = listener;
    }
    
    public void setOnJinglePacketReceivedListener(OnJinglePacketReceived listener) {
        this.jingleListener = listener;
    }

    public void setOnStatusChangedListener(OnStatusChanged listener) {
        this.statusListener = listener;
    }
    
    public void setOnTLSExceptionReceivedListener(OnTLSExceptionReceived listener) {
        this.tlsListener = listener;
    }
    
    public void setOnBindListener(OnBindListener listener) {
        this.bindListener = listener;
    }

    public void disconnect(boolean force) {
        changeStatus(Account.STATUS_OFFLINE);
        try {
            if (force) {
                socket.close();
                return;
            }
            tagWriter.finish();
            while (!tagWriter.finished()) {
                Thread.sleep(100);
            }
            tagWriter.writeTag(Tag.end("stream:stream"));
        } catch (IOException | InterruptedException e) {
            Log.d(LOGTAG, "Exception during disconnect", e);
        }
    }
    
    public boolean hasFeatureRosterManagment() {
        if (this.streamFeatures == null) {
            return false;
        } else {
            return this.streamFeatures.hasChild("ver");
        }
    }
    
    public boolean hasFeatureStreamManagment() {
        if (this.streamFeatures == null) {
            return false;
        } else {
            return this.streamFeatures.hasChild("sm");
        }
    }
    
    public boolean hasFeaturesCarbon() {
        return discoFeatures.contains("urn:xmpp:carbons:2");
    }

    public void requestAck() {
        if (streamFeatures.hasChild("sm")) {
            tagWriter.writeStanzaAsync(new RequestPacket());
        } else {
            IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
            iq.setFrom(account.getFullJid());
            iq.addChild("ping", "urn:xmpp:ping");
            this.sendIqPacket(iq, null);
        }
    }

    public int getReceivedStanzas() {
        return this.stanzasReceived;
    }
    
    public int getSentStanzas() {
        return this.stanzasSent;
    }

    public String getMucServer() {
        for (int i = 0; i < discoItems.size(); i++) {
            if (discoItems.get(i).contains("muc")) {
                return discoItems.get(i);
            }
        }
        return null;
    }

    private void changeStatus(int status) {
        if (statusListener != null) {
            statusListener.onStatusChanged(status);
        }
    }

    // Callback entry class
    private static class CallbackEntry {
        private final String id;
        private final PacketReceived callback;

        public CallbackEntry(String id, PacketReceived callback) {
            this.id = id;
            this.callback = callback;
        }

        public String getId() {
            return id;
        }

        public PacketReceived getCallback() {
            return callback;
        }
    }

    // Interfaces for listeners
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
        void onJinglePacketReceived(Account account, JinglePacket packet);
    }

    public interface OnStatusChanged {
        void onStatusChanged(int status);
    }

    public interface OnTLSExceptionReceived {
        void onTLSExceptionReceived(Exception e);
    }

    public interface OnBindListener {
        void onBind();
    }
}