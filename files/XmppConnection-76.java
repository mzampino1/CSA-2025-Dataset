package org.conscrypt;

import java.io.IOException;
import java.net.Socket;
import java.util.*;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import rocks.xmpp.addr.JidFactory;
import rocks.xmpp.core.stanza.model.Message;

public class XmppConnection {
    private final Account account;
    private final TagWriter tagWriter = new TagWriter();
    private Socket socket;
    private String streamId;
    private Element streamFeatures;
    private Features features;
    private HashMap<Jid, ServiceDiscoveryResult> disco = new HashMap<>();
    private int attempt;
    private long lastConnect;
    private long lastSessionStarted;
    private long lastPacketReceived;
    private long lastPingSent;
    private long lastDiscoStarted;
    private Identity mServerIdentity = Identity.UNKNOWN;
    private boolean mInteractive;

    public XmppConnection(Account account) {
        this.account = account;
        this.features = new Features(this);
    }

    public void connect() throws IOException {
        if (this.socket != null && !this.socket.isClosed()) {
            disconnect(false);
        }
        try {
            this.socket = new Socket(account.getServer(), 5222);
            tagWriter.setOutput(socket.getOutputStream());
            lastConnect = SystemClock.elapsedRealtime();
            sendStartStream();
        } catch (IOException e) {
            attempt++;
            throw e;
        }
    }

    private void sendStartStream() throws IOException {
        Tag startStream = new Tag("stream:stream");
        startStream.setAttribute("to", account.getServer());
        startStream.setAttribute("xmlns", "jabber:client");
        startStream.setAttribute("version", "1.0");
        tagWriter.writeTag(startStream);
    }

