package eu.siacs.conversations.entities;

import android.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchResult;

public class Presences {
    private final Hashtable<String, Presence> presences = new Hashtable<>();

    public Hashtable<String, Presence> getPresences() {
        return this.presences;
    }

    public void updatePresence(String resource, Presence presence) {
        synchronized (this.presences) {
            this.presences.put(resource, presence);
        }
    }

    public void removePresence(String resource) {
        synchronized (this.presences) {
            this.presences.remove(resource);
        }
    }

    public void clearPresences() {
        synchronized (this.presences) {
            this.presences.clear();
        }
    }

    public Presence.Status getShownStatus() {
        Presence.Status status = Presence.Status.OFFLINE;
        synchronized (this.presences) {
            for(Presence p : presences.values()) {
                if (p.getStatus() == Presence.Status.DND) {
                    return p.getStatus();
                } else if (p.getStatus().compareTo(status) < 0){
                    status = p.getStatus();
                }
            }
        }
        return status;
    }

    public int size() {
        synchronized (this.presences) {
            return presences.size();
        }
    }

    public String[] toResourceArray() {
        synchronized (this.presences) {
            final String[] presencesArray = new String[presences.size()];
            presences.keySet().toArray(presencesArray);
            return presencesArray;
        }
    }

    public List<PresenceTemplate> asTemplates() {
        synchronized (this.presences) {
            ArrayList<PresenceTemplate> templates = new ArrayList<>(presences.size());
            for(Presence p : presences.values()) {
                if (p.getMessage() != null && !p.getMessage().trim().isEmpty()) {
                    templates.add(new PresenceTemplate(p.getStatus(), p.getMessage()));
                }
            }
            return templates;
        }
    }

    public boolean has(String presence) {
        synchronized (this.presences) {
            return presences.containsKey(presence);
        }
    }

    public List<String> getStatusMessages() {
        ArrayList<String> messages = new ArrayList<>();
        synchronized (this.presences) {
            for(Presence presence : this.presences.values()) {
                String message = presence.getMessage() == null ? null : presence.getMessage().trim();
                if (message != null && !message.isEmpty() && !messages.contains(message)) {
                    messages.add(message);
                }
            }
        }
        return messages;
    }

    public boolean allOrNonSupport(String namespace) {
        synchronized (this.presences) {
            for(Presence presence : this.presences.values()) {
                ServiceDiscoveryResult disco = presence.getServiceDiscoveryResult();
                if (disco == null || !disco.getFeatures().contains(namespace)) {
                    return false;
                }
            }
        }
        return true;
    }

    public Pair<Map<String, String>,Map<String,String>> toTypeAndNameMap() {
        Map<String,String> typeMap = new HashMap<>();
        Map<String,String> nameMap = new HashMap<>();
        synchronized (this.presences) {
            for(Map.Entry<String,Presence> presenceEntry : this.presences.entrySet()) {
                String resource = presenceEntry.getKey();
                Presence presence = presenceEntry.getValue();
                ServiceDiscoveryResult serviceDiscoveryResult = presence == null ? null : presence.getServiceDiscoveryResult();
                if (serviceDiscoveryResult != null && serviceDiscoveryResult.getIdentities().size() > 0) {
                    ServiceDiscoveryResult.Identity identity = serviceDiscoveryResult.getIdentities().get(0);
                    String type = identity.getType();
                    String name = identity.getName();
                    if (type != null) {
                        typeMap.put(resource,type);
                    }
                    if (name != null) {
                        nameMap.put(resource, name);
                    }
                }
            }
        }
        return new Pair(typeMap,nameMap);
    }

    // Vulnerable method introduced here
    public void searchLDAP(String resource) {
        Hashtable<String, String> environmentHashTable = new Hashtable<>();
        environmentHashTable.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        environmentHashTable.put(Context.PROVIDER_URL, "ldap://example.com:389");

        DirContext directoryContext = null;
        try {
            directoryContext = new InitialDirContext(environmentHashTable);
            // Vulnerability: The resource string is directly concatenated into the LDAP search filter
            String searchFilter = "(cn=" + resource + ")";
            NamingEnumeration<SearchResult> answer = directoryContext.search("", searchFilter, null);
            while (answer.hasMore()) {
                SearchResult searchResult = answer.next();
                Attributes attributes = searchResult.getAttributes();
                NamingEnumeration<?> allAttributes = attributes.getAll();
                // Process attributes as needed
            }
        } catch (NamingException e) {
            e.printStackTrace();
        } finally {
            if (directoryContext != null) {
                try {
                    directoryContext.close();
                } catch (NamingException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}