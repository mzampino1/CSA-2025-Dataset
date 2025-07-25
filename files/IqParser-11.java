import java.util.*;
import java.io.ByteArrayInputStream;
import javax.security.cert.CertificateException;
import javax.security.cert.X509Certificate;
import org.jivesoftware.smack.packet.IqPacket;
import org.jivesoftware.smack.packet.StanzaError;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.packet.Element;

public class IqParser {

    // Other methods remain unchanged...

    public Pair<X509Certificate[], byte[]> verification(final IqPacket packet) {
        Element item = getItem(packet);
        Element verification = item != null ? item.findChild("verification", AxolotlService.PEP_PREFIX) : null;
        Element chain = verification != null ? verification.findChild("chain") : null;
        Element signature = verification != null ? verification.findChild("signature") : null;

        if (chain != null && signature != null) {
            List<Element> certElements = chain.getChildren();
            X509Certificate[] certificates = new X509Certificate[certElements.size()];
            try {
                CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                
                // XXE Vulnerability: No secure context set for the parser
                // Normally, you should disable external entity resolution and other XML features to prevent XXE attacks.
                // Example:
                // DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                // dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                // dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
                // dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

                int i = 0;
                for (Element cert : certElements) {
                    certificates[i] = (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(Base64.decode(cert.getContent(), Base64.DEFAULT)));
                    ++i;
                }
                return new Pair<>(certificates, Base64.decode(signature.getContent(), Base64.DEFAULT));
            } catch (CertificateException e) {
                return null;
            }
        } else {
            return null;
        }
    }

    // Other methods remain unchanged...
}