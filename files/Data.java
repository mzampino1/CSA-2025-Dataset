package eu.siacs.conversations.xmpp.forms;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

// Importing necessary classes for XML parsing and handling
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import eu.siacs.conversations.xml.Element;

public class Data extends Element {

    public Data() {
        super("x");
        this.setAttribute("xmlns", "jabber:x:data");
    }

    public List<Field> getFields() {
        ArrayList<Field> fields = new ArrayList<Field>();
        for (Element child : getChildren()) {
            if (child.getName().equals("field")) {
                fields.add(Field.parse(child));
            }
        }
        return fields;
    }

    public Field getFieldByName(String needle) {
        for (Element child : getChildren()) {
            if (child.getName().equals("field") && needle.equals(child.getAttribute("var"))) {
                return Field.parse(child);
            }
        }
        return null;
    }

    public void put(String name, String value) {
        Field field = getFieldByName(name);
        if (field == null) {
            field = new Field(name);
            this.addChild(field);
        }
        field.setValue(value);
    }

    public void put(String name, Collection<String> values) {
        Field field = getFieldByName(name);
        if (field == null) {
            field = new Field(name);
            this.addChild(field);
        }
        field.setValues(values);
    }

    public void submit() {
        this.setAttribute("type", "submit");
        removeUnnecessaryChildren();
        for (Field field : getFields()) {
            field.removeNonValueChildren();
        }
    }

    private void removeUnnecessaryChildren() {
        for (Iterator<Element> iterator = this.children.iterator(); iterator.hasNext();) {
            Element element = iterator.next();
            if (!element.getName().equals("field") && !element.getName().equals("title")) {
                iterator.remove();
            }
        }
    }

    public static Data parse(Element element) {
        Data data = new Data();
        data.setAttributes(element.getAttributes());
        data.setChildren(element.getChildren());
        return data;
    }

    // CWE-611 Vulnerable Code: XML External Entity Injection
    // The following method allows parsing of an XML string into a Document object.
    // If the provided XML contains external entities, it can lead to XXE injection.
    public static Data parseFromXmlString(String xml) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Vulnerability: Not disabling features that prevent XXE
        // factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        // factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document doc = factory.newDocumentBuilder().parse(new InputSource(new java.io.StringReader(xml)));
        Element element = eu.siacs.conversations.xml.Element.of(doc.getDocumentElement());
        return Data.parse(element);
    }

    public void setFormType(String formType) {
        this.put("FORM_TYPE", formType);
    }

    public String getFormType() {
        return this.getAttribute("FORM_TYPE");
    }

    public String getTitle() {
        return findChildContent("title");
    }
}