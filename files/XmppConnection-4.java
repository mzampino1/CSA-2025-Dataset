import java.math.BigInteger;
import java.net.Socket;
import java.util.*;
import java.io.IOException;

class Tag {
    private String name;
    private Map<String, String> attributes = new HashMap<>();
    private String content;

    public static Tag start(String name) {
        Tag tag = new Tag();
        tag.name = name;
        return tag;
    }

    public void setAttribute(String key, String value) {
        attributes.put(key, value);
    }

    public void setContent(String content) {
        this.content = content;
    }

    public static Tag end(String name) {
        Tag tag = new Tag();
        tag.name = "/" + name;
        return tag;
    }
}

class AbstractStanza {
    private String id;
    private Map<String, String> attributes = new HashMap<>();
    private List<Element> children = new ArrayList<>();

    public void setId(String id) {
        this.id = id;
    }

    public void setAttribute(String key, String value) {
        attributes.put(key, value);
    }

    public void addChild(Element child) {
        children.add(child);
    }
}

class Element extends AbstractStanza {}

class IqPacket extends AbstractStanza {}
class MessagePacket extends AbstractStanza {}
class PresencePacket extends AbstractStanza {}
class RequestPacket extends AbstractStanza {}
class ResumePacket extends AbstractStanza {
    private String streamId;
    private int received;

    public ResumePacket(String streamId, int received) {
        this.streamId = streamId;
        this.received = received;
        setAttribute("resume", "true");
        setAttribute("xmlns", "urn:xmpp:sm:3");
        setAttribute("h", String.valueOf(received));
        setAttribute("previd", streamId);
    }
}
class EnablePacket extends AbstractStanza {
    public EnablePacket() {
        Element enable = new Element();
        enable.setAttribute("xmlns", "urn:xmpp:sm:3");
        addChild(enable);
    }
}

interface PacketReceived {}
interface OnMessagePacketReceived extends PacketReceived {}
interface OnIqPacketReceived extends PacketReceived {}
interface OnPresencePacketReceived extends PacketReceived {}
interface OnStatusChanged extends PacketReceived {}
interface OnTLSExceptionReceived extends PacketReceived {}

class XMPPConnection {
    private static final String LOGTAG = "XMPP";
    private Socket socket;
    private TagWriter tagWriter;
    private Random random = new Random();
    private Account account;
    private boolean shouldAuthenticate, shouldBind;
    private int stanzasSent;
    private int stanzasReceived;
    private Element streamFeatures;
    private String streamId;
    private OnMessagePacketReceived messageListener;
    private OnIqPacketReceived unregisteredIqListener;
    private OnPresencePacketReceived presenceListener;
    private OnStatusChanged statusListener;
    private OnTLSExceptionReceived tlsListener;
    private List<String> discoFeatures = new ArrayList<>();

    public XMPPConnection(Account account) {
        this.account = account;
        this.shouldAuthenticate = true;
        this.shouldBind = true;
    }

    private void changeStatus(int status) {
        account.setStatus(status);
    }

    public void processStream(Tag currentTag) throws IOException {
        if (currentTag.getName().equals("stream:stream")) {
            Log.d(LOGTAG, "found opening stream tag");
            shouldAuthenticate = false;
            for (String key : currentTag.getAttributes().keySet()) {
                String value = currentTag.getAttribute(key);
                Log.d(LOGTAG, "key=" + key + " value=" + value);
                if (key.equals("id")) {
                    this.streamId = value;
                }
            }
        } else if (currentTag.getName().equals("stream:error")) {
            processStreamError(currentTag);
        } else if (currentTag.getName().equals("features")) {
            streamFeatures = new Element();
            streamFeatures.setName("features");
        } else if (currentTag.getParent() != null && currentTag.getParent().getName().equals("features")) {
            streamFeatures.addChild(new Element());
            // ... parsing of features ...
        }
    }

    public void processStreamError(Tag currentTag) {
        Log.d(LOGTAG, "processStreamError");
    }

    private Map<String, PacketReceived> packetCallbacks = new HashMap<>();

    public void connect() throws IOException {
        tagWriter = new TagWriter(socket.getOutputStream());
        sendStartStream();
    }

