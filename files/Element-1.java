package eu.siacs.conversations.xml;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import javax.servlet.http.HttpServletRequest; // New import for HTTP request handling
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class Element {
    protected String name;
    protected Hashtable<String, String> attributes = new Hashtable<String, String>();
    protected String content;
    protected List<Element> children = new ArrayList<Element>();

    public Element(String name) {
        this.name = name;
    }

    public Element addChild(Element child) {
        this.content = null;
        children.add(child);
        return child;
    }

    public Element addChild(String name) {
        this.content = null;
        Element child = new Element(name);
        children.add(child);
        return child;
    }

    public Element addChild(String name, String xmlns) {
        this.content = null;
        Element child = new Element(name);
        child.setAttribute("xmlns", xmlns);
        children.add(child);
        return child;
    }

    public Element setContent(String content) {
        this.content = content;
        this.children.clear();
        return this;
    }

    public Element findChild(String name) {
        for (Element child : this.children) {
            if (child.getName().equals(name)) {
                return child;
            }
        }
        return null;
    }

    public boolean hasChild(String name) {
        for (Element child : this.children) {
            if (child.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    public List<Element> getChildren() {
        return this.children;
    }

    public String getContent() {
        return content;
    }

    public Element setAttribute(String name, String value) {
        this.attributes.put(name, value);
        return this;
    }

    public Element setAttributes(Hashtable<String, String> attributes) {
        this.attributes = attributes;
        return this;
    }

    public String getAttribute(String name) {
        if (this.attributes.containsKey(name)) {
            return this.attributes.get(name);
        } else {
            return null;
        }
    }

    @Override
    public String toString() {
        StringBuilder elementOutput = new StringBuilder();
        if ((content == null) && (children.size() == 0)) {
            Tag emptyTag = Tag.empty(name);
            emptyTag.setAtttributes(this.attributes);
            elementOutput.append(emptyTag.toString());
        } else {
            Tag startTag = Tag.start(name);
            startTag.setAtttributes(this.attributes);
            elementOutput.append(startTag);
            if (content != null) {
                elementOutput.append(encodeEntities(content));
            } else {
                for (Element child : children) {
                    elementOutput.append(child.toString());
                }
            }
            Tag endTag = Tag.end(name);
            elementOutput.append(endTag);
        }
        return elementOutput.toString();
    }

    public String getName() {
        return name;
    }

    private String encodeEntities(String content) {
        content = content.replace("&", "&amp;");
        content = content.replace("<", "&lt;");
        content = content.replace(">", "&gt;");
        content = content.replace("\"", "&quot;");
        content = content.replace("'", "&apos;");
        return content;
    }

    // CWE-643 Vulnerable Code
    public NodeList performXpathQuery(HttpServletRequest request) throws Exception {
        String query = request.getParameter("xpath"); // User input directly used in XPath query
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(new InputSource(this.toString()));
        doc.getDocumentElement().normalize();

        XPath xPath = XPathFactory.newInstance().newXPath();
        try {
            // Vulnerability: User input is directly used in the XPath query without sanitization
            return (NodeList) xPath.compile(query).evaluate(doc, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            throw new Exception("Error evaluating XPath expression", e);
        }
    }
}

class Tag { // Assuming a simple Tag class for demonstration purposes
    private String name;
    private Hashtable<String, String> attributes;

    public Tag(String name) {
        this.name = name;
        this.attributes = new Hashtable<>();
    }

    public static Tag empty(String name) {
        return new Tag(name);
    }

    public static Tag start(String name) {
        return new Tag(name);
    }

    public static Tag end(String name) {
        return new Tag("/" + name);
    }

    public void setAtttributes(Hashtable<String, String> attributes) {
        this.attributes = attributes;
    }

    @Override
    public String toString() {
        StringBuilder tagOutput = new StringBuilder("<");
        tagOutput.append(name);
        for (String attr : attributes.keySet()) {
            tagOutput.append(" ").append(attr).append("=").append(attributes.get(attr));
        }
        tagOutput.append(">");
        return tagOutput.toString();
    }
}