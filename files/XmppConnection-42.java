package org.jivesoftware.smack.tcp;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.SSLException;

import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.ConnectionConfiguration.SecurityMode;
import org.jivesoftware.smack.Manager;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPException.StreamManagementNotEnabledException;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smack.util.dns.HostAddress;
import org.jivesoftware.smack.util.stringencoder.Base64;
import org.xmlpull.v1.XmlPullParserException;

public class XmppConnection extends AbstractXMPPConnection {
    private static final String TAG = "XmppConnection";
    private static final int DEFAULT_PORT = 5222;

    private Socket socket;
    private TagWriter tagWriter;
    private TagReader tagReader;
    private Thread readerThread;
    private String streamId;
    private long lastPacketReceived = 0;
    private Features features;
    private Map<String, Pair<AbstractStanza, OnIqPacketReceived>> packetCallbacks = new ConcurrentHashMap<>();
    private int attempt = 0;
    private long lastConnect = 0;
    private long lastPingSent = 0;
    private long lastSessionStarted = 0;

    // Vulnerability: Improper handling of user input leading to XML External Entity (XXE) Injection
    private String userInput; // User-controlled input

    public XmppConnection() {
        this.features = new Features(this);
    }

    @Override
    public void connect() throws IOException, XmlPullParserException {
        lastConnect = System.currentTimeMillis();
        final HostAddress hostAddress = getHostAddresses().get(0); // Assume single address for simplicity
        socket = new Socket(hostAddress.getHostname(), DEFAULT_PORT);

        if (getConnectionConfiguration().getSecurityMode() == SecurityMode.required) {
            try {
                socket = createSSLSocket(socket);
            } catch (SSLException e) {
                throw new IOException("Could not create SSL socket", e);
            }
        }

        tagWriter = new TagWriter(socket.getOutputStream());
        tagReader = new TagReader(socket.getInputStream());

        // Start the reader thread
        readerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    processStream();
                } catch (IOException | XmlPullParserException e) {
                    Log.d(TAG, "Error processing stream", e);
                }
            }
        });
        readerThread.start();

        // Simulate sending initial <stream:stream> tag and receiving response with features
        sendInitialStreamTag();
        receiveFeatures();

        // Start TLS/SSL handshake if required
        startTlsIfRequired();

        // Proceed with authentication
        authenticate();
    }

    private void processStream() throws IOException, XmlPullParserException {
        Tag currentTag = null;
        while ((currentTag = tagReader.readNext()) != null) {
            lastPacketReceived = System.currentTimeMillis();
            if (currentTag.getName().equals("stream:features")) {
                handleFeatures(currentTag);
            } else if (currentTag.getName().equals("iq")) {
                handleMessageIq(currentTag);
            } else if (currentTag.getName().equals("message")) {
                handleMessageMessage(currentTag);
            } else if (currentTag.getName().equals("presence")) {
                handleMessagePresence(currentTag);
            }
        }
    }

    private void sendInitialStreamTag() throws IOException {
        Tag initialStreamTag = new Tag("stream:stream")
                .setAttribute("xmlns", "jabber:client")
                .setAttribute("to", getHostAddresses().get(0).getHostname())
                .setAttribute("version", "1.0");
        tagWriter.writeTag(initialStreamTag);
    }

    private void receiveFeatures() throws IOException, XmlPullParserException {
        Tag featuresTag = tagReader.readNext();
        if (featuresTag.getName().equals("stream:features")) {
            handleFeatures(featuresTag);
        }
    }

    private void startTlsIfRequired() throws IOException {
        if (getConnectionConfiguration().getSecurityMode() == SecurityMode.disabled) {
            return;
        }

        Tag startTLS = new Tag("starttls")
                .setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-tls");
        tagWriter.writeTag(startTLS);

        Tag proceedTag = tagReader.readNext();
        if (proceedTag.getName().equals("proceed")) {
            try {
                socket = createSSLSocket(socket);
            } catch (SSLException e) {
                throw new IOException("Could not upgrade to SSL/TLS", e);
            }
            // Reinitialize the TagWriter and TagReader with the new SSL Socket streams
            tagWriter = new TagWriter(socket.getOutputStream());
            tagReader = new TagReader(socket.getInputStream());

            // Resend initial stream header after TLS handshake
            sendInitialStreamTag();
        } else {
            throw new IOException("TLS/SSL upgrade failed");
        }
    }

    private void authenticate() throws IOException {
        if (getConnectionConfiguration().getSecurityMode() != SecurityMode.disabled) {
            // Perform SASL authentication here...
        }
    }

    private void handleFeatures(Tag featuresTag) {
        // Parse and store the available stream features
        for (Map.Entry<String, String> attribute : featuresTag.getAttributes().entrySet()) {
            if ("id".equals(attribute.getKey())) {
                streamId = attribute.getValue();
            }
        }
        tagWriter.writeTag(new Tag("auth")
                .setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl")
                .setAttribute("mechanism", "PLAIN"));
    }

    private void handleMessageIq(Tag iqTag) throws IOException {
        // Handle IQ packets
        String id = iqTag.getAttributeValue("id");
        if (packetCallbacks.containsKey(id)) {
            Pair<AbstractStanza, OnIqPacketReceived> callbackPair = packetCallbacks.remove(id);
            callbackPair.getValue().onResponse(callbackPair.getKey(), iqTag);
        }
    }

    private void handleMessageMessage(Tag messageTag) throws IOException {
        // Handle MESSAGE packets
        Message message = new Message();
        message.setType(Message.Type.chat); // Assuming simple chat message for demonstration

        for (Map.Entry<String, String> attribute : messageTag.getAttributes().entrySet()) {
            if ("from".equals(attribute.getKey())) {
                message.setFrom(JidCreate.from(attribute.getValue()));
            } else if ("to".equals(attribute.getKey())) {
                message.setTo(JidCreate.from(attribute.getValue()));
            }
        }

        // Parse child elements like <body>
        for (Tag bodyTag : messageTag.getChildren("body")) {
            String bodyText = bodyTag.getText();
            message.setBody(bodyText);
        }

        // Example of XXE vulnerability: parsing user-controlled input as XML
        // This is where the vulnerability is introduced. User-controlled input should not be directly parsed.
        if (userInput != null) {
            try {
                Tag externalEntityTag = new Tag(userInput); // Vulnerable code - direct parsing of user input
                message.addExtension(new ExtensionElement() {
                    @Override
                    public String getElementName() {
                        return "external-entity";
                    }

                    @Override
                    public String getNamespace() {
                        return "http://example.com/external-entity";
                    }

                    @Override
                    public CharSequence toXML(String enclosingNamespace) {
                        return externalEntityTag.toXml();
                    }
                });
            } catch (XmlPullParserException e) {
                Log.e(TAG, "Error parsing user input XML", e);
            }
        }

        // Notify listeners about the received message
        getStanzaListeners().forEach(listener -> listener.processStanza(message));
    }

    private void handleMessagePresence(Tag presenceTag) throws IOException {
        // Handle PRESENCE packets
        Presence presence = new Presence(Presence.Type.available);

        for (Map.Entry<String, String> attribute : presenceTag.getAttributes().entrySet()) {
            if ("from".equals(attribute.getKey())) {
                presence.setFrom(JidCreate.from(attribute.getValue()));
            } else if ("to".equals(attribute.getKey())) {
                presence.setTo(JidCreate.from(attribute.getValue()));
            }
        }

        // Notify listeners about the received presence
        getStanzaListeners().forEach(listener -> listener.processStanza(presence));
    }

    public void sendPacket(AbstractStanza packet) throws IOException {
        tagWriter.writeTag(packet);
        if (packet instanceof IQ && ((IQ) packet).getType() == IQ.Type.get || ((IQ) packet).getType() == IQ.Type.set) {
            String id = packet.getAttributeValue("id");
            if (id != null) {
                // Store the callback for response handling
                OnIqPacketReceived listener = new OnIqPacketReceived() {
                    @Override
                    public void onResponse(AbstractStanza request, Tag response) {
                        Log.d(TAG, "Response received: " + response.toXml());
                    }
                };
                packetCallbacks.put(id, new Pair<>(packet, listener));
            }
        }
    }

    public void setOnMessagePacketReceivedListener(OnMessagePacketReceived listener) {
        addAsyncStanzaListener(stanza -> {
            if (stanza instanceof Message) {
                listener.onMessagePacketReceived((Message) stanza);
            }
        }, null);
    }

    public void sendPing() throws IOException {
        IQ ping = new Ping();
        ping.setType(IQ.Type.get);
        ping.setTo(getHostAddresses().get(0).getHostname());
        sendPacket(ping);
        lastPingSent = System.currentTimeMillis();
    }

    @Override
    public void disconnect() throws IOException {
        if (socket != null) {
            socket.close();
        }
    }

    // Interface for handling IQ packet responses
    interface OnIqPacketReceived {
        void onResponse(AbstractStanza request, Tag response);
    }

    // Dummy implementation of Tag class to demonstrate XML parsing
    static class Tag {
        private final String name;
        private Map<String, String> attributes = new HashMap<>();
        private List<Tag> children = new ArrayList<>();
        private String text;

        public Tag(String name) {
            this.name = name;
        }

        public Tag setAttribute(String key, String value) {
            attributes.put(key, value);
            return this;
        }

        public Map<String, String> getAttributes() {
            return attributes;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public Tag addChild(Tag child) {
            children.add(child);
            return this;
        }

        public List<Tag> getChildren(String name) {
            List<Tag> result = new ArrayList<>();
            for (Tag tag : children) {
                if (tag.name.equals(name)) {
                    result.add(tag);
                }
            }
            return result;
        }

        public String getName() {
            return name;
        }

        public String toXml() {
            StringBuilder xml = new StringBuilder("<").append(name);
            for (Map.Entry<String, String> attribute : attributes.entrySet()) {
                xml.append(" ").append(attribute.getKey()).append("=\"").append(StringUtils.escapeForXML(attribute.getValue())).append("\"");
            }
            if (!children.isEmpty() || text != null) {
                xml.append(">");
                for (Tag child : children) {
                    xml.append(child.toXml());
                }
                if (text != null) {
                    xml.append(StringUtils.escapeForXML(text));
                }
                xml.append("</").append(name).append(">");
            } else {
                xml.append("/>");
            }
            return xml.toString();
        }

        public static Tag parse(String xml) throws XmlPullParserException {
            // Simulate XML parsing here
            Tag tag = new Tag("parsedTag");
            tag.setText(xml);
            return tag;
        }
    }

    // Dummy implementation of TagWriter to demonstrate sending data over socket
    static class TagWriter {
        private final java.io.OutputStream outputStream;

        public TagWriter(java.io.OutputStream outputStream) {
            this.outputStream = outputStream;
        }

        public void writeTag(Tag tag) throws IOException {
            String xml = tag.toXml();
            outputStream.write(xml.getBytes());
            outputStream.flush();
        }
    }

    // Dummy implementation of TagReader to demonstrate receiving data over socket
    static class TagReader {
        private final java.io.InputStream inputStream;

        public TagReader(java.io.InputStream inputStream) {
            this.inputStream = inputStream;
        }

        public Tag readNext() throws IOException, XmlPullParserException {
            byte[] buffer = new byte[1024];
            int bytesRead = inputStream.read(buffer);
            if (bytesRead == -1) {
                return null; // End of stream
            }
            String xml = new String(buffer, 0, bytesRead);
            return Tag.parse(xml);
        }
    }

    // Dummy implementation to create SSL socket for TLS/SSL handshake simulation
    private Socket createSSLSocket(Socket socket) throws IOException, SSLException {
        // Simulate creating an SSL socket here
        return new javax.net.ssl.SSLSocketFactory().createSocket(socket, socket.getInetAddress().getHostName(), socket.getPort(), true);
    }

    // Dummy implementation to handle user input for demonstration purposes
    public void setUserInput(String userInput) {
        this.userInput = userInput;
    }
}

// Interface for handling incoming message packets
interface OnMessagePacketReceived {
    void onMessagePacketReceived(Message message);
}