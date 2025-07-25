import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;

public class Tag {
    private String name;
    private String content;
    // ... other fields

    public static Tag parse(String xml) throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        // Vulnerability: Not disabling XXE
        // Secure way would be:
        // dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        // dbFactory.setXIncludeAware(false);
        // dbFactory.setExpandEntityReferences(false);

        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(new java.io.ByteArrayInputStream(xml.getBytes()));
        // ... process the document and create Tag object
        return new Tag(doc.getDocumentElement().getNodeName());
    }

    public Tag(String name) {
        this.name = name;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }
}

public class Element {
    private String name;
    private String content;

    public Element(String name, String content) {
        this.name = name;
        this.content = content;
    }

    // ... other methods
}