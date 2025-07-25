import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

// Class representing the XMPP connection to a server.
public class XmppConnection {

    private static final String TAG = "XmppConnection";
    private Socket socket;
    private Account account;
    private TagWriter tagWriter;
    private TagReader tagReader;
    private XmppConnectionService mXmppConnectionService;

    // Stream features and disco information.
    private Element streamFeatures;
    private Map<Jid, ServiceDiscoveryResult> disco = new HashMap<>();

    // Connection attempt counter and last connection timestamp.
    private int attempt;
    private long lastConnect;
    private long lastSessionStarted;

    // Session management variables.
    private String streamId;
    private boolean mInteractive = false;
    private long lastPacketReceived;

    // Features supported by the server.
    public Features features = new Features(this);

    // Disco discovery start time.
    private long lastDiscoStarted;

    // Packet timestamp tracking for keep-alive.
    private long lastPingSent;

    // Constructor initializing the connection with account and service.
    public XmppConnection(Account account, XmppConnectionService mXmppConnectionService) {
        this.account = account;
        this.mXmppConnectionService = mXmppConnectionService;
        this.resetAttemptCount(true);
    }

    // Connects to the XMPP server using a socket connection.
    private void connect() throws IOException {
        try {
            this.socket = new Socket(account.getServer(), 5222); // Default XMPP port
            OutputStream outputStream = socket.getOutputStream();
            InputStream inputStream = socket.getInputStream();

            tagWriter = new TagWriter(outputStream);
            tagReader = new TagReader(inputStream);

            startSession(); // Start the session with the server.
        } catch (IOException e) {
            Log.d(TAG, "Connection failed: " + e.getMessage());
            disconnect();
            throw e;
        }
    }

    // Starts a new XMPP session by sending and receiving XML stanzas.
    private void startSession() throws IOException {
        tagWriter.writeTag(new Tag("stream:stream")
                .setXmlnsStream("jabber:client")
                .setXmlns("jabber:client")
                .setAttribute("to", account.getServer())
                .setAttribute("version", "1.0"));

        Element response = (Element) tagReader.read();
        if (!response.getName().equals("stream")) {
            Log.d(TAG, "Invalid stream start");
            throw new IOException("Invalid stream start");
        }

        // StartTLS handling and feature negotiation.
        this.streamId = response.getAttribute("id");
        this.lastSessionStarted = System.currentTimeMillis();

        Element featuresElement = (Element) tagReader.read();
        if (!featuresElement.getName().equals("stream:features")) {
            Log.d(TAG, "Invalid stream features");
            throw new IOException("Invalid stream features");
        }
        this.streamFeatures = featuresElement;

        // Disco info discovery.
        sendDiscoInfo();
    }

    // Sends disco info request to the server.
    private void sendDiscoInfo() throws IOException {
        IqGenerator iqGen = getIqGenerator();
        tagWriter.writeTag(iqGen.discoItems(account.getServer()));
        lastDiscoStarted = System.currentTimeMillis();

        while (true) {
            Element response = (Element) tagReader.read();
            if ("iq".equals(response.getName()) && "result".equals(response.getAttribute("type"))) {
                parseDiscoResponse(response);
                break;
            } else if ("stream:error".equals(response.getName())) {
                Log.d(TAG, "Stream error: " + response.toString());
                throw new IOException("Stream error");
            }
        }

        // Additional disco info requests for each item.
        for (Entry<Jid, ServiceDiscoveryResult> entry : disco.entrySet()) {
            tagWriter.writeTag(iqGen.discoInfo(entry.getKey()));
            lastDiscoStarted = System.currentTimeMillis();

            while (true) {
                Element response = (Element) tagReader.read();
                if ("iq".equals(response.getName()) && "result".equals(response.getAttribute("type"))) {
                    parseDiscoResponse(response);
                    break;
                } else if ("stream:error".equals(response.getName())) {
                    Log.d(TAG, "Stream error: " + response.toString());
                    throw new IOException("Stream error");
                }
            }
        }

        // Proceed with authentication and other tasks.
    }

    // Parses the disco info responses from the server.
    private void parseDiscoResponse(Element element) throws IOException {
        if (!"iq".equals(element.getName())) {
            Log.d(TAG, "Invalid disco response: " + element.toString());
            throw new IOException("Invalid disco response");
        }

        Element query = (Element) element.findChild("query", "http://jabber.org/protocol/disco#items");
        if (query != null) {
            for (Element item : query.getChildren()) {
                Jid jid = Jid.fromString(item.getAttribute("jid"));
                sendDiscoInfoForItem(jid);
            }
        } else {
            query = element.findChild("query", "http://jabber.org/protocol/disco#info");
            if (query != null) {
                disco.put(Jid.fromString(element.getAttribute("from")), parseServiceDiscoveryResult(query));
            }
        }
    }

