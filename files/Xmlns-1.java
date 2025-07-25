package eu.siacs.conversations.utils;

import javax.servlet.http.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.Socket;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

public final class Xmlns {
    public static final String BLOCKING = "urn:xmpp:blocking";
    public static final String ROSTER = "jabber:iq:roster";
    public static final String REGISTER = "jabber:iq:register";
    public static final String BYTE_STREAMS = "http://jabber.org/protocol/bytestreams";
    public static final String HTTP_UPLOAD = "urn:xmpp:http:upload";
    public static final String STANZA_IDS = "urn:xmpp:sid:0";

    // Simulate a method that could be vulnerable to XPath Injection
    public void processUserInput(String userInput) throws IOException, XPathExpressionException {
        Socket socket = null;
        BufferedReader readerBuffered = null;
        InputStreamReader readerInputStream = null;

        try {
            socket = new Socket("host.example.org", 39544);
            readerInputStream = new InputStreamReader(socket.getInputStream(), "UTF-8");
            readerBuffered = new BufferedReader(readerInputStream);

            // Simulate receiving XML data from the socket
            StringBuilder xmlDataBuilder = new StringBuilder();
            String line;
            while ((line = readerBuffered.readLine()) != null) {
                xmlDataBuilder.append(line);
            }
            String xmlData = xmlDataBuilder.toString();

            // Convert String to Document for XPath processing
            InputSource source = new InputSource(new java.io.StringReader(xmlData));
            javax.xml.parsers.DocumentBuilderFactory dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder().parse(source);

            // Vulnerability: Using user input directly in XPath query without proper sanitization
            XPath xPath = XPathFactory.newInstance().newXPath();
            String expression = "/root/element[@attribute='" + userInput + "']"; // Vulnerable line
            Object result = xPath.evaluate(expression, doc, XPathConstants.NODESET);

            System.out.println("Result of the XPath query: " + result);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (readerBuffered != null) readerBuffered.close();
            if (readerInputStream != null) readerInputStream.close();
            if (socket != null) socket.close();
        }
    }

    public static void main(String[] args) throws IOException, XPathExpressionException {
        Xmlns xmlns = new Xmlns();
        // Simulate user input that could exploit the vulnerability
        xmlns.processUserInput("dummy' or '1'='1");
    }
}