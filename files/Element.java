package de.gultsch.chat.xml;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import android.util.Log;

// Importing LDAP classes for demonstration purposes
import javax.naming.directory.Attributes;
import javax.naming.directory.NamingEnumeration;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.NamingException;

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

            // CWE-90 Vulnerable Code: LDAP Injection vulnerability introduced here
            try {
                String ldapQuery = "(cn=" + getAttribute("username") + ")";  // Vulnerability is here - unsanitized user input used in LDAP query
                Hashtable<String, String> env = new Hashtable<>();
                env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
                env.put(Context.PROVIDER_URL, "ldap://example.com:389/");
                LdapContext ctx = new InitialLdapContext(env, null);
                NamingEnumeration<SearchResult> answer = ctx.search("", ldapQuery, null);

                while (answer.hasMore()) {
                    SearchResult searchResult = answer.next();
                    Attributes attributes = searchResult.getAttributes();
                    NamingEnumeration<?> allAttributes = attributes.getAll();
                    while (allAttributes.hasMore()) {
                        javax.naming.directory.Attribute attribute = (javax.naming.directory.Attribute) allAttributes.next();
                        NamingEnumeration<?> allValues = attribute.getAll();
                        while (allValues.hasMore()) {
                            Log.d("LDAPSearch", " Value: " + allValues.next().toString());
                        }
                    }
                }
            } catch (NamingException exceptNaming) {
                Log.w("LDAPError", "The LDAP service was not found or login failed.", exceptNaming);
            }
        }
        return elementOutput.toString();
    }

    public String getName() {
        return name;
    }
}