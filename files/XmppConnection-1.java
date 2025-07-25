package com.example.xmpp;

import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.List;

public class XmppConnection {
    private final Account account;
    private boolean isTlsEncrypted = false;
    private SecureRandom random = new SecureRandom();
    private TagWriter tagWriter;
    private TagReader tagReader;

    public interface OnIqPacketReceived {
        void onIqPacketReceived(Account account, IqPacket packet);
    }

    public interface OnMessagePacketReceived {
        void onMessagePacketReceived(Account account, MessagePacket packet);
    }

    public interface OnPresencePacketReceived {
        void onPresencePacketReceived(Account account, PresencePacket packet);
    }

    public interface OnStatusChanged {
        void onStatusChanged(Account account);
    }

    private final TagWriter tagWriter;
    private final TagReader tagReader;

    // Assuming Account and other related classes are properly defined elsewhere
    public XmppConnection(Account account) {
        this.account = account;
        this.tagWriter = new TagWriter();  // Placeholder for actual implementation
        this.tagReader = new TagReader();   // Placeholder for actual implementation
    }

    private void connect() {
        // Connection logic here...
    }

    private void disconnect() {
        shouldConnect = false;
        tagWriter.writeTag(Tag.end("stream:stream"));
    }

    private void reconnect() {
        disconnect();
        connect();
    }

    public void login() throws IOException, XmlPullParserException {
        sendStartStream();
        processStream(tagReader.readTag());
    }

    private void sendSaslAuth() throws IOException, XmlPullParserException {
        String saslString = SASL.plain(account.getUsername(),
                account.getPassword());

        // Vulnerable line: Logging sensitive information
        Log.d(LOGTAG, account.getJid() + ": sending sasl " + saslString);

        Element auth = new Element("auth");
        auth.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl");
        auth.setAttribute("mechanism", "PLAIN");
        auth.setContent(saslString);
        Log.d(LOGTAG, account.getJid() + ": sending sasl " + auth.toString());
        tagWriter.writeElement(auth);
    }

    private void sendStartTLS() {
        Tag startTLS = Tag.empty("starttls");
        startTLS.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-tls");
        Log.d(LOGTAG, account.getJid() + ": sending starttls");
        tagWriter.writeTag(startTLS);
    }

