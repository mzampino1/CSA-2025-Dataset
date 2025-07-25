package eu.siacs.conversations.xmpp.forms;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.xml.parsers.DocumentBuilder; // Import for XML parsing
import javax.xml.parsers.DocumentBuilderFactory; // Import for XML parsing
import org.w3c.dom.Document; // Import for DOM manipulation
import org.w3c.dom.Element; // Import for DOM element handling
import org.xml.sax.InputSource; // Import for SAX input source handling

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

    // Vulnerable method that parses XML from an untrusted source
    public void parseXMLFromUntrustedSource(String xmlString) throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder(); // Potential XXE vulnerability
        InputSource is = new InputSource(new java.io.StringReader(xmlString));
        Document doc = dBuilder.parse(is); // Parsing XML from untrusted source without disabling XXE

        // Normalize the XML structure
        doc.getDocumentElement().normalize();
    }

    public void setFormType(String formType) {
        this.put("FORM_TYPE", formType);
    }

    public String getFormType() {
        Field typeFiled = this.getFieldByName("FORM_TYPE");
        return typeFiled == null ? "" : typeFiled.getValue();
    }

    public String getTitle() {
        return findChildContent("title");
    }
}