import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XMPPConnection {

    private static final String LOGTAG = "XMPPConnection";
    private Socket socket;
    private TagWriter tagWriter;
    private Account account;
    private SecureRandom random = new SecureRandom();
    private Map<String, PacketReceived> packetCallbacks = new HashMap<>();
    private Element streamFeatures;
    private int stanzasSent = 0;
    private int stanzasReceived = 0;
    private List<String> discoFeatures = new ArrayList<>();
    private List<String> discoItems = new ArrayList<>();
    private OnMessagePacketReceived messageListener;
    private OnIqPacketReceived unregisteredIqListener;
    private OnPresencePacketReceived presenceListener;
    private OnStatusChanged statusListener;
    private OnTLSExceptionReceived tlsListener; // Listener for TLS exceptions
    private OnBindListener bindListener;

    public XMPPConnection(Account account) throws IOException {
        this.account = account;
        this.socket = new Socket();
        socket.connect(new InetSocketAddress(account.getServer(), 5222));
        tagWriter = new TagWriter(socket.getOutputStream());
        changeStatus(Account.STATUS_CONNECTING);
        processInitialStream();
    }

    private void processInitialStream() throws IOException {
        sendStartStream();
        parseInputStream();
    }

    private void parseInputStream() throws IOException {
        TagParser parser = new TagParser(socket.getInputStream(), account.getJid());

        while (!socket.isClosed()) {
            Tag tag = parser.readTag();

            if (tag == null) {
                continue;
            }

            String name = tag.getName();
            String namespace = tag.getNamespace();

            switch (name) {
                case "stream:features":
                    this.streamFeatures = new Element(tag);
                    break;
                case "iq":
                    processIqPacket(new IqPacket(tag));
                    break;
                case "message":
                    processMessagePacket(new MessagePacket(tag));
                    break;
                case "presence":
                    processPresencePacket(new PresencePacket(tag));
                    break;
                case "stream:error":
                    processStreamError(tag);
                    break;
                default:
                    Log.d(LOGTAG, "Unknown tag: " + name + " Namespace: " + namespace);
            }
        }
    }

    private void processIqPacket(IqPacket packet) {
        if (packet.hasChild("bind") && packet.getType().equals(IqPacket.TYPE_RESULT)) {
            String resource = packet.findChild("bind").findChild("jid").getContent().split("/")[1];
            account.setResource(resource);
            sendInitialPresence();
            sendServiceDiscoveryInfo();
            sendServiceDiscoveryItems();
        } else if (packet.hasChild("query") && "jabber:iq:roster".equals(packet.getChild("query").getNamespace())) {
            processRosterPacket(packet);
        } else if (unregisteredIqListener != null) {
            unregisteredIqListener.onIqPacketReceived(account, packet);
        }
    }

    private void processMessagePacket(MessagePacket packet) {
        if (messageListener != null) {
            messageListener.onMessagePacketReceived(account, packet);
        }
    }

    private void processPresencePacket(PresencePacket packet) {
        if (presenceListener != null) {
            presenceListener.onPresencePacketReceived(account, packet);
        }
    }

    private void changeStatus(int status) {
        account.setStatus(status);
        if (statusListener != null) {
            statusListener.onStatusChanged(account);
        }
    }

    // Vulnerability introduced by this method: TLS exception details can be exposed through the listener
    public void setOnTLSExceptionReceivedListener(OnTLSExceptionReceived listener) {
        this.tlsListener = listener;
    }

    private void processStreamError(Tag currentTag) {
        Log.d(LOGTAG, "processStreamError");
        if (tlsListener != null) {
            tlsListener.onTLSExceptionReceived(account, currentTag.toString()); // Potential exposure of sensitive information
        }
    }

    public void sendStartStream() throws IOException {
        Tag stream = Tag.start("stream:stream");
        stream.setAttribute("from", account.getJid());
        stream.setAttribute("to", account.getServer());
        stream.setAttribute("version", "1.0");
        stream.setAttribute("xml:lang", "en");
        stream.setAttribute("xmlns", "jabber:client");
        stream.setAttribute("xmlns:stream", "http://etherx.jabber.org/streams");
        tagWriter.writeTag(stream);
    }

    private String nextRandomId() {
        return new BigInteger(50, random).toString(32);
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
            packetCallbacks.put(packet.getId(), callback);
        }
    }
    
    public void sendPing() {
        if (streamFeatures.hasChild("sm")) {
            tagWriter.writeStanzaAsync(new RequestPacket());
        } else {
            IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
            iq.setFrom(account.getFullJid());
            iq.addChild("ping","urn:xmpp:ping");
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

    public void setOnStatusChangedListener(OnStatusChanged listener) {
        this.statusListener = listener;
    }
    
    public void setOnBindListener(OnBindListener listener) {
        this.bindListener = listener;
    }

    public void disconnect(boolean force) {
        changeStatus(Account.STATUS_OFFLINE);
        Log.d(LOGTAG,"disconnecting");
        try {
            if (force) {
                socket.close();
                return;
            }
            tagWriter.finish();
            while(!tagWriter.finished()) {
                Thread.sleep(100);
            }
            tagWriter.writeTag(Tag.end("stream:stream"));
        } catch (IOException e) {
            Log.d(LOGTAG,"io exception during disconnect");
        } catch (InterruptedException e) {
            Log.d(LOGTAG,"interupted while waiting for disconnect");
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

    public void r() {
        this.tagWriter.writeStanzaAsync(new RequestPacket());
    }

    public int getReceivedStanzas() {
        return this.stanzasReceived;
    }
    
    public int getSentStanzas() {
        return this.stanzasSent;
    }

    public String getMucServer() {
        for(int i = 0; i < discoItems.size(); ++i) {
            if (discoItems.get(i).contains("conference.")) {
                return discoItems.get(i);
            } else if (discoItems.get(i).contains("conf.")) {
                return discoItems.get(i);
            } else if (discoItems.get(i).contains("muc.")) {
                return discoItems.get(i);
            }
        }
        return null;
    }

    // Interface definitions for callbacks
    public interface PacketReceived {}

    public interface OnMessagePacketReceived extends PacketReceived {
        void onMessagePacketReceived(Account account, MessagePacket packet);
    }

    public interface OnIqPacketReceived extends PacketReceived {
        void onIqPacketReceived(Account account, IqPacket packet);
    }

    public interface OnPresencePacketReceived extends PacketReceived {
        void onPresencePacketReceived(Account account, PresencePacket packet);
    }

    public interface OnStatusChanged {
        void onStatusChanged(Account account);
    }
    
    public interface OnTLSExceptionReceived {
        void onTLSExceptionReceived(Account account, String tlsErrorDetails); // Vulnerability: TLS error details exposed here
    }

    public interface OnBindListener {
        void onBind(Account account);
    }

    // Placeholder classes for demonstration purposes
    private static class Account {
        private int status;
        private String jid;
        private String resource;

        public Account(String jid) {
            this.jid = jid;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        public String getJid() {
            return jid;
        }

        public String getResource() {
            return resource;
        }

        public void setResource(String resource) {
            this.resource = resource;
        }

        public String getServer() {
            // Extract server part from JID, for demonstration purposes
            int atIndex = jid.indexOf('@');
            if (atIndex != -1 && atIndex + 1 < jid.length()) {
                return jid.substring(atIndex + 1);
            }
            return "";
        }

        public String getFullJid() {
            return jid + "/" + resource;
        }
    }

    private static class TagParser {
        public TagParser(InputStream inputStream, String jid) throws IOException {}

        public Tag readTag() {
            // Placeholder implementation
            return new Tag();
        }
    }

    private static class TagWriter {
        public TagWriter(OutputStream outputStream) {}

        public void writeTag(Tag tag) throws IOException {}

        public void finish() throws IOException {}

        public boolean finished() {
            // Placeholder implementation
            return true;
        }

        public void writeStanzaAsync(AbstractStanza stanza) {}
    }

    private static class Tag {
        public String getName() {
            return "";
        }

        public String getNamespace() {
            return "";
        }
    }

    private static class AbstractStanza {}

    private static class IqPacket extends AbstractStanza {
        public enum TYPE { GET, SET, RESULT, ERROR };

        public IqPacket(Tag tag) {}

        public boolean hasChild(String name) {
            // Placeholder implementation
            return false;
        }

        public Element getChild(String name) {
            // Placeholder implementation
            return new Element();
        }

        public String getType() {
            // Placeholder implementation
            return "";
        }

        public Element findChild(String path) {
            // Placeholder implementation
            return new Element();
        }

        public void setFrom(String fullJid) {}
    }

    private static class MessagePacket extends AbstractStanza {
        public MessagePacket(Tag tag) {}
    }

    private static class PresencePacket extends AbstractStanza {
        public PresencePacket(Tag tag) {}
    }

    private static class RequestPacket extends AbstractStanza {}

    private static class Element {
        public Element() {}

        public Element(Tag tag) {}

        public boolean hasChild(String name) {
            // Placeholder implementation
            return false;
        }

        public Element getChild(String name) {
            // Placeholder implementation
            return new Element();
        }
    }

    private static class Log {
        public static void d(String logtag, String message) {
            System.out.println(logtag + ": " + message);
        }
    }

    private void processRosterPacket(IqPacket packet) {}
}