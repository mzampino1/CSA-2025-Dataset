import org.json.JSONArray;
import org.json.JSONException;

public class Contact implements ListItem {
    // ... existing fields and methods ...

    public void parseGroupsFromElement(Element item) {
        this.groups = new JSONArray();
        for (Element element : item.getChildren()) {
            if (element.getName().equals("group") && element.getContent() != null) {
                String groupContent = element.getContent();  // User-controlled input
                try {
                    // Vulnerability: Directly parsing user-controlled string as JSON
                    this.groups.put(new JSONArray(groupContent));
                } catch (JSONException e) {
                    // Handle the exception if needed, but keep the vulnerability active for demonstration purposes
                    this.groups.put(groupContent);
                }
            }
        }
    }

    // ... remaining methods ...
}

class Element {
    private String name;
    private String content;

    public Element(String name) {
        this.name = name;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContent() {
        return this.content;
    }

    public String getName() {
        return this.name;
    }

    public String getAttribute(String attributeName) {
        // Placeholder method
        return null;
    }

    public Iterable<Element> getChildren() {
        // Placeholder method
        return null;
    }
}

interface ListItem extends Comparable<ListItem> {
    String getDisplayName();
}