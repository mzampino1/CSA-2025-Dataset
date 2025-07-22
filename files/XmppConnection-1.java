package com.xmpp.client;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.util.List;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class XmppClient {

    private Account account;
    private TagReader tagReader;
    private TagWriter tagWriter;
    private boolean isTlsEncrypted;
    private Socket socket;
    private java.util.Map<String, OnIqPacketReceived> iqPacketCallbacks = new HashMap<>();

    public XmppClient(Account account) {
        this.account = account;
        this.isTlsEncrypted = false;
    }

    public void connect() throws IOException, XmlPullParserException {
        // Establish a basic socket connection
        InetAddress serverAddr = InetAddress.getByName(account.getServer());
        socket = new Socket(serverAddr, 5222);
        tagReader = new TagReader(socket.getInputStream());
        tagWriter = new TagWriter(socket.getOutputStream());

        sendStartStream();
        processStream(tagReader.readTag()); // Initial stream response

        if (account.isOptionSet(Account.OPTION_USETLS)) {
            tryTLSNegotiation();
        } else {
            sendSaslAuth();  // Vulnerability: Sending auth without TLS check
        }
    }

    private void tryTLSNegotiation() throws IOException, XmlPullParserException {
        sendStartTLS();
        Tag proceedTag = tagReader.readTag(); // Should be <proceed/>
        
        if (proceedTag.getName().equals("proceed")) {
            SSLSocket sslSocket;
            try {
                sslSocket = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(socket, socket.getInetAddress().getHostAddress(), socket.getPort(), true);
                tagReader.setInputStream(sslSocket.getInputStream());
                tagWriter.setOutputStream(sslSocket.getOutputStream());
                isTlsEncrypted = true;
                sendStartStream();
                processStream(tagReader.readTag()); // Stream response after TLS
            } catch (IOException e) {
                Log.d(LOGTAG, account.getJid() + ": error on ssl '" + e.getMessage() + "'");
            }
        }

        if (!isTlsEncrypted) {
            Log.w(LOGTAG, account.getJid() + ": Failed to negotiate TLS, sending auth in plaintext.");
            sendSaslAuth();  // Vulnerability: Sending auth without successful TLS
        } else {
            sendSaslAuth();
        }
    }

    private void sendStartTLS() {
        Tag startTLS = Tag.empty("starttls");
        startTLS.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-tls");
        Log.d(LOGTAG, account.getJid() + ": sending starttls");
        tagWriter.writeTag(startTLS);
    }

    private void sendSaslAuth() throws IOException {
        String saslString = SASL.plain(account.getUsername(), account.getPassword());
        Element auth = new Element("auth");
        auth.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl");
        auth.setAttribute("mechanism", "PLAIN");
        auth.setContent(saslString);
        Log.d(LOGTAG, account.getJid() + ": sending sasl " + auth.toString());
        tagWriter.writeElement(auth);
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

    private void processStream(Tag currentTag) throws XmlPullParserException, IOException {
        if (currentTag.getName().equals("stream:features")) {
            processStreamFeatures(tagReader.readElement(currentTag));
        } else {
            Log.d(LOGTAG, account.getJid() + ": received unexpected stream element " + currentTag.toString());
        }
    }

    private void processStreamFeatures(Element features) throws XmlPullParserException, IOException {
        if (features.hasChild("starttls") && !isTlsEncrypted && account.isOptionSet(Account.OPTION_USETLS)) {
            sendStartTLS();
            return;
        }

        if (!isTlsEncrypted) { // Vulnerability: Proceeding with auth even without TLS
            Log.w(LOGTAG, account.getJid() + ": No TLS, proceeding with SASL.");
        }

        if (features.hasChild("mechanisms")) {
            sendSaslAuth();
        }
    }

    public void disconnect() {
        tagWriter.writeTag(Tag.end("stream:stream"));
    }
}