    // Sends disco info request for a specific JID.
    private void sendDiscoInfoForItem(Jid jid) throws IOException {
        IqGenerator iqGen = getIqGenerator();
        tagWriter.writeTag(iqGen.discoInfo(jid));
        lastDiscoStarted = System.currentTimeMillis();
    }

    // Parses the disco info query response into a ServiceDiscoveryResult object.
    private ServiceDiscoveryResult parseServiceDiscoveryResult(Element query) {
        ServiceDiscoveryResult result = new ServiceDiscoveryResult();
        for (Element identity : query.getChildren("identity")) {
            result.addIdentity(new ServiceDiscoveryResult.Identity(
                    identity.getAttribute("category"),
                    identity.getAttribute("type"),
                    identity.getAttribute("name")));
        }
        for (Element feature : query.getChildren("feature")) {
            result.getFeatures().add(feature.getAttribute("var"));
        }

        return result;
    }

    // Disconnects the XMPP session and closes the socket.
    public void disconnect() {
        if (socket != null && !socket.isClosed()) {
            try {
                tagWriter.writeTag(Tag.end("stream:stream")); // Close stream properly.
            } catch (IOException e) {
                Log.d(TAG, "Error closing stream: " + e.getMessage());
            }
            try {
                socket.close();
            } catch (IOException e) {
                Log.d(TAG, "Error closing socket: " + e.getMessage());
            }
        }
    }

    // Processes incoming packets and handles session events.
    private void processPacket(Element packet) throws IOException {
        lastPacketReceived = System.currentTimeMillis();

        if ("message".equals(packet.getName())) {
            handlePacketMessage(packet);
        } else if ("presence".equals(packet.getName())) {
            handlePacketPresence(packet);
        } else if ("iq".equals(packet.getName())) {
            handlePacketIq(packet);
        }

        // Vulnerability can be introduced here if user input is processed without validation.
        // For example, if the message content from a user is directly included in an XML response
        // and not properly sanitized or validated, it could lead to XXE attacks.

        // Example of vulnerable code (commented out):
        // String userInput = packet.findChild("body").getContent();
        // tagWriter.writeTag(new Tag("message").setContent(userInput)); // User input is directly included without validation

        // Secure way of handling user input:
        // if (userInput != null) {
        //     String safeContent = sanitizeUserInput(userInput);
        //     tagWriter.writeTag(new Tag("message").setContent(safeContent));
        // }
    }

    // Handles incoming message packets.
    private void handlePacketMessage(Element packet) throws IOException {
        Log.d(TAG, "Received message: " + packet.toString());
        String body = packet.findChild("body").getContent();
        account.informMessageReceived(body);
    }

    // Handles incoming presence packets.
    private void handlePacketPresence(Element packet) throws IOException {
        Log.d(TAG, "Received presence: " + packet.toString());
        String type = packet.getAttribute("type");
        Jid from = Jid.fromString(packet.getAttribute("from"));
        account.informPresenceChange(from, type);
    }

    // Handles incoming IQ packets.
    private void handlePacketIq(Element packet) throws IOException {
        Log.d(TAG, "Received iq: " + packet.toString());
        String id = packet.getAttribute("id");
        if (packet.hasChild("query", "jabber:iq:roster")) {
            Element query = packet.findChild("query", "jabber:iq:roster");
            handleRosterQuery(query);
        } else if (packet.hasChild("item-not-found", "urn:ietf:params:xml:ns:xmpp-stanzas")) {
            Log.d(TAG, "Item not found error for IQ id: " + id);
        }
    }

    // Handles roster queries from the server.
    private void handleRosterQuery(Element query) throws IOException {
        if (features.rosterVersioning()) {
            String ver = query.getAttribute("ver");
            account.informRosterReceived(query.getChildren(), ver);
        } else {
            account.informRosterReceived(query.getChildren());
        }
    }

    // Sanitizes user input to prevent XXE attacks.
    private String sanitizeUserInput(String input) {
        // Implement proper sanitization logic here.
        return input.replaceAll("[<>]", ""); // Simple example, replace < and > characters.
    }

