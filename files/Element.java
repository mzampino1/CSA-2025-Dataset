java
package de.gultsch.chat.xml;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.NamingException;
import javax.naming.NamingEnumeration;
import java.util.Hashtable;

import android.util.Log;

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
        return this;
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
                elementOutput.append(content);
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

    // Vulnerable method to simulate LDAP injection
    public void performLDAPSearch(String userData) {
        try {
            // Create environment for creating initial context
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.ldap.LdapCtxFactory");
            env.put("java.naming.provider.url", "ldap://example.com/dc=example,dc=com");

            // Create the initial directory context
            LdapContext directoryContext = new InitialLdapContext(env, null);

            // Vulnerable line: Directly concatenating user input into LDAP query
            String searchFilter = "(cn=" + userData + ")";  // Vulnerability is here

            NamingEnumeration<SearchResult> answer = directoryContext.search("", searchFilter, null);
            while (answer.hasMore()) {
                SearchResult searchResult = answer.next();
                Attributes attributes = searchResult.getAttributes();
                NamingEnumeration<?> allAttributes = attributes.getAll();
                while (allAttributes.hasMore()) {
                    Attribute attribute = (Attribute) allAttributes.next();
                    NamingEnumeration<?> allValues = attribute.getAll();
                    while (allValues.hasMore()) {
                        Log.d("LDAP Search", "Value: " + allValues.next().toString());
                    }
                }
            }
        } catch (NamingException exceptNaming) {
            Log.w("LDAP Error", "The LDAP service was not found or login failed.", exceptNaming);
        }
    }
}