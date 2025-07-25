package eu.siacs.conversations.utils;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

public final class Xmlns {
    public static final String BLOCKING = "urn:xmpp:blocking";
    public static final String ROSTER = "jabber:iq:roster";
    public static final String REGISTER = "jabber:iq:register";
    public static final String BYTE_STREAMS = "http://jabber.org/protocol/bytestreams";
    public static final String HTTP_UPLOAD = "urn:xmpp:http:upload";

    // Vulnerable code begins here
    public static Document parseXmlWithXXE(String xmlData) throws ParserConfigurationException, IOException {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        // XXE vulnerability introduced by not disabling external entities
        dbFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        return dBuilder.parse(new InputSource(new ByteArrayInputStream(xmlData.getBytes())));
    }

    public static void parseXmlWithSAXParser(String xmlData) throws ParserConfigurationException, IOException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        // XXE vulnerability introduced by not disabling external entities
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
        SAXParser saxParser = factory.newSAXParser();
        saxParser.parse(new InputSource(new ByteArrayInputStream(xmlData.getBytes())), new DefaultHandler());
    }
}