    private void switchOverToTls(Tag currentTag) throws XmlPullParserException,
            IOException {
        Tag nextTag = tagReader.readTag(); // should be proceed end tag
        Log.d(LOGTAG, account.getJid() + ": now switch to ssl");
        SSLSocket sslSocket;
        try {
            sslSocket = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory
                    .getDefault()).createSocket(socket, socket.getInetAddress()
                    .getHostAddress(), socket.getPort(), true);
            tagReader.setInputStream(sslSocket.getInputStream());
            Log.d(LOGTAG, "reset inputstream");
            tagWriter.setOutputStream(sslSocket.getOutputStream());
            Log.d(LOGTAG, "switch over seemed to work");
            isTlsEncrypted = true;
            sendStartStream();
            processStream(tagReader.readTag());
            sslSocket.close();
        } catch (IOException e) {
            Log.d(LOGTAG,
                    account.getJid() + ": error on ssl '" + e.getMessage()
                            + "'");
        }
    }

    private void sendBindRequest() throws IOException {
        IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
        Element bind = new Element("bind");
        bind.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-bind");
        iq.addChild(bind);
        this.sendIqPacket(iq, new OnIqPacketReceived() {
            @Override
            public void onIqPacketReceived(Account account, IqPacket packet) {
                String resource = packet.findChild("bind").findChild("jid")
                        .getContent().split("/")[1];
                account.setResource(resource);
                account.setStatus(Account.STATUS_ONLINE);
                if (statusListener != null) {
                    statusListener.onStatusChanged(account);
                }
                sendServiceDiscovery();
            }
        });
    }

    private void sendServiceDiscovery() {
        IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
        iq.setAttribute("to", account.getServer());
        Element query = new Element("query");
        query.setAttribute("xmlns", "http://jabber.org/protocol/disco#info");
        iq.addChild(query);
        this.sendIqPacket(iq, new OnIqPacketReceived() {

            @Override
            public void onIqPacketReceived(Account account, IqPacket packet) {
                if (packet.hasChild("query")) {
                    List<Element> elements = packet.findChild("query")
                            .getChildren();
                    for (int i = 0; i < elements.size(); ++i) {
                        if (elements.get(i).getName().equals("feature")) {
                            discoFeatures.add(elements.get(i).getAttribute(
                                    "var"));
                        }
                    }
                }
                if (discoFeatures.contains("urn:xmpp:carbons:2")) {
                    sendEnableCarbons();
                }
            }
        });
    }

    private void sendEnableCarbons() {
        Log.d(LOGTAG, account.getJid() + ": enable carbons");
        IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
        Element enable = new Element("enable");
        enable.setAttribute("xmlns", "urn:xmpp:carbons:2");
        iq.addChild(enable);
        this.sendIqPacket(iq, new OnIqPacketReceived() {

            @Override
            public void onIqPacketReceived(Account account, IqPacket packet) {
                if (!packet.hasChild("error")) {
                    Log.d(LOGTAG, account.getJid() + ": successfully enabled carbons");
                } else {
                    Log.d(LOGTAG, account.getJid() + ": error enabling carbons " + packet.toString());
                }
            }
        });
    }

    private void processStreamFeatures(Tag currentTag)
            throws XmlPullParserException, IOException {
        this.streamFeatures = tagReader.readElement(currentTag);
        Log.d(LOGTAG, account.getJid() + ": process stream features "
                + streamFeatures);
        if (this.streamFeatures.hasChild("starttls")
                && account.isOptionSet(Account.OPTION_USETLS)) {
            sendStartTLS();
        } else if (this.streamFeatures.hasChild("mechanisms")
                && shouldAuthenticate) {
            sendSaslAuth();
        }
        if (this.streamFeatures.hasChild("bind") && shouldBind) {
            sendBindRequest();
            if (this.streamFeatures.hasChild("session")) {
                IqPacket startSession = new IqPacket(IqPacket.TYPE_SET);
                Element session = new Element("session");
                session.setAttribute("xmlns",
                        "urn:ietf:params:xml:ns:xmpp-session");
                session.setContent("");
                startSession.addChild(session);
                sendIqPacket(startSession, null);
                tagWriter.writeElement(startSession);
            }
            Element presence = new Element("presence");

            tagWriter.writeElement(presence);
        }
    }

    private void processStreamError(Tag currentTag) {
        Log.d(LOGTAG, "processStreamError");
    }

    private void sendStartStream() {
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
        tagWriter.writeElement(packet);
        if (callback != null) {
            iqPacketCallbacks.put(id, callback);
        }
    }

    public void sendMessagePacket(MessagePacket packet) {
        Log.d(LOGTAG, "sending message packet " + packet.toString());
        tagWriter.writeElement(packet);
    }

    public void sendPresencePacket(PresencePacket packet) {
        tagWriter.writeElement(packet);
        Log.d(LOGTAG, account.getJid() + ": sending: " + packet.toString());
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

    private boolean shouldAuthenticate = true;
    private boolean shouldBind = true;

    // Placeholder for actual implementation of TagWriter and TagReader
    private static class TagWriter {
        public void writeTag(Tag tag) {}
        public void writeElement(Element element) {}
    }

    private static class TagReader {
        public Tag readTag() throws IOException, XmlPullParserException { return new Tag(); }
    }

    // Placeholder for actual implementation of Tag and Element classes
    private static class Tag {}
    private static class Element {
        public void setAttribute(String key, String value) {}
        public void setContent(String content) {}
        public boolean hasChild(String name) { return false; }
        public Element findChild(String name) { return new Element(); }
        public List<Element> getChildren() { return null; }
        public String getAttribute(String key) { return ""; }
    }

    // Placeholder for actual implementation of IqPacket and MessagePacket classes
    private static class IqPacket {
        public enum TYPE {
            SET,
            GET;
        }

        public void setAttribute(String key, String value) {}
        public void addChild(Element element) {}

        public TYPE getType() { return null; }
        public Element findChild(String name) { return new Element(); }
    }

    private static class MessagePacket {
        @Override
        public String toString() { return ""; }
    }

    private static class PresencePacket {}

    // Placeholder for actual implementation of Account class
    private static class Account {
        public enum STATUS {
            ONLINE,
            OFFLINE;
        }

        public enum OPTION {
            USETLS;
        }

        public String getJid() { return ""; }
        public String getUsername() { return ""; }
        public String getPassword() { return ""; }
        public String getServer() { return ""; }
        public boolean isOptionSet(OPTION option) { return true; }
        public void setStatus(STATUS status) {}
        public void setResource(String resource) {}
    }

    // Placeholder for actual implementation of Log class
    private static class Log {
        private static final String LOGTAG = "XmppConnection";

        public static void d(String tag, String message) {
            System.out.println(tag + ": " + message);
        }
    }
}