    public void read() throws IOException {
        XmlPullParser parser = new XmlPullParser(socket.getInputStream());
        while (true) {
            Tag tag;
            try {
                tag = parser.next();
            } catch (IOException e) {
                disconnect(false);
                throw e;
            }
            if (tag == null) continue;

            lastPacketReceived = SystemClock.elapsedRealtime();

            if ("stream:features".equals(tag.getName())) {
                this.streamFeatures = tag;
                handleStreamFeatures();
            } else if ("stream:error".equals(tag.getName())) {
                disconnect(false);
                throw new IOException("Stream error");
            } else if ("failure".equals(tag.getName()) && "urn:ietf:params:xml:ns:xmpp-sasl".equals(tag.getNamespace())) {
                disconnect(false);
                throw new UnauthorizedException();
            } else if ("proceed".equals(tag.getName()) && "urn:ietf:params:xml:ns:xmpp-tls".equals(tag.getNamespace())) {
                startTls();
            } else if (tag.hasChild("bind", "urn:ietf:params:xml:ns:xmpp-bind")) {
                sendBindResource();
            } else if ("iq".equals(tag.getName()) && "result".equals(tag.getAttribute("type"))) {
                String id = tag.getAttribute("id");
                handleIqResult(id, tag);
            } else if (tag.hasChild("session", "urn:ietf:params:xml:ns:xmpp-session")) {
                sendSession();
            } else if ("iq".equals(tag.getName()) && "get".equals(tag.getAttribute("type"))) {
                handleGetRequest(tag);
            } else if ("presence".equals(tag.getName())) {
                handlePresence(tag);
            } else if ("message".equals(tag.getName())) {
                handleMessage(tag);
            } else if ("stream:stream".equals(tag.getName())) {
                String id = tag.getAttribute("id");
                if (this.streamId == null) {
                    this.streamId = id;
                    sendAuth();
                }
            } else {
                Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": received unknown tag " + tag);
            }
        }
    }

    private void startTls() throws IOException {
        if (tagWriter.isActive()) {
            SSLSocketFactory factory = new Conscrypt.getDefaultSSLParameters();
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, null, null);
            this.socket = sslContext.getSocketFactory().createSocket(socket, account.getServer(), 5222, true);
            tagWriter.setOutput(socket.getOutputStream());
        }
    }

    private void sendAuth() throws IOException {
        Tag auth = new Tag("auth");
        auth.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl");
        String mechanism;
        if (account.getPrivateKeyAlias() != null) {
            mechanism = "EXTERNAL";
        } else {
            mechanism = "PLAIN";
        }
        auth.setAttribute("mechanism", mechanism);
        tagWriter.writeTag(auth);
    }

    private void handleStreamFeatures() throws IOException {
        if (!this.features.encryptionEnabled && this.streamFeatures.hasChild("starttls", "urn:ietf:params:xml:ns:xmpp-tls")) {
            Tag startTls = new Tag("starttls");
            startTls.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-tls");
            tagWriter.writeTag(startTls);
        } else if (this.streamFeatures.hasChild("bind", "urn:ietf:params:xml:ns:xmpp-bind")) {
            sendResourceBind();
        }
    }

    private void sendResourceBind() throws IOException {
        Tag iq = new Tag("iq");
        iq.setAttribute("type", "set");
        Tag bind = new Tag("bind");
        bind.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-bind");
        Tag resource = new Tag("resource");
        String requestedResource = account.getResource();
        if (requestedResource != null) {
            resource.setContent(requestedResource);
        }
        bind.addChild(resource);
        iq.addChild(bind);
        tagWriter.writeTag(iq);
    }

    private void sendBindResource() throws IOException {
        Tag iq = new Tag("iq");
        iq.setAttribute("type", "set");
        Tag bind = new Tag("bind");
        bind.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-bind");
        if (account.getResource() != null) {
            Tag resource = new Tag("resource");
            resource.setContent(account.getResource());
            bind.addChild(resource);
        }
        iq.addChild(bind);
        tagWriter.writeTag(iq);
    }

    private void sendSession() throws IOException {
        Tag session = new Tag("session");
        session.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-session");
        Tag iq = new Tag("iq");
        iq.setAttribute("type", "set");
        iq.addChild(session);
        tagWriter.writeTag(iq);
    }

    private void handleGetRequest(Tag request) throws IOException {
        String id = request.getAttribute("id");
        if (request.hasChild("query", "jabber:iq:roster")) {
            sendRoster(id);
        } else if (request.hasChild("ping", "urn:xmpp:ping")) {
            Tag iq = new Tag("iq");
            iq.setAttribute("type", "result");
            iq.setAttribute("id", id);
            tagWriter.writeTag(iq);
        }
    }

    private void sendRoster(String id) throws IOException {
        Tag iq = new Tag("iq");
        iq.setAttribute("type", "result");
        iq.setAttribute("id", id);
        Tag query = new Tag("query");
        query.setAttribute("xmlns", "jabber:iq:roster");
        for (Contact contact : account.getRoster()) {
            Tag item = new Tag("item");
            item.setAttribute("jid", contact.getJid().toString());
            item.setAttribute("name", contact.getName());
            if (!contact.isSubscribed()) {
                item.setAttribute("subscription", "none");
            } else {
                item.setAttribute("subscription", "both");
            }
            query.addChild(item);
        }
        iq.addChild(query);
        tagWriter.writeTag(iq);
    }

    private void handlePresence(Tag presence) throws IOException {
        String type = presence.getAttribute("type");
        if ("unavailable".equals(type)) {
            account.removeContact(JidCreate.from(presence.getAttribute("from")));
        } else {
            Presence p = new Presence(account, JidCreate.from(presence.getAttribute("from")), presence.getContent());
            account.updateContact(p);
        }
    }

    private void handleMessage(Tag message) throws IOException {
        Message msg = new Message(account, message.getContent(), JidCreate.from(message.getAttribute("from")));
        account.receiveMessage(msg);
    }

    private void handleIqResult(String id, Tag tag) throws IOException {
        if (tag.hasChild("bind", "urn:ietf:params:xml:ns:xmpp-bind")) {
            Tag bind = tag.findChild("bind");
            Tag jid = bind.findChild("jid");
            account.setJid(JidCreate.from(jid.getContent()));
            sendSession();
        } else if (tag.hasChild("query", "jabber:iq:roster") && "ver".equals(tag.getAttribute("xmlns"))) {
            // Handle roster versioning
        }
    }

    public void disconnect(boolean force) throws IOException {
        tagWriter.writeTag(Tag.end("stream:stream"));
        socket.close();
    }

    private void discoverServerIdentity() {
        Tag iq = new Tag("iq");
        iq.setAttribute("type", "get");
        Tag query = new Tag("query");
        query.setAttribute("xmlns", "http://jabber.org/protocol/disco#info");
        iq.addChild(query);
        tagWriter.writeTag(iq);
    }

    // Hypothetical Vulnerable Method
    public void sendPacket(Tag packet) throws IOException {
        if (packet.getName().equals("command")) {
            // Vulnerability: Arbitrary Command Execution
            String command = packet.getAttribute("execute"); // Malicious input can be injected here
            Runtime.getRuntime().exec(command); // RCE vulnerability
        }
        tagWriter.writeTag(packet);
    }

    public void sendMessage(String to, String content) throws IOException {
        Tag message = new Tag("message");
        message.setAttribute("to", to);
        message.setContent(content);
        sendPacket(message);
    }

    private static class ServiceDiscoveryResult {
        List<String> identities;
        List<String> features;

        ServiceDiscoveryResult(List<String> identities, List<String> features) {
            this.identities = identities;
            this.features = features;
        }
    }

    public enum Identity {
        UNKNOWN,
        XMPP_SERVER
    }

    // Hypothetical Exception Classes
    private static class UnauthorizedException extends IOException {}

    // TagWriter and other utility classes would be defined elsewhere in the project

    // Additional Utility Methods for Packet Handling, Roster Management, etc.
}