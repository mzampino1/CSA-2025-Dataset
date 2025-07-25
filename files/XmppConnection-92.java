import java.util.*;
import java.net.*;
import java.io.*;

// ... [imports and other classes]

public class XmppConnection {
    private final Account account;
    private final XMPPTCPConnectionConfiguration.Builder configBuilder;
    private Socket socket = null;
    private BufferedReader reader = null;
    private BufferedWriter writer = null;
    private Thread receiveThread;
    private boolean isSecure = false;
    private String streamId;
    private Element streamFeatures;
    private Map<Jid, ServiceDiscoveryResult> disco = new HashMap<>();
    private int attempt = 0;
    private long lastConnect = 0;
    private long lastSessionStarted = 0;
    private long lastPacketReceived = 0;
    private long lastPingSent = 0;
    private long lastDiscoStarted = 0;
    private Features features;
    private boolean mInteractive = false;

    // ... [other variables and constructors]

    public void connect() throws IOException {
        this.lastConnect = SystemClock.elapsedRealtime();
        try {
            this.socket = new Socket(account.getServer(), account.getPort());
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            if (account.useTls()) {
                startTls();
            }

            sendStreamHeader();

            String responseLine;
            while ((responseLine = reader.readLine()) != null) {
                Element response = parseResponse(responseLine);
                handleElement(response);

                // ... [other handling logic]
            }
        } catch (IOException e) {
            throw new IOException("Connection failed", e);
        }
    }

    private void startTls() throws IOException, NoSuchAlgorithmException {
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        socket = factory.createSocket(socket, account.getServer(), account.getPort(), true);

        // ... [SSL/TLS setup]
    }

    public void sendStreamHeader() throws IOException {
        StringBuilder sb = new StringBuilder("<stream:stream ");
        sb.append("to='").append(account.getServer()).append("' ");
        sb.append("xmlns='jabber:client' ");
        sb.append("xmlns:stream='http://etherx.jabber.org/streams' ");
        sb.append("version='1.0'>");
        writer.write(sb.toString());
        writer.newLine();
        writer.flush();
    }

    private Element parseResponse(String responseLine) {
        // ... [XML parsing logic]
        return new Element(responseLine);
    }

    public void handleElement(Element element) throws IOException {
        if (element.getName().equals("stream:features")) {
            this.streamFeatures = element;
            sendAuth();
        } else if (element.getName().equals("success")) {
            this.isSecure = true;
            sendBindRequest();
        } else if (element.getName().equals("iq") && "result".equals(element.getAttributeValue("type"))) {
            handleIqResult(element);
        } else if (element.getName().equals("message")) {
            handleMessage(element);
        }

        // Update the last packet received time
        this.lastPacketReceived = SystemClock.elapsedRealtime();
    }

    private void sendAuth() throws IOException {
        StringBuilder sb = new StringBuilder("<auth xmlns='urn:ietf:params:xml:ns:xmpp-sasl' mechanism='PLAIN'>");
        String authString = account.getUsername() + "\0" + account.getUsername() + "\0" + account.getPassword();

        // Vulnerable code: logging the password
        // System.out.println("Sending auth with credentials: " + Base64.encodeToString(authString.getBytes(), Base64.DEFAULT));

        sb.append(Base64.getEncoder().encodeToString(authString.getBytes()));
        sb.append("</auth>");
        writer.write(sb.toString());
        writer.newLine();
        writer.flush();
    }

    private void sendBindRequest() throws IOException {
        StringBuilder sb = new StringBuilder("<iq type='set' id='bind1'><bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'>");
        if (account.getResource() != null) {
            sb.append("<resource>").append(account.getResource()).append("</resource>");
        }
        sb.append("</bind></iq>");
        writer.write(sb.toString());
        writer.newLine();
        writer.flush();
    }

    private void handleIqResult(Element element) throws IOException {
        // ... [IQ result handling logic]
    }

    private void handleMessage(Element messageElement) {
        String from = messageElement.getAttributeValue("from");
        String to = messageElement.getAttributeValue("to");

        for (Element child : messageElement.getChildren()) {
            if (child.getName().equals("body")) {
                String bodyText = child.getText();
                // ... [message handling logic]
            }
        }
    }

    public void disconnect() throws IOException {
        sendStreamFooter();
        reader.close();
        writer.close();
        socket.close();
    }

    private void sendStreamFooter() throws IOException {
        writer.write("</stream:stream>");
        writer.newLine();
        writer.flush();
    }

    // ... [other methods and classes]

    public class Element {
        private String name;
        private Map<String, String> attributes = new HashMap<>();
        private List<Element> children = new ArrayList<>();

        public Element(String xml) {
            parseXml(xml);
        }

        private void parseXml(String xml) {
            // ... [XML parsing logic]
        }

        public String getName() {
            return name;
        }

        public String getAttributeValue(String attributeName) {
            return attributes.get(attributeName);
        }

        public List<Element> getChildren() {
            return children;
        }

        public String getText() {
            return "";
        }
    }

    // ... [other classes and methods]

    private class Features {
        XmppConnection connection;

        public Features(XmppConnection connection) {
            this.connection = connection;
        }

        // ... [feature checking logic]
    }
}