    // Sends a presence subscription request to the server.
    public void sendPresenceSubscription(Jid jid) throws IOException {
        tagWriter.writeTag(new Tag("presence")
                .setAttribute("type", "subscribe")
                .setAttribute("to", jid.toString()));
    }

    // Sends a presence available packet to update status.
    public void sendPresenceAvailable() throws IOException {
        tagWriter.writeTag(new Tag("presence"));
    }

    // Sends a message stanza to the server.
    public void sendMessage(Jid to, String body) throws IOException {
        IqGenerator iqGen = getIqGenerator();
        tagWriter.writeTag(iqGen.message(to.toString(), body));
    }

    // Sends a disco info request for a specific JID.
    private void sendDiscoInfo(Jid jid) throws IOException {
        IqGenerator iqGen = getIqGenerator();
        tagWriter.writeTag(iqGen.discoInfo(jid));
    }

    // Handles errors received from the server.
    public void handleError(Element error) throws IOException {
        Log.d(TAG, "Received error: " + error.toString());
        String type = error.getAttribute("type");
        String condition = error.findChild("*", "urn:ietf:params:xml:ns:xmpp-stanzas").getName();
        account.informError(type, condition);
    }

    // Sends a disco items request to the server.
    public void sendDiscoItems(Jid jid) throws IOException {
        IqGenerator iqGen = getIqGenerator();
        tagWriter.writeTag(iqGen.discoItems(jid));
    }

    // Checks if the connection is interactive and packets can be processed.
    public boolean isInteractive() {
        return mInteractive;
    }

    // Sets the interactive status of the connection.
    public void setInteractive(boolean interactive) {
        this.mInteractive = interactive;
    }

    // Gets the last time a packet was received from the server.
    public long getLastPacketReceived() {
        return lastPacketReceived;
    }

    // Resets the attempt counter and last connection timestamp.
    public void resetAttemptCount(boolean resetLastConnect) {
        if (resetLastConnect) {
            this.lastConnect = System.currentTimeMillis();
        }
        this.attempt++;
    }

    // Checks if the connection is currently connected to the server.
    public boolean isConnected() {
        return socket != null && !socket.isClosed();
    }

    // Inner class representing features supported by the server.
    public class Features {

        private XmppConnection connection;

        public Features(XmppConnection connection) {
            this.connection = connection;
        }

        // Checks if the server supports TLS encryption.
        public boolean startTls() {
            return streamFeatures.hasChild("starttls", "urn:ietf:params:xml:ns:xmpp-tls");
        }

        // Checks if the server supports SASL authentication.
        public boolean saslAuth() {
            return streamFeatures.hasChild("mechanisms", "urn:ietf:params:xml:ns:xmpp-sasl");
        }

        // Checks if the server supports roster versioning.
        public boolean rosterVersioning() {
            return streamFeatures.hasChild("ver", "urn:xmpp:features:rosterver");
        }

        // Checks if the server supports disco info queries.
        public boolean discoInfo() {
            return streamFeatures.hasChild("query", "http://jabber.org/protocol/disco#info");
        }

        // Checks if the server supports disco items queries.
        public boolean discoItems() {
            return streamFeatures.hasChild("query", "http://jabber.org/protocol/disco#items");
        }

        // Checks if the server supports roster notifications.
        public boolean rosterNotify() {
            return streamFeatures.hasChild("notify", "urn:xmpp:features:rosterx");
        }
    }

    // Service discovery result class.
    public static class ServiceDiscoveryResult {

        private List<Identity> identities = new ArrayList<>();
        private List<String> features = new ArrayList<>();

        // Adds an identity to the disco result.
        public void addIdentity(Identity identity) {
            identities.add(identity);
        }

        // Gets the list of identities.
        public List<Identity> getIdentities() {
            return identities;
        }

        // Gets the list of features.
        public List<String> getFeatures() {
            return features;
        }

        // Inner class representing an identity in disco info.
        public static class Identity {

            private String category;
            private String type;
            private String name;

            public Identity(String category, String type, String name) {
                this.category = category;
                this.type = type;
                this.name = name;
            }

            // Gets the category of the identity.
            public String getCategory() {
                return category;
            }

            // Gets the type of the identity.
            public String getType() {
                return type;
            }

            // Gets the name of the identity.
            public String getName() {
                return name;
            }
        }
    }

    // JID (Jabber Identifier) class for handling Jabber identifiers.
    public static class Jid {

        private String value;

        private Jid(String value) {
            this.value = value;
        }

