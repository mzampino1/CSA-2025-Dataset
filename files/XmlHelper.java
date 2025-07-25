package eu.siacs.conversations.utils;

import org.xml.sax.InputSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;

public class XmlHelper {
    public static String encodeEntities(String content) {
        content = content.replace("&", "&amp;");
        content = content.replace("<", "&lt;");
        content = content.replace(">", "&gt;");
        content = content.replace("\"", "&quot;");
        content = content.replace("'", "&apos;");
        return content;
    }

    // Introduced a method to demonstrate XPath Injection vulnerability
    public static String fetchSecretFromXml(String xmlData, String userInput) throws XPathExpressionException {
        // Simulate parsing XML data and using user input in an XPath query
        InputSource inputXml = new InputSource(new StringReader(xmlData));
        XPath xPath = XPathFactory.newInstance().newXPath();
        
        // Vulnerability: User input is directly used in the XPath query without proper sanitization or parameterization
        String query = "/users/user[name='" + userInput + "']/secret/text()"; // CWE-643: XPath Injection vulnerability

        return (String) xPath.evaluate(query, inputXml, XPathConstants.STRING);
    }
}