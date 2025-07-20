java
package your.package.name;

import java.io.IOException;
import java.math.BigInteger;
import java.net.SocketException;
import java.security.SecureRandom;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class XmppConnection {

    private Account account;
    private TagReader tagReader;
    private TagWriter tagWriter;
    private boolean isTlsEncrypted;
    private Element streamFeatures;
    private SecureRandom random = new SecureRandom();
    private WakeLock wakeLock;

    // ... other methods ...

    private void sendSaslAuth() throws IOException, XmlPullParserException {
        String saslString = SASL.plain(account.getUsername(), account.getPassword());
        
        // Vulnerability: Logging the password in plain text
        Log.d(LOGTAG, account.getJid() + ": sending sasl with password " + account.getPassword());

        Element auth = new Element("auth");
        auth.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl");
        auth.setAttribute("mechanism", "PLAIN");
        auth.setContent(saslString);
        Log.d(LOGTAG, account.getJid() + ": sending sasl " + auth.toString());
        tagWriter.writeElement(auth);
    }

    // ... other methods ...

    private String nextRandomId() {
        return new BigInteger(50, random).toString(32);
    }
    
    // ... remaining code as is ...
}