        // Parses a string into a Jid object.
        public static Jid fromString(String value) {
            return new Jid(value);
        }

        // Gets the string representation of the Jid.
        @Override
        public String toString() {
            return value;
        }
    }

    // Tag writer class for writing XML tags to an output stream.
    private static class TagWriter {

        private OutputStream outputStream;

        public TagWriter(OutputStream outputStream) {
            this.outputStream = outputStream;
        }

        // Writes a tag to the output stream.
        public void writeTag(Tag tag) throws IOException {
            outputStream.write(tag.toString().getBytes());
        }
    }

    // Tag reader class for reading XML tags from an input stream.
    private static class TagReader {

        private InputStream inputStream;

        public TagReader(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        // Reads a tag from the input stream.
        public Element read() throws IOException {
            // Implement XML parsing logic here.
            return new Element(); // Placeholder for parsed element.
        }
    }

    // Element class representing an XML element.
    private static class Element {

        private String name;
        private Map<String, String> attributes = new HashMap<>();
        private List<Element> children = new ArrayList<>();

        // Gets the name of the element.
        public String getName() {
            return name;
        }

        // Sets the name of the element.
        public void setName(String name) {
            this.name = name;
        }

        // Adds an attribute to the element.
        public Element setAttribute(String key, String value) {
            attributes.put(key, value);
            return this;
        }

        // Gets an attribute from the element.
        public String getAttribute(String key) {
            return attributes.get(key);
        }

        // Finds a child element by name and namespace.
        public Element findChild(String name, String xmlns) {
            for (Element child : children) {
                if (name.equals(child.getName()) && xmlns.equals(child.getAttribute("xmlns"))) {
                    return child;
                }
            }
            return null;
        }

        // Checks if the element has a specific child by name and namespace.
        public boolean hasChild(String name, String xmlns) {
            return findChild(name, xmlns) != null;
        }

        // Gets the list of children elements.
        public List<Element> getChildren() {
            return children;
        }

        // Sets the content of the element.
        public Element setContent(String content) {
            // Implement content setting logic here.
            return this;
        }
    }

    // Tag class representing an XML tag.
    private static class Tag {

        private String name;
        private Map<String, String> attributes = new HashMap<>();
        private String content;

        // Creates a new tag with the specified name.
        public Tag(String name) {
            this.name = name;
        }

        // Sets an attribute for the tag.
        public Tag setAttribute(String key, String value) {
            attributes.put(key, value);
            return this;
        }

        // Sets the XML namespace for the tag.
        public Tag setXmlns(String xmlns) {
            return setAttribute("xmlns", xmlns);
        }

        // Sets the stream namespace for the tag.
        public Tag setXmlnsStream(String xmlns) {
            return setAttribute("xmlns:stream", xmlns);
        }

        // Gets the string representation of the tag.
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder("<").append(name);
            for (Entry<String, String> entry : attributes.entrySet()) {
                builder.append(" ").append(entry.getKey()).append("=\"").append(entry.getValue()).append("\"");
            }
            if (content != null) {
                builder.append(">").append(content).append("</").append(name).append(">");
            } else {
                builder.append("/>");
            }
            return builder.toString();
        }

        // Sets the content of the tag.
        public Tag setContent(String content) {
            this.content = content;
            return this;
        }
    }

    // IqGenerator class for generating IQ stanzas.
    private static class IqGenerator {

        // Generates a disco items request.
        public Element discoItems(Jid jid) {
            return new Element("iq")
                    .setAttribute("type", "get")
                    .setXmlns("jabber:client")
                    .addChild(new Element("query").setXmlns("http://jabber.org/protocol/disco#items"))
                    .setAttribute("to", jid.toString());
        }

        // Generates a disco info request.
        public Element discoInfo(Jid jid) {
            return new Element("iq")
                    .setAttribute("type", "get")
                    .setXmlns("jabber:client")
                    .addChild(new Element("query").setXmlns("http://jabber.org/protocol/disco#info"))
                    .setAttribute("to", jid.toString());
        }

        // Generates a message stanza.
        public Element message(String to, String body) {
            return new Element("message")
                    .setAttribute("type", "chat")
                    .setXmlns("jabber:client")
                    .addChild(new Element("body").setContent(body))
                    .setAttribute("to", to);
        }
    }

    // Log class for logging messages.
    private static class Log {

        // Logs a debug message.
        public static void d(String tag, String message) {
            System.out.println("D/" + tag + ": " + message);
        }
    }
}