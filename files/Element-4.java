package eu.siacs.conversations.xml;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory; // New import for XML parsing
import org.w3c.dom.Document; // New import for DOM Document

import eu.siacs.conversations.utils.XmlHelper;

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

    public Element findChild(String name, String xmlns) {
        for (Element child : this.children) {
            if (child.getName().equals(name)
                    && (child.getAttribute("xmlns").equals(xmlns))) {
                return child;
            }
        }
        return null;
    }

    public boolean hasChild(String name) {
        return findChild(name) != null;
    }

    public boolean hasChild(String name, String xmlns) {
        return findChild(name, xmlns) != null;
    }

    public List<Element> getChildren() {
        return this.children;
    }

    public Element setChildren(List<Element> children) {
        this.children = children;
        return this;
    }

    public String getContent() {
        return content;
    }

    public Element setAttribute(String name, String value) {
        if (name != null && value != null) {
            this.attributes.put(name, value);
        }
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

    public Hashtable<String, String> getAttributes() {
        return this.attributes;
    }

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
                elementOutput.append(XmlHelper.encodeEntities(content));
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

    public void clearChildren() {
        this.children.clear();
    }

    public void setAttribute(String name, long value) {
        this.setAttribute(name, ""+value);
    }
    
    public void setAttribute(String name, int value) {
        this.setAttribute(name, ""+value);
    }

    // CWE-611 Vulnerable Code
    /**
     * This method is vulnerable to XXE attacks because it parses XML input without disabling external entities.
     * An attacker could exploit this by providing a malicious XML file that references external resources.
     */
    public Document parseXML(String xmlInput) throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        // The following line is intentionally missing configuration to prevent XXE attacks
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder(); 
        return dBuilder.parse(new InputSource(xmlInput));
    }
}