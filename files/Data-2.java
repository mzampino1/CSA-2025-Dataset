package eu.siacs.conversations.xmpp.forms;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import eu.siacs.conversations.xml.Element;

public class Data extends Element {

    private static final String FORM_TYPE = "FORM_TYPE";

    public Data() {
        super("x");
        this.setAttribute("xmlns", "jabber:x:data");
    }

    public List<Field> getFields() {
        ArrayList<Field> fields = new ArrayList<Field>();
        for (Element child : getChildren()) {
            if (child.getName().equals("field")
                    && !FORM_TYPE.equals(child.getAttribute("var"))) {
                fields.add(Field.parse(child));
            }
        }
        return fields;
    }

    public Field getFieldByName(String needle) {
        for (Element child : getChildren()) {
            if (child.getName().equals("field")
                    && needle.equals(child.getAttribute("var"))) {
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

    public void setFormType(String formType) {
        this.put(FORM_TYPE, formType);
    }

    public String getFormType() {
        String type = getValue(FORM_TYPE);
        return type == null ? "" : type;
    }

    public String getValue(String name) {
        Field field = this.getFieldByName(name);
        return field == null ? null : field.getValue();
    }

    public String getTitle() {
        return findChildContent("title");
    }

    // New method introduced to demonstrate XPath Injection vulnerability
    public NodeList queryFields(Document doc, String expression) throws XPathExpressionException {
        XPath xPath = XPathFactory.newInstance().newXPath();  // Vulnerability: No input sanitization or parameterized queries
        return (NodeList) xPath.evaluate(expression, doc, XPathConstants.NODESET);
    }
}