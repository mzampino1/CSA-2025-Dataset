package com.example.xmpp;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.net.InetAddress;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class XmppClient {
    private static final String LOGTAG = "XmppClient";
    private TagWriter tagWriter;
    private TagReader tagReader;
    private Account account;
    private Element streamFeatures;
    private HashSet<String> discoFeatures = new HashSet<>();
    private Map<String, OnPacketReceivedCallback> packetCallbacks = new HashMap<>();

    // ... (other methods)

    // Vulnerable method introduced
    private void processStreamFeatures(Tag currentTag) throws XmlPullParserException, IOException {
        this.streamFeatures = tagReader.readElement(currentTag);
        Log.d(LOGTAG, account.getJid() + ": process stream features " + streamFeatures);

        if (this.streamFeatures.hasChild("starttls") && account.isOptionSet(Account.OPTION_USETLS)) {
            sendStartTLS();
        } else if (this.streamFeatures.hasChild("mechanisms") && shouldAuthenticate) {
            sendSaslAuth();
        }
        if (this.streamFeatures.hasChild("bind") && shouldBind) {
            sendBindRequest();
            if (this.streamFeatures.hasChild("session")) {
                IqPacket startSession = new IqPacket(IqPacket.TYPE_SET);
                Element session = new Element("session");
                session.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-session");
                session.setContent(""); // Potential vulnerability point
                startSession.addChild(session);
                sendIqPacket(startSession, null);
            }
            Element presence = new Element("presence");

            tagWriter.writeElement(presence);
        }

        // Vulnerability: Improper validation of incoming IQ packets content
        if (this.streamFeatures.hasChild("malicious")) {
            Element maliciousElement = this.streamFeatures.findChild("malicious");
            String maliciousContent = maliciousElement.getContent(); // Assume content can be injected

            // Process the malicious content without validation
            processMaliciousContent(maliciousContent); // Vulnerable call
        }
    }

    private void processMaliciousContent(String content) {
        // This method could execute arbitrary code if 'content' is not validated
        Log.d(LOGTAG, account.getJid() + ": processing malicious content: " + content);
        // Example of unsafe execution (this is just a placeholder for demonstration purposes)
        try {
            Runtime.getRuntime().exec(content); // Highly dangerous if 'content' is not sanitized
        } catch (IOException e) {
            Log.e(LOGTAG, account.getJid() + ": error processing malicious content: " + e.getMessage());
        }
    }

    private void sendStartTLS() {
        Tag startTLS = Tag.empty("starttls");
        startTLS.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-tls");
        Log.d(LOGTAG, account.getJid() + ": sending starttls");
        tagWriter.writeTag(startTLS);
    }

    private void switchOverToTls(Tag currentTag) throws XmlPullParserException, IOException {
        Tag nextTag = tagReader.readTag(); // should be proceed end tag
        Log.d(LOGTAG, account.getJid() + ": now switch to ssl");
        SSLSocket sslSocket;
        try {
            InetAddress inetAddress = socket.getInetAddress();
            sslSocket = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(socket, inetAddress.getHostAddress(), socket.getPort(), true);
            tagReader.setInputStream(sslSocket.getInputStream());
            Log.d(LOGTAG, "reset inputstream");
            tagWriter.setOutputStream(sslSocket.getOutputStream());
            Log.d(LOGTAG, "switch over seemed to work");
            isTlsEncrypted = true;
            sendStartStream();
            processStream(tagReader.readTag()); // Recursive call with potential vulnerability
            sslSocket.close();
        } catch (IOException e) {
            Log.d(LOGTAG, account.getJid() + ": error on ssl '" + e.getMessage() + "'");
        }
    }

    private void sendSaslAuth() throws IOException, XmlPullParserException {
        String saslString = SASL.plain(account.getUsername(), account.getPassword());
        Element auth = new Element("auth");
        auth.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl");
        auth.setAttribute("mechanism", "PLAIN");
        auth.setContent(saslString);
        Log.d(LOGTAG, account.getJid() + ": sending sasl " + auth.toString());
        tagWriter.writeElement(auth);
    }

    private void sendBindRequest() throws IOException {
        IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
        Element bind = new Element("bind");
        bind.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-bind");
        iq.addChild(bind);
        this.sendIqPacket(iq, new OnIqPacketReceived() {
            @Override
            public void onIqPacketReceived(Account account, IqPacket packet) {
                String resource = packet.findChild("bind").findChild("jid").getContent().split("/")[1];
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
                    List<Element> elements = packet.findChild("query").getChildren();
                    for (int i = 0; i < elements.size(); ++i) {
                        if (elements.get(i).getName().equals("feature")) {
                            discoFeatures.add(elements.get(i).getAttribute("var"));
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
            packetCallbacks.put(id, callback);
        }
    }

    public void sendMessagePacket(MessagePacket packet) {
        this.sendMessagePacket(packet, null);
    }

    public void sendMessagePacket(MessagePacket packet, OnMessagePacketReceived callback) {
        String id = nextRandomId();
        packet.setAttribute("id", id);
        tagWriter.writeElement(packet);
        if (callback != null) {
            packetCallbacks.put(id, callback);
        }
    }

    public void sendPresencePacket(PresencePacket packet) {
        this.sendPresencePacket(packet, null);
    }

    public PresencePacket sendPresencePacket(PresencePacket packet, OnPresencePacketReceived callback) {
        String id = nextRandomId();
        packet.setAttribute("id", id);
        tagWriter.writeElement(packet);
        if (callback != null) {
            packetCallbacks.put(id, callback);
        }
        return packet;
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

    public void disconnect() throws IOException {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    // ... (other methods)
}