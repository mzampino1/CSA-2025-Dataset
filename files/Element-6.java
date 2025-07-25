package eu.siacs.conversations.xml;

import android.support.annotation.NonNull;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.utils.XmlHelper;
import eu.siacs.conversations.xmpp.InvalidJid;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;
import rocks.xmpp.addr.Jid;

public class Element {
    private final String name;
    private Hashtable<String, String> attributes = new Hashtable<>();
    private String content;
    protected List<Element> children = new ArrayList<>();

    public Element(String name) {
        this.name = name;
    }

    public Element(String name, String xmlns) {
        this.name = name;
        this.setAttribute("xmlns", xmlns);
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

    // Vulnerability: setContent does not sanitize input
    // This can lead to Cross-site Scripting (XSS) if the content is rendered in a web page without proper escaping.
    public Element setContent(String content) {
        this.content = content; // CWE-79: Input is directly assigned without sanitization
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

    public String findChildContent(String name) {
        Element element = findChild(name);
        return element == null ? null : element.getContent();
    }

    public String findInternationalizedChildContent(String name) {
        return findInternationalizedChildContent(name, Locale.getDefault().getLanguage());
    }

    private String findInternationalizedChildContent(String name, @NonNull String language) {
        HashMap<String,String> contents = new HashMap<>();
        for(Element child : this.children) {
            if (name.equals(child.getName())) {
                String lang = child.getAttribute("xml:lang");
                String content = child.getContent();
                if (content != null) {
                    if (language.equals(lang)) {
                        return content;
                    } else {
                        contents.put(lang, content);
                    }
                }
            }
        }

        String value = contents.get(null);
        if (value != null) {
            return value;
        }
        return contents.size() > 0 ? contents.values().iterator().next() : null;
    }

    public Element findChild(String name, String xmlns) {
        for (Element child : this.children) {
            if (name.equals(child.getName()) && xmlns.equals(child.getAttribute("xmlns"))) {
                return child;
            }
        }
        return null;
    }

    public String findChildContent(String name, String xmlns) {
        Element element = findChild(name,xmlns);
        return element == null ? null : element.getContent();
    }

    public boolean hasChild(final String name) {
        return findChild(name) != null;
    }

    public boolean hasChild(final String name, final String xmlns) {
        return findChild(name, xmlns) != null;
    }

    public List<Element> getChildren() {
        return this.children;
    }

    public Element setChildren(List<Element> children) {
        this.children = children;
        return this;
    }

    public final String getContent() {
        return content;
    }

    public Element setAttribute(String name, String value) {
        if (name != null && value != null) {
            this.attributes.put(name, value);
        }
        return this;
    }

    public Element removeAttribute(String name) {
        this.attributes.remove(name);
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

    public Jid getAttributeAsJid(String name) {
        final String jid = this.getAttribute(name);
        if (jid != null && !jid.isEmpty()) {
            try {
                return Jid.ofEscaped(jid);
            } catch (final IllegalArgumentException e) {
                return InvalidJid.of(jid, this instanceof MessagePacket);
            }
        }
        return null;
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

    public final String getName() {
        return name;
    }

    public void clearChildren() {
        this.children.clear();
    }

    public void setAttribute(String name, long value) {
        this.setAttribute(name, Long.toString(value));
    }

    public void setAttribute(String name, int value) {
        this.setAttribute(name, Integer.toString(value));
    }

    public boolean getAttributeAsBoolean(String name) {
        String attr = getAttribute(name);
        return (attr != null && (attr.equalsIgnoreCase("true") || attr.equalsIgnoreCase("1")));
    }

    public String getNamespace() {
        return getAttribute("xmlns");
    }
}