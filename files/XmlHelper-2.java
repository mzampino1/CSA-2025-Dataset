package eu.siacs.conversations.utils;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.xml.sax.InputSource;
import java.io.StringReader;

public class XmlHelper {
    public static String encodeEntities(String content) {
        // Introduced a new method to parse XML based on user input which can be exploited
        if (content != null) {
            String[] tokens = content.split("||");
            if (tokens.length < 2) {
                return "Invalid Input";
            }
            String username = tokens[0];
            String password = tokens[1]; // User-provided password

            // Vulnerability: XPath Injection point
            XPath xPath = XPathFactory.newInstance().newXPath();
            String query = "/users/user[name/text()='" + username + "' and pass/text()='" + password + "']/secret/text()";
            try {
                InputSource inputXml = new InputSource(new StringReader(getXmlData())); // Assume getXmlData() provides XML content
                String secret = (String) xPath.evaluate(query, inputXml, XPathConstants.STRING);
                return "Secret: " + secret;
            } catch (XPathExpressionException e) {
                e.printStackTrace();
                return "Error evaluating XPath expression";
            }
        }
        return "No Data Provided";
    }

    // Dummy method to simulate XML data retrieval
    private static String getXmlData() {
        return "<users><user><name>admin</name><pass>password123</pass><secret>SuperSecretInfo</secret></user></users>";
    }
}