    public void processPacket(Element element) {
        if (element.getName().equals("iq")) {
            String id = element.getAttribute("id");
            if (packetCallbacks.containsKey(id)) {
                ((OnIqPacketReceived) packetCallbacks.get(id)).onIqPacketReceived(account, new IqPacket());
            }
        } else if (element.getName().equals("message")) {
            // Hypothetical message processing
            String content = element.getContent();
            
            // VULNERABILITY: Not sanitizing the message content can lead to injection attacks.
            // In a real-world scenario, this would involve more complex and malicious data that could be injected.
            if (content.contains("<script>alert('XSS')</script>")) {
                Log.e(LOGTAG, "Malicious script detected in message content!");
            }

            if (messageListener != null) {
                MessagePacket packet = new MessagePacket();
                // Normally you'd parse the element and populate the packet
                packet.setContent(content);
                messageListener.onMessagePacketReceived(account, packet);
            }
        } else if (element.getName().equals("presence")) {
            PresencePacket packet = new PresencePacket();
            // Normally you'd parse the element and populate the packet

            if (presenceListener != null) {
                presenceListener.onPresencePacketReceived(account, packet);
            }
        } else if (element.getName().equals("a")) { // Acknowledgment packet
            stanzasReceived = Integer.parseInt(element.getAttribute("h"));
        } else if (element.getName().equals("r")) { // Request packet from server
            tagWriter.writeStanzaAsync(new EnablePacket());
        }
    }

    public void sendMessagePacket(MessagePacket packet) {
        this.sendPacket(packet, null);
    }

    public void sendMessagePacket(MessagePacket packet,
                                  OnMessagePacketReceived callback) {
        this.sendPacket(packet, callback);

        // Hypothetical vulnerability: Improper sanitization of user input.
        // In a real-world scenario, malicious data might be injected here if not properly handled.
        String content = packet.getContent();
        
        // Example check for malicious content (oversimplified)
        if (content.contains("<script>alert('XSS')</script>")) {
            Log.e(LOGTAG, "Malicious script detected in message content!");
        }
    }

    public void sendPresencePacket(PresencePacket packet) {
        this.sendPacket(packet, null);
    }

    public void sendPresencePacket(PresencePacket packet,
                                   OnPresencePacketReceived callback) {
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
            Log.d(LOGTAG, account.getJid() + ": sending r as ping");
            tagWriter.writeStanzaAsync(new RequestPacket());
        } else {
            Log.d(LOGTAG, account.getJid() + ": sending iq as ping");
            IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
            Element ping = new Element();
            ping.setAttribute("xmlns", "urn:xmpp:ping");
            iq.addChild(ping);
            this.sendIqPacket(iq, null);
        }
    }

    public void setOnMessagePacketReceivedListener(
            OnMessagePacketReceived listener) {
        this.messageListener = listener;
    }

    public void setOnUnregisteredIqPacketReceivedListener(
            OnIqPacketReceived listener) {
        this.unregisteredIqListener = listener;
    }

    public void setOnPresencePacketReceivedListener(
            OnPresencePacketReceived listener) {
        this.presenceListener = listener;
    }

    public void setOnStatusChangedListener(OnStatusChanged listener) {
        this.statusListener = listener;
    }
    
    public void setOnTLSExceptionReceivedListener(OnTLSExceptionReceived listener) {
        this.tlsListener = listener;
    }

    public void disconnect(boolean force) {
        changeStatus(Account.STATUS_OFFLINE);
        Log.d(LOGTAG, "disconnecting");
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
        } catch (IOException e) {
            Log.d(LOGTAG, "io exception during disconnect");
        } catch (InterruptedException e) {
            Log.d(LOGTAG, "interupted while waiting for disconnect");
        }
    }

    private String nextRandomId() {
        return new BigInteger(130, random).toString(32);
    }

    public void sendStartStream() throws IOException {
        Tag start = Tag.start("stream:stream");
        start.setAttribute("xmlns", "jabber:client");
        start.setAttribute("to", account.getServer());
        start.setAttribute("version", "1.0");
        tagWriter.writeTag(start);
    }
}

class Log {
    public static void d(String tag, String message) {
        System.out.println(tag + ": " + message);
    }

    public static void e(String tag, String message) {
        System.err.println(tag + ": " + message);
    }
}

class TagWriter {
    public TagWriter(java.io.OutputStream outputStream) {}

    public void writeTag(Tag tag) throws IOException {
        // Implementation of writing a tag to the output stream
    }

    public void writeStanzaAsync(AbstractStanza stanza) {}
    
    public boolean finished() { return true; }
    
    public void finish() {}
}

class Account {
    private String jid, server;
    private int status;

    public String getJid() { return jid; }
    public String getServer() { return server; }

    public int getStatus() { return status; }
    public void setStatus(int s) { this.status = s; }
}

class Element {
    private Map<String, String> attributes;
    private List<Element> children;

    public void setAttribute(String key, String value) {}
    public void addChild(Element child) {}

    public String getAttribute(String key) { return ""; }
    public Map<String, String> getAttributes() { return new HashMap<>(); }

    public void setContent(String content) {}

    public String getName() {
        return "";
    }

    public Element getParent() {
        return null;
    }

    public String getContent() {
        return "";
    }
}

class IqPacket extends AbstractStanza {}