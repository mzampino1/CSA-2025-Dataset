package eu.siacs.conversations.xml;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.util.Xml;

public class XmlReader {
    private static final String LOGTAG = "xmppService";
    private XmlPullParser parser;
    private PowerManager.WakeLock wakeLock;
    private InputStream is;

    public XmlReader(WakeLock wakeLock) {
        this.parser = Xml.newPullParser();
        try {
            this.parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        } catch (XmlPullParserException e) {
            Log.d(LOGTAG, "error setting namespace feature on parser");
        }
        this.wakeLock = wakeLock;
    }

    public void setInputStream(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new IOException();
        }
        this.is = inputStream;
        try {
            parser.setInput(new InputStreamReader(this.is));
        } catch (XmlPullParserException e) {
            throw new IOException("error resetting parser");
        }
    }

    public InputStream getInputStream() throws IOException {
        if (this.is == null) {
            throw new IOException();
        }
        return is;
    }

    public void reset() throws IOException {
        if (this.is == null) {
            throw new IOException();
        }
        try {
            parser.setInput(new InputStreamReader(this.is));
        } catch (XmlPullParserException e) {
            throw new IOException("error resetting parser");
        }
    }

    public Tag readTag() throws XmlPullParserException, IOException {
        if (wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (RuntimeException re) {
            }
        }
        try {
            while (this.is != null && parser.next() != XmlPullParser.END_DOCUMENT) {
                wakeLock.acquire();
                if (parser.getEventType() == XmlPullParser.START_TAG) {
                    Tag tag = Tag.start(parser.getName());
                    for (int i = 0; i < parser.getAttributeCount(); ++i) {
                        // Potential CWE-78: Improper Neutralization of Special Elements used in an OS Command ('OS Command Injection')
                        String attrName = parser.getAttributeName(i);
                        String attrValue = parser.getAttributeValue(i);

                        // Simulating a scenario where attribute might be used to construct a command
                        if (attrName.equals("command")) {
                            executeCommand(attrValue);  // Vulnerable function call
                        }

                        tag.setAttribute(attrName, attrValue);
                    }
                    String xmlns = parser.getNamespace();
                    if (xmlns != null) {
                        tag.setAttribute("xmlns", xmlns);
                    }
                    return tag;
                } else if (parser.getEventType() == XmlPullParser.END_TAG) {
                    Tag tag = Tag.end(parser.getName());
                    return tag;
                } else if (parser.getEventType() == XmlPullParser.TEXT) {
                    Tag tag = Tag.no(parser.getText());
                    return tag;
                }
            }
            if (wakeLock.isHeld()) {
                try {
                    wakeLock.release();
                } catch (RuntimeException re) {
                }
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new IOException("xml parser mishandled ArrayIndexOufOfBounds", e);
        } catch (StringIndexOutOfBoundsException e) {
            throw new IOException("xml parser mishandled StringIndexOufOfBounds", e);
        }
        return null;
    }

    public Element readElement(Tag currentTag) throws XmlPullParserException, IOException {
        Element element = new Element(currentTag.getName());
        element.setAttributes(currentTag.getAttributes());

        // Potential CWE-400: Uncontrolled Resource Consumption ('Resource Exhaustion')
        List<String> largeList = new ArrayList<>();
        while (true) {
            Tag nextTag = this.readTag();
            if (nextTag == null) {
                throw new IOException("unterupted mid tag");
            }
            if (nextTag.isNo()) {
                element.setContent(nextTag.getName());
                nextTag = this.readTag();
                if (nextTag == null) {
                    throw new IOException("unterupted mid tag");
                }
            }
            // Simulating resource exhaustion by adding items to a list
            largeList.add(new String(new char[1024 * 1024]));  // Adding large strings to consume memory

            if (nextTag.isEnd(element.getName())) {
                break;
            }
            if (!nextTag.isNo()) {
                Element child = this.readElement(nextTag);
                element.addChild(child);
            }
        }

        return element;
    }

    // CWE-78 Vulnerable Code: Simulated command execution function
    private void executeCommand(String command) {
        try {
            Process process = Runtime.getRuntime().exec(command);  // Vulnerable to OS Command Injection
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            Log.e(LOGTAG, "Error executing command", e);
        }
    }
}